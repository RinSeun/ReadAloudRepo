package com.lss.lotusaloud

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

object Translate {
    val LANGS = listOf(
        "Off" to "",
        "English" to TranslateLanguage.ENGLISH,
        "Spanish" to TranslateLanguage.SPANISH,
        "French" to TranslateLanguage.FRENCH,
        "German" to TranslateLanguage.GERMAN,
        "Italian" to TranslateLanguage.ITALIAN,
        "Portuguese" to TranslateLanguage.PORTUGUESE,
        "Japanese" to TranslateLanguage.JAPANESE,
        "Korean" to TranslateLanguage.KOREAN,
        "Chinese" to TranslateLanguage.CHINESE,
        "Russian" to TranslateLanguage.RUSSIAN,
        "Arabic" to TranslateLanguage.ARABIC,
        "Hindi" to TranslateLanguage.HINDI,
        "Vietnamese" to TranslateLanguage.VIETNAMESE,
        "Tagalog" to TranslateLanguage.TAGALOG,
        "Polish" to TranslateLanguage.POLISH,
        "Dutch" to TranslateLanguage.DUTCH,
        "Turkish" to TranslateLanguage.TURKISH,
        "Ukrainian" to TranslateLanguage.UKRAINIAN,
        "Indonesian" to TranslateLanguage.INDONESIAN,
        "Thai" to TranslateLanguage.THAI,
    )

    fun label(code: String): String = LANGS.firstOrNull { it.second == code }?.first ?: code

    fun translate(text: String, target: String, status: (String) -> Unit): String {
        if (target.isBlank()) return text
        val id = LanguageIdentification.getClient()
        val detected = try {
            Tasks.await(id.identifyLanguage(text.take(1500)))
        } catch (e: Exception) {
            "und"
        } finally {
            id.close()
        }
        val source = (if (detected == "und") null else TranslateLanguage.fromLanguageTag(detected)) ?: TranslateLanguage.ENGLISH
        if (source == target) return text

        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        )
        try {
            status("Getting ${label(target)} ready (first time only, about 30 MB)...")
            try {
                Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()))
            } catch (e: Exception) {
                throw ProviderError("Couldn't download ${label(target)} for translating. Check your internet connection and try again.")
            }
            val paras = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
            val out = ArrayList<String>(paras.size)
            paras.forEachIndexed { i, p ->
                status("Translating to ${label(target)}... ${(i + 1) * 100 / paras.size}%")
                out.add(
                    try {
                        Tasks.await(translator.translate(p))
                    } catch (e: Exception) {
                        p
                    }
                )
            }
            return out.joinToString("\n\n")
        } finally {
            translator.close()
        }
    }
}
