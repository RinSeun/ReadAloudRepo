package com.lss.lotusaloud

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Saved Reads: documents, articles and long texts you can come back to.
 * Only the text and where you stopped are kept on the phone (no audio), so it stays small.
 */
data class SavedRead(
    val id: String,
    val title: String,
    val kind: String,
    val chars: Int,
    val fraction: Double,
    val done: Boolean,
    val voice: String,
    val lang: String,
    val added: Long,
    val opened: Long,
) {
    val percent get() = if (done) 100 else (fraction * 100).toInt().coerceIn(0, 99)
    val resumeFrom get() = if (done || fraction < 0.01) 0.0 else fraction
    val badge
        get() = when (kind) {
            KIND_PDF -> "PDF"
            KIND_WORD -> "WORD"
            KIND_BOOK -> "BOOK"
            KIND_WEB -> "WEB"
            else -> "TEXT"
        }

    companion object {
        const val KIND_PDF = "pdf"
        const val KIND_WORD = "word"
        const val KIND_BOOK = "epub"
        const val KIND_WEB = "web"
        const val KIND_TEXT = "text"
    }
}

/** Who reads what in a multi-voice book. Keys are character names; "" is the narrator, "?" is lines nobody was found for. */
class CastSetup(
    var on: Boolean,
    var narrator: String,
    val voices: MutableMap<String, String>,
    val renames: MutableMap<String, String>,
    var characters: Int,
)

object Saved {
    const val MAX = 40
    const val LONG_TEXT = 2500
    private const val MAX_TEXT_CHARS = 1_500_000
    private val lock = Any()

    private fun dir(ctx: Context) = File(ctx.filesDir, "saved").apply { mkdirs() }
    private fun index(ctx: Context) = File(dir(ctx), "index.json")
    private fun textFile(ctx: Context, id: String) = File(dir(ctx), "$id.txt")
    private fun castFile(ctx: Context, id: String) = File(dir(ctx), "$id.cast.json")

    /** The multi-voice setup for a saved read, or null if it was never scanned. */
    fun cast(ctx: Context, id: String): CastSetup? = try {
        val f = castFile(ctx, id)
        if (!f.exists()) null else {
            val o = JSONObject(f.readText())
            fun map(key: String): MutableMap<String, String> {
                val m = LinkedHashMap<String, String>()
                val j = o.optJSONObject(key) ?: return m
                j.keys().forEach { k -> m[k] = j.optString(k) }
                return m
            }
            CastSetup(o.optBoolean("on", true), o.optString("narrator"), map("voices"), map("renames"), o.optInt("characters"))
        }
    } catch (e: Exception) {
        null
    }

    fun saveCast(ctx: Context, id: String, c: CastSetup) {
        val o = JSONObject().put("on", c.on).put("narrator", c.narrator).put("characters", c.characters)
            .put("voices", JSONObject(c.voices as Map<*, *>)).put("renames", JSONObject(c.renames as Map<*, *>))
        castFile(ctx, id).writeText(o.toString())
    }

    fun list(ctx: Context): List<SavedRead> = synchronized(lock) {
        try {
            val f = index(ctx)
            if (!f.exists()) return emptyList()
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SavedRead(
                    o.getString("id"), o.optString("title"), o.optString("kind", SavedRead.KIND_TEXT),
                    o.optInt("chars"), o.optDouble("fraction", 0.0), o.optBoolean("done"),
                    o.optString("voice"), o.optString("lang"), o.optLong("added"), o.optLong("opened"),
                )
            }.sortedByDescending { it.opened }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun get(ctx: Context, id: String) = list(ctx).firstOrNull { it.id == id }

    fun text(ctx: Context, id: String): String? = try {
        textFile(ctx, id).takeIf { it.exists() }?.readText()
    } catch (e: Exception) {
        null
    }

    /** The saved read with exactly this text, if you've already added it (so it can carry on from your spot). */
    fun find(ctx: Context, text: String): SavedRead? {
        val id = hash(text.take(MAX_TEXT_CHARS))
        return list(ctx).firstOrNull { it.id == id }
    }

    /** Adds a read, or returns the one already saved with the same text (so it can carry on where it stopped). */
    fun add(ctx: Context, title: String, text: String, kind: String): SavedRead = synchronized(lock) {
        val body = text.take(MAX_TEXT_CHARS)
        val id = hash(body)
        val now = System.currentTimeMillis()
        val items = list(ctx).toMutableList()
        val existing = items.firstOrNull { it.id == id }
        val item = existing?.copy(opened = now)
            ?: SavedRead(id, title.replace('_', ' ').trim().ifBlank { "Untitled" }.take(120), kind, body.length, 0.0, false, "", "", now, now)
        if (existing == null) textFile(ctx, id).writeText(body)
        items.removeAll { it.id == id }
        items.add(0, item)
        while (items.size > MAX) {
            val old = items.removeAt(items.size - 1)
            textFile(ctx, old.id).delete()
            castFile(ctx, old.id).delete()
        }
        save(ctx, items)
        item
    }

    fun update(ctx: Context, id: String, change: (SavedRead) -> SavedRead) = synchronized(lock) {
        val items = list(ctx)
        if (items.none { it.id == id }) return
        save(ctx, items.map { if (it.id == id) change(it) else it })
    }

    fun remove(ctx: Context, id: String) = synchronized(lock) {
        castFile(ctx, id).delete()
        textFile(ctx, id).delete()
        save(ctx, list(ctx).filterNot { it.id == id })
    }

    fun clear(ctx: Context) = synchronized(lock) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }

    /** Changes whenever the shelf changes, so the screen knows when to redraw. */
    fun stamp(ctx: Context): Long = index(ctx).let { if (it.exists()) it.lastModified() * 31 + it.length() else 0L }

    fun bytes(ctx: Context): Long = dir(ctx).listFiles()?.sumOf { it.length() } ?: 0L

    fun kindFor(name: String?, mime: String?): String {
        val n = (name ?: "").lowercase()
        val m = mime ?: ""
        return when {
            m == "application/pdf" || n.endsWith(".pdf") -> SavedRead.KIND_PDF
            m.contains("epub") || n.endsWith(".epub") -> SavedRead.KIND_BOOK
            m.contains("wordprocessingml") || n.endsWith(".docx") -> SavedRead.KIND_WORD
            else -> SavedRead.KIND_TEXT
        }
    }

    fun titleFor(text: String): String {
        val first = text.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "Untitled"
        return if (first.length <= 60) first else first.take(57).trimEnd() + "…"
    }

    private fun save(ctx: Context, items: List<SavedRead>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title).put("kind", it.kind).put("chars", it.chars)
                    .put("fraction", it.fraction).put("done", it.done).put("voice", it.voice).put("lang", it.lang)
                    .put("added", it.added).put("opened", it.opened)
            )
        }
        val f = index(ctx)
        val tmp = File(f.parentFile, "index.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(f)
    }

    private fun hash(s: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        return d.take(10).joinToString("") { "%02x".format(it) }
    }
}
