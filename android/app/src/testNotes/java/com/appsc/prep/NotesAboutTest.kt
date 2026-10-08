package com.appsc.prep

import androidx.test.core.app.ApplicationProvider
import com.appsc.prep.data.NotesTerms
import com.appsc.prep.data.Repository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotesAboutTest {
    private val repo = Repository { ApplicationProvider.getApplicationContext<android.content.Context>().assets.open(it) }

    @Test fun sameTermWrittenManyWays() {
        for (s in listOf("s.144", "sec144", "Sec. 144", "Section 144", "S 144")) assertEquals(s, "Section 144", NotesTerms.parse(s)!!.display)
        assertTrue(NotesTerms.parse("s.144")!!.regex.containsMatchIn("prohibitory orders under Sec 144 of the CrPC"))
        assertTrue(NotesTerms.parse("Section 144")!!.regex.containsMatchIn("(S.144)"))
        for (s in listOf("Art 21", "art. 21", "Article 21")) assertEquals("Article 21", NotesTerms.parse(s)!!.display)
        for (s in listOf("84th amendment", "84th Amendment Act", "Eighty-fourth Amendment", "84 amendment"))
            assertEquals(s, "84th Amendment", NotesTerms.parse(s)!!.display)
        assertTrue(NotesTerms.parse("84th amendment")!!.regex.containsMatchIn("The Eighty-fourth Amendment froze seats"))
        assertTrue(NotesTerms.parse("seventh schedule")!!.regex.containsMatchIn("listed in the 7th Schedule"))
        assertTrue(!NotesTerms.parse("Art 21")!!.regex.containsMatchIn("Art. 210"))
    }

    @Test fun notInYourSourcesIsNotAMeaning() {
        val (_, lines) = runBlocking { repo.notesAbout("Operation Sindoor") }!!
        assertTrue(lines.none { it.text.startsWith("Not in your sources") })
    }

    @Test fun whatTheNotesSay() {
        for (s in listOf("84th amendment", "Art. 370", "Dyarchy", "Polavaram", "repo rate", "7th schedule")) {
            val (title, lines) = runBlocking { repo.notesAbout(s) }!!
            println("ABOUT [$s] -> $title")
            lines.forEach { println("   - ${it.text}  |  ${it.where}") }
            assertTrue(s, lines.isNotEmpty())
        }
    }
}
