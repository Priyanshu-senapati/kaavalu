package com.dasen.kaavalu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.core.Tier

@Composable
fun Home(engine: RiskEngine, navigate: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val state by engine.state.collectAsStateWithLifecycle()
    var lang by remember { mutableStateOf(Prefs.language(ctx)) }
    val protected = Permissions.essentialsGranted(ctx)
    val missing = Step.entries.filter { !Permissions.isGranted(ctx, it) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("ಕಾವಲು", style = MaterialTheme.typography.displaySmall, color = Guard)
        Text(
            "Kaavalu is watching this phone for scam calls.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )

        ShieldCard(protected, missing.size) { navigate(Screen.ONBOARDING) }

        RiskMeter(state.score, state.tier, state.sessionActive)

        if (state.contributions.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("What Kaavalu saw", style = MaterialTheme.typography.titleMedium)
                    state.contributions.forEach { c ->
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

        Button(
            onClick = { navigate(Screen.SCAN) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Guard),
        ) { Text("Is this notice real?") }

        Button(
            onClick = { navigate(Screen.ASK) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Guard),
        ) { Text("Ask Kaavalu") }

        val guardian = Prefs.guardian(ctx)
        Text(
            if (guardian == null) "No family guardian saved yet."
            else "Family guardian: $guardian",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )

        // The warning language. Changing it here takes effect on the next warning, so the
        // same phone can be shown to an English, Hindi and Kannada audience in one sitting.
        Text("Warning language", style = MaterialTheme.typography.titleMedium)
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

        OutlinedButton(
            onClick = { navigate(Screen.DEMO) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Demo console") }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ShieldCard(protected: Boolean, missing: Int, onFix: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (protected) Guard else Alarm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (protected) "Protection is on" else "Protection is not complete",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Text(
                if (missing == 0) "Every permission Kaavalu needs is granted."
                else "$missing permission${if (missing == 1) "" else "s"} still needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )
            if (missing > 0) {
                Button(
                    onClick = onFix,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
                ) { Text("Finish setup") }
            }
        }
    }
}

@Composable
private fun RiskMeter(score: Int, tier: Tier, sessionActive: Boolean) {
    val colour = when (tier) {
        Tier.CALM -> Guard
        Tier.WATCH -> Color(0xFFB8860B)
        else -> Alarm
    }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Risk $score / 100", style = MaterialTheme.typography.titleLarge)
                Text(tier.name, color = colour, style = MaterialTheme.typography.titleMedium)
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
            Text(
                if (sessionActive) "Watching an unknown call right now."
                else "No suspicious call. Kaavalu stays silent for people you know.",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }
    }
}
