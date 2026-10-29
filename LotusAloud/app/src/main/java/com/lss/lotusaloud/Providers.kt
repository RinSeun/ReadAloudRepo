package com.lss.lotusaloud

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Settings a voice service needs for one request. */
data class TtsConfig(val fishKey: String, val elevenKey: String, val fishModel: String, val quality: String) {
    companion object {
        fun from(p: Prefs) = TtsConfig(p.apiKey, p.elevenKey, p.model, p.quality)
    }
}

/** Where the audio for one clip comes from: a live stream, or finished audio. Always 16-bit mono PCM. */
sealed class AudioSource(val rate: Int) {
    class Stream(rate: Int, val input: InputStream, val conn: HttpURLConnection) : AudioSource(rate)
    class Bytes(rate: Int, val data: ByteArray) : AudioSource(rate)
}

class ProviderError(message: String) : Exception(message)

/**
 * The voice services Lotus Aloud can use. A voice ID says which one it belongs to:
 *   "19beff07..."        Fish Audio (no prefix - this is how all older voices are stored)
 *   "eleven:21m00Tcm..."  ElevenLabs
 *   "phone:en-us-x-..."   the phone's built-in voices ("phone:" alone = the phone's default voice)
 */
object Providers {
    const val FISH = "fish"
    const val ELEVEN = "eleven"
    const val PHONE = "phone"

    fun of(voiceId: String): String = when {
        voiceId.startsWith("eleven:") -> ELEVEN
        voiceId.startsWith("phone:") -> PHONE
        else -> FISH
    }

    fun rawId(voiceId: String): String = voiceId.substringAfter(':', voiceId).let { if (of(voiceId) == FISH) voiceId else it }

    fun label(voiceId: String): String = when (of(voiceId)) {
        ELEVEN -> "ElevenLabs"
        PHONE -> "Phone"
        else -> "Fish"
    }

    /** Is the key this voice needs filled in? */
    fun hasKey(voiceId: String, cfg: TtsConfig): Boolean = when (of(voiceId)) {
        ELEVEN -> cfg.elevenKey.isNotBlank()
        PHONE -> true
        else -> cfg.fishKey.isNotBlank()
    }

    fun missingKeyText(voiceId: String): String = when (of(voiceId)) {
        ELEVEN -> "Add your ElevenLabs API key in Lotus Aloud first."
        else -> "Add your Fish Audio API key in Lotus Aloud first."
    }

    private fun elevenModel(quality: String) = if (quality == "fast") "eleven_flash_v2_5" else "eleven_multilingual_v2"

    /** Identifies one exact piece of audio, so we can keep it on the phone and replay it for free. */
    fun cacheKey(text: String, voiceId: String, cfg: TtsConfig): String = when (of(voiceId)) {
        ELEVEN -> "eleven|${elevenModel(cfg.quality)}|$voiceId|$text"
        PHONE -> "phone|$voiceId|$text"
        else -> cfg.fishModel + "|" + SpeechPlayer.buildBody(text, voiceId, cfg.quality).toString()
    }

    /** Starts getting audio for the text. Runs on a background thread. Throws ProviderError with a friendly message. */
    fun open(ctx: Context, text: String, voiceId: String, cfg: TtsConfig): AudioSource = when (of(voiceId)) {
        ELEVEN -> openEleven(text, rawId(voiceId), cfg)
        PHONE -> PhoneTts.synthesize(ctx, text, rawId(voiceId))
        else -> openFish(text, voiceId, cfg)
    }

    // ------------------------------------------------------------------ Fish Audio
    private fun openFish(text: String, voiceId: String, cfg: TtsConfig): AudioSource {
        val body = SpeechPlayer.buildBody(text, voiceId, cfg.quality)
        val c = post(SpeechPlayer.API_URL, body.toString()) {
            it.setRequestProperty("Authorization", "Bearer ${cfg.fishKey.trim()}")
            it.setRequestProperty("model", cfg.fishModel)
        }
        val code = c.responseCode
        if (code != 200) {
            val detail = readError(c)
            c.disconnect()
            throw ProviderError(SpeechPlayer.errorText(code, detail, cfg.fishModel))
        }
        return AudioSource.Stream(SpeechPlayer.FISH_RATE, c.inputStream, c)
    }

