package com.lss.lotusaloud

object Moods {
    val LIST = listOf(
        "Normal" to "",
        "Calm" to "calm",
        "Happy" to "happy",
        "Excited" to "excited",
        "Confident" to "confident",
        "Soft" to "soft tone",
        "Whispering" to "whispering",
        "Sad" to "sad",
        "Nervous" to "nervous",
        "Angry" to "angry",
        "Sarcastic" to "sarcastic",
        "Shouting" to "shouting",
        "Scared" to "scared",
        "Curious" to "curious",
    )

    fun label(cue: String): String = LIST.firstOrNull { it.second == cue }?.first ?: cue.replaceFirstChar { it.uppercase() }

    private val SENTENCE_END = Regex("(?<=[.!?\\u2026][\"')\\]]?)\\s+")
    private val CUE_CHARS = Regex("[\\[\\]()]")

    fun tag(text: String, cue: String, model: String): String {
        val c = cue.replace(CUE_CHARS, "").trim()
        if (c.isEmpty()) return text
        val open = if (model.trim().lowercase().startsWith("s1")) "($c) " else "[$c] "
        return text.split("\n\n").joinToString("\n\n") { para ->
            para.split(SENTENCE_END).filter { it.isNotBlank() }.joinToString(" ") { open + it.trim() }
        }
    }
}
