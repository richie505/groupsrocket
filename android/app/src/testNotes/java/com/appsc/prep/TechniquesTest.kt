package com.appsc.prep

import androidx.test.core.app.ApplicationProvider
import com.appsc.prep.data.Question
import com.appsc.prep.data.Repository
import com.appsc.prep.data.Techniques
import com.appsc.prep.data.Techniques.Kind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TechniquesTest {
    private fun q(stem: String, opts: List<String>, a: Int, book: Int = 2) =
        Question("t", stem, emptyList(), opts, a, "", true, "", emptyList(), book = book)

    /** Guide example (Find just ONE wrong statement): statement II has 'only' and the pick that accepts it is explained. */
    @Test fun statementsFindOneWrong() {
        val x = q(
            "What is/are the provision(s) to safeguard the autonomy of the Supreme Court of India ?\n" +
                "I. While appointing the Supreme Court Judges, the President of India has to consult the Chief Justice of India.\n" +
                "II. The Supreme Court Judges can be removed by the Chief Justice of India only.\n" +
                "III. The salaries of the Judges are charged on the Consolidated Fund of India on which the legislature does not have to vote.\n" +
                "Which of the statements given above is/are correct ?",
            listOf("I and II only", "II and III only", "I, II and III", "I and III only"), 3,
        )
        assertEquals(Kind.STATEMENTS, Techniques.kind(x))
        assertTrue(Techniques.hints(x).joinToString(), Techniques.hints(x).any { it.startsWith("Statement II is in 3 of the 4 options") && it.contains("'only'") })
        val rv = Techniques.review(x, 2)
        assertTrue(rv.joinToString(), rv.any { it.contains("statement II as true, but it was FALSE") && it.contains("'only'") })
    }

    @Test fun assertionReason() {
        val x = q(
            "Assertion (A): Transfer earnings are not to be included in the estimation of National Income.\nReason (R): Transfer earnings are not payments for factor services",
            listOf("Both (A) and (R) are correct and (R) is the correct explanation of (A)", "Both (A) and (R) are correct and (R) is not the correct explanation of (A)",
                "(A) is correct, but (R) is not correct", "(R) is correct, but (A) is not correct"), 0, book = 3,
        )
        assertEquals(Kind.ASSERTION, Techniques.kind(x))
        assertTrue(Techniques.review(x, 2).first().contains("most common APPSC answer"))
    }

    @Test fun numbersAndMatch() {
        val n = q("Bhopal disaster occurred in the year", listOf("1984", "1985", "1982", "1986"), 0, book = 5)
        assertEquals(Kind.NUMBERS, Techniques.kind(n))
        assertTrue(Techniques.hints(n).any { it.contains("(1) or (2)") })
        val m = q(
            "Match the following Missions in List - I with the Time in List - II correctly.",
            listOf("A-4, B-3, C-2, D-1", "A-4, B-2, C-3, D-1", "A-1, B-3, C-2, D-4", "A-2, B-3, C-4, D-1"), 0, book = 6,
        )
        assertEquals(Kind.MATCH, Techniques.kind(m))
        assertTrue(Techniques.review(m, 3).any { it.contains("shares the most pairs") })
    }

    @Test fun negativeQuestionFlips() {
        val x = q("Which one of the following is not a mode of acquisition of Citizenship In India?",
            listOf("By birth", "By registration", "By naturalization", "By having ownership on land other properties"), 3)
        assertTrue(Techniques.isNegative(x.stem))
        assertTrue(Techniques.hints(x).first().contains("NOT correct"))
        assertTrue(Techniques.review(x, 0).first().contains("NOT / EXCEPT"))
    }

    /** Every MCQ in the bank: no crash, a hint for most, a technique note for every wrong pick of a scored MCQ. */
    @Test fun wholeBank() {
        val repo = Repository { ApplicationProvider.getApplicationContext<android.content.Context>().assets.open(it) }
        var total = 0; var hinted = 0; var reviewed = 0
        for (b in 1..5) {
            val m = runBlocking { repo.mcq(b) }
            (m.rows.values + m.units.values).flatten().distinctBy { it.id }.filter { it.kind == 's' && it.answer >= 0 && it.options.size >= 2 }.forEach { x ->
                total++
                if (Techniques.hints(x).isNotEmpty()) hinted++
                val wrong = (x.answer + 1) % x.options.size
                if (Techniques.review(x, wrong).isNotEmpty()) reviewed++
            }
        }
        println("techniques: $total scored MCQs, $hinted with hints, $reviewed with a wrong-answer note")
        assertEquals(total, reviewed)
        assertTrue(hinted > total * 0.9)
    }
}
