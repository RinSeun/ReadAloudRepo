package com.lss.lotusaloud

/**
 * The "What's new" notes shown in the app. Newest first.
 * When you add a feature, add a new entry at the top - the pop-up then shows once after that update.
 */
object Changelog {
    data class Entry(val title: String, val items: List<String>)

    val ENTRIES = listOf(
        Entry(
            "\uD83E\uDEB7 Big lotus & Fish credit",
            listOf(
                "A big glowing lotus at the top - it gently breathes while reading. Tap it to follow along.",
                "Your Fish Audio credit balance is shown under it, with a warning when it gets low. Tap it to refresh.",
                "The \u201c\u2728 New\u201d button got a fresh look.",
            ),
        ),
        Entry(
            "\uD83D\uDCD6 Follow along, links & long reads",
            listOf(
                "Follow-along screen: see the text as it's read, with the current sentence highlighted. Tap any sentence to jump there.",
                "Open it by tapping the notification, or \u201cFollow along\u201d in the app.",
                "Share or paste a webpage link and it reads the article - no menus or ads.",
                "Long text is split into parts: it starts faster, there are no gaps, and you can skip part by part (also with earbud double-tap).",
            ),
        ),
        Entry(
            "\uD83C\uDFA8 New look + screenshot double-tap",
            listOf(
                "A new dark lotus design with your logo at the top.",
                "Double-tap the lotus bubble to read your latest screenshot - no Share menu needed.",
                "Tap \u201cAllow double-tap screenshot reading\u201d once to turn it on.",
            ),
        ),
        Entry(
            "\uD83E\uDEB7 Now called Lotus Aloud",
            listOf(
                "New name: Lotus Aloud. The Quick Settings tile is now \u201cLotus Read\u201d.",
                "The app icon is now the Lotus Shield Studio logo.",
                "The floating bubble and notifications use it too.",
                "Works with Android's themed icons.",
            ),
        ),
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
                "Take a screenshot → Share → Lotus Aloud, and it reads the text in it.",
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
                "Floating lotus bubble: copy text in any app (Opera, Messenger...), tap the bubble to read it.",
                "Lock-screen, earbud, Bluetooth and car controls.",
                "Skip back / forward 10 seconds.",
                "Speed changes instantly, even while reading.",
                "Recent reads - replay any of them instantly and free.",
            ),
        ),
        Entry(
            "⬇ Quick Settings tile",
            listOf("“Lotus Read” tile: copy text, swipe down, tap it to read."),
        ),
        Entry(
            "🎉 First version",
            listOf(
                "Highlight text in any app → read it in your Fish voice.",
                "Share text to Lotus Aloud.",
                "Your voices, main voice, model, quality and speed settings.",
            ),
        ),
    )
}
