package com.lss.lotusaloud

/**
 * Splits long text into parts so reading starts fast and you can jump part by part.
 * The first part is short (quick start); the rest are bigger. Splits happen between
 * paragraphs or sentences, never mid-sentence.
 */
object Parts {
    private const val FIRST_TARGET = 350
    private const val TARGET = 1200
    private val SENTENCE_END = Regex("(?<=[.!?\\u2026])\\s+")

    fun split(text: String): List<String> {
        val pieces = mutableListOf<String>() // paragraphs, or sentence groups of very long paragraphs
        for (para in text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }) {
            if (para.length <= TARGET) {
                pieces.add(para)
            } else {
                val sb = StringBuilder()
                for (s in para.split(SENTENCE_END)) {
                    if (sb.isNotEmpty() && sb.length + s.length + 1 > TARGET) {
                        pieces.add(sb.toString()); sb.clear()
                    }
                    if (sb.isNotEmpty()) sb.append(' ')
                    sb.append(s)
                }
                if (sb.isNotEmpty()) pieces.add(sb.toString())
            }
        }
        if (pieces.isEmpty()) return emptyList()

        // the first piece may itself be long: give it a short head for a fast start
        val first = pieces[0]
        if (first.length > FIRST_TARGET * 2) {
            val sentences = first.split(SENTENCE_END)
            val head = StringBuilder()
            var i = 0
            while (i < sentences.size && (head.isEmpty() || head.length + sentences[i].length < FIRST_TARGET)) {
                if (head.isNotEmpty()) head.append(' ')
                head.append(sentences[i]); i++
            }
            if (i < sentences.size) {
                pieces[0] = head.toString()
                pieces.add(1, sentences.drop(i).joinToString(" "))
            }
        }

        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        for (p in pieces) {
            val limit = if (parts.isEmpty()) FIRST_TARGET else TARGET
            if (cur.isNotEmpty() && cur.length + p.length + 2 > limit) {
                parts.add(cur.toString()); cur.clear()
            }
            if (cur.isNotEmpty()) cur.append("\n\n")
            cur.append(p)
        }
        if (cur.isNotEmpty()) parts.add(cur.toString())
        return parts
    }
}
