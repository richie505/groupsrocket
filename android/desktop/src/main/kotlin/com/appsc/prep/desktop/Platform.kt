package com.appsc.prep.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appsc.prep.ui.theme.C
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.appsc.prep.ui.components.Platform
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Path
import java.nio.file.Paths

/** Folder for progress and logs: %APPDATA%\Rocket Prep on Windows, ~/.rocket-prep elsewhere. */
fun appDataDir(): Path {
    val base = System.getenv("APPDATA")?.let { Paths.get(it, APP_NAME) }
        ?: Paths.get(System.getProperty("user.home"), ".rocket-prep")
    return base.also { it.toFile().mkdirs() }
}

object DesktopPlatform : Platform {
    override val desktop = true

    /** Read-aloud with Windows' own voices; none on other systems (the Listen button is then hidden). */
    private val windowsSpeech: DesktopSpeech? by lazy {
        if (System.getProperty("os.name").orEmpty().startsWith("Windows")) DesktopSpeech(SapiSpeaker(appDataDir().toFile())) else null
    }
    override val speech: com.appsc.prep.ui.components.Speech? get() = windowsSpeech

    override fun openInBrowser(url: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
    }

    override fun askGemini(prompt: String): String {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(prompt), null)
        openInBrowser("https://gemini.google.com/app")
        return "Question copied - paste it into Gemini with Ctrl + V."
    }

    /** A short message shown at the bottom of the window. */
    var toast by mutableStateOf<String?>(null)

    /** Set by the window: pop one screen. */
    var navBack: () -> Unit = {}

    // Innermost screen last; only the last one gets the key.
    private class Handler<T>(var fn: T)
    private val backs = mutableListOf<Handler<() -> Unit>>()
    private val keys = mutableListOf<Handler<(String) -> Boolean>>()

    override fun share(title: String, text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        toast = "Copied to the clipboard – paste it anywhere with Ctrl + V"
    }

    /**
     * Google pages (Search Google, Explain simply, a word's meaning) open in the web browser - signed in to Google,
     * and an app window cannot hold a full browser. To keep text: copy it there (Ctrl + C), then "Add text ..."
     * below takes what was copied.
     */
    @Composable
    override fun WebPage(
        url: String,
        modifier: Modifier,
        back: MutableState<(() -> Boolean)?>,
        selected: MutableState<(((com.appsc.prep.ui.components.WebText) -> Unit) -> Unit)?>?,
    ) {
        LaunchedEffect(url) { openInBrowser(url) }
        Column(modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Opened in your web browser.", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = C.Ink))
            Text(
                if (selected != null) "To keep something in your notes: select it in the browser, press Ctrl + C, " +
                    "then click the green button below - the copied text comes in ticked, ready to edit and save."
                else "Read the answer there, then come back here.",
                style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = C.Body),
            )
            OutlinedButton(onClick = { openInBrowser(url) }) { Text("Open in the browser again") }
        }
    }

    // backups: a plain Windows save/open box
    @Composable
    override fun rememberSaveFile(done: (Boolean) -> Unit): ((String, String) -> Unit)? = { name, text ->
        val box = java.awt.FileDialog(null as java.awt.Frame?, "Save backup", java.awt.FileDialog.SAVE).apply { file = name; isVisible = true }
        val file = box.file?.let { java.io.File(box.directory, it) }
        done(file != null && runCatching { file.writeText(text) }.isSuccess)
    }

    @Composable
    override fun rememberOpenFile(got: (String?) -> Unit): (() -> Unit)? = {
        val box = java.awt.FileDialog(null as java.awt.Frame?, "Open backup", java.awt.FileDialog.LOAD).apply { isVisible = true }
        got(box.file?.let { f -> runCatching { java.io.File(box.directory, f).readText() }.getOrNull() })
    }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
        val current by rememberUpdatedState(onBack)
        if (enabled) {
            DisposableEffect(Unit) {
                val h = Handler { current() }
                backs += h
                onDispose { backs -= h }
            }
        }
    }

    @Composable
    override fun Shortcuts(onKey: (String) -> Boolean) {
        val current by rememberUpdatedState(onKey)
        DisposableEffect(Unit) {
            val h = Handler<(String) -> Boolean> { current(it) }
            keys += h
            onDispose { keys -= h }
        }
    }

    fun back() {
        backs.lastOrNull()?.fn?.invoke() ?: navBack()
    }

    fun onKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        if (e.key == Key.Escape || (e.isAltPressed && e.key == Key.DirectionLeft)) {
            back()
            return true
        }
        if (e.isCtrlPressed || e.isAltPressed || e.isMetaPressed) return false
        val name = when (e.key) {
            Key.DirectionLeft -> "Left"
            Key.DirectionRight -> "Right"
            Key.One, Key.NumPad1, Key.A -> "1"
            Key.Two, Key.NumPad2, Key.B -> "2"
            Key.Three, Key.NumPad3, Key.C -> "3"
            Key.Four, Key.NumPad4, Key.D -> "4"
            Key.Five, Key.NumPad5, Key.E -> "5"
            else -> return false
        }
        return keys.lastOrNull()?.fn?.invoke(name) ?: false
    }
}
