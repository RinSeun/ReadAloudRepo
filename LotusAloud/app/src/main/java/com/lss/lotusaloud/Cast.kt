package com.lss.lotusaloud

/**
 * Multi-voice: finds the dialogue in a story and works out who says each line,
 * so the narrator and every character can have their own voice. Runs on the phone, no AI service.
 */
object Cast {
    const val NARRATOR = ""
    const val OTHER = "?"

    class Segment(val text: String, val speaker: String, val sep: String)
    class Character(val name: String, val lines: Int, val sample: String)
    class Scan(val segments: List<Segment>, val characters: List<Character>, val dialogue: Int, val otherLines: Int, val otherSample: String)

    private class Quote(val start: Int, val end: Int, val para: Int, val inner: String) {
        var speaker: String? = null
        var weak = false
    }

    private const val VERBS =
        "said|says|asked|asks|replied|replies|answered|answers|shouted|shouts|yelled|yells|whispered|whispers|" +
            "cried|called|added|muttered|mutters|murmured|murmurs|snapped|snaps|laughed|sighed|continued|began|" +
            "insisted|exclaimed|screamed|growled|hissed|demanded|admitted|explained|repeated|groaned|breathed|" +
            "agreed|suggested|warned|mumbled|stammered|stuttered|told|tells|declared|announced|interrupted|" +
            "pleaded|begged|grumbled|chuckled|giggled|teased|offered|protested|retorted|countered|corrected|" +
            "whimpered|sobbed|roared|barked|purred|drawled|noted|observed|wondered|guessed|joked|promised|" +
            "ordered|commanded|cut in|went on|spoke|says softly|managed|gasped|panted|choked|croaked|piped up|" +
            "sang|chimed in|confirmed|conceded|concluded|reminded|scoffed|sneered|smirked|grinned|smiled|nodded|shrugged"

    private const val NAME = "((?:(?:Mr|Mrs|Ms|Dr|Miss|Lady|Lord|Sir|Captain|Professor|Aunt|Uncle|King|Queen|Prince|Princess)\\.?\\s+)?" +
        "[A-Z][\\p{Ll}'\\u2019-]+(?:\\s+[A-Z][\\p{Ll}'\\u2019-]+)?)"
    private const val PRONOUN = "(he|she|it|they|I|we|He|She|It|They|We)"
    private const val NOUN = "((?:old|young|little|tall|short|small|big|tiny)\\s+\\p{Ll}+|\\p{Ll}{3,})"

    private val AFTER_NAME_VERB = Regex("^[\\s,\\u2014\\u2013-]*$NAME\\s+(?:\\p{Ll}+ly\\s+)?(?:$VERBS)\\b")
    private val AFTER_VERB_NAME = Regex("^[\\s,\\u2014\\u2013-]*(?:$VERBS)\\s+$NAME\\b")
    private val AFTER_PRONOUN = Regex("^[\\s,\\u2014\\u2013-]*$PRONOUN\\s+(?:\\p{Ll}+ly\\s+)?(?:$VERBS)\\b|^[\\s,]*(?:$VERBS)\\s+(he|she|it)\\b")
    private val AFTER_VERB_THE = Regex("^[\\s,\\u2014\\u2013-]*(?:$VERBS)\\s+the\\s+$NOUN(?=\\s*[.,!?;]|\\s+(?:and|as|with|softly|quietly|again)\\b)")
    private val AFTER_THE_VERB = Regex("^[\\s,\\u2014\\u2013-]*[Tt]he\\s+$NOUN\\s+(?:\\p{Ll}+ly\\s+)?(?:$VERBS)\\b")
    private val AFTER_BEAT = Regex("^\\s+$NAME\\s+\\p{Ll}+")
    private val BEFORE_NAME_VERB = Regex("$NAME\\s+(?:[\\p{Ll}'\\u2019]+\\s+){0,4}?(?:$VERBS)\\b[^.!?\\u201c\\u201d\"]*[,:]?\\s*$")
    private val BEFORE_PRONOUN = Regex("\\b$PRONOUN\\s+(?:[\\p{Ll}'\\u2019]+\\s+){0,3}?(?:$VERBS)\\b[^.!?]*[,:]?\\s*$")
    private val BEFORE_PRONOUN_BEAT = Regex("(?:^|[.!?]\\s+)(He|She|It|They)\\s+\\p{Ll}[^.!?]*[.!?]\\s*$")
    private val BEFORE_BEAT = Regex("(?:^|[.!?]\\s+)$NAME\\s+\\p{Ll}[^.!?]*[.!?]\\s*$")

