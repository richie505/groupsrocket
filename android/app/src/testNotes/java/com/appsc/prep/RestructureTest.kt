package com.appsc.prep

import androidx.test.core.app.ApplicationProvider
import com.appsc.prep.data.Acronyms
import com.appsc.prep.data.IdMoves
import com.appsc.prep.data.ProgressStore
import com.appsc.prep.data.Repository
import com.appsc.prep.data.Run
import com.appsc.prep.data.Saved
import com.appsc.prep.data.SpeechText
import com.appsc.prep.data.Storage
import com.appsc.prep.data.TextBlock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Full forms added to short forms, and the reader's own notes under "Not in your sources" lines. */
@RunWith(RobolectricTestRunner::class)
class RestructureTest {
    private val repo = Repository { ApplicationProvider.getApplicationContext<android.content.Context>().assets.open(it) }

    private class MemStorage : Storage {
        val sets = HashMap<String, Set<String>>()
        val strings = HashMap<String, String>()
        override fun getStringSet(key: String) = sets[key].orEmpty()
        override fun getString(key: String) = strings[key]
        override fun getFloat(key: String, default: Float) = default
        override fun putStringSet(key: String, value: Set<String>) { sets[key] = value }
        override fun putString(key: String, value: String) { strings[key] = value }
        override fun putFloat(key: String, value: Float) {}
    }

    @Test fun shortFormsGetTheirFullForm() {
        repo.abbreviations
        repo.checkedAcronyms
        val blocks = Acronyms.annotate(
            listOf(
                TextBlock('b', listOf(Run("NCPCR monitors the RTE Act; NCPCR also hears complaints.", 0))),
                TextBlock('b', listOf(Run("Fiscal Deficit (FD) and SC reservation in seats; quota for SC students.", 0))),
            ),
            book = 2,
        )
        val first = (blocks[0] as TextBlock).runs
        val shown = first.joinToString("") { it.text }
        assertTrue(shown, shown.startsWith("NCPCR (National Commission for Protection of Child Rights) monitors"))
        assertEquals(1, Regex("""\(National Commission""").findAll(shown).count()) // first time only
        val second = (blocks[1] as TextBlock).runs.joinToString("") { it.text }
        assertFalse(second, second.contains("FD (")) // defined right there
        assertTrue(second, second.contains("SC (Scheduled Caste)"))
        // a meaning from another page shows only when this page is about it
        val jj = Acronyms.annotate(listOf(TextBlock('b', listOf(Run("JJB and CWC in every district.", 0)))), 2, "Juvenile Justice Act")
        assertFalse(jj.toString(), (jj[0] as TextBlock).runs.joinToString("") { it.text }.contains("Water"))
        val dam = Acronyms.annotate(listOf(TextBlock('b', listOf(Run("CWC monitors reservoir water levels.", 0)))), 4, "Dams")
        assertTrue((dam[0] as TextBlock).runs.joinToString("") { it.text }.contains("CWC (Central Water Commission)"))
        fun shown(text: String, book: Int) = (Acronyms.annotate(listOf(TextBlock('b', listOf(Run(text, 0)))), book)[0] as TextBlock).runs.joinToString("") { it.text }
        assertTrue(shown("The 101st CAA inserted Art. 246A.", 2).contains("CAA (Constitutional Amendment Act)"))
        assertTrue(shown("Protests against the CAA in 2019.", 2).contains("CAA (Citizenship Amendment Act)"))
        assertTrue(shown("ASI excavated the site in 1921.", 1).contains("(Archaeological Survey of India)"))
        assertTrue(shown("AP's RTGS dashboard tracks grievances.", 4).contains("(Real Time Governance Society)"))
        assertTrue(shown("Two CJIs retired in 2025.", 2).contains("(Chief Justices of India)"))
        // read-aloud says each NCPCR in full already: the added full form is not read again (2, not 3)
        val spoken = SpeechText.parts("t", blocks, 2).joinToString(" ") { it.second }
        assertEquals(spoken, 2, Regex("National Commission for Protection of Child Rights").findAll(spoken).count())
    }

