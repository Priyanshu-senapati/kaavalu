package com.dasen.kaavalu.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.core.Tier
import com.dasen.kaavalu.respond.Guardian

/**
 * The home screen has one job beyond showing status: make the case that this app is needed.
 * A shield that just says "on" is something you uninstall in a week. So the screen shows
 * what it is guarding against, what it has actually done, and lets you feel the save before
 * you ever need it.
 */
@Composable
fun Home(
    engine: RiskEngine,
    modifier: Modifier = Modifier,
    onRerunSetup: () -> Unit,
    navigate: (Tab) -> Unit,
) {
    val ctx = LocalContext.current
    val state by engine.state.collectAsStateWithLifecycle()
    var lang by remember { mutableStateOf(Prefs.language(ctx)) }
    val missing = Step.entries.filter { !Permissions.isGranted(ctx, it) }
    val protected = Permissions.essentialsGranted(ctx)

    Column(
        modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Wordmark()

        ShieldCard(protected, missing.size, state.sessionActive, onRerunSetup)

        if (state.sessionActive || state.score > 0) {
            LiveRiskCard(state.score, state.tier, lang, state.contributions)
        } else {
            WhyItMattersCard()
            PracticeCard { navigate(Tab.DEMO) }
        }

        GuardianCard(ctx)
        LanguageCard(lang) { lang = it; Prefs.setLanguage(ctx, it) }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Guard),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_nav_home),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Kaavalu", style = MaterialTheme.typography.displaySmall, color = Guard)
            Text(
                "ಕಾವಲು  ·  the watch",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
    }
}

@Composable
private fun ShieldCard(
    protected: Boolean,
    missing: Int,
    watching: Boolean,
    onFix: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "glow",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = if (protected) Guard else Alarm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A live dot, not a static badge: the user can see it is awake.
                Box(
                    Modifier
                        .size(10.dp)
                        .alpha(if (protected) glow else 1f)
                        .clip(CircleShape)
                        .background(if (protected) Gold else Color.White),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (watching) "WATCHING A CALL NOW"
                    else if (protected) "ON GUARD" else "NOT PROTECTED",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (protected) Gold else Color.White,
                )
            }
            Text(
                if (protected) "Kaavalu is watching every unknown call"
                else "Setup is not finished",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text(
                if (missing == 0) {
                    "Nothing to do. It stays silent for people in your contacts."
                } else {
                    "$missing of 6 permissions still needed. Kaavalu cannot see " +
                        "everything it needs to until they are granted."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
            )
            if (missing > 0) {
                Button(
                    onClick = onFix,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Ink,
                    ),
                ) { Text("Finish setup") }
            }
        }
    }
}

/** The argument for the app, in numbers, on the screen you see every day. */
@Composable
private fun WhyItMattersCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Why this is on your phone",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Stat(Modifier.weight(1f), "Rs 1,935cr", "lost in 2024")
                Stat(Modifier.weight(1f), "1.2 lakh", "families hit")
                Stat(Modifier.weight(1f), "21x", "since 2022")
            }
            HorizontalDivider(color = Color(0xFFE8E2D8))
            Text(
                "There is no such thing as a digital arrest in Indian law. No officer will " +
                    "ever arrest you over a call, and none will ever tell you to keep it " +
                    "secret from your family.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, figure: String, caption: String) {
    // Weighted, not fixed-width: "Rs 1,935cr" wrapped onto two lines and collided with
    // its caption at 96dp on a real phone.
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(
            figure,
            style = MaterialTheme.typography.titleMedium,
            color = Alarm,
            maxLines = 1,
        )
        Text(caption, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2)
    }
}

/** Feeling the save once is worth more than any description of it. */
@Composable
private fun PracticeCard(onPractice: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("See it work, before you need it", style = MaterialTheme.typography.titleMedium)
            Text(
                "Run a safe practice call. Kaavalu will react exactly as it would during a " +
                    "real scam, so you know what to expect.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
            Button(
                onClick = onPractice,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Paper),
            ) { Text("Practice a scam call") }
        }
    }
}

@Composable
private fun LiveRiskCard(
    score: Int,
    tier: Tier,
    lang: String,
    contributions: List<com.dasen.kaavalu.core.Contribution>,
) {
    val colour = when (tier) {
        Tier.CALM -> Guard
        Tier.WATCH -> Caution
        else -> Alarm
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Current risk $score out of 100, level ${tier.name}" },
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Risk $score / 100", style = MaterialTheme.typography.titleLarge)
                Text(
                    tier.name,
                    color = colour,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp)),
            ) {
                LinearProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier.fillMaxSize(),
                    color = colour,
                    trackColor = Color(0xFFE8E2D8),
                    drawStopIndicator = {},
                )
            }
            contributions.forEach { c ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "+${c.points}",
                        color = Alarm,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(44.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Column {
                        Text(
                            Copy.reasonFor(lang, c.key, c.arg),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            Copy.sourceFor(lang, c.key),
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardianCard(ctx: android.content.Context) {
    val guardian = Prefs.guardian(ctx)
    var sent by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your family guardian", style = MaterialTheme.typography.titleMedium)
            Text(
                guardian ?: "No number saved. Nobody will be alerted.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (guardian == null) Alarm else Ink,
            )
            Text(
                "If a call looks like a scam, this number gets a text with the reasons. " +
                    "The scam works by keeping you alone, so this is the part that breaks it.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
            if (guardian != null) {
                OutlinedButton(
                    onClick = {
                        Guardian.sendTest(ctx)
                        sent = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text(if (sent) "Test alert sent" else "Send a test alert") }
            }
        }
    }
}

@Composable
private fun LanguageCard(lang: String, onPick: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Warning language", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Copy.languages.forEach { (code, label) ->
                    FilterChip(
                        selected = lang == code,
                        onClick = { onPick(code) },
                        label = { Text(label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Guard,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            Text(
                "Warnings are spoken and shown in this language.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
    }
}
