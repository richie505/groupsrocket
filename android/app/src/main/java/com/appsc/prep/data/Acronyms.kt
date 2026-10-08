package com.appsc.prep.data

/**
 * Adds the full form after the first short form of each kind on a page: "NCPCR" -> "NCPCR (National Commission
 * for Protection of Child Rights)". The added text is a [Run] with flag 8: shown in grey, skipped by read-aloud
 * (which already says the short form in full). Meanings come from [SpeechText.meaning] (the same as read-aloud), so a page about
 * reservation gets "SC (Scheduled Caste)" and a page about judges "SC (Supreme Court)".
 *
 * The whole page (and its section title, [context]) picks among meanings: "CWC (Child Welfare Committee)" on a
 * Juvenile Justice page, "CWC (Central Water Commission)" on a dams page.
 *
 * Mixed-case ones (MoLE, MoSJE, MeitY, NaBFID) too, when the checked list has them.
 *
 * Not explained: source codes (CDI, TH, APPSC ...), Roman numerals, everyday ones in [KNOWN], a short form the
 * same bullet already spells out or defines right there ("Fiscal Deficit (FD)", "FD (fiscal deficit)").
 */
object Acronyms {

    fun annotate(blocks: List<Block>, book: Int, context: String = ""): List<Block> {
        val page = blocks.joinToString(" ") { b ->
            when (b) {
                is TextBlock -> b.runs.joinToString("") { it.text }
                is TableBlock -> (listOf(b.head) + b.rows).joinToString(" ") { r -> r.joinToString(" ") { c -> c.joinToString("") { it.text } } }
            }
        }.lowercase() + " " + context.lowercase()
        val seen = HashSet<String>()
        fun runs(rs: List<Run>): List<Run> {
            val text = rs.joinToString("") { it.text }
            if (SpeechText.SHORT_FORM.find(text) == null) return rs
            val here = text.lowercase()
            val out = ArrayList<Run>(rs.size + 2)
            var pos = 0
            for (r in rs) {
                if (r.muted || r.fullForm) {
                    out += r
                    pos += r.text.length
                    continue
                }
                var from = 0
                for (m in SpeechText.SHORT_FORM.findAll(r.text)) {
                    if (m.value.none(Char::isUpperCase)) continue
                    val start = pos + m.range.first
                    val end = pos + m.range.last + 1
                    val before = text.substring(0, start)
                    val after = text.substring(end)
                    val found = SpeechText.find(m.value, before, after, book, page, display = true) ?: continue
                    if (found.key in seen) continue
                    // defined right here: "Fiscal Deficit (FD)" or "FD (fiscal deficit)"
                    if (before.trimEnd().endsWith("(") || after.trimStart().startsWith("(")) {
                        seen += found.key
                        continue
                    }
                    val full = found.shown
                    seen += found.key
                    // spelled out in this same bullet / row already: no need to repeat it
                    if (full.equals(m.value, ignoreCase = true) || full.lowercase() in here) continue
                    out += Run(r.text.substring(from, m.range.last + 1), r.flags)
                    out += Run(" ($full)", 8)
                    from = m.range.last + 1
                }
                if (from < r.text.length) out += Run(r.text.substring(from), r.flags)
                pos += r.text.length
            }
            return out
        }
        return blocks.map { b ->
            when (b) {
                is TextBlock -> TextBlock(b.kind, runs(b.runs))
                is TableBlock -> TableBlock(b.head.map { runs(it) }, b.rows.map { row -> row.map { runs(it) } })
            }
        }
    }
}
