package com.appsc.prep.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.navigation.compose.rememberNavController
import com.appsc.prep.data.ProgressStore
import com.appsc.prep.data.Repository
import com.appsc.prep.ui.NavImpl
import com.appsc.prep.ui.components.AppState
import com.appsc.prep.ui.components.LocalApp
import com.appsc.prep.ui.tabFor
import com.appsc.prep.ui.theme.PrepTheme
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The Windows window drawn at common screen sizes, one image per screen, into desktop/build/shots
 * (./gradlew :desktop:test --tests '*WindowShotsTest*'). For checking the layout on a wide window.
 */
class WindowShotsTest {
    private val routes = listOf(
        "today", "plan", "day/1", "books", "book/2", "row/2/0", "read/2/0/1", "quiz/row/2/0/all?s=-1", "progress", "saved",
    )

    private fun shoot(width: Int, height: Int) {
        val out = File("build/shots/${width}x$height").apply { mkdirs() }
        val repo = Repository(::openAsset)
        runBlocking { for (b in repo.index.map { it.id }) { repo.book(b); repo.mcq(b) } }
        for (route in routes) {
            val owner = Owner()
            val app = AppState(repo, ProgressStore(FileStorage(Files.createTempDirectory("shots").resolve("p.json"))), DesktopPlatform)
            val scene = ImageComposeScene(width, height, Density(1f)) {
                PrepTheme {
                    CompositionLocalProvider(
                        LocalApp provides app,
                        androidx.lifecycle.compose.LocalLifecycleOwner provides owner,
                        androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner provides owner,
                    ) {
                        val nav = rememberNavController()
                        val actions = remember(nav) { NavImpl(nav) }
                        DesktopShell(nav, actions, tabFor(route))
                        LaunchedEffect(Unit) { if (route != "today") nav.navigate(route) }
                    }
                }
            }
            var t = 0L
            repeat(40) { scene.render(t); t += 50_000_000L; Thread.sleep(15) }
            val img = scene.render(t)
            File(out, route.replace(Regex("[/?=]"), "_") + ".png").writeBytes(img.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            scene.close()
        }
    }

    /** What a window gives the screens: a running lifecycle and a view-model store (for navigation). */
    private class Owner : androidx.lifecycle.LifecycleOwner, androidx.lifecycle.ViewModelStoreOwner {
        private val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply {
            currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        }
        override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        override val viewModelStore = androidx.lifecycle.ViewModelStore()
    }

    @Test fun shots() {
        // as in the app, the screens run on the window (Swing) thread
        javax.swing.SwingUtilities.invokeAndWait {
            shoot(1366, 768)
            shoot(1920, 1080)
        }
    }
}
