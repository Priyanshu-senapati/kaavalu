package com.dasen.kaavalu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.core.AppKind
import com.dasen.kaavalu.core.Channel
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.SignalBus
import com.dasen.kaavalu.core.Tier

/**
 * What makes the live demo reliable. Signals are injected by hand, and "demo time" makes one
 * real second count as one minute so the 20-minute threshold fires in 20 seconds.
 *
 * Say both of those out loud when you present. A jury forgives compressed time; it does not
 * forgive finding out afterwards.
 */
@Composable
fun DemoConsole(engine: RiskEngine, onBack: () -> Unit) {
    val lang = Prefs.language(LocalContext.current)
    val s by engine.state.collectAsStateWithLifecycle()
    var demoTime by remember { mutableStateOf(engine.config.timeScale > 1.0) }

    val tone = when (s.tier) {
        Tier.CALM -> Tone.Safe
        Tier.WATCH -> Tone.Checking
        else -> Tone.Danger
    }
    val shown = rememberTicker(s.contributions.size)

    ScreenColumn {
        ScreenTitle(
            title = "Demo console",
            lead = "Signals injected by hand, on compressed time.",
            onBack = onBack,
            backLabel = "Back to Setup",
        )

        Sheet(padding = Space.xl, spacing = Space.md) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignTag(
                    s.tier.name.lowercase(),
                    container = tone.solid(),
                    content = tone.onSolid(),
                    live = s.sessionActive,
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    if (s.sessionActive) "Call in progress" else "No call",
                    style = MaterialTheme.typography.titleSmall,
                    color = Muted,
                )
            }
            if (s.contributions.isEmpty()) {
                Text("No signals yet", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Text("Inject one below and watch it add up.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                SignalScale(s.score, engineScale(engine.config, s.score))
            } else {
                EvidenceTally(
                    lines = contributionLines(s.contributions, lang),
                    total = s.score,
                    tone = tone,
                    revealed = shown,
                    lang = lang,
                    totalCaption = Copy.evidenceTotal(lang),
                    capLabel = Copy.capped(lang),
                    scale = engineScale(engine.config, s.score),
                )
            }
        }

        Well(padding = Space.lg) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Demo time", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text(
                        "One real second counts as one minute.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
                Switch(
                    checked = demoTime,
                    onCheckedChange = {
                        demoTime = it
                        engine.config = engine.config.copy(timeScale = if (it) 60.0 else 1.0)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Paper,
                        checkedTrackColor = Guard,
                        uncheckedThumbColor = Muted,
                        uncheckedTrackColor = Sheet,
                        uncheckedBorderColor = Line,
                    ),
                    modifier = Modifier.semantics { contentDescription = "Demo time" },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionTitle("Inject a signal")
            ActionStrip {
                Inject("Unknown WhatsApp video call", "Video, and not in contacts", "+40") {
                    SignalBus.emit(
                        Signal.CallStarted(
                            number = "+91 98xxx xxx01",
                            channel = Channel.WHATSAPP,
                            isKnown = false,
                            isVideo = true,
                        ),
                    )
                }
                Divider()
                Inject("Unknown cellular call", "Not in contacts, unverified by the network", "+25") {
                    SignalBus.emit(
                        Signal.CallStarted(
                            number = "+91 80xxx xxx44",
                            channel = Channel.CELLULAR,
                            isKnown = false,
                            unverified = true,
                        ),
                    )
                }
                Divider()
                Inject("Open a UPI app", "A payment app during the call", "+30") {
                    SignalBus.emit(Signal.SensitiveAppOpened("com.phonepe.app", AppKind.PAYMENT))
                }
                Divider()
                Inject("Open a screen-share app", "Remote access during the call", "+35") {
                    SignalBus.emit(
                        Signal.SensitiveAppOpened("com.anydesk.anydeskandroid", AppKind.REMOTE_ACCESS),
                    )
                }
                Divider()
                Inject("Flag a notice", "Adds to the next call, for 48 hours", "+20") {
                    SignalBus.emit(Signal.NoticeFlagged(70, listOf("Threatens immediate arrest.")))
                }
                Divider()
                Inject("End the call", "Starts the 30-minute watch after a call", "—") {
                    SignalBus.emit(Signal.CallEnded)
                }
            }
        }

        // The family member who set this up has never seen the thing they installed actually
        // fire. One button that shows them is worth more than any number of screenshots.
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionTitle("Show the family what a warning looks like")
            BigAction("Run the full warning now", style = ActionStyle.Danger, icon = R.drawable.ic_warning, critical = true) {
                SignalBus.emit(
                    Signal.CallStarted(
                        number = "+91 98xxx xxx01",
                        channel = Channel.WHATSAPP,
                        isKnown = false,
                        isVideo = true,
                    ),
                )
                SignalBus.emit(Signal.SensitiveAppOpened("com.phonepe.app", AppKind.PAYMENT))
            }
            BigAction("Reset", style = ActionStyle.Secondary) { engine.reset() }
        }
    }
}

@Composable
private fun Divider() = HRule(Modifier.padding(start = 64.dp))

/** A demo control that says what it will do to the score, so the presenter never guesses. */
@Composable
private fun Inject(label: String, effect: String, points: String, onClick: () -> Unit) {
    ActionRow(
        icon = R.drawable.ic_nav_demo,
        verb = label,
        detail = effect,
        tone = Tone.Checking,
        trailing = { Text(points, style = KType.points, color = Caution) },
        onClick = onClick,
    )
}
