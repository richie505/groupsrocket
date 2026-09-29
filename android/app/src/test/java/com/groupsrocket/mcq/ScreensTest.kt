package com.groupsrocket.mcq

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import org.junit.Rule
import org.junit.Test

/** Renders the main screens against the bundled data. Run: ./gradlew recordPaparazziDebug */
class ScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, showSystemUi = false)

    private fun app() = AppState(Repository(paparazzi.context), Progress(paparazzi.context))

    @Test fun plan() = paparazzi.snapshot { Themed { PlanScreen(app()) {} } }

    @Test fun subjects() = paparazzi.snapshot { Themed { SubjectsScreen(app()) {} } }

    @Test fun tracker() = paparazzi.snapshot { Themed { TrackerScreen(app()) {} } }

    @Test fun sheet() = paparazzi.snapshot { Themed { SheetScreen(app(), "indian-polity/1") {} } }

    @Test fun quiz() {
        val app = app()
        val quiz = Screen.Quiz("Indian Polity #1", app.repo.mcqs("indian-polity/1"))
        paparazzi.snapshot { Themed { QuizScreen(app, quiz, onExit = {}, onRetry = {}) } }
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) = RocketTheme { Surface(content = content) }
}
