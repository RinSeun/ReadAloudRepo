package com.lss.fishreader

/**
 * The "What's new" notes shown in the app. Newest first.
 * When you add a feature, add a new entry at the top - the pop-up then shows once after that update.
 */
object Changelog {
    data class Entry(val title: String, val items: List<String>)

    val ENTRIES = listOf(
        Entry(
            "✨ What's new button",
            listOf(
                "This pop-up! Tap “What's new” at the top of the app any time.",
                "It shows once by itself after each update.",
                "“Get latest version” opens your GitHub Releases page.",
            ),
        ),
        Entry(
            "📷 Read screenshots & photos",
            listOf(
                "Take a screenshot → Share → Fish Reader, and it reads the text in it.",
                "Works with photos and several screenshots at once.",
                "“Read a screenshot or photo” button in the app.",
                "Text is found on your phone - free, no Fish credit used.",
            ),
        ),
        Entry(
            "🔤 Renamed to “Read audio”",
            listOf("Our option in the highlight menu is now “Read audio”, so it isn't mixed up with Claude's or Android's “Read aloud”."),
        ),
        Entry(
            "🐟 Floating bubble, media controls & more",
            listOf(
                "Floating fish bubble: copy text in any app (Opera, Messenger...), tap the bubble to read it.",
                "Lock-screen, earbud, Bluetooth and car controls.",
                "Skip back / forward 10 seconds.",
                "Speed changes instantly, even while reading.",
                "Recent reads - replay any of them instantly and free.",
            ),
        ),
        Entry(
            "⬇ Quick Settings tile",
            listOf("“Fish Read” tile: copy text, swipe down, tap it to read."),
        ),
        Entry(
            "🎉 First version",
            listOf(
                "Highlight text in any app → read it in your Fish voice.",
                "Share text to Fish Reader.",
                "Your voices, main voice, model, quality and speed settings.",
            ),
        ),
    )
}
