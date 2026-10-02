package com.appsc.prep

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
    @Test fun section() = shot("4_section", preload = 2) { SectionScreen(2, 0, nav) }
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
    @Test fun progress() = shot("8_progress") { ProgressScreen(nav) }
}
