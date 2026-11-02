package com.lss.lotusaloud

object ReadSession {
    @Volatile var title: String = ""
    @Volatile var parts: List<String> = emptyList()
    @Volatile var index: Int = 0
    @Volatile var voice: String = ""
    @Volatile var original: String = ""
    @Volatile var originalTitle: String? = null
    @Volatile var chosenVoice: String? = null

    @Volatile var pendingFraction: Double? = null

    /** The Saved Read being played (its spot is remembered), or null. */
    @Volatile var savedId: String? = null

    @Volatile var version: Int = 0

    val count get() = parts.size
    val hasNext get() = index + 1 < parts.size
    val hasPrev get() = index > 0

    fun start(newTitle: String, newParts: List<String>, newVoice: String) {
        title = newTitle
        parts = newParts
        voice = newVoice
        index = 0
        pendingFraction = null
        version++
    }
}
