# Lotus Aloud for Android 🪷 Made for LSS Only

Read anything on your phone out loud in **your own voices**: Fish Audio, ElevenLabs, or the
phone's free built-in voices. Highlight text in any app and tap **Read audio**. It also reads
copied text, webpages, screenshots and photos.

---

## ⚡ How it starts reading so fast

1. It sends only the **first sentence or two** (~170 characters) to the voice service.
2. Audio **plays while it streams in**. It starts once a tenth of a second of sound has arrived.
3. As soon as that audio is coming in, the **next part is prepared in the background**.
   Parts get bigger as it goes (~170 → ~550 → ~1,200 characters), so each one is ready before
   the last one ends, with no gaps.
4. It stays **one part ahead**. Stop halfway and you've only used credit for what you heard plus one part.
5. The connection to the voice service opens the moment you **touch the bubble, tap the tile or
   open the app**, so the real request doesn't wait for it.
6. Everything it makes is **saved on the phone**. Replays from *Recent reads* are instant and free.

---

## Ways to read

| How | What to do |
|---|---|
| **Highlight menu** | Highlight text → **Read audio** (tap **⋮** in the menu if you don't see it) |
| **Share** | Share text, a webpage link, a screenshot or a photo → **Lotus Aloud** |
| **Webpages** | Share a page's link (e.g. Opera → ⋮ → Share). It reads just the article, without menus or ads |
| **Lotus bubble** | Copy text in any app (Opera, Messenger…), then tap the floating lotus. **Double-tap** reads your latest screenshot |
| **Quick Settings tile** | Copy text, swipe down, tap **Lotus Read** |
| **In the app** | Paste text or a link on the Player tab, or replay something from **Recent reads** |

Apps like Opera and Messenger hide the **Read audio** option. Use **Share**, the **bubble** or the **tile** there.

## While it reads

- **Follow along:** see the text with the current sentence lit up. Tap any sentence to jump there.
  Open it from the notification, the big lotus, or **Follow along** in the app.
- **Controls:** pause, ⏪ 10s / 10s ⏩, next / previous part. These work from the notification, lock screen,
  earbuds, Bluetooth and the car.
- **Speed** changes instantly, even mid-sentence.
- **Credit:** your Fish credit or ElevenLabs characters left show under the lotus. Tap to refresh.

## The app

- **Player:** the big glowing lotus (it breathes while reading), credit, now playing, paste box, recent reads.
- **Voices:** your main voice up top, every voice with a coloured badge (Fish / ElevenLabs / Phone).
  Tap **★** to make a voice main, **▶** to hear it, hold one to remove it. Add voices and keys here.
- **Settings:** speed, start speed vs. quality (**Fast** by default), Fish model, Lotus bubble,
  screenshot double-tap, Quick Settings tile, How to use, Test my voice, **What's new**.

## Voices

- **Fish Audio:** paste a voice's fish.audio link. Needs a Fish API key (fish.audio/app/api-keys).
  The API is billed **separately** from Plus/Pro. "No API credit"? Add credit or pick **s2.1-pro-free**.
- **ElevenLabs:** paste your ElevenLabs key, then pick from your voices. The free plan has 10,000 characters a month.
- **Phone:** the phone's own voices. Free, offline and instant. Handy when credit runs out.

All voices are AI-generated. Only use voices you own or that are public. Don't clone someone's voice without their OK.

---

## Get the app (one time, ~10 minutes)

GitHub builds the app for you, so you never need Android Studio.

1. Put this folder in a **private** GitHub repo (in GitHub Desktop: add the folder → **Commit** → **Publish/Push**).
   Make sure the `.github` folder is included.
2. Open the repo's **Actions** tab on github.com. A build starts by itself (about 5 minutes). Wait for the green ✓.
3. On your **phone**, open the repo on github.com (signed in) → **Releases** → the newest one →
   tap **LotusAloud.apk**, then open it.
   - Allow installing from your browser when Android asks.
   - **Samsung Auto Blocker** may block it: Settings → Security and privacy → Auto Blocker → off (you can turn it back on after).
   - Play Protect "unknown app"? Tap **More details → Install anyway** (it's your own app).
4. Open **Lotus Aloud** → **Voices** → tap **Fish Audio** under *Connected services*, paste your key, **Save**.
   Allow notifications when asked.

## Updating

1. Replace the files in your local repo folder with the new ones.
2. GitHub Desktop → **Commit** → **Push**. A new build starts automatically.
3. Install the new **LotusAloud.apk** from Releases over the old one. Your settings, voices and keys are kept.
4. The **What's new** pop-up shows once after each update.

## Good to know

- Keep the repo **private**: it contains the app's signing key (`app/lotusaloud.keystore`), which lets
  new versions install over old ones. Never change `applicationId` (`com.lss.fishreader`) or the keystore,
  or updates won't install.
- Your API keys stay on your phone. Text you read is sent to the voice service you picked, to make the speech.
- Battery: the bubble barely checks anything while nothing's playing, and the warm-up is one tiny request at most once a minute.
- Building on a PC instead (optional): open this folder in Android Studio → **Build → Build APK(s)**.
