package com.lss.lotusaloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.zip.ZipFile

object Documents {
    const val MAX_PDF_PAGES = 120

    val MIME_TYPES = arrayOf(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain",
        "text/markdown",
        "application/epub+zip",
    )

    class Doc(val title: String, val text: String, val kind: String = SavedRead.KIND_TEXT)

    fun isDocument(mime: String?, name: String?): Boolean {
        val m = mime ?: ""
        val n = (name ?: "").lowercase()
        return m == "application/pdf" || m.contains("wordprocessingml") || m.startsWith("text/") ||
            m.contains("epub") || n.endsWith(".epub") ||
            n.endsWith(".pdf") || n.endsWith(".docx") || n.endsWith(".txt") || n.endsWith(".md")
    }

    fun displayName(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    fun extract(ctx: Context, path: String, mime: String?, name: String?, progress: (Int, Int) -> Unit): Doc {
        val file = File(path)
        try {
            val n = (name ?: "").lowercase()
            var title = (name ?: "Document").substringBeforeLast('.')
            val isPdf = mime == "application/pdf" || n.endsWith(".pdf") || looksLikePdf(file)
            val isEpub = !isPdf && ((mime ?: "").contains("epub") || n.endsWith(".epub") || (looksLikeZip(file) && isEpubZip(file)))
            val isWord = !isPdf && !isEpub && ((mime ?: "").contains("wordprocessingml") || n.endsWith(".docx") || looksLikeZip(file))
            val text = when {
                isPdf -> pdf(ctx, file, progress)
                isEpub -> epub(file, progress).also { (t, _) -> if (!t.isNullOrBlank()) title = t }.second
                isWord -> docx(file)
                else -> file.readText(Charsets.UTF_8)
            }
            if (text.isBlank()) throw ProviderError("Couldn't find any text in that document.")
            val kind = when {
                isPdf -> SavedRead.KIND_PDF
                isEpub -> SavedRead.KIND_BOOK
                isWord -> SavedRead.KIND_WORD
                else -> SavedRead.KIND_TEXT
            }
            return Doc(title, text, kind)
        } finally {
            file.delete()
        }
    }

    private fun looksLikePdf(f: File): Boolean = try {
        f.inputStream().use { val b = ByteArray(5); it.read(b) == 5 && String(b) == "%PDF-" }
    } catch (e: Exception) { false }

    private fun looksLikeZip(f: File): Boolean = try {
        f.inputStream().use { val b = ByteArray(2); it.read(b) == 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() }
    } catch (e: Exception) { false }

    private val PARAGRAPH_END = Regex("</w:p>")
    private val RUN_TEXT = Regex("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|<w:tab/>|<w:br/>")

    private fun docx(file: File): String {
        val xml = try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: throw ProviderError("That Word file looks empty.")
                zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: ProviderError) {
            throw e
        } catch (e: Exception) {
            throw ProviderError("Couldn't open that Word file. Only .docx works (not old .doc files).")
        }
        return xml.split(PARAGRAPH_END).map { para ->
            RUN_TEXT.findAll(para).joinToString("") { m ->
                when (m.value) {
                    "<w:tab/>" -> " "
                    "<w:br/>" -> "\n"
                    else -> unescape(m.groupValues[1])
                }
            }.trim()
        }.filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    private fun isEpubZip(f: File): Boolean = try {
        ZipFile(f).use { it.getEntry("META-INF/container.xml") != null }
    } catch (e: Exception) { false }

    private val OPF_PATH = Regex("full-path=\"([^\"]+)\"")
    private val DC_TITLE = Regex("<dc:title[^>]*>([^<]+)</dc:title>")
    private val MANIFEST_ITEM = Regex("<item\\s[^>]*>")
    private val SPINE_REF = Regex("<itemref\\s[^>]*idref=\"([^\"]+)\"")
    private fun attr(tag: String, name: String) = Regex("\\s$name=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)

    private fun epub(file: File, progress: (Int, Int) -> Unit): Pair<String?, String> {
        try {
            ZipFile(file).use { zip ->
                fun read(path: String) = zip.getEntry(path)?.let { e -> zip.getInputStream(e).bufferedReader(Charsets.UTF_8).use { it.readText() } }
                val container = read("META-INF/container.xml") ?: throw ProviderError("That book looks empty.")
                val opfPath = OPF_PATH.find(container)?.groupValues?.get(1) ?: throw ProviderError("Couldn't open that book.")
                val opf = read(opfPath) ?: throw ProviderError("Couldn't open that book.")
                if (zip.getEntry("META-INF/encryption.xml") != null && zip.getEntry("META-INF/rights.xml") != null) {
                    throw ProviderError("That book is copy-protected (DRM), so it can't be read aloud.")
                }
                val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
                val title = DC_TITLE.find(opf)?.groupValues?.get(1)?.let { unescape(it).trim() }
                val hrefs = HashMap<String, String>()
                MANIFEST_ITEM.findAll(opf).forEach { m ->
                    val id = attr(m.value, "id")
                    val href = attr(m.value, "href")
                    if (id != null && href != null) hrefs[id] = href
                }
                val order = SPINE_REF.findAll(opf).mapNotNull { hrefs[it.groupValues[1]] }.toList()
                val chapters = ArrayList<String>()
                order.forEachIndexed { i, href ->
                    progress(i + 1, order.size)
                    val path = normalize(base + java.net.URLDecoder.decode(href.substringBefore('#'), "UTF-8"))
                    val html = read(path) ?: return@forEachIndexed
                    val text = htmlText(html)
                    if (text.isNotBlank()) chapters.add(text)
                }
                return title to chapters.joinToString("\n\n")
            }
        } catch (e: ProviderError) {
            throw e
        } catch (e: Exception) {
            throw ProviderError("Couldn't open that book.")
        }
    }

    private fun normalize(path: String): String {
        val out = ArrayList<String>()
        path.split('/').forEach { seg ->
            when (seg) {
                "", "." -> {}
                ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
                else -> out.add(seg)
            }
        }
        return out.joinToString("/")
    }

    private val DROP_BLOCKS = Regex("(?is)<(head|script|style|nav)[^>]*>.*?</\\1>")
    private val BLOCK_END = Regex("(?i)</(p|div|h[1-6]|li|blockquote|section|tr)>|<br\\s*/?>")
    private val TAG = Regex("<[^>]+>")
    private val NUM_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")

    private fun htmlText(html: String): String {
        var t = DROP_BLOCKS.replace(html, " ")
        t = BLOCK_END.replace(t, "\n\n")
        t = TAG.replace(t, "")
        t = NUM_ENTITY.replace(t) { m ->
            val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else " "
        }
        t = unescape(t.replace("&nbsp;", " ").replace("&mdash;", "—").replace("&ndash;", "–")
            .replace("&rsquo;", "’").replace("&lsquo;", "‘").replace("&rdquo;", "”").replace("&ldquo;", "“")
            .replace("&hellip;", "…"))
        return t.split(Regex("\\n\\s*\\n")).map { it.replace(Regex("\\s+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&apos;", "'").replace("&amp;", "&")

    private fun pdf(ctx: Context, file: File, progress: (Int, Int) -> Unit): String {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = try {
            PdfRenderer(pfd)
        } catch (e: SecurityException) {
            pfd.close()
            throw ProviderError("That PDF is password-protected. Open it, copy the text, and share that instead.")
        } catch (e: Exception) {
            pfd.close()
            throw ProviderError("Couldn't open that PDF.")
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val pages = mutableListOf<String>()
        try {
            val count = minOf(renderer.pageCount, MAX_PDF_PAGES)
            for (i in 0 until count) {
                progress(i + 1, count)
                val page = renderer.openPage(i)

                val scale = 1600f / maxOf(1, page.width)
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceIn(1, 4000)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                try {
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    page.close()
                }
                try {
                    val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bmp, 0)))
                    val blocks = result.textBlocks
                        .filter { b ->

                            val box = b.boundingBox
                            !(box != null && b.text.trim().length < 40 && (box.bottom < h * 0.06 || box.top > h * 0.94))
                        }
                        .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
                        .map { it.text.trim() }
                        .filter { it.isNotEmpty() }
                    if (blocks.isNotEmpty()) pages.add(blocks.joinToString("\n\n"))
                } catch (e: Exception) {
                } finally {
                    bmp.recycle()
                }
            }
        } finally {
            recognizer.close()
            renderer.close()
            pfd.close()
        }
        return pages.joinToString("\n\n")
    }
}
