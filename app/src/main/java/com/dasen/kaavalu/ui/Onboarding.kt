package com.dasen.kaavalu.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.respond.Delivery
import com.dasen.kaavalu.respond.Guardian
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion

/**
 * Setup is done once, by the son or daughter, on the phone of the person being protected.
 *
 * First run is a ladder: one screen per permission with a plain sentence on why it is needed,
 * because a parent is not going to grant six permissions to a wall of toggles. Afterwards the
 * same screen becomes the Setup tab — a checklist that shows at a glance which of the six are
 * live and lets any one of them be fixed without starting over.
 */
@Composable
fun Onboarding(firstRun: Boolean = true, onOpenDemo: () -> Unit = {}, onDone: () -> Unit) {
    if (firstRun) FirstRunLadder(onDone) else SetupChecklist(onOpenDemo)
}

@Composable
private fun FirstRunLadder(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val steps = Step.entries
    var page by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refresh++ }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    // Forward through the ladder moves left, back moves right: the same direction the tabs use.
    val inMs = motion(Motion.EMPHASIZED)
    val outMs = motion(Motion.QUICK)
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (fadeIn(tween(inMs)) + slideInHorizontally(tween(inMs, easing = Motion.enter)) { dir * it / 10 })
                .togetherWith(fadeOut(tween(outMs)) + slideOutHorizontally(tween(outMs, easing = Motion.exit)) { -dir * it / 20 })
        },
        label = "ladder",
    ) { current ->
        ScreenColumn(spacing = Space.lg) {
            when {
                current == 0 -> {
                    Spacer(Modifier.height(Space.lg))
                    Wordmark()
                    Text("Set up Kaavalu", style = MaterialTheme.typography.displayMedium, color = Ink)
                    Text(
                        "Do this once, on the phone of the person you are protecting. After that it " +
                            "runs in the background and speaks up only when a call looks like a scam.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Ink2,
                    )
                    GuardianForm { name, number, lang ->
                        Prefs.save(ctx, number, name, lang)
                        page = 1
                    }
                }

                current <= steps.size -> {
                    val step = steps[current - 1]
                    // Reading refresh here ties this block to the counter, so returning from
                    // Settings re-evaluates whether the permission is now granted.
                    val granted = remember(current, refresh) { Permissions.isGranted(ctx, step) }

                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Text("Step $current of ${steps.size}", style = KType.utility, color = Muted)
                        SegmentMeter(steps.indices.map { it < current })
                    }
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        current.toString().padStart(2, '0'),
                        style = KType.total,
                        color = Guard500,
                    )
                    Text(step.title, style = MaterialTheme.typography.headlineLarge, color = Ink)
                    Text(step.why, style = MaterialTheme.typography.bodyLarge, color = Ink2)

                    Well(padding = Space.lg) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusGlyph(granted)
                            Spacer(Modifier.width(Space.md))
                            Text(
                                if (granted) "On" else "Not on yet",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (granted) Guard900 else Ink2,
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        BigAction(
                            if (granted) "Check again" else "Turn on",
                            style = if (granted) ActionStyle.Secondary else ActionStyle.Primary,
                            critical = true,
                        ) {
                            grant(ctx, step, runtimeLauncher::launch) { intent ->
                                runCatching { settingsLauncher.launch(intent) }
                            }
                        }
                        BigAction(
                            if (granted) "Next" else "Skip for now",
                            style = if (granted) ActionStyle.Primary else ActionStyle.Ghost,
                            icon = if (granted) R.drawable.ic_arrow else null,
                        ) { page++ }

                        if (step == Step.BATTERY) {
                            Permissions.autostartIntent()?.let { intent ->
                                BigAction(
                                    "Also allow autostart on this phone",
                                    style = ActionStyle.Ghost,
                                    textStyle = MaterialTheme.typography.titleMedium,
                                ) { runCatching { settingsLauncher.launch(intent) } }
                            }
                        }
                    }
                }

                else -> {
                    val missing = remember(refresh) { steps.filter { !Permissions.isGranted(ctx, it) } }
                    Spacer(Modifier.height(Space.lg))
                    if (missing.isEmpty()) {
                        Plate(Guard) {
                            SignTag("Protected", container = Gold, content = Guard900, live = true)
                            Spacer(Modifier.height(Space.xxs))
                            Text("Protection is on", style = MaterialTheme.typography.displayMedium, color = Paper)
                            Text(
                                "Kaavalu now checks every call from an unknown number.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Guard100,
                            )
                        }
                    } else {
                        Plate(Caution100, watermark = Caution.copy(alpha = 0.10f), depth = 0.03f) {
                            SignTag("Not protected", container = Caution, content = Color.White)
                            Spacer(Modifier.height(Space.xxs))
                            Text(
                                if (missing.size == 1) "1 setting is still off" else "${missing.size} settings are still off",
                                style = MaterialTheme.typography.displaySmall,
                                color = Caution900,
                            )
                            missing.forEach {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusGlyph(false)
                                    Spacer(Modifier.width(Space.sm))
                                    Text(it.title, style = MaterialTheme.typography.titleMedium, color = Caution900)
                                }
                            }
                        }
                        BigAction("Go back and turn them on", style = ActionStyle.Ghost) { page = 1 }
                    }
                    BigAction("Finish", icon = R.drawable.ic_check, critical = true, onClick = onDone)
                }
            }
        }
    }
}

