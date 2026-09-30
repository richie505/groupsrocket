package com.groupsrocket.mcq

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.time.LocalDate

/**
 * Practice history, persisted in SharedPreferences.
 *  - answers:   mcq id -> "1|sheetRef" (last attempt correct) or "0|sheetRef"
 *  - best:      sheetRef -> best score % from a full-sheet practice
 *  - bookmarks: mcq id -> sheetRef
 */
class Progress(context: Context) {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    private val answers = load("answers")
    private val best = load("best")
    private val bookmarks = load("bookmarks")

    /** Bumped on every change so Compose screens re-read. */
    var version by mutableIntStateOf(0)
        private set

    private fun load(key: String): JSONObject = JSONObject(prefs.getString(key, "{}") ?: "{}")

    private fun save() {
        prefs.edit()
            .putString("answers", answers.toString())
            .putString("best", best.toString())
            .putString("bookmarks", bookmarks.toString())
            .apply()
        version++
    }

    fun record(mcq: Mcq, correct: Boolean) {
        answers.put(mcq.id, "${if (correct) 1 else 0}|${mcq.sheet}")
        save()
    }

    fun recordSheetScore(ref: SheetRef, percent: Int) {
        if (percent > best.optInt(ref, -1)) {
            best.put(ref, percent)
            save()
        }
    }

    fun bestScore(ref: SheetRef): Int? = if (best.has(ref)) best.getInt(ref) else null

    fun isBookmarked(id: String) = bookmarks.has(id)

    fun toggleBookmark(mcq: Mcq) {
        if (bookmarks.has(mcq.id)) bookmarks.remove(mcq.id) else bookmarks.put(mcq.id, mcq.sheet)
        save()
    }

    data class Tally(val attempted: Int, val correct: Int)

    private var tallyVersion = -1
    private var tallies: Map<SheetRef, Tally> = emptyMap()

    /** Attempted / correct question counts per sheet, from the latest attempt of each question. */
    fun tallyBySheet(): Map<SheetRef, Tally> {
        if (tallyVersion != version) {
            val acc = HashMap<SheetRef, IntArray>()
            for (id in answers.keys()) {
                val v = answers.getString(id)
                val t = acc.getOrPut(v.substringAfter("|")) { IntArray(2) }
                t[0]++
                if (v.startsWith("1|")) t[1]++
            }
            tallies = acc.mapValues { Tally(it.value[0], it.value[1]) }
            tallyVersion = version
        }
        return tallies
    }

    fun tally(refs: Collection<SheetRef>): Tally {
        val all = tallyBySheet()
        return refs.mapNotNull { all[it] }.fold(Tally(0, 0)) { a, b -> Tally(a.attempted + b.attempted, a.correct + b.correct) }
    }

    private fun idsWhere(src: JSONObject, pred: (String) -> Boolean): Map<String, SheetRef> =
        src.keys().asSequence().filter { pred(src.getString(it)) }
            .associateWith { src.getString(it).substringAfter("|") }

    /** Ids of questions whose latest attempt was wrong, with their sheet. */
    fun wrongIds(): Map<String, SheetRef> = idsWhere(answers) { it.startsWith("0|") }

    fun bookmarkIds(): Map<String, SheetRef> =
        bookmarks.keys().asSequence().associateWith { bookmarks.getString(it) }

    fun overall(): Tally = tally(tallyBySheet().keys)

    // ---- 90-day plan ----

    fun planStart(): LocalDate? = prefs.getString("planStart", null)?.let(LocalDate::parse)

    fun setPlanStart(date: LocalDate) {
        prefs.edit().putString("planStart", date.toString()).apply()
        version++
    }

    fun isDayDone(day: Int) = prefs.getBoolean("dayDone$day", false)

    fun markDayDone(day: Int) {
        prefs.edit().putBoolean("dayDone$day", true).apply()
        version++
    }

    fun resetAll() {
        prefs.edit().clear().apply()
        listOf(answers, best, bookmarks).forEach { o -> o.keys().asSequence().toList().forEach(o::remove) }
        version++
    }
}
