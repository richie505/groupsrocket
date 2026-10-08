package com.appsc.prep.desktop

import com.appsc.prep.data.ProgressStore
import com.appsc.prep.data.Repository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DesktopTest {
    @Test
    fun progressSurvivesRestart() {
        val file = Files.createTempDirectory("prep").resolve("progress.json")
        ProgressStore(FileStorage(file)).apply {
            recordAnswer("q1", true)
            recordAnswer("q2", false)
            toggleDone("1:0:0")
            changeTextScale(0.1f)
        }
        // Saves run in the background; wait for the file to catch up.
        var tries = 0
        while (!(Files.exists(file) && "1.1" in Files.readString(file)) && tries++ < 50) Thread.sleep(50)
        val again = ProgressStore(FileStorage(file))
        assertEquals(mapOf("q1" to true, "q2" to false), again.answers)
        assertTrue(again.isDone("1:0:0"))
        assertEquals(1.1f, again.textScale, 0.001f)
    }

    @Test
    fun appDataIsInsideTheJar() {
        val repo = Repository(::openAsset)
        assertEquals(5, repo.index.size) // the five ROCKET books
        val mcq = runBlocking { repo.mcq(2) }
        assertTrue(mcq.rows.values.sumOf { it.size } > 1000)
    }
}
