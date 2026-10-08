package com.appsc.prep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.appsc.prep.data.ProgressStore
import com.appsc.prep.data.Repository
import com.appsc.prep.platform.AndroidPlatform
import com.appsc.prep.platform.PrefsStorage
import com.appsc.prep.platform.ReadAloud
import com.appsc.prep.ui.AppNavHost
import com.appsc.prep.ui.NavImpl
import com.appsc.prep.ui.openTab
import com.appsc.prep.ui.showTabs
import com.appsc.prep.ui.tabFor
import com.appsc.prep.ui.tabs
import com.appsc.prep.ui.components.AppState
import com.appsc.prep.ui.components.LocalApp
import com.appsc.prep.ui.theme.C
import com.appsc.prep.ui.theme.PrepTheme

class MainActivity : ComponentActivity() {
    override fun onDestroy() {
        // closing the app ends read-aloud
        ReadAloud.onStart = null
        if (isFinishing) ReadAloud.shutdown()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // light-only app: no automatic darkening by the phone's "dark mode for apps" (see themes.xml)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.decorView.isForceDarkAllowed = false
        // read-aloud shows a notification (with Pause/Stop) while it runs: ask once on Android 13+
        ReadAloud.init(this).onStart = {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val app = AppState(
            Repository { applicationContext.assets.open(it) },
            ProgressStore(PrefsStorage(applicationContext)),
            AndroidPlatform(applicationContext),
        )
        setContent {
            PrepTheme {
                CompositionLocalProvider(LocalApp provides app) { AppRoot() }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val actions = remember(nav) { NavImpl(nav) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding(),
    ) {
        Box(Modifier.weight(1f)) { AppNavHost(nav, actions) }
        if (showTabs(route)) {
            HorizontalDivider(color = C.Line)
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.height(68.dp)) {
                val current = tabFor(route)
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = { nav.openTab(t.route) },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label, style = TextStyle(fontSize = 12.sp)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.Accent,
                            selectedTextColor = C.Accent,
                            indicatorColor = C.AccentSoft,
                            unselectedIconColor = C.Muted,
                            unselectedTextColor = C.Muted,
                        ),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
