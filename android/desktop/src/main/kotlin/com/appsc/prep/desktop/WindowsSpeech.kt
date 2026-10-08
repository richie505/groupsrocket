package com.appsc.prep.desktop

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.appsc.prep.ui.components.Playback
import com.appsc.prep.ui.components.Speech
import com.appsc.prep.ui.components.SpeechPage
import java.io.File
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/** Speaks one paragraph and returns when it is done; [cancel] stops it at once (pause, skip, close). */
interface Speaker {
    /** Speaks [text] at [rate] (1 = normal); false when it was cancelled or failed. */
    fun speak(text: String, rate: Float): Boolean
    fun cancel()
}

/**
 * Read-aloud for Windows: page by page, paragraph by paragraph, with pause, skip, speed and carrying on to the
 * next page - the same controls as on the phone. Each paragraph is spoken by [speaker] on a background thread.
 */
class DesktopSpeech(private val speaker: Speaker) : Speech {
    private val state = mutableStateOf(Playback())
    override val playback: State<Playback> get() = state

    private var page: SpeechPage? = null
    private var next: (() -> SpeechPage?)? = null
    private var part = 0
    private var rate = 1f
    @Volatile private var run = 0 // the reading in progress; a new one makes the old thread stop

    override fun play(page: SpeechPage, from: Int, rate: Float, next: () -> SpeechPage?) {
        this.page = page
        this.next = next
        this.rate = rate
        part = from.coerceIn(0, (page.parts.size - 1).coerceAtLeast(0))
        start()
    }

    override fun pause() {
        halt()
        show(playing = false)
    }

    override fun resume() {
        if (page != null) start()
    }

    override fun seek(part: Int) {
        val p = page ?: return
        this.part = part.coerceIn(0, (p.parts.size - 1).coerceAtLeast(0))
        if (state.value.playing) start() else show(playing = false)
    }

    override fun setRate(rate: Float) {
        this.rate = rate
        if (state.value.playing) start() // the new speed from this paragraph
    }

    override fun stop() {
        halt()
        page = null
        set(Playback())
    }

    private fun halt() {
        run++
        speaker.cancel()
    }

    private fun start() {
        halt()
        val me = run
        show(playing = true)
        thread(isDaemon = true, name = "read-aloud") {
            while (me == run) {
                val p = page ?: return@thread
                if (p.parts.isEmpty() || part > p.parts.lastIndex) {
                    // end of the page: on to the next one the reader gives, else done
                    val n = next?.invoke()
                    if (me != run) return@thread
                    if (n == null) {
                        page = null
                        set(Playback())
                        return@thread
                    }
                    page = n
                    part = 0
                    show(playing = true)
                    continue
                }
                val ok = speaker.speak(p.parts[part], rate)
                if (me != run) return@thread
                if (!ok) { // the voice failed: stop instead of racing through the page
                    show(playing = false)
                    return@thread
                }
                part++
                if (part <= p.parts.lastIndex) show(playing = true)
            }
        }
    }

    private fun show(playing: Boolean) {
        val p = page ?: return
        set(Playback(active = true, playing = playing, pageId = p.id, part = part.coerceAtMost((p.parts.size - 1).coerceAtLeast(0))))
    }

    /** Compose state is changed on the window's thread. */
    private fun set(value: Playback) {
        if (SwingUtilities.isEventDispatchThread()) state.value = value else SwingUtilities.invokeLater { state.value = value }
    }
}

/**
 * Windows' own voices (SAPI, as Narrator uses), offline. Each paragraph is spoken by a small script run with
 * wscript.exe - a windowless host, so no console flashes up. An Indian English voice (Heera / Ravi) is used
 * when installed, else the default voice.
 */
class SapiSpeaker(private val dir: File) : Speaker {
    @Volatile private var proc: Process? = null

    private val script: File by lazy {
        File(dir, "speak.vbs").apply {
            writeText(
                """
                ' Speaks the UTF-8 text in file argument 0 at SAPI rate argument 1 (-10 .. 10).
                Set voice = CreateObject("SAPI.SpVoice")
                Set indian = voice.GetVoices("Language=4009")
                If indian.Count > 0 Then Set voice.Voice = indian.Item(0)
                voice.Rate = CInt(WScript.Arguments(1))
                Set st = CreateObject("ADODB.Stream")
                st.Type = 2
                st.Charset = "utf-8"
                st.Open
                st.LoadFromFile WScript.Arguments(0)
                text = st.ReadText
                st.Close
                voice.Speak text
                """.trimIndent().replace("\n", "\r\n"),
            )
        }
    }

    override fun speak(text: String, rate: Float): Boolean {
        val file = File(dir, "part.txt")
        return runCatching {
            file.writeText(text, Charsets.UTF_8)
            val p = ProcessBuilder("wscript.exe", "//B", "//NoLogo", script.absolutePath, file.absolutePath, sapiRate(rate).toString())
                .redirectErrorStream(true).start()
            proc = p
            val started = System.nanoTime()
            val ok = p.waitFor() == 0
            // a paragraph never takes under 0.3 s to say: an instant finish means the voice did not run
            val instant = System.nanoTime() - started < 300_000_000L && text.length > 30
            ok && !instant
        }.getOrDefault(false)
    }

    override fun cancel() {
        proc?.let { p -> runCatching { p.descendants().forEach { it.destroy() }; p.destroyForcibly() } }
        proc = null
    }

    companion object {
        /** App speed (0.75x .. 2x) to SAPI's -10 .. 10 scale, where 0 is normal. */
        fun sapiRate(rate: Float): Int = when {
            rate <= 0.8f -> -2
            rate <= 1.05f -> 0
            rate <= 1.3f -> 2
            rate <= 1.6f -> 4
            else -> 7
        }
    }
}
