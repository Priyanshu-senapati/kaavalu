package com.dasen.kaavalu.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
 */
@Composable
fun Timeline(
    contributions: List<Contribution>,
    escalations: List<Escalation>,
    lang: String,
    scale: Double = 1.0,
    modifier: Modifier = Modifier,
) {
    if (contributions.isEmpty()) return
    val ctx = LocalContext.current
    val startedAt = contributions.first().at

    // Signals before actions when they share a millisecond: the phone saw something, and
    // then Kaavalu reacted to it. Showing the reaction first would read as a false cause.
    val entries = (contributions.map(Entry::Signal) + escalations.map(Entry::Acted))
        .sortedWith(compareBy({ it.at }, { it is Entry.Acted }))

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(
            Copy.timelineHeading(lang),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "The call began at " + DateFormat.getTimeFormat(ctx).format(Date(startedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        entries.forEachIndexed { index, entry ->
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

@Composable
private fun TimelineRow(
    entry: Entry,
    lang: String,
    into: String,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val acted = entry is Entry.Acted
    val colour = when {
        entry is Entry.Acted -> when (entry.escalation.tier) {
            Tier.GUARDIAN, Tier.INTERRUPT -> Alarm
            else -> Caution
        }
        else -> Guard
    }

    val label = when (entry) {
        is Entry.Signal -> Copy.reasonFor(lang, entry.contribution.key, entry.contribution.arg)
        is Entry.Acted -> Copy.escalationLabel(lang, entry.escalation.tier.name)
    }
    val detail = when (entry) {
        is Entry.Signal -> Copy.sourceFor(lang, entry.contribution.key)
        is Entry.Acted -> null
    }
    Row(Modifier.fillMaxWidth().semantics { contentDescription = "$into into the call, $label" }) {

        // The thread. Drawn as three pieces so the first and last rows do not trail a
        // line into nothing.
        Column(
            Modifier.width(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(if (isFirst) 6.dp else 10.dp)
                    .background(if (isFirst) Color.Transparent else Line),
            )
            Box(
                Modifier
                    .size(if (acted) 13.dp else 9.dp)
                    .clip(CircleShape)
                    .background(colour),
            )
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(if (acted) 30.dp else 26.dp)
                        .background(Line),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.padding(bottom = if (isLast) 0.dp else 14.dp)) {
            // Tabular figures so the times line up down the column rather than jittering.
            Text(
                into,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = "tnum",
                ),
                color = Muted,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (acted) colour else Ink,
                fontWeight = if (acted) FontWeight.Bold else FontWeight.Normal,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}
