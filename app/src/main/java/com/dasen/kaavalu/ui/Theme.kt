package com.dasen.kaavalu.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dasen.kaavalu.R

/*
 * ---------------------------------------------------------------------------------------
 * COLOUR
 * ---------------------------------------------------------------------------------------
 * Four meanings, never mixed:
 *
 *   paper + ink   neutral, everything that is simply information
 *   green         safe — protection is on, a permission is granted
 *   gold / amber  checking or attention — reading, listening, something still to do
 *   oxblood       danger, and only danger. It is not used for "setup incomplete".
 *
 * Every pair the app uses is contrast-checked; the ratios are next to the values.
 */

// Neutrals
val Ink = Color(0xFF14120F)          // primary text              16.6:1 on Paper
val Ink2 = Color(0xFF3D3831)         // body text                 11.3:1 on Sheet
val Muted = Color(0xFF5F594F)        // secondary text             6.2:1 on Paper, 6.8:1 on Sheet
val Line = Color(0xFFC2B6A0)         // borders that must be seen  1.8:1 on Paper
val LineSoft = Color(0xFFE2DACB)     // rules between rows
val Paper = Color(0xFFF6F1E8)        // the page
val PaperDeep = Color(0xFFECE4D5)    // recessed wells             Muted on it: 5.5:1
val Sheet = Color(0xFFFFFCF7)        // raised sheets. Warm, never #FFF.

/** The name the older screens use for a raised sheet. */
val Surface = Sheet

// Guard — safe, armed, protective
val Guard900 = Color(0xFF12261A)
val Guard800 = Color(0xFF18311F)
val Guard = Color(0xFF1F3D2B)        // Paper on it: 10.6:1
val Guard500 = Color(0xFF2F5B41)
val Guard300 = Color(0xFF8FB39C)
val Guard100 = Color(0xFFDBE7DF)     // on Guard: 9.4:1
val Guard50 = Color(0xFFEDF3EF)

// Alarm — scam, danger, the interrupt
val Alarm900 = Color(0xFF5C110E)
val Alarm = Color(0xFF8E1F1A)        // white on it: 8.9:1
val Alarm500 = Color(0xFFB23026)
val Alarm100 = Color(0xFFF6DCD9)
val Alarm50 = Color(0xFFFCF1EF)

// Caution — checking, attention, the middle verdict
val Caution900 = Color(0xFF5E3505)   // on Caution100: 8.6:1, on Caution50: 9.8:1
val Caution = Color(0xFF8A5009)      // on Sheet: 6.3:1
val Caution500 = Color(0xFFB4690E)
val Caution100 = Color(0xFFF8E6C8)
val Caution50 = Color(0xFFFDF6E9)

// Gold — the one accent. On dark grounds only: green plate, oxblood field.
val Gold = Color(0xFFFFD58A)         // on Alarm: 6.4:1, on Guard: 8.6:1
val Gold300 = Color(0xFFF0BC63)
val GoldDeep = Color(0xFFC98A1B)     // the reading sweep on paper

/*
 * ---------------------------------------------------------------------------------------
 * TYPE
 * ---------------------------------------------------------------------------------------
 * One family, Anek, drawn by Ek Type as a single design across Indian scripts. The Latin,
 * Devanagari and Kannada cuts share their proportions, so a warning set in Kannada is the
 * same voice as the English beside it rather than whatever the phone falls back to.
 *
 * Each cut already carries Latin, so a Kannada sentence with "1930" or "UPI" in it stays in
 * one face. The width axis does the work other apps do with a second family: condensed and
 * heavy for the state of things, normal width for reading, narrow for numerals so a column
 * of points holds its edge.
 */

@OptIn(ExperimentalTextApi::class)
private fun anek(res: Int, weight: Int, width: Float) = Font(
    res,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
    ),
)

/** The seven type roles, cut from one script's file. */
class Faces(res: Int) {
    /** The state of things. Condensed, heavy. */
    val display = FontFamily(anek(res, 700, 86f), anek(res, 780, 86f))

    /** What a screen is for. */
    val title = FontFamily(anek(res, 600, 94f), anek(res, 660, 94f))

    /** Reading, evidence, utility: one width, four weights. */
    val body = FontFamily(
        anek(res, 420, 100f),
        anek(res, 500, 100f),
        anek(res, 560, 100f),
        anek(res, 640, 100f),
    )

    /** Points, totals, phone numbers. Narrow, so a column of them keeps its right edge. */
    val numeral = FontFamily(anek(res, 700, 78f), anek(res, 780, 78f))

    /** Status words. The only uppercase in the app. */
    val sign = FontFamily(anek(res, 680, 100f))