    // ------------------------------------------------------------------ ElevenLabs
    private const val ELEVEN_RATE = 24000 // raw PCM at 24 kHz works on every ElevenLabs plan

    private fun openEleven(text: String, voice: String, cfg: TtsConfig): AudioSource {
        val body = JSONObject().put("text", text).put("model_id", elevenModel(cfg.quality))
        val url = "https://api.elevenlabs.io/v1/text-to-speech/$voice/stream?output_format=pcm_$ELEVEN_RATE"
        val c = post(url, body.toString()) { it.setRequestProperty("xi-api-key", cfg.elevenKey.trim()) }
        val code = c.responseCode
        if (code != 200) {
            val detail = readError(c)
            c.disconnect()
            throw ProviderError(
                when {
                    code == 401 -> if (detail.contains("quota", true)) "ElevenLabs: you've used up your characters for this month."
                    else "ElevenLabs rejected your API key. Re-copy it from elevenlabs.io."
                    code == 402 || detail.contains("quota", true) -> "ElevenLabs: you've used up your characters for this month."
                    code == 404 || detail.contains("voice_not_found", true) -> "ElevenLabs couldn't find that voice."
                    else -> "ElevenLabs error $code. ${detail.take(160)}"
                }
            )
        }
        return AudioSource.Stream(ELEVEN_RATE, c.inputStream, c)
    }

    /** Your ElevenLabs voices (name to ID). */
    fun elevenVoices(key: String): List<Pair<String, String>> {
        val c = get("https://api.elevenlabs.io/v1/voices") { it.setRequestProperty("xi-api-key", key.trim()) }
        try {
            if (c.responseCode == 401) throw ProviderError("ElevenLabs rejected your API key.")
            if (c.responseCode != 200) throw ProviderError("ElevenLabs error ${c.responseCode}.")
            val arr = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("voices")
                ?: return emptyList()
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.optString("name", "Voice") to o.optString("voice_id")
            }.filter { it.second.isNotBlank() }.sortedBy { it.first.lowercase() }
        } finally {
            c.disconnect()
        }
    }

    /** Characters left this month on ElevenLabs. */
    fun elevenCharactersLeft(key: String): Long {
        val c = get("https://api.elevenlabs.io/v1/user/subscription") { it.setRequestProperty("xi-api-key", key.trim()) }
        try {
            if (c.responseCode == 401) throw ProviderError("Key not accepted")
            if (c.responseCode != 200) throw ProviderError("Couldn't check (${c.responseCode})")
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            return (o.optLong("character_limit") - o.optLong("character_count")).coerceAtLeast(0)
        } finally {
            c.disconnect()
        }
    }

    // ------------------------------------------------------------------ http helpers
    private fun post(url: String, json: String, headers: (HttpURLConnection) -> Unit): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15000
        c.readTimeout = 60000
        c.setRequestProperty("Content-Type", "application/json")
        headers(c)
        c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return c
    }

    private fun get(url: String, headers: (HttpURLConnection) -> Unit): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        headers(c)
        return c
    }

    private fun readError(c: HttpURLConnection): String = try {
        c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
    } catch (e: Exception) {
        ""
    }
}

/** The phone's own text-to-speech voices (free, offline). */
object PhoneTts {
    private var tts: TextToSpeech? = null
    private val useLock = Object()

