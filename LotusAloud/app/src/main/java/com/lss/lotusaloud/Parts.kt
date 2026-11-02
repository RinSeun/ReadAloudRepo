package com.lss.lotusaloud

object Parts {
    private val LIMITS = intArrayOf(170, 320, 550, 750, 900)
    private val SENTENCE_END = Regex("(?<=[.!?\\u2026])\\s+|(?<=[\\u3002\\uFF01\\uFF1F])\\s*")

    private fun limit(index: Int) = LIMITS[minOf(index, LIMITS.size - 1)]

    fun split(text: String): List<String> {
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        val paras = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
        for (para in paras) {
            val sentences = para.split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }
            sentences.forEachIndexed { si, s ->
                val sep = when {
                    cur.isEmpty() -> ""
                    si == 0 -> "\n\n"
                    else -> " "
                }
                if (cur.isNotEmpty() && cur.length + sep.length + s.length > limit(parts.size)) {
                    parts.add(cur.toString())
                    cur.clear()
                    cur.append(s)
                } else {
                    cur.append(sep).append(s)
                }
            }
        }
        if (cur.isNotEmpty()) parts.add(cur.toString())
        return parts
    }
}
