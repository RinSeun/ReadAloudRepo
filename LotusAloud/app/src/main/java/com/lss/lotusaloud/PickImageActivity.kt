package com.lss.lotusaloud

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast

class PickImageActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        if (Edition.needsAgreement(this)) { Edition.askToAgree(this); return }
        val i = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES)
        else Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        try {
            startActivityForResult(i, 1)
        } catch (e: Exception) {
            try {
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 1)
            } catch (e2: Exception) {
                finish()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode == RESULT_OK && uri != null) {
            val path = ImageText.copyToCache(this, uri)
            when {
                path == null -> Toast.makeText(this, "Couldn't open that image.", Toast.LENGTH_SHORT).show()
                !Prefs(this).hasKeyFor() -> Toast.makeText(this, Providers.missingKeyText(Prefs(this).mainVoice), Toast.LENGTH_LONG).show()
                else -> {
                    Toast.makeText(this, "Finding the text...", Toast.LENGTH_SHORT).show()
                    ReadService.readImages(this, listOf(path))
                }
            }
        }
        finish()
    }
}
