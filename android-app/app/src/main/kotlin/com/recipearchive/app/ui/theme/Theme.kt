package com.recipearchive.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SeedBrown = Color(0xFF6B4F3B)
private val SeedCream = Color(0xFFFFF7EC)

private val LightColors = lightColorScheme(
    primary = SeedBrown,
    onPrimary = SeedCream,
    secondary = Color(0xFF8A6D4E),
    background = Color(0xFFFFFBF5),
    surface = Color(0xFFFFFBF5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0C4A8),
    onPrimary = Color(0xFF3B2A1C),
    secondary = Color(0xFFD4B896),
    background = Color(0xFF1C1712),
    surface = Color(0xFF1C1712),
)

@Composable
fun RecipeArchiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
