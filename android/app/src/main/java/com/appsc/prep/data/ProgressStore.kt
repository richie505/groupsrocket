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

    fun changeSpeechRate(rate: Float) {
        speechRate = rate
        prefs.putFloat(KEY_RATE, rate)
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
    }
}
