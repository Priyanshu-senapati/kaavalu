package com.dasen.kaavalu.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/*
 * The component vocabulary. Five surfaces, each with one job, so no screen is a stack of
 * identical boxes:
 *
 *   Plate   the state of things. Solid colour, the shield watermarked into it.
 *   Sheet   evidence and lists. Warm white, hairline rows, a little lift.
 *   Well    recessed. Where something is captured: a photo, a voice, typed words.
 *   Strip   a group of actions sharing one sheet, divided by rules.
 *   Sign    a short status word on a solid tag.
 *
 * And one signature: the evidence tally, which is how Kaavalu shows a verdict.
 */

// ── Motion ──────────────────────────────────────────────────────────────────────────────

/**
 * A duration that collapses to zero when the phone has asked for stillness. Every tween in
 * the app goes through this.
 */
@Composable
fun motion(duration: Int): Int = if (LocalReduceMotion.current) 0 else duration

/** A spring that becomes a snap under reduced motion. */
@Composable
fun <T> kSpring(
    damping: Float = Motion.SETTLE_DAMPING,
    stiffness: Float = Motion.SETTLE_STIFFNESS,
): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else spring(dampingRatio = damping, stiffness = stiffness)

/**
 * A staged choreography clock. Advances 0 -> 1 -> 2 ... through [schedule], waiting each
 * interval in turn, and restarts whenever [key] changes.
 *
 * Under reduce-motion it jumps straight to the final stage, so the content is *present*
 * rather than merely un-animated.
 */
@Composable
fun rememberStage(key: Any?, schedule: List<Long>): Int {
    val reduce = LocalReduceMotion.current
    var stage by remember(key, reduce) { mutableIntStateOf(if (reduce) schedule.size else 0) }
    LaunchedEffect(key, reduce) {
        if (reduce) {
            stage = schedule.size
            return@LaunchedEffect
        }
        stage = 0
        schedule.forEachIndexed { i, wait ->
            delay(wait)
            stage = i + 1
        }
    }
    return stage
}

/**
 * A count that walks up to [target] one step at a time, [step] ms apart. For a list that
 * grows while you watch it — a live call gathering evidence — so each new line arrives as
 * its own event. Drops straight down when the list is cleared.
 */
@Composable
fun rememberTicker(target: Int, step: Long = 140L): Int {
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableIntStateOf(if (reduce) target else 0) }
    LaunchedEffect(target, reduce) {
        if (reduce || target < shown) {
            shown = target
            return@LaunchedEffect
        }
        while (shown < target) {
            delay(step)
            shown++
        }
    }
    return shown
}

/** One stage of an arrival: it rises a little and settles. */
@Composable
fun StageIn(
    visible: Boolean,
    from: Int = 40,
    damping: Float = Choreo.UNFOLD_DAMPING,
    stiffness: Float = Choreo.UNFOLD_STIFFNESS,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(protectiveSpring(damping, stiffness)) +
            slideInVertically(protectiveSlide(damping, stiffness)) { from },
        exit = fadeOut(tween(motion(Motion.QUICK))),
    ) { content() }
}

/** The entrance used by an answer panel. */
@Composable
fun VerdictReveal(visible: Boolean = true, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(motion(Motion.STANDARD))) +
            slideInVertically(kSpring()) { it / 10 },
        exit = fadeOut(tween(motion(Motion.QUICK))),
    ) { content() }
}

/** The press response shared by every tappable surface: a short, firm give. */
@Composable
fun pressScale(source: MutableInteractionSource, depth: Float = 0.97f): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) depth else 1f,
        animationSpec = kSpring(Motion.PRESS_DAMPING, Motion.PRESS_STIFFNESS),
        label = "press",
    )
    return scale
}

// ── Tone ────────────────────────────────────────────────────────────────────────────────

/** The four meanings colour is allowed to carry. */
enum class Tone { Safe, Checking, Danger, Neutral }

/** The solid colour of a sign or a filled mark in this tone. */
fun Tone.solid(onDark: Boolean = false): Color = when (this) {
    Tone.Safe -> if (onDark) Guard300 else Guard
    Tone.Checking -> if (onDark) Gold else Caution
    Tone.Danger -> if (onDark) Gold else Alarm
    Tone.Neutral -> if (onDark) Paper else Ink2
}

