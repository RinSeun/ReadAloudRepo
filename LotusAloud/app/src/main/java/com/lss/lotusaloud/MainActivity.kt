package com.lss.lotusaloud

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateUtils
import android.text.InputType
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.lss.lotusaloud.SpeechPlayer.Phase

class MainActivity : Activity() {
    companion object {
        const val EXTRA_ASK_SCREENSHOTS = "askScreenshots"
        private var reopenTab: Int? = null
        private var reopenMore = false

        private val TAB_PAGES = listOf(0, 1, 3)
        private const val SCREENSHOT_PERMISSION_REQUEST = 7
    }

    private val accent: Int get() = Look.accent
    private val bgColor: Int get() = Look.bg
    private val cardColor: Int get() = Look.card
    private val fieldColor: Int get() = Look.field
    private val textColor: Int get() = Look.text
    private val mutedColor: Int get() = Look.muted
    private val player get() = SpeechPlayer.shared
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var prefs: Prefs
    private lateinit var statusText: TextView
    private lateinit var pauseBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var voicesBox: LinearLayout
    private lateinit var apiKeyEdit: EditText
    private lateinit var elevenKeyEdit: EditText
    private lateinit var historyBox: LinearLayout
    private lateinit var nowCard: LinearLayout
    private lateinit var savedBox: LinearLayout
    private var savedStamp = -1L
    private var currentTab = 0
    private var wasBlack = false
    private lateinit var bubbleSwitch: android.widget.Switch
    private lateinit var shotsSwitch: android.widget.Switch
    private lateinit var summaryText: TextView
    private lateinit var topBar: LinearLayout
    private var historyStamp = 0L
    private var waitingForOverlay = false
    private var settingSwitch = false
    private val pages = mutableListOf<ScrollView>()
    private val tabs = mutableListOf<TextView>()
    private var addService = Providers.FISH
    private lateinit var addNameEdit: EditText
    private lateinit var addLinkEdit: EditText
    private lateinit var addRow: LinearLayout
    private lateinit var mainVoiceBox: LinearLayout
    private var segments = listOf<Pair<String, TextView>>()
    private lateinit var fishStatus: TextView
    private lateinit var elevenStatus: TextView
    private lateinit var fishKeyBox: LinearLayout
    private lateinit var elevenKeyBox: LinearLayout
    private lateinit var addPickBtn: Button
    private lateinit var addBtn: Button
    private lateinit var heroLogo: android.widget.ImageView
    private lateinit var creditPill: TextView
    private lateinit var langBar: LinearLayout
    private lateinit var langCaption: TextView
    private var transValue: TextView? = null
    private var creditText = ""
    private var creditColor = 0
    private var breathing: android.animation.ObjectAnimator? = null

