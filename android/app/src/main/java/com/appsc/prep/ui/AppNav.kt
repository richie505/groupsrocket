package com.appsc.prep.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.appsc.prep.ui.screens.BookScreen
import com.appsc.prep.ui.screens.BooksScreen
import com.appsc.prep.ui.screens.DayScreen
import com.appsc.prep.ui.screens.Nav
import com.appsc.prep.ui.screens.PlanScreen
import com.appsc.prep.ui.screens.ProgressScreen
import com.appsc.prep.ui.screens.QuizScreen
import com.appsc.prep.ui.screens.QuizSource
import com.appsc.prep.ui.screens.ReaderScreen
import com.appsc.prep.ui.screens.SavedScreen
import com.appsc.prep.ui.screens.SectionScreen
import com.appsc.prep.ui.screens.TodayScreen

/** Routes and screens shared by the Android app and the Windows app; each draws its own tab bar. */
data class Tab(val route: String, val label: String, val icon: ImageVector)

val tabs = listOf(
    Tab("today", "Today", Icons.Outlined.Home),
    Tab("plan", "Plan", Icons.Outlined.CalendarMonth),
    Tab("books", "Notes", Icons.AutoMirrored.Outlined.MenuBook),
    Tab("progress", "Progress", Icons.Outlined.BarChart),
    Tab("saved", "Saved", Icons.Outlined.BookmarkBorder),
)

/** Whether the tab bar shows on this route (hidden while reading or practising). */
fun showTabs(route: String?): Boolean =
    route in tabs.map { it.route } || route?.startsWith("day/") == true ||
        route?.startsWith("row/") == true || route?.startsWith("book/") == true

/** The tab a route belongs to. */
fun tabFor(route: String?): String = tabs.firstOrNull { it.route == route }?.route ?: when {
    route?.startsWith("day/") == true || route?.startsWith("quiz/day") == true -> "plan"
    else -> "books"
}

fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo("today") { saveState = true }
    launchSingleTop = true
    restoreState = true
}

class NavImpl(private val nav: NavHostController) : Nav {
    override fun quiz(kind: String, book: Int, index: Int, mode: String, sub: Int) =
        nav.navigate("quiz/$kind/$book/$index/$mode?s=$sub")
    override fun day(n: Int) = nav.navigate("day/$n")
    override fun row(book: Int, row: Int) = nav.navigate("row/$book/$row")
    override fun read(book: Int, row: Int, sec: Int) = nav.navigate("read/$book/$row/$sec")
    override fun book(id: Int) = nav.navigate("book/$id")
    override fun back() {
        nav.popBackStack()
    }
}

@Composable
fun AppNavHost(nav: NavHostController, actions: Nav) {
    NavHost(nav, startDestination = "today") {
        composable("today") { TodayScreen(actions) }
        composable("plan") { PlanScreen(actions) }
        composable("books") { BooksScreen(actions) }
        composable("progress") { ProgressScreen(actions) }
        composable("saved") { SavedScreen(actions) }
        composable(
            "quiz/{k}/{b}/{i}/{m}?s={s}",
            listOf(
                navArgument("k") { type = NavType.StringType },
                navArgument("b") { type = NavType.IntType },
                navArgument("i") { type = NavType.IntType },
                navArgument("m") { type = NavType.StringType },
                navArgument("s") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) {
            val a = it.arguments!!
            val src = QuizSource(a.getString("k")!!, a.getInt("b"), a.getInt("i"), a.getInt("s"))
            val title = when (src.kind) {
                "day" -> "Day ${src.index} MCQs"
                "sub" -> if (src.sub < 0) "Other section MCQs" else "Subsection MCQs"
                "row" -> "Section MCQs"
                else -> "MCQ Practice"
            }
            QuizScreen(src, a.getString("m")!!, title, actions)
        }
        composable("day/{n}", listOf(navArgument("n") { type = NavType.IntType })) {
            DayScreen(it.arguments!!.getInt("n"), actions)
        }
        composable("book/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
            BookScreen(it.arguments!!.getInt("id"), actions)
        }
        composable(
            "row/{b}/{r}",
            listOf(navArgument("b") { type = NavType.IntType }, navArgument("r") { type = NavType.IntType }),
        ) {
            SectionScreen(it.arguments!!.getInt("b"), it.arguments!!.getInt("r"), actions)
        }
        composable(
            "read/{b}/{r}/{s}",
            listOf(
                navArgument("b") { type = NavType.IntType },
                navArgument("r") { type = NavType.IntType },
                navArgument("s") { type = NavType.IntType },
            ),
        ) {
            ReaderScreen(it.arguments!!.getInt("b"), it.arguments!!.getInt("r"), it.arguments!!.getInt("s"), actions)
        }
    }
}
