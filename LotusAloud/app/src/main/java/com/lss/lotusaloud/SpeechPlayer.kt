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

class SpeechPlayer {
    enum class Phase { IDLE, LOADING, PLAYING, PAUSED, ENDED, ERROR }

    data class State(
        val phase: Phase,
        val current: Double = 0.0,
        val duration: Double = 0.0,
        val available: Double = 0.0,
        val streaming: Boolean = false,
        val message: String = "",
        val since: Long = 0L,
        val speed: Float = 1f,
    )

    companion object {
        const val API_URL = "https://api.fish.audio/v1/tts"
        const val FISH_RATE = 44100
        private const val PIECE_MS = 25
        private const val REBUFFER_SEC = 1.5
        private const val DISK_CACHE_MAX = 30
        private const val DISK_CACHE_BYTES = 40L * 1024 * 1024

        fun clearSaved(ctx: Context) {
            File(ctx.cacheDir, "reads").listFiles()?.forEach { it.delete() }
        }

        fun savedBytes(ctx: Context): Long = File(ctx.cacheDir, "reads").listFiles()?.sumOf { it.length() } ?: 0L

        val shared = SpeechPlayer()

        fun errorText(code: Int, detail: String, model: String): String = when {
            code == 401 || code == 403 -> "Your API key was rejected. Re-copy it from fish.audio/app/api-keys."
            code == 402 -> "No API credit. Fish bills the API separately from Plus/Pro. Add credit at fish.audio, or pick the s2.1-pro-free model."
            code == 404 -> "Voice not found. Check the voice link."
            code == 400 && detail.contains("model", ignoreCase = true) -> "Fish didn't accept the model \"$model\". Pick another one."
            else -> "Fish Audio error $code. ${detail.take(200)}"
        }

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
    private var pos = 0
    private var baseSec = 0.0
    private var fedSinceBase = 0L
    private var paused = false
    private var phase = Phase.IDLE
    private var message = ""
    private var since = 0L
    private var speed = 1f
    @Volatile private var startBufferSec = 0.1
    private var track: AudioTrack? = null
    private var cacheDir: File? = null
    private var appCtx: Context? = null
    private var rate = FISH_RATE
    private fun bps() = rate * 2
    private val fetches = HashMap<String, Fetch>()

    private class Fetch {
        val lock = Object()
        var data = ByteArray(1 shl 18)
        var len = 0
        var rate = FISH_RATE
        var done = false
        var error: String? = null
        @Volatile var cancelled = false

        fun add(src: ByteArray, n: Int) = synchronized(lock) {
            if (len + n > data.size) data = data.copyOf(maxOf(data.size * 2, len + n))
            System.arraycopy(src, 0, data, len, n)
            len += n
            lock.notifyAll()
        }

        fun finish(err: String? = null) = synchronized(lock) {
            error = err
            done = true
            lock.notifyAll()
        }
    }

    @Volatile var onEnded: (() -> Unit)? = null

    fun init(context: Context) {
        if (cacheDir == null) cacheDir = File(context.cacheDir, "reads").apply { mkdirs() }
        if (appCtx == null) {
            appCtx = context.applicationContext
            Pronounce.load(context)
        }
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

    fun speak(text: String, voiceId: String, cfg: TtsConfig, quickStart: Boolean = true, onAudio: (() -> Unit)? = null) {
        startBufferSec = if (quickStart) 0.1 else 0.6
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
        Thread({ download(g, key, text, voiceId, cfg, onAudio) }, "lotus-download").start()
        Thread({ play(g) }, "lotus-play").start()
    }

    fun prefetch(text: String, voiceId: String, cfg: TtsConfig) {
        val key = sha1(Providers.cacheKey(text, voiceId, cfg))
        if (findCached(key) != null) return
        fetch(key, text, voiceId, cfg)
    }

    @Volatile private var prefetchGen = 0

    fun prefetchInOrder(items: List<Pair<String, String>>, cfg: TtsConfig, together: Int = 1) {
        val myGen = ++prefetchGen
        Thread({
            val running = ArrayList<Fetch>()
            for ((text, voiceId) in items) {
                if (prefetchGen != myGen) return@Thread
                val key = sha1(Providers.cacheKey(text, voiceId, cfg))
                if (findCached(key) != null) continue
                running.add(fetch(key, text, voiceId, cfg))
                while (running.size >= together) {
                    val first = running.removeAt(0)
                    synchronized(first.lock) {
                        val until = System.currentTimeMillis() + 60_000
                        while (!first.done && !first.cancelled && System.currentTimeMillis() < until) {
                            first.lock.wait(500)
                        }
                    }
                    if (prefetchGen != myGen) return@Thread
                }
            }
        }, "lotus-prefetch").start()
    }

    fun cancelPrefetches() {
        synchronized(fetches) { fetches.values.forEach { it.cancelled = true } }
    }

    fun stop() {
        cancelPrefetches()
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

    private fun haltTrackLocked() {
        try {
            track?.pause()
            track?.flush()
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
            var total = 0L
            files.forEachIndexed { i, f ->
                total += f.length()
                if (i >= DISK_CACHE_MAX || total > DISK_CACHE_BYTES) f.delete()
            }
        } catch (e: Exception) {
        }
    }

    private fun fetch(key: String, text: String, voiceId: String, cfg: TtsConfig): Fetch {
        synchronized(fetches) {
            fetches[key]?.let { if (!it.cancelled) return it }
            val f = Fetch()
            fetches[key] = f
            Thread({ runFetch(key, f, text, voiceId, cfg) }, "lotus-fetch").start()
            return f
        }
    }

    private fun runFetch(key: String, f: Fetch, text: String, voiceId: String, cfg: TtsConfig) {
        val ctx = appCtx
        var src: AudioSource? = null
        var complete = false
        try {
            if (ctx == null) throw ProviderError("Lotus Aloud wasn't ready - try again.")
            val s = Providers.open(ctx, text, voiceId, cfg)
            src = s
            f.rate = s.rate
            when (s) {
                is AudioSource.Bytes -> f.add(s.data, s.data.size)
                is AudioSource.Stream -> {
                    val chunk = ByteArray(16384)
                    s.input.use { input ->
                        while (true) {
                            if (f.cancelled) return
                            val n = input.read(chunk)
                            if (n < 0) break
                            if (n > 0) f.add(chunk, n)
                        }
                    }
                    complete = true
                }
            }
            val copy = synchronized(f.lock) { f.data.copyOf(f.len) }
            if (copy.size < 2) {
                f.finish("The voice service sent no audio.")
            } else {
                saveToCache(key, f.rate, copy)
                f.finish()
            }
        } catch (e: ProviderError) {
            f.finish(e.message ?: "The voice service had a problem.")
        } catch (e: IOException) {
            f.finish(if (synchronized(f.lock) { f.len } > 1) null else "Couldn't reach the voice service. Check your internet connection.")
        } catch (e: Exception) {
            f.finish("Something went wrong: ${e.message}")
        } finally {
            if (!f.done) f.finish("Stopped.")
            if (!complete) (src as? AudioSource.Stream)?.conn?.disconnect()
            synchronized(fetches) { if (fetches[key] === f) fetches.remove(key) }
        }
    }

    private fun download(g: Int, key: String, text: String, voiceId: String, cfg: TtsConfig, onAudio: (() -> Unit)?) {
        var told = false
        fun audioArrived() {
            if (told) return
            told = true
            try { onAudio?.invoke() } catch (e: Exception) {}
        }

        val cached = if (synchronized(fetches) { fetches.containsKey(key) }) null else findCached(key)
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
                audioArrived()
                return
            } catch (e: Exception) {
            }
        }

        val f = fetch(key, text, voiceId, cfg)
        var copied = 0
        while (true) {
            var newBytes: ByteArray? = null
            var finished = false
            var err: String? = null
            var r = FISH_RATE
            synchronized(f.lock) {
                if (f.len == copied && !f.done) f.lock.wait(40)
                if (f.len > copied) {
                    newBytes = f.data.copyOfRange(copied, f.len)
                    copied = f.len
                }
                finished = f.done && f.len == copied
                err = f.error
                r = f.rate
            }
            val nb = newBytes
            val stillMine = synchronized(lock) {
                if (g != gen) false else {
                    if (nb != null) {
                        if (len == 0) rate = r
                        append(nb, nb.size)
                    }
                    true
                }
            }
            if (!stillMine) return
            if (nb != null) audioArrived()
            if (finished) {
                if (err != null && copied < 2) return fail(g, err!!)
                synchronized(lock) { if (g == gen) done = true }
                return
            }
        }
    }

    private fun play(g: Int) {
        while (true) {
            val st = synchronized(lock) {
                val avail = len - pos
                when {
                    g != gen || phase == Phase.ERROR -> -1
                    avail >= (bps() * startBufferSec * speed).toInt() || (done && avail >= 2) -> 1
                    done -> -1
                    else -> 0
                }
            }
            if (st == -1) return
            if (st == 1) break
            Thread.sleep(20)
        }

        val r = synchronized(lock) { rate }
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
            var rebuffering = false
            outer@ while (true) {
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
                            val avail = (len - pos) and 1.inv()
                            if (rebuffering && !done && avail < (bps() * REBUFFER_SEC * speed).toInt()) {
                                piece = ByteArray(0)
                            } else if (avail <= 0) {
                                if (done) {
                                    finished = true
                                } else {
                                    piece = ByteArray(0)
                                    rebuffering = true
                                    setPhase(Phase.LOADING)
                                }
                            } else {
                                rebuffering = false
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