    /** One checked list and the whole page decide short forms, on the page and in read-aloud alike. */
    @Test fun shortFormsByPage() {
        repo.abbreviations
        repo.checkedAcronyms
        fun spoken(text: String, book: Int, context: String = "") =
            SpeechText.parts("t", listOf(TextBlock('b', listOf(Run(text, 0)))), book, context).joinToString(" ") { it.second }
        fun says(text: String, book: Int, expect: String, context: String = "") {
            val out = spoken(text, book, context)
            assertTrue("$text -> $out", out.contains(expect))
        }
        // CWC: three meanings in the notes
        val jj = spoken("JJB and CWC in every district, each with at least one woman member.", 2, "Juvenile Justice Act 2015")
        assertTrue(jj, jj.contains("Child Welfare Committee") && !jj.contains("Water"))
        says("CWC clears dam and reservoir projects on inter-state rivers.", 4, "Central Water Commission")
        says("The CWC authorised Gandhi to launch civil disobedience.", 1, "Congress Working Committee")
        // others the old lists got wrong
        says("Lytton passed the VPA; Ripon repealed it.", 1, "Vernacular Press Act")
        says("GPS issued coins; his mother Balasri's Nasik inscription.", 1, "Gautamiputra Satakarni")
        says("The CWC and NCM were led from Wardha.", 1, "Non-Cooperation Movement")
        says("Members are elected by PR through the single transferable vote.", 2, "proportional representation")
        says("Article 51A lists the FDs.", 2, "Fundamental Duties")
        says("Fiscal targets cut the FD to 4.4% of GDP.", 3, "fiscal deficit")
        says("2.35 lakh MT rice per month.", 3, "metric tonnes")
        says("KWDT-II allotted 196 TMC to AP from the Krishna.", 4, "thousand million cubic feet")
        says("Mamata Banerjee's TMC won Bengal.", 2, "Trinamool Congress")
        says("The MPC (Art. 243ZE) prepares the draft plan.", 2, "Metropolitan Planning Committee")
        says("The MPC kept the repo rate at 5.5%.", 3, "Monetary Policy Committee")
        // names and labels stay as written
        // Roman numerals after Class / Part / Edict, ranges too
        says("children in Bal Vatika (pre-Class I) and Classes I-VIII in government schools", 2, "Classes 1 to 8")
        says("children in Bal Vatika (pre-Class I) and Classes I-VIII", 2, "pre-Class 1")
        says("Kalinga edicts replace RE XI-XIII there.", 1, "13")
        // hyphenated names: listed ones and ones built from their parts
        says("Introduced on 15 Aug 1995 as NP-NSPE.", 2, "National Programme of Nutritional Support to Primary Education")
        says("NFHS-5 urban TFR 1.6", 2, "National Family Health Survey 5")
        says("roads under PMGSY-IV in border villages", 2, "Pradhan Mantri Gram Sadak Yojana 4")
        says("the Union Cabinet extended PM-KISAN to 2030-31", 3, "Pradhan Mantri Kisan Samman Nidhi")
        says("ex-CJI B.R. Gavai told the JPC", 2, "ex Chief Justice of India")
        // found across all six books: kings, symbols, units, small comma numbers
        says("Fa-Hien visited during Chandragupta II (Gupta).", 1, "Chandragupta the Second")
        says("Pulumavi III (Satavahana); V. S. Sukthankar held that", 1, "V. S. Sukthankar")
        says("I-Tsing (Yijing); Chinese", 1, "I-Tsing")
        says("Books: *Gulamgiri* (Slavery, 1873)", 1, "Books: Gulamgiri (Slavery")
        says("Ganga 2,525 km > Godavari 1,465 km", 4, "greater than Godavari 1465 kilometres")
        says("Bihar 1,106, then West Bengal 1,028, Kerala 860", 2, "Bihar 1106, then West Bengal 1028")
        says("submersible carrying 3 crew to 6000 m in the ocean", 5, "6000 metres")
        says("primary 450 kcal + 12 g protein", 2, "450 kilocalories")
        says("PM2.5 annual 5 µg/m³", 5, "particulate matter 2.5 annual 5 micrograms per cubic metre")
        says("APPSC-G1 2017 key: seals", 1, "Group 1 2017")
        says("World War I ended in 1918", 1, "World War One")
        says("Type II diabetes", 5, "Type 2 diabetes")
        says("old credit set off up to 1/4 of liability", 3, "1 by 4")
        says("Swarna Andhra @2047 targets", 3, "Swarna Andhra at 2047")
        // Indian-style big numbers
        says("2025-26; outlay ₹1,30,794.90 crore (CDI).", 2, "1 lakh 30 thousand 794.90 crore rupees")
        says("Off-budget borrowings ₹1,18,394 crore (Mar 2022).", 3, "1 lakh 18 thousand 394 crore rupees")
        says("2,61,393 MSMEs set up in 2024-25", 3, "2 lakh 61 thousand 393")
        says("Total OFC 78,502 km", 3, "78502 kilometres")
        // numbers with M / lakh MT
        says("About 8 M were rural and 2 M urban (5.6 M boys).", 2, "2 million urban")
        // Article numbers and initials are not millions/billions
        fun saysNot(text: String, book: Int, bad: String) = spoken(text, book, "").let { assertTrue("$text -> $it", !it.contains(bad)) }
        saysNot("NCBC got constitutional status under Art. 338B (102nd Amdt).", 2, "billion")
        says("NCBC got constitutional status under Art. 338B (102nd Amdt).", 2, "102nd Amendment")
        saysNot("Part IXB covers co-operative societies; Art. 243M exempts some areas.", 2, "billion")
        saysNot("1927 M. A. Ansari presided.", 1, "million")
        says("The fund holds $2.5B in assets.", 3, "2.5 billion")
        says("Kesavananda Bharati v. State of Kerala (1973) set the basic structure.", 2, "Bharati versus State")
        says("Art. VI of the Outer Space Treaty fixes national responsibility.", 5, "Article 6")
        says("Table: Head (₹ cr), 2022-23 Actuals", 3, "in crore rupees")
        says("Outlay ₹1.97 lakh cr for 14 sectors.", 3, "1.97 lakh crore rupees")
        says("Blocked ITC refunds hit exporters under GST.", 3, "input tax credit")
        says("Hub Anganwadi Centres with ITC and Pratham.", 2, "ITC Limited")
        says("2.35 lakh MT rice per month.", 3, "2.35 lakh metric tonnes rice")
        says("Horticulture 367.72 MT overtook foodgrains.", 3, "367.72 million tonnes")
        // mixed case; spelled out elsewhere on the page is no reason to skip it here
        val mole = Acronyms.annotate(
            listOf(
                TextBlock('b', listOf(Run("Ministry of Labour and Employment, launched 2017.", 0))),
                TextBlock('b', listOf(Run("NCLP: run by MoLE since 1988; MoSJE and MeitY too; DCPUs in districts.", 0))),
            ),
            2, "PENCIL portal and National Child Labour Project",
        )
        val second2 = (mole[1] as TextBlock).runs.joinToString("") { it.text }
        assertTrue(second2, second2.contains("NCLP (National Child Labour Project)"))
        assertTrue(second2, second2.contains("MoLE (Ministry of Labour and Employment)"))
        assertTrue(second2, second2.contains("MoSJE (Ministry of Social Justice and Empowerment)"))
        assertTrue(second2, second2.contains("MeitY (Ministry of Electronics and Information Technology)"))
        assertTrue(second2, second2.contains("DCPUs (District Child Protection Units)"))
        says("Run by MoLE; NGOs and McDonald stayed.", 2, "Ministry of Labour and Employment")
        val first = Acronyms.annotate(listOf(TextBlock('b', listOf(Run("FIRST Telugu inscription", 0)))), 1)
        assertEquals("FIRST Telugu inscription", (first[0] as TextBlock).runs.joinToString("") { it.text })
    }

