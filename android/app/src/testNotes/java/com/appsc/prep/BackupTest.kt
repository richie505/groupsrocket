package com.appsc.prep

import com.appsc.prep.data.DayLog
import com.appsc.prep.data.IdMoves
import com.appsc.prep.data.NoteMoves
import com.appsc.prep.data.ProgressStore
import com.appsc.prep.data.Saved
import com.appsc.prep.data.SpeechText
import com.appsc.prep.data.Storage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Backup file and restore (Progress screen), and the read-aloud safety net. */
class BackupTest {
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

    private val moves = IdMoves("2.18", mapOf("2:5:9" to "2:5:3"), setOf("2:4:1"))

    @Test fun backupComesBackOnANewPhone() {
        val phone = ProgressStore(MemStorage())
        phone.migrate(moves)
        phone.setDone("1:0:0", true)
        phone.setDone("2:5:3", true)
        phone.toggleSaved(Saved("3:1:2", "GST", "Indirect taxes"))
        phone.recordAnswer("n1-5", true)
        phone.setAdded("2:7:1#4", "NCRB 2022: MP, Maharashtra and UP lead.\nSecond line.")
        val file = phone.backup()

        // a new phone that already has a little of its own
        val fresh = ProgressStore(MemStorage())
        fresh.migrate(moves)
        fresh.setDone("4:0:0", true)
        fresh.recordAnswer("n1-5", false)
        val r = fresh.restore(file, moves)

        assertEquals(setOf("1:0:0", "2:5:3", "4:0:0"), fresh.done) // nothing lost, nothing doubled
        assertEquals(listOf("3:1:2"), fresh.saved.map { it.id })
        assertEquals(true, fresh.answers["n1-5"]) // the backup's answer wins
        assertEquals("NCRB 2022: MP, Maharashtra and UP lead.\nSecond line.", fresh.added["2:7:1#4"])
        assertEquals(ProgressStore.Restored(read = 2, saved = 1, answers = 1, notes = 1), r)
        assertTrue(fresh.streak() >= 1)

        // restoring the same file again adds nothing new
        assertEquals(0, fresh.restore(file, moves).read)
    }

    @Test fun timeOnEachPageComesBackOnANewPhone() {
        val phone = ProgressStore(MemStorage())
        phone.addStudyTime(notes = true, seconds = 900, what = "n:2:77:0")
        phone.practising = "q:sub:2:77:0"
        phone.recordAnswer("n2-1", true)
        phone.recordAnswer("n2-2", false)
        phone.addStudyTime(notes = false, seconds = 300, what = "q:sub:2:77:0")
        assertEquals(com.appsc.prep.data.Spent(900, 0), phone.spentOn()["n:2:77:0"])
        assertEquals(com.appsc.prep.data.Spent(300, 2), phone.spentOn()["q:sub:2:77:0"])
        val file = phone.backup()

        val fresh = ProgressStore(MemStorage())
        fresh.addStudyTime(notes = true, seconds = 60, what = "n:2:77:0")
        fresh.restore(file, moves)
        assertEquals(com.appsc.prep.data.Spent(960, 0), fresh.spentOn()["n:2:77:0"]) // both phones' time
        fresh.restore(file, moves)
        assertEquals(com.appsc.prep.data.Spent(960, 0), fresh.spentOn()["n:2:77:0"]) // the same file twice counts once
    }

    @Test fun activityLogAddsUpAcrossDevices() {
        val phone = ProgressStore(MemStorage())
        phone.recordAnswer("n1-1", true)
        phone.recordAnswer("n1-2", false)
        phone.markSeen("n1-3")
        phone.addStudyTime(notes = true, seconds = 600)
        phone.addStudyTime(notes = false, seconds = 300)
        assertEquals(DayLog(mcqs = 3, correct = 1, notesSec = 600, mcqSec = 300), phone.dayLog())

        val desktop = ProgressStore(MemStorage())
        desktop.recordAnswer("n2-1", true)
        desktop.addStudyTime(notes = false, seconds = 120)
        desktop.restore(phone.backup(), moves)
        val both = DayLog(mcqs = 4, correct = 2, notesSec = 600, mcqSec = 420)
        assertEquals(both, desktop.dayLog())
        desktop.restore(phone.backup(), moves) // the same backup twice counts once
        assertEquals(both, desktop.dayLog())

        // back on the phone: the desktop's file carries the phone's own log too, which is not counted twice
        phone.restore(desktop.backup(), moves)
        assertEquals(both, phone.dayLog())
    }

    @Test fun ownNotesFollowRewrittenPages() {
        val round1 = NoteMoves(listOf("b2-1" to mapOf("2:5:0#8" to "2:5:0#3")))
        val both = NoteMoves(round1.rounds + ("b2-2" to mapOf("2:5:0#3" to "2:5:0#1")))
        val phone = ProgressStore(MemStorage())
        phone.setAdded("2:5:0#8", "found it")
        val oldFile = phone.backup() // made before any rewrite
        phone.migrateNotes(round1)
        assertEquals("found it", phone.added["2:5:0#3"])
        phone.migrateNotes(round1) // once only
        assertEquals(setOf("2:5:0#3"), phone.added.keys)
        phone.migrateNotes(both)
        assertEquals(setOf("2:5:0#1"), phone.added.keys)

        // an old backup's notes are moved through every round; a new one's only through the rounds it lacks
        val desktop = ProgressStore(MemStorage())
        desktop.migrateNotes(both)
        desktop.restore(oldFile, moves, both)
        assertEquals(setOf("2:5:0#1"), desktop.added.keys)
        val laptop = ProgressStore(MemStorage())
        laptop.migrateNotes(both)
        laptop.restore(phone.backup(), moves, both)
        assertEquals(setOf("2:5:0#1"), laptop.added.keys)
    }

    @Test fun oldBackupFollowsTheRegroupedPages() {
        // a backup from before the notes were regrouped (no ids version) uses the old page ids
        val old = """{"app":"Rocket Prep backup","format":1,"ids":"","done":["2:5:9","2:4:1"],"saved":["0\t2:5:9\tPOCSO\tChild rights"]}"""
        val store = ProgressStore(MemStorage())
        store.migrate(moves)
        store.restore(old, moves)
        assertEquals(setOf("2:5:3"), store.done) // moved; the merged page's mark is dropped
        assertEquals(listOf("2:5:3"), store.saved.map { it.id })
    }

    @Test fun otherFilesAreRefused() {
        val store = ProgressStore(MemStorage())
        for (bad in listOf("hello", "{}", """{"app":"Something else"}""")) {
            val e = runCatching { store.restore(bad, moves) }.exceptionOrNull()
            assertTrue(bad, e is IllegalArgumentException && e.message!!.contains("not a Rocket Prep backup"))
        }
        assertTrue(store.done.isEmpty())
    }

    @Test fun aFailingReadAloudRuleIsSkippedOnPhones() {
        val was = SpeechText.strict
        try {
            SpeechText.strict = false // as on a phone
            assertEquals("Chandragupta II", SpeechText.safely("Chandragupta II") { Regex("(?<=[a-z]{2,} )II").replace(it, ""); throw IllegalStateException("bad rule") })
            assertEquals("deep", SpeechText.safely("deep") { throw StackOverflowError() })
            SpeechText.strict = true // tests: a broken rule must fail
            assertTrue(runCatching { SpeechText.safely("x") { throw IllegalStateException() } }.isFailure)
        } finally {
            SpeechText.strict = was
        }
    }
}