    private val poll = object : Runnable {
        override fun run() {
            refreshStatus()
            if (currentTab == 1) renderSaved()
            handler.postDelayed(this, 400)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Look.load(prefs)
        wasBlack = prefs.pureBlack
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Look.bg))
        window.navigationBarColor = Look.bar

        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setBackgroundColor(bgColor)
        setContentView(outer)
        Insets.apply(this, outer)
        topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.VERTICAL
        topBar.setPadding(dp(16), dp(26), dp(16), dp(4))
        outer.addView(topBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val pageHolder = android.widget.FrameLayout(this)
        outer.addView(pageHolder, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        val (playerPage, savedPage, voicesPage, settingsPage) = List(4) {
            val page = LinearLayout(this)
            page.orientation = LinearLayout.VERTICAL
            page.setPadding(dp(16), 0, dp(16), dp(28))
            val sc = ScrollView(this)
            sc.addView(page)
            sc.visibility = if (it == 0) View.VISIBLE else View.GONE
            pageHolder.addView(sc, android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            pages.add(sc)
            page
        }
        buildTabBar(outer)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            ) { goBack() }
        }
        var cur: LinearLayout

        val header = row(topBar, 0)
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        header.addView(titles, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        label(titles, "Lotus Aloud", 26f, bold = true)
        label(titles, Edition.TAGLINE.uppercase(), 11f, bold = true, color = accent)
        val newPill = TextView(this)
        newPill.text = "New"
        newPill.textSize = 13f
        newPill.setTextColor(Color.WHITE)
        newPill.setTypeface(newPill.typeface, Typeface.BOLD)
        newPill.background = rounded(Color.argb(40, 255, 255, 255), 99, Color.argb(50, 255, 255, 255))
        newPill.setPadding(dp(14), dp(7), dp(14), dp(7))
        newPill.setOnClickListener { showWhatsNew() }
        header.addView(newPill, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        val hero = android.widget.FrameLayout(this)
        val glow = View(this)
        glow.background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = dp(110).toFloat()
            colors = intArrayOf(Look.accentA(110), Look.accentA(0))
        }
        hero.addView(glow, android.widget.FrameLayout.LayoutParams(dp(240), dp(240), Gravity.CENTER))
        heroLogo = android.widget.ImageView(this)
        heroLogo.setImageResource(resources.getIdentifier("ic_lotus_hero", "drawable", packageName))
        hero.addView(heroLogo, android.widget.FrameLayout.LayoutParams(dp(160), dp(160), Gravity.CENTER))
        val heroLp = LinearLayout.LayoutParams(MATCH_PARENT, dp(200))
        heroLp.topMargin = dp(4)
        playerPage.addView(hero, heroLp)
        heroLogo.setOnClickListener { startActivity(Intent(this, ReaderActivity::class.java)) }

        creditPill = TextView(this)
        creditPill.textSize = 13f
        creditPill.gravity = Gravity.CENTER
        creditPill.setTextColor(mutedColor)
        creditPill.setPadding(dp(14), dp(6), dp(14), dp(6))
        creditPill.background = rounded(cardColor, 99)
        creditPill.setOnClickListener { checkCredit(force = true) }
        val creditRow = LinearLayout(this)
        creditRow.gravity = Gravity.CENTER
        creditRow.addView(creditPill, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        playerPage.addView(creditRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        showCredit()

        val langScroll = android.widget.HorizontalScrollView(this)
        langScroll.isHorizontalScrollBarEnabled = false
        langBar = LinearLayout(this)
        langBar.orientation = LinearLayout.HORIZONTAL
        langBar.gravity = Gravity.CENTER_VERTICAL
        langScroll.addView(langBar, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        val langLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        langLp.topMargin = dp(12)
        playerPage.addView(langScroll, langLp)
        langCaption = label(playerPage, "", 12f, top = 6)
        langCaption.gravity = Gravity.CENTER
        langCaption.setOnClickListener { if (prefs.translateTo.isNotBlank()) pickLangVoice(prefs.translateTo) }
        renderLangBar()

        nowCard = card(playerPage, "Now playing")
        cur = nowCard
        statusText = label(cur, "Idle", 15f)
        val controls = row(cur, 10)
        button(controls, "−10s", 1f) { player.skip(-10.0) }
        pauseBtn = button(controls, "Pause", 1.4f, primary = true) { player.togglePause() }
        button(controls, "+10s", 1f) { player.skip(10.0) }
        val followRow = row(cur, 2)
        button(followRow, "Follow along", 1f) { startActivity(Intent(this, ReaderActivity::class.java)) }
        stopBtn = button(followRow, "Stop", 1f) { player.stop() }
        if (Edition.PLAY) button(followRow, "Report") { Report.show(this) }

        cur = card(playerPage, "Read something")
        val paste = edit(cur, "Paste text or a webpage link")
        paste.setSingleLine(false)
        paste.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        paste.minLines = 3
        paste.gravity = Gravity.TOP or Gravity.START
        val readBtn = button(row(cur, 8), "Read", 1f, primary = true) {
            if (paste.text.isBlank()) toast("Paste or type something first.")
            else if (requireKey()) ReadService.read(this, paste.text.toString())
        }
        readBtn.textSize = 16f
        readBtn.minHeight = dp(52)
        val pickRow = row(cur, 2)
        button(pickRow, "Document", 1f) { pickDocument() }
        button(pickRow, "Photo", 1f) { pickImage() }
        label(cur, "Documents are kept in your Library so you can carry on later.", 12f, top = 6)

        bigTitle(savedPage, "Library")
        label(
            savedPage,
            "Your documents and books, and where you stopped. Only the text is kept on your phone, never the audio.",
            13f, top = 2,
        ).setPadding(dp(10), 0, dp(10), 0)
        val addDocBtn = button(row(savedPage, 12), "Add a document", 1f, primary = true) { pickDocument() }
        addDocBtn.textSize = 16f
        addDocBtn.minHeight = dp(52)
        label(savedPage, "PDF, Word or EPUB", 12f, top = 2).gravity = Gravity.CENTER
        savedBox = LinearLayout(this)
        savedBox.orientation = LinearLayout.VERTICAL
        savedPage.addView(savedBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        renderSaved()

        sectionLabel(savedPage, "RECENT READS")
        cur = group(savedPage)
        cur.setPadding(dp(14), dp(10), dp(10), dp(12))
        label(cur, "Your last 10 reads. Replays are instant and free.", 12f)
        historyBox = LinearLayout(this)
        historyBox.orientation = LinearLayout.VERTICAL
        cur.addView(historyBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        renderHistory()

        val back = label(voicesPage, "‹  Settings", 15f, bold = true, color = Look.soft, top = 18)
        back.setPadding(dp(4), dp(6), dp(4), dp(6))
        back.setOnClickListener { selectTab(3) }
        bigTitle(voicesPage, "Voices")
        sectionLabel(voicesPage, "MAIN VOICE")
        mainVoiceBox = LinearLayout(this)
        mainVoiceBox.orientation = LinearLayout.VERTICAL
        voicesPage.addView(mainVoiceBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        sectionLabel(voicesPage, "ALL VOICES")
        voicesBox = group(voicesPage)
        renderVoices()

        sectionLabel(voicesPage, "ADD A VOICE")
        cur = group(voicesPage)
        cur.setPadding(dp(12), dp(10), dp(12), dp(12))
        val seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        seg.background = rounded(fieldColor, 12)
        seg.setPadding(dp(3), dp(3), dp(3), dp(3))
        cur.addView(seg, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        segments = listOf(Providers.FISH to "Fish", Providers.ELEVEN to "ElevenLabs", Providers.PHONE to "Phone").map { (key, text) ->
            val t = TextView(this)
            t.text = text
            t.gravity = Gravity.CENTER
            t.textSize = 13f
            t.setTypeface(t.typeface, Typeface.BOLD)
            t.setPadding(0, dp(9), 0, dp(9))
            t.setOnClickListener { addService = key; showAddService() }
            seg.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            key to t
        }
        addNameEdit = edit(cur, "Name (e.g. Narrator)")
        addLinkEdit = edit(cur, "")
        addRow = row(cur)
        addPickBtn = button(addRow, "", 1f) { pickVoice() }
        addBtn = button(addRow, "+ Add voice", 1f, primary = true) { addTypedVoice() }
        showAddService()
        val consent = label(cur, "Only add voices you own or have permission to use.", 13f, bold = true, color = Look.soft, top = 10)
        consent.gravity = Gravity.CENTER

        sectionLabel(voicesPage, "CONNECTED SERVICES")
        val services = group(voicesPage)
        val fish = serviceRow(services, "Fish Audio")
        fishStatus = fish.first
        fishKeyBox = fish.second
        apiKeyEdit = edit(fishKeyBox, "Paste your Fish Audio key", prefs.apiKey)
        apiKeyEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        linkLabel(fishKeyBox, "Get a key at fish.audio/app/api-keys", "https://fish.audio/app/api-keys")
        button(row(fishKeyBox), "Save key", 1f, primary = true) { saveCloudKey("Fish Audio") }
        divider(services)
        val eleven = serviceRow(services, "ElevenLabs")
        elevenStatus = eleven.first
        elevenKeyBox = eleven.second
        elevenKeyEdit = edit(elevenKeyBox, "Paste your ElevenLabs key", prefs.elevenKey)
        elevenKeyEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        linkLabel(elevenKeyBox, "Get a key at elevenlabs.io → Developers → API keys", "https://elevenlabs.io/app/developers/api-keys")
        button(row(elevenKeyBox), "Save key", 1f, primary = true) { saveCloudKey("ElevenLabs") }
        divider(services)
        val phone = serviceRow(services, "Your phone")
        phone.first.text = "Built in · free · no key needed"
        (phone.first.tag as? View)?.background = rounded(Color.parseColor("#2EA043"), 99)
        refreshServices()
        label(voicesPage, "All voices are AI-generated. Hold a voice to remove it.", 12f, top = 10)

        bigTitle(settingsPage, "Settings")
        summaryText = buildSummaryCard(settingsPage)

        sectionLabel(settingsPage, "BASICS")
        var g = group(settingsPage)

        val speedBox = LinearLayout(this)
        speedBox.orientation = LinearLayout.VERTICAL
        speedBox.setPadding(dp(20), dp(14), dp(20), dp(14))
        g.addView(speedBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val speedHead = row(speedBox, 0)
        val speedTitle = label(speedHead, "Speed", 16f)
        speedTitle.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        val speedValue = TextView(this)
        speedValue.textSize = 26f
        speedValue.setTypeface(speedValue.typeface, Typeface.BOLD)
        speedValue.setTextColor(Color.WHITE)
        speedHead.addView(speedValue)
        val seek = SeekBar(this)
        seek.max = 15
        seek.progress = ((prefs.speed - 0.5f) * 10).toInt().coerceIn(0, 15)
        fun showSpeed() {
            val txt = String.format("%.1fx", 0.5f + seek.progress / 10f)
            val sp = android.text.SpannableString(txt)
            sp.setSpan(ForegroundColorSpan(Look.soft), txt.length - 1, txt.length, 0)
            sp.setSpan(android.text.style.RelativeSizeSpan(0.6f), txt.length - 1, txt.length, 0)
            speedValue.text = sp
        }
        showSpeed()
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                showSpeed()
                prefs.speed = 0.5f + progress / 10f
                player.setSpeed(prefs.speed)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        val slRow = row(speedBox, 8)
        roundButton(slRow, "−") { seek.progress = (seek.progress - 1).coerceAtLeast(0) }
        slRow.addView(seek, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        roundButton(slRow, "+") { seek.progress = (seek.progress + 1).coerceAtMost(15) }
        val ticks = row(speedBox, 2)
        ticks.setPadding(dp(46), 0, dp(46), 0)
        listOf("0.5", "1", "1.5", "2").forEachIndexed { n, t ->
            val tv = label(ticks, t, 11f, color = Look.faint)
            tv.gravity = when (n) { 0 -> Gravity.START; 3 -> Gravity.END; else -> Gravity.CENTER }
            tv.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        divider(g)
        val transRow = settingsRow(g, "Language", translateText(), valuePink = true)
        transValue = transRow.second
        transRow.first.setOnClickListener { pickTranslate() }
        divider(g)
        val themeRow = settingsRow(g, "Theme", Look.THEMES[Look.index].name, valuePink = true)
        themeRow.first.setOnClickListener { pickTheme() }

        val moreHolder = group(settingsPage)
        (moreHolder.layoutParams as LinearLayout.LayoutParams).topMargin = dp(14)
        val moreRow = settingsRow(moreHolder, "More settings", "Quality, moods, skipping, shortcuts and more")
        val moreBox = LinearLayout(this)
        moreBox.orientation = LinearLayout.VERTICAL
        moreBox.visibility = if (reopenMore) View.VISIBLE else View.GONE
        settingsPage.addView(moreBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        moreRow.first.setOnClickListener {
            val open = moreBox.visibility != View.VISIBLE
            moreBox.visibility = if (open) View.VISIBLE else View.GONE
            moreRow.second.text = if (open) "Tap to hide" else "Quality, moods, skipping, shortcuts and more"
        }
        if (reopenMore) moreRow.second.text = "Tap to hide"
        reopenMore = false

        sectionLabel(moreBox, "READING")
        g = group(moreBox)
        val qBox = LinearLayout(this)
        qBox.orientation = LinearLayout.VERTICAL
        qBox.setPadding(dp(20), dp(14), dp(20), dp(16))
        g.addView(qBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        label(qBox, "Start speed vs. quality", 16f)
        val qSeg = LinearLayout(this)
        qSeg.orientation = LinearLayout.HORIZONTAL
        qSeg.background = rounded(bgColor, 14)
        qSeg.setPadding(dp(3), dp(3), dp(3), dp(3))
        val qLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        qLp.topMargin = dp(10)
        qBox.addView(qSeg, qLp)
        val qOptions = listOf("fast" to "Fast", "balanced" to "Balanced", "quality" to "Best")
        val qViews = qOptions.map { (key, text) ->
            val t = TextView(this)
            t.text = text
            t.gravity = Gravity.CENTER
            t.textSize = 13f
            t.setTypeface(t.typeface, Typeface.BOLD)
            t.setPadding(0, dp(9), 0, dp(9))
            qSeg.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            key to t
        }
        fun showQuality() = qViews.forEach { (key, t) ->
            val on = key == prefs.quality
            t.background = if (on) rounded(accent, 11) else null
            t.setTextColor(if (on) Color.WHITE else mutedColor)
        }
        qViews.forEach { (key, t) -> t.setOnClickListener { prefs.quality = key; showQuality() } }
        showQuality()
        divider(g)

        val modelRow = settingsRow(g, "Fish model", prefs.model, valuePink = true)
        modelRow.first.setOnClickListener { pickModel(modelRow.second) }
        divider(g)

        val moodRow = settingsRow(g, "Mood", Moods.label(prefs.mood) + " · Fish voices", valuePink = true)
        moodRow.first.setOnClickListener { pickMood(moodRow.second) }
        divider(g)
        settingsRow(g, "Voice for each language", "Native voices sound much better").first.setOnClickListener {
            val langs = Translate.LANGS.filter { it.second.isNotBlank() }
            android.app.AlertDialog.Builder(this)
                .setTitle("Voice for each language")
                .setItems(langs.map { "${it.first} · ${langVoiceName(it.second)}" }.toTypedArray()) { _, i ->
                    pickLangVoice(langs[i].second)
                }
                .setNegativeButton("Close", null)
                .show()
        }

        sectionLabel(moreBox, "LOOK")
        g = group(moreBox)
        val blackRow = settingsRow(g, "Pure black background", "Deeper blacks, easier on OLED screens", chevron = false)
        val blackSwitch = switchOn(blackRow.first) { on ->
            if (on != prefs.pureBlack) { prefs.pureBlack = on; applyTheme() }
        }
        settingSwitch = true
        blackSwitch.isChecked = prefs.pureBlack
        settingSwitch = false

        sectionLabel(moreBox, "STORAGE")
        g = group(moreBox)
        val storageGroup = g
        val clearSavedRow = settingsRow(g, "Clear Saved Reads", savedReadsText())
        clearSavedRow.first.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Clear Saved Reads?")
                .setMessage("This removes every saved read and the spot you stopped at. Your files themselves aren't touched.")
                .setPositiveButton("Clear") { _, _ ->
                    if (ReadSession.savedId != null) ReadSession.savedId = null
                    Saved.clear(this)
                    savedStamp = -1L
                    renderSaved()
                    clearSavedRow.second.text = savedReadsText()
                    toast("Saved Reads cleared.")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        sectionLabel(moreBox, "CLEANER READING")
        g = group(moreBox)
        val skipRow = settingsRow(g, "Smart skipping", skipText(), chevron = false)
        val skipSwitch = switchOn(skipRow.first) { on -> prefs.smartSkip = on; skipRow.second.text = skipText() }
        settingSwitch = true
        skipSwitch.isChecked = prefs.smartSkip
        settingSwitch = false
        skipRow.first.setOnClickListener { pickSkips(skipRow.second) }
        divider(g)
        val sayRow = settingsRow(g, "Pronunciation", sayText())
        sayRow.first.setOnClickListener { showSayRules(sayRow.second) }

        sectionLabel(moreBox, "SHORTCUTS")
        g = group(moreBox)
        val bubbleRow = settingsRow(g, "Lotus bubble", "Tap it to read copied text", chevron = false)
        bubbleSwitch = switchOn(bubbleRow.first) { on -> if (on != (prefs.bubbleOn && BubbleService.canShow(this))) toggleBubble() }
        divider(g)
        val shotsRow = settingsRow(g, "Screenshot double-tap",
            if (Edition.PLAY) "Bubble opens your photos to pick one" else "Bubble reads your latest one", chevron = false)
        shotsSwitch = switchOn(shotsRow.first) { on ->
            prefs.shotsOn = on
            if (on && !Edition.PLAY && !Screenshots.canRead(this)) askScreenshotAccess()
        }
        divider(g)
        val tileRow = settingsRow(g, "Quick Settings tile", "Swipe down → Lotus Read")
        tileRow.first.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) requestTile()
            else toast("Swipe down twice, tap the pencil (edit), and drag “Lotus Read” into your tiles.")
        }

        sectionLabel(settingsPage, "HELP")
        g = group(settingsPage)
        settingsRow(g, "How to use", null).first.setOnClickListener { showHowTo() }
        divider(g)
        settingsRow(g, "Cloud voices and your privacy", "What's sent where, and keeping your keys safe").first.setOnClickListener { showCloudNote() }
        divider(g)
        settingsRow(g, "Terms of use", null).first.setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Edition.TERMS_URL))) } catch (e: Exception) { toast("Couldn't open the browser.") }
        }
        divider(g)
        settingsRow(g, "Test my voice", null).first.setOnClickListener {
            if (requireKey()) ReadService.read(this, "Hello! Lotus Aloud is working.", record = false)
        }
        divider(g)
        settingsRow(g, "What's new", null).first.setOnClickListener { showWhatsNew() }
        divider(storageGroup)
        val clearRow = settingsRow(storageGroup, "Clear recent reads", savedText())
        clearRow.first.setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Clear recent reads?")
                .setMessage("This removes your recent reads and the audio saved for replaying them.")
                .setPositiveButton("Clear") { _, _ ->
                    History.clear(this)
                    renderHistory()
                    clearRow.second.text = savedText()
                    toast("Recent reads cleared.")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        if (Edition.PLAY) {
            divider(g)
            settingsRow(g, "Report a problem with audio", "Offensive, misleading or broken").first.setOnClickListener { Report.show(this) }
            if (Edition.PRIVACY_URL.isNotBlank()) {
                divider(g)
                settingsRow(g, "Privacy policy", null).first.setOnClickListener {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Edition.PRIVACY_URL))) } catch (e: Exception) {}
                }
            }
        }

        val foot = label(settingsPage, "Lotus Aloud ${appVersion()}\n${Edition.TAGLINE}", 11f, color = Look.dim, top = 18)
        foot.gravity = Gravity.CENTER
        refreshSwitches()
        reopenTab?.let { selectTab(it); reopenTab = null }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun goBack() {
        when (currentTab) {
            2 -> selectTab(3)
            0 -> finish()
            else -> selectTab(0)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = goBack()

    override fun onResume() {
        super.onResume()
        Providers.warmUp(this)
        refreshSwitches()
        if (!Edition.PLAY && intent?.getBooleanExtra(EXTRA_ASK_SCREENSHOTS, false) == true) {
            intent.removeExtra(EXTRA_ASK_SCREENSHOTS)
            selectTab(3)
            askScreenshotAccess()
        }
        handler.post(poll)
        renderHistory()
        renderSaved()
        if (waitingForOverlay) {
            waitingForOverlay = false
            if (BubbleService.canShow(this)) {
                BubbleService.start(this)
                toast("Lotus bubble is on!")
            } else {
                toast("Lotus Aloud still needs “Appear on top” to show the bubble.")
            }
        } else if (prefs.bubbleOn && BubbleService.canShow(this)) {
            BubbleService.start(this)
        }
        updateBubbleButton()
        checkCredit(force = false)

        val news = {
            if (prefs.seenChanges < Changelog.ENTRIES.size) {
                val fresh = (Changelog.ENTRIES.size - prefs.seenChanges).coerceAtMost(Changelog.ENTRIES.size)
                prefs.seenChanges = Changelog.ENTRIES.size
                handler.post { showWhatsNew(fresh) }
            }
        }
        if (prefs.termsAccepted < Edition.TERMS_VERSION) handler.post { showAgreement(news) } else news()
    }

    private fun openPage(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { toast("Couldn't open the browser.") }
    }

    private fun showAgreement(then: () -> Unit) {
        val updated = prefs.termsAccepted > 0
        val dlg = android.app.Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar)
        dlg.setCancelable(false)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bgColor)

        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(22), dp(36), dp(22), dp(16))
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val logo = android.widget.ImageView(this)
        logo.setImageResource(resources.getIdentifier("ic_lotus_hero", "drawable", packageName))
        page.addView(logo, LinearLayout.LayoutParams(MATCH_PARENT, dp(110)))
        val title = label(page, if (updated) "We've updated our terms" else "Welcome to Lotus Aloud", 28f, bold = true, color = Color.WHITE, top = 14)
        title.gravity = Gravity.CENTER
        val sub = label(page, Edition.TAGLINE, 13f, color = Look.soft, top = 2)
        sub.gravity = Gravity.CENTER

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = rounded(cardColor, 20)
        card.setPadding(dp(18), dp(16), dp(18), dp(16))
        val clp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        clp.topMargin = dp(22)
        page.addView(card, clp)
        val points = listOf(
            "Private by default" to "Phone voices never send your text anywhere. Your library, settings and keys stay on your phone.",
            "AI voices" to "Fish Audio and ElevenLabs voices are AI-generated and use your own account. Text you play with them is sent to that service.",
            "Use it fairly" to "Only read content you have the right to use, and only use voices you own or have permission to use. Never impersonate or mislead people.",
        )
        points.forEachIndexed { i, (h, b) ->
            label(card, h, 15f, bold = true, color = Color.WHITE, top = if (i == 0) 0 else 14)
            label(card, b, 13.5f, top = 3)
        }

        val links = LinearLayout(this)
        links.gravity = Gravity.CENTER
        val llp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        llp.topMargin = dp(18)
        page.addView(links, llp)
        fun link(text: String, url: String) {
            val t = TextView(this)
            t.text = text
            t.textSize = 14.5f
            t.setTypeface(t.typeface, Typeface.BOLD)
            t.setTextColor(Look.soft)
            t.paintFlags = t.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
            t.setPadding(dp(10), dp(8), dp(10), dp(8))
            t.setOnClickListener { openPage(url) }
            links.addView(t)
        }
        link("Terms of Use", Edition.TERMS_URL)
        link("Privacy Policy", Edition.PRIVACY_PAGE)

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.VERTICAL
        bottom.setPadding(dp(18), dp(10), dp(18), dp(18))
        val note = label(bottom, "By tapping Agree and continue, you agree to the Terms of Use and Privacy Policy.", 12.5f)
        note.gravity = Gravity.CENTER
        val agree = button(row(bottom, 10), "Agree and continue", 1f, primary = true) {
            prefs.termsAccepted = Edition.TERMS_VERSION
            dlg.dismiss()
            then()
        }
        agree.textSize = 16f
        agree.minHeight = dp(54)
        val leave = label(bottom, "Not now", 14f, top = 6)
        leave.gravity = Gravity.CENTER
        leave.setPadding(0, dp(8), 0, dp(4))
        leave.setOnClickListener {
            dlg.dismiss()
            finish()
        }
        root.addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        dlg.setContentView(root)
        dlg.window?.statusBarColor = bgColor
        Insets.apply(this, root)
        dlg.show()
    }

    private fun showCredit() {
        if (!::creditPill.isInitialized) return
        val key = prefs.apiKey
        val v = Credit.lastValue
        val service = Providers.of(prefs.mainVoice)
        val left = Credit.elevenLeft
        val (text, color) = when {
            service == Providers.PHONE -> "Phone voice · free" to mutedColor
            service == Providers.ELEVEN && prefs.elevenKey.isBlank() -> "Add your ElevenLabs key below" to mutedColor
            service == Providers.ELEVEN && creditText.isNotEmpty() -> creditText to creditColor
            service == Providers.ELEVEN && left == null -> "Checking ElevenLabs..." to mutedColor
            service == Providers.ELEVEN && left != null && left < 1000 ->
                "ElevenLabs: only $left characters left" to Color.parseColor("#FFB020")
            service == Providers.ELEVEN -> "ElevenLabs: ${"%,d".format(left)} characters left" to mutedColor
            key.isBlank() -> "Add your Fish API key below" to mutedColor
            prefs.model.contains("free") -> "Free model · no credit used" to mutedColor
            creditText.isNotEmpty() -> creditText to creditColor
            v == null -> "Checking Fish credit..." to mutedColor
            v < Credit.LOW -> "Fish credit low: ${Credit.format(v)} · tap to refresh" to Color.parseColor("#FFB020")
            else -> "Fish credit: ${Credit.format(v)}" to mutedColor
        }
        creditPill.text = text
        creditPill.setTextColor(color)
        refreshSummary()
    }

    private fun checkCredit(force: Boolean) {
        val service = Providers.of(prefs.mainVoice)
        val key = if (service == Providers.ELEVEN) prefs.elevenKey else prefs.apiKey
        if (service == Providers.PHONE || key.isBlank()) { showCredit(); return }
        if (!force && System.currentTimeMillis() - Credit.lastChecked < 2 * 60 * 1000L) { showCredit(); return }
        Credit.lastChecked = System.currentTimeMillis()
        creditText = if (force) "Checking..." else ""
        creditColor = mutedColor
        showCredit()
        Thread {
            val msg = try {
                if (service == Providers.ELEVEN) Credit.elevenLeft = Providers.elevenCharactersLeft(key) else Credit.fetch(key)
                ""
            } catch (e: Exception) {
                (if (service == Providers.ELEVEN) "ElevenLabs: " else "Fish credit: ") + e.message
            }
            handler.post {
                creditText = msg
                showCredit()
            }
        }.start()
    }

    private fun setBreathing(on: Boolean) {
        if (!::heroLogo.isInitialized) return
        if (on && breathing == null) {
            val sx = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f)
            val sy = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f)
            breathing = android.animation.ObjectAnimator.ofPropertyValuesHolder(heroLogo, sx, sy).apply {
                duration = 1400
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                start()
            }
        } else if (!on && breathing != null) {
            breathing?.cancel()
            breathing = null
            heroLogo.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
        }
    }

    private fun showWhatsNew(newCount: Int = 1) {
        val dlg = android.app.Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bgColor)

        val scroll = ScrollView(this)
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(18), dp(12), dp(18), dp(24))
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val close = TextView(this)
        close.background = Glyph(Glyph.Kind.CLOSE, mutedColor, dp(16).toFloat())
        val closeSize = dp(36)
        close.setOnClickListener { dlg.dismiss() }
        val closeRow = LinearLayout(this)
        closeRow.gravity = Gravity.END
        closeRow.addView(close, LinearLayout.LayoutParams(closeSize, closeSize))
        page.addView(closeRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val hero = android.widget.FrameLayout(this)
        val logo = android.widget.ImageView(this)
        logo.setImageResource(resources.getIdentifier("ic_lotus_hero", "drawable", packageName))
        hero.addView(logo, android.widget.FrameLayout.LayoutParams(dp(104), dp(104), Gravity.CENTER))
        page.addView(hero, LinearLayout.LayoutParams(MATCH_PARENT, dp(140)))

        val title = label(page, "What's new", 30f, bold = true, color = Color.WHITE)
        title.gravity = Gravity.CENTER
        val sub = label(page, "Lotus Aloud ${appVersion()} · ${Edition.TAGLINE}", 13f, top = 2)
        sub.gravity = Gravity.CENTER

        if (!Edition.PLAY) {
            val up = LinearLayout(this)
            up.orientation = LinearLayout.VERTICAL
            up.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Look.deep1, Look.deep2, cardColor),
            ).apply { cornerRadius = dp(22).toFloat(); setStroke(dp(1), Look.edge) }
            up.setPadding(dp(18), dp(16), dp(18), dp(14))
            val upLp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            upLp.topMargin = dp(18)
            page.addView(up, upLp)
            label(up, "Updates", 16f, bold = true, color = Color.WHITE)
            label(up, "You're on version ${appVersion()}. New versions show up on your GitHub Releases page after each build.", 13f, top = 4)
            val b = button(up, "Get the latest version", primary = true) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.RELEASES_URL)))
            }
            (b.layoutParams as LinearLayout.LayoutParams).apply { width = MATCH_PARENT; topMargin = dp(10) }
        }

        val olderBox = LinearLayout(this)
        olderBox.orientation = LinearLayout.VERTICAL
        Changelog.ENTRIES.forEachIndexed { i, e ->
            val target = if (i < 3) page else olderBox
            changeCard(target, e, isNew = i < newCount, latest = i == 0)
        }
        if (Changelog.ENTRIES.size > 3) {
            val more = button(page, "Show older updates (${Changelog.ENTRIES.size - 3})") {}
            (more.layoutParams as LinearLayout.LayoutParams).apply { width = MATCH_PARENT; topMargin = dp(14) }
            olderBox.visibility = View.GONE
            page.addView(olderBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            more.setOnClickListener {
                olderBox.visibility = View.VISIBLE
                more.visibility = View.GONE
            }
        }

        val bottom = LinearLayout(this)
        bottom.setPadding(dp(18), dp(10), dp(18), dp(18))
        bottom.setBackgroundColor(Look.bar)
        val got = button(bottom, "Got it", 1f, primary = true) { dlg.dismiss() }
        got.minHeight = dp(50)
        root.addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        dlg.setContentView(root)
        dlg.window?.statusBarColor = bgColor
        dlg.window?.navigationBarColor = Look.bar
        Insets.apply(this, root)
        dlg.show()
    }

    private fun changeCard(parent: LinearLayout, e: Changelog.Entry, isNew: Boolean, latest: Boolean) {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(cardColor, 22, if (latest) Look.edge else null)
        c.setPadding(dp(16), dp(14), dp(16), dp(14))
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(12)
        parent.addView(c, lp)

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        c.addView(head, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val name = TextView(this)
        name.text = e.title
        name.textSize = 16f
        name.setTextColor(Color.WHITE)
        name.setTypeface(name.typeface, Typeface.BOLD)
        name.setPadding(0, 0, dp(8), 0)
        head.addView(name, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (isNew) {
            val pill = TextView(this)
            pill.text = "NEW"
            pill.textSize = 10f
            pill.setTextColor(Color.WHITE)
            pill.setTypeface(pill.typeface, Typeface.BOLD)
            pill.background = rounded(accent, 99)
            pill.setPadding(dp(9), dp(3), dp(9), dp(3))
            head.addView(pill)
        }
        for (item in e.items) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val rlp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            rlp.topMargin = dp(9)
            c.addView(row, rlp)
            val dot = View(this)
            dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Look.soft) }
            val dlp = LinearLayout.LayoutParams(dp(6), dp(6))
            dlp.topMargin = dp(8)
            dlp.marginStart = dp(4)
            dlp.marginEnd = dp(12)
            row.addView(dot, dlp)
            val t = TextView(this)
            t.text = item
            t.textSize = 14f
            t.setTextColor(Color.parseColor("#DCD3DB"))
            t.setLineSpacing(0f, 1.2f)
            row.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        saveKey()
        super.onPause()
    }

    private val pickRequest = 42

    private fun pickImage() {
        if (!requireKey()) return
        val i = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES)
        else Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        try {
            startActivityForResult(i, pickRequest)
        } catch (e: Exception) {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), pickRequest)
        }
    }

    private val docRequest = 43

    private fun pickDocument() {
        if (!requireKey()) return
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, Documents.MIME_TYPES)
        try {
            startActivityForResult(i, docRequest)
        } catch (e: Exception) {
            toast("Couldn't open the file picker.")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == docRequest && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val name = Documents.displayName(this, uri)
            val path = ImageText.copyToCache(this, uri)
            if (path == null) toast("Couldn't open that file.")
            else {
                toast("Opening ${name ?: "your document"}...")
                ReadService.readDocument(this, path, contentResolver.getType(uri), name, save = true)
            }
            return
        }
        if (requestCode != pickRequest || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val path = ImageText.copyToCache(this, uri)
        if (path == null) {
            toast("Couldn't open that image.")
        } else {
            toast("Finding the text...")
            ReadService.readImages(this, listOf(path))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun askScreenshotAccess() {
        if (Screenshots.canRead(this)) {
            toast("Screenshot reading is already on.")
            return
        }
        requestPermissions(arrayOf(Screenshots.permission()), SCREENSHOT_PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != SCREENSHOT_PERMISSION_REQUEST) return
        if (Screenshots.canRead(this)) {
            toast("Done! Double-tap the lotus bubble to read your latest screenshot.")
        } else {
            toast("Choose “Allow all” for photos so new screenshots can be read.")
        }
        refreshSwitches()
    }

    private fun toggleBubble() {
        if (prefs.bubbleOn && BubbleService.canShow(this)) {
            BubbleService.stop(this)
            toast("Bubble hidden.")
        } else if (BubbleService.canShow(this)) {
            BubbleService.start(this)
            toast("Lotus bubble is on!")
        } else {
            waitingForOverlay = true
            toast("Turn on “Appear on top” for Lotus Aloud, then come back.")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        updateBubbleButton()
    }

    private fun updateBubbleButton() = refreshSwitches()

    private fun refreshSwitches() {
        if (::bubbleSwitch.isInitialized) {
            settingSwitch = true
            bubbleSwitch.isChecked = prefs.bubbleOn && BubbleService.canShow(this)
            shotsSwitch.isChecked = prefs.shotsOn && (Edition.PLAY || Screenshots.canRead(this))
            settingSwitch = false
        }
    }

    private fun renderHistory() {
        if (!::historyBox.isInitialized) return
        val items = History.list(this)
        historyStamp = items.firstOrNull()?.time ?: 0L
        historyBox.removeAllViews()
        if (items.isEmpty()) {
            label(historyBox, "Nothing yet - things you read will show up here.", 13f)
            return
        }
        val voiceNames = prefs.voices.associate { it.id to it.name }
        for (item in items.take(10)) {
            val r = row(historyBox, 4)
            val t = TextView(this)
            val snippet = item.text.replace(Regex("\\s+"), " ").take(70) + if (item.text.length > 70) "…" else ""
            val ago = DateUtils.getRelativeTimeSpanString(item.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            val voice = voiceNames[item.voice]?.let { " · $it" } ?: ""
            t.text = "$snippet\n$ago$voice"
            t.textSize = 13f
            t.setTextColor(labelColor())
            r.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            button(r, "Play") { if (requireKey(item.voice)) ReadService.read(this, item.text, item.voice) }
        }
        button(row(historyBox), "Clear recent reads", 1f) {
            History.clear(this)
            renderHistory()
        }
    }

    private fun savedReadsText(): String {
        val n = Saved.list(this).size
        val kb = Saved.bytes(this) / 1024
        val size = if (kb >= 1024) "%.1f MB".format(kb / 1024.0) else "$kb KB"
        return if (n == 0) "Nothing saved" else "$n saved · $size"
    }

    private fun renderSaved() {
        if (!::savedBox.isInitialized) return
        val live = livePercent()
        val phase = if (ReadSession.savedId != null) player.snapshot().phase.ordinal else -1
        val stamp = Saved.stamp(this) * 7 + (ReadSession.savedId?.hashCode() ?: 0) * 31 + phase * 1000 + live
        if (stamp == savedStamp) return
        savedStamp = stamp
        val items = Saved.list(this)
        savedBox.removeAllViews()
        if (items.isEmpty()) {
            val empty = label(savedBox, "Nothing here yet.\nTap Add a document above, or Document on the Listen tab.", 14f, top = 30)
            empty.gravity = Gravity.CENTER
            return
        }
        val playing = ReadSession.savedId
        sectionLabel(savedBox, if (items.size == 1) "1 SAVED READ" else "${items.size} SAVED READS")
        for (item in items) savedCard(item, item.id == playing, if (item.id == playing && live >= 0) live else item.percent)
    }

    private fun livePercent(): Int {
        if (ReadSession.savedId == null || ReadSession.count == 0) return -1
        val s = player.snapshot()
        if (s.phase != Phase.PLAYING && s.phase != Phase.PAUSED && s.phase != Phase.LOADING) return -1
        val inPart = if (!s.streaming && s.duration > 0) (s.current / s.duration).coerceIn(0.0, 1.0) else 0.0
        return (((ReadSession.index + inPart) / ReadSession.count) * 100).toInt().coerceIn(0, 99)
    }

    private fun niceTitle(t: String) = t.replace('_', ' ').replace(Regex("\\s+"), " ").trim()

    private fun savedCard(item: SavedRead, playing: Boolean, percent: Int) {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(cardColor, 18, if (playing) Look.accentA(150) else null)
        c.setPadding(dp(14), dp(14), dp(14), dp(12))
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.bottomMargin = dp(12)
        savedBox.addView(c, lp)

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        c.addView(top, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val icon = TextView(this)
        icon.text = item.badge
        icon.textSize = 11f
        icon.letterSpacing = 0.06f
        icon.setTypeface(icon.typeface, Typeface.BOLD)
        icon.setTextColor(Look.soft)
        icon.gravity = Gravity.CENTER
        icon.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(Look.deep3) }
        top.addView(icon, LinearLayout.LayoutParams(dp(52), dp(52)))

        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        mid.setPadding(dp(12), 0, 0, 0)
        top.addView(mid, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val title = label(mid, niceTitle(item.title), 15f, bold = true)
        title.maxLines = 2
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        val kind = when (item.kind) {
            SavedRead.KIND_PDF -> "PDF"
            SavedRead.KIND_WORD -> "Word"
            SavedRead.KIND_BOOK -> "Book"
            SavedRead.KIND_WEB -> "Article"
            else -> "Text"
        }
        val mins = (item.chars / 900).coerceAtLeast(1)
        val length = if (mins >= 60) "${mins / 60} h ${mins % 60} min" else "$mins min"
        val now = System.currentTimeMillis()
        val ago = if (now - item.opened < 60_000) "just now"
        else DateUtils.getRelativeTimeSpanString(item.opened, now, DateUtils.MINUTE_IN_MILLIS).toString()
        label(mid, "$kind · about $length · $ago", 12f, top = 2)

        val phase = player.snapshot().phase
        val active = playing && (phase == Phase.PLAYING || phase == Phase.PAUSED || phase == Phase.LOADING)
        val pct = if (item.done && !active) 100 else percent.coerceIn(0, 100)
        val track = LinearLayout(this)
        track.orientation = LinearLayout.HORIZONTAL
        track.background = rounded(Look.raise, 99)
        val tlp = LinearLayout.LayoutParams(MATCH_PARENT, dp(5))
        tlp.topMargin = dp(12)
        c.addView(track, tlp)
        if (pct > 0) {
            val fill = View(this)
            fill.background = rounded(if (pct == 100) Color.parseColor("#2EA043") else accent, 99)
            track.addView(fill, LinearLayout.LayoutParams(0, MATCH_PARENT, pct.toFloat()))
        }
        if (pct < 100) track.addView(View(this), LinearLayout.LayoutParams(0, MATCH_PARENT, (100 - pct).toFloat()))
        val state = when {
            active && phase == Phase.LOADING -> "Getting ready · $pct%"
            active && phase == Phase.PAUSED -> "Paused · $pct%"
            active -> "Playing · $pct%"
            item.done -> "Finished"
            pct > 0 -> "$pct% read"
            else -> "Not started"
        }
        label(c, state, 12f, color = if (active) Look.soft else null, top = 6)

        val buttons = row(c, 8)
        val playText = when {
            active && phase != Phase.PAUSED -> "Pause"
            active -> "Resume"
            item.resumeFrom > 0.0 -> "Continue"
            else -> "Play"
        }
        button(buttons, playText, 1.2f, primary = true) {
            if (active) {
                player.togglePause()
            } else {
                ReadService.readSaved(this, item.id)
                if (item.resumeFrom > 0.0) toast("Continuing from ${item.percent}%")
            }
            savedStamp = -1L
        }
        button(buttons, "Restart", 1f) {
            ReadService.readSaved(this, item.id, fromStart = true)
            savedStamp = -1L
        }
        val cast = Saved.cast(this, item.id)
        val castText = if (cast != null && cast.on && cast.characters > 0) "Voices · ${cast.characters}" else "Voices"
        val castBtn = button(buttons, castText, 1.1f) {
            CastScreen(this, item) { savedStamp = -1L; renderSaved() }.show()
        }
        if (cast != null && cast.on) castBtn.setTextColor(Look.soft)
        button(buttons, "More", 0.9f) { savedOptions(item) }
        for (n in 0 until buttons.childCount) {
            (buttons.getChildAt(n) as? Button)?.apply {
                setPadding(dp(4), 0, dp(4), 0)
                textSize = 13.5f
                maxLines = 1
            }
        }
        c.setOnLongClickListener { savedOptions(item); true }
    }

    private fun savedOptions(item: SavedRead) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions.add("Follow along" to {
            if (ReadSession.savedId != item.id) ReadService.readSaved(this, item.id)
            startActivity(Intent(this, ReaderActivity::class.java))
        })
        if (item.done || item.percent > 0) actions.add("Mark as not started" to {
            Saved.update(this, item.id) { it.copy(fraction = 0.0, done = false) }
            renderSaved()
        })
        if (!item.done) actions.add("Mark as finished" to {
            Saved.update(this, item.id) { it.copy(fraction = 1.0, done = true) }
            renderSaved()
        })
        actions.add("Rename" to { renameSaved(item) })
        actions.add("Remove" to {
            if (ReadSession.savedId == item.id) ReadSession.savedId = null
            Saved.remove(this, item.id)
            renderSaved()
            toast("Removed “${niceTitle(item.title)}”.")
        })
        android.app.AlertDialog.Builder(this)
            .setTitle(niceTitle(item.title))
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun renameSaved(item: SavedRead) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(22), dp(8), dp(22), 0)
        val name = edit(box, "Name", niceTitle(item.title))
        android.app.AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isNotEmpty()) {
                    Saved.update(this, item.id) { it.copy(title = n.take(120)) }
                    renderSaved()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickTheme() {
        val names = Look.THEMES.map { t ->
            val sp = android.text.SpannableString("●  ${t.name}")
            sp.setSpan(ForegroundColorSpan(t.accent), 0, 1, 0)
            sp
        }.toTypedArray<CharSequence>()
        android.app.AlertDialog.Builder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(names, Look.index) { d, i ->
                d.dismiss()
                if (i != prefs.theme) {
                    prefs.theme = i
                    applyTheme()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyTheme() {
        Look.load(prefs)
        reopenTab = currentTab
        reopenMore = prefs.pureBlack != wasBlack
        recreate()
    }

    private fun requestTile() {
        if (Build.VERSION.SDK_INT < 33) return
        val sbm = getSystemService(StatusBarManager::class.java) ?: return
        val iconRes = resources.getIdentifier("ic_notif", "drawable", packageName).takeIf { it != 0 }
            ?: android.R.drawable.ic_media_play
        sbm.requestAddTileService(
            ComponentName(this, ReadTileService::class.java),
            "Lotus Read",
            Icon.createWithResource(this, iconRes),
            mainExecutor,
        ) { result ->
            toast(
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "Added! Swipe down to find Lotus Read."
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "Lotus Read is already in your Quick Settings."
                    else -> "Not added. You can add it from the Quick Settings edit screen."
                }
            )
        }
    }

    private fun renderVoices() {
        val main = prefs.mainVoice
        val voices = prefs.voices

        mainVoiceBox.removeAllViews()
        val mv = voices.firstOrNull { it.id == main }
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.HORIZONTAL
        hero.gravity = Gravity.CENTER_VERTICAL
        hero.setPadding(dp(14), dp(14), dp(14), dp(14))
        hero.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Look.deep1, Look.deep2, cardColor),
        ).apply { cornerRadius = dp(18).toFloat(); setStroke(dp(1), Look.accentA(70)) }
        mainVoiceBox.addView(hero, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val heroId = mv?.id ?: Prefs.DEFAULT_VOICE
        avatar(hero, mv?.name ?: "Fish", heroId, 46)
        val ht = LinearLayout(this)
        ht.orientation = LinearLayout.VERTICAL
        ht.setPadding(dp(12), 0, dp(8), 0)
        hero.addView(ht, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        label(ht, mv?.name ?: "Fish default voice", 17f, bold = true)
        chip(ht, serviceName(heroId, long = true), heroId)
        playCircle(hero, 42, big = true) {
            if (requireKey(heroId)) ReadService.read(this, "Hi! This is how ${mv?.name ?: "this voice"} sounds.", heroId, record = false)
        }

        voicesBox.removeAllViews()
        voices.forEachIndexed { n, v ->
            if (n > 0) divider(voicesBox)
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(12), dp(10), dp(10), dp(10))
            voicesBox.addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            avatar(r, v.name, v.id, 36)
            val t = LinearLayout(this)
            t.orientation = LinearLayout.VERTICAL
            t.setPadding(dp(11), 0, dp(6), 0)
            r.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            label(t, v.name, 15f, bold = v.id == main)
            chip(t, serviceName(v.id, long = false), v.id)
            val star = TextView(this)
            val isMain = v.id == main
            star.text = if (isMain) "MAIN" else "Set main"
            star.textSize = if (isMain) 10f else 12f
            if (isMain) star.letterSpacing = 0.08f
            star.setTypeface(star.typeface, Typeface.BOLD)
            star.setTextColor(if (isMain) Color.WHITE else Look.soft)
            star.background = rounded(if (isMain) accent else Look.raise, 99)
            star.setPadding(dp(10), dp(5), dp(10), dp(5))
            val starLp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            starLp.marginEnd = dp(8)
            star.layoutParams = starLp
            star.setOnClickListener {
                if (v.id != main) {
                    prefs.mainVoice = v.id
                    Credit.lastChecked = 0L
                    renderVoices()
                    checkCredit(force = false)
                    toast("${v.name} is now your main voice.")
                }
            }
            r.addView(star)
            playCircle(r, 34, big = false) {
                if (requireKey(v.id)) ReadService.read(this, "Hi! This is how ${v.name} sounds.", v.id, record = false)
            }
            r.setOnLongClickListener {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Remove ${v.name}?")
                    .setPositiveButton("Remove") { _, _ ->
                        prefs.voices = prefs.voices.filter { it.id != v.id }
                        renderVoices()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        }
        if (voices.isEmpty()) {
            val empty = label(voicesBox, "No voices yet - add one below. Fish's default voice is used until then.", 13f)
            empty.setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        showCredit()
    }

    private fun bigTitle(parent: LinearLayout, text: String) {
        val box = android.widget.FrameLayout(this)
        val t = TextView(this)
        t.text = text
        t.textSize = 32f
        t.setTextColor(textColor)
        t.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        t.setPadding(dp(10), dp(56), dp(10), dp(6))
        box.addView(t)
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.setMargins(-dp(16), 0, -dp(16), 0)
        box.setPadding(dp(16), 0, dp(16), 0)
        parent.addView(box, lp)
    }

    private fun buildSummaryCard(parent: LinearLayout): TextView {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(dp(20), dp(16), dp(16), dp(16))
        c.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Look.deep3, cardColor, cardColor),
        ).apply { cornerRadius = dp(24).toFloat(); setStroke(dp(1), Look.accentA(64)) }
        c.elevation = dp(6).toFloat()
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(8)
        parent.addView(c, lp)
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        c.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        label(t, "Voices", 17f, bold = true)
        val sub = label(t, "", 13f)
        chevron(c)
        c.setOnClickListener { selectTab(2) }
        return sub
    }

    private fun refreshSummary() {
        if (!::summaryText.isInitialized) return
        val name = prefs.voices.firstOrNull { it.id == prefs.mainVoice }?.name ?: "Fish default"
        val credit = if (::creditPill.isInitialized) creditPill.text.toString() else ""
        val txt = "Main voice  $name" + if (credit.isNotBlank()) "  ·  $credit" else "  ·  tap to add or change"
        val sp = android.text.SpannableString(txt)
        val st = txt.indexOf(name)
        sp.setSpan(ForegroundColorSpan(Look.soft), st, st + name.length, 0)
        summaryText.text = sp
    }

    private fun chevron(parent: LinearLayout) {
        val ch = TextView(this)
        ch.text = "›"
        ch.textSize = 22f
        ch.setTextColor(Look.dim)
        ch.setPadding(dp(8), 0, 0, 0)
        parent.addView(ch)
    }

    private fun settingsRow(
        parent: LinearLayout, title: String, sub: String?, valuePink: Boolean = false, chevron: Boolean = true,
    ): Pair<LinearLayout, TextView> {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(20), dp(15), dp(18), dp(15))
        parent.addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        r.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        label(t, title, 16f)
        val subView = label(t, sub ?: "", 13f, color = if (valuePink) Look.soft else null)
        if (sub == null) subView.visibility = View.GONE
        if (chevron) chevron(r)
        return r to subView
    }

    private fun switchOn(row: LinearLayout, onChange: (Boolean) -> Unit): android.widget.Switch {
        val bar = View(this)
        bar.setBackgroundColor(Color.argb(34, 255, 255, 255))
        val bl = LinearLayout.LayoutParams(dp(1), dp(28))
        bl.setMargins(dp(12), 0, dp(14), 0)
        row.addView(bar, bl)
        val sw = android.widget.Switch(this)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = android.content.res.ColorStateList(states, intArrayOf(Color.WHITE, Color.WHITE))
        sw.trackTintList = android.content.res.ColorStateList(states, intArrayOf(accent, Look.trackOff))
        sw.setOnCheckedChangeListener { _, on -> if (!settingSwitch) onChange(on) }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        return sw
    }

    private fun roundButton(parent: LinearLayout, text: String, onClick: () -> Unit) {
        val b = TextView(this)
        b.text = text
        b.gravity = Gravity.CENTER
        b.textSize = 17f
        b.setTextColor(textColor)
        b.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Look.raise) }
        b.setOnClickListener { onClick() }
        parent.addView(b, LinearLayout.LayoutParams(dp(32), dp(32)))
    }

    private fun translateText() =
        if (prefs.translateTo.isBlank()) "Off · reads in the original language"
        else Translate.label(prefs.translateTo) + " · everything is translated first"

    private fun renderLangBar() {
        if (!::langBar.isInitialized) return
        langBar.removeAllViews()
        val current = prefs.translateTo
        val shown = (listOf("") + (listOf(current) + prefs.recentLangs).filter { it.isNotBlank() }.distinct().take(3))
        for (code in shown) {
            val pill = langPill(if (code.isBlank()) "Original" else Translate.label(code), code == current) { setTranslate(code) }
            if (code.isNotBlank()) pill.setOnLongClickListener { pickLangVoice(code); true }
        }
        langPill("+ More", false) { pickTranslate() }
        if (::langCaption.isInitialized) {
            langCaption.text = if (current.isBlank()) "Hold a language to choose its voice"
            else "${Translate.label(current)} is read by ${langVoiceName(current)} · tap to change"
        }
    }

    private fun langVoiceName(lang: String): String {
        val id = prefs.langVoice(lang)
        return when {
            id == null -> if (Providers.of(prefs.mainVoice) == Providers.PHONE) "your phone (automatic)"
                else (prefs.voices.firstOrNull { it.id == prefs.mainVoice }?.name ?: "your main voice")
            id.startsWith("phone:@") -> "your phone's ${Translate.label(lang)} voice"
            else -> prefs.voices.firstOrNull { it.id == id }?.name ?: "your main voice"
        }
    }

    private fun pickLangVoice(lang: String) {
        val name = Translate.label(lang)
        val options = mutableListOf<Pair<String, String?>>()
        options.add("Automatic (your main voice)" to null)
        options.add("Phone · $name (free, made for $name)" to "phone:@$lang")
        prefs.voices.forEach { v -> options.add("${v.name} · ${Providers.label(v.id)}" to v.id) }
        val current = options.indexOfFirst { it.second == prefs.langVoice(lang) }.coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("Voice for $name")
            .setSingleChoiceItems(options.map { it.first }.toTypedArray(), current) { d, i ->
                prefs.setLangVoice(lang, options[i].second)
                d.dismiss()
                renderLangBar()
                toast("$name will be read by ${langVoiceName(lang)}.")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun langPill(text: String, on: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = 13f
        t.setTextColor(if (on) Color.WHITE else mutedColor)
        if (on) t.setTypeface(t.typeface, Typeface.BOLD)
        t.background = rounded(if (on) accent else cardColor, 99, if (on) null else Look.raise)
        t.setPadding(dp(14), dp(7), dp(14), dp(7))
        t.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        lp.marginEnd = dp(6)
        langBar.addView(t, lp)
        return t
    }

    private fun setTranslate(code: String) {
        val firstTime = code.isNotBlank() && code !in prefs.recentLangs
        if (code == prefs.translateTo) return
        prefs.translateTo = code
        renderLangBar()
        transValue?.text = translateText()
        val busy = player.isBusy() && ReadSession.original.isNotBlank()
        if (busy) {
            ReadService.retranslate(this)
            toast(if (code.isBlank()) "Back to the original language..." else "Switching to ${Translate.label(code)}...")
        } else {
            toast(if (code.isBlank()) "Reading in the original language." else "Everything will be read in ${Translate.label(code)}.")
        }
        if (firstTime && !busy) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Reading in ${Translate.label(code)}")
                .setMessage(
                    "Everything you read is translated first, then read out loud.\n\n" +
                        "• Translation happens on your phone: free, and offline after the first time " +
                        "(it downloads the language once, about 30 MB).\n\n" +
                        "• Fish and ElevenLabs voices can speak it, including your own voice. " +
                        "Phone voices: pick one for that language in Voices.\n\n" +
                        "• Tap “Original” on the Listen tab to switch back."
                )
                .setPositiveButton("Got it", null)
                .show()
        }
    }

    private fun skipText(): String {
        if (!prefs.smartSkip) return "Off · reads everything"
        val on = listOfNotNull(
            "headers & page numbers".takeIf { prefs.skipHeaders },
            "citations".takeIf { prefs.skipCitations },
            "links".takeIf { prefs.skipLinks },
            "reference lists".takeIf { prefs.skipRefs },
        )
        return if (on.isEmpty()) "On · nothing selected" else "Skips " + on.joinToString(", ") + " · tap to choose"
    }

    private fun pickSkips(valueView: TextView) {
        val labels = arrayOf(
            "Headers, footers & page numbers (repeated on every page)",
            "Citations like [12] or (Smith et al., 2020)",
            "Long links, DOIs and email addresses",
            "The reference list at the end",
        )
        val checked = booleanArrayOf(prefs.skipHeaders, prefs.skipCitations, prefs.skipLinks, prefs.skipRefs)
        android.app.AlertDialog.Builder(this)
            .setTitle("Smart skipping")
            .setMultiChoiceItems(labels, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Save") { _, _ ->
                prefs.skipHeaders = checked[0]
                prefs.skipCitations = checked[1]
                prefs.skipLinks = checked[2]
                prefs.skipRefs = checked[3]
                valueView.text = skipText()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sayText(): String {
        val n = prefs.sayRules.size
        return if (n == 0) "Teach it how to say names and words" else "$n word${if (n == 1) "" else "s"} · tap to edit"
    }

    private fun showSayRules(valueView: TextView) {
        val rules = prefs.sayRules
        val items = rules.map { "${it.word}  →  ${it.sayAs}" }.toTypedArray()
        val b = android.app.AlertDialog.Builder(this)
            .setTitle("Pronunciation")
            .setPositiveButton("+ Add word") { _, _ -> editSayRule(null, valueView) }
            .setNegativeButton("Close", null)
        if (items.isEmpty()) {
            b.setMessage(
                "Teach Lotus Aloud how to say things it gets wrong.\n\n" +
                    "For example:\n• LSS → L S S\n• Nguyen → Win\n• gif → jif\n\n" +
                    "It works with every voice, and Follow along still shows the original word."
            )
        } else {
            b.setItems(items) { _, i -> editSayRule(rules[i], valueView) }
        }
        b.show()
    }

    private fun editSayRule(rule: SayRule?, valueView: TextView) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(22), dp(8), dp(22), 0)
        val word = edit(box, "Word or phrase (e.g. LSS)", rule?.word ?: "")
        val say = edit(box, "Say it like (e.g. L S S)", rule?.sayAs ?: "")
        val dlg = android.app.AlertDialog.Builder(this)
            .setTitle(if (rule == null) "Add a word" else "Edit “${rule.word}”")
            .setView(box)
            .setPositiveButton("Save", null)
            .setNeutralButton("Test", null)
            .setNegativeButton(if (rule == null) "Cancel" else "Delete") { _, _ ->
                if (rule != null) {
                    prefs.sayRules = prefs.sayRules.filterNot { it == rule }
                    valueView.text = sayText()
                    toast("Removed “${rule.word}”.")
                }
            }
            .create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val w = word.text.toString().trim()
                val s = say.text.toString().trim()
                if (w.isEmpty() || s.isEmpty()) { toast("Fill in both boxes."); return@setOnClickListener }
                val list = prefs.sayRules.filterNot { it == rule || it.word.equals(w, ignoreCase = true) } + SayRule(w, s)
                prefs.sayRules = list
                valueView.text = sayText()
                toast("Saved: “$w” is said “$s”.")
                dlg.dismiss()
            }
            dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val s = say.text.toString().trim()
                if (s.isEmpty()) toast("Type how it should sound first.")
                else if (requireKey()) ReadService.read(this, s, record = false)
            }
        }
        dlg.show()
    }

    private fun savedText(): String {
        val mb = SpeechPlayer.savedBytes(this) / (1024.0 * 1024.0)
        return "Keeps your last ${History.MAX} · saved audio ${String.format("%.1f", mb)} MB (max 40 MB)"
    }

    private fun pickTranslate() {
        val labels = Translate.LANGS.map { if (it.second.isBlank()) "Original (no translation)" else it.first }
        val current = Translate.LANGS.indexOfFirst { it.second == prefs.translateTo }.coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle("Read in...")
            .setSingleChoiceItems(labels.toTypedArray(), current) { d, i ->
                d.dismiss()
                setTranslate(Translate.LANGS[i].second)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickMood(valueView: TextView) {
        val labels = Moods.LIST.map { it.first } + "Custom..."
        val current = Moods.LIST.indexOfFirst { it.second == prefs.mood }.let { if (it < 0) labels.size - 1 else it }
        android.app.AlertDialog.Builder(this)
            .setTitle("Mood (Fish voices)")
            .setSingleChoiceItems(labels.toTypedArray(), current) { d, i ->
                d.dismiss()
                if (i < Moods.LIST.size) {
                    setMood(Moods.LIST[i].second, valueView)
                } else {
                    val e = EditText(this)
                    e.hint = "e.g. very excited, sleepy, pirate"
                    e.setText(if (current == labels.size - 1) prefs.mood else "")
                    e.setSingleLine(true)
                    val box = LinearLayout(this)
                    box.setPadding(dp(22), dp(8), dp(22), 0)
                    box.addView(e, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Custom mood")
                        .setMessage("Describe how it should sound, in a few words.")
                        .setView(box)
                        .setPositiveButton("Use it") { _, _ -> setMood(e.text.toString(), valueView) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setMood(cue: String, valueView: TextView) {
        prefs.mood = cue
        valueView.text = Moods.label(prefs.mood) + " · Fish voices"
        toast(if (cue.isBlank()) "Normal voice." else "Mood: ${Moods.label(prefs.mood)}. Try it by playing a voice.")
    }

    private fun pickModel(valueView: TextView) {
        val models = Prefs.MODELS.toMutableList()
        if (prefs.model !in models) models.add(prefs.model)
        android.app.AlertDialog.Builder(this)
            .setTitle("Fish model")
            .setSingleChoiceItems(models.toTypedArray(), models.indexOf(prefs.model)) { d, i ->
                prefs.model = models[i]
                valueView.text = models[i]
                showCredit()
                d.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHowTo() {
        android.app.AlertDialog.Builder(this)
            .setTitle("How to use")
            .setMessage(
                "• Highlight text in any app and tap “Read audio” (look under ⋮ if you don't see it).\n\n" +
                    "• Share text, a link or a screenshot to Lotus Aloud.\n\n" +
                    "• In Opera or Messenger: copy the text, then tap the lotus bubble. Double-tap it to read your latest screenshot. " +
                    "Tap it while reading to stop. Drag to move it, long-press to open the app.\n\n" +
                    "• Or copy text and tap “Lotus Read” in your swipe-down panel.\n\n" +
                    "• Tap the notification, or the big lotus, to follow along with the text."
            )
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun serviceColor(id: String): Int = when (Providers.of(id)) {
        Providers.ELEVEN -> Color.parseColor("#B79BFF")
        Providers.PHONE -> Color.parseColor("#5EE0CF")
        else -> Color.parseColor("#FF7A97")
    }

    private fun serviceName(id: String, long: Boolean): String = when (Providers.of(id)) {
        Providers.ELEVEN -> "ELEVENLABS · CLOUD"
        Providers.PHONE -> "PHONE · PRIVATE"
        else -> if (long) "FISH AUDIO · CLOUD" else "FISH · CLOUD"
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    private fun avatar(parent: LinearLayout, name: String, id: String, sizeDp: Int) {
        val c = serviceColor(id)
        val a = TextView(this)
        a.text = name.trim().firstOrNull()?.uppercase() ?: "?"
        a.gravity = Gravity.CENTER
        a.textSize = sizeDp * 0.4f
        a.setTypeface(a.typeface, Typeface.BOLD)
        a.setTextColor(c)
        a.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(withAlpha(c, 50)) }
        parent.addView(a, LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)))
    }

    private fun chip(parent: LinearLayout, text: String, id: String) {
        val c = serviceColor(id)
        val t = TextView(this)
        t.text = text
        t.textSize = 10f
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.letterSpacing = 0.04f
        t.setTextColor(c)
        t.background = rounded(withAlpha(c, 40), 99)
        t.setPadding(dp(8), dp(2), dp(8), dp(2))
        val lp = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        lp.topMargin = dp(3)
        parent.addView(t, lp)
    }

    private fun playCircle(parent: LinearLayout, sizeDp: Int, big: Boolean, onClick: () -> Unit) {
        val b = View(this)
        b.background = android.graphics.drawable.LayerDrawable(arrayOf(
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (big) accent else Look.raise)
            },
            Glyph(Glyph.Kind.PLAY, Color.WHITE, dp(if (big) 14 else 11).toFloat()),
        ))
        b.setOnClickListener { onClick() }
        parent.addView(b, LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)))
    }

    private fun sectionLabel(parent: LinearLayout, text: String) {
        val t = TextView(this)
        t.text = text
        t.textSize = 11f
        t.letterSpacing = 0.08f
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.setTextColor(Look.soft)
        t.setPadding(dp(14), dp(18), dp(4), dp(7))
        parent.addView(t, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun group(parent: LinearLayout): LinearLayout {
        val g = LinearLayout(this)
        g.orientation = LinearLayout.VERTICAL
        g.background = rounded(cardColor, 16)
        parent.addView(g, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return g
    }

    private fun divider(parent: LinearLayout) {
        val d = View(this)
        d.setBackgroundColor(Color.argb(14, 255, 255, 255))
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, dp(1))
        lp.marginStart = dp(12)
        parent.addView(d, lp)
    }

    private fun linkLabel(parent: LinearLayout, text: String, url: String) {
        val l = label(parent, text, 13f, color = Color.parseColor("#6CA8FF"), top = 6)
        l.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun serviceRow(parent: LinearLayout, name: String): Pair<TextView, LinearLayout> {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(14), dp(12), dp(14), dp(12))
        parent.addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val dot = View(this)
        dot.background = rounded(Look.dim, 99)
        r.addView(dot, LinearLayout.LayoutParams(dp(9), dp(9)))
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.setPadding(dp(12), 0, dp(8), 0)
        r.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        label(t, name, 15f, bold = true)
        val status = label(t, "", 12f)
        status.tag = dot
        val chev = TextView(this)
        chev.text = "›"
        chev.textSize = 20f
        chev.setTextColor(Look.dim)
        r.addView(chev)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), 0, dp(14), dp(12))
        box.visibility = View.GONE
        parent.addView(box, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        r.setOnClickListener {
            val open = box.visibility != View.VISIBLE
            box.visibility = if (open) View.VISIBLE else View.GONE
            chev.rotation = if (open) 90f else 0f
        }
        return status to box
    }

    private fun refreshServices() {
        if (!::fishStatus.isInitialized) return
        for ((status, has) in listOf(fishStatus to prefs.apiKey.isNotBlank(), elevenStatus to prefs.elevenKey.isNotBlank())) {
            status.text = if (has) "Connected" else "Not set up · tap to add key"
            (status.tag as? View)?.background = rounded((if (has) Color.parseColor("#2EA043") else Look.dim), 99)
        }
    }

    private fun refreshStatus() {
        val s = player.snapshot()
        setBreathing(s.phase == Phase.PLAYING)
        val newest = History.list(this).firstOrNull()?.time ?: 0L
        if (newest != historyStamp) renderHistory()
        val time = if (s.streaming || s.duration <= 0.0) ReadService.fmt(s.current)
        else "${ReadService.fmt(s.current)} / ${ReadService.fmt(s.duration)}"
        statusText.text = when (s.phase) {
            Phase.IDLE -> "Idle"
            Phase.LOADING -> "Generating audio... ${(System.currentTimeMillis() - s.since) / 1000}s"
            Phase.PLAYING -> (if (s.streaming) "Playing · live  " else "Playing  ") + time
            Phase.PAUSED -> "Paused  $time"
            Phase.ENDED -> "Done"
            Phase.ERROR -> s.message
        }
        statusText.setTextColor(if (s.phase == Phase.ERROR) Color.parseColor("#D1242F") else labelColor())
        nowCard.visibility = if (s.phase == Phase.IDLE) View.GONE else View.VISIBLE
        pauseBtn.text = if (s.phase == Phase.PAUSED) "Resume" else "Pause"
        setOn(pauseBtn, s.phase == Phase.PLAYING || s.phase == Phase.PAUSED)
        setOn(stopBtn, s.phase == Phase.LOADING || s.phase == Phase.PLAYING || s.phase == Phase.PAUSED)
    }

    private fun saveCloudKey(service: String) {
        val finish = {
            saveKey()
            checkCredit(force = true)
            toast("Saved.")
        }
        if (prefs.cloudNoteSeen) finish() else showCloudNote(service) {
            prefs.cloudNoteSeen = true
            finish()
        }
    }

    private fun showCloudNote(service: String? = null, onOk: (() -> Unit)? = null) {
        val name = service ?: "Fish Audio and ElevenLabs"
        val msg = "• Your key works like a password. It's saved only on this phone, never sent to us. Don't share it.\n\n" +
            "• Text you play with $name voices is sent to $name to make the audio.\n\n" +
            "• For private things like passwords, bank details, medical info or personal messages, use a Phone voice. Nothing leaves your phone.\n\n" +
            "• Set a spending limit in your $name account. If you lose your phone, delete the key on their website."
        val b = android.app.AlertDialog.Builder(this)
            .setTitle(if (service != null) "Before you connect $service" else "Cloud voices and your privacy")
            .setMessage(msg)
        if (onOk != null) {
            b.setPositiveButton("I understand") { _, _ -> onOk() }
            b.setNegativeButton("Cancel", null)
        } else {
            b.setPositiveButton("Got it", null)
        }
        b.show()
    }

    private fun saveKey() {
        if (::apiKeyEdit.isInitialized) prefs.apiKey = apiKeyEdit.text.toString()
        if (::elevenKeyEdit.isInitialized) prefs.elevenKey = elevenKeyEdit.text.toString()
        refreshServices()
    }

    private fun requireKey(voiceId: String = prefs.mainVoice): Boolean {
        saveKey()
        if (prefs.hasKeyFor(voiceId)) return true
        toast(if (Providers.of(voiceId) == Providers.ELEVEN) "Paste your ElevenLabs key first." else "Paste your Fish Audio key first.")
        selectTab(2)
        val eleven = Providers.of(voiceId) == Providers.ELEVEN
        (if (eleven) elevenKeyBox else fishKeyBox).visibility = View.VISIBLE
        (if (eleven) elevenKeyEdit else apiKeyEdit).requestFocus()
        return false
    }

    private fun buildTabBar(parent: LinearLayout) {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(Look.bar)
        bar.setPadding(0, dp(6), 0, dp(10))
        val line = View(this)
        line.setBackgroundColor(Look.line)
        parent.addView(line, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)))
        parent.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        listOf("Listen", "Library", "Settings").forEachIndexed { i, t ->
            val tab = TextView(this)
            tab.text = t
            tab.gravity = Gravity.CENTER
            tab.textSize = 13f
            tab.letterSpacing = 0.02f
            tab.setPadding(0, dp(12), 0, dp(12))
            tab.setOnClickListener { selectTab(TAB_PAGES[i]) }
            bar.addView(tab, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            tabs.add(tab)
        }
        selectTab(0)
    }

    private fun selectTab(i: Int) {
        currentTab = i
        if (i == 1) renderSaved()
        if (::topBar.isInitialized) topBar.visibility = if (i == 0) View.VISIBLE else View.GONE
        pages.forEachIndexed { n, p -> p.visibility = if (n == i) View.VISIBLE else View.GONE }
        val tabIndex = if (i == 2) 2 else TAB_PAGES.indexOf(i)
        tabs.forEachIndexed { n, t ->
            val on = n == tabIndex
            t.setTextColor(if (on) Look.soft else mutedColor)
            t.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            t.background = if (on) tabMark() else null
        }
    }

    private fun tabMark(): android.graphics.drawable.Drawable {
        val bar = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(accent) }
        val layer = android.graphics.drawable.LayerDrawable(arrayOf(bar))
        layer.setLayerGravity(0, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        layer.setLayerSize(0, dp(28), dp(3))
        return layer
    }

    private fun showAddService() {
        for ((key, t) in segments) {
            val on = key == addService
            t.background = if (on) rounded(accent, 10) else null
            t.setTextColor(if (on) Color.WHITE else mutedColor)
        }
        when (addService) {
            Providers.FISH -> {
                addNameEdit.visibility = View.VISIBLE
                addLinkEdit.visibility = View.VISIBLE
                addLinkEdit.hint = "fish.audio voice link or ID"
                addPickBtn.visibility = View.GONE
                addBtn.visibility = View.VISIBLE
            }
            Providers.ELEVEN -> {
                addNameEdit.visibility = View.VISIBLE
                addLinkEdit.visibility = View.VISIBLE
                addLinkEdit.hint = "ElevenLabs voice ID (or pick below)"
                addPickBtn.visibility = View.VISIBLE
                addPickBtn.text = "Pick from my voices"
                addBtn.visibility = View.VISIBLE
            }
            else -> {
                addNameEdit.visibility = View.GONE
                addLinkEdit.visibility = View.GONE
                addPickBtn.visibility = View.VISIBLE
                addPickBtn.text = "Pick a phone voice"
                addBtn.visibility = View.GONE
            }
        }
    }

    private fun addVoice(name: String, id: String) {
        if (prefs.voices.any { it.id == id }) { toast("That voice is already in the list."); return }
        val list = prefs.voices.toMutableList()
        list.add(Voice(name.ifBlank { "Voice ${list.size + 1}" }, id))
        prefs.voices = list
        if (list.size == 1) prefs.mainVoice = id
        addNameEdit.setText("")
        addLinkEdit.setText("")
        renderVoices()
        toast("Added ${list.last().name}.")
    }

    private fun addTypedVoice() {
        val typed = addLinkEdit.text.toString()
        val name = addNameEdit.text.toString().trim()
        if (addService == Providers.ELEVEN) {
            val id = Regex("[A-Za-z0-9]{20}").find(typed)?.value
            if (id == null) toast("That doesn't look like an ElevenLabs voice ID.") else addVoice(name, "eleven:$id")
        } else {
            val id = Prefs.extractVoiceId(typed)
            if (!Prefs.isVoiceId(id)) toast("That doesn't look like a Fish voice link.") else addVoice(name, id)
        }
    }

    private fun pickVoice() {
        saveKey()
        if (addService == Providers.ELEVEN && prefs.elevenKey.isBlank()) {
            toast("Add your ElevenLabs key first (Connected services, below).")
            elevenKeyBox.visibility = View.VISIBLE
            elevenKeyEdit.requestFocus()
            return
        }
        toast("Loading voices...")
        val service = addService
        val key = prefs.elevenKey
        Thread {
            val result = try {
                Result.success(if (service == Providers.ELEVEN) Providers.elevenVoices(key) else PhoneTts.voices(this))
            } catch (e: Exception) {
                Result.failure(e)
            }
            handler.post {
                result.onFailure { toast(it.message ?: "Couldn't load voices.") }
                result.onSuccess { list ->
                    val choices = if (service == Providers.PHONE) listOf("Phone's default voice" to "") + list else list
                    if (choices.isEmpty()) { toast("No voices found."); return@onSuccess }
                    android.app.AlertDialog.Builder(this)
                        .setTitle(if (service == Providers.ELEVEN) "Your ElevenLabs voices" else "Voices on your phone")
                        .setItems(choices.map { it.first }.toTypedArray()) { _, i ->
                            val (label, id) = choices[i]
                            val name = addNameEdit.text.toString().trim().ifEmpty {
                                if (service == Providers.PHONE) "Phone · ${label.substringBefore(" ·")}" else label
                            }
                            addVoice(name, (if (service == Providers.ELEVEN) "eleven:" else "phone:") + id)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }.start()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun labelColor(): Int = textColor

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
        if (strokeColor != null) setStroke(dp(1), strokeColor)
    }

    private fun setOn(b: Button, on: Boolean) {
        b.isEnabled = on
        b.alpha = if (on) 1f else 0.4f
    }

    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(cardColor, 18)
        c.setPadding(dp(16), dp(14), dp(16), dp(16))
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(14)
        parent.addView(c, lp)
        label(c, title, 17f, bold = true)
        return c
    }

    private fun label(
        parent: LinearLayout, text: String, size: Float,
        bold: Boolean = false, color: Int? = null, top: Int = 0,
    ): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color ?: if (size <= 13f) mutedColor else textColor)
        t.setLineSpacing(0f, 1.15f)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(top)
        parent.addView(t, lp)
        return t
    }

    private fun row(parent: LinearLayout, top: Int = 6): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(top)
        parent.addView(r, lp)
        return r
    }

    private fun button(
        parent: LinearLayout, text: String, weight: Float = 0f, primary: Boolean = false, onClick: () -> Unit,
    ): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.textSize = 14f
        b.setTextColor(if (primary) Color.WHITE else textColor)
        b.background = rounded(if (primary) accent else Look.raise, 12)
        b.stateListAnimator = null
        b.minHeight = dp(44)
        b.minimumHeight = dp(44)
        b.setPadding(dp(12), 0, dp(12), 0)
        b.setOnClickListener { onClick() }
        val lp = if (weight > 0f) LinearLayout.LayoutParams(0, WRAP_CONTENT, weight)
        else LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        parent.addView(b, lp)
        return b
    }

    private fun edit(parent: LinearLayout, hint: String, value: String = ""): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(value)
        e.setSingleLine(true)
        e.setTextColor(textColor)
        e.setHintTextColor(mutedColor)
        e.background = rounded(fieldColor, 12, Look.fieldEdge)
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(6)
        parent.addView(e, lp)
        return e
    }
}
