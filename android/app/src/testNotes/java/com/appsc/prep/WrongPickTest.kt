package com.appsc.prep

import com.appsc.prep.data.Repository
import com.appsc.prep.data.WrongPick
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A wrong answer highlights the explanation sentence that says why the picked option is wrong. */
class WrongPickTest {
    private fun said(x: String, r: IntRange?) = r?.let { x.substring(it) }

    @Test fun statementQuestion() {
        val x = "Statements 1 and 2 are correct. Statement 3 is false because the Regulating Act created the " +
            "Governor-General of Bengal, not the office of the Viceroy of India."
        val opts = listOf("1 and 2 only", "2 and 3 only", "1 and 3 only", "1, 2 and 3")
        val stem = "Consider the following statements about the Regulating Act, 1773:\n1. ...\n2. ...\n3. ...\nWhich are correct?"
        // took statement 3 as true: its sentence
        assertTrue(said(x, WrongPick.sentence(x, stem, opts, 0, 3))!!.startsWith("Statement 3 is false"))
        // the right answer highlights nothing
        assertNull(WrongPick.sentence(x, stem, opts, 0, 0))
    }

    @Test fun oneSentenceWithAPartOnTheWrongOption() {
        val stem = "Who was the first Indian member of the Viceroy's Executive Council?"
        val opts = listOf("Satyendra Sinha", "Dadabhai Naoroji", "Gopal Krishna Gokhale", "Pherozeshah Mehta")
        val x = "Satyendra Sinha joined the Council in 1909, whereas Dadabhai Naoroji was elected to the British House of Commons."
        assertEquals("whereas Dadabhai Naoroji was elected to the British House of Commons.", said(x, WrongPick.sentence(x, stem, opts, 0, 1)))
    }

    @Test fun optionQuestion() {
        val stem = "Which body was set up by Pitt's India Act, 1784?"
        val opts = listOf("Supreme Court at Calcutta", "Board of Control", "Federal Court", "Council of India")
        val x = "Pitt's India Act created the Board of Control to supervise the Company. The Supreme Court at Calcutta " +
            "was set up earlier, by the Regulating Act of 1773. The Council of India came in 1858."
        assertEquals("The Supreme Court at Calcutta was set up earlier, by the Regulating Act of 1773.", said(x, WrongPick.sentence(x, stem, opts, 1, 0)))
        assertEquals("The Council of India came in 1858.", said(x, WrongPick.sentence(x, stem, opts, 1, 3)))
    }

    /** Over the whole bank: how often a wrong pick gets a sentence (printed), and it is never the whole text. */
    @Test fun acrossTheBank() = runBlocking {
        val repo = Repository { File("src/main/assets/$it").inputStream() }
        var picks = 0
        var found = 0
        for (b in repo.index.map { it.id }) {
            repo.mcq(b).rows.values.flatten().forEach { q ->
                if (q.answer < 0) return@forEach
                q.options.indices.filter { it != q.answer }.forEach { p ->
                    picks++
                    val r = WrongPick.sentence(q.explanation, q.stem, q.options, q.answer, p) ?: return@forEach
                    found++
                    assertTrue(q.id, r.last < q.explanation.length && r.first <= r.last)
                    assertTrue(q.id, (r.last - r.first + 1) < q.explanation.length)
                }
            }
        }
        println("WrongPick: a sentence for $found of $picks wrong picks (${found * 100 / picks}%)")
        // ROCKET explanations are mostly one sentence on the right answer, so few name the wrong options
        assertTrue("$found of $picks", found * 100 / picks >= 3)
    }

    @Test fun statementNotNamedByNumber() {
        val stem = "Consider the following statements about the Amber Box:\n1. It includes MSP and input subsidies.\n" +
            "2. Its de minimis limit for developing countries is 10 per cent.\nWhich of the statements given above are correct?"
        val opts = listOf("1 only", "2 only", "Both 1 and 2", "Neither 1 nor 2")
        val x = "Amber Box support includes trade-distorting support such as MSP and input subsidies. " +
            "The de minimis limits are 5 per cent for developed countries and 10 per cent for developing countries."
        // left statement 2 out: its sentence
        assertTrue(said(x, WrongPick.sentence(x, stem, opts, 2, 0))!!.startsWith("The de minimis limits"))
    }

    /** "From your notes": the notes line with the answer, from the question's own page. */
    @Test fun notesExcerpts() = runBlocking {
        val repo = Repository { File("src/main/assets/$it").inputStream() }
        val mcq = repo.mcq(2)
        val book = repo.book(2)
        val q = mcq.rows.getValue(0).first { it.options.getOrNull(it.answer) == "Regulating Act, 1773" }
        val (row, sec) = mcq.placeOf(q.id)!!
        val lines = com.appsc.prep.data.NotesExcerpt.forQuestion(q, book.rows[row].secs[sec.coerceAtLeast(0)].blocks)
        assertTrue("${q.stem} -> $lines", lines.isNotEmpty() && lines.none { it.contains("[GK]") })
        // across the bank: how many questions get a quote from their notes page
        var asked = 0
        var quoted = 0
        for (b in repo.index.map { it.id }) {
            val m = repo.mcq(b)
            val bk = repo.book(b)
            m.rows.forEach { (r, qs) ->
                qs.forEach { x ->
                    val (rr, s) = m.placeOf(x.id) ?: return@forEach
                    val secs = bk.rows[rr].secs
                    asked++
                    val got = if (s >= 0) com.appsc.prep.data.NotesExcerpt.forQuestion(x, secs[s].blocks)
                    else secs.map { com.appsc.prep.data.NotesExcerpt.forQuestion(x, it.blocks) }.maxByOrNull { it.size }.orEmpty()
                    if (got.isNotEmpty()) quoted++
                }
            }
        }
        println("NotesExcerpt: a quote for $quoted of $asked questions (${quoted * 100 / asked}%)")
        assertTrue("$quoted of $asked", quoted * 100 / asked >= 60)
    }
}
