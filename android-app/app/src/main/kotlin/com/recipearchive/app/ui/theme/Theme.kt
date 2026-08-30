package com.recipearchive.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography

private val Cocoa = Color(0xFF6B3F2A)
private val Cream = Color(0xFFFFF8EF)
private val Sage = Color(0xFF53634A)
private val Apricot = Color(0xFFD77A45)

private val LightColors = lightColorScheme(
    primary = Cocoa,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC9),
    onPrimaryContainer = Color(0xFF2B160B),
    secondary = Sage,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E8CB),
    onSecondaryContainer = Color(0xFF172112),
    tertiary = Apricot,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCA),
    onTertiaryContainer = Color(0xFF321206),
    background = Cream,
    surface = Color(0xFFFFFBF7),
    surfaceVariant = Color(0xFFF2E5DA),
    onSurfaceVariant = Color(0xFF55443A),
    outline = Color(0xFF8A7466),
    outlineVariant = Color(0xFFDCC3B5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB68E),
    onPrimary = Color(0xFF4A200B),
    primaryContainer = Color(0xFF63351F),
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFFBACCAF),
    onSecondary = Color(0xFF263422),
    secondaryContainer = Color(0xFF3D4B37),
    onSecondaryContainer = Color(0xFFD6E8CB),
    tertiary = Color(0xFFFFB68F),
    onTertiary = Color(0xFF50200C),
    tertiaryContainer = Color(0xFF70361F),
    onTertiaryContainer = Color(0xFFFFDBCA),
    background = Color(0xFF1D1A17),
    surface = Color(0xFF211D1A),
    surfaceVariant = Color(0xFF55443A),
    onSurfaceVariant = Color(0xFFDCC3B5),
    outline = Color(0xFFA38D7F),
    outlineVariant = Color(0xFF55443A),
)

private val RecipeTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

private val RecipeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun RecipeArchiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RecipeTypography,
        shapes = RecipeShapes,
        content = content,
    )
}
