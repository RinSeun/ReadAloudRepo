package com.lss.lotusaloud

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast

class ReadActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val i = intent
        val isImage = i?.type?.startsWith("image/") == true

        val docUri: Uri? = when (i?.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> if (!isImage && i.getCharSequenceExtra(Intent.EXTRA_TEXT).isNullOrBlank()) imageUris(i).firstOrNull() else null
            else -> null
        }

        if (!Prefs(this).hasKeyFor()) {
            Toast.makeText(this, Providers.missingKeyText(Prefs(this).mainVoice), Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        if (docUri != null) {
            val name = Documents.displayName(this, docUri)
            val path = ImageText.copyToCache(this, docUri)
            if (path == null) {
                Toast.makeText(this, "Couldn't open that file.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Opening ${name ?: "your document"}...", Toast.LENGTH_SHORT).show()
                ReadService.readDocument(this, path, i?.type, name)
            }
            finish()
            return
        }

        if (isImage) {
            val uris = imageUris(i!!)
            val paths = uris.mapNotNull { ImageText.copyToCache(this, it) }
            if (paths.isEmpty()) {
                Toast.makeText(this, "Couldn't open that image.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Finding the text...", Toast.LENGTH_SHORT).show()
                ReadService.readImages(this, paths)
            }
            finish()
            return
        }

        val text = when (i?.action) {
            Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            Intent.ACTION_SEND -> i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            else -> null
        } ?: ""
        if (text.isBlank()) Toast.makeText(this, "Highlight some text first.", Toast.LENGTH_SHORT).show()
        else ReadService.read(this, text)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun imageUris(i: Intent): List<Uri> = when (i.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        )
        Intent.ACTION_SEND_MULTIPLE ->
            (if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM))?.toList() ?: emptyList()
        else -> emptyList()
    }
}
