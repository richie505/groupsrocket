package com.appsc.prep

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assertIsDisplayed
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

/** Rocket Revision: each topic page as a mind map built from the notes, every fact kept (scripts/build_mindmap.py). */
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

    @Test fun sameTopicsSamePages() = runBlocking {
        assertTrue(repo.revision)
        assertEquals("Rocket Revision", repo.appName)
        var topics = 0
        for (info in repo.index) {
            val rev = repo.book(info.id)
            val full = notes.book(info.id)
            // every topic and page of the notes is here, at the same place, so the 90-day plan, MCQ practice and progress line up
            assertEquals(full.rows.map { it.title }, rev.rows.map { it.title })
            rev.rows.forEachIndexed { i, r ->
                assertEquals(r.title, full.rows[i].secs.size, r.secs.size)
                assertEquals(r.secs.size, info.rows[i].subsectionCount) // the revision app's own index.json
                r.secs.forEachIndexed { j, s ->
                    assertEquals(full.rows[i].secs[j].title, s.title)
                    assertTrue("${r.title} / ${s.title}", s.map.isNotEmpty()) // every page is a mind map
                }
            }
            topics += rev.rows.size
        }
        assertEquals(226, topics)
    }

    private fun mapText(nodes: List<com.appsc.prep.data.MapNode>): String =
        nodes.joinToString("\n") { n -> n.runs.joinToString("") { it.text } + "\n" + mapText(n.children) }

    private fun pageText(blocks: List<com.appsc.prep.data.Block>) = blocks.filter { !(it is TextBlock && it.kind in "na") }
        .joinToString("\n") { b ->
            when (b) {
                is TextBlock -> b.runs.joinToString("") { it.text }.let { if (b.kind == 'h') it.replace(Regex("""^\s*\d+\.\s*"""), "") else it }
                is TableBlock -> (listOf(b.head) + b.rows).joinToString("\n") { r -> r.joinToString(" | ") { c -> c.joinToString("") { it.text } } }
            }
        }

    /** A page's mind map keeps its facts: every number of the notes page, and the Source line never. */
    @Test fun mapsKeepTheFacts() = runBlocking {
        val m = mapText(repo.book(2).rows[0].secs[0].map) // Indian Polity · Constitutional development · Company rule
        for (fact in listOf("Regulating Act", "Warren Hastings", "1773")) assertTrue("$fact missing:\n$m", m.contains(fact))
        val numbers = Regex("""\d+(?:\.\d+)?""")
        for (info in repo.index) {
            val full = notes.book(info.id)
            repo.book(info.id).rows.forEachIndexed { i, r ->
                r.secs.forEachIndexed { j, s ->
                    val x = mapText(s.map)
                    assertTrue("${s.title}: $x", !x.contains("ROCKET SHEET") && !x.startsWith("Source:"))
                    val inMap = numbers.findAll(x.replace(",", "")).map { it.value }.toSet()
                    val pt = pageText(full.rows[i].secs[j].blocks).replace(",", "")
                    for (n in numbers.findAll(pt).map { it.value }.toSet()) {
                        assertTrue("${s.title}: $n missing from the map: " + pt.lines().filter { l -> numbers.findAll(l).any { it.value == n } }, n in inMap)
                    }
                }
            }
        }
    }

    /** Read-aloud follows the map: line n of the page text is the n-th node of the map, on every page. */
    @Test fun readAloudLinesAreMapNodes() = runBlocking {
        for (info in repo.index) {
            repo.book(info.id).rows.forEach { r ->
                r.secs.forEach { s ->
                    val lines = com.appsc.prep.ui.components.mapLines(s.map)
                    assertEquals("${s.title}: lines", s.blocks.size, (lines.values.maxOrNull() ?: -1) + 1)
                    s.map.forEach { br -> assertEquals(s.title, 'h', (s.blocks[lines[br]!!] as TextBlock).kind) }
                }
            }
        }
    }

    /** A stand-in for the phone's text-to-speech: it only says which part is being read. */
    private class FakeSpeech : com.appsc.prep.ui.components.Speech {
        override val playback = androidx.compose.runtime.mutableStateOf(com.appsc.prep.ui.components.Playback())
        override fun play(page: com.appsc.prep.ui.components.SpeechPage, from: Int, rate: Float, next: () -> com.appsc.prep.ui.components.SpeechPage?) {
            playback.value = com.appsc.prep.ui.components.Playback(active = true, playing = true, pageId = page.id, part = from)
        }
        override fun pause() { playback.value = playback.value.copy(playing = false) }
        override fun resume() { playback.value = playback.value.copy(playing = true) }
        override fun seek(part: Int) { playback.value = playback.value.copy(part = part) }
        override fun setRate(rate: Float) {}
        override fun stop() { playback.value = com.appsc.prep.ui.components.Playback() }
    }

    /** Reading a mind map aloud keeps the node being read on screen, highlighted - it does not jump away. */
    @Test fun readAloudStaysOnTheNodeBeingRead() {
        val fake = FakeSpeech()
        val platform = object : com.appsc.prep.ui.components.Platform by AndroidPlatform(ctx) {
            override val speech get() = fake
        }
        val app = AppState(repo, ProgressStore(PrefsStorage(ctx)), platform)
        runBlocking { app.repo.book(2); app.repo.mcq(2) }
        rule.setContent { PrepTheme { CompositionLocalProvider(LocalApp provides app) { ReaderScreen(2, 0, 0, nav) } } }
        rule.waitForIdle()
        val page = runBlocking { app.repo.book(2).rows[0].secs[0] }
        val parts = com.appsc.prep.data.SpeechText.parts(page.title, page.blocks, 2, "")
        val last = page.map.last().let { br -> generateSequence(br) { it.children.lastOrNull() }.last() }
        val lastText = last.runs.joinToString("") { it.text }
        val at = parts.indexOfLast { lastText.take(12) in it.second }
        assertTrue(lastText, at > 0)
        rule.runOnIdle { fake.play(com.appsc.prep.ui.components.SpeechPage("2:0:0", page.title, parts.map { it.second }), at, 1f) { null } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("screenshots/revision_5_mindmap_reading.png")
        rule.onAllNodesWithText(lastText.take(12), substring = true)[0].assertIsDisplayed()
    }

    /** Read-aloud reads the revision points; the source line is not spoken. */
    @Test fun readAloudReadsPoints() = runBlocking {
        val row = repo.book(2).rows[0]
        val spoken = com.appsc.prep.data.SpeechText.parts(row.secs[0].title, row.secs[0].blocks, 2).joinToString(" ") { it.second }
        assertTrue(spoken, spoken.contains("Regulating Act"))
        assertTrue(spoken, !spoken.contains("ROCKET SHEET"))
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
