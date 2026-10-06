package com.appsc.prep

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.appsc.prep.data.ProgressStore
import com.appsc.prep.data.Repository
import com.appsc.prep.data.TableBlock
import com.appsc.prep.data.TextBlock
import com.appsc.prep.platform.AndroidPlatform
import com.appsc.prep.platform.PrefsStorage
import com.appsc.prep.ui.components.AppState
import com.appsc.prep.ui.components.LocalApp
import com.appsc.prep.ui.screens.DayScreen
import com.appsc.prep.ui.screens.Nav
import com.appsc.prep.ui.screens.ReaderScreen
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

/** Rocket Revision: each ROCKET SHEET's revision points, built from its MCQs (scripts/build_revision.py). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class RevisionTest {
    @get:Rule
    val rule = createComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repo by lazy { Repository { ctx.assets.open(it) } }

    /** The same assets read as the notes app does (no edition.json): the ROCKET notes, to compare with. */
    private val notes by lazy {
        Repository { name -> if (name == "edition.json") throw java.io.FileNotFoundException(name) else ctx.assets.open(name) }
    }

    private val nav = object : Nav {
        override fun quiz(kind: String, book: Int, index: Int, mode: String, sub: Int) {}
        override fun day(n: Int) {}
        override fun row(book: Int, row: Int) {}
        override fun read(book: Int, row: Int, sec: Int) {}
        override fun book(id: Int) {}
        override fun back() {}
    }

    private fun line(b: com.appsc.prep.data.Block) = when (b) {
        is TextBlock -> b.runs.joinToString("") { it.text }
        is TableBlock -> ""
    }

    @Test fun sameSheetsOnePageEach() = runBlocking {
        assertTrue(repo.revision)
        assertEquals("Rocket Revision", repo.appName)
        var sheets = 0
        for (info in repo.index) {
            val rev = repo.book(info.id)
            val full = notes.book(info.id)
            // every sheet of the notes is here, at the same place, so the 90-day plan and progress line up
            assertEquals(full.rows.map { it.title }, rev.rows.map { it.title })
            rev.rows.forEachIndexed { i, r ->
                assertEquals(1, r.secs.size)
                assertEquals(1, info.rows[i].subsectionCount) // the revision app's own index.json
                assertTrue(r.secs[0].title, r.secs[0].title.startsWith("Revision points ("))
            }
            sheets += rev.rows.size
        }
        assertEquals(721, sheets)
    }

    /** A sheet's revision points are what its MCQs test: answer in bold, each point once, no question talk. */
    @Test fun pointsFromTheMcqs() = runBlocking {
        val page = repo.book(2).rows[0].secs[0] // Indian Polity #1: Constitutional Development and Key Firsts
        val t = page.blocks.joinToString("\n") { line(it) }
        for (fact in listOf("Regulating Act, 1773", "Rajendra Prasad", "Sukumar Sen", "Nagaur")) {
            assertTrue("$fact missing:\n$t", t.contains(fact))
        }
        assertTrue(t, t.startsWith("Source: Indian Polity – ROCKET Sheets PDF, ROCKET SHEET #1"))
        assertTrue(t, page.blocks.any { b -> b is TextBlock && b.runs.any { it.bold } })
        for (info in repo.index) {
            repo.book(info.id).rows.forEach { r ->
                val lines = r.secs[0].blocks.map { line(it) }
                assertEquals(r.title, lines.size, lines.toSet().size)
                assertTrue(r.title, lines.size > 1)
                lines.forEach { l ->
                    assertTrue("${r.title}: $l", !Regex("""^(Both statements|Statements? [\d, and]+ (is|are)|The incorrect)""").containsMatchIn(l))
                }
            }
        }
    }

    /** Read-aloud reads the revision points; the source line is not spoken. */
    @Test fun readAloudReadsPoints() = runBlocking {
        val row = repo.book(2).rows[0]
        val spoken = com.appsc.prep.data.SpeechText.parts(row.secs[0].title, row.secs[0].blocks, 2).joinToString(" ") { it.second }
        assertTrue(spoken, spoken.contains("Regulating Act"))
        assertTrue(spoken, !spoken.contains("Source:"))
    }

    private fun app() = AppState(repo, ProgressStore(PrefsStorage(ctx)), AndroidPlatform(ctx)).also {
        runBlocking { it.repo.book(2); it.repo.mcq(2) }
    }

    @Test fun today() {
        val app = app()
        rule.setContent { PrepTheme { CompositionLocalProvider(LocalApp provides app) { TodayScreen(nav) } } }
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithText("Rocket Revision").fetchSemanticsNodes().size)
        rule.onRoot().captureRoboImage("screenshots/revision_1_today.png")
    }

    @Test fun day1() {
        val app = app()
        rule.setContent { PrepTheme { CompositionLocalProvider(LocalApp provides app) { DayScreen(1, nav) } } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("screenshots/revision_2_day1.png")
    }

    @Test fun page() {
        val app = app()
        rule.setContent { PrepTheme { CompositionLocalProvider(LocalApp provides app) { ReaderScreen(2, 0, 0, nav) } } }
        rule.waitForIdle()
        assertEquals(1, rule.onAllNodesWithContentDescription("Mark as revised").fetchSemanticsNodes().size)
        rule.onRoot().captureRoboImage("screenshots/revision_3_page.png")
    }
}