/** Text that sits on [solid]. */
fun Tone.onSolid(onDark: Boolean = false): Color = when {
    onDark -> Alarm900
    this == Tone.Checking -> Color.White
    else -> Paper
}

/** Headline colour on a light ground. */
fun Tone.ink(): Color = when (this) {
    Tone.Safe -> Guard900
    Tone.Checking -> Caution900
    Tone.Danger -> Alarm900
    Tone.Neutral -> Ink
}

/** The points in a tally. */
fun Tone.figure(onDark: Boolean = false): Color = when (this) {
    Tone.Safe -> if (onDark) Guard300 else Guard500
    Tone.Checking -> if (onDark) Gold else Caution
    Tone.Danger -> if (onDark) Gold else Alarm
    Tone.Neutral -> if (onDark) Paper else Ink2
}

// ── Identity ────────────────────────────────────────────────────────────────────────────

/**
 * The wordmark. The English name carries the weight; the Kannada name sits on the same
 * baseline as its equal, in the Kannada cut of the same family.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier, colour: Color = Guard) {
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Bottom) {
        Icon(
            painterResource(R.drawable.ic_shield_k),
            contentDescription = null,
            tint = colour,
            modifier = Modifier.padding(bottom = 3.dp).size(28.dp),
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            "Kaavalu",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Latin.display),
            color = colour,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(Space.xs))
        Text(
            "ಕಾವಲು",
            style = MaterialTheme.typography.titleMedium.script("kn"),
            color = Guard500,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * A status word on a solid tag. This is the only uppercase in the app, and it only ever
 * names a real state: PROTECTED, LIVE, SCAM. A sign is never a section heading.
 */
@Composable
fun SignTag(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    live: Boolean = false,
    style: TextStyle = KType.sign,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.xs))
            .background(container)
            .padding(horizontal = Space.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) {
            LiveDot(content)
            Spacer(Modifier.width(Space.xs))
        }
        Text(text.uppercase(), style = style, color = content)
    }
}

/**
 * The "I am still here" signal. A slow change in opacity rather than a pulse in size: it
 * should be noticed by someone looking for it, not catch the eye of someone who is not.
 */
@Composable
fun LiveDot(colour: Color) {
    val still = LocalReduceMotion.current
    val alpha = if (still) 1f else {
        val t = rememberInfiniteTransition(label = "live")
        val a by t.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(1400, easing = Motion.standard), RepeatMode.Reverse),
            label = "liveAlpha",
        )
        a
    }
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(colour),
    )
}

/**
 * A screen's title block. Left-aligned, larger than a section, and only on screens that
 * are not already named by the tab the user just pressed does it carry a way back.
 */
@Composable
fun ScreenTitle(
    title: String,
    lead: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Go back",
) {
    Column {
        if (onBack != null) {
            val source = remember { MutableInteractionSource() }
            val scale = pressScale(source)
            Box(
                Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .size(Touch.min)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(Sheet)
                    .border(Stroke.hair, Line, RoundedCornerShape(Radius.md))
                    .clickable(
                        interactionSource = source,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = backLabel,
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_back), backLabel, tint = Ink, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(Space.lg))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = Ink,
            modifier = Modifier.semantics { heading() },
        )
        lead?.let {
            Spacer(Modifier.height(Space.xs))
            Text(it, style = MaterialTheme.typography.bodyLarge, color = Muted)
        }
    }
}

/** A heading inside a screen. Sentence case, a real title — not an uppercase eyebrow. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, colour: Color = Ink) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = colour,
        modifier = modifier.semantics { heading() },
    )
}

// ── Surfaces ────────────────────────────────────────────────────────────────────────────

/** A hairline. */
@Composable
fun HRule(modifier: Modifier = Modifier, colour: Color = LineSoft, thickness: Dp = Stroke.hair) {
    Box(modifier.fillMaxWidth().height(thickness).background(colour))
}

