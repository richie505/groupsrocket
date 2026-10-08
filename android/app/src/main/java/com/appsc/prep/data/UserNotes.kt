package com.appsc.prep.data

/**
 * The grey "Not in your sources: ..." lines say what the notes lack. The reader can look it up on Google (inside the
 * app) and add what they found: it is kept on the phone ([ProgressStore.added]) and shown, and read aloud, right
 * under that line as "Your note" (a [TextBlock] of kind [KIND]).
 */
object UserNotes {
    const val KIND = 'u'

    fun isGap(b: Block) = b is TextBlock && b.runs.joinToString("") { it.text }.trimStart().startsWith("Not in your sources")

    /** Key of the gap at [index] on page [pageId] ("book:row:sec"). */
    fun key(pageId: String, index: Int) = "$pageId#$index"

    /** Index for the reader's note on the whole page (a kept "Explain simply" answer), shown at its end. */
    const val PAGE = -1

    /** What to search for: the missing fact, without "Not in your sources:" and the "check the latest ..." advice. */
    fun query(b: Block): String {
        val t = (b as TextBlock).runs.joinToString("") { it.text }.trim()
            .removePrefix("Not in your sources").trimStart(':', ' ', '-')
        val cut = listOfNotNull(
            Regex("""(?i)[.;(]?\s*[-–(]?\s*(check|add from|verify|look up|see the latest|use the latest)\b""").find(t)?.range?.first,
            Regex("""\s\(""").find(t)?.range?.first, // "(a 2023 APPSC PYQ was based on ...)"
        ).minOrNull() ?: t.length
        return t.substring(0, cut).trim().trimEnd('.', ';', ',', '(', '-', ' ').ifEmpty { t }
    }

    /** The page's blocks with the reader's own notes after the gaps they fill: (index in [blocks] or -1, block). */
    fun withAdded(blocks: List<Block>, pageId: String, added: Map<String, String>): List<Pair<Int, Block>> = buildList {
        blocks.forEachIndexed { i, b ->
            add(i to b)
            if (isGap(b)) added[key(pageId, i)]?.takeIf { it.isNotBlank() }?.let { add(i to TextBlock(KIND, listOf(Run(it.trim(), 0)))) }
        }
        // a simple explanation kept from "Explain simply" ([PAGE] key) closes the page
        added[key(pageId, PAGE)]?.takeIf { it.isNotBlank() }?.let { add(PAGE to TextBlock(KIND, listOf(Run(it.trim(), 0)))) }
    }
}
