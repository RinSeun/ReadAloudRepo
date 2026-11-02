package com.lss.lotusaloud

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Report {
    private val REASONS = arrayOf(
        "Offensive or harmful audio",
        "Sounds like a real person without their permission",
        "Misleading or impersonating someone",
        "Wrong, broken or garbled audio",
        "Something else",
    )

    fun show(activity: Activity) {
        var reason = 0
        AlertDialog.Builder(activity)
            .setTitle("Report a problem with this audio")
            .setSingleChoiceItems(REASONS, 0) { _, i -> reason = i }
            .setPositiveButton("Next") { _, _ -> askNote(activity, REASONS[reason]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askNote(activity: Activity, reason: String) {
        val box = LinearLayout(activity)
        val dp = activity.resources.displayMetrics.density
        box.setPadding((22 * dp).toInt(), (8 * dp).toInt(), (22 * dp).toInt(), 0)
        val note = EditText(activity)
        note.hint = "Anything else we should know? (optional)"
        note.minLines = 2
        box.addView(note, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(activity)
            .setTitle(reason)
            .setMessage("This sends the reason, your note, the voice and the bit of text being read to Lotus Shield Studio. Nothing else.")
            .setView(box)
            .setPositiveButton("Send report") { _, _ -> send(activity, reason, note.text.toString().trim()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun details(activity: Activity, reason: String, note: String): String {
        val prefs = Prefs(activity)
        val voiceId = ReadSession.voice.ifBlank { prefs.mainVoice }
        val voiceName = prefs.voices.firstOrNull { it.id == voiceId }?.name ?: "?"
        val snippet = ReadSession.parts.getOrNull(ReadSession.index)?.replace(Regex("\\s+"), " ")?.take(300) ?: ""
        val version = try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        } catch (e: Exception) { "?" }
        return buildString {
            appendLine("Lotus Aloud report")
            appendLine("Reason: $reason")
            if (note.isNotBlank()) appendLine("Note: $note")
            appendLine("Voice: $voiceName (${Providers.label(voiceId)}: ${Providers.rawId(voiceId)})")
            appendLine("Model: ${prefs.model} · Mood: ${Moods.label(prefs.mood)} · Translate: ${Translate.label(prefs.translateTo)}")
            appendLine("Title: ${ReadSession.title.take(120)}")
            appendLine("Text: $snippet")
            append("App ${version} · Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }

    private fun send(activity: Activity, reason: String, note: String) {
        val text = details(activity, reason, note)
        val app = activity.applicationContext
        val main = Handler(Looper.getMainLooper())
        if (Edition.REPORT_WEBHOOK.isNotBlank()) {
            Thread {
                val ok = try {
                    val c = URL(Edition.REPORT_WEBHOOK).openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.connectTimeout = 15000
                    c.readTimeout = 15000
                    c.setRequestProperty("Content-Type", "application/json")
                    c.outputStream.use { it.write(JSONObject().put("content", text.take(1900)).toString().toByteArray()) }
                    val code = c.responseCode
                    c.disconnect()
                    code in 200..299
                } catch (e: Exception) {
                    false
                }
                main.post {
                    Toast.makeText(app, if (ok) "Thanks, your report was sent." else "Couldn't send the report. Check your internet and try again.", Toast.LENGTH_LONG).show()
                }
            }.start()
            return
        }
        if (Edition.SUPPORT_EMAIL.isNotBlank()) {
            val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(Edition.SUPPORT_EMAIL))
                .putExtra(Intent.EXTRA_SUBJECT, "Lotus Aloud report: $reason")
                .putExtra(Intent.EXTRA_TEXT, text)
            try {
                activity.startActivity(i)
                return
            } catch (e: Exception) {
            }
        }
        Toast.makeText(app, "Reporting isn't set up in this version yet.", Toast.LENGTH_LONG).show()
    }
}