    private val NOT_NAMES = setOf(
        "The", "A", "An", "He", "She", "They", "I", "We", "You", "It", "His", "Her", "Their", "Our", "My", "Your",
        "But", "And", "Then", "When", "What", "Where", "Why", "How", "Who", "Yes", "No", "Oh", "Well", "So", "If",
        "As", "Just", "This", "That", "There", "Here", "Now", "After", "Before", "Still", "Even", "Maybe", "Please",
        "Thanks", "Okay", "Ok", "OK", "Hey", "Hi", "Hello", "Sorry", "Good", "Not", "Some", "All", "One", "Every",
        "Everyone", "Someone", "Nobody", "Something", "Nothing", "Chapter", "Part", "Monday", "Tuesday", "Wednesday",
        "Thursday", "Friday", "Saturday", "Sunday", "God", "Ah", "Um", "Uh", "Wait", "Look", "Come", "Let", "Don't",
        "Instead", "Suddenly", "Finally", "Meanwhile", "Later", "Once", "With", "Without", "From", "For", "At", "In",
        "On", "Of", "To", "By", "Into", "Through", "While", "Because", "Although", "Though", "Behind", "Outside",
    )

    /** Picks the quote marks the book uses. */
    private fun quoteRegex(t: String): Regex {
        val curly = t.count { it == '\u201c' }
        val straight = t.count { it == '"' } / 2
        val guill = t.count { it == '\u00ab' }
        val cjk = t.count { it == '\u300c' }
        val single = Regex("(?<![\\p{L}\\p{N}])\\u2018").findAll(t).count()
        val best = listOf(curly to 0, straight to 1, guill to 2, cjk to 3, single to 4).maxByOrNull { it.first }!!
        return when (best.second) {
            1 -> Regex("\"([^\"\\n]+)(?:\"|(?=\\n)|$)")
            2 -> Regex("\\u00ab([^\\u00ab\\u00bb\\n]+)(?:\\u00bb|(?=\\n)|$)")
            3 -> Regex("\\u300c([^\\u300c\\u300d\\n]+)(?:\\u300d|(?=\\n)|$)")
            4 -> Regex("(?<![\\p{L}\\p{N}])\\u2018([^\\n]+?)(?:\\u2019(?![\\p{L}])|(?=\\n)|$)")
            else -> Regex("\\u201c([^\\u201c\\u201d\\n]+)(?:\\u201d|(?=\\n)|$)")
        }
    }

    private fun cleanName(raw: String?): String? {
        if (raw == null) return null
        var n = raw.trim().removeSuffix("'s").removeSuffix("\u2019s")
        val words = n.split(Regex("\\s+"))
        if (words.first().trimEnd('.') in NOT_NAMES) {
            if (words.size == 1) return null
            n = words.drop(1).joinToString(" ")
            if (n in NOT_NAMES) return null
        }
        if (words.size == 2 && words[1] in NOT_NAMES) n = words[0]
        if (n.length < 2 || n in NOT_NAMES) return null
        return n
    }

    fun scan(text: String, renames: Map<String, String> = emptyMap()): Scan {
        val qre = quoteRegex(text)
        val paraStarts = ArrayList<Int>()
        paraStarts.add(0)
        text.forEachIndexed { i, c -> if (c == '\n') paraStarts.add(i + 1) }
        fun paraOf(pos: Int): Int {
            var lo = 0
            var hi = paraStarts.size - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (paraStarts[mid] <= pos) lo = mid else hi = mid - 1
            }
            return lo
        }
        fun paraEnd(p: Int) = if (p + 1 < paraStarts.size) paraStarts[p + 1] - 1 else text.length

        val quotes = qre.findAll(text).filter { it.groupValues[1].any { c -> c.isLetter() } }
            .map { Quote(it.range.first, it.range.last + 1, paraOf(it.range.first), it.groupValues[1].trim()) }.toList()

