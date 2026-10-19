package com.appsc.prep.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** A saved subsection: id is "book:row:sec". */
data class Saved(val id: String, val title: String, val rowTitle: String)

/** One day's study record: MCQs practised (and answered right), seconds spent on notes and on MCQs. */
data class DayLog(val mcqs: Int = 0, val correct: Int = 0, val notesSec: Int = 0, val mcqSec: Int = 0) {
    val totalSec get() = notesSec + mcqSec
    val empty get() = mcqs == 0 && totalSec == 0

    operator fun plus(o: DayLog) = DayLog(mcqs + o.mcqs, correct + o.correct, notesSec + o.notesSec, mcqSec + o.mcqSec)

    /** Each field's larger value: the same device's log seen twice (an older and a newer backup) counts once. */
    fun latest(o: DayLog) = DayLog(maxOf(mcqs, o.mcqs), maxOf(correct, o.correct), maxOf(notesSec, o.notesSec), maxOf(mcqSec, o.mcqSec))

    fun encode() = "$mcqs\t$correct\t$notesSec\t$mcqSec"

    companion object {
        fun decode(p: List<String>): DayLog? {
            val n = p.map { it.toIntOrNull() ?: return null }
            return if (n.size < 4) null else DayLog(n[0], n[1], n[2], n[3])
        }
    }
}

/**
 * Time (and MCQs) on one thing in a day. [key]: "n:book:row:sec" a notes page (read or listened to),
 * "q:kind:book:index:sub" an MCQ practice ([com.appsc.prep.ui.screens.QuizSource]).
 */
data class Spent(val sec: Int = 0, val mcqs: Int = 0) {
    operator fun plus(o: Spent) = Spent(sec + o.sec, mcqs + o.mcqs)
    fun latest(o: Spent) = Spent(maxOf(sec, o.sec), maxOf(mcqs, o.mcqs))
}

