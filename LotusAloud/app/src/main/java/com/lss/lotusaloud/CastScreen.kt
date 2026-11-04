package com.lss.lotusaloud

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class CastScreen(private val act: Activity, private val item: SavedRead, private val onClose: () -> Unit) {
    private val prefs = Prefs(act)
    private val main = Handler(Looper.getMainLooper())
    private val dlg = Dialog(act, android.R.style.Theme_DeviceDefault_NoActionBar)
    private lateinit var list: LinearLayout
    private lateinit var startBtn: Button
    private val firstTime = Saved.cast(act, item.id) == null
    private val setup = Saved.cast(act, item.id) ?: CastSetup(true, prefs.mainVoice, LinkedHashMap(), LinkedHashMap(), 0)
    private var scan: Cast.Scan? = null
    private var text = ""

    private val avatarColors = listOf("#FF7A97", "#B79BFF", "#5EE0CF", "#FFC94A", "#6CA8FF", "#7EE081", "#FF9F5A", "#E58CFF")
        .map { Color.parseColor(it) }

    fun show() {
        val root = LinearLayout(act)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Look.bg)

        val top = LinearLayout(act)
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(18), dp(10), dp(10), 0)
        val back = TextView(act)
        back.text = "‹  Library"
        back.textSize = 15f
        back.setTypeface(back.typeface, Typeface.BOLD)
        back.setTextColor(Look.soft)
        back.setPadding(dp(2), dp(10), dp(10), dp(10))
        back.setOnClickListener { dlg.dismiss() }
        top.addView(back, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        root.addView(top, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val scroll = ScrollView(act)
        list = LinearLayout(act)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(18), 0, dp(18), dp(24))
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(act)
        bottom.setPadding(dp(16), dp(10), dp(16), dp(16))
        bottom.setBackgroundColor(Look.bar)
        button(bottom, "Scan again", 1f) { rescan() }
        startBtn = button(bottom, "Start listening", 1.6f, primary = true) {
            save()
            dlg.dismiss()
            ReadService.readSaved(act, item.id)
        }
        root.addView(bottom, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        dlg.setContentView(root)
        dlg.window?.statusBarColor = Look.bg
        dlg.window?.navigationBarColor = Look.bar
        dlg.setOnDismissListener { save(); onClose() }
        Insets.apply(act, root)
        dlg.show()
        rescan()
    }

    private fun save() {
        scan?.let { setup.characters = it.characters.size }
        Saved.saveCast(act, item.id, setup)
    }

    private fun rescan() {
        list.removeAllViews()
        header()
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        box.setPadding(0, dp(50), 0, dp(50))
        box.addView(ProgressBar(act))
        val t = label(box, "Looking for the narrator and characters…", 14f, Look.muted, top = 14)
        t.gravity = Gravity.CENTER
        list.addView(box, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        startBtn.isEnabled = false
        startBtn.alpha = 0.5f
        val renames = HashMap(setup.renames)
        Thread({
            val raw = Saved.text(act, item.id) ?: ""
            val clean = TextCleaner.clean(SmartSkip.apply(raw, prefs))
            val result = try { Cast.scan(clean, renames) } catch (e: Exception) { null }
            main.post {
                if (!dlg.isShowing) return@post
                text = clean
                scan = result
                if (firstTime && result != null && setup.voices.isEmpty()) autoPick(result)
                save()
                render()
            }
        }, "lotus-cast-scan").start()
    }

    private fun autoPick(s: Cast.Scan) {
        val others = prefs.voices.map { it.id }.filter { it != setup.narrator }
        if (others.isEmpty()) return
        s.characters.forEachIndexed { i, c -> setup.voices[c.name] = others[i % others.size] }
        if (s.otherLines > 0) setup.voices[Cast.OTHER] = others[s.characters.size % others.size]
    }

    private fun header() {
        val title = label(list, "Voices", 32f, Look.text, top = 6)
        title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val sub = label(list, niceTitle(item.title), 14f, Look.muted, top = 0)
        sub.maxLines = 2
        sub.ellipsize = TextUtils.TruncateAt.END
    }

    private fun render() {
        list.removeAllViews()
        header()
        startBtn.isEnabled = true
        startBtn.alpha = 1f
        val s = scan
        if (s == null) {
            label(list, "Couldn't scan this one. Try Scan again.", 14f, Look.muted, top = 20)
            return
        }

        val summary = when {
            s.dialogue == 0 -> "No dialogue found. Multi-voice works best with stories and novels that use quote marks."
            s.characters.isEmpty() -> "Found ${s.dialogue} lines of dialogue, but no names to go with them."
            else -> "Found ${s.characters.size} character${if (s.characters.size == 1) "" else "s"} in ${s.dialogue} lines of dialogue."
        }
        label(list, summary, 14f, Look.text, top = 10)

        val switchCard = card()
        val sr = LinearLayout(act)
        sr.gravity = Gravity.CENTER_VERTICAL
        switchCard.addView(sr, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val st = LinearLayout(act)
        st.orientation = LinearLayout.VERTICAL
        sr.addView(st, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        label(st, "Read with multiple voices", 16f, Look.text, bold = true)
        label(st, if (setup.on) "Each character gets their own voice" else "Off · one voice reads everything", 12f, Look.muted, top = 2)
        val sw = Switch(act)
        sw.isChecked = setup.on
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        sw.thumbTintList = android.content.res.ColorStateList(states, intArrayOf(Color.WHITE, Color.WHITE))
        sw.trackTintList = android.content.res.ColorStateList(states, intArrayOf(Look.accent, Look.trackOff))
        sw.setOnCheckedChangeListener { _, on -> setup.on = on; save(); render() }
        sr.addView(sw)

        if (prefs.translateTo.isNotBlank()) {
            val warn = label(list, "Multi-voice reads in the original language. Set Language to Original in Settings to use it.", 13f, Color.parseColor("#FFB020"), top = 12)
            warn.setPadding(dp(4), 0, dp(4), 0)
        }

        val dim = !setup.on
        section("NARRATOR")
        person(
            name = "Narrator", key = Cast.NARRATOR, sub = "Everything outside the quotes",
            sample = firstNarration(s), color = Look.accent, dim = dim,
        )

        if (s.characters.isNotEmpty()) section("CHARACTERS")
        s.characters.forEachIndexed { i, c ->
            person(
                name = c.name, key = c.name, sub = "${c.lines} line${if (c.lines == 1) "" else "s"}",
                sample = c.sample, color = avatarColors[i % avatarColors.size], dim = dim,
            )
        }
        if (s.otherLines > 0) {
            section("EVERYONE ELSE")
            person(
                name = "Other voices", key = Cast.OTHER,
                sub = "${s.otherLines} line${if (s.otherLines == 1) "" else "s"} we couldn't match to a name",
                sample = s.otherSample, color = Look.dim, dim = dim,
            )
        }

        val tip = label(
            list,
            "Names come from lines like “Run!” Mia said. Tap a character to rename them, join two together, " +
                "or hand their lines to the narrator.",
            12f, Look.muted, top = 16,
        )
        tip.setPadding(dp(4), 0, dp(4), 0)
        if (setup.renames.isNotEmpty()) {
            val undo = label(list, "Undo name changes", 13f, Look.soft, bold = true, top = 10)
            undo.setPadding(dp(4), dp(6), dp(4), dp(6))
            undo.setOnClickListener {
                setup.renames.clear()
                rescan()
            }
        }
    }

    private fun firstNarration(s: Cast.Scan): String =
        s.segments.firstOrNull { it.speaker == Cast.NARRATOR && it.text.length > 30 }?.text
            ?.split(Regex("(?<=[.!?])\\s+"))?.firstOrNull()?.take(120) ?: text.take(120)

    private fun voiceFor(key: String): String? =
        if (key == Cast.NARRATOR) setup.narrator.takeIf { v -> prefs.voices.any { it.id == v } } ?: prefs.mainVoice
        else setup.voices[key]?.takeIf { v -> prefs.voices.any { it.id == v } }

    private fun voiceName(id: String?): String {
        if (id == null) return "Same as narrator"
        val v = prefs.voices.firstOrNull { it.id == id } ?: return "Same as narrator"
        val service = when (Providers.of(id)) {
            Providers.ELEVEN -> "ElevenLabs"
            Providers.PHONE -> "Phone"
            else -> "Fish"
        }
        return "${v.name} · $service"
    }

    private fun person(name: String, key: String, sub: String, sample: String, color: Int, dim: Boolean) {
        val c = card()
        c.alpha = if (dim) 0.55f else 1f

        val top = LinearLayout(act)
        top.gravity = Gravity.CENTER_VERTICAL
        c.addView(top, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val av = TextView(act)
        av.text = name.trimStart().take(1).uppercase()
        av.textSize = 17f
        av.gravity = Gravity.CENTER
        av.setTypeface(av.typeface, Typeface.BOLD)
        av.setTextColor(Color.WHITE)
        av.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        top.addView(av, LinearLayout.LayoutParams(dp(42), dp(42)))
        val mid = LinearLayout(act)
        mid.orientation = LinearLayout.VERTICAL
        mid.setPadding(dp(12), 0, dp(8), 0)
        top.addView(mid, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val n = label(mid, name, 16f, Look.text, bold = true)
        n.maxLines = 1
        n.ellipsize = TextUtils.TruncateAt.END
        label(mid, sub, 12f, Look.muted, top = 1)
        val play = View(act)
        play.background = android.graphics.drawable.LayerDrawable(arrayOf(
            GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Look.raise) },
            Glyph(Glyph.Kind.PLAY, Color.WHITE, dp(11).toFloat()),
        ))
        play.setOnClickListener {
            val v = voiceFor(key) ?: voiceFor(Cast.NARRATOR)!!
            if (!prefs.hasKeyFor(v)) toast(Providers.missingKeyText(v))
            else ReadService.read(act, sample.ifBlank { "Hello, I'm $name." }, v, record = false)
        }
        top.addView(play, LinearLayout.LayoutParams(dp(38), dp(38)))

        if (sample.isNotBlank()) {
            val q = label(c, "“${sample.trim().trim('“', '”', '"')}”", 13f, Look.faint, top = 10)
            q.setTypeface(q.typeface, Typeface.ITALIC)
            q.maxLines = 2
            q.ellipsize = TextUtils.TruncateAt.END
        }

        val pick = LinearLayout(act)
        pick.gravity = Gravity.CENTER_VERTICAL
        pick.background = rounded(Look.raise, 12)
        pick.setPadding(dp(14), dp(11), dp(12), dp(11))
        val plp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        plp.topMargin = dp(12)
        c.addView(pick, plp)
        val vl = label(pick, "Voice", 13f, Look.muted)
        vl.layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        val vn = TextView(act)
        vn.text = voiceName(voiceFor(key)) + "  ›"
        vn.textSize = 14f
        vn.setTypeface(vn.typeface, Typeface.BOLD)
        vn.setTextColor(Look.soft)
        vn.gravity = Gravity.END
        vn.maxLines = 1
        vn.ellipsize = TextUtils.TruncateAt.END
        pick.addView(vn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        pick.setOnClickListener { pickVoice(key, name) }

        if (key != Cast.NARRATOR && key != Cast.OTHER) c.setOnClickListener { characterMenu(key) }
    }

    private fun pickVoice(key: String, name: String) {
        val voices = prefs.voices
        if (voices.isEmpty()) { toast("Add a voice on the Voices page first."); return }
        val options = ArrayList<Pair<String, String?>>()
        if (key != Cast.NARRATOR) options.add("Same as narrator" to null)
        voices.forEach { options.add(voiceName(it.id) to it.id) }
        val current = voiceFor(key)
        val checked = options.indexOfFirst { it.second == current }.coerceAtLeast(0)
        AlertDialog.Builder(act)
            .setTitle("Voice for $name")
            .setSingleChoiceItems(options.map { it.first }.toTypedArray(), checked) { d, i ->
                val id = options[i].second
                if (key == Cast.NARRATOR) setup.narrator = id ?: prefs.mainVoice
                else if (id == null) setup.voices.remove(key) else setup.voices[key] = id
                save()
                d.dismiss()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun characterMenu(name: String) {
        val others = scan?.characters?.map { it.name }?.filter { it != name } ?: emptyList()
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions.add("Rename" to { renameCharacter(name) })
        if (others.isNotEmpty()) actions.add("Join with another character" to {
            AlertDialog.Builder(act)
                .setTitle("$name is the same person as…")
                .setItems(others.toTypedArray()) { _, i -> renameTo(name, others[i]) }
                .setNegativeButton("Cancel", null)
                .show()
        })
        actions.add("Let the narrator read their lines" to { renameTo(name, Cast.NARRATOR) })
        actions.add("Move their lines to Other voices" to { renameTo(name, Cast.OTHER) })
        AlertDialog.Builder(act)
            .setTitle(name)
            .setItems(actions.map { it.first }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun renameCharacter(name: String) {
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(22), dp(8), dp(22), 0)
        val e = EditText(act)
        e.setText(name)
        e.setSingleLine(true)
        e.setTextColor(Look.text)
        e.background = rounded(Look.field, 12, Look.fieldEdge)
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        box.addView(e, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        AlertDialog.Builder(act)
            .setTitle("Rename $name")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val n = e.text.toString().trim()
                if (n.isNotEmpty() && n != name && n != Cast.OTHER) renameTo(name, n)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renameTo(from: String, to: String) {
        setup.renames.remove(to)
        setup.renames[from] = to
        val voice = setup.voices.remove(from)
        if (voice != null && to != Cast.NARRATOR && !setup.voices.containsKey(to)) setup.voices[to] = voice
        save()
        rescan()
    }

    private fun niceTitle(t: String) = t.replace('_', ' ').replace(Regex("\\s+"), " ").trim()

    private fun section(text: String) {
        val t = label(list, text, 11f, Look.soft, bold = true, top = 20)
        t.letterSpacing = 0.08f
        t.setPadding(dp(4), 0, 0, dp(2))
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(act)
        c.orientation = LinearLayout.VERTICAL
        c.background = rounded(Look.card, 18)
        c.setPadding(dp(16), dp(14), dp(14), dp(14))
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(10)
        list.addView(c, lp)
        return c
    }

    private fun label(parent: LinearLayout, text: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0): TextView {
        val t = TextView(act)
        t.text = text
        t.textSize = size
        t.setTextColor(color)
        t.setLineSpacing(0f, 1.15f)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        lp.topMargin = dp(top)
        parent.addView(t, lp)
        return t
    }

    private fun button(parent: LinearLayout, text: String, weight: Float, primary: Boolean = false, onClick: () -> Unit): Button {
        val b = Button(act)
        b.text = text
        b.isAllCaps = false
        b.textSize = 15f
        b.setTextColor(if (primary) Color.WHITE else Look.text)
        b.background = rounded(if (primary) Look.accent else Look.raise, 12)
        b.stateListAnimator = null
        b.minHeight = dp(50)
        b.minimumHeight = dp(50)
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(0, WRAP_CONTENT, weight)
        lp.setMargins(dp(4), dp(3), dp(4), dp(3))
        parent.addView(b, lp)
        return b
    }

    private fun rounded(color: Int, r: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun toast(s: String) = Toast.makeText(act, s, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * act.resources.displayMetrics.density).toInt()
}
