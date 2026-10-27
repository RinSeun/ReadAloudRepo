package com.lss.fishreader

import android.Manifest
import android.app.Activity
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
import com.lss.fishreader.FishPlayer.Phase

/** Settings + status screen. */
class MainActivity : Activity() {
    private val accent = Color.parseColor("#FA1E4E")
    private val player get() = FishPlayer.shared
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var prefs: Prefs
    private lateinit var statusText: TextView
    private lateinit var pauseBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var voicesBox: LinearLayout
    private lateinit var apiKeyEdit: EditText

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
        root.setPadding(dp(20), dp(20), dp(20), dp(40))
        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        // ---- header
        label(root, "Fish Reader", 28f, bold = true)
        label(root, "MADE FOR LSS ONLY", 12f, bold = true, color = accent)
        label(
            root,
            "Highlight text in any app (Opera, Discord, Messages...) and tap “Read aloud”. " +
                "If you don't see it, tap ⋮ or the arrow in the menu that pops up. " +
                "You can also Share text to Fish Reader.",
            14f, top = 10,
        )

        // ---- status / controls
        heading(root, "Now playing")
        statusText = label(root, "Idle", 15f)
        val controls = row(root)
        pauseBtn = button(controls, "Pause", 1f) { player.togglePause() }
        stopBtn = button(controls, "Stop", 1f) { player.stop() }

        // ---- paste & read
        heading(root, "Read text")
        val paste = edit(root, "Paste or type text here")
        paste.setSingleLine(false)
        paste.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        paste.minLines = 3
        paste.gravity = Gravity.TOP or Gravity.START
        button(row(root), "Read this", 1f) {
            if (requireKey()) ReadService.read(this, paste.text.toString())
        }

        // ---- API key
        heading(root, "Fish Audio API key")
        apiKeyEdit = edit(root, "Paste your key", prefs.apiKey)
        apiKeyEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val link = label(root, "Get a key at fish.audio/app/api-keys", 14f, color = Color.parseColor("#1F6FEB"))
        link.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://fish.audio/app/api-keys"))) }

        // ---- voices
        heading(root, "Voices")
        label(root, "“Read aloud” uses your main voice. Paste a fish.audio voice link to add more.", 13f)
        voicesBox = LinearLayout(this)
        voicesBox.orientation = LinearLayout.VERTICAL
        root.addView(voicesBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        renderVoices()
        val nameEdit = edit(root, "Name (e.g. Narrator)")
        val linkEdit = edit(root, "Voice link or ID")
        button(row(root), "+ Add voice", 1f) {
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
        heading(root, "Model")
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
        root.addView(spinner, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        label(root, "Getting “No API credit”? Pick s2.1-pro-free.", 13f)

        // ---- quality
        heading(root, "Start speed vs. quality")
        val group = RadioGroup(this)
        val options = listOf("fast" to "Fastest start", "balanced" to "Balanced (recommended)", "quality" to "Best quality")
        options.forEachIndexed { i, (key, text) ->
            val rb = RadioButton(this)
            rb.id = 1000 + i
            rb.text = text
            group.addView(rb)
            if (prefs.quality == key) rb.isChecked = true
        }
        group.setOnCheckedChangeListener { _, checkedId -> prefs.quality = options[checkedId - 1000].first }
        root.addView(group)

        // ---- speed
        heading(root, "Speed")
        val speedLabel = label(root, "", 14f)
        val seek = SeekBar(this)
        seek.max = 15 // 0.5x .. 2.0x in 0.1 steps
        seek.progress = ((prefs.speed - 0.5f) * 10).toInt().coerceIn(0, 15)
        fun showSpeed() { speedLabel.text = String.format("%.1fx", 0.5f + seek.progress / 10f) }
        showSpeed()
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                showSpeed()
                prefs.speed = 0.5f + progress / 10f
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        root.addView(seek, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // ---- save / test
        val actions = row(root, 24)
        button(actions, "Save", 1f) {
            saveKey()
            toast("Saved.")
        }
        button(actions, "Save & test", 1f) {
            saveKey()
            if (requireKey()) ReadService.read(this, "Hello! Fish Reader is working.")
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        saveKey()
        super.onPause()
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
                val bg = GradientDrawable()
                bg.cornerRadius = dp(6).toFloat()
                bg.setColor(accent)
                mainBtn.background = bg
                mainBtn.setTextColor(Color.WHITE)
            }
            button(r, "▶") {
                saveKey()
                if (requireKey()) ReadService.read(this, "Hi! This is how ${v.name} sounds.", v.id)
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
        pauseBtn.isEnabled = s.phase == Phase.PLAYING || s.phase == Phase.PAUSED
        stopBtn.isEnabled = s.phase == Phase.LOADING || s.phase == Phase.PLAYING || s.phase == Phase.PAUSED
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

    private fun labelColor(): Int {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return if (night) Color.parseColor("#EDEDF0") else Color.parseColor("#1F1F24")
    }

    private fun label(
        parent: LinearLayout, text: String, size: Float,
        bold: Boolean = false, color: Int? = null, top: Int = 0,
    ): TextView {
        val t = TextView(this)
        t.text = text
        t.textSize = size
        t.setTextColor(color ?: labelColor())
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(top)
        parent.addView(t, lp)
        return t
    }

    private fun heading(parent: LinearLayout, text: String) = label(parent, text, 18f, bold = true, top = 24)

    private fun row(parent: LinearLayout, top: Int = 6): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(top)
        parent.addView(r, lp)
        return r
    }

    private fun button(parent: LinearLayout, text: String, weight: Float = 0f, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        val lp = if (weight > 0f) LinearLayout.LayoutParams(0, WRAP_CONTENT, weight)
        else LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        parent.addView(b, lp)
        return b
    }

    private fun edit(parent: LinearLayout, hint: String, value: String = ""): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(value)
        e.setSingleLine(true)
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(4)
        parent.addView(e, lp)
        return e
    }
}
