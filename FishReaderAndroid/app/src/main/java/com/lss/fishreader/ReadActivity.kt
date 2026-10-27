package com.lss.fishreader

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * Invisible screen behind the "Read aloud" button in the text-selection menu
 * (and "Fish Reader" in the Share menu). Hands the text to the reading service and closes.
 */
class ReadActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = when (intent?.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            else -> null
        } ?: ""

        when {
            Prefs(this).apiKey.isBlank() -> {
                Toast.makeText(this, "Add your Fish Audio API key first.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            text.isBlank() -> Toast.makeText(this, "Highlight some text first.", Toast.LENGTH_SHORT).show()
            else -> ReadService.read(this, text)
        }
        finish()
    }
}
