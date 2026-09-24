package com.dasen.kaavalu.respond

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.KaavaluApp
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.SignalBus
import com.dasen.kaavalu.ui.ActionStyle
import com.dasen.kaavalu.ui.Alarm
import com.dasen.kaavalu.ui.Alarm900
import com.dasen.kaavalu.ui.BigAction
import com.dasen.kaavalu.ui.EvidenceTally
import com.dasen.kaavalu.ui.Gold
import com.dasen.kaavalu.ui.KType
import com.dasen.kaavalu.ui.KaavaluTheme
import com.dasen.kaavalu.ui.Motion
import com.dasen.kaavalu.ui.Space
import com.dasen.kaavalu.ui.Stroke
import com.dasen.kaavalu.ui.Tone
import com.dasen.kaavalu.ui.Touch
import com.dasen.kaavalu.ui.contributionLines
import com.dasen.kaavalu.ui.engineScale
import com.dasen.kaavalu.ui.motion
import com.dasen.kaavalu.ui.rememberTicker
import com.dasen.kaavalu.ui.script
import com.dasen.kaavalu.ui.tallyRows

/**
 * The interrupt. The whole phone changes state: the window is oxblood before Compose draws,
 * and the first frame already carries the headline, the body and the three ways out. Only
 * what explains the warning — the evidence — arrives after it, line by line, adding up to
 * the score in the corner.
 *
 * This is the one screen in the app that has to work on a frightened seventy-year-old holding
 * the phone at arm's length while a stranger shouts at them. It never claims certainty: it
 * shows the points and says what they mean, and the three actions never scroll away.
 */
class InterruptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val engine = KaavaluApp.engineOf(this)
        val lang = Prefs.language(this)

        setContent {
            KaavaluTheme {
                val s by engine.state.collectAsStateWithLifecycle()

                // The evidence waits a beat, so the eye lands on the headline first. The
                // headline itself waits for nothing.
                var armed by remember { mutableStateOf(false) }
                val beat = motion(260).toLong()
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(beat)
                    armed = true
                }
                val shown = rememberTicker(if (armed) s.contributions.size else 0, step = 160L)
                val rule by animateFloatAsState(
                    if (armed) 1f else 0f,
                    tween(motion(520), easing = Motion.enter),
                    label = "warnRule",
                )

                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Alarm900, Alarm, Alarm)))
                        .statusBarsPadding(),
                ) {
                    Box(Modifier.weight(1f)) {
                        // The mark, cut large into the field. Static: the warning is not decoration.
                        Icon(
                            painterResource(R.drawable.ic_shield_k),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.06f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 72.dp, y = (-24).dp)
                                .size(300.dp),
                        )
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = Space.xl),
                            verticalArrangement = Arrangement.spacedBy(Space.md),
                        ) {
                            Spacer(Modifier.height(Space.lg))
                            MastRow(s.score)
                            Spacer(Modifier.height(Space.xs))

                            Text(
                                Copy.interruptTitle(lang),
                                style = MaterialTheme.typography.displayLarge.script(lang),
                                color = Color.White,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                Copy.interruptBody(lang),
                                style = MaterialTheme.typography.bodyLarge.script(lang),
                                color = Color.White.copy(alpha = 0.94f),
                            )

                            // A gold rule draws across: the structural beat between "stop"
                            // and "here is why".
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(Stroke.signal)
                                    .drawBehind { drawRect(Gold, size = Size(size.width * rule, size.height)) },
                            )

                            Text(
                                Copy.whyHeading(lang, s.score),
                                style = MaterialTheme.typography.titleMedium.script(lang),
                                color = Gold,
                            )
                            EvidenceTally(
                                lines = contributionLines(s.contributions, lang),
                                total = s.score,
                                tone = Tone.Danger,
                                revealed = shown,
                                onDark = true,
                                lang = lang,
                                totalCaption = Copy.evidenceTotal(lang),
                                capLabel = Copy.capped(lang),
                                scale = engineScale(engine.config, s.score),
                            )

                            // The signals say what was seen. This says why it should worry you,
                            // which is the part a frightened person cannot work out alone. It
                            // arrives as the tally closes: it is the conclusion of the sum.
                            AnimatedVisibility(
                                visible = shown >= tallyRows(s.contributions.size),
                                enter = fadeIn(tween(motion(Motion.STANDARD))),
                            ) {
                                Text(
                                    Copy.whyThisMatters(lang),
                                    style = MaterialTheme.typography.titleMedium.script(lang),
                                    color = Gold,
                                    modifier = Modifier.padding(top = Space.xs),
                                )
                            }

                            Text(
                                Copy.uncertainty(lang),
                                style = MaterialTheme.typography.bodyMedium.script(lang),
                                color = Color.White.copy(alpha = 0.76f),
                            )
                            Spacer(Modifier.height(Space.md))
                        }
                    }

                    // Pinned, and never animated. At 100/100 there are five reasons, and when
                    // these scrolled with them "Call my family" left the screen at exactly the
                    // wrong moment. The way out must never wait for a spring to settle.
                    Column(
                        Modifier
                            .background(Alarm900)
                            .drawBehind {
                                drawRect(Color.White.copy(alpha = 0.16f), size = Size(size.width, 1.dp.toPx()))
                            }
                            .navigationBarsPadding()
                            .padding(horizontal = Space.xl, vertical = Space.md),
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        BigAction(
                            Copy.callFamily(lang),
                            style = ActionStyle.Paper,
                            icon = R.drawable.ic_family,
                            critical = true,
                            textStyle = MaterialTheme.typography.labelLarge.script(lang),
                        ) { Prefs.guardian(this@InterruptActivity)?.let { dial(it) } }

                        BigAction(
                            Copy.callHelpline(lang),
                            style = ActionStyle.Gold,
                            icon = R.drawable.ic_phone,
                            critical = true,
                            textStyle = MaterialTheme.typography.labelLarge.script(lang),
                        ) { dial("1930") }

                        QuietAction(Copy.knownPerson(lang), lang) {
                            s.caller?.let { Prefs.trust(this@InterruptActivity, it) }
                            SignalBus.emit(Signal.MarkedSafe)
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun dial(number: String) {
        runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))) }
    }
}

/** Who is speaking, and the number the evidence below adds up to. */
@Composable
private fun MastRow(score: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_shield_k),
            contentDescription = null,
            tint = Gold,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(Space.xs))
        Column(Modifier.weight(1f)) {
            Text("Scam warning".uppercase(), style = KType.sign, color = Gold)
            Text("ಕಾವಲು · Kaavalu", style = KType.utility.script("kn"), color = Color.White.copy(alpha = 0.76f))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$score", style = KType.points.copy(fontSize = KType.dial.fontSize, lineHeight = KType.dial.lineHeight), color = Gold)
            Text("/100", style = KType.utility, color = Color.White.copy(alpha = 0.76f), modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

/** The quiet way out, for the case where the caller really is someone known. */
@Composable
private fun QuietAction(label: String, lang: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Touch.min)
            .background(if (pressed) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.script(lang),
            color = Color.White.copy(alpha = 0.88f),
            textAlign = TextAlign.Center,
        )
    }
}
