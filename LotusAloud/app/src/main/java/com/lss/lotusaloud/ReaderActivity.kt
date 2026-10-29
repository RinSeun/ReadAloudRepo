package com.lss.lotusaloud

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lss.lotusaloud.SpeechPlayer.Phase

/**
 * Follow-along screen: shows the text being read, highlights the current sentence,
 * and lets you tap any sentence to jump there.
 */
class ReaderActivity : Activity() {
    private val accent = Color.parseColor("#FA1E4E")
    private val bgColor = Color.parseColor("#120E14")
    private val cardColor = Color.parseColor("#1E1820")
    private val textColor = Color.parseColor("#F3EDF2")
    private val dimColor = Color.parseColor("#8E8290")
    private val handler = Handler(Looper.getMainLooper())
    private val player get() = SpeechPlayer.shared

    private class Sentence(val part: Int, val start: Int, val end: Int, val partStart: Int, val partLen: Int)

    private lateinit var scroll: ScrollView
    private lateinit var body: TextView
    private lateinit var titleView: TextView
    private lateinit var status: TextView
    private lateinit var playBtn: Button
    private lateinit var prevBtn: Button
    private lateinit var nextBtn: Button
    private lateinit var followBtn: Button

    private var sentences = listOf<Sentence>()
    private var builtVersion = -1
    private var current = -1
    private var follow = true
    private var lastUserScroll = 0L

    private val poll = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(bgColor)
        setContentView(root)

        // ---- top bar
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(dp(16), dp(24), dp(12), dp(8))
        root.addView(top, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val logo = android.widget.ImageView(this)
        logo.setImageResource(resources.getIdentifier("ic_bubble", "drawable", packageName))
        top.addView(logo, LinearLayout.LayoutParams(dp(36), dp(36)))
        titleView = TextView(this)
        titleView.setTextColor(textColor)
        titleView.textSize = 16f
        titleView.setTypeface(titleView.typeface, Typeface.BOLD)
        titleView.maxLines = 2
        titleView.setPadding(dp(12), 0, dp(8), 0)
        top.addView(titleView, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        followBtn = smallButton(top, "Following") {
            follow = !follow
            followBtn.text = if (follow) "Following" else "Follow"
            followBtn.alpha = if (follow) 1f else 0.6f
            if (follow) scrollToCurrent(force = true)
        }

        // ---- the text
        scroll = ScrollView(this)
        body = TextView(this)
        body.setTextColor(dimColor)
        body.textSize = 19f
        body.setLineSpacing(0f, 1.35f)
        body.setPadding(dp(20), dp(8), dp(20), dp(120))
        body.movementMethod = LinkMovementMethod.getInstance()
        body.highlightColor = Color.TRANSPARENT
        scroll.addView(body)
        // remember when *you* touch the text, so auto-scroll doesn't fight you
        scroll.setOnTouchListener { _, e ->
            if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN || e.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                lastUserScroll = System.currentTimeMillis()
            }
            false
        }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // ---- controls
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(dp(22f), dp(22f), dp(22f), dp(22f), 0f, 0f, 0f, 0f)
            setColor(cardColor)
        }
        panel.setPadding(dp(14), dp(12), dp(14), dp(18))
        root.addView(panel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        status = TextView(this)
        status.setTextColor(dimColor)
        status.textSize = 13f
        status.gravity = Gravity.CENTER
        panel.addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        panel.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        prevBtn = ctrl(row, "⏮") { ReadService.control(this, ReadService.ACTION_PREV_PART) }
        ctrl(row, "⏪ 10") { player.skip(-10.0) }
        playBtn = ctrl(row, "⏸", big = true) { player.togglePause() }
        ctrl(row, "10 ⏩") { player.skip(10.0) }
        nextBtn = ctrl(row, "⏭") { ReadService.control(this, ReadService.ACTION_NEXT_PART) }
    }

    override fun onResume() {
        super.onResume()
        handler.post(poll)
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
        super.onPause()
    }

    // ------------------------------------------------------------------ building the text
    private fun rebuild() {
        builtVersion = ReadSession.version
        current = -1
        val parts = ReadSession.parts
        titleView.text = ReadSession.title.ifBlank { "Lotus Aloud" }
        if (parts.isEmpty()) {
            sentences = emptyList()
            body.text = "Nothing is being read right now.\n\nHighlight text anywhere and tap “Read audio”, " +
                "or use the lotus bubble."
            return
        }
        val sb = StringBuilder()
        val list = mutableListOf<Sentence>()
        val splitter = Regex("[^.!?\\u2026\\n]+[.!?\\u2026]*[\"')\\]\\u201d]*\\s*|\\n+")
        parts.forEachIndexed { pi, part ->
            if (pi > 0) sb.append("\n\n")
            val partStart = sb.length
            for (m in splitter.findAll(part)) {
                val chunk = m.value
                if (chunk.isBlank()) continue
                val s = partStart + m.range.first
                list.add(Sentence(pi, s, partStart + m.range.last + 1, partStart, part.length))
            }
            sb.append(part)
        }
        sentences = list
        val span = SpannableString(sb.toString())
        list.forEachIndexed { i, sen ->
            span.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = jumpTo(i)
                override fun updateDrawState(ds: TextPaint) {} // keep normal look (no underline)
            }, sen.start, sen.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        body.setText(span, TextView.BufferType.SPANNABLE)
    }

