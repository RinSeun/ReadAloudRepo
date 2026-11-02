package com.lss.lotusaloud

import android.content.Context

object LangVoices {
    /**
     * Which voice should read text in [lang]:
     * 1. the voice you picked for that language, if any;
     * 2. if your main voice is a phone voice that speaks another language, the phone's voice for [lang];
     * 3. otherwise null (= your main voice; Fish and ElevenLabs voices speak many languages).
     * Runs on a background thread.
     */
    fun pick(ctx: Context, prefs: Prefs, lang: String): String? {
        if (lang.isBlank()) return null
        prefs.langVoice(lang)?.let { id ->
            if (id.startsWith("phone:@") || prefs.voices.any { it.id == id }) return id
        }
        val main = prefs.mainVoice
        if (Providers.of(main) == Providers.PHONE) {
            val mainLang = try { PhoneTts.languageOf(ctx, Providers.rawId(main)) } catch (e: Exception) { return null }
            if (mainLang != lang) return "phone:@$lang"
        }
        return null
    }
}
