package com.dasen.kaavalu.ui

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * The design tokens. Every spacing, radius and duration in the app comes from here, so that
 * "make the cards breathe a bit more" is one edit rather than forty, and so two people
 * building two screens cannot quietly invent two different rhythms.
 */

/** A 4dp rhythm. Nothing in the app is allowed to be 13dp or 27dp. */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp

    /** The page gutter. One value, every screen. */
    val gutter = 20.dp

    /** Between unrelated blocks on a page. */
    val section = 24.dp
}

/**
 * Corner radii. Small and machined rather than soft: a safety instrument has edges. The
 * microphone is the one circle in the app, which is what makes it read as *the* control.
 */
object Radius {
    val xs = 2.dp
    val sm = 4.dp
    val md = 8.dp
    val lg = 12.dp
    val pill = 999.dp
}

/** Hairlines and the heavier rules that close a tally. */
object Stroke {
    val hair = 1.dp
    val rule = 2.dp
    val signal = 3.dp
}

/**
 * Minimum touch target. Material says 48dp; this app's user is seventy and may have a tremor,
 * so the floor is 56dp for anything that matters and 64dp for the actions taken in a panic.
 */
object Touch {
    val min = 56.dp
    val critical = 64.dp
}

/**
 * Motion. Three durations, two easings, and a rule: motion either explains a change or it
 * does not ship. Entering uses decelerate, leaving uses accelerate, and the emphasized curve
 * is reserved for the two moments that carry weight — a verdict appearing, a score filling.
 */
object Motion {
    const val QUICK = 120
    const val STANDARD = 220
    const val EMPHASIZED = 380
    /**
     * The score dial and the meters. Tuned to resolve as the reasons start arriving, not
     * after they have all landed: a number still ticking under text the user has finished
     * reading is the pause that makes a reveal feel like four separate events.
     */
    const val METER = 450

    /** Decelerate. Things arriving should settle, not slam. */
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Accelerate. Things leaving should get out of the way. */
    val exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Standard in-place change. */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> enterSpec(duration: Int = STANDARD): FiniteAnimationSpec<T> =
        tween(duration, easing = enter)

    fun <T> exitSpec(duration: Int = QUICK): FiniteAnimationSpec<T> =
        tween(duration, easing = exit)

    /** A press: fast, firm, no wobble. */
    const val PRESS_DAMPING = 0.9f
    const val PRESS_STIFFNESS = Spring.StiffnessMedium

    /** Something moving to a new resting place — the nav signal, a thumbnail shrinking. */
    const val SETTLE_DAMPING = 0.88f
    const val SETTLE_STIFFNESS = Spring.StiffnessMediumLow

    /** The verdict locking. One small give, once, and only here. */
    const val LOCK_DAMPING = 0.55f
    const val LOCK_STIFFNESS = Spring.StiffnessMedium
}

/**
 * The choreography of the three signature moments.
 *
 * Kaavalu's motion has one character: protective. Something arrives and shelters, rather than
 * alarming or overriding. The shield lands first and the words unfold from behind it, which is
 * the app's own name acted out — and it is deliberately not an alarm, because the copy on that
 * screen exists to calm a frightened person into hanging up, and motion that fights the copy
 * loses.
 *
 * Everything else in the app is left alone at the standard 120–220ms.
 */
object Choreo {
    /** The shield lands with a little give. DampingRatioLowBouncy is 0.75f; this sits under it. */
    const val SHIELD_DAMPING = 0.72f
    const val SHIELD_STIFFNESS = Spring.StiffnessMediumLow      // 400f

    /** The headline unfolds behind the shield. Barely overshoots. */
    const val UNFOLD_DAMPING = 0.85f
    const val UNFOLD_STIFFNESS = Spring.StiffnessMediumLow

    /** Reasons arrive quickly and do not bounce: they are being read, not admired. */
    const val REASON_DAMPING = 0.9f
    const val REASON_STIFFNESS = Spring.StiffnessMedium         // 1500f

    // The schedule, in milliseconds between stages.
    const val SHIELD_IN = 120L
    const val HEADLINE_IN = 60L
    const val REASONS_IN = 140L
    const val STAGGER = 45L

    /** The red plane is not choreographed. It is the interrupt. */
    const val PLANE_FADE = 180
}

/** The protective spring, for a float. Collapses to [snap] when the phone asked for stillness. */
@Composable
fun protectiveSpring(
    damping: Float = Choreo.SHIELD_DAMPING,
    stiffness: Float = Choreo.SHIELD_STIFFNESS,
): FiniteAnimationSpec<Float> =
    if (LocalReduceMotion.current) snap() else spring(dampingRatio = damping, stiffness = stiffness)

/** The same spring for the slide component of an entrance. */
@Composable
fun protectiveSlide(
    damping: Float = Choreo.SHIELD_DAMPING,
    stiffness: Float = Choreo.SHIELD_STIFFNESS,
): FiniteAnimationSpec<IntOffset> =
    if (LocalReduceMotion.current) snap() else spring(dampingRatio = damping, stiffness = stiffness)

/**
 * Whether this phone has been told to stop animating.
 *
 * Android has no `prefers-reduced-motion`; the equivalent is the developer-options animation
 * scale, which accessibility tools and battery savers both set to zero. Honouring it costs
 * one read and means the breathing shield does not pulse forever on the phone of someone who
 * asked the system to hold still.
 */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun rememberSystemReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return runCatching {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}
