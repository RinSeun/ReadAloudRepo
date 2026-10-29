package com.lss.lotusaloud

import android.app.Activity
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast

/**
 * Invisible screen opened by the "Lotus Read" Quick Settings tile.
 * Android only lets an app read the clipboard while one of its windows has focus,
 * so this briefly takes focus, reads what you copied, starts reading it, and closes.
 */
class ClipReadActivity : Activity() {
    private var handled = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true

        val text = try {
            val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
            if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this)?.toString() ?: "" else ""
        } catch (e: Exception) {
            ""
        }

        when {
            !Prefs(this).hasKeyFor() -> {
                Toast.makeText(this, Providers.missingKeyText(Prefs(this).mainVoice), Toast.LENGTH_LONG).show()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            text.isBlank() -> Toast.makeText(this, "Copy some text first, then tap Lotus Read.", Toast.LENGTH_LONG).show()
            else -> ReadService.read(this, text)
        }
        finish()
    }
}
