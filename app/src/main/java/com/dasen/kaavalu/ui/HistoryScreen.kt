package com.dasen.kaavalu.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.core.CallHistory
import com.dasen.kaavalu.core.SessionRecord
import com.dasen.kaavalu.core.Tier
import com.dasen.kaavalu.core.peak
import com.dasen.kaavalu.core.spokenDuration
import com.dasen.kaavalu.report.CallReport
import java.text.DateFormat
import java.util.Date

/**
 * Recent calls from numbers that are not in the contacts, newest first, and each one's
 * report. Every such call is here, quiet or not: a call that scored 15 and a call that
 * reached the family are both things a family member wants to be able to look back at.
 */
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val calls = remember { CallHistory.decode(Prefs.history(ctx)) }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = calls.firstOrNull { it.startedAt == open }

    BackHandler(enabled = selected != null) { open = null }

    val inMs = motion(Motion.EMPHASIZED)
    val outMs = motion(Motion.QUICK)
    AnimatedContent(
        targetState = selected,
        transitionSpec = {
            val dir = if (targetState != null) 1 else -1
            (fadeIn(tween(inMs)) + slideInHorizontally(tween(inMs, easing = Motion.enter)) { dir * it / 12 })
                .togetherWith(fadeOut(tween(outMs)))
        },
        label = "history",
    ) { record ->
        if (record == null) {
            HistoryList(calls, onBack) { open = it.startedAt }
        } else {
            ScreenColumn {
                ScreenTitle(
                    title = dateTime(record.startedAt),
                    lead = record.caller?.takeIf { it.isNotBlank() } ?: "Number not shown",
                    onBack = { open = null },
                    backLabel = "Back to recent calls",
                )
                CallRecord(record)
            }
        }
    }
}

@Composable
private fun HistoryList(calls: List<SessionRecord>, onBack: () -> Unit, onOpen: (SessionRecord) -> Unit) {
    ScreenColumn {
        ScreenTitle(
            title = "Recent calls",
            lead = "Calls from numbers not in the contacts, newest first. Kept only on this phone.",
            onBack = onBack,
        )
        if (calls.isEmpty()) {
            Well(padding = Space.xl) {
                Text("No calls from unknown numbers yet", style = MaterialTheme.typography.titleLarge, color = Ink)
                Text(
                    "When one comes in it appears here, whether or not it looked like a scam, " +
                        "with a report of what the phone saw.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
        } else {
            ActionStrip {
                calls.forEachIndexed { i, r ->
                    if (i > 0) HRule(Modifier.padding(start = 104.dp))
                    HistoryRow(r) { onOpen(r) }
                }
            }
            Text(
                "The last ${CallHistory.CAP} are kept. Choosing “I know this person” on a warning removes that call.",
                style = KType.utility,
                color = Muted,
            )
        }
    }
}

/** What a call reached, in words. A quiet call is not called "safe": it was only quiet. */
private fun outcome(t: Tier): Pair<String, Tone> = when (t) {
    Tier.CALM -> "Quiet" to Tone.Neutral
    Tier.WATCH -> "Caution" to Tone.Checking
    Tier.INTERRUPT -> "Warned" to Tone.Danger
    Tier.GUARDIAN -> "Family told" to Tone.Danger
}

@Composable
private fun HistoryRow(r: SessionRecord, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val (word, tone) = outcome(r.peak)
    val signs = r.contributions.size
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .background(if (pressed) PaperDeep else Color.Transparent)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "25 Sep" over "5:00 am". A locale's short date ("25/09/2026") wrapped mid-year on a
        // real phone with larger text; day and month never do, and the year is in the detail.
        Column(Modifier.width(72.dp), horizontalAlignment = Alignment.End) {
            Text(
                shortDay(r.startedAt),
                style = KType.utility.copy(fontFamily = Latin.numeral, fontFeatureSettings = "tnum, lnum"),
                color = Ink2,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.startedAt)),
                style = KType.utility.copy(fontFamily = Latin.numeral, fontFeatureSettings = "tnum, lnum"),
                color = Muted,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                r.caller?.takeIf { it.isNotBlank() } ?: "Number not shown",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 1,
            )
            Text(
                listOfNotNull(
                    "${r.score} points",
                    "$signs ${if (signs == 1) "sign" else "signs"}",
                    r.durationMs?.let { spokenDuration(it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
        Spacer(Modifier.width(Space.sm))
        SignTag(word, container = tone.solid(), content = tone.onSolid())
    }
}

/**
 * One call, as a record: when, who, how far it got, the timeline, and the report to send
 * to a bank or the police. Shared by the history and the recovery screen.
 */
@Composable
fun CallRecord(record: SessionRecord) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Sheet(padding = Space.lg, spacing = Space.md) {
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                RecordFact("Began", dateTime(record.startedAt), Modifier.weight(1f))
                Spacer(Modifier.width(Space.md))
                RecordFact("Risk", "${record.score}/100", Modifier.weight(0.5f))
            }
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                RecordFact("Caller", record.caller?.takeIf { it.isNotBlank() } ?: "Not shown", Modifier.weight(1f))
                Spacer(Modifier.width(Space.md))
                RecordFact(
                    "Lasted",
                    record.durationMs?.let { spokenDuration(it) } ?: "Not recorded",
                    Modifier.weight(0.5f),
                )
            }
            HRule()
            Timeline(
                contributions = record.contributions,
                escalations = record.escalations,
                lang = "en",
                scale = record.timeScale,
            )
        }
        BigAction("Share the call report", icon = R.drawable.ic_arrow, critical = true) {
            shareReport(ctx, record)
        }
    }
}

@Composable
private fun RecordFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = KType.utility, color = Muted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

private fun shortDay(at: Long): String {
    val locale = java.util.Locale.getDefault()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM")
    return java.text.SimpleDateFormat(pattern, locale).format(Date(at))
}

private fun dateTime(at: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))

internal fun shareReport(ctx: android.content.Context, record: SessionRecord) {
    val text = CallReport.build(record, Prefs.userName(ctx))
    runCatching {
        ctx.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Kaavalu call report")
                    .putExtra(Intent.EXTRA_TEXT, text),
                "Share the call report",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
