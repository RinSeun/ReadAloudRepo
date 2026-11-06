package com.lss.lotusaloud

object Changelog {
    data class Entry(val title: String, val items: List<String>)

    val ENTRIES = listOf(
        Entry(
            "Better Tagalog",
            listOf(
                "Tagalog now uses your phone's Filipino voice, so it sounds natural instead of being read with an English voice.",
                "Tip: find Tagalog under + More in the language pills. If your phone has no Filipino voice yet, install it in Settings → Text-to-speech → Install voice data → Filipino.",
            ),
        ),
        Entry(
            "Terms and privacy, up front",
            listOf(
                "The first time you open Lotus Aloud, a short welcome screen explains how your text and voices are handled, with links to the Terms of Use and Privacy Policy.",
                "Only use voices you own or have permission to use. This reminder now also appears under Add a voice.",
            ),
        ),
        Entry(
            "Fewer pauses between parts",
            listOf(
                "Lotus Aloud now gets the very next part ready first, then the ones after it, instead of all at once. The next part arrives sooner, so there's less waiting.",
                "Fewer “too many requests” slowdowns on Fish Audio's free model.",
            ),
        ),
        Entry(
            "Know where your text goes",
            listOf(
                "Voices now show CLOUD (Fish Audio, ElevenLabs) or PRIVATE (phone voices), so you always know if your text leaves your phone.",
                "Adding a Fish Audio or ElevenLabs key now explains how to keep it safe and what gets sent.",
                "Settings → Help → Cloud voices and your privacy, any time you want to read it again.",
                "Terms of use are now in Settings → Help.",
            ),
        ),
        Entry(
            "Lotus bubble shows it's working",
            listOf(
                "When you tap the lotus bubble, it gives a little bounce and a spinning ring appears while your text, webpage or screenshot is being prepared. No more wondering if it heard you.",
                "Tapping the bubble again while it's getting ready tells you what it's doing, instead of starting over.",
                "Links that start with http:// now open safely over https.",
                "If a webpage can't be opened, Lotus Aloud tells you, instead of reading the error page out loud.",
            ),
        ),
        Entry(
            "Multi-voice & a simpler app",
            listOf(
                "Multi-voice: tap Voices on a book in your Library. Lotus Aloud finds the narrator and each character, and you pick a voice for each one, like a real audiobook.",
                "Rename characters, join two names that are the same person, or give their lines to the narrator. Tap the play button to hear how each one sounds.",
                "Three tabs now: Listen, Library and Settings. Your voices are at the top of Settings.",
                "Listen keeps just the essentials: paste and Read, Document, Photo. Now playing only shows while something is playing.",
                "Library holds your saved documents and recent reads together.",
                "Settings shows the basics first (voices, speed, language, theme). Everything else is under More settings.",
            ),
        ),
        Entry(
            "Saved Reads, your way",
            listOf(
                "Saved Reads now only keeps documents you add yourself: tap Add a document on the Saved tab, or Read a document on the Player tab.",
                "Webpages, pasted text and files shared from other apps are no longer saved automatically.",
                "Shared a file you already saved? It still carries on from your spot.",
                "Each saved read now has its own Play, Restart and More buttons. Play turns into Pause while it reads.",
                "The progress bar moves while you listen, and file names look tidier (no underscores).",
            ),
        ),
        Entry(
            "Webpages that wouldn't open",
            listOf(
                "Sites that turned Lotus Aloud away (“error 403”) now open in a hidden browser view on your phone, the same way they load when you visit them.",
                "If a site still won't allow it, you get a clear tip instead of an error number.",
                "You can type a site without https:// (for example gamestop.com).",
            ),
        ),
        Entry(
            "A cleaner look",
            listOf(
                "Emoji are gone from buttons, tabs, menus and cards. Everything is plain, easy-to-read text, with your theme color.",
                "New play, pause and skip buttons that match your theme.",
                "Saved Reads show a small PDF, BOOK, WORD or WEB label.",
                "Voices: tap Set main to choose your main voice.",
                "The glow now sits only behind the lotus.",
            ),
        ),
        Entry(
            "Saved Reads & themes",
            listOf(
                "New Saved tab: your documents, books, articles and long reads are kept on a shelf, and Lotus Aloud remembers where you stopped. Tap Play to carry on, or tap a read to start over, rename it, mark it finished or remove it.",
                "Open the same document again and it carries on from your spot.",
                "EPUB books now work (books without copy protection), along with PDF, Word and text files.",
                "Only the text and your spot are kept, never the audio, so Saved Reads stays small.",
                "Themes (Settings → Look): Lotus, Ocean, Jade, Violet, Sunset, Gold and Sakura, plus a pure black background for OLED screens.",
            ),
        ),
        Entry(
            "Smart skipping & pronunciation",
            listOf(
                "Smart skipping (Settings → Cleaner reading): skips headers and footers that repeat on every page, page numbers, citations like [12] or (Smith et al., 2020), long links, DOIs and email addresses, and the reference list at the end. Tap it to choose what gets skipped.",
                "Pronunciation: teach Lotus Aloud how to say names and words it gets wrong (LSS → L S S, Nguyen → Win). Works with every voice, and Follow along still shows the original word.",
            ),
        ),
        Entry(
            "Better pronunciation in other languages",
            listOf(
                "Phone voices now switch to the right language by themselves: Korean text is read by your phone's Korean voice, not the English one.",
                "Pick a voice for each language: hold a language pill (or Settings → Voice for each language). A native voice sounds far better than an English voice speaking Korean.",
                "Works without translating too: Lotus Aloud spots the language of what you're reading and uses that language's voice.",
                "Fixed: short phrases translated oddly (“Hello” became the phone-call hello 여보세요 in Korean). Common greetings and thanks are now always right in all 19 translation languages, and other short text is translated as a proper sentence.",
                "Korean, Japanese and Chinese text is now split into sentences properly, so it starts faster and Follow along highlights the right sentence.",
            ),
        ),
        Entry(
            "Switch languages in one tap",
            listOf(
                "Language pills under the lotus on the Player tab: tap Original, a recent language, or + More.",
                "Switch while something is playing and it carries on in the new language from about the same spot.",
                "A fresh look for this What's new screen, with an Updates card to grab the latest version.",
                "Fixed: the top of the screen could flash black for a moment when tapping the lotus bubble, the Lotus Read tile or “Read audio”.",
                "Recent reads keep just your last 10, and saved audio never takes more than 40 MB. Settings → Clear recent reads empties it any time.",
                "New installs start with your phone's free voice, so friends can try it straight away. Your own voices stay as they are.",
            ),
        ),
        Entry(
            "Translate & read",
            listOf(
                "Settings → Translate to: pick a language and everything you read is translated first, then read out loud.",
                "Translation happens on your phone: free, no credit, and offline after a one-time download per language (about 30 MB).",
                "It works out what language the text is in by itself, and skips translating if it's already in your language.",
                "Fish and ElevenLabs voices can speak other languages, so you can hear your own voice in Spanish, Japanese and more.",
            ),
        ),
        Entry(
            "Documents & moods",
            listOf(
                "Read PDFs, Word (.docx) and text files: share one to Lotus Aloud, open a PDF with it, or tap “Read a document” in the app.",
                "Scanned PDFs work too: pages are read on your phone (free, no credit), and page numbers and headers are skipped.",
                "Moods for Fish voices: Settings → Mood. Calm, happy, excited, whispering, sad, sarcastic and more, or type your own.",
                "Parts are now a bit smaller later on, so long reads keep up better.",
            ),
        ),
        Entry(
            "Starts reading faster, no more waiting between parts",
            listOf(
                "No more stuttering: if the voice service falls behind, it pauses once to build a cushion instead of choppy start-stop audio.",
                "Stays three parts ahead on long reads.",
                "If Fish says “too many requests at once” (common on free accounts), it waits a moment and tries again instead of stopping.",
                "Fixed: long reads sometimes paused between parts. The next part now starts playing even if it's still being made, and the app stays two parts ahead.",
                "Parts grow more gently (one or two sentences first, then a bit more each time), so the next one is ready in time.",
                "Audio starts sooner: the first bit read is now just a sentence or two, and the rest is prepared while you listen.",
                "The next part is only prepared once the current one is playing, so it doesn't slow the start down.",
                "Lotus Aloud gets the connection ready the moment you touch the bubble, tap the tile or open the app.",
                "Playback starts as soon as a tenth of a second of audio is ready.",
                "“Fast” start is now the default (Settings → Start speed vs. quality). Pick Balanced or Best if you prefer.",
            ),
        ),
        Entry(
            "New Voices & Settings look",
            listOf(
                "Voices: your main voice up top, coloured badges per service, tap Set main to make a voice your main, hold one to remove it.",
                "Connected services show a green dot once your key is in - tap to add or change a key.",
                "Settings: big title, clean grouped rows, a big speed number with − / + buttons, and real on/off switches.",
                "You can now turn the screenshot double-tap off if you don't want it.",
            ),
        ),
        Entry(
            "Tabs",
            listOf(
                "The app now has tabs at the bottom: Player, Voices and Settings - no more long scrolling page.",
                "The floating bubble uses less battery when nothing is playing.",
            ),
        ),
        Entry(
            "More voices: ElevenLabs & your phone",
            listOf(
                "Add voices from ElevenLabs (paste your ElevenLabs key, then pick from your voices).",
                "Or use your phone's own voices - free, offline and instant. Handy when credit runs out.",
                "Mix them freely: each voice remembers its service, and everything (follow along, parts, speed) works with all of them.",
                "The top shows the right balance for your main voice: Fish credit or ElevenLabs characters left.",
            ),
        ),
        Entry(
            "Big lotus & Fish credit",
            listOf(
                "A big glowing lotus at the top - it gently breathes while reading. Tap it to follow along.",
                "Your Fish Audio credit balance is shown under it, with a warning when it gets low. Tap it to refresh.",
                "The New button got a fresh look.",
            ),
        ),
        Entry(
            "Follow along, links & long reads",
            listOf(
                "Follow-along screen: see the text as it's read, with the current sentence highlighted. Tap any sentence to jump there.",
                "Open it by tapping the notification, or “Follow along” in the app.",
                "Share or paste a webpage link and it reads the article - no menus or ads.",
                "Long text is split into parts: it starts faster, there are no gaps, and you can skip part by part (also with earbud double-tap).",
            ),
        ),
        Entry(
            "New look + screenshot double-tap",
            listOf(
                "A new dark lotus design with your logo at the top.",
                "Double-tap the lotus bubble to read your latest screenshot - no Share menu needed.",
                "Tap “Allow double-tap screenshot reading” once to turn it on.",
            ),
        ),
        Entry(
            "Now called Lotus Aloud",
            listOf(
                "New name: Lotus Aloud. The Quick Settings tile is now “Lotus Read”.",
                "The app icon is now the Lotus Shield Studio logo.",
                "The floating bubble and notifications use it too.",
                "Works with Android's themed icons.",
            ),
        ),
        Entry(
            "What's new button",
            listOf(
                "This pop-up! Tap “What's new” at the top of the app any time.",
                "It shows once by itself after each update.",
                "“Get latest version” opens your GitHub Releases page.",
            ),
        ),
        Entry(
            "Read screenshots & photos",
            listOf(
                "Take a screenshot → Share → Lotus Aloud, and it reads the text in it.",
                "Works with photos and several screenshots at once.",
                "“Read a screenshot or photo” button in the app.",
                "Text is found on your phone - free, no Fish credit used.",
            ),
        ),
        Entry(
            "Renamed to “Read audio”",
            listOf("Our option in the highlight menu is now “Read audio”, so it isn't mixed up with Claude's or Android's “Read aloud”."),
        ),
        Entry(
            "Floating bubble, media controls & more",
            listOf(
                "Floating lotus bubble: copy text in any app (Opera, Messenger...), tap the bubble to read it.",
                "Lock-screen, earbud, Bluetooth and car controls.",
                "Skip back / forward 10 seconds.",
                "Speed changes instantly, even while reading.",
                "Recent reads - replay any of them instantly and free.",
            ),
        ),
        Entry(
            "Quick Settings tile",
            listOf("“Lotus Read” tile: copy text, swipe down, tap it to read."),
        ),
        Entry(
            "First version",
            listOf(
                "Highlight text in any app → read it in your Fish voice.",
                "Share text to Lotus Aloud.",
                "Your voices, main voice, model, quality and speed settings.",
            ),
        ),
    )
}
