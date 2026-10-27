package com.lss.fishreader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads speech from Fish Audio as raw PCM (16-bit mono, 44.1 kHz) and plays it
 * while it streams in, so audio starts as soon as the first part is generated.
 */
class FishPlayer {
    enum class Phase { IDLE, LOADING, PLAYING, PAUSED, ENDED, ERROR }

    data class State(
        val phase: Phase,
        val current: Double = 0.0,
        val duration: Double = 0.0,
        val streaming: Boolean = false,
        val message: String = "",
        val since: Long = 0L, // when this phase started (ms)
    )

    companion object {
        const val API_URL = "https://api.fish.audio/v1/tts"
        const val RATE = 44100
        const val BYTES_PER_SEC = RATE * 2
        private const val PIECE = 4410 // 25 ms of audio per write
        private const val CACHE_MAX = 8

        /** One player for the whole app (service + screens). */
        val shared = FishPlayer()

        fun errorText(code: Int, detail: String, model: String): String = when {
            code == 401 || code == 403 -> "Your API key was rejected. Re-copy it from fish.audio/app/api-keys."
            code == 402 -> "No API credit. Fish bills the API separately from Plus/Pro. Add credit at fish.audio, or pick the s2.1-pro-free model."
            code == 404 -> "Voice not found. Check the voice link."
            code == 400 && detail.contains("model", ignoreCase = true) -> "Fish didn't accept the model \"$model\". Pick another one."
            else -> "Fish Audio error $code. ${detail.take(200)}"
        }

        /** The request body sent to Fish. */
        fun buildBody(text: String, voiceId: String, quality: String, speed: Float): JSONObject =
            JSONObject().apply {
                put("text", text)
                put("format", "pcm")
                put("sample_rate", RATE)
                put("prosody", JSONObject().put("speed", speed.toDouble()))
                when (quality) {
                    "fast" -> { put("latency", "low"); put("chunk_length", 150) }
                    "quality" -> put("latency", "normal")
                    else -> put("latency", "balanced")
                }
                if (voiceId.isNotBlank()) put("reference_id", voiceId)
            }
    }

    private val lock = Object()
    private var gen = 0
    private var buf = ByteArray(1 shl 20)
    private var len = 0
    private var done = true
    private var pos = 0
    private var paused = false
    private var phase = Phase.IDLE
    private var message = ""
    private var since = 0L
    private var track: AudioTrack? = null

