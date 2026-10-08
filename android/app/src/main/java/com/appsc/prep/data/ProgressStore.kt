package com.appsc.prep.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** A saved subsection: id is "book:row:sec". */
data class Saved(val id: String, val title: String, val rowTitle: String)

/** Read/done state, bookmarks, last position and reader settings, kept in [Storage]. */
class ProgressStore(private val prefs: Storage) {

    var done by mutableStateOf(prefs.getStringSet(KEY_DONE).toSet())
        private set
    var saved by mutableStateOf(decodeSaved(prefs.getStringSet(KEY_SAVED)))
        private set
    var activeDates by mutableStateOf(prefs.getStringSet(KEY_DATES).toSet())
        private set
    var lastRead by mutableStateOf(prefs.getString(KEY_LAST))
        private set
    /** MCQ answers: question id -> answered correctly (latest attempt). */
    var answers by mutableStateOf(
        prefs.getStringSet(KEY_ANSWERS).associate { it.substringBefore(':') to it.endsWith(":1") },
    )
        private set
    /** Unscored questions (no key / cancelled) that have been attempted. */
    var seen by mutableStateOf(prefs.getStringSet(KEY_SEEN).toSet())
        private set
    var textScale by mutableFloatStateOf(prefs.getFloat(KEY_SCALE, 1f))
        private set
    /** Read-aloud speed (1 = normal). */
    var speechRate by mutableFloatStateOf(prefs.getFloat(KEY_RATE, 1f))
        private set

    /**
     * Moves read marks, bookmarks and the last position to the new subsection ids once per notes version
     * ([Repository.idMoves]). A merged page's read mark is dropped: the page it went into keeps its own mark.
     */
    fun migrate(moves: IdMoves) {
        if (moves.version.isEmpty() || prefs.getString(KEY_IDS) == moves.version) return
        val map = moves.map
        if (map.isNotEmpty()) {
            done = done.filter { it !in moves.merged }.map { map[it] ?: it }.toSet()
            prefs.putStringSet(KEY_DONE, done)
            saved = saved.map { s -> map[s.id]?.let { s.copy(id = it) } ?: s }.distinctBy { it.id }
            prefs.putStringSet(KEY_SAVED, saved.mapIndexed { i, s -> "$i\t${s.id}\t${s.title}\t${s.rowTitle}" }.toSet())
            lastRead?.let { l -> map[l]?.let { rememberPosition(it) } }
        }
        prefs.putString(KEY_IDS, moves.version)
    }

    fun changeSpeechRate(rate: Float) {
        speechRate = rate
        prefs.putFloat(KEY_RATE, rate)
    }

    /** What the reader added under "Not in your sources" lines ([UserNotes]): "book:row:sec#block" -> text. */
    var added by mutableStateOf(
        prefs.getStringSet(KEY_ADDED).associate { it.substringBefore('\t') to it.substringAfter('\t').replace("\\n", "\n") },
    )
        private set

    fun setAdded(key: String, text: String?) {
        added = if (text.isNullOrBlank()) added - key else added + (key to text.trim())
        prefs.putStringSet(KEY_ADDED, added.map { (k, v) -> "$k\t${v.replace("\n", "\\n")}" }.toSet())
    }

    fun isDone(id: String) = id in done

    fun setDone(id: String, value: Boolean) {
        if (value == (id in done)) return
        done = if (value) done + id else done - id
        prefs.putStringSet(KEY_DONE, done)
        if (value) markActive()
    }

    fun toggleDone(id: String) = setDone(id, id !in done)

    fun doneCount(book: Int, row: Int, total: Int): Int =
        (0 until total).count { subsectionId(book, row, it) in done }

    fun recordAnswer(questionId: String, correct: Boolean) {
        answers = answers + (questionId to correct)
        prefs.putStringSet(KEY_ANSWERS, answers.map { (k, v) -> "$k:${if (v) 1 else 0}" }.toSet())
        markActive()
    }

    fun markSeen(questionId: String) {
        if (questionId in seen) return
        seen = seen + questionId
        prefs.putStringSet(KEY_SEEN, seen)
        markActive()
    }

    fun attempted(questionId: String) = questionId in answers || questionId in seen

    /** (attempted, correct) among the given questions; unscored ones count as attempted only. */
    fun quizStats(ids: List<String>): Pair<Int, Int> {
        var attempted = 0
        var correct = 0
        for (id in ids) {
            if (id in seen) attempted++
            val a = answers[id] ?: continue
            attempted++
            if (a) correct++
        }
        return attempted to correct
    }

    fun isSaved(id: String) = saved.any { it.id == id }

    fun toggleSaved(item: Saved) {
        saved = if (isSaved(item.id)) saved.filterNot { it.id == item.id } else listOf(item) + saved
        prefs.putStringSet(KEY_SAVED, saved.mapIndexed { i, s -> "$i\t${s.id}\t${s.title}\t${s.rowTitle}" }.toSet())
    }

    fun rememberPosition(id: String) {
        lastRead = id
        prefs.putString(KEY_LAST, id)
    }

    fun changeTextScale(delta: Float) {
        textScale = (textScale + delta).coerceIn(0.85f, 1.45f)
        prefs.putFloat(KEY_SCALE, textScale)
    }

