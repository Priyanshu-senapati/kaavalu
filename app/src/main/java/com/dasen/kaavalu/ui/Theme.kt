package com.dasen.kaavalu.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF141210)
val Paper = Color(0xFFFBF7F0)
val Alarm = Color(0xFF8E1F1A)
val Gold = Color(0xFFFFD58A)
val Guard = Color(0xFF14432B)
val Caution = Color(0xFF8A5A00)
val Muted = Color(0xFF6B655C)

/**
 * One light theme, deliberately. The user is often 70 years old in a bright room, and a
 * warning that is hard to read is a warning that does not work. Type runs a step larger
 * than Material defaults throughout.
 */
private val Scheme = lightColorScheme(
    primary = Guard,
    onPrimary = Color.White,
    secondary = Alarm,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    error = Alarm,
    onError = Color.White,
)

private val LargeType = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun KaavaluTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = LargeType, content = content)
}
