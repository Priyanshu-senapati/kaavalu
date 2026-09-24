package com.dasen.kaavalu.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.core.Contribution
import com.dasen.kaavalu.core.Escalation
import com.dasen.kaavalu.core.RiskConfig
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.core.Tier

/**
 * Home. One focal point — the plate that says whether this phone is being watched — then,
 * only while a call is live, the evidence it is gathering; then what to do if a call feels
 * wrong; then the three rules that end every one of these calls.
 */
@Composable
fun Home(engine: RiskEngine, navigate: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val state by engine.state.collectAsStateWithLifecycle()
    val lang = remember { Prefs.language(ctx) }
    val statuses = Step.entries.map { Permissions.isGranted(ctx, it) }
    val armed = Permissions.essentialsGranted(ctx)

    ScreenColumn {
        Wordmark()

        ProtectionPlate(
            armed = armed,
            on = statuses.count { it },
            total = statuses.size,
            name = Prefs.userName(ctx),
            trusted = Prefs.trustedCount(ctx),
            onFix = { navigate(Screen.SETUP) },
        )

        // The live call only earns its space while something is happening.
        AnimatedVisibility(
            visible = state.sessionActive || state.score > 0,
            enter = fadeIn(tween(motion(Motion.STANDARD))) + expandVertically(tween(motion(Motion.EMPHASIZED), easing = Motion.enter)),
            exit = fadeOut(tween(motion(Motion.QUICK))) + shrinkVertically(tween(motion(Motion.STANDARD))),
        ) {
            LiveCall(
                score = state.score,
                tier = state.tier,
                sessionActive = state.onCall,
                contributions = state.contributions,
                escalations = state.escalations,
                config = engine.config,
                lang = lang,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionTitle("If a call feels wrong")
            ActionStrip {
                ActionRow(
                    icon = R.drawable.ic_nav_scan,
                    verb = "Check a notice",
                    detail = "Photograph the letter, or pick the screenshot they sent",
                ) { navigate(Screen.SCAN) }
                HRule(Modifier.padding(start = 64.dp))
                ActionRow(
                    icon = R.drawable.ic_nav_ask,
                    verb = "Ask Kaavalu",
                    detail = "Say what the caller told you and hear the answer",
                ) { navigate(Screen.ASK) }
                HRule(Modifier.padding(start = 64.dp))
                ActionRow(
                    icon = R.drawable.ic_phone,
                    verb = "Cyber-crime helpline",
                    detail = "Free, national. Call it to report the fraud",
                    tone = Tone.Danger,
                    trailing = {
                        Text("1930", style = KType.dial, color = Alarm)
                    },
                ) { dialNumber(ctx, "1930") }
                HRule(Modifier.padding(start = 64.dp))
                ActionRow(
                    icon = R.drawable.ic_nav_scan,
                    verb = "Recent calls",
                    detail = recentDetail(ctx),
                ) { navigate(Screen.HISTORY) }
                HRule(Modifier.padding(start = 64.dp))
                ActionRow(
                    icon = R.drawable.ic_warning,
                    verb = "I already sent money",
                    detail = "What to do in the next hour, in order",
                    tone = Tone.Danger,
                ) { navigate(Screen.RECOVER) }
            }
        }

        RulesWell()
    }
}

/**
 * Whether the arming animation still owes the user a play. Process-scoped on purpose: it must
 * survive both a tab switch (which destroys Home's composition) and a rotation (which destroys
 * the Activity), because in neither case has protection just been turned on.
 */
internal object ShieldLanding {
    private var played = false

    /** Setup is starting again, so arming is a moment again. */
    fun rearm() {
        played = false
    }

    fun consume(armed: Boolean): Boolean {
        if (!armed || played) return false
        played = true
        return true
    }
}

/**
 * The state of the app, readable from across the room. Green and still when protection is
 * on, amber with one clear action when it is not. Never oxblood: a missing permission is a
 * job to do, not a scam.
 */
@Composable
private fun ProtectionPlate(
    armed: Boolean,
    on: Int,
    total: Int,
    name: String,
    trusted: Int,
    onFix: () -> Unit,
) {
    val colour by animateColorAsState(
        if (armed) Guard else Caution100,
        tween(motion(Motion.EMPHASIZED), easing = Motion.standard),
        label = "plate",
    )
    // Arming is a moment: the shield draws itself up the plate once per process.
    val reduce = LocalReduceMotion.current
    val first = remember(armed) { ShieldLanding.consume(armed) }
    val reveal = remember { Animatable(if (first && !reduce) 0f else 1f) }
    LaunchedEffect(armed) {
        if (first && !reduce) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(900, easing = Motion.enter))
        } else {
            reveal.snapTo(1f)
        }
    }

    Plate(
        colour = colour,
        watermark = if (armed) Color.White.copy(alpha = 0.09f) else Caution.copy(alpha = 0.10f),
        reveal = reveal.value,
        depth = if (armed) 0.18f else 0.03f,
    ) {
        val inMs = motion(Motion.STANDARD)
        val lag = motion(80)
        val outMs = motion(Motion.QUICK)
        AnimatedContent(
            targetState = armed,
            transitionSpec = { fadeIn(tween(inMs, delayMillis = lag)) togetherWith fadeOut(tween(outMs)) },
            label = "plateContent",
        ) { isArmed ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (isArmed) {
                    SignTag("Protected", container = Gold, content = Guard900, live = true)
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        "Watching over $name",
                        style = MaterialTheme.typography.displaySmall,
                        color = Paper,
                        modifier = Modifier.widthIn(max = 300.dp),
                    )
                    Text(
                        "Every call from a number that isn’t in the contacts is checked, on this phone.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Guard100,
                    )
                    HRule(Modifier.padding(top = Space.xxs), colour = Color.White.copy(alpha = 0.16f))
                    Row(Modifier.semantics(mergeDescendants = true) {}) {
                        PlateFact("$on/$total", "safeguards on", Modifier.weight(1f))
                        PlateFact("$trusted", if (trusted == 1) "trusted number" else "trusted numbers", Modifier.weight(1f))
                    }
                } else {
                    SignTag("Not protected", container = Caution, content = Color.White)
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        "Kaavalu can’t watch calls yet",
                        style = MaterialTheme.typography.displaySmall,
                        color = Caution900,
                    )
                    Text(
                        "${total - on} of $total safeguards are off. It takes a minute to turn them on.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Caution900,
                    )
                    Spacer(Modifier.height(Space.xs))
                    BigAction("Turn them on", icon = R.drawable.ic_arrow, critical = true, onClick = onFix)
                }
            }
        }
    }
}

