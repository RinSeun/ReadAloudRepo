package com.lss.lotusaloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Voice(val name: String, val id: String)

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("fish_reader", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) { sp.edit().putString("apiKey", v.trim()).apply() }

    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) { sp.edit().putString("model", v.trim().ifEmpty { DEFAULT_MODEL }).apply() }

    var quality: String
        get() = sp.getString("quality", "fast") ?: "fast"
        set(v) { sp.edit().putString("quality", v).apply() }

    var mood: String
        get() = sp.getString("mood", "") ?: ""
        set(v) { sp.edit().putString("mood", v.trim()).apply() }

    var translateTo: String
        get() = sp.getString("translateTo", "") ?: ""
        set(v) {
            sp.edit().putString("translateTo", v).apply()
            if (v.isNotBlank()) {
                val recent = (listOf(v) + recentLangs.filter { it != v }).take(3)
                sp.edit().putString("recentLangs", recent.joinToString(",")).apply()
            }
        }

    /** Which voice reads each language ("ko" -> voice id). Missing = automatic. */
    fun langVoice(lang: String): String? = try {
        JSONObject(sp.getString("langVoices", "{}") ?: "{}").optString(lang, "").ifBlank { null }
    } catch (e: Exception) { null }

    fun setLangVoice(lang: String, voiceId: String?) {
        val o = try { JSONObject(sp.getString("langVoices", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
        if (voiceId.isNullOrBlank()) o.remove(lang) else o.put(lang, voiceId)
        sp.edit().putString("langVoices", o.toString()).apply()
    }

    val hasLangVoices: Boolean
        get() = try { JSONObject(sp.getString("langVoices", "{}") ?: "{}").length() > 0 } catch (e: Exception) { false }

    val recentLangs: List<String>
        get() = (sp.getString("recentLangs", "") ?: "").split(",").filter { it.isNotBlank() }

    var speed: Float
        get() = sp.getFloat("speed", 1f)
        set(v) { sp.edit().putFloat("speed", v.coerceIn(0.5f, 2f)).apply() }

    var voices: List<Voice>
        get() {
            val raw = sp.getString("voices", null) ?: return firstVoices()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Voice(o.optString("name", "Voice ${i + 1}"), o.optString("id"))
                }.filter { it.id.isNotBlank() }
            } catch (e: Exception) {
                listOf(Voice(Edition.DEFAULT_VOICE_NAME, DEFAULT_VOICE))
            }
        }
        set(list) {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("name", it.name).put("id", it.id)) }
            sp.edit().putString("voices", arr.toString()).apply()
        }

    /**
     * Fresh installs start with the phone's free voice. Someone updating from a version that came
     * with the built-in Fish voice (and who has a Fish key) keeps it, so nothing changes under them.
     */
    private fun firstVoices(): List<Voice> {
        val list = if (!Edition.PLAY && apiKey.isNotBlank())
            listOf(Voice("My voice", LEGACY_VOICE))
        else
            listOf(Voice(Edition.DEFAULT_VOICE_NAME, DEFAULT_VOICE))
        voices = list
        if (!sp.contains("mainVoice")) mainVoice = list.first().id
        return list
    }

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

    var shotsOn: Boolean
        get() = sp.getBoolean("shotsOn", true)
        set(v) { sp.edit().putBoolean("shotsOn", v).apply() }

    var elevenKey: String
        get() = sp.getString("elevenKey", "") ?: ""
        set(v) { sp.edit().putString("elevenKey", v.trim()).apply() }

    fun hasKeyFor(voiceId: String = mainVoice): Boolean = Providers.hasKey(voiceId, TtsConfig.from(this))

    var seenChanges: Int
        get() = sp.getInt("seenChanges", 0)
        set(v) { sp.edit().putInt("seenChanges", v).apply() }

    companion object {
        const val RELEASES_URL = "https://github.com/RinSeun/ReadAloudRepo/releases"
        val DEFAULT_VOICE: String get() = Edition.DEFAULT_VOICE
        const val LEGACY_VOICE = "19beff0760174a6e92a34eafbfbeada7"
        const val DEFAULT_MODEL = "s2.1-pro"
        val MODELS = listOf("s2.1-pro", "s2.1-pro-free", "s2-pro", "s1")

        private val VOICE_ID = Regex("[0-9a-fA-F]{32}")

        fun extractVoiceId(s: String): String = VOICE_ID.find(s)?.value?.lowercase() ?: s.trim()

        fun isVoiceId(s: String): Boolean = VOICE_ID.matches(s)
    }
}