        // 1. Who says it, from the words around the quote.
        val pronounQuotes = HashMap<Quote, String>()
        quotes.forEachIndexed { i, q ->
            val prevInPara = quotes.getOrNull(i - 1)?.takeIf { it.para == q.para }
            val nextInPara = quotes.getOrNull(i + 1)?.takeIf { it.para == q.para }
            val after = text.substring(q.end, minOf(nextInPara?.start ?: paraEnd(q.para), q.end + 120))
            val before = text.substring(maxOf(prevInPara?.end ?: paraStarts[q.para], q.start - 160), q.start)
            val name = AFTER_NAME_VERB.find(after)?.groupValues?.get(1)?.let(::cleanName)
                ?: AFTER_VERB_NAME.find(after)?.groupValues?.get(1)?.let(::cleanName)
                ?: (AFTER_VERB_THE.find(after) ?: AFTER_THE_VERB.find(after))?.groupValues?.get(1)?.let { "The " + it.trim() }
                ?: if (prevInPara == null) BEFORE_NAME_VERB.find(before)?.groupValues?.get(1)?.let(::cleanName) else null
            val pron = AFTER_PRONOUN.find(after)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
                ?: if (prevInPara == null) {
                    BEFORE_PRONOUN.find(before)?.groupValues?.get(1)
                        ?: if (BEFORE_BEAT.find(before)?.groupValues?.get(1)?.let(::cleanName) == null) BEFORE_PRONOUN_BEAT.find(before)?.groupValues?.get(1) else null
                } else null
            if (name != null) {
                q.speaker = name
            } else if (pron != null) {
                pronounQuotes[q] = pron.lowercase()
                q.speaker = null
            } else {
                val beat = AFTER_BEAT.find(after)?.groupValues?.get(1)?.let(::cleanName)
                    ?: if (prevInPara == null) BEFORE_BEAT.find(before)?.groupValues?.get(1)?.let(::cleanName) else null
                if (beat != null) { q.speaker = beat; q.weak = true }
            }
        }


        // 1b. "she said" / "it said": the last character mentioned in the story text just before (not inside quotes).
        val known = quotes.mapNotNull { it.speaker }.toSet()
        val people = known.filter { !it.startsWith("The ") }
        val things = known.filter { it.startsWith("The ") }
        quotes.forEachIndexed { i, q ->
            val pron = pronounQuotes[q] ?: return@forEachIndexed
            if (pron == "i" || pron == "we" || pron == "they") return@forEachIndexed
            val prevInPara = quotes.getOrNull(i - 1)?.takeIf { it.para == q.para }
            if (prevInPara?.speaker != null) { q.speaker = prevInPara.speaker; return@forEachIndexed }
            if (pron == "it") {
                q.speaker = quotes.subList(0, i).lastOrNull { it.speaker?.startsWith("The ") == true }?.speaker
                    ?: things.firstOrNull()
                return@forEachIndexed
            }
            val from = maxOf(0, q.start - 600)
            val sb = StringBuilder(text.substring(from, q.start))
            quotes.filter { it.end > from && it.start < q.start }.forEach { o ->
                for (k in maxOf(o.start, from) until minOf(o.end, q.start)) sb.setCharAt(k - from, ' ')
            }
            val narration = sb.toString()
            var best: String? = null
            var bestAt = -1
            for (p in people) {
                val keys = listOf(p) + p.split(" ").filter { it.length > 2 && it !in NOT_NAMES && !it.endsWith(".") }
                for (k in keys) {
                    val at = Regex("\\b" + Regex.escape(k) + "\\b").findAll(narration).lastOrNull()?.range?.first ?: -1
                    if (at > bestAt) { bestAt = at; best = p }
                }
            }
            // The person who just spoke is rarely "he" in the reply, so then it's the other person in the conversation.
            val earlier = quotes.subList(0, i).filter { it.speaker != null && it.para != q.para }
            val lastSpeaker = earlier.lastOrNull()?.speaker
            val otherSpeaker = earlier.lastOrNull { it.speaker != lastSpeaker }?.speaker
            q.speaker = if (best != null && best == lastSpeaker && otherSpeaker != null) otherSpeaker else best ?: otherSpeaker
        }

