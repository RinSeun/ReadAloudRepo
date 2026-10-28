package com.lss.lotusaloud

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

/**
 * Finds the text in screenshots and photos, on the phone (Google ML Kit - free, works offline).
 */
object ImageText {

    /** Copies a shared/picked image into the app's own storage so it can be read after the share screen closes. */
    fun copyToCache(ctx: Context, uri: Uri): String? {
        return try {
            val dir = File(ctx.cacheDir, "images").apply { mkdirs() }
            val out = File(dir, "img_${System.nanoTime()}")
            val input = ctx.contentResolver.openInputStream(uri) ?: return null
            input.use { inp -> out.outputStream().use { inp.copyTo(it) } }
            if (out.length() > 0) out.absolutePath else { out.delete(); null }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns the text in the images, in reading order. Runs on a background thread (it blocks).
     * Deletes the copied image files afterwards.
     */
    fun recognize(ctx: Context, paths: List<String>): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val parts = mutableListOf<String>()
        try {
            for (path in paths) {
                val file = File(path)
                try {
                    val image = InputImage.fromFilePath(ctx, Uri.fromFile(file))
                    val result = Tasks.await(recognizer.process(image))
                    val h = image.height
                    val w = image.width
                    val isScreenshot = h > w * 1.6 // tall phone screenshot
                    val blocks = result.textBlocks
                        .filter { b ->
                            val box = b.boundingBox
                            // skip the status bar (clock, battery...) at the very top of screenshots
                            !(isScreenshot && box != null && box.bottom < h * 0.045)
                        }
                        .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                        .map { it.text.trim() }
                        .filter { it.isNotEmpty() }
                    if (blocks.isNotEmpty()) parts.add(blocks.joinToString("\n\n"))
                } catch (e: Exception) {
                    // unreadable image - skip it
                } finally {
                    file.delete()
                }
            }
        } finally {
            recognizer.close()
        }
        return parts.joinToString("\n\n")
    }
}
