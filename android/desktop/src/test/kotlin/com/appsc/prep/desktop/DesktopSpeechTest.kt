package com.appsc.prep.desktop

import com.appsc.prep.ui.components.SpeechPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/** Windows read-aloud: paragraphs in order, on to the next page, pause / resume, speed, stop. */
class DesktopSpeechTest {
    /** A voice that "speaks" a paragraph when the test lets it ([step]). */
    private class FakeSpeaker : Speaker {
        val said = CopyOnWriteArrayList<String>()
        val rates = CopyOnWriteArrayList<Float>()
        private val go = Semaphore(0)
        private val turn = java.util.concurrent.atomic.AtomicInteger() // each cancel ends the paragraph being said
        override fun speak(text: String, rate: Float): Boolean {
            val mine = turn.get()
            rates += rate
            while (true) {
                if (turn.get() != mine) return false
                if (go.tryAcquire(10, TimeUnit.MILLISECONDS)) {
                    if (turn.get() != mine) { go.release(); return false } // the step was meant for the next paragraph
                    said += text
                    return true
                }
            }
        }
        override fun cancel() { turn.incrementAndGet() }
        fun step(n: Int = 1) = go.release(n)
    }

    private fun settle() {
        Thread.sleep(150)
        SwingUtilities.invokeAndWait {} // state changes land on the window thread
    }

    @Test fun readsPageThenNextPage() {
        val voice = FakeSpeaker()
        val speech = DesktopSpeech(voice)
        val p1 = SpeechPage("2:0:1", "A", listOf("a1", "a2"))
        val p2 = SpeechPage("2:0:2", "B", listOf("b1"))
        var asked = 0
        speech.play(p1, 0, 1f) { asked++; if (asked == 1) p2 else null }
        settle()
        assertEquals("2:0:1", speech.playback.value.pageId)
        assertTrue(speech.playback.value.playing)
        voice.step(2); settle()
        assertEquals("2:0:2", speech.playback.value.pageId) // carried on to the next page
        voice.step(); settle()
        assertEquals(listOf("a1", "a2", "b1"), voice.said)
        assertFalse(speech.playback.value.active) // nothing after the last page
    }

    @Test fun pauseResumeSpeedSeekStop() {
        val voice = FakeSpeaker()
        val speech = DesktopSpeech(voice)
        val p = SpeechPage("1:0:0", "P", listOf("one", "two", "three"))
        speech.play(p, 0, 1f) { null }
        settle()
        speech.pause(); settle()
        assertFalse(speech.playback.value.playing)
        assertTrue(speech.playback.value.active)
        speech.resume(); settle()
        voice.step(); settle()
        assertEquals(listOf("one"), voice.said)
        assertEquals(1, speech.playback.value.part)
        speech.setRate(1.5f); settle()
        assertEquals(1.5f, voice.rates.last())
        speech.seek(2); settle()
        voice.step(); settle()
        assertEquals(listOf("one", "three"), voice.said)
        speech.play(p, 0, 1f) { null }; settle()
        speech.stop(); settle()
        assertFalse(speech.playback.value.active)
        assertEquals(4, SapiSpeaker.sapiRate(1.5f))
        assertEquals(0, SapiSpeaker.sapiRate(1f))
    }
}
