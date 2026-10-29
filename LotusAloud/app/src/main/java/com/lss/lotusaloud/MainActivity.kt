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
    private lateinit var historyBox: LinearLayout
    private lateinit var bubbleBtn: Button
    private lateinit var shotsBtn: Button
    private var historyStamp = 0L
    private var waitingForOverlay = false
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

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(28), dp(16), dp(40))
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bgColor)
        scroll.addView(root)
        setContentView(scroll)
        var cur: LinearLayout

        // ---- header: name + "New" pill
        val header = row(root, 0)
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
        root.addView(hero, heroLp)
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
        root.addView(creditRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        showCredit()
        label(
            root,
            "Highlight text in any app (Opera, Discord, Messages...) and tap “Read audio”. " +
                "If you don't see it, tap ⋮ or the arrow in the menu that pops up. " +
                "In Opera or Messenger: copy the text, then tap the floating lotus bubble.",
            14f, top = 10,
        )

        // ---- status / controls
        cur = card(root, "Now playing")
        statusText = label(cur, "Idle", 15f)
        val controls = row(cur)
        button(controls, "⏪ 10s", 1f) { player.skip(-10.0) }
        pauseBtn = button(controls, "Pause", 1f) { player.togglePause() }
        button(controls, "10s ⏩", 1f) { player.skip(10.0) }
        stopBtn = button(controls, "Stop", 1f) { player.stop() }
        button(row(cur), "\uD83D\uDCD6 Follow along", 1f) { startActivity(Intent(this, ReaderActivity::class.java)) }

        // ---- paste & read
        cur = card(root, "Read text")
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
            root,
            "Paste a webpage link to hear the article. Or take a screenshot \u2192 Share \u2192 Lotus Aloud.",
            13f,
        )

        // ---- recent reads
        cur = card(root, "Recent reads")
        label(cur, "Tap ▶ to hear one again. Replays are instant and free.", 13f)
        historyBox = LinearLayout(this)
        historyBox.orientation = LinearLayout.VERTICAL
        cur.addView(historyBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        renderHistory()

        // ---- floating bubble + Quick Settings tile (for apps like Opera & Messenger that hide "Read audio")
        cur = card(root, "Opera, Messenger & other apps")
        label(
            root,
            "Some apps hide “Read audio”. In those, copy the text (Messenger: long-press the message → Copy), " +
                "then tap the floating lotus bubble. Double-tap it to read your latest screenshot. Tap it while reading to stop. " +
                "Drag it to move it; long-press it to open this app.",
            14f,
        )
        bubbleBtn = button(row(cur), "", 1f, primary = true) { toggleBubble() }
        updateBubbleButton()
        shotsBtn = button(row(cur), "Allow double-tap screenshot reading", 1f) { askScreenshotAccess() }
        shotsBtn.visibility = if (Screenshots.canRead(this)) View.GONE else View.VISIBLE
        label(cur, "Prefer swiping down? Use the “Lotus Read” Quick Settings tile the same way.", 13f, top = 8)
        if (Build.VERSION.SDK_INT >= 33) {
            button(row(cur), "Add “Lotus Read” tile to Quick Settings", 1f) { requestTile() }
        } else {
            label(cur, "To add it: swipe down twice, tap ✎ (edit), and drag “Lotus Read” into your tiles.", 13f)
        }

        // ---- API key
        cur = card(root, "Fish Audio API key")
        apiKeyEdit = edit(cur, "Paste your key", prefs.apiKey)
        apiKeyEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val link = label(cur, "Get a key at fish.audio/app/api-keys", 14f, color = Color.parseColor("#1F6FEB"))
        link.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://fish.audio/app/api-keys"))) }

        // ---- voices
        cur = card(root, "Voices")
        label(cur, "“Read audio” uses your main voice. Paste a fish.audio voice link to add more.", 13f)
        voicesBox = LinearLayout(this)
        voicesBox.orientation = LinearLayout.VERTICAL
        cur.addView(voicesBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        renderVoices()
        val nameEdit = edit(cur, "Name (e.g. Narrator)")
        val linkEdit = edit(cur, "Voice link or ID")
        button(row(cur), "+ Add voice", 1f) {
            val id = Prefs.extractVoiceId(linkEdit.text.toString())
            when {
                !Prefs.isVoiceId(id) -> toast("That doesn't look like a Fish voice link.")
                prefs.voices.any { it.id == id } -> toast("That voice is already in the list.")
                else -> {
                    val list = prefs.voices.toMutableList()
                    val name = nameEdit.text.toString().trim().ifEmpty { "Voice ${list.size + 1}" }
                    list.add(Voice(name, id))
                    prefs.voices = list
                    if (list.size == 1) prefs.mainVoice = id
                    nameEdit.setText("")
                    linkEdit.setText("")
                    renderVoices()
                    toast("Added $name.")
                }
            }
        }

        // ---- model
        cur = card(root, "Model")
        val models = Prefs.MODELS.toMutableList()
        if (prefs.model !in models) models.add(prefs.model)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, models)
        spinner.setSelection(models.indexOf(prefs.model))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.model = models[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        cur.addView(spinner, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        label(cur, "Getting “No API credit”? Pick s2.1-pro-free.", 13f)

        // ---- quality
        cur = card(root, "Start speed vs. quality")
        val group = RadioGroup(this)
        val options = listOf("fast" to "Fastest start", "balanced" to "Balanced (recommended)", "quality" to "Best quality")
        options.forEachIndexed { i, (key, text) ->
            val rb = RadioButton(this)
            rb.id = 1000 + i
            rb.text = text
            rb.setTextColor(textColor)
            group.addView(rb)
            if (prefs.quality == key) rb.isChecked = true
        }
        group.setOnCheckedChangeListener { _, checkedId -> prefs.quality = options[checkedId - 1000].first }
        cur.addView(group)

        // ---- speed
        cur = card(root, "Speed (changes instantly, even while reading)")
        val speedLabel = label(cur, "", 14f)
        val seek = SeekBar(this)
        seek.max = 15 // 0.5x .. 2.0x in 0.1 steps
        seek.progress = ((prefs.speed - 0.5f) * 10).toInt().coerceIn(0, 15)
        fun showSpeed() { speedLabel.text = String.format("%.1fx", 0.5f + seek.progress / 10f) }
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
        cur.addView(seek, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // ---- save / test
        val actions = row(cur, 24)
        button(actions, "Save", 1f) {
            saveKey()
            checkCredit(force = true)
            toast("Saved.")
        }
        button(actions, "Save & test", 1f, primary = true) {
            saveKey()
            if (requireKey()) ReadService.read(this, "Hello! Lotus Aloud is working.", record = false)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::shotsBtn.isInitialized) shotsBtn.visibility = if (Screenshots.canRead(this)) View.GONE else View.VISIBLE
        if (intent?.getBooleanExtra(EXTRA_ASK_SCREENSHOTS, false) == true) {
            intent.removeExtra(EXTRA_ASK_SCREENSHOTS)
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
        val (text, color) = when {
            key.isBlank() -> "Add your Fish API key below" to mutedColor
            prefs.model.contains("free") -> "\uD83C\uDF81 Free model \u00b7 no credit used" to mutedColor
            creditText.isNotEmpty() -> creditText to creditColor
            v == null -> "Checking Fish credit..." to mutedColor
            v < Credit.LOW -> "\u26A0 Fish credit low: ${Credit.format(v)} \u00b7 tap to refresh" to Color.parseColor("#FFB020")
            else -> "Fish credit: ${Credit.format(v)}" to mutedColor
        }
        creditPill.text = text
        creditPill.setTextColor(color)
    }

    /** Asks Fish for the balance (at most every 2 minutes unless you tap the pill). */
    private fun checkCredit(force: Boolean) {
        val key = prefs.apiKey
        if (key.isBlank()) { showCredit(); return }
        if (!force && System.currentTimeMillis() - Credit.lastChecked < 2 * 60 * 1000L) { showCredit(); return }
        Credit.lastChecked = System.currentTimeMillis()
        creditText = if (force) "Checking..." else ""
        creditColor = mutedColor
        showCredit()
        Thread {
            val msg = try { Credit.fetch(key); "" } catch (e: Exception) { "Fish credit: ${e.message}" }
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
        if (::shotsBtn.isInitialized) shotsBtn.visibility = if (Screenshots.canRead(this)) View.GONE else View.VISIBLE
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

    private fun updateBubbleButton() {
        if (!::bubbleBtn.isInitialized) return
        bubbleBtn.text = if (prefs.bubbleOn && BubbleService.canShow(this)) "Hide floating bubble" else "Show floating lotus bubble"
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
            button(r, "▶") { if (requireKey()) ReadService.read(this, item.text, item.voice) }
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
        voicesBox.removeAllViews()
        val main = prefs.mainVoice
        for (v in prefs.voices) {
            val r = row(voicesBox, 4)
            val name = TextView(this)
            name.text = v.name
            name.textSize = 15f
            if (v.id == main) name.setTypeface(name.typeface, Typeface.BOLD)
            r.addView(name, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

            val mainBtn = button(r, if (v.id == main) "★ Main" else "Make main") {
                prefs.mainVoice = v.id
                renderVoices()
                toast("${v.name} is now your main voice.")
            }
            if (v.id == main) {
                mainBtn.isEnabled = false
                mainBtn.background = rounded(accent, 12)
                mainBtn.setTextColor(Color.WHITE)
            }
            button(r, "▶") {
                saveKey()
                if (requireKey()) ReadService.read(this, "Hi! This is how ${v.name} sounds.", v.id, record = false)
            }
            button(r, "✕") {
                prefs.voices = prefs.voices.filter { it.id != v.id }
                renderVoices()
            }
        }
        if (prefs.voices.isEmpty()) label(voicesBox, "No voices yet - Fish's default voice will be used.", 13f)
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
    }

    private fun requireKey(): Boolean {
        if (prefs.apiKey.isBlank()) {
            toast("Paste your Fish Audio API key first.")
            apiKeyEdit.requestFocus()
            return false
        }
        return true
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
