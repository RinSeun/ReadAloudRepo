package com.lss.lotusaloud

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Gets speech from the chosen voice service (Fish Audio, ElevenLabs or the phone) as raw
 * 16-bit mono PCM and plays it while it streams in. Supports pause, skip back/forward, live speed changes, and keeps
 * recent audio on the phone so replays are instant and free.
 */
class SpeechPlayer {
    enum class Phase { IDLE, LOADING, PLAYING, PAUSED, ENDED, ERROR }

    data class State(
        val phase: Phase,
        val current: Double = 0.0,
        val duration: Double = 0.0,
        val available: Double = 0.0, // seconds generated so far
        val streaming: Boolean = false,
        val message: String = "",
        val since: Long = 0L, // when this phase started (ms)
        val speed: Float = 1f,
    )

    companion object {
        const val API_URL = "https://api.fish.audio/v1/tts"
        const val FISH_RATE = 44100
        private const val PIECE_MS = 25 // audio handed to the speaker per write
        private const val DISK_CACHE_MAX = 15

        /** One player for the whole app (service, bubble, tile, screens). */
        val shared = SpeechPlayer()

        fun errorText(code: Int, detail: String, model: String): String = when {
            code == 401 || code == 403 -> "Your API key was rejected. Re-copy it from fish.audio/app/api-keys."
            code == 402 -> "No API credit. Fish bills the API separately from Plus/Pro. Add credit at fish.audio, or pick the s2.1-pro-free model."
            code == 404 -> "Voice not found. Check the voice link."
            code == 400 && detail.contains("model", ignoreCase = true) -> "Fish didn't accept the model \"$model\". Pick another one."
            else -> "Fish Audio error $code. ${detail.take(200)}"
        }

        /** The request body sent to Fish. Speed is applied on the phone, so it isn't sent. */
        fun buildBody(text: String, voiceId: String, quality: String): JSONObject =
            JSONObject().apply {
                put("text", text)
                put("format", "pcm")
                put("sample_rate", FISH_RATE)
                when (quality) {
                    "fast" -> { put("latency", "low"); put("chunk_length", 150) }
                    "quality" -> put("latency", "normal")
                    else -> put("latency", "balanced")
                }
                if (voiceId.isNotBlank()) put("reference_id", voiceId)
            }

        private fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private val lock = Object()
    private var gen = 0
    private var seekGen = 0
    private var buf = ByteArray(1 shl 20)
    private var len = 0
    private var done = true
    private var pos = 0              // bytes handed to the speaker so far
    private var baseSec = 0.0        // where playback was when the track was last reset
    private var fedSinceBase = 0L    // bytes written to the track since then
    private var paused = false
    private var phase = Phase.IDLE
    private var message = ""
    private var since = 0L
    private var speed = 1f
    private var track: AudioTrack? = null
    private var cacheDir: File? = null
    private var appCtx: Context? = null
    private var rate = FISH_RATE     // samples per second of the current clip (depends on the voice service)
    private fun bps() = rate * 2     // bytes per second
    private val inFlight = java.util.Collections.synchronizedSet(HashSet<String>()) // downloads running now

    /** Called (on a background thread) when a clip finishes playing to the end. */
    @Volatile var onEnded: (() -> Unit)? = null

    fun init(context: Context) {
        if (cacheDir == null) cacheDir = File(context.cacheDir, "reads").apply { mkdirs() }
        if (appCtx == null) appCtx = context.applicationContext
    }

    private fun setPhase(p: Phase) {
        if (phase != p) {
            phase = p
            since = System.currentTimeMillis()
        }
    }

    private fun currentLocked(): Double {
        val t = track
        if (t != null) {
            val head = try { t.playbackHeadPosition.toLong() and 0xffffffffL } catch (e: Exception) { 0L }
            return minOf(baseSec + head.toDouble() / rate, pos.toDouble() / bps())
        }
        return if (phase == Phase.ENDED) len.toDouble() / bps() else baseSec
    }

    fun snapshot(): State = synchronized(lock) {
        State(
            phase = phase,
            current = currentLocked(),
            duration = if (done) len.toDouble() / bps() else 0.0,
            available = len.toDouble() / bps(),
            streaming = !done,
            message = message,
            since = since,
            speed = speed,
        )
    }

    fun isBusy(): Boolean = snapshot().phase.let { it == Phase.LOADING || it == Phase.PLAYING || it == Phase.PAUSED }

    fun speak(text: String, voiceId: String, cfg: TtsConfig) {
        val key = sha1(Providers.cacheKey(text, voiceId, cfg))
        val g = synchronized(lock) {
            gen++
            haltTrackLocked()
            paused = false
            pos = 0
            baseSec = 0.0
            fedSinceBase = 0
            message = ""
            buf = ByteArray(1 shl 20)
            len = 0
            done = false
            phase = Phase.LOADING
            since = System.currentTimeMillis()
            gen
        }
        Thread({ download(g, key, text, voiceId, cfg) }, "lotus-download").start()
        Thread({ play(g) }, "lotus-play").start()
    }

    /** Generates audio in the background and keeps it on the phone, so playing it later starts instantly. */
    fun prefetch(text: String, voiceId: String, cfg: TtsConfig) {
        val ctx = appCtx ?: return
        val key = sha1(Providers.cacheKey(text, voiceId, cfg))
        if (findCached(key) != null || !inFlight.add(key)) return
        Thread({
            try {
                when (val src = Providers.open(ctx, text, voiceId, cfg)) {
                    is AudioSource.Bytes -> if (src.data.size > 1) saveToCache(key, src.rate, src.data)
                    is AudioSource.Stream -> try {
                        val data = src.input.use { it.readBytes() }
                        if (data.size > 1) saveToCache(key, src.rate, data)
                    } finally {
                        src.conn.disconnect()
                    }
                }
            } catch (e: Exception) {
                // it'll simply be generated when it's needed
            } finally {
                inFlight.remove(key)
            }
        }, "lotus-prefetch").start()
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

    fun pause() = synchronized(lock) { if (phase == Phase.PLAYING) togglePause() }
    fun resume() = synchronized(lock) { if (phase == Phase.PAUSED) togglePause() }

    /** Changes speed straight away, without generating the audio again. */
    fun setSpeed(s: Float) {
        synchronized(lock) {
            speed = s.coerceIn(0.5f, 2f)
            applySpeedLocked(track)
        }
    }

    private fun applySpeedLocked(t: AudioTrack?) {
        if (t == null) return
        try {
            t.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f)
        } catch (e: Exception) {
        }
    }

    fun skip(seconds: Double) = synchronized(lock) { seekToLocked(currentLocked() + seconds) }

    fun seekTo(seconds: Double) = synchronized(lock) { seekToLocked(seconds) }

    private fun seekToLocked(seconds: Double) {
        val canSeek = phase == Phase.PLAYING || phase == Phase.PAUSED ||
            (phase == Phase.ENDED && done && len > 0)
        if (!canSeek) return
        val maxSec = len.toDouble() / bps()
        val target = seconds.coerceIn(0.0, maxOf(0.0, maxSec - 0.05))
        pos = ((target * bps()).toInt()) and 1.inv()
        baseSec = pos.toDouble() / bps()
        fedSinceBase = 0
        seekGen++
        val t = track
        if (t != null) {
            try {
                t.pause()
                t.flush()
                if (!paused) t.play()
            } catch (e: Exception) {
            }
        } else if (phase == Phase.ENDED) {
            // finished already: start playing again from the new spot
            gen++
            paused = false
            setPhase(Phase.PLAYING)
            val g = gen
            Thread({ play(g) }, "lotus-play").start()
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

    // ------------------------------------------------------------------ cache on the phone
    /** A saved clip and its sample rate (file name: key.rate.pcm). */
    private fun findCached(key: String): Pair<File, Int>? {
        val dir = cacheDir ?: return null
        val f = dir.listFiles { f -> f.name.startsWith("$key.") && f.name.endsWith(".pcm") }?.firstOrNull() ?: return null
        val r = f.name.removePrefix("$key.").removeSuffix(".pcm").toIntOrNull() ?: return null
        return if (f.length() > 1) f to r else null
    }

    private fun saveToCache(key: String, sampleRate: Int, data: ByteArray) {
        val dir = cacheDir ?: return
        try {
            val tmp = File(dir, "$key.tmp")
            tmp.writeBytes(data)
            tmp.renameTo(File(dir, "$key.$sampleRate.pcm"))
            val files = dir.listFiles { f -> f.name.endsWith(".pcm") }?.sortedByDescending { it.lastModified() } ?: return
            files.drop(DISK_CACHE_MAX).forEach { it.delete() }
        } catch (e: Exception) {
        }
    }

    // ------------------------------------------------------------------ download
    private fun download(g: Int, key: String, text: String, voiceId: String, cfg: TtsConfig) {
        // If this exact audio is already being prepared in the background, wait for it
        val waitUntil = System.currentTimeMillis() + 90_000
        while (inFlight.contains(key) && System.currentTimeMillis() < waitUntil) {
            if (synchronized(lock) { g != gen }) return
            Thread.sleep(50)
        }
        // Replay from the phone if we've generated this exact audio before
        val cached = findCached(key)
        if (cached != null) {
            try {
                val data = cached.first.readBytes()
                cached.first.setLastModified(System.currentTimeMillis())
                synchronized(lock) {
                    if (g != gen) return
                    rate = cached.second
                    buf = data
                    len = data.size
                    done = true
                }
                return
            } catch (e: Exception) {
                // fall through and generate it again
            }
        }

        val ctx = appCtx ?: return fail(g, "Lotus Aloud wasn't ready - try again.")
        var source: AudioSource? = null
        inFlight.add(key)
        try {
            val src = Providers.open(ctx, text, voiceId, cfg)
            source = src
            when (src) {
                is AudioSource.Bytes -> {
                    synchronized(lock) {
                        if (g != gen) return
                        rate = src.rate
                        buf = src.data
                        len = src.data.size
                        done = true
                    }
                    if (src.data.size > 1) saveToCache(key, src.rate, src.data)
                }
                is AudioSource.Stream -> {
                    synchronized(lock) { if (g == gen) rate = src.rate }
                    val chunk = ByteArray(16384)
                    src.input.use { input ->
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            if (n > 0) {
                                val stillMine = synchronized(lock) {
                                    if (g == gen) { append(chunk, n); true } else false
                                }
                                if (!stillMine) return
                            }
                        }
                    }
                    val copy = synchronized(lock) {
                        if (g != gen) return
                        done = true
                        if (len < 2) {
                            message = "The voice service sent no audio."
                            setPhase(Phase.ERROR)
                            return
                        }
                        buf.copyOf(len)
                    }
                    saveToCache(key, src.rate, copy)
                }
            }
        } catch (e: ProviderError) {
            fail(g, e.message ?: "The voice service had a problem.")
        } catch (e: IOException) {
            val haveSome = synchronized(lock) { g == gen && len > 1 }
            if (haveSome) {
                synchronized(lock) { if (g == gen) done = true } // connection dropped: play what we got
            } else {
                fail(g, "Couldn't reach the voice service. Check your internet connection.")
            }
        } catch (e: Exception) {
            fail(g, "Something went wrong: ${e.message}")
        } finally {
            inFlight.remove(key)
            (source as? AudioSource.Stream)?.conn?.disconnect()
        }
    }

    // ------------------------------------------------------------------ playback
    private fun play(g: Int) {
        // wait for the first ~0.2 s of audio (or the whole clip if it's shorter)
        while (true) {
            val st = synchronized(lock) {
                val avail = len - pos
                when {
                    g != gen || phase == Phase.ERROR -> -1
                    avail >= bps() / 5 || (done && avail >= 2) -> 1
                    done -> -1
                    else -> 0
                }
            }
            if (st == -1) return
            if (st == 1) break
            Thread.sleep(20)
        }

        val r = synchronized(lock) { rate } // this clip's sample rate (known once audio has arrived)
        val pieceBytes = (r * 2 * PIECE_MS / 1000) and 1.inv()
        val minBuf = AudioTrack.getMinBufferSize(r, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(r)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf * 2, r / 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val mine = synchronized(lock) {
            if (g == gen) {
                track = t
                applySpeedLocked(t)
                if (!paused) t.play()
                true
            } else false
        }
        if (!mine) {
            t.release()
            return
        }

        try {
            outer@ while (true) {
                // ---- feed audio to the speaker
                while (true) {
                    var piece: ByteArray? = null
                    var quit = false
                    var finished = false
                    var sg = 0
                    synchronized(lock) {
                        sg = seekGen
                        if (g != gen) {
                            quit = true
                        } else if (!paused) {
                            val avail = (len - pos) and 1.inv() // whole samples only
                            if (avail <= 0) {
                                if (done) finished = true else piece = ByteArray(0) // still generating
                            } else {
                                val n = minOf(pieceBytes, avail)
                                piece = buf.copyOfRange(pos, pos + n)
                                pos += n
                                setPhase(Phase.PLAYING)
                            }
                        }
                    }
                    if (quit) return
                    if (finished) break
                    val p = piece
                    if (p == null || p.isEmpty()) {
                        Thread.sleep(30)
                    } else {
                        val r = t.write(p, 0, p.size)
                        if (r < 0) return
                        synchronized(lock) { if (sg == seekGen) fedSinceBase += r }
                    }
                }

                // ---- let the end play out of the speaker (a skip back sends us round again)
                var deadline = System.currentTimeMillis() + 4000
                while (true) {
                    var stillMine = false
                    var isPaused = false
                    var more = false
                    var fed = 0L
                    synchronized(lock) {
                        stillMine = g == gen
                        isPaused = paused
                        more = pos < len
                        fed = fedSinceBase
                    }
                    if (!stillMine) return
                    if (more) continue@outer
                    val now = System.currentTimeMillis()
                    if (isPaused) deadline = now + 4000
                    val head = t.playbackHeadPosition.toLong() and 0xffffffffL
                    if (head >= fed / 2 || now > deadline) break@outer
                    Thread.sleep(30)
                }
            }
            val endedNow = synchronized(lock) {
                if (g == gen) { setPhase(Phase.ENDED); true } else false
            }
            if (endedNow) try { onEnded?.invoke() } catch (e: Exception) {}
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