/** Wrong on the latest attempt, or its answer was seen before picking and not answered on its own since. */
fun needsPractice(id: String, answers: Map<String, Boolean>, seen: Set<String>) =
    answers[id] == false || (id in seen && id !in answers)

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
    /** Questions ever answered wrong (kept after a later right answer: "right on a retry"). Before 2.59 only the latest wrong ones. */
    var missed by mutableStateOf(prefs.getStringSet(KEY_MISSED).toSet() + answers.filterValues { !it }.keys)
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

    /** Rounds of [NoteMoves] already applied to [added]. */
    private var noteRounds = prefs.getStringSet(KEY_NOTE_ROUNDS).toSet()

    /** Keeps the reader's own notes under their gap lines after pages were rewritten ([NoteMoves]). */
    fun migrateNotes(moves: NoteMoves) {
        if (noteRounds.containsAll(moves.ids)) return
        if (added.isNotEmpty()) {
            added = moves.apply(added, noteRounds)
            prefs.putStringSet(KEY_ADDED, added.map { (k, v) -> "$k\t${v.replace("\n", "\\n")}" }.toSet())
        }
        noteRounds = noteRounds + moves.ids
        prefs.putStringSet(KEY_NOTE_ROUNDS, noteRounds)
    }

    fun changeSpeechRate(rate: Float) {
        speechRate = rate
        prefs.putFloat(KEY_RATE, rate)
    }

    /** Read-aloud stops at the end of the subsection instead of going on through the book. */
    var speechThisPageOnly by mutableStateOf(prefs.getString(KEY_SPEECH_SCOPE) == "page")
        private set

    fun changeSpeechThisPageOnly(only: Boolean) {
        speechThisPageOnly = only
        prefs.putString(KEY_SPEECH_SCOPE, if (only) "page" else "book")
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
        logToday { it.copy(mcqs = it.mcqs + 1, correct = it.correct + if (correct) 1 else 0) }
        practising?.let { spendToday(it, Spent(mcqs = 1)) }
        answers = answers + (questionId to correct)
        prefs.putStringSet(KEY_ANSWERS, answers.map { (k, v) -> "$k:${if (v) 1 else 0}" }.toSet())
        if (!correct && questionId !in missed) {
            missed = missed + questionId
            prefs.putStringSet(KEY_MISSED, missed)
        }
        markActive()
    }

    fun markSeen(questionId: String) {
        logToday { it.copy(mcqs = it.mcqs + 1) }
        practising?.let { spendToday(it, Spent(mcqs = 1)) }
        if (questionId in seen) return
        seen = seen + questionId
        prefs.putStringSet(KEY_SEEN, seen)
        markActive()
    }

    fun attempted(questionId: String) = questionId in answers || questionId in seen

    fun needsPractice(questionId: String) = needsPractice(questionId, answers, seen)

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

    /** This device's id, telling its activity log apart from other devices' (brought in by a restore). */
    private val device: String = prefs.getString(KEY_DEVICE) ?: java.util.UUID.randomUUID().toString().take(8).also {
        prefs.putString(KEY_DEVICE, it)
    }

    /** Day ("2026-10-11") -> what was done on this device. */
    private var ownLog by mutableStateOf(
        prefs.getStringSet(KEY_LOG).mapNotNull { l -> l.split('\t').let { p -> DayLog.decode(p.drop(1))?.let { p[0] to it } } }.toMap(),
    )

    /** Device -> day -> log, from other devices' backups. */
    private var otherLogs by mutableStateOf(decodeOthers(prefs.getStringSet(KEY_LOGS)))

    /** Day ("2026-10-11") -> MCQs practised and time spent, on this device and the restored ones together. */
    val activity: Map<String, DayLog>
        get() {
            val all = ownLog.toMutableMap()
            for (log in otherLogs.values) for ((d, l) in log) all[d] = (all[d] ?: DayLog()) + l
            return all
        }

    fun dayLog(day: LocalDate = LocalDate.now()) = activity[day.toString()] ?: DayLog()

    /** Adds [seconds] of study today, on the notes or on MCQs; [what] is the page or practice ([Spent]). */
    fun addStudyTime(notes: Boolean, seconds: Int, what: String? = null) {
        if (seconds <= 0) return
        logToday { if (notes) it.copy(notesSec = it.notesSec + seconds) else it.copy(mcqSec = it.mcqSec + seconds) }
        if (what != null) spendToday(what, Spent(sec = seconds))
    }

    /** The MCQ practice on screen ("q:..."), so answered MCQs are counted under it. Set by the study clock. */
    var practising: String? = null

    /** Day -> what -> time and MCQs, on this device. */
    private var ownSpent by mutableStateOf(decodeSpent(prefs.getStringSet(KEY_SPENT)))

    /** Device -> day -> what -> time and MCQs, from other devices' backups. */
    private var otherSpent by mutableStateOf(
        prefs.getStringSet(KEY_SPENT_OTHER).mapNotNull { l -> l.split('\t').takeIf { it.size >= 5 }?.let { it[0] to it.drop(1).joinToString("\t") } }
            .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> decodeSpent(v) },
    )

    /** What the time of a day went on (notes pages and MCQ practices), on all devices together. */
    fun spentOn(day: LocalDate = LocalDate.now()): Map<String, Spent> {
        val d = day.toString()
        val all = (ownSpent[d] ?: emptyMap()).toMutableMap()
        for (dev in otherSpent.values) for ((k, v) in dev[d] ?: emptyMap()) all[k] = (all[k] ?: Spent()) + v
        return all
    }

    private fun spendToday(what: String, add: Spent) {
        val t = LocalDate.now().toString()
        val day = ownSpent[t] ?: emptyMap()
        ownSpent = ownSpent + (t to day + (what to (day[what] ?: Spent()) + add))
        prefs.putStringSet(KEY_SPENT, encodeSpent(ownSpent).toSet())
    }

    private fun encodeSpent(m: Map<String, Map<String, Spent>>) =
        m.flatMap { (d, w) -> w.map { (k, v) -> "$d\t$k\t${v.sec}\t${v.mcqs}" } }

    private fun decodeSpent(raw: Collection<String>): Map<String, Map<String, Spent>> =
        raw.mapNotNull { l ->
            val p = l.split('\t')
            if (p.size < 4) null else Triple(p[0], p[1], Spent(p[2].toIntOrNull() ?: return@mapNotNull null, p[3].toIntOrNull() ?: 0))
        }.groupBy { it.first }.mapValues { (_, v) -> v.associate { it.second to it.third } }

    private fun logToday(change: (DayLog) -> DayLog) {
        val t = LocalDate.now().toString()
        ownLog = ownLog + (t to change(ownLog[t] ?: DayLog()))
        prefs.putStringSet(KEY_LOG, ownLog.map { (d, l) -> "$d\t${l.encode()}" }.toSet())
    }

    private fun encodeOthers() = otherLogs.flatMap { (dev, log) -> log.map { (d, l) -> "$dev\t$d\t${l.encode()}" } }

    private fun decodeOthers(raw: Collection<String>): Map<String, Map<String, DayLog>> =
        raw.mapNotNull { l -> l.split('\t').let { p -> if (p.size < 6) null else DayLog.decode(p.drop(2))?.let { Triple(p[0], p[1], it) } } }
            .groupBy { it.first }.mapValues { (_, v) -> v.associate { it.second to it.third } }

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
        set("missed", missed)
        set("noteRounds", noteRounds)
        set("added", added.map { (k, v) -> "$k\t${v.replace("\n", "\\n")}" })
        lastRead?.let { put("last", kotlinx.serialization.json.JsonPrimitive(it)) }
        put("device", kotlinx.serialization.json.JsonPrimitive(device))
        set("log", ownLog.map { (d, l) -> "$device\t$d\t${l.encode()}" } + encodeOthers())
        set("spent", encodeSpent(ownSpent).map { "$device\t$it" } + otherSpent.flatMap { (dev, m) -> encodeSpent(m).map { "$dev\t$it" } })
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
    fun restore(text: String, moves: IdMoves, noteMoves: NoteMoves = NoteMoves(emptyList())): Restored {
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
        missed = missed + list("missed") + inAnswers.filterValues { !it }.keys
        prefs.putStringSet(KEY_MISSED, missed)

        // notes saved before pages were rewritten sit under other gap indices: move them as this device's were
        val inNotes = noteMoves.apply(
            list("added").filter { '\t' in it }.associate { it.substringBefore('\t') to it.substringAfter('\t').replace("\\n", "\n") },
            list("noteRounds"),
        )
        added = added + inNotes
        prefs.putStringSet(KEY_ADDED, added.map { (k, v) -> "$k\t${v.replace("\n", "\\n")}" }.toSet())

        // other devices' activity logs; this device's own log here is always the newest
        val inLogs = decodeOthers(list("log")).filterKeys { it != device }
        if (inLogs.isNotEmpty()) {
            otherLogs = (otherLogs.keys + inLogs.keys).associateWith { dev ->
                val a = otherLogs[dev] ?: emptyMap()
                val b = inLogs[dev] ?: emptyMap()
                (a.keys + b.keys).associateWith { d -> (a[d] ?: DayLog()).latest(b[d] ?: DayLog()) }
            }
            prefs.putStringSet(KEY_LOGS, encodeOthers().toSet())
        }

        val inSpent = list("spent").mapNotNull { l -> l.split('\t').takeIf { it.size >= 5 }?.let { it[0] to it.drop(1).joinToString("\t") } }
            .filter { it.first != device }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> decodeSpent(v) }
        if (inSpent.isNotEmpty()) {
            otherSpent = (otherSpent.keys + inSpent.keys).associateWith { dev ->
                val a = otherSpent[dev] ?: emptyMap()
                val b = inSpent[dev] ?: emptyMap()
                (a.keys + b.keys).associateWith { d ->
                    val x = a[d] ?: emptyMap()
                    val y = b[d] ?: emptyMap()
                    (x.keys + y.keys).associateWith { k -> (x[k] ?: Spent()).latest(y[k] ?: Spent()) }
                }
            }
            prefs.putStringSet(KEY_SPENT_OTHER, otherSpent.flatMap { (dev, m) -> encodeSpent(m).map { "$dev\t$it" } }.toSet())
        }

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
        const val KEY_SPEECH_SCOPE = "speech_scope"
        const val KEY_ANSWERS = "answers"
        const val KEY_SEEN = "seen"
        const val KEY_MISSED = "missed"
        const val KEY_IDS = "ids_version"
        const val KEY_ADDED = "added_notes"
        const val BACKUP_APP = "Rocket Prep backup"
        const val KEY_BACKUP = "last_backup"
        const val KEY_DEVICE = "device_id"
        const val KEY_NOTE_ROUNDS = "note_rounds"
        const val KEY_LOG = "activity_log"
        const val KEY_LOGS = "activity_other"
        const val KEY_SPENT = "spent_on"
        const val KEY_SPENT_OTHER = "spent_on_other"
    }
}