    private fun jumpTo(i: Int) {
        val sen = sentences.getOrNull(i) ?: return
        val fraction = (sen.start - sen.partStart).toDouble() / sen.partLen.coerceAtLeast(1)
        ReadService.jump(this, sen.part, fraction)
        follow = true
        followBtn.text = "Following"
        followBtn.alpha = 1f
    }

    // ------------------------------------------------------------------ live highlight
    private fun refresh() {
        if (builtVersion != ReadSession.version) rebuild()
        val s = player.snapshot()
        val idx = ReadSession.index
        val count = ReadSession.count

        val partInfo = if (count > 1) "Part ${idx + 1} of $count  ·  " else ""
        val time = if (s.streaming || s.duration <= 0.0) ReadService.fmt(s.current)
        else "${ReadService.fmt(s.current)} / ${ReadService.fmt(s.duration)}"
        status.text = when (s.phase) {
            Phase.LOADING -> partInfo + "Generating..."
            Phase.PLAYING -> partInfo + time
            Phase.PAUSED -> partInfo + "Paused · " + time
            Phase.ENDED -> if (ReadSession.hasNext) partInfo + "Next part..." else "Done"
            Phase.ERROR -> s.message
            Phase.IDLE -> if (count > 0) "Stopped" else ""
        }
        playBtn.text = if (s.phase == Phase.PLAYING || s.phase == Phase.LOADING) "⏸" else "▶"
        prevBtn.alpha = if (count > 1) 1f else 0.35f
        nextBtn.alpha = if (ReadSession.hasNext) 1f else 0.35f

        // where are we? Speech runs at a fairly even pace, so time tells us roughly how far into the part we are.
        val part = ReadSession.parts.getOrNull(idx) ?: return
        val fraction = when {
            s.phase == Phase.ENDED -> 1.0
            s.duration > 0 && !s.streaming -> (s.current / s.duration).coerceIn(0.0, 1.0)
            else -> (s.current * 14.5 / part.length.coerceAtLeast(1)).coerceIn(0.0, 0.98) // ~14.5 characters a second
        }
        val busy = s.phase == Phase.PLAYING || s.phase == Phase.PAUSED || s.phase == Phase.LOADING || s.phase == Phase.ENDED
        val target = if (!busy) -1 else {
            val partSentences = sentences.withIndex().filter { it.value.part == idx }
            val pos = partSentences.firstOrNull()?.value?.partStart?.plus((fraction * part.length).toInt()) ?: -1
            partSentences.lastOrNull { it.value.start <= pos }?.index ?: partSentences.firstOrNull()?.index ?: -1
        }
        if (target != current) {
            current = target
            highlight(idx)
            scrollToCurrent(force = false)
        }
    }

    private fun highlight(partIdx: Int) {
        val text = body.text as? android.text.Spannable ?: return // edits show up straight away
        text.getSpans(0, text.length, ForegroundColorSpan::class.java).forEach { text.removeSpan(it) }
        text.getSpans(0, text.length, BackgroundColorSpan::class.java).forEach { text.removeSpan(it) }
        text.getSpans(0, text.length, StyleSpan::class.java).forEach { text.removeSpan(it) }
        // the current part reads brighter than the rest
        val inPart = sentences.filter { it.part == partIdx }
        if (inPart.isNotEmpty()) {
            text.setSpan(ForegroundColorSpan(textColor), inPart.first().start, inPart.last().end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val sen = sentences.getOrNull(current) ?: return
        text.setSpan(ForegroundColorSpan(Color.WHITE), sen.start, sen.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(BackgroundColorSpan(Color.argb(90, 250, 30, 78)), sen.start, sen.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(StyleSpan(Typeface.BOLD), sen.start, sen.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun scrollToCurrent(force: Boolean) {
        if (!follow) return
        if (!force && System.currentTimeMillis() - lastUserScroll < 4000) return // you're looking around - don't yank the view
        val sen = sentences.getOrNull(current) ?: return
        body.post {
            val layout = body.layout ?: return@post
            val line = layout.getLineForOffset(sen.start)
            val y = body.paddingTop + layout.getLineTop(line) - scroll.height / 3
            scroll.smoothScrollTo(0, y.coerceAtLeast(0))
        }
    }

    // ------------------------------------------------------------------ helpers
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun ctrl(parent: LinearLayout, label: String, big: Boolean = false, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = if (big) 22f else 15f
        b.setTextColor(Color.WHITE)
        b.stateListAnimator = null
        b.background = GradientDrawable().apply {
            shape = if (big) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
            cornerRadius = dp(14f)
            setColor(if (big) accent else Color.parseColor("#2C2330"))
        }
        b.setOnClickListener { onClick() }
        val size = if (big) dp(64) else WRAP_CONTENT
        val lp = LinearLayout.LayoutParams(if (big) size else 0, if (big) size else dp(48), if (big) 0f else 1f)
        lp.setMargins(dp(4), dp(8), dp(4), 0)
        parent.addView(b, lp)
        return b
    }

    private fun smallButton(parent: LinearLayout, label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.textSize = 12f
        b.setTextColor(Color.WHITE)
        b.stateListAnimator = null
        b.minHeight = 0
        b.minimumHeight = 0
        b.setPadding(dp(12), dp(6), dp(12), dp(6))
        b.background = GradientDrawable().apply { cornerRadius = dp(14f); setColor(accent) }
        b.setOnClickListener { onClick() }
        parent.addView(b, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        return b
    }
}