@Composable
private fun PlateFact(figure: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(figure, style = KType.dial.copy(fontSize = KType.dial.fontSize * 0.8f), color = Gold)
        Spacer(Modifier.width(Space.xs))
        Text(label, style = KType.utility, color = Guard100, modifier = Modifier.padding(bottom = 4.dp))
    }
}

/** What each tier means, in words a person would use. */
private fun tierWords(tier: Tier, active: Boolean): Pair<String, String> = when (tier) {
    Tier.CALM -> (if (active) "Unknown caller" else "Call ended") to
        (if (active) "Nothing unusual yet" else "Still watching for a while")
    Tier.WATCH -> "Caution" to "Kaavalu is paying attention to this call"
    Tier.INTERRUPT -> "Warning shown" to "This call looks like the scam"
    Tier.GUARDIAN -> "Family alerted" to "Your family has been sent a message"
}

private fun toneOf(tier: Tier) = when (tier) {
    Tier.CALM -> Tone.Safe
    Tier.WATCH -> Tone.Checking
    else -> Tone.Danger
}

/** The thresholds the engine acts on, as a scale the tally can be read against. */
internal fun engineScale(c: RiskConfig, score: Int) = ScaleSpec(
    marks = listOf(c.watchAt, c.interruptAt, c.guardianAt),
    describe = "Score $score of 100. Caution from ${c.watchAt}, warning from ${c.interruptAt}, " +
        "family alerted from ${c.guardianAt}.",
)

internal fun contributionLines(contributions: List<Contribution>, lang: String) = contributions.map {
    EvidenceLine(it.points, Copy.reasonFor(lang, it.key, it.arg), Copy.sourceFor(lang, it.key))
}

