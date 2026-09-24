package com.dasen.kaavalu.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.core.Tier

@Composable
fun Home(engine: RiskEngine, navigate: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val state by engine.state.collectAsStateWithLifecycle()
    var lang by remember { mutableStateOf(Prefs.language(ctx)) }
    val statuses = Step.entries.map { Permissions.isGranted(ctx, it) }
    val live = statuses.count { it }
    val protectedNow = Permissions.essentialsGranted(ctx)

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Wordmark()

        ShieldCard(
            armed = protectedNow,
            live = live,
            total = Step.entries.size,
            name = Prefs.userName(ctx),
            onFix = { navigate(Screen.SETUP) },
        )

        // The live meter only earns its space while something is actually happening. When
        // nothing is, the screen says so in a sentence instead of showing a zeroed gauge.
        AnimatedVisibility(state.sessionActive || state.score > 0) {
            Panel(background = if (state.tier == Tier.CALM) Color.White else AlarmSoft) {
                val colour = when (state.tier) {
                    Tier.CALM -> Guard
                    Tier.WATCH -> Caution
                    else -> Alarm
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Risk ${state.score} / 100", style = MaterialTheme.typography.titleLarge)
                    Text(
                        state.tier.name.lowercase().replaceFirstChar(Char::uppercase),
                        color = colour,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Meter(state.score / 100f, colour)
                Text(
                    if (state.sessionActive) "Watching an unknown call right now."
                    else "The last call ended. Kaavalu keeps watching for a few minutes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                // The timeline replaces the flat list here: on the home screen the
                // question is "how did this build up?", not "what is the total?", and
                // the total is already the big number above.
                Timeline(
                    contributions = state.contributions,
                    escalations = state.escalations,
                    lang = lang,
                    scale = engine.config.timeScale,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Text("If something feels wrong", style = MaterialTheme.typography.titleMedium)

        ActionTile(
            icon = R.drawable.ic_nav_scan,
            title = "Check a notice",
            detail = "Photograph a warrant or a WhatsApp screenshot.",
            onClick = { navigate(Screen.SCAN) },
        )
        ActionTile(
            icon = R.drawable.ic_nav_ask,
            title = "Ask Kaavalu",
            detail = "Say what they told you. Get an answer out loud.",
            onClick = { navigate(Screen.ASK) },
        )
        ActionTile(
            icon = R.drawable.ic_phone,
            title = "Call 1930",
            detail = "The national cyber-crime helpline. Free, 24 hours.",
            tint = Alarm,
            onClick = {
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )

        RulesCard()

        Panel {
            Text("Warning language", style = MaterialTheme.typography.titleMedium)
            Text(
                "Everything Kaavalu says or writes follows this, not the phone's language.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Copy.languages.forEach { (code, label) ->
                    FilterChip(
                        selected = lang == code,
                        onClick = {
                            lang = code
                            Prefs.setLanguage(ctx, code)
                        },
                        label = { Text(label) },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Stat("$live/${Step.entries.size}", "permissions", Modifier.weight(1f))
            Stat("${Prefs.trustedCount(ctx)}", "trusted numbers", Modifier.weight(1f))
            Stat(
                Copy.languages.first { it.first == lang }.second,
                "warning language",
                Modifier.weight(1f),
            )
        }

        OutlinedButton(
            onClick = { navigate(Screen.DEMO) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open the demo console") }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ShieldCard(armed: Boolean, live: Int, total: Int, name: String, onFix: () -> Unit) {
    Panel(background = if (armed) GuardSoft else AlarmSoft) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BreathingShield(armed)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (armed) "Protection is on" else "Protection is not complete",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (armed) Guard else Alarm,
                )
                Text(
                    if (armed) "Watching every call to $name from a number that is not in the contacts."
                    else "${total - live} of $total permissions are still missing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
        }
        if (!armed) {
            BigAction("Finish setup", container = Alarm, icon = R.drawable.ic_warning, onClick = onFix)
        }
    }
}

/**
 * The part that makes this a thing the family keeps rather than a thing they installed once.
 * Three sentences, always on the home screen, in the same words the warning screen uses, so
 * the rule is already familiar by the time the scam call actually arrives.
 */
@Composable
private fun RulesCard() {
    Panel(background = Color(0xFF1F3D2B)) {
        Text(
            "Three rules that end every one of these calls",
            style = MaterialTheme.typography.titleMedium,
            color = Gold,
        )
        listOf(
            "No agency arrests anyone over a phone or video call. Ever.",
            "No officer will ever ask you to keep it from your family.",
            "No government account needs your money \"for verification\".",
        ).forEachIndexed { i, rule ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "${i + 1}",
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(22.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    rule,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