        // 2. Lines with no name: same paragraph = same speaker, otherwise people take turns.
        var last: String? = null
        var beforeLast: String? = null
        var lastPara = -10
        for ((i, q) in quotes.withIndex()) {
            val prev = quotes.getOrNull(i - 1)
            if (q.speaker == null) {
                q.speaker = when {
                    prev != null && prev.para == q.para && prev.speaker != null -> prev.speaker
                    q.para - lastPara <= 2 && beforeLast != null && beforeLast != last &&
                        text.substring(paraStarts[q.para], q.start).isBlank() -> beforeLast
                    else -> null
                }
            }
            val s = q.speaker
            if (s != null && s != last) { beforeLast = last; last = s }
            if (s != null) lastPara = q.para
        }

        // 3. Join "Mia Chen" with "Mia", apply your renames, and send rare speakers to "Other voices".
        val counts = HashMap<String, Int>()
        quotes.forEach { q -> q.speaker?.let { counts[it] = (counts[it] ?: 0) + 1 } }
        val merge = HashMap<String, String>()
        for (n in counts.keys) {
            val words = n.split(" ")
            if (words.size > 1) {
                val target = listOf(words.last(), words.first()).firstOrNull { it != n && (counts[it] ?: 0) > 0 }
                if (target != null) merge[n] = target
            }
        }
        fun finalName(n: String?): String {
            if (n == null) return OTHER
            var x = merge[n] ?: n
            var hops = 0
            while (renames.containsKey(x) && hops < 5) { x = renames[x]!!; hops++ }
            return x
        }
        quotes.forEach { it.speaker = finalName(it.speaker) }
        val finalCounts = HashMap<String, Int>()
        quotes.forEach { q -> finalCounts[q.speaker!!] = (finalCounts[q.speaker!!] ?: 0) + 1 }
        quotes.forEach { q ->
            val s = q.speaker!!
            if (s != NARRATOR && s != OTHER && (finalCounts[s] ?: 0) < 2 && !renames.containsValue(s)) q.speaker = OTHER
        }

        // 4. Cut the text into narrator and character pieces, in reading order.
        val segs = ArrayList<Segment>()
        var pendingPara = false
        fun push(piece: String, speaker: String) {
            if (piece.isBlank()) {
                if (piece.contains('\n')) pendingPara = true
                return
            }
            val newPara = pendingPara || piece.takeWhile { it.isWhitespace() }.contains('\n')
            pendingPara = false
            val lead = if (newPara) "\n" else ""
            val body = piece.trim()
            val sep = if (newPara && segs.isNotEmpty()) "\n\n" else if (segs.isEmpty()) "" else " "
            val lastSeg = segs.lastOrNull()
            if (!body.any { it.isLetterOrDigit() } && lastSeg != null) {
                segs[segs.size - 1] = Segment(lastSeg.text + body, lastSeg.speaker, lastSeg.sep)
                return
            }
            if (lastSeg != null && lastSeg.speaker == speaker && !lead.contains('\n')) {
                segs[segs.size - 1] = Segment(lastSeg.text + " " + body, speaker, lastSeg.sep)
            } else if (lastSeg != null && lastSeg.speaker == speaker) {
                segs[segs.size - 1] = Segment(lastSeg.text + "\n\n" + body, speaker, lastSeg.sep)
            } else {
                segs.add(Segment(body, speaker, sep))
            }
        }
        var pos = 0
        for (q in quotes) {
            if (q.start > pos) push(text.substring(pos, q.start), NARRATOR)
            push(text.substring(q.start, q.end), q.speaker!!)
            pos = q.end
        }
        if (pos < text.length) push(text.substring(pos), NARRATOR)

        val chars = quotes.filter { it.speaker != OTHER && it.speaker != NARRATOR }.groupBy { it.speaker!! }
            .map { (name, qs) -> Character(name, qs.size, qs.maxByOrNull { it.inner.length.coerceAtMost(90) }!!.inner.take(110)) }
            .sortedByDescending { it.lines }
        val others = quotes.filter { it.speaker == OTHER }
        return Scan(segs, chars, quotes.size, others.size, others.firstOrNull()?.inner?.take(110) ?: "")
    }
}
