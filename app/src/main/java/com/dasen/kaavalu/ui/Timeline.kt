package com.dasen.kaavalu.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.core.Contribution
import com.dasen.kaavalu.core.Escalation
import com.dasen.kaavalu.core.Tier
import java.util.Date

/**
 * How the situation built up, in the order it happened.
 *
 * A score says "you are at 85". This says how it got there, which is the thing a family
 * member or a sceptic actually wants to see. Every contribution has carried a timestamp
 * since the first version of the engine; nothing rendered it until now.
 *
 * Signals the phone noticed and actions Kaavalu took are shown on one thread, because the
 * interesting part of the story is how they interleave.
 *
 * Set as a sibling of the evidence tally: the same narrow numeral column on the left, but
 * holding time into the call instead of points, and a thread instead of a margin rule. The
 * thread is the one vertical line in the app that means "then".
 */
private sealed interface Entry {
    val at: Long

    data class Signal(val contribution: Contribution) : Entry {
        override val at: Long get() = contribution.at
    }

    data class Acted(val escalation: Escalation) : Entry {
        override val at: Long get() = escalation.at
    }
}

/**
 * [scale] is the engine's time scale. Under demo time a whole call happens inside one real
 * minute, so wall-clock stamps all read the same and the timeline says nothing. Positions
 * are shown as time *into the call* instead, which is both what a viewer wants to know
 * and what stays true when the clock is compressed. At scale 1.0 it is simply real elapsed
 * time, so there is no second mode to reason about.
 *
 * [revealed] follows the same rule as the tally: entries arrive one at a time while a live
 * call is still gathering them.
 */
@Composable
fun Timeline(
    contributions: List<Contribution>,
    escalations: List<Escalation>,
    lang: String,
    scale: Double = 1.0,
    modifier: Modifier = Modifier,
    revealed: Int = Int.MAX_VALUE,
) {
    if (contributions.isEmpty()) return
    val ctx = LocalContext.current
    val startedAt = contributions.first().at

    // Signals before actions when they share a millisecond: the phone saw something, and
    // then Kaavalu reacted to it. Showing the reaction first would read as a false cause.
    val entries = (contributions.map(Entry::Signal) + escalations.map(Entry::Acted))
        .sortedWith(compareBy({ it.at }, { it is Entry.Acted }))

    Column(modifier.fillMaxWidth()) {
        Text(
            Copy.timelineHeading(lang),
            style = MaterialTheme.typography.titleMedium.script(lang),
            color = Ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "The call began at " + DateFormat.getTimeFormat(ctx).format(Date(startedAt)),
            style = KType.utility,
            color = Muted,
        )
        Spacer(Modifier.height(Space.sm))
        entries.forEachIndexed { index, entry ->
            AnimatedVisibility(
                visible = index < revealed,
                enter = fadeIn(tween(motion(Motion.STANDARD))) +
                    slideInVertically(kSpring(Choreo.REASON_DAMPING, Choreo.REASON_STIFFNESS)) { -it / 4 },
                exit = fadeOut(tween(motion(Motion.QUICK))),
            ) {
                TimelineRow(
                    entry = entry,
                    lang = lang,
                    into = intoCall(entry.at - startedAt, scale),
                    isFirst = index == 0,
                    isLast = index == entries.lastIndex,
                )
            }
        }
    }
}

/** Elapsed call time as m:ss, or h:mm:ss once a call has run past an hour, as these do. */
internal fun intoCall(elapsedMs: Long, scale: Double): String {
    val seconds = ((elapsedMs.coerceAtLeast(0L)) * scale / 1000.0).toLong()
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%d:%02d".format(m, s)
    }
}

/** The tone of something Kaavalu did. Watching is attention; interrupting is danger. */
private fun toneOf(tier: Tier) = when (tier) {
    Tier.INTERRUPT, Tier.GUARDIAN -> Tone.Danger
    else -> Tone.Checking
}

@Composable
private fun TimelineRow(
    entry: Entry,
    lang: String,
    into: String,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val acted = entry is Entry.Acted
    val tone = when (entry) {
        is Entry.Acted -> toneOf(entry.escalation.tier)
        is Entry.Signal -> Tone.Neutral
    }
    val label = when (entry) {
        is Entry.Signal -> Copy.reasonFor(lang, entry.contribution.key, entry.contribution.arg)
        is Entry.Acted -> Copy.escalationLabel(lang, entry.escalation.tier.name)
    }
    val detail = when (entry) {
        is Entry.Signal -> Copy.sourceFor(lang, entry.contribution.key)
        is Entry.Acted -> null
    }
    val points = (entry as? Entry.Signal)?.contribution?.points

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("$into into the call. $label.")
                    points?.let { append(" Plus $it points.") }
                }
            },
    ) {
        // Time into the call, in the tally's numeral column so the two read as one family.
        Box(Modifier.width(56.dp).padding(top = Space.sm), contentAlignment = Alignment.TopEnd) {
            Text(
                into,
                style = KType.utility.copy(fontFamily = Latin.numeral, fontFeatureSettings = "tnum, lnum"),
                color = if (acted) tone.solid() else Muted,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Space.sm))

        // The thread. Drawn in three pieces so the first and last rows do not trail a line
        // into nothing. A signal is a small square on the thread; something Kaavalu did is
        // a larger solid block, because the app acting is the event the thread is about.
        Column(Modifier.width(18.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .width(Stroke.rule)
                    .height(Space.sm + if (acted) 2.dp else 5.dp)
                    .background(if (isFirst) Color.Transparent else Line),
            )
            Box(
                Modifier
                    .size(if (acted) 14.dp else 8.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(if (acted) tone.solid() else Ink2),
            )
            Box(
                Modifier
                    .width(Stroke.rule)
                    .weight(1f)
                    .background(if (isLast) Color.Transparent else Line),
            )
        }
        Spacer(Modifier.width(Space.sm))

        Row(Modifier.weight(1f).padding(top = Space.sm - 2.dp, bottom = if (isLast) 0.dp else Space.md)) {
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = (if (acted) MaterialTheme.typography.titleMedium else KType.evidence).script(lang),
                    color = if (acted) tone.ink() else Ink,
                )
                if (detail != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(detail, style = KType.utility.script(lang), color = Muted)
                }
            }
            if (points != null) {
                Spacer(Modifier.width(Space.sm))
                Text("+$points", style = KType.utility.copy(fontFamily = Latin.numeral, fontFeatureSettings = "tnum, lnum"), color = Ink2)
            }
        }
    }
}