    /** Consecutive days (ending today or yesterday) on which something was marked done. */
    fun streak(today: LocalDate = LocalDate.now()): Int {
        var d = if (today.toString() in activeDates) today else today.minusDays(1)
        var n = 0
        while (d.toString() in activeDates) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    private fun markActive() {
        val t = LocalDate.now().toString()
        if (t !in activeDates) {
            activeDates = activeDates + t
            prefs.putStringSet(KEY_DATES, activeDates)
        }
    }

    /**
     * Everything kept for the reader, as one JSON file to save outside the app: read marks, bookmarks, MCQ answers,
     * study days, own notes, last page and reader settings.
     */
    fun backup(): String = kotlinx.serialization.json.buildJsonObject {
        fun set(key: String, v: Collection<String>) = put(key, kotlinx.serialization.json.JsonArray(v.sorted().map { kotlinx.serialization.json.JsonPrimitive(it) }))
        put("app", kotlinx.serialization.json.JsonPrimitive(BACKUP_APP))
        put("format", kotlinx.serialization.json.JsonPrimitive(1))
        put("ids", kotlinx.serialization.json.JsonPrimitive(prefs.getString(KEY_IDS) ?: ""))
        set("done", done)
        set("saved", saved.mapIndexed { i, x -> "$i\t${x.id}\t${x.title}\t${x.rowTitle}" })
        set("dates", activeDates)
        set("answers", answers.map { (k, v) -> "$k:${if (v) 1 else 0}" })
        set("seen", seen)
        set("added", added.map { (k, v) -> "$k\t${v.replace("\n", "\\n")}" })
        lastRead?.let { put("last", kotlinx.serialization.json.JsonPrimitive(it)) }
        put("scale", kotlinx.serialization.json.JsonPrimitive(textScale))
        put("rate", kotlinx.serialization.json.JsonPrimitive(speechRate))
    }.toString()

    /** Day of the last backup ("2026-10-04"), or null. */
    var lastBackup by mutableStateOf(prefs.getString(KEY_BACKUP))
        private set

    fun backedUp(today: LocalDate = LocalDate.now()) {
        lastBackup = today.toString()
        prefs.putString(KEY_BACKUP, today.toString())
    }

    /** What a restore brought in. */
    data class Restored(val read: Int, val saved: Int, val answers: Int, val notes: Int)

    /**
     * Adds a [backup] to what is on this device: nothing here is lost, and where both have the same item (an MCQ
     * answer, an own note) the backup's wins. Ids from an older notes version are moved with [moves] first.
     * Throws [IllegalArgumentException] when [text] is not a backup of this app.
     */
    fun restore(text: String, moves: IdMoves): Restored {
        val o = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject }.getOrNull()
        require(o != null && (o["app"] as? kotlinx.serialization.json.JsonPrimitive)?.content == BACKUP_APP) { "This file is not a Rocket Prep backup." }
        fun list(key: String): List<String> =
            (o[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList()
        fun str(key: String) = (o[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
        // a backup made before the notes were regrouped uses the old page ids
        val old = str("ids") != moves.version && moves.map.isNotEmpty()
        fun id(x: String) = if (old) moves.map[x] ?: x else x

        val newDone = list("done").filter { !(old && it in moves.merged) }.map(::id).toSet() - done
        done = done + newDone
        prefs.putStringSet(KEY_DONE, done)

        val incoming = decodeSaved(list("saved").toSet()).map { it.copy(id = id(it.id)) }.filter { s -> saved.none { it.id == s.id } }
        saved = saved + incoming
        prefs.putStringSet(KEY_SAVED, saved.mapIndexed { i, x -> "$i\t${x.id}\t${x.title}\t${x.rowTitle}" }.toSet())

        activeDates = activeDates + list("dates")
        prefs.putStringSet(KEY_DATES, activeDates)

        val inAnswers = list("answers").filter { ':' in it }.associate { it.substringBefore(':') to it.endsWith(":1") }
        answers = answers + inAnswers
        prefs.putStringSet(KEY_ANSWERS, answers.map { (k, v) -> "$k:${if (v) 1 else 0}" }.toSet())
        seen = seen + list("seen")
        prefs.putStringSet(KEY_SEEN, seen)

        val inNotes = list("added").filter { '\t' in it }.associate { it.substringBefore('\t') to it.substringAfter('\t').replace("\\n", "\n") }
        added = added + inNotes
        prefs.putStringSet(KEY_ADDED, added.map { (k, v) -> "$k\t${v.replace("\n", "\\n")}" }.toSet())

        if (lastRead == null) str("last")?.let { rememberPosition(id(it)) }
        return Restored(newDone.size, incoming.size, inAnswers.size, inNotes.size)
    }

    private fun decodeSaved(raw: Set<String>): List<Saved> =
        raw.mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 4) null else (p[0].toIntOrNull() ?: 0) to Saved(p[1], p[2], p[3])
        }.sortedBy { it.first }.map { it.second }

    private companion object {
        const val KEY_DONE = "done"
        const val KEY_SAVED = "saved"
        const val KEY_DATES = "dates"
        const val KEY_LAST = "last"
        const val KEY_SCALE = "scale"
        const val KEY_RATE = "speech_rate"
        const val KEY_ANSWERS = "answers"
        const val KEY_SEEN = "seen"
        const val KEY_IDS = "ids_version"
        const val KEY_ADDED = "added_notes"
        const val BACKUP_APP = "Rocket Prep backup"
        const val KEY_BACKUP = "last_backup"
    }
}
