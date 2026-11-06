package com.lss.lotusaloud

import android.content.Context

object LangVoices {
    fun pick(ctx: Context, prefs: Prefs, lang: String): String? {
        if (lang.isBlank()) return null
        prefs.langVoice(lang)?.let { id ->
            if (id.startsWith("phone:@") || prefs.voices.any { it.id == id }) return id
        }
        val main = prefs.mainVoice
        if (Providers.of(main) == Providers.PHONE) {
            val mainLang = try { PhoneTts.languageOf(ctx, Providers.rawId(main)) } catch (e: Exception) { return null }
            if (mainLang != PhoneTts.sameLang(lang)) return "phone:@$lang"
        }
        return null
    }
}