/**
 * The state surface. A solid field of colour with the shield cut large into its corner:
 * the one place the brand mark is allowed to be big, because this is the one place the
 * app is saying what it is doing.
 *
 * [reveal] draws the watermark up from the bottom, 0 to 1. Arming uses it once.
 */
@Composable
fun Plate(
    colour: Color,
    modifier: Modifier = Modifier,
    watermark: Color = Color.White.copy(alpha = 0.09f),
    reveal: Float = 1f,
    depth: Float = 0.16f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.lg)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = colour, spotColor = colour)
            .clip(shape)
            .background(colour)
            .drawBehind {
                // Tonal depth: the plate is lit from above, so it reads as an object.
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = depth))))
            },
    ) {
        Icon(
            painterResource(R.drawable.ic_shield_k),
            contentDescription = null,
            tint = watermark,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 56.dp, y = (-18).dp)
                .size(220.dp)
                .drawWithContent {
                    clipRect(top = size.height * (1f - reveal)) { this@drawWithContent.drawContent() }
                },
        )
        Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.sm), content = content)
    }
}

/**
 * A raised sheet: evidence, lists, forms. The lift is small and warm, and the edge is a
 * hairline, so the sheet survives sunlight where a shadow alone would vanish.
 */
@Composable
fun Sheet(
    modifier: Modifier = Modifier,
    padding: Dp = Space.lg,
    tint: Color = Sheet,
    edge: Color = LineSoft,
    spacing: Dp = Space.sm,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.md)
    Column(
        modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, ambientColor = Ink.copy(alpha = 0.10f), spotColor = Ink.copy(alpha = 0.14f))
            .background(tint, shape)
            .border(Stroke.hair, edge, shape)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/**
 * A recessed well. Pressed into the page rather than lifted from it, with a soft inner
 * shadow along the top edge: this is where something is put *in*.
 */
@Composable
fun Well(
    modifier: Modifier = Modifier,
    padding: Dp = Space.lg,
    tint: Color = PaperDeep,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.lg)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint)
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(
                        listOf(Ink.copy(alpha = 0.08f), Color.Transparent),
                        endY = 12.dp.toPx(),
                    ),
                    size = Size(size.width, 12.dp.toPx()),
                )
            }
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        content = content,
    )
}

// ── Actions ─────────────────────────────────────────────────────────────────────────────

enum class ActionStyle { Primary, Danger, Secondary, Ghost, Gold, Paper }

/**
 * A button that feels like one. It compresses under the thumb and darkens a shade, and
 * comes back with a firm spring. 56dp tall, 64dp for anything pressed in a panic.
 */
@Composable
fun BigAction(
    label: String,
    style: ActionStyle = ActionStyle.Primary,
    icon: Int? = null,
    enabled: Boolean = true,
    critical: Boolean = false,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val base = when (style) {
        ActionStyle.Primary -> Guard
        ActionStyle.Danger -> Alarm
        ActionStyle.Secondary -> Sheet
        ActionStyle.Ghost -> Color.Transparent
        ActionStyle.Gold -> Gold
        ActionStyle.Paper -> Paper
    }
    val ink = when (style) {
        ActionStyle.Primary -> Paper
        ActionStyle.Danger -> Color.White
        ActionStyle.Secondary -> Ink
        ActionStyle.Ghost -> Guard
        ActionStyle.Gold -> Ink
        ActionStyle.Paper -> Alarm900
    }
    val fill by animateColorAsState(
        when {
            !enabled -> LineSoft
            pressed && style == ActionStyle.Ghost -> Guard50
            pressed -> lerp(base, Ink, 0.14f)
            else -> base
        },
        tween(motion(Motion.QUICK)),
        label = "actionFill",
    )
    val shape = RoundedCornerShape(Radius.md)
    val scale = pressScale(source)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxWidth()
            .heightIn(min = if (critical) Touch.critical else Touch.min)
            .clip(shape)
            .background(fill)
            .then(if (style == ActionStyle.Secondary) Modifier.border(1.5.dp, Line, shape) else Modifier)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Space.lg, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val colour = if (enabled) ink else Muted
            if (icon != null) {
                Icon(painterResource(icon), null, tint = colour, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(Space.sm))
            }
            Text(label, style = textStyle, color = colour, textAlign = TextAlign.Center)
        }
    }
}

