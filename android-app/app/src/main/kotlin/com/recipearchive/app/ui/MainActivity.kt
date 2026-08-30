package com.recipearchive.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.recipearchive.app.RecipeApplication
import com.recipearchive.app.ui.nav.RecipeNavHost
import com.recipearchive.app.ui.theme.RecipeArchiveTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as RecipeApplication).container
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            RecipeArchiveApp {
                RecipeNavHost(container = container, widthSizeClass = windowSizeClass.widthSizeClass)
            }
        }
    }
}

@Composable
private fun RecipeArchiveApp(content: @Composable () -> Unit) {
    RecipeArchiveTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
