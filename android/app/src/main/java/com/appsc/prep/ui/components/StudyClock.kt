package com.appsc.prep.ui.components

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.delay

/** What the reader is doing on a route, for the study-time log: notes, MCQs, or neither (null). */
fun studyKind(route: String?): Boolean? = when {
    route == null -> null
    route.startsWith("read/") -> true
    route.startsWith("quiz/") -> false
    else -> null
}

object StudyClock {
    /** Off in screenshot tests, where a clock that never stops would keep the UI from going idle. */
    @Volatile var on = System.getProperty("prep.strict") != "true"
}

/** No touch, click, scroll or mouse move for this long and the time stops counting (the app was left open). */
const val IDLE_MS = 5 * 60_000L

/**
 * Records study time, on the notes or on MCQs ([studyKind] of the current route), while the app is in front and
 * used. Listening to read-aloud counts as notes time, even with the screen off. Only records; there is no target.
 */
fun Modifier.studyClock(route: () -> String?, what: () -> String? = { null }): Modifier = if (!StudyClock.on) this else composed {
    val app = LocalApp.current
    val window = LocalWindowInfo.current
    val currentRoute = rememberUpdatedState(route)
    val currentWhat = rememberUpdatedState(what)
    val lastUse = remember { longArrayOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        // seconds per (notes?, what) since the last flush: "what" is the page or practice ([com.appsc.prep.data.Spent])
        val counted = HashMap<Pair<Boolean, String?>, Int>()
        fun flush() {
            for ((k, sec) in counted) app.store.addStudyTime(notes = k.first, seconds = sec, what = k.second)
            counted.clear()
        }
        try {
            while (true) {
                delay(1000)
                val kind = studyKind(currentRoute.value())
                val here = currentWhat.value()
                app.store.practising = here?.takeIf { kind == false }
                val playback = app.platform.speech?.playback?.value
                val listening = playback?.playing == true
                val inUse = window.isWindowFocused && System.currentTimeMillis() - lastUse[0] < IDLE_MS
                val key = when {
                    kind == false && inUse -> false to here
                    // read-aloud counts for the page being read, wherever the app is
                    listening && kind != false -> true to "n:${playback!!.pageId}"
                    kind == true && inUse -> true to here
                    else -> null
                }
                if (key != null) counted[key] = (counted[key] ?: 0) + 1
                if (counted.values.sum() >= 30) flush()
            }
        } finally {
            flush()
        }
    }
    onPreviewKeyEvent {
        lastUse[0] = System.currentTimeMillis()
        false
    }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                lastUse[0] = System.currentTimeMillis()
            }
        }
    }
}

/** "2h 05m", "40m", "0m". */
fun studyTime(seconds: Int): String {
    val m = seconds / 60
    return if (m < 60) "${m}m" else "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m"
}