/** A group of actions sharing one sheet, divided by rules. */
@Composable
fun ActionStrip(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Sheet(modifier, padding = 0.dp, spacing = 0.dp, content = content)
}

/**
 * One row of an [ActionStrip]: an icon and a verb, with the consequence underneath. The
 * arrow leans forward under the thumb, which is the whole of the "this goes somewhere"
 * signal — no chevron in a box, no tile.
 */
@Composable
fun ActionRow(
    icon: Int,
    verb: String,
    detail: String,
    tone: Tone = Tone.Safe,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill by animateColorAsState(
        if (pressed) PaperDeep else Color.Transparent,
        tween(motion(Motion.QUICK)),
        label = "rowFill",
    )
    val lean by animateFloatAsState(
        if (pressed) 6f else 0f,
        kSpring(Motion.PRESS_DAMPING, Motion.PRESS_STIFFNESS),
        label = "lean",
    )
    val accent = tone.solid()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .background(fill)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(verb, style = MaterialTheme.typography.titleLarge, color = if (tone == Tone.Danger) Alarm900 else Ink)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Spacer(Modifier.width(Space.sm))
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                painterResource(R.drawable.ic_arrow),
                null,
                tint = Ink2,
                modifier = Modifier.size(24.dp).graphicsLayer { translationX = lean.dp.toPx() },
            )
        }
    }
}

/**
 * A language choice, set in its own script. Selection fills from the edge rather than
 * swapping, so the change reads as a change.
 */
@Composable
fun LangChip(
    label: String,
    code: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.md)
    val source = remember { MutableInteractionSource() }
    val spec = tween<Color>(motion(Motion.STANDARD), easing = Motion.standard)
    val fill by animateColorAsState(if (selected) Guard else Sheet, spec, label = "chipFill")
    val edge by animateColorAsState(if (selected) Guard else Line, spec, label = "chipEdge")
    val ink by animateColorAsState(if (selected) Paper else Ink, spec, label = "chipInk")
    val scale = pressScale(source)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = Touch.min)
            .clip(shape)
            .background(fill)
            .border(1.5.dp, edge, shape)
            .semantics { this.selected = selected }
            .clickable(interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium.script(code), color = ink, maxLines = 1)
    }
}

// ── State marks ─────────────────────────────────────────────────────────────────────────

/**
 * On or off, as a mark. Turning on is the one small celebration the app allows: the check
 * lands with a short spring. Turning off is instant.
 */
@Composable
fun StatusGlyph(ok: Boolean, modifier: Modifier = Modifier) {
    val enter = if (LocalReduceMotion.current) fadeIn(snap()) else
        scaleIn(spring(dampingRatio = 0.6f, stiffness = Motion.PRESS_STIFFNESS), initialScale = 0.4f) + fadeIn(tween(90))
    AnimatedContent(
        targetState = ok,
        transitionSpec = { enter togetherWith fadeOut(snap()) },
        label = "glyph",
        modifier = modifier.semantics { stateDescription = if (ok) "On" else "Off" },
    ) { on ->
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(if (on) Guard else Caution50)
                .then(if (on) Modifier else Modifier.border(2.dp, Caution500, RoundedCornerShape(Radius.sm))),
            contentAlignment = Alignment.Center,
        ) {
            if (on) {
                Icon(painterResource(R.drawable.ic_check), null, tint = Paper, modifier = Modifier.size(20.dp))
            } else {
                Box(Modifier.size(width = 14.dp, height = 3.dp).background(Caution900))
            }
        }
    }
}

/**
 * Progress as a row of segments, one per thing that has to be true. A segment fills when
 * its item does, so the meter and the list below it are visibly the same fact.
 */
