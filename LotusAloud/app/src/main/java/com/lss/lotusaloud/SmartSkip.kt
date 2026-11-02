package com.lss.lotusaloud

object SmartSkip {
    private val PAGE_MARK = Regex("^\\s*(?:page\\s*)?\\d{1,4}(?:\\s*(?:of|/)\\s*\\d{1,4})?\\s*$|^\\s*[-\\u2013\\u2014]\\s*\\d{1,4}\\s*[-\\u2013\\u2014]\\s*$", RegexOption.IGNORE_CASE)
    private val DIGITS = Regex("\\d+")

    private val NUMBER_CITE = Regex("\\s?\\[\\d{1,3}(?:\\s*[,\\u2013-]\\s*\\d{1,3})*\\]")
    private val AUTHOR_CITE = Regex(
        "\\s?\\((?:see\\s+)?(?:[A-Z][\\p{L}'\\u2019-]+(?:\\s+et\\s+al\\.?)?(?:,?\\s+(?:and|&)\\s+[A-Z][\\p{L}'\\u2019-]+)?,?\\s+(?:19|20)\\d{2}[a-z]?(?:,\\s*(?:p|pp)\\.\\s*\\d+(?:[\\u2013-]\\d+)?)?(?:;\\s*)?)+\\)"
    )
    private val SUPERSCRIPT = Regex("[\\u00b9\\u00b2\\u00b3\\u2070\\u2074-\\u2079]+")

    private val DOI = Regex("(?:doi:\\s*|https?://doi\\.org/)?\\b10\\.\\d{4,9}/\\S+", RegexOption.IGNORE_CASE)
    private val ARXIV = Regex("\\barXiv:\\s*\\d{4}\\.\\d{4,5}(?:v\\d+)?", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("\\b[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+\\b")
    private val PATH_URL = Regex("(?:https?://)?\\b(?:[a-z0-9-]+\\.)+[a-z]{2,}/\\S*", RegexOption.IGNORE_CASE)
    private val LONG_TOKEN = Regex("\\S{40,}")

    private val REF_HEADING = Regex("^\\s*(?:\\d+\\.?\\s*)?(?:references|bibliography|works cited|literature cited|sources|citations|endnotes)\\s*:?\\s*$", RegexOption.IGNORE_CASE)

    fun apply(raw: String, p: Prefs): String {
        if (!p.smartSkip) return raw
        var t = raw.replace("\r", "")
        if (p.skipRefs) t = cutReferences(t)
        if (p.skipHeaders) t = dropRepeatedLines(t)
        if (p.skipCitations) {
            t = NUMBER_CITE.replace(t, "")
            t = AUTHOR_CITE.replace(t, "")
            t = SUPERSCRIPT.replace(t, "")
        }
        if (p.skipLinks) {
            t = DOI.replace(t, " ")
            t = ARXIV.replace(t, " ")
            t = EMAIL.replace(t, " ")
            t = PATH_URL.replace(t, " ")
            t = LONG_TOKEN.replace(t) { m -> if (m.value.any { it == '/' || it == '_' || it == '=' || it == '&' }) " " else m.value }
        }
        return t
    }

    private fun cutReferences(t: String): String {
        val lines = t.split("\n")
        if (lines.size < 8) return t
        var total = 0
        val starts = lines.map { val s = total; total += it.length + 1; s }
        for (i in lines.indices.reversed()) {
            if (REF_HEADING.matches(lines[i]) && starts[i] > total * 0.5) {
                return lines.subList(0, i).joinToString("\n")
            }
        }
        return t
    }

    private fun dropRepeatedLines(t: String): String {
        val lines = t.split("\n")
        val key = { s: String -> DIGITS.replace(s.trim().lowercase(), "#") }
        val counts = HashMap<String, Int>()
        if (lines.size >= 12) {
            for (l in lines) {
                val s = l.trim()
                if (s.length in 3..120) counts[key(s)] = (counts[key(s)] ?: 0) + 1
            }
        }
        return lines.filterNot { l ->
            val s = l.trim()
            s.isNotEmpty() && (PAGE_MARK.matches(s) || (s.length in 3..120 && (counts[key(s)] ?: 0) >= 3))
        }.joinToString("\n")
    }
}