    /** Starts the phone's speech engine (once) and waits until it's ready. */
    private fun engine(ctx: Context): TextToSpeech {
        tts?.let { return it }
        val ready = CountDownLatch(1)
        var ok = false
        var created: TextToSpeech? = null
        Handler(Looper.getMainLooper()).post {
            created = TextToSpeech(ctx.applicationContext) { status ->
                ok = status == TextToSpeech.SUCCESS
                ready.countDown()
            }
        }
        if (!ready.await(10, TimeUnit.SECONDS) || !ok) {
            throw ProviderError("Your phone's speech engine isn't available. Check Settings → Text-to-speech.")
        }
        tts = created
        return created!!
    }

    /** Installed voices: (display name, voice name). */
    fun voices(ctx: Context): List<Pair<String, String>> {
        val e = engine(ctx)
        return (e.voices ?: emptySet())
            .filter { !it.isNetworkConnectionRequired }
            .sortedWith(compareBy({ it.locale.displayLanguage }, { it.name }))
            .map { v ->
                val quality = if (v.quality >= android.speech.tts.Voice.QUALITY_VERY_HIGH) " ★" else ""
                "${v.locale.displayName} · ${v.name.substringAfterLast('-')}$quality" to v.name
            }
    }

    fun synthesize(ctx: Context, text: String, voiceName: String): AudioSource = synchronized(useLock) {
        val e = engine(ctx)
        if (voiceName.isNotBlank()) {
            e.voices?.firstOrNull { it.name == voiceName }?.let { e.voice = it }
        } else {
            e.defaultVoice?.let { e.voice = it }
        }
        e.setSpeechRate(1f) // speed is applied by our player
        val out = File(ctx.cacheDir, "phone_tts.wav")
        out.delete()
        val done = CountDownLatch(1)
        var failed = false
        val id = "lotus-${System.nanoTime()}"
        e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == id) done.countDown() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (utteranceId == id) { failed = true; done.countDown() } }
        })
        if (e.synthesizeToFile(text, null, out, id) != TextToSpeech.SUCCESS) {
            throw ProviderError("Your phone's voice couldn't read that.")
        }
        if (!done.await(90, TimeUnit.SECONDS) || failed || !out.exists()) {
            throw ProviderError("Your phone's voice couldn't read that.")
        }
        return Wav.read(out.readBytes()).also { out.delete() }
    }
}

/** Reads a WAV file into 16-bit mono PCM. */
object Wav {
    fun read(b: ByteArray): AudioSource.Bytes {
        fun u16(i: Int) = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)
        fun u32(i: Int) = u16(i) or (u16(i + 2) shl 16)
        if (b.size < 44 || String(b, 0, 4) != "RIFF") throw ProviderError("Your phone's voice made an unreadable file.")
        var p = 12
        var rate = 22050
        var channels = 1
        var bits = 16
        while (p + 8 <= b.size) {
            val id = String(b, p, 4)
            val size = u32(p + 4)
            if (id == "fmt ") {
                channels = u16(p + 10)
                rate = u32(p + 12)
                bits = u16(p + 22)
            } else if (id == "data") {
                val end = minOf(b.size, p + 8 + size)
                val raw = b.copyOfRange(p + 8, end)
                return AudioSource.Bytes(rate, toMono16(raw, channels, bits))
            }
            p += 8 + size + (size and 1)
        }
        throw ProviderError("Your phone's voice made an unreadable file.")
    }

    private fun toMono16(raw: ByteArray, channels: Int, bits: Int): ByteArray {
        if (channels == 1 && bits == 16) return raw
        val inBytes = bits / 8
        val frames = raw.size / (inBytes * channels)
        val out = ByteArray(frames * 2)
        for (f in 0 until frames) {
            var sum = 0
            for (ch in 0 until channels) {
                val i = (f * channels + ch) * inBytes
                sum += if (bits == 8) ((raw[i].toInt() and 0xff) - 128) shl 8
                else (raw[i].toInt() and 0xff) or (raw[i + 1].toInt() shl 8)
            }
            val v = (sum / channels).coerceIn(-32768, 32767)
            out[f * 2] = (v and 0xff).toByte()
            out[f * 2 + 1] = ((v shr 8) and 0xff).toByte()
        }
        return out
    }
}