@Composable
fun SegmentMeter(states: List<Boolean>, modifier: Modifier = Modifier) {
    val on = states.count { it }
    Row(
        modifier
            .fillMaxWidth()
            .height(10.dp)
            .clearAndSetSemantics { contentDescription = "$on of ${states.size} on" },
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        states.forEach { ok ->
            val c by animateColorAsState(
                if (ok) Guard500 else Line,
                tween(motion(Motion.EMPHASIZED), easing = Motion.standard),
                label = "segment",
            )
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(Radius.xs)).background(c))
        }
    }
}

// ── Evidence ────────────────────────────────────────────────────────────────────────────

/** One entry in a tally: how many points, what was seen, and which part of the phone saw it. */
data class EvidenceLine(val points: Int, val text: String, val source: String)

/**
 * Where a score sits against the thresholds that act on it. [marks] are the scores at which
 * something changes; the zones between them are safe, checking and danger in that order.
 */
data class ScaleSpec(val marks: List<Int>, val describe: String)

/**
 * The evidence tally. Kaavalu's signature, and its argument: a verdict is a sum, and here is
 * every line of it, with the part of the phone that produced each one.
 *
 * Laid out like a ledger. Points in a narrow numeral column with their right edges aligned,
 * a margin rule, the reason, the source under it. Then a double rule and the total.
 *
 * [revealed] is how many lines have arrived; the total counts up as each one does, so
 * every reason visibly adds to the verdict. Past the last line, the rule draws and the
 * total locks.
 */
@Composable
fun EvidenceTally(
    lines: List<EvidenceLine>,
    total: Int,
    tone: Tone,
    modifier: Modifier = Modifier,
    revealed: Int = Int.MAX_VALUE,
    onDark: Boolean = false,
    lang: String = "en",
    totalCaption: String = "points of evidence",
    capLabel: String = "Kaavalu’s scale stops at 100",
    moreLabel: (Int) -> String = { "$it more signs" },
    scale: ScaleSpec? = null,
) {
    val sum = lines.sumOf { it.points }
    // A strong fake notice can match a dozen markers. The strongest few carry the argument;
    // the rest fold into one line that opens on request, so the verdict stays readable.
    val folded = lines.size > TALLY_LIMIT + 1
    val head = if (folded) lines.take(TALLY_LIMIT) else lines
    val rest = if (folded) lines.drop(TALLY_LIMIT) else emptyList()
    val rows = tallyRows(lines.size)
    val done = revealed >= rows
    val shown = if (done) rows else revealed.coerceAtLeast(0)
    val running = if (done) total else {
        (head.take(shown).sumOf { it.points } + if (shown > head.size) rest.sumOf { it.points } else 0)
            .coerceAtMost(100)
    }
    var open by remember(lines) { androidx.compose.runtime.mutableStateOf(false) }

    val figure = tone.figure(onDark)
    val text = if (onDark) Color.White else Ink
    val sub = if (onDark) Color.White.copy(alpha = 0.72f) else Muted
    val rule = if (onDark) Color.White.copy(alpha = 0.22f) else LineSoft

    Column(modifier.fillMaxWidth()) {
        head.forEachIndexed { i, line ->
            Arrive(i < shown) {
                Column {
                    if (i > 0) HRule(Modifier.padding(start = 76.dp), rule)
                    TallyRow(line, figure, text, sub, rule, lang)
                }
            }
        }
        if (folded) {
            Arrive(shown > head.size) {
                Column {
                    HRule(Modifier.padding(start = 76.dp), rule)
                    MoreRow(rest.sumOf { it.points }, moreLabel(rest.size), open, figure, text, sub, rule, lang) { open = !open }
                    AnimatedVisibility(
                        visible = open,
                        enter = fadeIn(tween(motion(Motion.STANDARD))) + expandVertically(tween(motion(Motion.EMPHASIZED), easing = Motion.enter)),
                        exit = fadeOut(tween(motion(Motion.QUICK))) + shrinkVertically(tween(motion(Motion.STANDARD), easing = Motion.exit)),
                    ) {
                        Column {
                            rest.forEach { line ->
                                HRule(Modifier.padding(start = 76.dp), rule)
                                TallyRow(line, figure.copy(alpha = 0.8f), text, sub, rule, lang)
                            }
                        }
                    }
                }
            }
        }

        // Nothing to add up until the first line lands: an empty tally showing "0" beside a
        // header that already names the score would contradict it.
        AnimatedVisibility(
            visible = done || shown > 0,
            enter = fadeIn(tween(motion(Motion.STANDARD))),
            exit = fadeOut(tween(motion(Motion.QUICK))),
        ) {
            Column { TallyFoot(done, sum, total, running, figure, text, sub, totalCaption, capLabel, lang, scale, onDark) }
        }
    }
}

