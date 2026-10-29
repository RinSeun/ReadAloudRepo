package com.lss.lotusaloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Voice(val name: String, val id: String)

/** All settings, stored on the phone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fish_reader", Context.MODE_PRIVATE) // old name kept so your settings carry over

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) { sp.edit().putString("apiKey", v.trim()).apply() }

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) { sp.edit().putString("model", v.trim().ifEmpty { DEFAULT_MODEL }).apply() }

    /** fast | balanced | quality */
    var quality: String
        get() = sp.getString("quality", "balanced") ?: "balanced"
        set(v) { sp.edit().putString("quality", v).apply() }

    var speed: Float
        get() = sp.getFloat("speed", 1f)
        set(v) { sp.edit().putFloat("speed", v.coerceIn(0.5f, 2f)).apply() }

    var voices: List<Voice>
        get() {
            val raw = sp.getString("voices", null) ?: return listOf(Voice("My voice", DEFAULT_VOICE))
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Voice(o.optString("name", "Voice ${i + 1}"), o.optString("id"))
                }.filter { it.id.isNotBlank() }
            } catch (e: Exception) {
                listOf(Voice("My voice", DEFAULT_VOICE))
            }
        }
        set(list) {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("name", it.name).put("id", it.id)) }
            sp.edit().putString("voices", arr.toString()).apply()
        }

    /** The voice used by "Read audio". Falls back to the first saved voice. */
    var mainVoice: String
        get() {
            val list = voices
            val v = sp.getString("mainVoice", DEFAULT_VOICE) ?: DEFAULT_VOICE
            return if (list.any { it.id == v }) v else list.firstOrNull()?.id ?: ""
        }
        set(v) { sp.edit().putString("mainVoice", v).apply() }

    var bubbleOn: Boolean
        get() = sp.getBoolean("bubbleOn", false)
        set(v) { sp.edit().putBoolean("bubbleOn", v).apply() }

    var bubbleX: Int
        get() = sp.getInt("bubbleX", -1)
        set(v) { sp.edit().putInt("bubbleX", v).apply() }

    var bubbleY: Int
        get() = sp.getInt("bubbleY", -1)
        set(v) { sp.edit().putInt("bubbleY", v).apply() }

    /** Double-tapping the bubble reads your latest screenshot. */
    var shotsOn: Boolean
        get() = sp.getBoolean("shotsOn", true)
        set(v) { sp.edit().putBoolean("shotsOn", v).apply() }

    var elevenKey: String
        get() = sp.getString("elevenKey", "") ?: ""
        set(v) { sp.edit().putString("elevenKey", v.trim()).apply() }

    /** Is the key for this voice (default: your main voice) filled in? */
    fun hasKeyFor(voiceId: String = mainVoice): Boolean = Providers.hasKey(voiceId, TtsConfig.from(this))

    /** How many "What's new" entries you've already seen (so the pop-up shows once per update). */
    var seenChanges: Int
        get() = sp.getInt("seenChanges", 0)
        set(v) { sp.edit().putInt("seenChanges", v).apply() }

    companion object {
        const val RELEASES_URL = "https://github.com/RinSeun/ReadAloudRepo/releases"
        const val DEFAULT_VOICE = "19beff0760174a6e92a34eafbfbeada7"
        const val DEFAULT_MODEL = "s2.1-pro"
        val MODELS = listOf("s2.1-pro", "s2.1-pro-free", "s2-pro", "s1")

        private val VOICE_ID = Regex("[0-9a-fA-F]{32}")

        /** Accepts a fish.audio voice link or a bare ID. */
        fun extractVoiceId(s: String): String = VOICE_ID.find(s)?.value?.lowercase() ?: s.trim()

        fun isVoiceId(s: String): Boolean = VOICE_ID.matches(s)
    }
}
