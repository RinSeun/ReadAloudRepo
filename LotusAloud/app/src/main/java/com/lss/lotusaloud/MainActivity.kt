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

/** Settings + status screen. */
class MainActivity : Activity() {
    companion object {
        const val EXTRA_ASK_SCREENSHOTS = "askScreenshots"
        private const val SCREENSHOT_PERMISSION_REQUEST = 7
    }

    private val accent = Color.parseColor("#FA1E4E")
    private val bgColor = Color.parseColor("#120E14")
    private val cardColor = Color.parseColor("#1E1820")
    private val fieldColor = Color.parseColor("#15101A")
    private val textColor = Color.parseColor("#F3EDF2")
    private val mutedColor = Color.parseColor("#A79BA6")
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
    private lateinit var bubbleSwitch: android.widget.Switch
    private lateinit var shotsSwitch: android.widget.Switch
    private lateinit var summaryText: TextView
    private lateinit var topBar: LinearLayout
    private var historyStamp = 0L
    private var waitingForOverlay = false
    private var settingSwitch = false // true while we update switches ourselves
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
    private var creditText = ""
    private var creditColor = 0
    private var breathing: android.animation.ObjectAnimator? = null

    private val poll = object : Runnable {
        override fun run() {
            refreshStatus()
            handler.postDelayed(this, 400)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val outer = LinearLayout(this)
        outer.orientation = LinearLayout.VERTICAL
        outer.setBackgroundColor(bgColor)
        setContentView(outer)
        topBar = LinearLayout(this)
        topBar.orientation = LinearLayout.VERTICAL
        topBar.setPadding(dp(16), dp(26), dp(16), dp(4))
        outer.addView(topBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val pageHolder = android.widget.FrameLayout(this)
        outer.addView(pageHolder, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        val (playerPage, voicesPage, settingsPage) = List(3) {
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
        var cur: LinearLayout

        // ---- header: name + "New" pill (stays on every tab)
        val header = row(topBar, 0)
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        header.addView(titles, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        label(titles, "Lotus Aloud", 26f, bold = true)
        label(titles, "MADE FOR LSS ONLY", 11f, bold = true, color = accent)
        val newPill = TextView(this)
        newPill.text = "\u2728 New"
        newPill.textSize = 13f
        newPill.setTextColor(Color.WHITE)
        newPill.setTypeface(newPill.typeface, Typeface.BOLD)
        newPill.background = rounded(Color.argb(40, 255, 255, 255), 99, Color.argb(50, 255, 255, 255))
        newPill.setPadding(dp(14), dp(7), dp(14), dp(7))
        newPill.setOnClickListener { showWhatsNew() }
        header.addView(newPill, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        // ---- the big glowing lotus (it gently "breathes" while reading)
        val hero = android.widget.FrameLayout(this)
        val glow = View(this)
        glow.background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = dp(110).toFloat()
            colors = intArrayOf(Color.argb(110, 250, 30, 78), Color.argb(0, 250, 30, 78))
        }
        hero.addView(glow, android.widget.FrameLayout.LayoutParams(dp(240), dp(240), Gravity.CENTER))
        heroLogo = android.widget.ImageView(this)
        heroLogo.setImageResource(resources.getIdentifier("ic_lotus_hero", "drawable", packageName))
        hero.addView(heroLogo, android.widget.FrameLayout.LayoutParams(dp(160), dp(160), Gravity.CENTER))
        val heroLp = LinearLayout.LayoutParams(MATCH_PARENT, dp(200))
        heroLp.topMargin = dp(4)
        playerPage.addView(hero, heroLp)
        heroLogo.setOnClickListener { startActivity(Intent(this, ReaderActivity::class.java)) }

        // ---- Fish credit balance (tap to refresh)
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
        // ---- status / controls
        cur = card(playerPage, "Now playing")
        statusText = label(cur, "Idle", 15f)
        val controls = row(cur)
        button(controls, "⏪ 10s", 1f) { player.skip(-10.0) }
        pauseBtn = button(controls, "Pause", 1f) { player.togglePause() }
        button(controls, "10s ⏩", 1f) { player.skip(10.0) }
        stopBtn = button(controls, "Stop", 1f) { player.stop() }
        button(row(cur), "\uD83D\uDCD6 Follow along", 1f) { startActivity(Intent(this, ReaderActivity::class.java)) }

        // ---- paste & read
        cur = card(playerPage, "Read text")
        val paste = edit(cur, "Paste or type text here")
        paste.setSingleLine(false)
        paste.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        paste.minLines = 3
        paste.gravity = Gravity.TOP or Gravity.START
        button(row(cur), "Read this", 1f, primary = true) {
            if (requireKey()) ReadService.read(this, paste.text.toString())
        }
        button(row(cur), "\uD83D\uDCF7 Read a screenshot or photo", 1f) { pickImage() }
        label(
            cur,
            "Paste a webpage link to hear the article. Or take a screenshot \u2192 Share \u2192 Lotus Aloud.",
            13f,
        )

        // ---- recent reads
        cur = card(playerPage, "Recent reads")
        label(cur, "Tap ▶ to hear one again. Replays are instant and free.", 13f)
        historyBox = LinearLayout(this)
        historyBox.orientation = LinearLayout.VERTICAL
        cur.addView(historyBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        renderHistory()

        // ---- Voices tab
        bigTitle(voicesPage, "Voices")
        sectionLabel(voicesPage, "MAIN VOICE")
        mainVoiceBox = LinearLayout(this)
        mainVoiceBox.orientation = LinearLayout.VERTICAL
        voicesPage.addView(mainVoiceBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        sectionLabel(voicesPage, "ALL VOICES \u00b7 TAP \u2605 TO MAKE MAIN")
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

        sectionLabel(voicesPage, "CONNECTED SERVICES")
        val services = group(voicesPage)
        val fish = serviceRow(services, "Fish Audio")
        fishStatus = fish.first
        fishKeyBox = fish.second
        apiKeyEdit = edit(fishKeyBox, "Paste your Fish Audio key", prefs.apiKey)
        apiKeyEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        linkLabel(fishKeyBox, "Get a key at fish.audio/app/api-keys", "https://fish.audio/app/api-keys")
        button(row(fishKeyBox), "Save key", 1f, primary = true) { saveKey(); checkCredit(force = true); toast("Saved.") }
        divider(services)
        val eleven = serviceRow(services, "ElevenLabs")
        elevenStatus = eleven.first
        elevenKeyBox = eleven.second
        elevenKeyEdit = edit(elevenKeyBox, "Paste your ElevenLabs key", prefs.elevenKey)
        elevenKeyEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        linkLabel(elevenKeyBox, "Get a key at elevenlabs.io \u2192 Developers \u2192 API keys", "https://elevenlabs.io/app/developers/api-keys")
        button(row(elevenKeyBox), "Save key", 1f, primary = true) { saveKey(); checkCredit(force = true); toast("Saved.") }
        divider(services)
        val phone = serviceRow(services, "Your phone")
        phone.first.text = "Built in \u00b7 free \u00b7 no key needed"
        (phone.first.tag as? View)?.background = rounded(Color.parseColor("#2EA043"), 99)
        refreshServices()
        label(voicesPage, "All voices are AI-generated. Hold a voice to remove it.", 12f, top = 10)

        // ---- Settings tab (Samsung-style: big title, grouped rows, switches)
        bigTitle(settingsPage, "Settings")
        summaryText = buildSummaryCard(settingsPage)

        sectionLabel(settingsPage, "READING")
        var g = group(settingsPage)
        // speed: big number, slider with - and +, tick marks
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
        seek.max = 15 // 0.5x .. 2.0x in 0.1 steps
        seek.progress = ((prefs.speed - 0.5f) * 10).toInt().coerceIn(0, 15)
        fun showSpeed() {
            val txt = String.format("%.1fx", 0.5f + seek.progress / 10f)
            val sp = android.text.SpannableString(txt)
            sp.setSpan(ForegroundColorSpan(Color.parseColor("#FF6B8A")), txt.length - 1, txt.length, 0)
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
        roundButton(slRow, "\u2212") { seek.progress = (seek.progress - 1).coerceAtLeast(0) }
        slRow.addView(seek, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        roundButton(slRow, "+") { seek.progress = (seek.progress + 1).coerceAtMost(15) }
        val ticks = row(speedBox, 2)
        ticks.setPadding(dp(46), 0, dp(46), 0)
        listOf("0.5", "1", "1.5", "2").forEachIndexed { n, t ->
            val tv = label(ticks, t, 11f, color = Color.parseColor("#7E7280"))
            tv.gravity = when (n) { 0 -> Gravity.START; 3 -> Gravity.END; else -> Gravity.CENTER }
            tv.layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
        }
        divider(g)
        // quality: three tap buttons
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
        // fish model: tap for a picker
        val modelRow = settingsRow(g, "Fish model", prefs.model, valuePink = true)
        modelRow.first.setOnClickListener { pickModel(modelRow.second) }

        sectionLabel(settingsPage, "SHORTCUTS")
        g = group(settingsPage)
        val bubbleRow = settingsRow(g, "Lotus bubble", "Tap it to read copied text", chevron = false)
        bubbleSwitch = switchOn(bubbleRow.first) { on -> if (on != (prefs.bubbleOn && BubbleService.canShow(this))) toggleBubble() }
        divider(g)
        val shotsRow = settingsRow(g, "Screenshot double-tap", "Bubble reads your latest one", chevron = false)
        shotsSwitch = switchOn(shotsRow.first) { on ->
            prefs.shotsOn = on
            if (on && !Screenshots.canRead(this)) askScreenshotAccess()
        }
        divider(g)
        val tileRow = settingsRow(g, "Quick Settings tile", "Swipe down \u2192 Lotus Read")
        tileRow.first.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) requestTile()
            else toast("Swipe down twice, tap \u270e (edit), and drag \u201cLotus Read\u201d into your tiles.")
        }

        sectionLabel(settingsPage, "HELP")
        g = group(settingsPage)
        settingsRow(g, "How to use", null).first.setOnClickListener { showHowTo() }
        divider(g)
        settingsRow(g, "Test my voice", null).first.setOnClickListener {
            if (requireKey()) ReadService.read(this, "Hello! Lotus Aloud is working.", record = false)
        }
        divider(g)
        settingsRow(g, "What's new", null).first.setOnClickListener { showWhatsNew() }

        val foot = label(settingsPage, "Lotus Aloud ${appVersion()}\nMade for LSS Only", 11f, color = Color.parseColor("#6D616F"), top = 18)
        foot.gravity = Gravity.CENTER
        refreshSwitches()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSwitches()
        if (intent?.getBooleanExtra(EXTRA_ASK_SCREENSHOTS, false) == true) {
            intent.removeExtra(EXTRA_ASK_SCREENSHOTS)
            selectTab(2)
            askScreenshotAccess()
        }
        handler.post(poll)
        renderHistory()
        if (waitingForOverlay) {
            waitingForOverlay = false
            if (BubbleService.canShow(this)) {
                BubbleService.start(this)
                toast("Lotus bubble is on!")
            } else {
                toast("Lotus Aloud still needs “Appear on top” to show the bubble.")
            }
        } else if (prefs.bubbleOn && BubbleService.canShow(this)) {
            BubbleService.start(this) // make sure it's running (e.g. after the phone restarts)
        }
        updateBubbleButton()
        checkCredit(force = false)
        // show "What's new" once after each update
        if (prefs.seenChanges < Changelog.ENTRIES.size) {
            prefs.seenChanges = Changelog.ENTRIES.size
            handler.post { showWhatsNew() }
        }
    }

    // ------------------------------------------------------------------ Fish credit
    private fun showCredit() {
        if (!::creditPill.isInitialized) return
        val key = prefs.apiKey
        val v = Credit.lastValue
        val service = Providers.of(prefs.mainVoice)
        val left = Credit.elevenLeft
        val (text, color) = when {
            service == Providers.PHONE -> "\uD83D\uDCF1 Phone voice \u00b7 free" to mutedColor
            service == Providers.ELEVEN && prefs.elevenKey.isBlank() -> "Add your ElevenLabs key below" to mutedColor
            service == Providers.ELEVEN && creditText.isNotEmpty() -> creditText to creditColor
            service == Providers.ELEVEN && left == null -> "Checking ElevenLabs..." to mutedColor
            service == Providers.ELEVEN && left != null && left < 1000 ->
                "\u26A0 ElevenLabs: only $left characters left" to Color.parseColor("#FFB020")
            service == Providers.ELEVEN -> "ElevenLabs: ${"%,d".format(left)} characters left" to mutedColor
            key.isBlank() -> "Add your Fish API key below" to mutedColor
            prefs.model.contains("free") -> "\uD83C\uDF81 Free model \u00b7 no credit used" to mutedColor
            creditText.isNotEmpty() -> creditText to creditColor
            v == null -> "Checking Fish credit..." to mutedColor
            v < Credit.LOW -> "\u26A0 Fish credit low: ${Credit.format(v)} \u00b7 tap to refresh" to Color.parseColor("#FFB020")
            else -> "Fish credit: ${Credit.format(v)}" to mutedColor
        }
        creditPill.text = text
        creditPill.setTextColor(color)
        refreshSummary()
    }

    /** Asks Fish for the balance (at most every 2 minutes unless you tap the pill). */
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

    // ------------------------------------------------------------------ big lotus
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

    // ------------------------------------------------------------------ what's new
    private fun showWhatsNew() {
        val text = Changelog.ENTRIES.joinToString("\n\n") { e ->
            e.title + "\n" + e.items.joinToString("\n") { "\u2022 $it" }
        }
        val body = TextView(this)
        body.text = text
        body.textSize = 14f
        body.setTextColor(labelColor())
        body.setPadding(dp(22), dp(8), dp(22), dp(8))
        val scroll = ScrollView(this)
        scroll.addView(body)
        android.app.AlertDialog.Builder(this)
            .setTitle("What's new in Lotus Aloud")
            .setView(scroll)
            .setPositiveButton("Got it", null)
            .setNeutralButton("Get latest version") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.RELEASES_URL)))
            }
            .show()
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        saveKey()
        super.onPause()
    }

    // ------------------------------------------------------------------ screenshots / photos
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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

    // ------------------------------------------------------------------ screenshot access (bubble double-tap)
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
            toast("Choose \u201cAllow all\u201d for photos so new screenshots can be read.")
        }
        refreshSwitches()
    }

    // ------------------------------------------------------------------ bubble
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

    /** Keeps the Settings switches in step with reality (e.g. after granting a permission). */
    private fun refreshSwitches() {
        if (::bubbleSwitch.isInitialized) {
            settingSwitch = true
            bubbleSwitch.isChecked = prefs.bubbleOn && BubbleService.canShow(this)
            shotsSwitch.isChecked = prefs.shotsOn && Screenshots.canRead(this)
            settingSwitch = false
        }
    }

    // ------------------------------------------------------------------ recent reads
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
            button(r, "\u25b6") { if (requireKey(item.voice)) ReadService.read(this, item.text, item.voice) }
        }
        button(row(historyBox), "Clear recent reads", 1f) {
            History.clear(this)
            renderHistory()
        }
    }

    // ------------------------------------------------------------------ tile
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

    // ------------------------------------------------------------------ voices
    private fun renderVoices() {
        val main = prefs.mainVoice
        val voices = prefs.voices

        // big "main voice" card
        mainVoiceBox.removeAllViews()
        val mv = voices.firstOrNull { it.id == main }
        val hero = LinearLayout(this)
        hero.orientation = LinearLayout.HORIZONTAL
        hero.gravity = Gravity.CENTER_VERTICAL
        hero.setPadding(dp(14), dp(14), dp(14), dp(14))
        hero.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#4A0F24"), Color.parseColor("#2A0E1B"), cardColor),
        ).apply { cornerRadius = dp(18).toFloat(); setStroke(dp(1), Color.argb(70, 250, 30, 78)) }
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

        // all voices
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
            star.text = if (v.id == main) "\u2605" else "\u2606"
            star.textSize = 21f
            star.setTextColor(if (v.id == main) Color.parseColor("#FFC94A") else Color.parseColor("#6D616F"))
            star.setPadding(dp(8), dp(4), dp(10), dp(4))
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

    // ------------------------------------------------------------------ settings tab pieces
    /** Big Samsung-style page title with a soft lotus glow behind it. */
    private fun bigTitle(parent: LinearLayout, text: String) {
        val box = android.widget.FrameLayout(this)
        box.background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.92f, 0f)
            gradientRadius = dp(230).toFloat()
            colors = intArrayOf(Color.argb(60, 250, 30, 78), Color.argb(0, 250, 30, 78))
        }
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

    /** "Lotus Aloud / Main voice X - credit" card that jumps to Voices. Returns its subtitle text. */
    private fun buildSummaryCard(parent: LinearLayout): TextView {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(dp(20), dp(16), dp(16), dp(16))
        c.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#34101E"), cardColor, cardColor),
        ).apply { cornerRadius = dp(24).toFloat(); setStroke(dp(1), Color.argb(64, 250, 30, 78)) }
        c.elevation = dp(6).toFloat()
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(8)
        parent.addView(c, lp)
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        c.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        label(t, "Lotus Aloud", 17f, bold = true)
        val sub = label(t, "", 13f)
        chevron(c)
        c.setOnClickListener { selectTab(1) }
        return sub
    }

    private fun refreshSummary() {
        if (!::summaryText.isInitialized) return
        val name = prefs.voices.firstOrNull { it.id == prefs.mainVoice }?.name ?: "Fish default"
        val credit = if (::creditPill.isInitialized) creditPill.text.toString() else ""
        val txt = "Main voice  $name" + if (credit.isNotBlank()) "  \u00b7  $credit" else ""
        val sp = android.text.SpannableString(txt)
        val st = txt.indexOf(name)
        sp.setSpan(ForegroundColorSpan(Color.parseColor("#FF6B8A")), st, st + name.length, 0)
        summaryText.text = sp
    }

    private fun chevron(parent: LinearLayout) {
        val ch = TextView(this)
        ch.text = "\u203A"
        ch.textSize = 22f
        ch.setTextColor(Color.parseColor("#6D616F"))
        ch.setPadding(dp(8), 0, 0, 0)
        parent.addView(ch)
    }

    /** A Samsung-style row: title + subtitle (or a pink value), optional chevron. Returns (row, subtitle). */
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
        val subView = label(t, sub ?: "", 13f, color = if (valuePink) Color.parseColor("#FF6B8A") else null)
        if (sub == null) subView.visibility = View.GONE
        if (chevron) chevron(r)
        return r to subView
    }

    /** Adds a divider + switch to the end of a row. */
    private fun switchOn(row: LinearLayout, onChange: (Boolean) -> Unit): android.widget.Switch {
        val bar = View(this)
        bar.setBackgroundColor(Color.argb(34, 255, 255, 255))
        val bl = LinearLayout.LayoutParams(dp(1), dp(28))
        bl.setMargins(dp(12), 0, dp(14), 0)
        row.addView(bar, bl)
        val sw = android.widget.Switch(this)
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = android.content.res.ColorStateList(states, intArrayOf(Color.WHITE, Color.WHITE))
        sw.trackTintList = android.content.res.ColorStateList(states, intArrayOf(accent, Color.parseColor("#4A3F4C")))
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
        b.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#2C2330")) }
        b.setOnClickListener { onClick() }
        parent.addView(b, LinearLayout.LayoutParams(dp(32), dp(32)))
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
                "\u2022 Highlight text in any app and tap \u201cRead audio\u201d (look under \u22ee if you don't see it).\n\n" +
                    "\u2022 Share text, a link or a screenshot to Lotus Aloud.\n\n" +
                    "\u2022 In Opera or Messenger: copy the text, then tap the lotus bubble. Double-tap it to read your latest screenshot. " +
                    "Tap it while reading to stop. Drag to move it, long-press to open the app.\n\n" +
                    "\u2022 Or copy text and tap \u201cLotus Read\u201d in your swipe-down panel.\n\n" +
                    "\u2022 Tap the notification, or the big lotus, to follow along with the text."
            )
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    // ------------------------------------------------------------------ voices tab pieces
    private fun serviceColor(id: String): Int = when (Providers.of(id)) {
        Providers.ELEVEN -> Color.parseColor("#B79BFF")
        Providers.PHONE -> Color.parseColor("#5EE0CF")
        else -> Color.parseColor("#FF7A97")
    }

    private fun serviceName(id: String, long: Boolean): String = when (Providers.of(id)) {
        Providers.ELEVEN -> "ELEVENLABS"
        Providers.PHONE -> "PHONE \u00b7 FREE"
        else -> if (long) "FISH AUDIO" else "FISH"
    }

    private fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    /** A round letter badge, coloured by service. */
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
        val b = TextView(this)
        b.text = "\u25B6"
        b.gravity = Gravity.CENTER
        b.textSize = if (big) 15f else 12f
        b.setTextColor(Color.WHITE)
        b.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (big) accent else Color.parseColor("#2C2330"))
        }
        b.setOnClickListener { onClick() }
        parent.addView(b, LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)))
    }

    private fun sectionLabel(parent: LinearLayout, text: String) {
        val t = TextView(this)
        t.text = text
        t.textSize = 11f
        t.letterSpacing = 0.08f
        t.setTypeface(t.typeface, Typeface.BOLD)
        t.setTextColor(Color.parseColor("#FF6B8A"))
        t.setPadding(dp(14), dp(18), dp(4), dp(7))
        parent.addView(t, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    /** A rounded box that holds rows. */
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

    /** A "connected service" row: status dot + name + status; tapping opens its key field. Returns (status text, key box). */
    private fun serviceRow(parent: LinearLayout, name: String): Pair<TextView, LinearLayout> {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(14), dp(12), dp(14), dp(12))
        parent.addView(r, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val dot = View(this)
        dot.background = rounded(Color.parseColor("#6D616F"), 99)
        r.addView(dot, LinearLayout.LayoutParams(dp(9), dp(9)))
        val t = LinearLayout(this)
        t.orientation = LinearLayout.VERTICAL
        t.setPadding(dp(12), 0, dp(8), 0)
        r.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        label(t, name, 15f, bold = true)
        val status = label(t, "", 12f)
        status.tag = dot
        val chev = TextView(this)
        chev.text = "\u203A"
        chev.textSize = 20f
        chev.setTextColor(Color.parseColor("#6D616F"))
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

    /** Green dot + "Connected" when a key is set. */
    private fun refreshServices() {
        if (!::fishStatus.isInitialized) return
        for ((status, has) in listOf(fishStatus to prefs.apiKey.isNotBlank(), elevenStatus to prefs.elevenKey.isNotBlank())) {
            status.text = if (has) "Connected" else "Not set up \u00b7 tap to add key"
            (status.tag as? View)?.background = rounded(Color.parseColor(if (has) "#2EA043" else "#6D616F"), 99)
        }
    }

    // ------------------------------------------------------------------ status
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
        pauseBtn.text = if (s.phase == Phase.PAUSED) "Resume" else "Pause"
        setOn(pauseBtn, s.phase == Phase.PLAYING || s.phase == Phase.PAUSED)
        setOn(stopBtn, s.phase == Phase.LOADING || s.phase == Phase.PLAYING || s.phase == Phase.PAUSED)
    }

    // ------------------------------------------------------------------ helpers
    private fun saveKey() {
        if (::apiKeyEdit.isInitialized) prefs.apiKey = apiKeyEdit.text.toString()
        if (::elevenKeyEdit.isInitialized) prefs.elevenKey = elevenKeyEdit.text.toString()
        refreshServices()
    }

    /** Makes sure the key for this voice (default: your main voice) is filled in. */
    private fun requireKey(voiceId: String = prefs.mainVoice): Boolean {
        saveKey()
        if (prefs.hasKeyFor(voiceId)) return true
        toast(if (Providers.of(voiceId) == Providers.ELEVEN) "Paste your ElevenLabs key first." else "Paste your Fish Audio key first.")
        selectTab(1)
        val eleven = Providers.of(voiceId) == Providers.ELEVEN
        (if (eleven) elevenKeyBox else fishKeyBox).visibility = View.VISIBLE
        (if (eleven) elevenKeyEdit else apiKeyEdit).requestFocus()
        return false
    }

    // ------------------------------------------------------------------ tabs
    private fun buildTabBar(parent: LinearLayout) {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setBackgroundColor(Color.parseColor("#0C080D"))
        bar.setPadding(0, dp(6), 0, dp(10))
        val line = View(this)
        line.setBackgroundColor(Color.parseColor("#2A2230"))
        parent.addView(line, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)))
        parent.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        listOf("\uD83E\uDEB7\nPlayer", "\uD83C\uDF99\nVoices", "\u2699\nSettings").forEachIndexed { i, t ->
            val tab = TextView(this)
            tab.text = t
            tab.gravity = Gravity.CENTER
            tab.textSize = 12f
            tab.setPadding(0, dp(6), 0, dp(4))
            tab.setOnClickListener { selectTab(i) }
            bar.addView(tab, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            tabs.add(tab)
        }
        selectTab(0)
    }

    private fun selectTab(i: Int) {
        if (::topBar.isInitialized) topBar.visibility = if (i == 0) View.VISIBLE else View.GONE
        pages.forEachIndexed { n, p -> p.visibility = if (n == i) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { n, t ->
            val on = n == i
            t.setTextColor(if (on) Color.parseColor("#FF6B8A") else mutedColor)
            t.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    // ------------------------------------------------------------------ adding voices
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

    /** Shows a list to pick from: your ElevenLabs voices, or the voices on your phone. */
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
                                if (service == Providers.PHONE) "Phone \u00b7 ${label.substringBefore(" \u00b7")}" else label
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

    /** A rounded section with a title. */
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
        b.background = rounded(if (primary) accent else Color.parseColor("#2C2330"), 12)
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
        e.background = rounded(fieldColor, 12, Color.parseColor("#3A2F3D"))
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(6)
        parent.addView(e, lp)
        return e
    }
}