@Composable
private fun TallyFoot(
    done: Boolean,
    sum: Int,
    total: Int,
    running: Int,
    figure: Color,
    text: Color,
    sub: Color,
    totalCaption: String,
    capLabel: String,
    lang: String,
    scale: ScaleSpec?,
    onDark: Boolean,
) {
    // The rule that closes the sum. It draws left to right once the last line is in.
    val draw by animateFloatAsState(
        if (done) 1f else 0f,
        tween(motion(Motion.EMPHASIZED), easing = Motion.standard),
        label = "rule",
    )
    Spacer(Modifier.height(Space.xs))
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .drawBehind {
                val w = size.width * draw
                val c = if (onDark) Color.White.copy(alpha = 0.7f) else Ink
                drawRect(c, size = Size(w, 2.dp.toPx()))
                drawRect(c, topLeft = Offset(0f, 4.dp.toPx()), size = Size(w, 1.dp.toPx()))
            },
    )
    Spacer(Modifier.height(Space.sm))
    // The cap is arithmetic, not evidence: it is said under the total, not listed above it.
    val note = if (done && sum > total) "$sum${if (lang == "en") " found" else ""} · $capLabel" else "/ 100"
    TallyTotal(running, done, figure, text, sub, totalCaption, note, lang)
    if (scale != null) {
        Spacer(Modifier.height(Space.md))
        SignalScale(if (done) total else running, scale, onDark)
    }
}

/** How many tally lines are shown before the rest fold away. */
const val TALLY_LIMIT = 4

/** The number of rows a tally of [lines] reasons shows: the reveal schedule counts these. */
fun tallyRows(lines: Int) = if (lines > TALLY_LIMIT + 1) TALLY_LIMIT + 1 else lines

@Composable
private fun Arrive(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(motion(Motion.STANDARD))) +
            slideInHorizontally(kSpring(Choreo.REASON_DAMPING, Choreo.REASON_STIFFNESS)) { -it / 10 },
        exit = fadeOut(tween(motion(Motion.QUICK))),
    ) { content() }
}

/** The folded remainder of a long tally. Its points count; its lines open on a tap. */
@Composable
private fun MoreRow(
    points: Int,
    label: String,
    open: Boolean,
    figure: Color,
    text: Color,
    sub: Color,
    rule: Color,
    lang: String,
    onClick: () -> Unit,
) {
    val turn by animateFloatAsState(if (open) 90f else 0f, kSpring(), label = "moreTurn")
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = Touch.min)
            .clickable(role = Role.Button, onClickLabel = if (open) "Hide" else "Show all", onClick = onClick),
    ) {
        Box(Modifier.width(64.dp).padding(top = Space.sm), contentAlignment = Alignment.TopEnd) {
            Text("+$points", style = KType.points, color = figure.copy(alpha = 0.8f), maxLines = 1)
        }
        Spacer(Modifier.width(Space.sm))
        Box(Modifier.width(Stroke.hair).fillMaxHeight().background(rule))
        Row(
            Modifier.weight(1f).padding(start = Space.md, top = Space.sm + 2.dp, bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = KType.evidence.script(lang), color = text)
                Text(if (open) "Hide" else "Show all", style = KType.utility, color = sub)
            }
            Icon(
                painterResource(R.drawable.ic_chevron),
                null,
                tint = sub,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn },
            )
        }
    }
}

