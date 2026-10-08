package com.appsc.prep.data

/**
 * "From your notes" under an MCQ's answer: the lines of the question's own notes page that best explain the right
 * answer - those with the answer's words first, then the question's. Source tags ([GK]) and the added full forms
 * are left out; a table row reads "cell: cell; cell".
 */
object NotesExcerpt {
    private val STOP = setOf(
        "a", "an", "the", "of", "in", "on", "at", "to", "for", "from", "by", "with", "and", "or", "is", "are", "was",
        "were", "be", "been", "it", "its", "this", "that", "these", "those", "as", "into", "than", "which", "who",
        "not", "no", "only", "all", "both", "neither", "nor", "none", "above", "following", "correct", "incorrect",
        "statement", "statements", "pair", "pairs", "given", "true", "false", "consider", "regarding", "reference",
        "what", "when", "where", "how", "under", "about", "also", "one", "two", "three", "four", "more", "most",
    )

    private fun words(s: String) =
        Regex("""[A-Za-z0-9][A-Za-z0-9'-]*""").findAll(s.lowercase()).map { it.value.removeSuffix("'s") }
            .filter { it !in STOP && it.length > 1 }.toSet()

    /** Source tags of the notes: "(CDI Block13)", "(APPCA Jun 2026)", "(IYB)" ... */
    private val SOURCE = Regex("""\s*\((?:CDI|CDX|APP|APHQ|APPCA|LENS|LENSD|CDCA|VIS|TH|IYB|APSES|SES|ES|PT365|NextGen|UPSC notes|GK)\b[^()]*\)""")

    private fun text(runs: List<Run>) = runs.filterNot { it.muted || it.fullForm }.joinToString("") { it.text }
        .replace(SOURCE, "")
        .replace(Regex("""\s{2,}"""), " ")
        .replace(Regex("""\s+([.,;:])"""), "$1")
        .replace(Regex(""":\."""), ".")
        .trim()

    /** The page as lines to quote: sentences of bullets and paragraphs, and table rows. */
    fun lines(blocks: List<Block>): List<String> = blocks.flatMap { b ->
        when (b) {
            is TextBlock -> if (b.kind == UserNotes.KIND) emptyList() else
                text(b.runs).split(Regex("""(?<![A-Z])(?<!\bArt)(?<!\bNo)(?<!\bvs)(?<!\bv)(?<!\bc)\.\s+(?=[A-Z0-9"“(])"""))
                    .map { it.trim().removeSuffix(".") + "." }
                    .filter { !it.startsWith("Not in your sources") && it.count(Char::isLetter) > 12 }
            is TableBlock -> b.rows.mapNotNull { r ->
                val cells = r.map { text(it) }.filter { it.isNotBlank() }
                if (cells.size < 2) null else cells.first() + ": " + cells.drop(1).joinToString("; ")
            }
        }
    }

    /** Up to [max] lines of [blocks] that best explain [q]'s right answer (empty when none clearly does). */
    fun forQuestion(q: Question, blocks: List<Block>, max: Int = 2): List<String> {
        val answer = words(q.options.getOrNull(q.answer).orEmpty() + " " + q.answerText) - setOf("only")
        val asked = words(q.stem) + words(q.explanation)
        val scored = lines(blocks).map { line ->
            val w = words(line)
            val a = (w intersect answer).size
            line to (3.0 * a + (w intersect asked).size) / Math.sqrt(w.size.coerceAtLeast(4).toDouble())
        }.filter { (line, score) -> score >= 1.2 && (answer.isEmpty() || (words(line) intersect answer).isNotEmpty() || score >= 2.0) }
        val best = scored.sortedByDescending { it.second }
        val top = best.firstOrNull()?.second ?: return emptyList()
        // a second line only when it explains nearly as well as the first
        return best.filterIndexed { i, (_, score) -> i == 0 || score >= 0.7 * top }.take(max)
            .map { (line, _) -> if (line.length > 320) line.take(317).trimEnd() + "…" else line }
    }
}
