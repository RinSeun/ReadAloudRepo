package com.lss.lotusaloud

/** What's being read right now: the parts and which one is playing (shared with the follow-along screen). */
object ReadSession {
    @Volatile var title: String = ""
    @Volatile var parts: List<String> = emptyList()
    @Volatile var index: Int = 0
    @Volatile var voice: String = ""
    /** Jump target inside the current part (0..1), applied once its length is known. */
    @Volatile var pendingFraction: Double? = null
    /** Bumps whenever a new text starts, so screens know to rebuild. */
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