@Composable
private fun TallyRow(line: EvidenceLine, figure: Color, text: Color, sub: Color, rule: Color, lang: String) {
    val sign = if (line.points < 0) "−${-line.points}" else "+${line.points}"
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription = "$sign points. ${line.text}. Seen by ${line.source}."
            },
    ) {
        Box(Modifier.width(64.dp).padding(top = Space.sm), contentAlignment = Alignment.TopEnd) {
            Text(sign, style = KType.points, color = if (line.points < 0) sub else figure, maxLines = 1)
        }
        Spacer(Modifier.width(Space.sm))
        Box(Modifier.width(Stroke.hair).fillMaxHeight().background(rule))
        Column(
            Modifier
                .weight(1f)
                .padding(start = Space.md, top = Space.sm + 2.dp, bottom = Space.sm),
        ) {
            Text(line.text, style = KType.evidence.script(lang), color = text)
            Spacer(Modifier.height(Space.xxs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_device), null, tint = sub, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(line.source, style = KType.utility.script(lang), color = sub)
            }
        }
    }
}

@Composable
private fun TallyTotal(
    running: Int,
    locked: Boolean,
    figure: Color,
    text: Color,
    sub: Color,
    caption: String,
    note: String,
    lang: String,
) {
    val count by animateFloatAsState(
        running.toFloat(),
        tween(motion(Motion.METER), easing = Motion.enter),
        label = "total",
    )
    // The lock: the total takes its tone colour and settles once. Until then it is the
    // neutral colour of a sum still being added up.
    val colour by animateColorAsState(if (locked) figure else sub, tween(motion(Motion.STANDARD)), label = "totalInk")
    val stamp = remember { Animatable(1f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(locked) {
        if (locked && !reduce) {
            stamp.snapTo(1.08f)
            stamp.animateTo(1f, spring(Motion.LOCK_DAMPING, Motion.LOCK_STIFFNESS))
        }
    }
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Total $running of 100. $caption." },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.widthIn(min = 64.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                count.roundToInt().toString(),
                style = KType.total,
                color = colour,
                modifier = Modifier.graphicsLayer {
                    scaleX = stamp.value
                    scaleY = stamp.value
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                },
            )
        }
        Spacer(Modifier.width(Space.md))
        Column {
            Text(caption, style = MaterialTheme.typography.titleMedium.script(lang), color = text)
            Text(note, style = KType.utility.script(lang), color = sub)
        }
    }
}

/**
 * The scale a score sits on. Replaces the dial: instead of a number in a ring, a segmented
 * bar that shows *where the lines are* — the score at which Kaavalu starts to caution, to
 * warn, to call the family — and where this call or notice has landed against them.
 */
@Composable
fun SignalScale(value: Int, spec: ScaleSpec, onDark: Boolean = false, modifier: Modifier = Modifier) {
    val at by animateFloatAsState(
        value.coerceIn(0, 100) / 100f,
        kSpring(),
        label = "scale",
    )
    val zones = if (onDark) {
        listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.28f))
    } else {
        listOf(Guard100, Caution100, Alarm100)
    }
    val fills = if (onDark) listOf(Guard300, Gold300, Gold) else listOf(Guard500, Caution500, Alarm)
    val needle = if (onDark) Color.White else Ink
    val marks = spec.marks
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spec.describe }) {
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val top = 10.dp.toPx()
            val h = 12.dp.toPx()
            val bounds = listOf(0f) + marks.take(2).map { it / 100f } + 1f
            for (z in 0 until bounds.size - 1) {
                val x0 = size.width * bounds[z]
                val x1 = size.width * bounds[z + 1]
                drawRect(zones[z.coerceAtMost(2)], Offset(x0, top), Size(x1 - x0, h))
                val fillTo = (size.width * at).coerceIn(x0, x1)
                if (fillTo > x0) drawRect(fills[z.coerceAtMost(2)], Offset(x0, top), Size(fillTo - x0, h))
            }
            marks.forEach { m ->
                val x = size.width * m / 100f
                drawRect(needle.copy(alpha = 0.55f), Offset(x - 1.dp.toPx(), top - 3.dp.toPx()), Size(2.dp.toPx(), h + 6.dp.toPx()))
            }
            // The needle: a notch above the bar where the score sits.
            val x = size.width * at
            val path = Path().apply {
                moveTo(x - 6.dp.toPx(), 0f)
                lineTo(x + 6.dp.toPx(), 0f)
                lineTo(x, 8.dp.toPx())
                close()
            }
            drawPath(path, needle)
        }
        Spacer(Modifier.height(Space.xxs))
        val label = if (onDark) Color.White.copy(alpha = 0.72f) else Muted
        Layout(content = { marks.forEach { Text("$it", style = KType.utility, color = label) } }) { ms, c ->
            val ps = ms.map { it.measure(c.copy(minWidth = 0)) }
            val w = c.maxWidth
            layout(w, ps.maxOfOrNull { it.height } ?: 0) {
                ps.forEachIndexed { i, p ->
                    val x = (w * marks[i] / 100f - p.width / 2f).roundToInt().coerceIn(0, (w - p.width).coerceAtLeast(0))
                    p.place(x, 0)
                }
            }
        }
    }
}

