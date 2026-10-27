# Fish Reader for Android — Made for LSS Only

Highlight text in **any app** (Opera, Discord, Messages, Chrome…) and tap **Read aloud** —
it's read out in your Fish Audio voice. A notification lets you pause / stop (also from the lock screen).

## Get the app (one-time, ~10 minutes)

GitHub builds the app for you — you never need Android Studio.

1. On **github.com**, click **New repository**. Name it `FishReader`, choose **Private**, click **Create repository**.
2. On the new repo's page, click **uploading an existing file**.
3. Open this `FishReaderAndroid` folder on your PC, select **everything inside it**
   (including the `.github` folder) and drag it onto the GitHub page. Click **Commit changes**.
4. Open the repo's **Actions** tab. A build starts by itself and takes about 5 minutes.
   Wait for the green ✓.
5. On your **phone**, open the repo on github.com (signed in) → **Releases** → the newest one →
   tap **FishReader.apk** to download it, then open it.
   - Android will ask to allow installing from your browser: allow it.
   - If Play Protect says "unknown app", tap **More details → Install anyway** (it's your own app).
6. Open **Fish Reader**, paste your Fish Audio API key, tap **Save & test**, and allow notifications.

## Use it

- **Highlight text → Read aloud.** If you don't see it, tap **⋮** (or the arrow) in the menu that pops up.
- Or **Share** text from any app → **Fish Reader**.
- Pause / Resume / Stop from the notification or the app.
- In the app: add voices (paste a fish.audio voice link), pick your **main voice**, try voices with ▶,
  change model, quality and speed, or paste text to read.

"No API credit" error? Pick the **s2.1-pro-free** model in the app, or add API credit at fish.audio.

## Updating

Any change committed to the repo builds a new version automatically. Install the new
`FishReader.apk` from Releases over the old one — your settings are kept.

Keep the repo **private**: it contains the app's signing key (`app/fishreader.keystore`), which is
what lets new versions install over old ones.

## Building on a PC instead (optional)

Open this folder in Android Studio and press **Build → Build APK(s)**.
