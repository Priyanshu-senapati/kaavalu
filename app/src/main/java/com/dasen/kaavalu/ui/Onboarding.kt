package com.dasen.kaavalu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R

/**
 * Setup is done once, by the son or daughter, on the phone of the person being protected.
 *
 * First run is a ladder: one screen per permission with a plain sentence on why it is
 * needed, because a parent is not going to grant six permissions to a wall of toggles.
 * Afterwards the same screen becomes the Setup tab — a checklist that shows, at a glance,
 * which of the six are live and lets any one of them be fixed without starting over.
 */
@Composable
fun Onboarding(firstRun: Boolean = true, onDone: () -> Unit) {
    if (firstRun) FirstRunLadder(onDone) else SetupChecklist(onDone)
}

@Composable
private fun FirstRunLadder(onDone: () -> Unit) {
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

                StepProgress(page, steps.size)
                Text(step.title, style = MaterialTheme.typography.headlineMedium)
                Text(step.why, style = MaterialTheme.typography.bodyLarge, color = Ink)

                Panel(background = if (granted) GuardSoft else Color(0xFFF2ECE1)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(granted)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (granted) "Granted" else "Not granted yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (granted) Guard else Ink,
                        )
                    }
                }

                BigAction(
                    if (granted) "Open again" else "Grant this",
                    container = if (granted) Color.White else Guard,
                    content = if (granted) Guard else Color.White,
                ) {
                    grant(ctx, step, runtimeLauncher::launch) { intent ->
                        runCatching { settingsLauncher.launch(intent) }
                    }
                }

                BigAction(
                    if (granted) "Next" else "Skip for now",
                    container = if (granted) Guard else Color(0xFFD9D2C6),
                    content = if (granted) Color.White else Ink,
                ) { page++ }

                if (step == Step.BATTERY) {
                    Permissions.autostartIntent()?.let { intent ->
                        TextButton(onClick = { runCatching { settingsLauncher.launch(intent) } }) {
                            Text("Also allow autostart on this phone")
                        }
                    }
                }
            }

            else -> {
                val missing = remember(refresh) { steps.filter { !Permissions.isGranted(ctx, it) } }
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
                    missing.forEach { ReasonRow(null, it.title, Caution) }
                    TextButton(onClick = { page = 1 }) { Text("Go back and grant them") }
                }
                BigAction("Finish", icon = R.drawable.ic_check, onClick = onDone)
            }
        }
    }
}

/**
 * The Setup tab. Everything on one page, because after the first run the family member is
 * looking for one specific thing that broke, not a six-screen wizard.
 */
@Composable
private fun SetupChecklist(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var lang by remember { mutableStateOf(Prefs.language(ctx)) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refresh++ }
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    val statuses = remember(refresh) { Step.entries.associateWith { Permissions.isGranted(ctx, it) } }
    val granted = statuses.count { it.value }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        ScreenHeader("Setup", "$granted of ${Step.entries.size} permissions are live.", onDone)

        Panel {
            Text("Family guardian", style = MaterialTheme.typography.titleMedium)
            if (editing) {
                GuardianForm(
                    initialName = Prefs.userName(ctx),
                    initialNumber = Prefs.guardian(ctx).orEmpty(),
                    initialLang = lang,
                    compact = true,
                ) { name, number, chosen ->
                    Prefs.save(ctx, number, name, chosen)
                    lang = chosen
                    editing = false
                }
            } else {
                Text(
                    Prefs.guardian(ctx)?.let { "$it  ·  protecting ${Prefs.userName(ctx)}" }
                        ?: "No guardian saved. The alert SMS has nowhere to go.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (Prefs.guardian(ctx) == null) Alarm else Muted,
                )
                OutlinedButton(onClick = { editing = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Change guardian or language")
                }
            }
        }

        Text("Permissions", style = MaterialTheme.typography.titleMedium)
        Step.entries.forEach { step ->
            val ok = statuses[step] == true
            Panel(background = if (ok) GuardSoft else Color.White) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(ok)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(step.title, style = MaterialTheme.typography.titleSmall)
                        Text(step.why, style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                if (!ok) {
                    BigAction("Grant this", container = Guard) {
                        grant(ctx, step, runtimeLauncher::launch) { intent ->
                            runCatching { settingsLauncher.launch(intent) }
                        }
                    }
                }
            }
        }

        Permissions.autostartIntent()?.let { intent ->
            OutlinedButton(
                onClick = { runCatching { settingsLauncher.launch(intent) } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Allow autostart (this phone brand needs it)") }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun grant(
    ctx: android.content.Context,
    step: Step,
    requestRuntime: (Array<String>) -> Unit,
    openSettings: (android.content.Intent) -> Unit,
) {
    if (step == Step.RUNTIME) {
        requestRuntime(Permissions.runtimePermissions)
    } else {
        Permissions.settingsIntent(ctx, step)?.let(openSettings)
    }
}

@Composable
private fun StepProgress(page: Int, total: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Step $page of $total", style = MaterialTheme.typography.bodySmall, color = Muted)
        Meter(page / total.toFloat(), Guard)
    }
}

@Composable
private fun StatusDot(ok: Boolean) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (ok) Guard else Color(0xFFE2DACB)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (ok) R.drawable.ic_check else R.drawable.ic_warning),
            contentDescription = null,
            tint = if (ok) Color.White else Caution,
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun GuardianForm(
    initialName: String = "",
    initialNumber: String = "",
    initialLang: String = "en",
    compact: Boolean = false,
    onNext: (name: String, guardian: String, lang: String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var number by remember { mutableStateOf(initialNumber) }
    var lang by remember { mutableStateOf(initialLang) }

    if (!compact) {
        Wordmark()
        Spacer(Modifier.height(4.dp))
        Text(
            "Set this up once for the person you are protecting. It then runs silently.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Their name") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = number,
        onValueChange = { number = it },
        label = { Text("Your number, as the family guardian") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
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

    BigAction(
        if (compact) "Save" else "Continue",
        enabled = number.filter(Char::isDigit).length >= 10,
    ) { onNext(name.ifBlank { "Your family member" }, number, lang) }
}