/**
 * The head of a verdict: a sign and a statement. [locked] is when the evidence has all
 * arrived; the sign stamps down once, a small give that says "decided".
 */
@Composable
fun VerdictHead(
    sign: String,
    headline: String,
    tone: Tone,
    locked: Boolean,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    lang: String = "en",
    style: TextStyle = MaterialTheme.typography.displayMedium,
) {
    val stamp = remember { Animatable(1f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(locked) {
        if (locked && !reduce) {
            stamp.snapTo(1.12f)
            stamp.animateTo(1f, spring(Motion.LOCK_DAMPING, Motion.LOCK_STIFFNESS))
        }
    }
    Column(modifier) {
        SignTag(
            sign,
            container = tone.solid(onDark),
            content = tone.onSolid(onDark),
            modifier = Modifier.graphicsLayer {
                scaleX = stamp.value
                scaleY = stamp.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            },
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            headline,
            style = style.script(lang),
            color = if (onDark) Color.White else tone.ink(),
            modifier = Modifier.semantics { heading() },
        )
    }
}

// ── Working states ──────────────────────────────────────────────────────────────────────

/**
 * The reading sweep. A band of gold light moving down over whatever is being read — the
 * actual photo, not a placeholder — so "reading" is something the user can watch happen.
 * Under reduced motion it is a still band across the middle.
 */
@Composable
fun BoxScope.ReadingSweep(colour: Color = GoldDeep) {
    val still = LocalReduceMotion.current
    val y = if (still) 0.5f else {
        val t = rememberInfiniteTransition(label = "sweep")
        val v by t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
            label = "sweepY",
        )
        v
    }
    Canvas(Modifier.matchParentSize()) {
        val cy = size.height * y
        val band = 56.dp.toPx()
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, colour.copy(alpha = 0.28f), Color.Transparent),
                startY = cy - band,
                endY = cy + band,
            ),
        )
        drawRect(colour, Offset(0f, cy - 1.dp.toPx()), Size(size.width, 2.dp.toPx()))
    }
}

/** Viewfinder corners. Drawn, not boxed: the frame is implied by its four corners. */
@Composable
fun BoxScope.FrameCorners(colour: Color = Ink2, inset: Dp = Space.md) {
    Canvas(Modifier.matchParentSize().padding(inset)) {
        val l = 22.dp.toPx()
        val w = 3.dp.toPx()
        val c = colour
        // top-left
        drawRect(c, Offset(0f, 0f), Size(l, w)); drawRect(c, Offset(0f, 0f), Size(w, l))
        // top-right
        drawRect(c, Offset(size.width - l, 0f), Size(l, w)); drawRect(c, Offset(size.width - w, 0f), Size(w, l))
        // bottom-left
        drawRect(c, Offset(0f, size.height - w), Size(l, w)); drawRect(c, Offset(0f, size.height - l), Size(w, l))
        // bottom-right
        drawRect(c, Offset(size.width - l, size.height - w), Size(l, w))
        drawRect(c, Offset(size.width - w, size.height - l), Size(w, l))
    }
}

/** Shared insets for scrolling screens. */
val ScreenPadding = PaddingValues(start = Space.gutter, end = Space.gutter, top = Space.lg, bottom = Space.xxxl)

/** A full-height scroll column with the app's gutter. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = Space.section,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}
