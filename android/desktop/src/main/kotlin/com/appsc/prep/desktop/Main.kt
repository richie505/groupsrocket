package com.appsc.prep.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.appsc.prep.data.ProgressStore
import com.appsc.prep.data.Repository
import com.appsc.prep.ui.AppNavHost
import com.appsc.prep.ui.NavImpl
import com.appsc.prep.ui.openTab
import com.appsc.prep.ui.tabFor
import com.appsc.prep.ui.tabs
import com.appsc.prep.ui.components.AppState
import com.appsc.prep.ui.components.LocalApp
import com.appsc.prep.ui.theme.C
import com.appsc.prep.ui.theme.PrepTheme
import kotlinx.coroutines.delay
import java.awt.Dimension
import javax.swing.JOptionPane

const val APP_NAME = "Rocket Prep"
val APP_VERSION: String = Repository::class.java.`package`?.implementationVersion ?: "dev"

/** Notes and PYQs ship inside the app jar (the Android assets folder). */
fun openAsset(name: String) =
    Repository::class.java.getResourceAsStream("/$name") ?: error("Missing app data: $name")

fun main() {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        val log = appDataDir().resolve("error.log")
        runCatching { log.toFile().appendText("${java.time.LocalDateTime.now()}\n${e.stackTraceToString()}\n") }
        JOptionPane.showMessageDialog(null, "$APP_NAME hit an error and has to close.\nDetails were saved to:\n$log", APP_NAME, JOptionPane.ERROR_MESSAGE)
        kotlin.system.exitProcess(1)
    }
    val app = AppState(Repository(::openAsset), ProgressStore(FileStorage(appDataDir().resolve("progress.json"))), DesktopPlatform)
    val icon = BitmapPainter(useResource("icon.png", ::loadImageBitmap))

    application {
        val state = rememberWindowState(size = DpSize(1200.dp, 820.dp), position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = { DesktopPlatform.speech?.stop(); exitApplication() },
            title = APP_NAME,
            icon = icon,
            state = state,
            onPreviewKeyEvent = DesktopPlatform::onKey,
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(880, 600) }
            PrepTheme {
                CompositionLocalProvider(LocalApp provides app) { DesktopRoot(app) }
            }
        }
    }
}

@Composable
private fun FrameWindowScope.DesktopRoot(app: AppState) {
    val nav = rememberNavController()
    val actions = remember(nav) { NavImpl(nav) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    var dialog by remember { mutableStateOf<String?>(null) }
    DesktopPlatform.navBack = { if (nav.previousBackStackEntry != null) nav.popBackStack() }

    Menus(app, nav) { dialog = it }
    DesktopShell(nav, actions, tabFor(route))

    when (dialog) {
        "keys" -> InfoDialog("Keyboard shortcuts", onClose = { dialog = null }) {
            SHORTCUTS.forEach { (heading, keys) ->
                Text(heading.uppercase(), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = C.Accent, letterSpacing = 0.8.sp), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                keys.forEach { (k, what) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(k, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink), modifier = Modifier.width(170.dp))
                        Text(what, style = TextStyle(fontSize = 14.sp, color = C.Body))
                    }
                }
            }
        }
        "about" -> InfoDialog("About $APP_NAME", onClose = { dialog = null }) {
            Text(
                "Version $APP_VERSION\n\nROCKET Sheets notes (14 subjects, 721 sheets), the 90-day plan and 15,855 MCQs " +
                    "with MCQ technique hints.\n\nYour progress is saved on this computer in:\n${appDataDir()}",
                style = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, color = C.Body),
            )
        }
    }
}

/** Help → Keyboard shortcuts: (heading, [(keys, action)]). */
private val SHORTCUTS = listOf(
    "While practising MCQs" to listOf(
        "1 – 4  or  A – D" to "choose an option",
        "Right arrow" to "next / skip",
        "Left arrow" to "previous question",
    ),
    "Anywhere" to listOf(
        "Esc  or  Alt + Left" to "go back",
        "Ctrl + 1 … 5" to "Today, Plan, Notes, Progress, Saved",
        "Ctrl + =  /  Ctrl + −" to "larger / smaller reading text",
    ),
)

@Composable
private fun FrameWindowScope.Menus(app: AppState, nav: NavHostController, show: (String) -> Unit) {
    val digit = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five)
    MenuBar {
        Menu("File", mnemonic = 'F') {
            Item("Exit", mnemonic = 'x', shortcut = KeyShortcut(Key.F4, alt = true)) { kotlin.system.exitProcess(0) }
        }
        Menu("Go", mnemonic = 'G') {
            tabs.forEachIndexed { i, t ->
                Item(t.label, shortcut = KeyShortcut(digit[i], ctrl = true)) { nav.openTab(t.route) }
            }
            Separator()
            Item("Back    (Esc)") { DesktopPlatform.back() }
        }
        Menu("View", mnemonic = 'V') {
            Item("Larger text", shortcut = KeyShortcut(Key.Equals, ctrl = true)) { app.store.changeTextScale(0.1f) }
            Item("Smaller text", shortcut = KeyShortcut(Key.Minus, ctrl = true)) { app.store.changeTextScale(-0.1f) }
        }
        Menu("Help", mnemonic = 'H') {
            Item("Keyboard shortcuts") { show("keys") }
            Item("About $APP_NAME") { show("about") }
        }
    }
}

/** The window's content: navigation pane on the left, the screen on the right (also rendered by the tests). */
@Composable
fun DesktopShell(nav: androidx.navigation.NavHostController, actions: NavImpl, current: String) {
    Row(Modifier.fillMaxSize().background(Color.White)) {
        Sidebar(current) { nav.openTab(it) }
        VerticalDivider(color = C.Line)
        Box(Modifier.weight(1f).fillMaxHeight().background(Color.White)) {
            // screens use the whole window: their lists keep a readable width and lay cards out in columns
            AppNavHost(nav, actions)
            Toast(Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Left navigation pane, as in Windows 11 apps. */
@Composable
private fun Sidebar(current: String, onOpen: (String) -> Unit) {
    Column(Modifier.width(212.dp).fillMaxHeight().background(Color.White).padding(horizontal = 10.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp, bottom = 18.dp)) {
            Icon(BitmapPainter(useResource("icon.png", ::loadImageBitmap)), null, tint = Color.Unspecified, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(APP_NAME, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = C.Navy))
                Text("Group 1 & 2", style = TextStyle(fontSize = 11.sp, color = C.Muted))
            }
        }
        tabs.forEach { t ->
            val selected = t.route == current
            Row(
                Modifier
                    .padding(vertical = 2.dp)
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) C.AccentSoft else Color.Transparent)
                    .clickable { onOpen(t.route) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(3.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) C.Accent else Color.Transparent))
                Spacer(Modifier.width(10.dp))
                Icon(t.icon, null, tint = if (selected) C.Accent else C.Muted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    t.label,
                    style = TextStyle(fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) C.Accent else C.Ink),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Version $APP_VERSION", style = TextStyle(fontSize = 11.sp, color = C.Faint), modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun Toast(modifier: Modifier) {
    val msg = DesktopPlatform.toast ?: return
    LaunchedEffect(msg) {
        delay(2500)
        DesktopPlatform.toast = null
    }
    Box(modifier.padding(bottom = 90.dp).clip(RoundedCornerShape(10.dp)).background(C.Navy).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Text(msg, style = TextStyle(fontSize = 14.sp, color = Color.White))
    }
}

@Composable
private fun InfoDialog(title: String, onClose: () -> Unit, body: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
        title = { Text(title) },
        text = { Column { body() } },
    )
}
