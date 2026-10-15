package com.appsc.prep

import androidx.test.core.app.ApplicationProvider
import com.appsc.prep.data.Repository
import com.appsc.prep.data.Run
import com.appsc.prep.data.TableBlock
import com.appsc.prep.data.TextBlock
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Writes every full form the notes show ("VC (Vice-Chairperson)") with its line, for checking (not a pass/fail test). */
@RunWith(RobolectricTestRunner::class)
class AcronymAuditTest {
    @Test fun dump() = runBlocking {
        val out = System.getProperty("acronym.audit") ?: return@runBlocking
        val repo = Repository { ApplicationProvider.getApplicationContext<android.content.Context>().assets.open(it) }
        val sb = StringBuilder()
        fun line(page: String, rs: List<Run>) {
            val plain = rs.filterNot { it.fullForm }.joinToString("") { it.text }.replace('\t', ' ').replace('\n', ' ')
            var before = ""
            for (r in rs) {
                if (r.fullForm) {
                    val short = Regex("""[A-Za-z][A-Za-z0-9&.'-]*$""").find(before)?.value.orEmpty()
                    sb.append(page).append('\t').append(short).append('\t').append(r.text.trim().removePrefix("(").removeSuffix(")"))
                        .append('\t').append(plain).append('\n')
                } else before += r.text
            }
        }
        for (b in 1..6) for (r in repo.book(b).rows) for ((si, s) in r.secs.withIndex()) {
            val page = "$b:${r.index}:$si"
            for (bl in s.blocks) when (bl) {
                is TextBlock -> line(page, bl.runs)
                is TableBlock -> (listOf(bl.head) + bl.rows).forEach { row -> row.forEach { line(page, it) } }
            }
        }
        java.io.File(out).writeText(sb.toString())
    }
}