/** The live call: what Kaavalu has seen so far, adding up in front of you. */
@Composable
private fun LiveCall(
    score: Int,
    tier: Tier,
    sessionActive: Boolean,
    contributions: List<Contribution>,
    escalations: List<Escalation>,
    config: RiskConfig,
    lang: String,
) {
    val tone = toneOf(tier)
    val (sign, headline) = tierWords(tier, sessionActive)
    val shown = rememberTicker(contributions.size + escalations.size)
    Sheet(padding = Space.lg, spacing = Space.md) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SignTag(
                if (sessionActive) "Live call" else "After the call",
                container = tone.solid(),
                content = tone.onSolid(),
                live = sessionActive,
            )
            Spacer(Modifier.width(Space.sm))
            Text(sign, style = MaterialTheme.typography.titleSmall, color = tone.ink())
        }
        Text(headline, style = MaterialTheme.typography.headlineMedium, color = Ink)
        if (contributions.isNotEmpty()) {
            // On Home the question is "how did this build up?", not "what is each line
            // worth?", so the total stands alone and the timeline tells the story under it.
            LiveTotal(score, tone, lang)
            SignalScale(score, engineScale(config, score))
            HRule(Modifier.padding(vertical = Space.xs))
            Timeline(
                contributions = contributions,
                escalations = escalations,
                lang = lang,
                scale = config.timeScale,
                revealed = shown,
            )
        }
    }
}

/** The score, set large, with the same caption the tally uses. */
@Composable
private fun LiveTotal(score: Int, tone: Tone, lang: String) {
    val count by androidx.compose.animation.core.animateFloatAsState(
        score.toFloat(),
        tween(motion(Motion.METER), easing = Motion.enter),
        label = "liveTotal",
    )
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${count.toInt()}", style = KType.total, color = tone.figure())
        Spacer(Modifier.width(Space.md))
        Column {
            Text(Copy.evidenceTotal(lang), style = MaterialTheme.typography.titleMedium.script(lang), color = Ink)
            Text("/ 100", style = KType.utility, color = Muted)
        }
    }
}

/**
 * The three rules. In the same words the warning uses, so the rule is already familiar by
 * the time the scam call actually arrives. Set large and numbered, pressed into the page:
 * this is the part to remember, not the part to tap.
 */
@Composable
private fun RulesWell() {
    Well(padding = Space.xl) {
        SectionTitle("Three rules")
        Text(
            "If a caller breaks any one of these, hang up.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
        Spacer(Modifier.height(Space.xs))
        listOf(
            "No agency arrests anyone over a phone or video call. Ever.",
            "No officer will ever ask you to keep it from your family.",
            "No government account needs your money “for verification”.",
        ).forEachIndexed { i, rule ->
            if (i > 0) HRule(colour = Line.copy(alpha = 0.6f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.xs).semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "${i + 1}",
                    style = KType.total.copy(fontSize = KType.dial.fontSize, lineHeight = KType.dial.lineHeight),
                    color = Guard500,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(36.dp),
                )
                Spacer(Modifier.width(Space.md))
                Text(
                    rule,
                    style = KType.evidence.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight),
                    color = Ink,
                    modifier = Modifier.weight(1f).padding(top = 4.dp),
                )
            }
        }
    }
}

/** "3 calls from unknown numbers this week", or what the history is for when it is empty. */
private fun recentDetail(ctx: android.content.Context): String {
    val calls = com.dasen.kaavalu.core.CallHistory.decode(Prefs.history(ctx))
    val week = System.currentTimeMillis() - 7 * 24 * 60 * 60_000L
    val recent = calls.count { it.startedAt >= week }
    return when {
        calls.isEmpty() -> "Every call from an unknown number, with its report"
        recent == 1 -> "1 call from an unknown number this week"
        recent > 1 -> "$recent calls from unknown numbers this week"
        else -> "${calls.size} earlier ${if (calls.size == 1) "call" else "calls"} from unknown numbers"
    }
}

internal fun dialNumber(ctx: android.content.Context, number: String) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