    /** "Not in your sources" lines: search text, the reader's own note under the line, kept and read aloud. */
    @Test fun ownNotesFillGaps() = runBlocking {
        val gap = TextBlock('b', listOf(Run("Not in your sources: top states for crimes against children, and NCRB child-marriage cases by state (a 2023 APPSC PYQ was based on NCRB data up to 2021).", 6)))
        assertEquals("top states for crimes against children, and NCRB child-marriage cases by state", com.appsc.prep.data.UserNotes.query(gap))
        val gap2 = TextBlock('b', listOf(Run("Not in your sources: Sanghabhuti's work and centre - check the AP History PYQ book.", 6)))
        assertEquals("Sanghabhuti's work and centre", com.appsc.prep.data.UserNotes.query(gap2))
        val mem = MemStorage()
        val store = ProgressStore(mem)
        val blocks = listOf(TextBlock('b', listOf(Run("Fact.", 0))), gap)
        store.setAdded(com.appsc.prep.data.UserNotes.key("2:138:8", 1), "UP had the most cases.\nMaharashtra second.")
        val again = ProgressStore(mem) // kept after the app restarts
        assertEquals("UP had the most cases.\nMaharashtra second.", again.added["2:138:8#1"])
        val shown = com.appsc.prep.data.UserNotes.withAdded(blocks, "2:138:8", again.added)
        assertEquals(3, shown.size)
        assertEquals(1, shown[2].first)
        val spoken = SpeechText.parts("t", shown.map { it.second }, 2).joinToString(" ") { it.second }
        assertTrue(spoken, spoken.contains("Maharashtra second"))
        store.setAdded("2:138:8#1", null)
        assertTrue(store.added.isEmpty())
    }
}
