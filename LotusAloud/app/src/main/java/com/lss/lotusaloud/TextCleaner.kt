package com.lss.lotusaloud

/** Makes copied text read naturally: drops links, re-joins wrapped lines, keeps paragraphs. */
object TextCleaner {
    private val LINKS = Regex("https?://\\S+|www\\.\\S+")
    private val HYPHEN_WRAP = Regex("(\\w)-\\n(\\w)")
    private val SPACES = Regex("[ \\t\\u00a0]+")
    private val PARAGRAPHS = Regex("\\n\\s*\\n")
    private val BULLET = Regex("^([-*\\u2022\\u25aa\\u25e6\\u00b7]|\\d+[.)])\\s+")
    private val BULLET_CHAR = Regex("^[-*\\u2022\\u25aa\\u25e6\\u00b7]\\s+")
    private val ENDS_SENTENCE = Regex("[.!?:;,][\"')\\]]?$")
    private val STARTS_CAPITAL = Regex("^[A-Z0-9\"'\\u201c]")
    private val MULTI_SPACE = Regex(" {2,}")

    fun clean(input: String): String {
        var t = input.replace("\r", "")
        t = LINKS.replace(t, " ")
        t = HYPHEN_WRAP.replace(t, "\$1\$2")
        t = SPACES.replace(t, " ")

        val paragraphs = t.split(PARAGRAPHS).mapNotNull { para ->
            val lines = para.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val sb = StringBuilder()
            lines.forEachIndexed { i, line ->
                val isBullet = BULLET.containsMatchIn(line)
                val clean = BULLET_CHAR.replace(line, "")
                if (sb.isEmpty()) {
                    sb.append(clean)
                } else {
                    val endsSentence = ENDS_SENTENCE.containsMatchIn(sb)
                    // a heading or list line followed by a new capitalised line gets a pause;
                    // ordinary wrapped lines (continuing mid-sentence) are just joined
                    val headingBreak = lines[i - 1].length < 60 && STARTS_CAPITAL.containsMatchIn(clean)
                    sb.append(if (!endsSentence && (isBullet || headingBreak)) ". " else " ").append(clean)
                }
            }
            if (sb.isEmpty()) null else sb.toString()
        }
        return MULTI_SPACE.replace(paragraphs.joinToString("\n\n"), " ").trim()
    }
}
