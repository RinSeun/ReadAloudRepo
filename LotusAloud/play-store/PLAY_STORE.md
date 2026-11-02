# Lotus Aloud on Google Play: step by step

This repo builds **two apps** every time you push:

| File (in Releases) | What it is |
|---|---|
| `LotusAloud.apk` | **Your personal app**, same as always (your voice preset, "Made for LSS Only") |
| `LotusAloud-Play.aab` | **The Play Store version**. This is the file you upload to Google Play |
| `LotusAloud-Play-test.apk` | The Play version as an APK, to install on your phone or send to testers before uploading |

The Play version is a separate app (`com.lotusshield.lotusaloud`), so it installs **next to** your personal one.

**What's different in the Play version**
- Says "by Lotus Shield Studio" instead of "Made for LSS Only"
- Starts with the phone's free voice (no preset Fish voice, so it works straight away for anyone)
- Never reads the photo library: screenshot double-tap opens the photo picker instead (Google's rule)
- Targets Android 15 (Google's rule), with the screen fitted around the status/navigation bars
- ⚑ **Report** button (Now playing, Follow along, Settings → Help), required by Google's AI-content rule
- **Privacy policy** link in Settings → Help
- No "Get latest version" button (Play updates it)

---

## 1. Fill in three settings (once)

Open `gradle.properties` (in this folder) and fill in:

```
lotusSupportEmail=your-studio-email@gmail.com
lotusPrivacyUrl=https://...your privacy page...
lotusReportWebhook=https://discord.com/api/webhooks/...
```

- **Support email:** shown on your store page, so a studio email is better than a personal one.
- **Privacy page:** see step 2.
- **Report webhook (recommended):** reports arrive in a Discord channel, without users leaving the app (Google wants that).
  Discord → your server → a private channel → ⚙ Edit channel → Integrations → Webhooks → New webhook → Copy webhook URL.
  If left empty, reports open the user's email app instead (works, but Google prefers in-app).

Commit → Push. The next build includes them.

## 2. Put the privacy policy online (free)

`play-store/privacy-policy.html` is ready. First replace `YOUR-EMAIL-HERE` (2 places) with your support email. Then either:
- **Google Sites** (easiest): sites.google.com → new site → paste the text → Publish → copy the link; or
- **GitHub Pages**: make a *new public* repo (e.g. `lotus-aloud-privacy`), upload the file renamed to `index.html`,
  Settings → Pages → Deploy from branch → main. Link: `https://RinSeun.github.io/lotus-aloud-privacy/`.
  (Don't make this app repo public: it contains your signing key.)

Put the link in `gradle.properties` (`lotusPrivacyUrl`) **and** in Play Console.

## 3. Play Console account

- play.google.com/console → sign up ($25 one-time). Personal account is fine.
- Identity check takes a few days.
- **New personal accounts must run a closed test with at least 12 testers for 14 days** before going public (step 6).

## 4. Create the app

Create app → Name **Lotus Aloud** → App → Free → accept declarations.

**Store listing (suggested text):**
- **Short description (80 max):** Hear anything read aloud in natural voices: articles, PDFs, screenshots and more.
- **Full description:**

> Lotus Aloud reads text out loud, from anywhere on your phone.
>
> • Highlight text in any app and tap "Read audio"
> • Share webpages, PDFs, Word files, screenshots or photos to hear them
> • Floating lotus bubble and Quick Settings tile: copy text, tap, listen
> • Follow along: see the text with the current sentence highlighted, and tap to jump
> • Translate & read: hear text in 20 languages (translated on your phone)
> • Free built-in phone voices, or bring your own Fish Audio or ElevenLabs account for natural AI voices
> • Speed control, skip, lock-screen and earbud controls, recent reads
>
> Voices from Fish Audio and ElevenLabs are AI-generated and use your own API key. Lotus Aloud has no ads and no tracking.

- **Graphics:** app icon 512×512, feature graphic 1024×500, at least 2 phone screenshots (take them from the Play test APK).
- **Category:** Tools (or Productivity). Contact email = your support email.

## 5. App content (left menu → Policy → App content)

| Section | Answer |
|---|---|
| Privacy policy | your link from step 2 |
| Ads | No ads |
| App access | All functionality available without special access (phone voices work without any key) |
| Content rating | Fill in the questionnaire honestly: a utility app that reads the user's own text aloud, and can make AI speech from it. No violence, gambling etc. |
| Target audience | 13+ (not for children) |
| News app | No |
| Data safety | see below |
| Government apps / Financial / Health | No |
| **Foreground service permissions** | **Media playback:** "Plays the speech the user asked for, with lock-screen controls, while they use other apps." **Special use:** "Shows the floating Lotus bubble the user turned on, so they can read copied text from any app with one tap." Add a short screen recording of each if asked |

**Data safety answers:**
- Does the app collect or share user data? **Yes** (be safe):
  - **App activity → Other user-generated content (the text being read):** *Shared* with the voice service the user picks, for **App functionality**. Not collected by you. Optional (phone voices don't send it).
  - **App info and performance → Diagnostics:** *Collected* by Google ML Kit, for **Analytics**. (See Google's ML Kit data disclosure page.)
  - **Reports (user-generated content):** *Collected* when the user sends a report, for **App functionality**. Optional.
- Is data encrypted in transit? **Yes** (all HTTPS)
- Can users request deletion? Data is on the device: clearing the app's storage deletes it.

## 6. Closed test (required: 12+ testers, 14 days)

Testing → Closed testing → Create track → upload `LotusAloud-Play.aab` → add testers' Google emails (or a Google Group) → roll out.
Testers join with the opt-in link and install from Play. Keep at least 12 testers opted in for 14 days, then apply for production.

## 7. Production

Production → Create release → upload the newest `LotusAloud-Play.aab` → release notes → review. Review usually takes a few days.

## Signing (already handled)

The build signs the AAB with this repo's key as your **upload key**. On first upload, choose **"Let Google manage and protect your app signing key"** (Play App Signing). Keep the repo private and back up `app/lotusaloud.keystore` somewhere safe: losing it means you can't upload updates without contacting Google.
