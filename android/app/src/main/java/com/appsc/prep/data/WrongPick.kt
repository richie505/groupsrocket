package com.appsc.prep.data

/**
 * After a wrong answer: the sentence of the explanation that says why the picked option is wrong, to highlight it.
 *  - Statement / pair questions ("1 and 3 only"): the sentence about a statement the pick got wrong - one it took
 *    as true that is false ("Statement 3 is false because ..."), else one it left out.
 *  - Other questions: the sentence that shares most of the picked option's own words (not the question's, not the
 *    right answer's).
 * Null when no sentence clearly fits (then nothing is highlighted).
 */
object WrongPick {
    private val STOP = setOf(
        "a", "an", "the", "of", "in", "on", "at", "to", "for", "from", "by", "with", "and", "or", "is", "are", "was",
        "were", "be", "been", "it", "its", "this", "that", "these", "those", "as", "into", "than", "which", "who",
        "not", "no", "only", "all", "both", "neither", "nor", "none", "above", "following", "correct", "incorrect",
        "statement", "statements", "pair", "pairs", "given", "true", "false",
    )

    /** Sentences of [text] as character ranges (a sentence ends at . ! ? before a capital or digit, not after "Art." etc.). */
    fun sentences(text: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = 0
        val end = Regex("""(?<![A-Z])(?<!\bArt)(?<!\bNo)(?<!\bvs)(?<!\bv)(?<!\bSec)(?<!\bc)(?<!\be\.g)(?<!\bi\.e)[.!?](?=\s+["“(]?[A-Z0-9])|\n""")
        for (m in end.findAll(text)) {
            val stop = if (m.value == "\n") m.range.first - 1 else m.range.last
            if (stop >= start) out += start..stop
            start = m.range.last + 1
            while (start < text.length && text[start].isWhitespace()) start++
        }
        if (start < text.length) out += start until text.length
        return out.filter { r -> text.substring(r).any(Char::isLetterOrDigit) }
    }

    private fun words(s: String) =
        Regex("""[A-Za-z0-9][A-Za-z0-9'-]*""").findAll(s.lowercase()).map { it.value.removeSuffix("'s") }.filter { it !in STOP && it.length > 1 }.toSet()

    /** Statement numbers an option names: "1 and 3 only" -> {1, 3}; "None of the above" -> {}; null when it names none. */
    private fun numbers(option: String): Set<Int>? {
        val o = option.lowercase()
        if (Regex("""^\s*(none|neither)\b""").containsMatchIn(o)) return emptySet()
        val n = Regex("""\b([1-9])\b""").findAll(o).map { it.value.toInt() }.toSet()
        return n.ifEmpty { null }
    }

    /** Parts of a sentence that can stand alone: split at ";" and ", but / whereas / while / although / not / rather than". */
    private fun clauses(text: String, sentence: IntRange): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = sentence.first
        val cut = Regex(""";\s+|,\s+(?=(?:but|whereas|while|although|though|not|rather than|unlike|and not)\b)""")
        for (m in cut.findAll(text.substring(sentence))) {
            val stop = sentence.first + m.range.first - 1
            if (stop >= start) out += start..stop
            start = sentence.first + m.range.last + 1
        }
        if (start <= sentence.last) out += start..sentence.last
        return if (out.size > 1) out else emptyList()
    }

    /** The range in [explanation] to highlight for a wrong [picked] option, or null. */
    fun sentence(explanation: String, stem: String, options: List<String>, answer: Int, picked: Int): IntRange? {
        if (picked == answer || picked !in options.indices || explanation.isBlank()) return null
        // whole sentences, and the parts of each (a one-sentence explanation often has a part on the wrong option)
        val sents = sentences(explanation).let { s -> s.flatMap { clauses(explanation, it) } + s }
            .filter { it.last - it.first + 1 < explanation.trim().length } // never the whole explanation
        val chosen = options[picked]
        val right = options.getOrNull(answer).orEmpty()

        // statement / pair questions
        val pickedN = numbers(chosen)
        val rightN = numbers(right)
        if (pickedN != null && rightN != null && Regex("""(?i)\b(statement|pair|assertion|\d\.)""").containsMatchIn(stem + " " + explanation)) {
            val wronglyIn = pickedN - rightN // taken as true, but false
            val wronglyOut = rightN - pickedN // true, but left out
            for (k in wronglyIn.sorted() + wronglyOut.sorted()) {
                val about = Regex("""(?i)\b(statements?|pairs?|options?)\s+(?:\d\s*(?:,|and)\s*)*$k\b|\($k\)|^$k[.)]""")
                sents.firstOrNull { about.containsMatchIn(explanation.substring(it)) }?.let { return it }
                // not named by number: the sentence about that statement's own words
                val line = Regex("""(?m)^\s*$k[.)]\s*(.+)$""").find(stem)?.groupValues?.get(1) ?: continue
                val own = words(line)
                if (own.size < 2) continue
                val (best, hits) = sents.map { it to (words(explanation.substring(it)) intersect own).size }.maxByOrNull { it.second } ?: continue
                if (hits >= 2 && hits * 2 >= minOf(own.size, 6)) return best
            }
            return null
        }

        // other questions: the picked option's own words
        val own = words(chosen) - words(stem) - words(right)
        if (own.isNotEmpty()) {
            val scored = sents.map { r -> r to (words(explanation.substring(r)) intersect own).size }
            val (best, hits) = scored.maxByOrNull { it.second } ?: return null
            if (hits >= 2 || (hits == 1 && own.size <= 2)) return best
        }
        // "The other statements / options / areas ..." speaks for every wrong pick
        return sents.firstOrNull { Regex("""(?i)^(the\s+)?(other|remaining|rest)\b|^(all\s+)?others\b""").containsMatchIn(explanation.substring(it).trim()) }
    }
}
