package com.appsc.prep

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.appsc.prep.platform.AndroidPlatform
import com.appsc.prep.platform.PrefsStorage
import com.appsc.prep.platform.ReadAloud
import com.appsc.prep.ui.components.SpeechPage
import com.appsc.prep.data.ProgressStore
import com.appsc.prep.data.Repository
import com.appsc.prep.ui.components.AppState
import com.appsc.prep.ui.components.LocalApp
import com.appsc.prep.ui.screens.BooksScreen
import com.appsc.prep.ui.screens.DayScreen
import com.appsc.prep.ui.screens.Nav
import com.appsc.prep.ui.screens.PlanScreen
import com.appsc.prep.ui.screens.ProgressScreen
import com.appsc.prep.ui.screens.QuizRound
import com.appsc.prep.ui.screens.QuizScreen
import com.appsc.prep.ui.screens.QuizSource
import com.appsc.prep.ui.screens.ReaderScreen
import com.appsc.prep.ui.screens.SectionScreen
import com.appsc.prep.ui.screens.TodayScreen
import com.appsc.prep.ui.theme.PrepTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.onAllNodesWithContentDescription
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val nav = object : Nav {
        override fun quiz(kind: String, book: Int, index: Int, mode: String, sub: Int) {}
        override fun day(n: Int) {}
        override fun row(book: Int, row: Int) {}
        override fun read(book: Int, row: Int, sec: Int) {}
        override fun book(id: Int) {}
        override fun back() {}
    }

    private fun shot(name: String, preload: Int? = null, content: @Composable () -> Unit) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ReadAloud.init(ctx).stop() // each screen starts without read-aloud
        val app = AppState(Repository { ctx.assets.open(it) }, ProgressStore(PrefsStorage(ctx)), AndroidPlatform(ctx))
        if (preload != null) runBlocking { app.repo.book(preload); app.repo.mcq(preload) }
        rule.setContent {
            PrepTheme { CompositionLocalProvider(LocalApp provides app) { content() } }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun today() = shot("1_today") { TodayScreen(nav) }
    @Test fun plan() = shot("2_plan") { PlanScreen(nav) }
    @Test fun day1() = shot("3_day1") { DayScreen(1, nav) }
    @Test fun day7() = shot("3b_day7_weekly_test") { DayScreen(7, nav) }
    @Test fun day84() = shot("3c_day84_mock") { DayScreen(84, nav) }

    /** Today's MCQs: the reader picks unattempted, incorrect or all, with the counts. */
    @Test fun dayPracticeSets() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = Repository { ctx.assets.open(it) }
        val day = repo.plan.days.first { it.n == 1 }
        val ids = runBlocking { day.rows.flatMap { r -> repo.mcq(r.book).rows[r.row].orEmpty() }.map { it.id }.distinct() }
        val store = ProgressStore(PrefsStorage(ctx))
        ids.take(10).forEachIndexed { i, id -> store.recordAnswer(id, i % 3 != 0) } // 4 wrong, 6 right
        val sets = com.appsc.prep.ui.components.practiceSets(ids, store.answers, store.seen)
        assertEquals(com.appsc.prep.ui.components.PracticeSets(ids.size - 10, 4, ids.size), sets)
        shot("27_day_practice_sets", preload = 2) { DayScreen(1, nav) }
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasText("Incorrect"))
        rule.waitForIdle()
        rule.onNodeWithText("${ids.size - 10}").assertExists()
        rule.onRoot().captureRoboImage("screenshots/27_day_practice_sets.png")
    }
    /**
     * Stepped clock: after the Google and word-meaning tests in the same run, this screen never reported idle
     * (an order-dependent Robolectric hang that does not occur alone), so it is drawn after 3 s instead of waiting.
     */
    @Test fun section() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val app = AppState(Repository { ctx.assets.open(it) }, ProgressStore(PrefsStorage(ctx)), AndroidPlatform(ctx))
        runBlocking { app.repo.book(2); app.repo.mcq(2) }
        rule.mainClock.autoAdvance = false
        rule.setContent { PrepTheme { CompositionLocalProvider(LocalApp provides app) { SectionScreen(2, 0, nav) } } }
        rule.mainClock.advanceTimeBy(3000)
        rule.onNodeWithText("POL · Indian Polity").assertExists()
        rule.onRoot().captureRoboImage("screenshots/4_section.png")
        rule.mainClock.autoAdvance = true
    }
    @Test fun reader() = shot("5_reader", preload = 2) { ReaderScreen(2, 0, 0, nav) }
    @Test fun readerTable() = shot("6_reader_table", preload = 2) { ReaderScreen(2, 0, 1, nav) }

    /** Read aloud: the Listen button opens the player bar, speaks the page and highlights the paragraph. */
    @Test fun readerListen() {
        shot("15_listen", preload = 2) { ReaderScreen(2, 106, 0, nav) }
        rule.onNodeWithContentDescription("Listen").performClick()
        rule.waitForIdle()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Pause").assertExists()
        rule.onRoot().captureRoboImage("screenshots/15_listen.png")
        assertEquals("2:106:0", ReadAloud.playback.value.pageId)
        assertTrue(ReadAloud.playback.value.playing)
        ReadAloud.stop()
    }

    /**
     * Long-press a word in the notes, tap Meaning: the dictionary card shows its meaning and notes pages.
     * Android 8.1: the selection magnifier of Android 9+ needs a real screen surface, which Robolectric lacks.
     */
    @Config(sdk = [27])
    @Test fun meaningOfASelectedWord() {
        shot("16_meaning", preload = 2) { ReaderScreen(2, 0, 0, nav) }
        // ROCKET Polity #1: "Dyarchy at the Centre under the Government of India Act of 1935 was never implemented."
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasText("Dyarchy at the Centre", substring = true))
        rule.waitForIdle()
        rule.onAllNodesWithText("Dyarchy at the Centre", substring = true)[0].performTouchInput { longClick(topLeft + androidx.compose.ui.geometry.Offset(width * 0.04f, 14f)) }
        rule.waitForIdle()
        rule.onNodeWithText("Meaning").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("MEANING").fetchSemanticsNodes().isNotEmpty() }
        // no ROCKET sheet title names dyarchy, so the card quotes the facts (FROM YOUR NOTES), not headings
        rule.waitUntil(20_000) { rule.onAllNodesWithText("FROM YOUR NOTES").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("screenshots/16_meaning.png")
    }

    /** Google's copy menu does not show inside the app, so the page's paragraphs are listed to tick. */
    @Test fun pickTextFromGoogle() {
        var added = ""
        val paras = listOf(
            "Madhya Pradesh, Maharashtra, and Uttar Pradesh record the highest total numbers of crimes against children.",
            "Top States for Crimes Against Children",
            "Child marriage cases under the PCMA are led by Karnataka, Assam and West Bengal.",
        )
        shot("25_pick_text") { com.appsc.prep.ui.components.PickText(paras, preselect = 0, onAdd = { added = it }) {} }
        rule.onNodeWithText(paras[2]).performClick()
        rule.onNodeWithText("Add (2)").performClick()
        assertEquals(paras[0] + "\n" + paras[2], added)
    }

    /** ⋮ → Explain simply: Google's AI Mode explains the page for a class 6 reader. */
    @Test fun explainSimply() {
        shot("28_explain_menu", preload = 2) { ReaderScreen(2, 0, 1, nav) }
        rule.onNodeWithContentDescription("More").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Explain simply").assertExists()
        rule.onRoot().captureRoboImage("screenshots/28_explain_menu.png")
        // (the Google page it opens is the one tested in keyTermsOnAPage; a second web page left running in the
        // same test run keeps later screens from going idle)
        // the question: plain words for class 6, the page's text without source tags, short enough for a link
        val p = com.appsc.prep.ui.screens.simplePrompt(
            "Lapsing of bills",
            "Lapsing of bills\n• Bill pending in LS lapses [GK] (CDI).\nNot in your sources: x\n" + "Rajya Sabha is never dissolved. ".repeat(80),
        )
        assertTrue(p, p.startsWith("Explain this in very simple English, as if to a class 6 student."))
        assertTrue(p, p.contains("Topic: Lapsing of bills") && p.contains("Bill pending in LS lapses."))
        assertTrue(p, !p.contains("[GK]") && !p.contains("CDI") && !p.contains("Not in your sources"))
        assertTrue("${p.length}", p.length < 1700)
        assertTrue(com.appsc.prep.ui.components.googleAiUrl("a b").startsWith("https://www.google.com/search?udm=50&"))
    }

    /** Every notes page ends with its key terms; tapping one opens its meaning. */
    @Test fun keyTermsOnAPage() {
        shot("17_key_terms", preload = 2) { ReaderScreen(2, 0, 0, nav) }
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasText("KEY TERMS"))
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("screenshots/17_key_terms.png")
        rule.onNodeWithText("dyarchy").performClick()
        rule.waitUntil(20_000) { rule.onAllNodesWithText("IN INDIAN CONTEXT").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("screenshots/18_key_term_meaning.png")
        // Google inside the app
        rule.onNodeWithText("Search on Google").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Google · dyarchy")[0].assertExists()
        rule.onRoot().captureRoboImage("screenshots/19_google.png")
        // signed-in Google in Chrome, and the same question to Gemini
        rule.onAllNodesWithContentDescription("Open in Chrome")[0].assertExists()
        rule.onAllNodesWithContentDescription("Ask Gemini")[0].assertExists()
        // close Google: its web page would otherwise keep the next test from ever going idle
        rule.onAllNodesWithContentDescription("Close")[0].performClick()
        rule.waitForIdle()
        assertEquals("https://www.google.com/search?hl=en&gl=in&q=84th+Amendment", com.appsc.prep.ui.components.googleUrl("84th Amendment"))
    }

    /** Reading page A, then opening page B by hand: B is read, and the screen stays on B. */
    @Test fun readAloudFollowsTheOpenedPage() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ReadAloud.init(ctx).play(SpeechPage("2:106:0", "A", listOf("one", "two")), 0, 1f) { null }
        val app = AppState(Repository { ctx.assets.open(it) }, ProgressStore(PrefsStorage(ctx)), AndroidPlatform(ctx))
        runBlocking { app.repo.book(2); app.repo.mcq(2) }
        rule.setContent { PrepTheme { CompositionLocalProvider(LocalApp provides app) { ReaderScreen(2, 0, 0, nav) } } }
        rule.waitForIdle()
        assertEquals("2:0:0", ReadAloud.playback.value.pageId)
        // still on page B, not taken back to A
        rule.onAllNodesWithText("Changing structure and urban families", substring = true).assertCountEquals(0)
        ReadAloud.stop()
    }
    @Test fun quiz() = shot("9_quiz") { QuizScreen(QuizSource("row", 2, 0), "new", "MCQ Practice", nav) }

    @Test fun quizExplained() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = Repository { ctx.assets.open(it) }
        // first History row whose first question carries an explanation
        val mcq = runBlocking { repo.mcq(1) }
        val row = mcq.rows.entries.sortedBy { it.key }.first { it.value.firstOrNull()?.explanation?.isNotBlank() == true }
        val q = row.value.first()
        shot("10_quiz_answered", preload = 1) { QuizScreen(QuizSource("row", 1, row.key), "all", "MCQ Practice", nav) }
        rule.onNodeWithText(q.options[q.answer]).performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("screenshots/10_quiz_answered.png")
    }

    /** A wrong answer: the explanation sentence on the picked option highlighted, and the notes lines quoted. */
    @Test fun wrongAnswerHighlightAndNotes() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = Repository { ctx.assets.open(it) }
        val mcq = runBlocking { repo.mcq(5) }
        // a question whose wrong pick has a sentence to highlight (the first in the book's first rows)
        val (row, q, wrong) = mcq.rows.entries.sortedBy { it.key }.asSequence().flatMap { (r, qs) ->
            qs.take(1).asSequence().mapNotNull { q ->
                (q.options.indices - q.answer).firstOrNull { com.appsc.prep.data.WrongPick.sentence(q.explanation, q.stem, q.options, q.answer, it) != null }
                    ?.let { Triple(r, q, it) }
            }
        }.first()
        shot("29_wrong_answer", preload = 5) { QuizScreen(QuizSource("row", 5, row), "all", "MCQ Practice", nav) }
        rule.onNodeWithText(q.options[wrong]).performClick()
        rule.waitForIdle()
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasText("EXPLANATION"))
        rule.waitForIdle()
        rule.onNodeWithText("Highlighted: why option (${wrong + 1}) is wrong").assertExists()
        rule.onRoot().captureRoboImage("screenshots/29_wrong_answer.png")
    }

    /** A Polity statements question: hint opened before answering, then a wrong pick with its technique note. */
    @Test fun hintAndTechnique() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val q = runBlocking { Repository { ctx.assets.open(it) }.mcq(2) }.rows.values.flatten().first {
            it.kind == 's' && com.appsc.prep.data.Techniques.kind(it) == com.appsc.prep.data.Techniques.Kind.STATEMENTS &&
                it.stem.length < 420 && com.appsc.prep.data.Techniques.hints(it).size >= 3
        }
        shot("13_hint") { QuizRound("h", listOf(q, q), listOf(q), {}, {}) }
        rule.onNodeWithText("Stuck? Show a hint").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("screenshots/13_hint.png")
        rule.onNodeWithText(q.options[(q.answer + 1) % q.options.size]).performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("screenshots/14_wrong_technique.png")
    }


    @Test fun books() = shot("7_notes") { BooksScreen(nav) }
    @Test fun progress() {
        shot("8_progress") { ProgressScreen(nav) }
        // the backup card at the end
        rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction()).performScrollToNode(androidx.compose.ui.test.hasText("Back up now"))
        rule.waitForIdle()
        rule.onAllNodesWithText("Last backup: never").fetchSemanticsNodes().let { assertEquals(1, it.size) }
        rule.onRoot().captureRoboImage("screenshots/26_backup.png")
    }
}
