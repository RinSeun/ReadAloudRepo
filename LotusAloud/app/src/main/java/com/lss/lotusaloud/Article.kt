package com.lss.lotusaloud

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.HttpURLConnection
import java.net.URL

/** Turns a webpage link into readable article text (title + paragraphs, no menus or ads). */
object Article {
    private val URL_RE = Regex("https?://[^\\s<>\"']+")

    /** If the text is basically just a link (maybe with a title), returns that link. */
    fun linkIn(text: String): String? {
        val t = text.trim()
        val urls = URL_RE.findAll(t).map { it.value.trimEnd('.', ',', ')', ']') }.toList()
        if (urls.size != 1) return null
        val rest = URL_RE.replace(t, "").trim()
        return if (rest.length <= 150) urls[0] else null
    }

    class Result(val title: String, val text: String)

    /** Downloads and extracts the article. Runs on a background thread. Throws with a friendly message. */
    fun fetch(link: String): Result {
        var url = link
        var conn: HttpURLConnection? = null
        try {
            // follow redirects ourselves (Android won't follow http -> https on its own)
            for (hop in 0 until 6) {
                val c = URL(url).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 15000
                c.readTimeout = 20000
                c.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
                )
                c.setRequestProperty("Accept", "text/html,application/xhtml+xml")
                val code = c.responseCode
                if (code in 300..399) {
                    val next = c.getHeaderField("Location") ?: throw Exception("That link redirects nowhere.")
                    url = URL(URL(url), next).toString()
                    c.disconnect()
                    continue
                }
                if (code != 200) throw Exception("The page answered with error $code.")
                conn = c
                break
            }
            val c = conn ?: throw Exception("Too many redirects.")
            val type = c.contentType ?: ""
            if (type.isNotEmpty() && !type.contains("html", ignoreCase = true)) {
                throw Exception("That link isn't a webpage.")
            }
            val doc = c.inputStream.use { Jsoup.parse(it, null, url) }

            val title = (doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty())
                .ifEmpty { doc.title().trim() }

            doc.select("script, style, noscript, iframe, svg, nav, header, footer, aside, form, button, figcaption").remove()

            val container: Element = doc.selectFirst("article") ?: bestContainer(doc) ?: doc.body()
                ?: throw Exception("Couldn't find any text on that page.")

            val blocks = mutableListOf<String>()
            for (el in container.select("h1, h2, h3, p, li, blockquote")) {
                val t = el.text().trim()
                if (t.length < 2) continue
                if (blocks.isNotEmpty() && blocks.last() == t) continue
                blocks.add(t)
            }
            var body = blocks.joinToString("\n\n")
            if (body.length < 200) body = container.text().trim()
            if (body.isBlank()) throw Exception("Couldn't find any text on that page.")
            if (title.isNotEmpty() && !body.startsWith(title)) body = "$title.\n\n$body"
            return Result(title.ifEmpty { url }, body)
        } catch (e: java.io.IOException) {
            throw Exception("Couldn't open that link. Check your internet connection.")
        } finally {
            conn?.disconnect()
        }
    }

    /** The block with the most paragraph text directly inside it. */
    private fun bestContainer(doc: Element): Element? {
        var best: Element? = null
        var bestScore = 0
        for (el in doc.select("main, section, div")) {
            var score = 0
            for (child in el.children()) {
                if (child.tagName() == "p") score += child.text().length
            }
            if (score > bestScore) { bestScore = score; best = el }
        }
        return best
    }
}
