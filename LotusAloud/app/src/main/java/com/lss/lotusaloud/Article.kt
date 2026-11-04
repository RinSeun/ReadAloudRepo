package com.lss.lotusaloud

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object Article {
    private val URL_RE = Regex("https?://[^\\s<>\"']+")

    private val BARE_SITE = Regex("^(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}(?:/\\S*)?$", RegexOption.IGNORE_CASE)

    fun linkIn(text: String): String? {
        val t = text.trim()
        if (!t.contains("://") && BARE_SITE.matches(t)) return "https://$t"
        val urls = URL_RE.findAll(t).map { it.value.trimEnd('.', ',', ')', ']') }.toList()
        if (urls.size != 1) return null
        val rest = URL_RE.replace(t, "").trim()
        return if (rest.length <= 150) urls[0] else null
    }

    class Result(val title: String, val text: String)

    fun fetch(ctx: Context, link: String): Result {
        val safe = if (link.startsWith("http://", ignoreCase = true)) "https://" + link.substring(7) else link
        return try {
            fetchDirect(safe)
        } catch (e: Blocked) {
            fetchInBrowser(ctx, safe)
        }
    }

    private val ERROR_PAGE = Regex("net::ERR_|ERR_[A-Z_]{6,}|Webpage not available|This site can.t be reached|DNS_PROBE", RegexOption.IGNORE_CASE)

    private class Blocked : Exception()

    private fun fetchDirect(link: String): Result {
        var url = link
        var conn: HttpURLConnection? = null
        try {
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
                    if (url.startsWith("http://", ignoreCase = true)) url = "https://" + url.substring(7)
                    c.disconnect()
                    continue
                }
                if (code == 401 || code == 403 || code == 429 || code == 503) throw Blocked()
                if (code == 404 || code == 410) throw Exception("That page doesn't exist (error $code).")
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
            return extract(doc, url) ?: throw Blocked()
        } catch (e: java.net.UnknownHostException) {
            throw Exception("Couldn't open that link. Check your internet connection, and that the address is right.")
        } catch (e: java.io.IOException) {
            throw Blocked()
        } finally {
            conn?.disconnect()
        }
    }

    private fun extract(doc: Document, url: String): Result? {
        run {
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
            if (body.length < 80) return null
            if (title.isNotEmpty() && !body.startsWith(title)) body = "$title.\n\n$body"
            return Result(title.ifEmpty { url }, body)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchInBrowser(ctx: Context, link: String): Result {
        val main = Handler(Looper.getMainLooper())
        val done = CountDownLatch(1)
        var html: String? = null
        var failed = false
        var finalUrl = link
        var view: WebView? = null
        main.post {
            try {
                val w = WebView(ctx.applicationContext)
                view = w
                w.settings.javaScriptEnabled = true
                w.settings.domStorageEnabled = true
                w.settings.blockNetworkImage = true
                w.webViewClient = object : WebViewClient() {
                    private var grabbed = false
                    override fun shouldOverrideUrlLoading(v: WebView, request: android.webkit.WebResourceRequest): Boolean {
                        val u = request.url.toString()
                        if (u.startsWith("http://", ignoreCase = true)) {
                            v.loadUrl("https://" + u.substring(7))
                            return true
                        }
                        return false
                    }
                    override fun onReceivedError(v: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                        if (request.isForMainFrame && !grabbed) {
                            grabbed = true
                            failed = true
                            done.countDown()
                        }
                    }
                    override fun onPageFinished(v: WebView, url: String) {
                        if (grabbed) return
                        grabbed = true
                        finalUrl = url
                        main.postDelayed({
                            v.evaluateJavascript("document.documentElement.outerHTML") { result ->
                                html = try { org.json.JSONArray("[$result]").getString(0) } catch (e: Exception) { null }
                                done.countDown()
                            }
                        }, 2500)
                    }
                }
                w.loadUrl(link)
            } catch (e: Exception) {
                done.countDown()
            }
        }
        val finished = done.await(25, TimeUnit.SECONDS)
        main.post { view?.stopLoading(); view?.destroy() }
        val page = html
        if (failed) throw Exception(OPEN_FAILED_TEXT)
        if (!finished || page.isNullOrBlank()) throw Exception(BLOCKED_TEXT)
        val doc = Jsoup.parse(page, finalUrl)
        if (ERROR_PAGE.containsMatchIn(doc.title()) || ERROR_PAGE.containsMatchIn(doc.text().take(600))) throw Exception(OPEN_FAILED_TEXT)
        val blockedPage = doc.title().contains("access denied", true) || doc.title().contains("just a moment", true) ||
            doc.text().contains("verify you are human", true)
        if (blockedPage) throw Exception(BLOCKED_TEXT)
        return extract(doc, finalUrl) ?: throw Exception(BLOCKED_TEXT)
    }

    private const val OPEN_FAILED_TEXT =
        "Couldn't open that page. Check the link, or open it in your browser, highlight the text and tap Read audio."

    private const val BLOCKED_TEXT =
        "That website doesn't let reading apps open it. Open it in your browser, highlight the text and tap Read audio (or Share it to Lotus Aloud from the page)."

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
