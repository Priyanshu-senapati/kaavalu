package com.dasen.kaavalu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs

/**
 * Setup is done once, by the son or daughter, on the phone of the person being protected.
 * One screen per permission with a plain sentence on why it is needed, then a check that
 * every one of them is actually live.
 */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val steps = Step.entries
    var page by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refresh++ }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        when {
            page == 0 -> GuardianForm { name, number, lang ->
                Prefs.save(ctx, number, name, lang)
                page = 1
            }

            page <= steps.size -> {
                val step = steps[page - 1]
                // Reading refresh here ties this block to the counter, so returning from
                // Settings re-evaluates whether the permission is now granted.
                val granted = remember(page, refresh) { Permissions.isGranted(ctx, step) }

                Text(
                    "Step $page of ${steps.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                )
                Text(step.title, style = MaterialTheme.typography.headlineMedium)
                Text(step.why, style = MaterialTheme.typography.bodyLarge, color = Ink)

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (granted) Guard else Color(0xFFEFE9DE),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (granted) "Granted" else "Not granted yet",
                        Modifier.padding(16.dp),
                        color = if (granted) Color.White else Ink,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                Button(
                    onClick = {
                        if (step == Step.RUNTIME) {
                            runtimeLauncher.launch(Permissions.runtimePermissions)
                        } else {
                            Permissions.settingsIntent(ctx, step)?.let {
                                runCatching { settingsLauncher.launch(it) }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Guard),
                ) { Text(if (granted) "Open again" else "Grant this") }

                Button(
                    onClick = { page++ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (granted) Guard else Color(0xFFD9D2C6),
                        contentColor = if (granted) Color.White else Ink,
                    ),
                ) { Text(if (granted) "Next" else "Skip for now") }

                if (step == Step.BATTERY) {
                    Permissions.autostartIntent()?.let { intent ->
                        TextButton(onClick = { runCatching { settingsLauncher.launch(intent) } }) {
                            Text("Also allow autostart on this phone")
                        }
                    }
                }
            }

            else -> {
                val missing = remember(refresh) {
                    steps.filter { !Permissions.isGranted(ctx, it) }
                }
                Text(
                    if (missing.isEmpty()) "Protection is on" else "Almost there",
                    style = MaterialTheme.typography.displaySmall,
                    color = if (missing.isEmpty()) Guard else Alarm,
                )
                if (missing.isEmpty()) {
                    Text(
                        "Kaavalu will now watch every call from a number that is not in the contacts.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Text("Still missing:", style = MaterialTheme.typography.titleMedium)
                    missing.forEach {
                        Text("- " + it.title, style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { page = 1 }) { Text("Go back and grant them") }
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Guard),
                ) { Text("Finish") }
            }
        }
    }
}

@Composable
private fun GuardianForm(onNext: (name: String, guardian: String, lang: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var lang by remember { mutableStateOf("en") }

    Text("ಕಾವಲು", style = MaterialTheme.typography.displaySmall, color = Guard)
    Text(
        "Set this up once for the person you are protecting. It then runs silently.",
        style = MaterialTheme.typography.bodyLarge,
    )

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Their name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = number,
        onValueChange = { number = it },
        label = { Text("Your number, as the family guardian") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
    )

    Text("Language for the spoken warning", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Copy.languages.forEach { (code, label) ->
            FilterChip(
                selected = lang == code,
                onClick = { lang = code },
                label = { Text(label) },
            )
        }
    }

    Button(
        onClick = { onNext(name.ifBlank { "Your family member" }, number, lang) },
        enabled = number.filter(Char::isDigit).length >= 10,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Guard),
    ) { Text("Continue") }
}