/**
 * The Setup tab. Everything on one page, because after the first run the family member is
 * looking for the one specific thing that broke, not a six-screen wizard.
 */
@Composable
private fun SetupChecklist(onOpenDemo: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var lang by rememberSaveable { mutableStateOf(Prefs.language(ctx)) }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refresh++ }
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    val statuses = remember(refresh) { Step.entries.associateWith { Permissions.isGranted(ctx, it) } }
    val granted = statuses.count { it.value }
    val total = Step.entries.size

    ScreenColumn {
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            ScreenTitle(
                title = "Setup",
                lead = if (granted == total) "All $total safeguards are on." else "$granted of $total safeguards are on.",
            )
            SegmentMeter(Step.entries.map { statuses[it] == true })
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionTitle("Family")
            Sheet(padding = Space.lg, spacing = Space.md) {
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
                    val guardian = Prefs.guardian(ctx)
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            painterResource(R.drawable.ic_family),
                            null,
                            tint = if (guardian == null) Caution else Guard500,
                            modifier = Modifier.padding(top = 4.dp).size(28.dp),
                        )
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                guardian ?: "No family number saved",
                                style = if (guardian == null) MaterialTheme.typography.titleLarge else KType.dial,
                                color = if (guardian == null) Caution900 else Ink,
                            )
                            Text(
                                if (guardian == null) "Alerts can’t be sent until one is saved."
                                else "Texted if ${Prefs.userName(ctx)} is on a scam call.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Muted,
                            )
                        }
                    }
                    BigAction(
                        if (guardian == null) "Add a family number" else "Change family number",
                        style = ActionStyle.Secondary,
                    ) { editing = true }
                    if (guardian != null) TestAlert(guardian)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionTitle("Warning language")
            Text(
                "Warnings are spoken and shown in this language, whatever the phone is set to.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Copy.languages.forEach { (code, label) ->
                    LangChip(label, code, lang == code, Modifier.weight(1f)) {
                        lang = code
                        Prefs.setLanguage(ctx, code)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionTitle("Safeguards")
            Sheet(padding = 0.dp, spacing = 0.dp) {
                Step.entries.forEachIndexed { i, step ->
                    val ok = statuses[step] == true
                    if (i > 0) HRule(Modifier.padding(start = 64.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(Space.lg),
                        verticalAlignment = Alignment.Top,
                    ) {
                        StatusGlyph(ok)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                            Text(step.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                            Text(step.why, style = MaterialTheme.typography.bodyMedium, color = Muted)
                            if (!ok) {
                                Spacer(Modifier.height(Space.xs))
                                BigAction("Turn on") {
                                    grant(ctx, step, runtimeLauncher::launch) { intent ->
                                        runCatching { settingsLauncher.launch(intent) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        ActionStrip {
            // Android removes the permissions of apps nobody opens for a few months. The
            // parent never opens this one — that is the point of it — so without this
            // exemption, protection quietly switches itself off one day.
            if (!remember(refresh) { ctx.packageManager.isAutoRevokeWhitelisted }) {
                ActionRow(
                    icon = R.drawable.ic_shield_k,
                    verb = "Keep these permissions",
                    detail = "Stop Android removing them because the app is rarely opened. Turn off “Pause app activity if unused”.",
                    tone = Tone.Checking,
                ) {
                    runCatching {
                        settingsLauncher.launch(
                            android.content.Intent(
                                android.content.Intent.ACTION_AUTO_REVOKE_PERMISSIONS,
                                android.net.Uri.fromParts("package", ctx.packageName, null),
                            ),
                        )
                    }
                }
                HRule(Modifier.padding(start = 64.dp))
            }
            Permissions.autostartIntent()?.let { intent ->
                ActionRow(
                    icon = R.drawable.ic_nav_setup,
                    verb = "Allow autostart",
                    detail = "This phone’s maker stops background apps unless you allow it",
                ) { runCatching { settingsLauncher.launch(intent) } }
                HRule(Modifier.padding(start = 64.dp))
            }
            ActionRow(
                icon = R.drawable.ic_nav_demo,
                verb = "Rehearse a warning",
                detail = "Demo console: show the family what a warning looks like",
                onClick = onOpenDemo,
            )
        }

        val trusted = Prefs.trustedCount(ctx)
        Text(
            if (trusted == 1) "1 number is marked as someone you know." else "$trusted numbers are marked as people you know.",
            style = KType.utility,
            color = Muted,
        )
    }
}

/**
 * A test of the one message that has to work. The family member sets this up, presses it,
 * and sees their own phone light up: that is the moment the app earns trust. The result is
 * reported honestly — "sent" means the network took it, never that it was read.
 */
@Composable
private fun TestAlert(guardian: String) {
    val ctx = LocalContext.current
    val status by Guardian.testStatus.collectAsStateWithLifecycle()
    BigAction(
        if (status == Delivery.SENDING) "Sending a test alert…" else "Send a test alert",
        style = ActionStyle.Ghost,
        icon = R.drawable.ic_family,
        enabled = status != Delivery.SENDING,
        textStyle = MaterialTheme.typography.titleMedium,
    ) { Guardian.sendTest(ctx) }
    val (line, colour) = when (status) {
        Delivery.SENT -> "Test sent to $guardian. Check that it arrived." to Guard500
        Delivery.FAILED -> "The test didn’t send. Check the SIM has balance and signal, then try again." to Caution900
        Delivery.NO_PERMISSION -> "SMS permission is off. Turn on “Phone, contacts, SMS and notifications” below." to Caution900
        else -> null to Muted
    }
    line?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = colour,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
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

/** A text field pressed into the page, labelled above rather than inside. */
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    help: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = Ink2)
        Well(padding = Space.md) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = Ink),
                cursorBrush = SolidColor(Guard),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .semantics { contentDescription = label },
            )
        }
        help?.let { Text(it, style = KType.utility, color = Muted) }
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
    var name by rememberSaveable { mutableStateOf(initialName) }
    var number by rememberSaveable { mutableStateOf(initialNumber) }
    var lang by rememberSaveable { mutableStateOf(initialLang) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Field("Their name", name, { name = it })
        Field(
            "Your phone number",
            number,
            { number = it },
            help = "Kaavalu texts this number if they get a scam call.",
            keyboard = KeyboardType.Phone,
        )

        // The Setup tab has its own language row; the compact form keeps whatever is chosen.
        if (!compact) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text("Language for the spoken warning", style = MaterialTheme.typography.titleSmall, color = Ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Copy.languages.forEach { (code, label) ->
                        LangChip(label, code, lang == code, Modifier.weight(1f)) { lang = code }
                    }
                }
            }
        }

        BigAction(
            if (compact) "Save" else "Continue",
            enabled = number.filter(Char::isDigit).length >= 10,
            critical = !compact,
            icon = if (compact) null else R.drawable.ic_arrow,
        ) { onNext(name.ifBlank { "Your family member" }, number, lang) }
    }
}
