package com.lss.fishreader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Voice(val name: String, val id: String)

/** All settings, stored on the phone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fish_reader", Context.MODE_PRIVATE)

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

    /** The voice used by "Read aloud". Falls back to the first saved voice. */
    var mainVoice: String
        get() {
            val list = voices
            val v = sp.getString("mainVoice", DEFAULT_VOICE) ?: DEFAULT_VOICE
            return if (list.any { it.id == v }) v else list.firstOrNull()?.id ?: ""
        }
        set(v) { sp.edit().putString("mainVoice", v).apply() }

    companion object {
        const val DEFAULT_VOICE = "19beff0760174a6e92a34eafbfbeada7"
        const val DEFAULT_MODEL = "s2.1-pro"
        val MODELS = listOf("s2.1-pro", "s2.1-pro-free", "s2-pro", "s1")

        private val VOICE_ID = Regex("[0-9a-fA-F]{32}")

        /** Accepts a fish.audio voice link or a bare ID. */
        fun extractVoiceId(s: String): String = VOICE_ID.find(s)?.value?.lowercase() ?: s.trim()

        fun isVoiceId(s: String): Boolean = VOICE_ID.matches(s)
    }
}
