package com.lss.lotusaloud

object Changelog {
    data class Entry(val title: String, val items: List<String>)

    val ENTRIES = listOf(
        Entry(
            "\uD83C\uDF0D Switch languages in one tap",
            listOf(
                "Language pills under the lotus on the Player tab: tap Original, a recent language, or + More.",
                "Switch while something is playing and it carries on in the new language from about the same spot.",
                "A fresh look for this What's new screen, with an Updates card to grab the latest version.",
                "Recent reads keep just your last 10, and saved audio never takes more than 40 MB. Settings \u2192 Clear recent reads empties it any time.",
                "New installs start with your phone's free voice, so friends can try it straight away. Your own voices stay as they are.",
            ),
        ),
        Entry(
            "\uD83C\uDF0D Translate & read",
            listOf(
                "Settings \u2192 Translate to: pick a language and everything you read is translated first, then read out loud.",
                "Translation happens on your phone: free, no credit, and offline after a one-time download per language (about 30 MB).",
                "It works out what language the text is in by itself, and skips translating if it's already in your language.",
                "Fish and ElevenLabs voices can speak other languages, so you can hear your own voice in Spanish, Japanese and more.",
            ),
        ),
        Entry(
            "\uD83D\uDCC4 Documents & \uD83C\uDFAD moods",
            listOf(
                "Read PDFs, Word (.docx) and text files: share one to Lotus Aloud, open a PDF with it, or tap \u201cRead a document\u201d in the app.",
                "Scanned PDFs work too: pages are read on your phone (free, no credit), and page numbers and headers are skipped.",
                "Moods for Fish voices: Settings \u2192 Mood. Calm, happy, excited, whispering, sad, sarcastic and more, or type your own.",
                "Parts are now a bit smaller later on, so long reads keep up better.",
            ),
        ),
        Entry(
            "\u26A1 Starts reading faster, no more waiting between parts",
            listOf(
                "No more stuttering: if the voice service falls behind, it pauses once to build a cushion instead of choppy start-stop audio.",
                "Stays three parts ahead on long reads.",
                "If Fish says \u201ctoo many requests at once\u201d (common on free accounts), it waits a moment and tries again instead of stopping.",
                "Fixed: long reads sometimes paused between parts. The next part now starts playing even if it's still being made, and the app stays two parts ahead.",
                "Parts grow more gently (one or two sentences first, then a bit more each time), so the next one is ready in time.",
                "Audio starts sooner: the first bit read is now just a sentence or two, and the rest is prepared while you listen.",
                "The next part is only prepared once the current one is playing, so it doesn't slow the start down.",
                "Lotus Aloud gets the connection ready the moment you touch the bubble, tap the tile or open the app.",
                "Playback starts as soon as a tenth of a second of audio is ready.",
                "\u201cFast\u201d start is now the default (Settings \u2192 Start speed vs. quality). Pick Balanced or Best if you prefer.",
            ),
        ),
        Entry(
            "\u2728 New Voices & Settings look",
            listOf(
                "Voices: your main voice up top, coloured badges per service, tap \u2605 to make a voice main, hold one to remove it.",
                "Connected services show a green dot once your key is in - tap to add or change a key.",
                "Settings: big title, clean grouped rows, a big speed number with \u2212 / + buttons, and real on/off switches.",
                "You can now turn the screenshot double-tap off if you don't want it.",
            ),
        ),
        Entry(
            "\uD83D\uDDC2 Tabs",
            listOf(
                "The app now has tabs at the bottom: Player, Voices and Settings - no more long scrolling page.",
                "The floating bubble uses less battery when nothing is playing.",
            ),
        ),
        Entry(
            "\uD83C\uDF99 More voices: ElevenLabs & your phone",
            listOf(
                "Add voices from ElevenLabs (paste your ElevenLabs key, then pick from your voices).",
                "Or use your phone's own voices - free, offline and instant. Handy when credit runs out.",
                "Mix them freely: each voice remembers its service, and everything (follow along, parts, speed) works with all of them.",
                "The top shows the right balance for your main voice: Fish credit or ElevenLabs characters left.",
            ),
        ),
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
