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

private val Forest = Color(0xFF2F6B35)
private val Leaf = Color(0xFF557A53)
private val Canvas = Color(0xFFF8FAF7)
private val Amber = Color(0xFFB8662B)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEFD8),
    onPrimaryContainer = Color(0xFF143819),
    secondary = Leaf,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F1E4),
    onSecondaryContainer = Color(0xFF1B321D),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE8D4),
    onTertiaryContainer = Color(0xFF48230A),
    background = Canvas,
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF2F5F1),
    onSurfaceVariant = Color(0xFF4D574D),
    outline = Color(0xFFAAB3A8),
    outlineVariant = Color(0xFFDDE3DB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD49C),
    onPrimary = Color(0xFF073910),
    primaryContainer = Color(0xFF235129),
    onPrimaryContainer = Color(0xFFB8ECB5),
    secondary = Color(0xFFB5CDB0),
    onSecondary = Color(0xFF20351F),
    secondaryContainer = Color(0xFF344A34),
    onSecondaryContainer = Color(0xFFD0E8CB),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF542700),
    tertiaryContainer = Color(0xFF713A13),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF111511),
    surface = Color(0xFF191D19),
    surfaceVariant = Color(0xFF252B25),
    onSurfaceVariant = Color(0xFFC4CCC2),
    outline = Color(0xFF8E978C),
    outlineVariant = Color(0xFF3E463E),
)

private val RecipeTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

private val RecipeShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
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