    val all = listOf(display, title, body, numeral, sign)
}

val Latin = Faces(R.font.anek_latin)
private val Devanagari = Faces(R.font.anek_devanagari)
private val Kannada = Faces(R.font.anek_kannada)

fun facesFor(lang: String): Faces = when (lang) {
    "hi" -> Devanagari
    "kn" -> Kannada
    else -> Latin
}

/**
 * The same role in the script [lang] is written in. Anything showing a sentence from
 * [com.dasen.kaavalu.Copy] goes through this; the app's own English chrome does not need to.
 */
fun TextStyle.script(lang: String): TextStyle {
    val role = Latin.all.indexOf(fontFamily)
    if (role < 0 || lang == "en") return this
    // Devanagari and Kannada carry marks above and below the letter. The same leading that
    // sets Latin comfortably clips a Kannada vowel sign, so they get a fifth more.
    return copy(fontFamily = facesFor(lang).all[role], lineHeight = lineHeight * 1.2f)
}

private val Leading = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    family: FontFamily,
    size: Int,
    line: Int,
    weight: Int,
    tracking: Double = 0.0,
    features: String? = null,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
    fontFeatureSettings = features,
    lineHeightStyle = Leading,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * The roles Material's slots do not name. Evidence is its own voice because it is the thing
 * the app is proudest of: a reason set like a line in a report, not like a paragraph.
 */
object KType {
    /** A reason, in a tally. */
    val evidence = style(Latin.body, 17, 24, 560)

    /** Where a reason came from, and other metadata. Never smaller than this. */
    val utility = style(Latin.body, 14, 19, 500, 0.01)

    /** A status word. Short, uppercase, spaced. Real state only, never a section heading. */
    val sign = style(Latin.sign, 13, 16, 680, 0.12)

    /** Points in a tally column. */
    val points = style(Latin.numeral, 26, 28, 780, -0.01, "tnum, lnum")

    /** The total under the tally, and other numbers that are the point of a screen. */
    val total = style(Latin.numeral, 56, 56, 780, -0.02, "tnum, lnum")

    /** A phone number set as the action itself. */
    val dial = style(Latin.numeral, 34, 36, 780, 0.0, "tnum, lnum")
}

/**
 * One step larger than Material throughout; the smallest text is 14sp. Display sizes are
 * condensed, so a large verdict still fits a narrow phone at 200% font scale without
 * breaking a word.
 */
private val KaavaluType = Typography(
    displayLarge = style(Latin.display, 46, 48, 780, -0.015),
    displayMedium = style(Latin.display, 40, 43, 780, -0.015),
    displaySmall = style(Latin.display, 34, 37, 780, -0.01),
    headlineLarge = style(Latin.title, 30, 35, 660, -0.01),
    headlineMedium = style(Latin.title, 27, 32, 660, -0.01),
    headlineSmall = style(Latin.title, 23, 28, 660),
    titleLarge = style(Latin.title, 21, 26, 660),
    titleMedium = style(Latin.title, 19, 24, 600),
    titleSmall = style(Latin.body, 16, 22, 640),
    bodyLarge = style(Latin.body, 18, 27, 420),
    bodyMedium = style(Latin.body, 16, 24, 420),
    bodySmall = style(Latin.body, 15, 22, 420),
    labelLarge = style(Latin.title, 19, 22, 660),
    labelMedium = KType.sign,
    labelSmall = KType.utility,
)

private val KaavaluShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.lg),
)

/**
 * One light theme, deliberately. The user is often seventy, in a bright room, and a warning
 * that is hard to read is a warning that does not work.
 */
private val Scheme = lightColorScheme(
    primary = Guard,
    onPrimary = Paper,
    primaryContainer = Guard50,
    onPrimaryContainer = Guard900,
    secondary = Alarm,
    onSecondary = Color.White,
    secondaryContainer = Guard50,
    onSecondaryContainer = Guard,
    tertiary = Caution,
    onTertiary = Color.White,
    tertiaryContainer = Caution50,
    onTertiaryContainer = Caution900,
    background = Paper,
    onBackground = Ink,
    surface = Sheet,
    onSurface = Ink,
    surfaceVariant = PaperDeep,
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = LineSoft,
    error = Alarm,
    onError = Color.White,
    errorContainer = Alarm50,
    onErrorContainer = Alarm900,
    scrim = Color(0xCC14120F),
)

@Composable
fun KaavaluTheme(content: @Composable () -> Unit) {
    val reduceMotion = rememberSystemReduceMotion()
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = Scheme,
            typography = KaavaluType,
            shapes = KaavaluShapes,
            content = content,
        )
    }
}
