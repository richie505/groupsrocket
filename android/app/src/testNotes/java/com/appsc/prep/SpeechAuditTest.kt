package com.appsc.prep

import androidx.test.core.app.ApplicationProvider
import com.appsc.prep.data.Repository
import com.appsc.prep.data.SpeechText
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Writes what read-aloud says for every page of the six books, for checking (not a pass/fail test). */
@RunWith(RobolectricTestRunner::class)
class SpeechAuditTest {
    @Test fun dump() = runBlocking {
        val out = System.getProperty("speech.audit") ?: return@runBlocking
        val repo = Repository { ApplicationProvider.getApplicationContext<android.content.Context>().assets.open(it) }
        repo.abbreviations
        repo.checkedAcronyms
        val sb = StringBuilder()
        for (b in 1..6) for (r in repo.book(b).rows) for ((si, s) in r.secs.withIndex()) {
            for ((_, line) in SpeechText.parts(s.title, s.blocks, b, r.title)) sb.append("$b:${r.index}:$si\t").append(line).append('\n')
        }
        java.io.File(out).writeText(sb.toString())
    }
}
