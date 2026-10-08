package com.appsc.prep

import androidx.test.core.app.ApplicationProvider
import com.appsc.prep.data.Repository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DictionaryTest {
    private val repo = Repository { ApplicationProvider.getApplicationContext<android.content.Context>().assets.open(it) }
    private val dict get() = repo.dictionary

    @Test fun wordsFromTheNotes() {
        val e = dict.lookup("dyarchy")!!
        assertEquals("dyarchy", e.word)
        assertTrue(e.senses.first().definition.contains("two joint rulers"))
        assertTrue(dict.lookup("Federalism")!!.senses.first().definition.contains("federal"))
        assertTrue(dict.lookup(" sovereign, ")!!.senses.any { it.definition.contains("not controlled by outside forces") })
    }

    @Test fun otherFormsFindTheDictionaryWord() {
        assertEquals("government", dict.lookup("governments")!!.word)
        assertEquals("abolish", dict.lookup("abolished")!!.word)
        assertEquals("policy", dict.lookup("policies")!!.word)
        assertEquals("migrate", dict.lookup("migrating")!!.word)
        assertEquals("disintegration", dict.lookup("disintegration.")!!.word)
    }

    @Test fun indianContextFirst() {
        val f = dict.lookup("federal")!!
        assertTrue(f.india!!.contains("Union of States"))
        // no US-only meanings
        assertTrue(f.senses.none { "United States" in it.definition || "Civil War" in it.definition })
        assertNull(dict.lookup("Washington")?.senses?.firstOrNull { "United States" in it.definition })
        assertTrue(dict.lookup("secular")!!.india!!.contains("sarva dharma sambhava"))
        // the notes' own definition
        val h = dict.lookup("absolute humidity")!!
        assertTrue(h.notes.first().text.contains("water vapour"))
    }

    @Test fun scienceAndCurrentAffairsInIndianContext() {
        for (t in listOf("PSLV", "Chandrayaan", "Sriharikota", "green hydrogen", "Ramsar", "El Nino", "net zero", "G20", "BRICS", "UPI", "Quad"))
            assertTrue(t, dict.lookup(t)?.india != null)
        assertTrue(dict.lookup("Ramsar")!!.india!!.contains("Kolleru"))
    }

    @Test fun everyPageHasKeyTerms() {
        val pages = repo.keyTerms
        // most ROCKET pages (scripts/build_key_terms.py)
        assertTrue("${pages.size}", pages.size > 1200)
        assertTrue(pages["2:0:0"]!!.contains("dyarchy"))
    }

    @Test fun shortFormsFromTheNotes() {
        assertEquals("World Trade Organization", dict.lookup("WTO")!!.shortForm)
        assertEquals("Visakhapatnam-Chennai Industrial Corridor", dict.lookup("VCIC")!!.shortForm)
        assertNull(dict.lookup("qwxzv"))
    }

    @Test fun notesHeadingsMentioningAWord() {
        val hits = runBlocking { repo.findInNotes("Fundamental Rights") }
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { "fundamental rights" in it.title.lowercase() })
    }
}
