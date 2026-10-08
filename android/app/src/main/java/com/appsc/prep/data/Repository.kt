package com.appsc.prep.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.time.LocalDate

/** Loads the bundled notes and plan from assets ([open] reads one by name). Books are loaded lazily and cached. */
class Repository(private val open: (String) -> InputStream) {

    val plan: Plan by lazy { parsePlan(readJson("plan.json")) }
    val index: List<BookInfo> by lazy { parseIndex(readJson("index.json")) }

    /** Key terms of each notes page ("book:row:sec" -> terms), from tools/build_key_terms.py. */
    val keyTerms: Map<String, List<String>> by lazy {
        runCatching { readJson("keyterms.json").jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } } }
            .getOrDefault(emptyMap())
    }

    /** Offline word meanings (WordNet) for "Meaning" on selected text. */
    val dictionary by lazy { abbreviations; Dictionary(open) }

    /** A place in the notes whose heading mentions a word. */
    data class NoteHit(val book: Int, val row: Int, val sec: Int, val title: String, val where: String)

    /** Up to [limit] notes subsections whose heading mentions [term] (whole words), headings starting with it first. */
    suspend fun findInNotes(term: String, limit: Int = 6): List<NoteHit> = withContext(Dispatchers.Default) {
        val t = term.trim()
        if (t.length < 3) return@withContext emptyList()
        // s.144 finds "Section 144", 84th amendment finds "Eighty-fourth Amendment" ...
        val re = NotesTerms.parse(t)?.regex ?: return@withContext emptyList()
        val hits = mutableListOf<Pair<Int, NoteHit>>()
        for (b in index.map { it.id }) {
            val bk = book(b)
            bk.rows.forEachIndexed { ri, r ->
                r.secs.forEachIndexed { si, s ->
                    // a sheet whose subsections are "Key facts" / "Sheet text": its title names the topic
                    val onPage = re.find(s.title)
                    val m = onPage ?: re.find(r.title)?.takeIf { si == 0 } ?: return@forEachIndexed
                    hits += m.range.first to NoteHit(b, ri, si, if (onPage != null) s.title else r.title, "${bk.short} · ${r.title}")
                }
            }
        }
        hits.sortedBy { it.first }.take(limit).map { it.second }
    }

    /**
     * What the notes say about a selected term: up to [limit] lines that mention it (s.144 = Sec 144 = Section 144,
     * 84th Amendment = Eighty-fourth Amendment ... see [NotesTerms]), each cut to the sentence with it, with where
     * it is. Lines that start with the term ("84th Amendment (2001): ...") come first, then lines under a heading
     * that names it, then the rest in book order.
     */
    suspend fun notesAbout(selection: String, limit: Int = 4): Pair<String, List<Dictionary.NoteDefinition>>? =
        withContext(Dispatchers.Default) {
            val term = NotesTerms.parse(selection) ?: return@withContext null
            val found = mutableListOf<Triple<Int, Int, Dictionary.NoteDefinition>>() // score, order, line
            var order = 0
            for (b in index.map { it.id }) {
                val bk = book(b)
                for (r in bk.rows) for (s in r.secs) {
                    val heading = term.regex.containsMatchIn(s.title) || term.regex.containsMatchIn(r.title)
                    for (blk in s.blocks) {
                        val lines = when (blk) {
                            // grey "Not in your sources: ..." notes say what the notes lack: not a meaning; grey "[Subject · ROCKET SHEET #N]" tags: not text
                            is TextBlock -> listOf(blk.runs.filterNot { it.muted && it.text.trimStart().let { t -> t.startsWith("Not in your sources") || t.startsWith("[") } }.joinToString("") { it.text }.trim())
                            is TableBlock -> blk.rows.map { row -> row.joinToString(" - ") { c -> c.joinToString("") { it.text } } }
                        }
                        for (line in lines) {
                            val m = term.regex.find(line) ?: continue
                            val text = sentence(line, m.range)
                            if (text.isBlank()) continue
                            // the line opens with the term > a heading names it > the rest; lines that only open a list last
                            val score = (if (m.range.first <= 3) 0 else 2) + (if (heading) 0 else 1) + (if (text.length < 45) 4 else 0)
                            found += Triple(score, order++, Dictionary.NoteDefinition(text, "${bk.short} · ${r.title}"))
                        }
                    }
                }
            }
            val seen = HashSet<String>()
            term.display to found.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
                .filter { seen.add(it.text.lowercase()) }.take(limit)
        }

    /** The sentence of [line] around [hit], without citations, at most about 300 characters. */
    private fun sentence(line: String, hit: IntRange): String {
        // sentence ends: ". " or "; " outside brackets, and not after an abbreviation (Art. 21, Sec. 144, Dr. X)
        val ends = mutableListOf<Int>()
        var depth = 0
        for (i in line.indices) {
            when (line[i]) {
                '(', '[' -> depth++
                ')', ']' -> depth = (depth - 1).coerceAtLeast(0)
                '.', ';' -> if (depth == 0 && i + 1 < line.length && line[i + 1] == ' ' && !abbreviationBefore(line, i)) ends += i + 1
            }
        }
        val from = (listOf(0) + ends).last { it <= hit.first }
        val to = ends.firstOrNull { it > hit.last } ?: line.length
        var t = SpeechText.withoutCitations(line.substring(from, to).trim())
        if (t.length > 320) {
            val at = t.indexOf(line.substring(hit.first, hit.last + 1)).coerceAtLeast(0)
            val a = (at - 140).coerceAtLeast(0)
            t = (if (a > 0) "…" else "") + t.substring(a, (a + 300).coerceAtMost(t.length)).trim() + (if (a + 300 < t.length) "…" else "")
        }
        return t.trimEnd(';', ' ')
    }

    private val ABBREVIATIONS = setOf(
        "art", "arts", "sec", "secs", "s", "ss", "no", "nos", "dr", "smt", "sri", "st", "vs", "v", "e.g", "i.e", "etc", "govt",
        "ltd", "cl", "para", "ch", "vol", "fig", "approx", "est", "c", "b", "d", "r", "mr", "mrs", "prof", "jr", "sr", "co",
    )

    private fun abbreviationBefore(line: String, dot: Int): Boolean {
        if (line[dot] != '.') return false
        val word = line.substring(0, dot).takeLastWhile { it.isLetter() || it == '.' }.lowercase()
        // initials such as "M.S. Gore", "T.K. Oommen"
        return word in ABBREVIATIONS || (word.length == 1 && line[dot - 1].isUpperCase())
    }

    /** Short forms the notes define (tools/build_abbreviations.py), for read-aloud. */
    val abbreviations: Map<String, List<String>> by lazy {
        runCatching {
            readJson("abbr.json").jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }
        }.getOrDefault(emptyMap()).also { SpeechText.fromNotes = it }
    }

    /**
     * Subsections merged or renumbered when repeated topics were merged (tools/merge_topics.py):
     * old "book:row:sec" -> new id, for [ProgressStore.migrate].
     */
    val idMoves: IdMoves by lazy {
        runCatching {
            val o = readJson("moved.json").jsonObject
            IdMoves(o.str("v"), o["m"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content }, o.strList("g").toSet())
        }.getOrDefault(IdMoves("", emptyMap(), emptySet()))
    }

    /** Short forms checked against the notes (tools/build_acronyms.py), for full forms and read-aloud. */
    val checkedAcronyms: Map<String, List<Pair<String, List<String>>>> by lazy {
        runCatching {
            readJson("acronyms.json").jsonObject.mapValues { (_, v) ->
                v.jsonArray.map { s -> s.jsonArray[0].jsonPrimitive.content to s.jsonArray[1].jsonArray.map { it.jsonPrimitive.content } }
            }
        }.getOrDefault(emptyMap()).also { SpeechText.checked = it }
    }

    /**
     * The revision edition (Rocket Revision) ships edition.json and rev{n}.json: for every ROCKET SHEET, the
     * facts its MCQs test (scripts/build_revision.py). Its pages are read from those.
     */
    val revision: Boolean by lazy { runCatching { open("edition.json").close() }.isSuccess }

    /** Name of this edition: "Rocket Prep" or "Rocket Revision". */
    val appName: String get() = if (revision) "Rocket Revision" else "Rocket Prep"

    private val books = HashMap<Int, Book>()
    private val mutex = Mutex()

    suspend fun book(id: Int): Book = mutex.withLock {
        books[id] ?: withContext(Dispatchers.IO) { parseBook(readJson(if (revision) "rev$id.json" else "book$id.json")) }.also { books[id] = it }
    }

    fun cachedBook(id: Int): Book? = books[id]


    private val mcqs = HashMap<Int, BookMcq>()

    suspend fun mcq(id: Int): BookMcq = mutex.withLock {
        mcqs[id] ?: withContext(Dispatchers.IO) { parseMcq(readJson("mcq$id.json"), id) }.also { mcqs[id] = it }
    }

    fun cachedMcq(id: Int): BookMcq? = mcqs[id]

    fun rowInfo(book: Int, row: Int): RowInfo? = index.getOrNull(book - 1)?.rows?.getOrNull(row)

    /** The plan's priority / past-paper figure for a notes row, if the plan lists it. */
    val planRowByRef: Map<Pair<Int, Int>, PlanRow> by lazy {
        buildMap { plan.days.forEach { d -> d.rows.forEach { r -> putIfAbsent(r.book to r.row, r) } } }
    }

    /** Index of the plan day for a date (clamped to the plan). */
    fun dayFor(date: LocalDate): PlanDay {
        val days = plan.days
        return days.firstOrNull { it.date == date }
            ?: if (date.isBefore(days.first().date)) days.first() else days.last()
    }

    private fun readJson(name: String): JsonElement =
        open(name).bufferedReader().use { Json.parseToJsonElement(it.readText()) }

    // ---- parsing ----

    private fun JsonElement.str(key: String): String =
        (this.jsonObject[key] as? JsonPrimitive)?.content ?: ""

    private fun JsonElement.intOr(key: String, def: Int = 0): Int =
        (this.jsonObject[key] as? JsonPrimitive)?.content?.toIntOrNull() ?: def

    private fun JsonElement.strList(key: String): List<String> =
        (this.jsonObject[key] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()

    private fun runs(e: JsonElement?): List<Run> = when (e) {
        null -> emptyList()
        is JsonPrimitive -> listOf(Run(e.content, 0))
        is JsonArray -> e.map { r ->
            val a = r.jsonArray
            Run(a[0].jsonPrimitive.content, a[1].jsonPrimitive.int)
        }
        else -> emptyList()
    }

    private fun parseBook(root: JsonElement): Book {
        val id = root.intOr("id")
        abbreviations // short forms the notes define, for the full forms added to the text
        checkedAcronyms
        val units = mutableListOf<NoteUnit>()
        val rows = mutableListOf<NoteRow>()
        root.jsonObject["units"]!!.jsonArray.forEachIndexed { ui, u ->
            units += NoteUnit(u.str("code"), u.str("title"))
            u.jsonObject["rows"]!!.jsonArray.forEach { r ->
                val secs = r.jsonObject["secs"]!!.jsonArray.map { s ->
                    Subsection(
                        title = s.str("t"),
                        badges = s.strList("badges"),
                        page = s.intOr("p"),
                        blocks = s.jsonObject["b"]!!.jsonArray.map { b -> parseBlock(b.jsonObject) }.let { plain ->
                            // full forms are extras: if they fail on a phone, show the page without them
                            try {
                                Acronyms.annotate(plain, id, context = r.str("title") + " " + s.str("t"))
                            } catch (e: Exception) {
                                if (SpeechText.strict) throw e
                                plain
                            }
                        },
                        universal = s.jsonObject.containsKey("u"),
                        coveredIn = (s.jsonObject["cov"] as? JsonArray)?.map { c -> c.jsonArray.map { it.jsonPrimitive.int } } ?: emptyList(),
                    )
                }
                rows += NoteRow(
                    book = id, index = rows.size, unitIndex = ui,
                    codes = r.strList("codes"), tag = r.str("tag"), title = r.str("title"),
                    sub = r.strList("sub"), pyq = r.str("pyq"), p1 = r.intOr("p1"), p2 = r.intOr("p2"),
                    secs = secs, sources = r.strList("src"),
                    see = (r.jsonObject["see"] as? JsonArray)?.map { runs(it) } ?: emptyList(),
                )
            }
        }
        return Book(id, root.str("title"), root.str("short"), root.intOr("pages"), units, rows)
    }

    private fun parseBlock(b: JsonObject): Block {
        val kind = (b["k"] as JsonPrimitive).content
        return if (kind == "t") {
            TableBlock(
                head = b["h"]!!.jsonArray.map { runs(it) },
                rows = b["r"]!!.jsonArray.map { row -> row.jsonArray.map { runs(it) } },
            )
        } else {
            TextBlock(kind.first(), runs(b["x"]))
        }
    }

    private fun parseMcq(root: JsonElement, book: Int): BookMcq {
        fun list(e: JsonElement): List<Question> = e.jsonArray.map { q ->
            val o = q.jsonObject
            val kind = (o["k"] as? JsonPrimitive)?.content?.firstOrNull() ?: 's'
            Question(
                id = q.str("id"),
                stem = q.str("s"),
                table = (o["t"] as? JsonArray)?.map { r -> r.jsonArray.map { it.jsonPrimitive.content } } ?: emptyList(),
                options = q.strList("o"),
                // flashcards: option 0 = "knew it", so a self-grade is checked like any answer
                answer = if (kind == 'f') 0 else q.intOr("a", -1),
                source = q.str("src"),
                appsc = o.containsKey("ap"),
                explanation = q.str("x"),
                notes = q.strList("n"),
                kind = kind,
                answerText = q.str("at"),
                cancelled = o.containsKey("cx"),
                book = book,
                technique = q.str("tq"),
            )
        }
        fun map(key: String): Map<Int, List<Question>> =
            (root.jsonObject[key] as? JsonObject)?.entries?.associate { (k, v) -> k.toInt() to list(v) } ?: emptyMap()
        val subs = (root.jsonObject["subs"] as? JsonObject)?.entries
            ?.associate { (k, v) -> k.toInt() to v.jsonArray.map { it.jsonPrimitive.int } } ?: emptyMap()
        return BookMcq(map("rows"), map("units"), subs)
    }

    private fun parseIndex(root: JsonElement): List<BookInfo> =
        root.jsonObject["books"]!!.jsonArray.map { b ->
            val id = b.intOr("id")
            BookInfo(
                id = id, title = b.str("title"), short = b.str("short"), pages = b.intOr("pages"),
                units = b.jsonObject["units"]!!.jsonArray.map { NoteUnit(it.str("code"), it.str("title")) },
                rows = b.jsonObject["rows"]!!.jsonArray.mapIndexed { i, r ->
                    RowInfo(
                        book = id, index = i, unitIndex = r.intOr("u"), codes = r.strList("codes"),
                        title = r.str("title"), tag = r.str("tag"), pyq = r.str("pyq"),
                        p1 = r.intOr("p1"), p2 = r.intOr("p2"), subsectionCount = r.intOr("n"),
                        questionCount = r.intOr("q"),
                    )
                },
                unitQuestions = (b.jsonObject["uq"] as? JsonObject)?.entries
                    ?.associate { (k, v) -> k.toInt() to v.jsonPrimitive.int } ?: emptyMap(),
            )
        }

    private fun parsePlan(root: JsonElement): Plan {
        val days = root.jsonObject["days"]!!.jsonArray.map { d ->
            PlanDay(
                n = d.intOr("n"),
                date = LocalDate.parse(d.str("date")),
                dow = d.str("dow"),
                phase = d.str("phase"),
                focus = d.str("focus"),
                brief = d.strList("brief"),
                left = d.str("left"),
                type = d.str("type"),
                rows = d.jsonObject["rows"]!!.jsonArray.mapNotNull { r ->
                    val ref = r.jsonObject["ref"] as? JsonArray ?: return@mapNotNull null
                    PlanRow(
                        codes = r.strList("codes"), topic = r.str("topic"), pages = r.str("p"),
                        pyq = r.str("pyq"), priority = r.str("pri"),
                        book = ref[0].jsonPrimitive.int, row = ref[1].jsonPrimitive.int,
                    )
                },
                tasks = d.jsonObject["tasks"]!!.jsonArray.map { t ->
                    PlanTask(t.str("time"), t.str("block"), t.str("task"))
                },
                quiz = d.intOr("quiz"),
                repair = d.jsonObject.containsKey("repair"),
            )
        }
        return Plan(
            start = LocalDate.parse(root.str("start")),
            exam = LocalDate.parse(root.str("exam")),
            examLabel = root.str("examLabel"),
            days = days,
            buffer = root.jsonObject["buffer"]!!.jsonArray.map { BufferItem(it.str("dates"), it.str("work")) },
        )
    }
}