    // recent finished audio, for instant replays
    private val cache = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean = size > CACHE_MAX
    }

    private fun setPhase(p: Phase) {
        if (phase != p) {
            phase = p
            since = System.currentTimeMillis()
        }
    }

    fun snapshot(): State = synchronized(lock) {
        val played = try {
            val t = track
            if (t != null) (t.playbackHeadPosition.toLong() and 0xffffffffL).toDouble() / RATE else 0.0
        } catch (e: Exception) {
            0.0
        }
        State(
            phase = phase,
            current = played,
            duration = if (done) len.toDouble() / BYTES_PER_SEC else 0.0,
            streaming = !done,
            message = message,
            since = since,
        )
    }

    fun speak(text: String, apiKey: String, voiceId: String, model: String, quality: String, speed: Float) {
        val body = buildBody(text, voiceId, quality, speed)
        val key = model + "|" + body.toString()

        val (g, cached) = synchronized(lock) {
            gen++
            haltTrackLocked()
            paused = false
            pos = 0
            message = ""
            phase = Phase.LOADING
            since = System.currentTimeMillis()
            val hit = cache[key]
            if (hit != null) {
                buf = hit.copyOf()
                len = buf.size
                done = true
            } else {
                buf = ByteArray(1 shl 20)
                len = 0
                done = false
            }
            Pair(gen, hit != null)
        }
        if (!cached) Thread({ download(g, key, apiKey.trim(), model, body) }, "fish-download").start()
        Thread({ play(g) }, "fish-play").start()
    }

    fun stop() {
        synchronized(lock) {
            gen++
            paused = false
            message = ""
            haltTrackLocked()
            setPhase(Phase.IDLE)
        }
    }

    fun togglePause() {
        synchronized(lock) {
            if (phase == Phase.PLAYING || phase == Phase.PAUSED) {
                paused = !paused
                try {
                    if (paused) track?.pause() else track?.play()
                } catch (e: Exception) {
                }
                setPhase(if (paused) Phase.PAUSED else Phase.PLAYING)
            }
        }
    }

    fun showError(msg: String) {
        synchronized(lock) {
            gen++
            haltTrackLocked()
            message = msg
            phase = Phase.ERROR
            since = System.currentTimeMillis()
        }
    }

    /** Silences the current track; its play thread notices the new generation and releases it. */
    private fun haltTrackLocked() {
        try {
            track?.pause()
            track?.flush() // also unblocks a write() waiting for buffer space
        } catch (e: Exception) {
        }
    }

    private fun fail(g: Int, msg: String) {
        synchronized(lock) {
            if (g != gen) return
            message = msg
            done = true
            haltTrackLocked()
            setPhase(Phase.ERROR)
        }
    }

    private fun append(src: ByteArray, n: Int) {
        if (len + n > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, len + n))
        System.arraycopy(src, 0, buf, len, n)
        len += n
    }

    // ------------------------------------------------------------------ download
    private fun download(g: Int, key: String, apiKey: String, model: String, body: JSONObject) {
        var conn: HttpURLConnection? = null
        try {
            val c = URL(API_URL).openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 15000
            c.readTimeout = 60000
            c.setRequestProperty("Authorization", "Bearer $apiKey")
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("model", model)
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = c.responseCode
            if (code != 200) {
                val detail = try {
                    c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) {
                    ""
                }
                fail(g, errorText(code, detail, model))
                return
            }

            val chunk = ByteArray(16384)
            c.inputStream.use { input ->
                var reading = true
                while (reading) {
                    val n = input.read(chunk)
                    if (n < 0) {
                        reading = false
                    } else if (n > 0) {
                        val stillMine = synchronized(lock) {
                            if (g == gen) { append(chunk, n); true } else false
                        }
                        if (!stillMine) return
                    }
                }
            }

            synchronized(lock) {
                if (g != gen) return
                done = true
                if (len < 2) {
                    message = "Fish returned no audio."
                    setPhase(Phase.ERROR)
                    return
                }
                cache[key] = buf.copyOf(len)
            }
        } catch (e: IOException) {
            val haveSome = synchronized(lock) { g == gen && len > 1 }
            if (haveSome) {
                synchronized(lock) { if (g == gen) done = true } // connection dropped: play what we got
            } else {
                fail(g, "Couldn't reach Fish Audio. Check your internet connection.")
            }
        } catch (e: Exception) {
            fail(g, "Something went wrong: ${e.message}")
        } finally {
            conn?.disconnect()
        }
    }

    // ------------------------------------------------------------------ playback
    private fun play(g: Int) {
        // wait for the first ~0.2 s of audio (or the whole clip if it's shorter)
        var ready = false
        while (!ready) {
            val st = synchronized(lock) {
                val avail = len - pos
                when {
                    g != gen || phase == Phase.ERROR -> -1
                    avail >= BYTES_PER_SEC / 5 || (done && avail >= 2) -> 1
                    done -> -1
                    else -> 0
                }
            }
            when (st) {
                -1 -> return
                1 -> ready = true
                else -> Thread.sleep(20)
            }
        }

        val minBuf = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf * 2, BYTES_PER_SEC / 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val mine = synchronized(lock) {
            if (g == gen) { track = t; true } else false
        }
        if (!mine) {
            t.release()
            return
        }

        var written = 0L
        try {
            t.play()
            var finished = false
            while (!finished) {
                var piece: ByteArray? = null
                var quit = false
                synchronized(lock) {
                    if (g != gen) {
                        quit = true
                    } else if (!paused) {
                        val avail = (len - pos) and 1.inv() // whole samples only
                        if (avail <= 0) {
                            if (done) finished = true else piece = ByteArray(0) // still generating
                        } else {
                            val n = minOf(PIECE, avail)
                            piece = buf.copyOfRange(pos, pos + n)
                            pos += n
                            setPhase(Phase.PLAYING)
                        }
                    }
                }
                if (quit) return
                val p = piece
                if (!finished) {
                    if (p == null || p.isEmpty()) {
                        Thread.sleep(30)
                    } else {
                        val r = t.write(p, 0, p.size)
                        if (r < 0) return
                        written += r
                    }
                }
            }

            // let the end play out of the speaker
            val totalFrames = written / 2
            var deadline = System.currentTimeMillis() + 3000
            var draining = true
            while (draining) {
                val (stillMine, isPaused) = synchronized(lock) { Pair(g == gen, paused) }
                if (!stillMine) return
                val now = System.currentTimeMillis()
                if (isPaused) deadline = now + 3000
                val head = t.playbackHeadPosition.toLong() and 0xffffffffL
                if (head >= totalFrames || now > deadline) draining = false else Thread.sleep(30)
            }
            synchronized(lock) { if (g == gen) setPhase(Phase.ENDED) }
        } catch (e: Exception) {
            synchronized(lock) {
                if (g == gen) {
                    message = "Couldn't play audio: ${e.message}"
                    setPhase(Phase.ERROR)
                }
            }
        } finally {
            synchronized(lock) { if (track === t) track = null }
            try {
                t.pause()
                t.flush()
                t.release()
            } catch (e: Exception) {
            }
        }
    }
}
