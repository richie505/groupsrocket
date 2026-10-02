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

    /** Short forms the notes define (tools/build_abbreviations.py), for read-aloud. */
    val abbreviations: Map<String, List<String>> by lazy {
        runCatching {
            readJson("abbr.json").jsonObject.mapValues { (_, v) -> v.jsonArray.map { it.jsonPrimitive.content } }
        }.getOrDefault(emptyMap()).also { SpeechText.fromNotes = it }
    }

    private val books = HashMap<Int, Book>()
    private val mutex = Mutex()

    suspend fun book(id: Int): Book = mutex.withLock {
        books[id] ?: withContext(Dispatchers.IO) { parseBook(readJson("book$id.json")) }.also { books[id] = it }
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
                        blocks = s.jsonObject["b"]!!.jsonArray.map { b -> parseBlock(b.jsonObject) },
                        universal = s.jsonObject.containsKey("u"),
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
