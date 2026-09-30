package com.appsc.prep.data

import java.time.LocalDate

/** A styled piece of text. [flags]: 1 = bold, 2 = italic, 4 = muted (e.g. [GK]). */
data class Run(val text: String, val flags: Int) {
    val bold get() = flags and 1 != 0
    val italic get() = flags and 2 != 0
    val muted get() = flags and 4 != 0
}

sealed interface Block

/**
 * [kind]: 'b' bullet, 's' sub-bullet, 'p' paragraph, 'x' exam angle,
 * 'a' see-also / cross reference, 'n' grey note.
 */
data class TextBlock(val kind: Char, val runs: List<Run>) : Block

data class TableBlock(val head: List<List<Run>>, val rows: List<List<List<Run>>>) : Block

/** A ■ heading in the notes, with its content. */
data class Subsection(
    val title: String,
    val badges: List<String>,
    val page: Int,
    val blocks: List<Block>,
    val universal: Boolean,
) {
    val wordCount: Int by lazy {
        blocks.sumOf { b ->
            when (b) {
                is TextBlock -> b.runs.sumOf { r -> r.text.count { it == ' ' } + 1 }
                is TableBlock -> (b.rows + listOf(b.head)).sumOf { row ->
                    row.sumOf { cell -> cell.sumOf { r -> r.text.count { it == ' ' } + 1 } }
                }
            }
        }
    }
}

/** A syllabus row (shown as a "Section" in the app). */
data class NoteRow(
    val book: Int,
    val index: Int,
    val unitIndex: Int,
    val codes: List<String>,
    val tag: String,
    val title: String,
    val sub: List<String>,
    val pyq: String,
    val p1: Int,
    val p2: Int,
    val secs: List<Subsection>,
    val sources: List<String>,
    val see: List<List<Run>>,
)

/** A UNIT (shown as a "Topic" in the app). */
data class NoteUnit(val code: String, val title: String)

data class Book(
    val id: Int,
    val title: String,
    val short: String,
    val pages: Int,
    val units: List<NoteUnit>,
    val rows: List<NoteRow>,
)

// ---- lightweight catalogue (index.json) ----

data class RowInfo(
    val book: Int,
    val index: Int,
    val unitIndex: Int,
    val codes: List<String>,
    val title: String,
    val tag: String,
    val pyq: String,
    val p1: Int,
    val p2: Int,
    val subsectionCount: Int,
    val questionCount: Int,
)

data class BookInfo(
    val id: Int,
    val title: String,
    val short: String,
    val pages: Int,
    val units: List<NoteUnit>,
    val rows: List<RowInfo>,
    val unitQuestions: Map<Int, Int>,
) {
    val subsectionTotal: Int get() = rows.sumOf { it.subsectionCount }
}

// ---- 90-day plan ----

data class PlanRow(
    val codes: List<String>,
    val topic: String,
    val pages: String,
    val pyq: String,
    val priority: String,
    val book: Int,
    val row: Int,
)

data class PlanTask(val time: String, val block: String, val task: String)

data class PlanDay(
    val n: Int,
    val date: LocalDate,
    val dow: String,
    val phase: String,
    val focus: String,
    val brief: List<String>,
    val left: String,
    val type: String,
    val rows: List<PlanRow>,
    val tasks: List<PlanTask>,
    /** Test size for review / revision / mock days (random questions); 0 = every question on the day's sections. */
    val quiz: Int = 0,
    /** Repair day: the test is the questions you last answered wrong. */
    val repair: Boolean = false,
)

data class BufferItem(val dates: String, val work: String)

data class Plan(
    val start: LocalDate,
    val exam: LocalDate,
    val examLabel: String,
    val days: List<PlanDay>,
    val buffer: List<BufferItem>,
)

fun subsectionId(book: Int, row: Int, sec: Int) = "$book:$row:$sec"

// ---- MCQ practice (MCQs written from the notes) ----

data class Question(
    val id: String,
    val stem: String,
    val table: List<List<String>>,
    val options: List<String>,
    val answer: Int,
    val source: String,
    val appsc: Boolean,
    val explanation: String,
    val notes: List<String>,
    /** 's' scored, 'f' flashcard (answer only, self-graded), 'u' unscored (no official key / cancelled). */
    val kind: Char = 's',
    val answerText: String = "",
    val cancelled: Boolean = false,
    /** Book (subject) the question is filed under: 1 History … 6 Current Affairs. Drives the MCQ technique hints. */
    val book: Int = 0,
    /** The exam technique the question trains. */
    val technique: String = "",
) {
    val scored get() = kind != 'u'
}

/** MCQs of one book: by notes row (Section) and by unit (Topic, general questions). */
data class BookMcq(
    val rows: Map<Int, List<Question>>,
    val units: Map<Int, List<Question>>,
    /** Per row, parallel to rows[row]: the subsection each question belongs to (-1 = section only). */
    val subs: Map<Int, List<Int>> = emptyMap(),
) {
    fun subQuestions(row: Int, sec: Int): List<Question> {
        val qs = rows[row] ?: return emptyList()
        val s = subs[row] ?: return emptyList()
        return qs.filterIndexed { i, _ -> s.getOrNull(i) == sec }
    }

    fun subCount(row: Int, sec: Int): Int = subs[row]?.count { it == sec } ?: 0
}
