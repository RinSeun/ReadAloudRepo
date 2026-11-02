package com.lss.lotusaloud

import android.graphics.Color

/** The app's colors. Lotus is the original look; the others swap the accent and tint the greys to match. */
object Look {
    class Theme(val name: String, val accent: Int)

    val THEMES = listOf(
        Theme("Lotus", Color.parseColor("#FA1E4E")),
        Theme("Ocean", Color.parseColor("#2F81F7")),
        Theme("Jade", Color.parseColor("#1DB57A")),
        Theme("Violet", Color.parseColor("#9B5CFF")),
        Theme("Sunset", Color.parseColor("#FF7A1A")),
        Theme("Gold", Color.parseColor("#E0A82E")),
        Theme("Sakura", Color.parseColor("#FF7EB6")),
    )

    var index = 0; private set
    var black = false; private set

    var accent = 0; private set
    var soft = 0; private set
    var deep1 = 0; private set
    var deep2 = 0; private set
    var deep3 = 0; private set
    var edge = 0; private set
    var bg = 0; private set
    var card = 0; private set
    var field = 0; private set
    var raise = 0; private set
    var bar = 0; private set
    var line = 0; private set
    var fieldEdge = 0; private set
    var trackOff = 0; private set
    var text = 0; private set
    var muted = 0; private set
    var dim = 0; private set
    var faint = 0; private set

    init { set(0, false) }

    fun load(p: Prefs) = set(p.theme, p.pureBlack)

    fun set(themeIndex: Int, pureBlack: Boolean) {
        index = themeIndex.coerceIn(0, THEMES.size - 1)
        black = pureBlack
        val a = THEMES[index].accent
        accent = a
        val lotus = index == 0
        fun n(hex: String) = if (lotus) Color.parseColor(hex) else tint(Color.parseColor(hex), a)
        soft = if (lotus) Color.parseColor("#FF6B8A") else mix(a, Color.WHITE, 0.35)
        deep1 = if (lotus) Color.parseColor("#4A0F24") else mix(a, Color.BLACK, 0.74)
        deep2 = if (lotus) Color.parseColor("#2A0E1B") else mix(a, Color.BLACK, 0.85)
        deep3 = if (lotus) Color.parseColor("#34101E") else mix(a, Color.BLACK, 0.80)
        edge = if (lotus) Color.parseColor("#5A1A2E") else mix(a, Color.BLACK, 0.65)
        bg = n("#120E14")
        card = n("#1E1820")
        field = n("#15101A")
        raise = n("#2C2330")
        bar = n("#0C080D")
        line = n("#2A2230")
        fieldEdge = n("#3A2F3D")
        trackOff = n("#4A3F4C")
        text = n("#F3EDF2")
        muted = n("#A79BA6")
        dim = n("#6D616F")
        faint = n("#8E8290")
        if (black) {
            bg = Color.BLACK
            bar = Color.BLACK
            field = Color.parseColor("#0A0A0B")
            card = mix(card, Color.BLACK, 0.35)
            line = mix(line, Color.BLACK, 0.3)
        }
    }

    fun accentA(alpha: Int) = Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))

    fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).toInt().coerceIn(0, 255)
        return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    /** Keeps the brightness of a grey but gives it a hint of the accent color instead of Lotus purple. */
    private fun tint(c: Int, a: Int): Int {
        val grey = (Color.red(c) + Color.green(c) + Color.blue(c)) / 3
        val g = Color.rgb(grey, grey, grey)
        val lum = grey / 255.0
        return mix(g, a, if (lum > 0.5) 0.04 else 0.07)
    }
}
