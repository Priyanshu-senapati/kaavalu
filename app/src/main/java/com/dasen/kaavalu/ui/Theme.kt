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
val Guard = Color(0xFF1F3D2B)
val Muted = Color(0xFF6B655C)

/** The middle of the ladder. A verdict that is neither "scam" nor "clear" gets its own colour. */
val Caution = Color(0xFFB4690E)

/** Card edges and dividers on Paper. Never a hairline: the user is presbyopic. */
val Line = Color(0xFFE6DFD2)

/** The tint behind the shield when protection is on. */
val GuardSoft = Color(0xFFE8F0E9)
val AlarmSoft = Color(0xFFF7E7E5)
val CautionSoft = Color(0xFFFBF0DC)

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
    // Selected chips and other "tonal" surfaces. Left at the Material default these come
    // out lavender, which belongs to no part of this app.
    secondaryContainer = GuardSoft,
    onSecondaryContainer = Guard,
    primaryContainer = GuardSoft,
    onPrimaryContainer = Guard,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF2ECE1),
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = Line,
    error = Alarm,
    onError = Color.White,
)

private val LargeType = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun KaavaluTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = LargeType, content = content)
}
