package com.recipearchive.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.recipearchive.app.RecipeApplication
import com.recipearchive.app.data.settings.ThemeMode
import com.recipearchive.app.ui.nav.RecipeNavHost
import com.recipearchive.app.ui.theme.RecipeArchiveTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideStatusBar()
        val container = (application as RecipeApplication).container
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val themeMode by container.settingsStore.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            RecipeArchiveApp(darkTheme = darkTheme) {
                RecipeNavHost(container = container, widthSizeClass = windowSizeClass.widthSizeClass)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    // This app is meant to sit as a fixed kitchen display -- the clock/battery/wifi status bar
    // just adds clutter there. Swiping down from the top edge still reveals it briefly.
    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun RecipeArchiveApp(darkTheme: Boolean, content: @Composable () -> Unit) {
    RecipeArchiveTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
