package com.dasen.kaavalu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.core.AppKind
import com.dasen.kaavalu.core.Channel
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.SignalBus

/**
 * What makes the live demo reliable. Signals are injected by hand, and "demo time" makes
 * one real second count as one minute so the 20-minute threshold fires in 20 seconds.
 *
 * Say both of those out loud when you present. A jury forgives compressed time; it does
 * not forgive finding out afterwards.
 */
@Composable
fun DemoConsole(engine: RiskEngine, onBack: () -> Unit) {
    val lang = Prefs.language(LocalContext.current)
    val s by engine.state.collectAsStateWithLifecycle()
    var demoTime by remember { mutableStateOf(engine.config.timeScale > 1.0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Demo console", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Risk " + s.score + "/100  |  " + s.tier.name,
            style = MaterialTheme.typography.titleLarge,
            color = if (s.score >= 65) Alarm else Guard,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = demoTime,
                onCheckedChange = {
                    demoTime = it
                    engine.config = engine.config.copy(timeScale = if (it) 60.0 else 1.0)
                },
            )
            Text("  Demo time (1 second = 1 minute)", style = MaterialTheme.typography.bodyMedium)
        }

        DemoButton("Unknown WhatsApp video call") {
            SignalBus.emit(
                Signal.CallStarted(
                    number = "+91 98xxx xxx01",
                    channel = Channel.WHATSAPP,
                    isKnown = false,
                    isVideo = true,
                ),
            )
        }
        DemoButton("Unknown cellular call (unverified)") {
            SignalBus.emit(
                Signal.CallStarted(
                    number = "+91 80xxx xxx44",
                    channel = Channel.CELLULAR,
                    isKnown = false,
                    unverified = true,
                ),
            )
        }
        DemoButton("Open UPI app") {
            SignalBus.emit(Signal.SensitiveAppOpened("com.phonepe.app", AppKind.PAYMENT))
        }
        DemoButton("Open screen-share app") {
            SignalBus.emit(
                Signal.SensitiveAppOpened("com.anydesk.anydeskandroid", AppKind.REMOTE_ACCESS),
            )
        }
        DemoButton("End call") { SignalBus.emit(Signal.CallEnded) }

        OutlinedButton(onClick = { engine.reset() }, modifier = Modifier.fillMaxWidth()) {
            Text("Reset")
        }

        if (s.contributions.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.contributions.forEach { c ->
                        Text(
                            "+" + c.points + "  " + Copy.reasonFor(lang, c.key, c.arg) +
                                "  (" + Copy.sourceFor(lang, c.key) + ")",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DemoButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Guard),
    ) { Text(label, style = MaterialTheme.typography.bodyLarge) }
}
