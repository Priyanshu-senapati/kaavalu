package com.dasen.kaavalu.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.respond.Speaker
import com.dasen.kaavalu.scan.NoticeMarkers
import com.dasen.kaavalu.scan.ScanResult
import com.dasen.kaavalu.scan.Verdict

/**
 * Ask Kaavalu. The user describes the call aloud in their own language and is told, aloud,
 * what to do. The transcript is matched against the same marker list the notice scanner
 * uses: one description of the scam, two ways in.
 *
 * Three things here exist because an earlier version heard the words and still said "safe":
 *  - every alternative the recognizer returns is scored, not only its first guess, because
 *    "they said I am under arrest" and "they said I am under a rest" are one sentence to a
 *    person and two very different strings to a regex;
 *  - offline recognition is a *fallback*, not the default. Forcing it on a phone with no
 *    downloaded model returns nothing at all;
 *  - there is a keyboard. A demo, or a noisy room, must never come down to the microphone.
 */
@Composable
fun AskScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val lang = remember { Prefs.language(ctx) }
    val speaker = remember { Speaker(ctx) }

    var listening by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var heard by rememberSaveable { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var typing by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    // The last few seconds of the microphone, for the waveform. Real input, never a loop.
    val levels = remember { mutableStateListOf<Float>() }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(ctx)) SpeechRecognizer.createSpeechRecognizer(ctx)
        else null
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recognizer?.destroy() }
            speaker.shutdown()
        }
    }

    fun answer(text: String) {
        heard = text
        partial = ""
        thinking = false
        val scored = NoticeMarkers.evaluateSpoken(text)
        result = scored
        val reply = when (scored.verdict) {
            Verdict.SCAM, Verdict.SUSPICIOUS -> Copy.askScamAnswer(lang)
            else -> Copy.askSafeAnswer(lang)
        }
        speaker.say(reply, lang)
    }

    /**
     * [offline] is only set after an online attempt has already failed: it is the
     * airplane-mode escape hatch, not the normal path.
     */
    fun listen(offline: Boolean) {
        val r = recognizer
        if (r == null) {
            error = "This phone has no speech recognition. Type it instead."
            typing = true
            return
        }
        error = null
        result = null
        heard = ""
        partial = ""
        thinking = false
        listening = true
        levels.clear()

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onEndOfSpeech() {
                listening = false
                level = 0f
                thinking = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Roughly -2..10 dB in practice. Normalised so the waveform has something to show.
                level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                levels.add(level)
                if (levels.size > WAVE_BARS) levels.removeAt(0)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
            }

            override fun onError(code: Int) {
                listening = false
                thinking = false
                level = 0f
                // A network failure on the first, online attempt is worth one silent retry
                // with the on-device model before the user is told anything went wrong.
                val networkish = code == SpeechRecognizer.ERROR_NETWORK ||
                    code == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                    code == SpeechRecognizer.ERROR_SERVER
                if (networkish && !offline) {
                    listen(offline = true)
                    return
                }
                error = when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    -> "Kaavalu didn’t catch that. Speak again, or type what they said."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Kaavalu needs the microphone to hear you. You can type it instead."
                    else -> "Kaavalu couldn’t hear that. Try again, or type it."
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                thinking = false
                level = 0f
                val all = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                if (all.isEmpty()) {
                    error = "Kaavalu didn’t catch that. Speak again, or type what they said."
                    return
                }
                // Score every alternative together; show the best one.
                answer(all.joinToString(". "))
                heard = all.first()
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "$lang-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "$lang-IN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline)
            // A frightened person pauses mid-sentence. The defaults cut them off.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
        }
        runCatching { r.startListening(intent) }.onFailure {
            listening = false
            error = "Kaavalu couldn’t start listening. Type it instead."
            typing = true
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) listen(offline = false)
        else {
            error = "Kaavalu needs the microphone to hear you. You can type it instead."
            typing = true
        }
    }

    fun toggleMic() {
        if (listening) {
            runCatching { recognizer?.stopListening() }
            listening = false
        } else if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            listen(offline = false)
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val mode = when {
        listening -> MicMode.Listening
        thinking -> MicMode.Thinking
        else -> MicMode.Idle
    }

    ScreenColumn(spacing = Space.lg) {
        ScreenTitle(
            title = "Ask Kaavalu",
            lead = "Tell Kaavalu what the caller said. It answers out loud.",
        )

        ListeningWell(mode, levels, level, partial, heard.isNotBlank(), onMic = ::toggleMic)

        AnimatedVisibility(
            visible = result == null && heard.isBlank() && mode == MicMode.Idle && error == null,
            enter = fadeIn(tween(motion(Motion.STANDARD))),
            exit = fadeOut(tween(motion(Motion.QUICK))) + shrinkVertically(tween(motion(Motion.STANDARD))),
        ) {
            Examples()
        }

        AnimatedVisibility(
            visible = heard.isNotBlank(),
            enter = fadeIn(tween(motion(Motion.STANDARD))) + expandVertically(tween(motion(Motion.STANDARD), easing = Motion.enter)),
            exit = fadeOut(tween(motion(Motion.QUICK))) + shrinkVertically(tween(motion(Motion.QUICK))),
        ) {
            Quote(label = "You said", text = heard, ink = Ink)
        }

        error?.let {
            VerdictReveal {
                Column(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    Instruction(it, Tone.Checking)
                    if (recognizer != null) {
                        BigAction("Try again", icon = R.drawable.ic_nav_ask, onClick = ::toggleMic)
                    }
                }
            }
        }

        result?.let { r -> VerdictReveal { AnswerSheet(r, lang, ctx, speaker) } }

        // The keyboard is never the point, but it is always there: a demo that depends on a
        // microphone in a loud hall is a demo that fails in front of the jury.
        BigAction(
            if (typing) "Hide the keyboard" else "Type it instead",
            style = ActionStyle.Ghost,
            icon = R.drawable.ic_keyboard,
            textStyle = MaterialTheme.typography.titleMedium,
        ) { typing = !typing }

        AnimatedVisibility(
            visible = typing,
            enter = fadeIn(tween(motion(Motion.QUICK))) + expandVertically(tween(motion(Motion.STANDARD), easing = Motion.enter)),
            exit = fadeOut(tween(motion(Motion.QUICK))) + shrinkVertically(tween(motion(Motion.QUICK))),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Well(padding = Space.lg) {
                    Text("What did they say?", style = MaterialTheme.typography.titleSmall, color = Ink2)
                    BasicTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        textStyle = MaterialTheme.typography.bodyLarge.script(lang).copy(color = Ink),
                        cursorBrush = SolidColor(Guard),
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Touch.critical)
                            .semantics { contentDescription = "What did they say?" },
                    )
                }
                BigAction("Check what they said", enabled = typed.isNotBlank()) { answer(typed) }
            }
        }
    }
}

private const val WAVE_BARS = 36

private enum class MicMode { Idle, Listening, Thinking }

/**
 * The listening surface. A microphone that is the one circle in the app, a waveform drawn
 * from the actual input level, and the words appearing as they are recognised. Idle is a
 * flat line; listening moves only when the person speaks; thinking is a slow light across
 * the stilled line while the recogniser finishes.
 */
@Composable
private fun ListeningWell(
    mode: MicMode,
    levels: List<Float>,
    level: Float,
    partial: String,
    askedBefore: Boolean,
    onMic: () -> Unit,
) {
    Well(padding = Space.xl) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val inMs = motion(Motion.STANDARD)
            val outMs = motion(Motion.QUICK)
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(tween(inMs)) togetherWith fadeOut(tween(outMs)) },
                label = "micSign",
                modifier = Modifier.heightIn(min = 30.dp),
            ) { m ->
                when (m) {
                    MicMode.Listening -> SignTag("Listening", container = Caution, content = Color.White, live = true)
                    MicMode.Thinking -> SignTag("Thinking", container = Ink2, content = Paper, live = true)
                    MicMode.Idle -> Spacer(Modifier.height(30.dp))
                }
            }
            Spacer(Modifier.height(Space.lg))
            MicButton(mode, level, onMic)
            Spacer(Modifier.height(Space.lg))
            Waveform(mode, levels)
            Spacer(Modifier.height(Space.md))
            Text(
                when {
                    mode == MicMode.Listening && partial.isNotBlank() -> "“$partial”"
                    mode == MicMode.Listening -> "Say what they told you."
                    mode == MicMode.Thinking -> "Checking what you said…"
                    askedBefore -> "Tap the microphone to ask again."
                    else -> "Tap the microphone and describe the call."
                },
                style = if (mode == MicMode.Listening && partial.isNotBlank()) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = if (partial.isNotBlank() && mode == MicMode.Listening) Ink else Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/**
 * A microphone that reacts to the room. Silence under a "Listening" label reads as broken,
 * so the ring tracks the actual input level — the one animation on this screen that carries
 * information rather than decoration.
 */
@Composable
private fun MicButton(mode: MicMode, level: Float, onClick: () -> Unit) {
    val listening = mode == MicMode.Listening
    val ring by animateFloatAsState(
        targetValue = if (listening) 1f + level * 0.32f else 1f,
        animationSpec = tween(motion(Motion.QUICK), easing = Motion.standard),
        label = "ring",
    )
    val fill by animateColorAsState(
        when (mode) {
            MicMode.Listening -> Caution500
            MicMode.Thinking -> Ink2
            MicMode.Idle -> Guard
        },
        tween(motion(Motion.STANDARD)),
        label = "micFill",
    )
    val source = remember { MutableInteractionSource() }
    val press = pressScale(source, depth = 0.94f)
    Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(160.dp)
                .graphicsLayer {
                    scaleX = ring
                    scaleY = ring
                    alpha = if (listening) 0.18f + level * 0.3f else 0f
                }
                .clip(CircleShape)
                .background(Caution100),
        )
        Box(
            Modifier
                .size(136.dp)
                .graphicsLayer { scaleX = press; scaleY = press }
                .clip(CircleShape)
                .background(fill)
                .clickable(
                    interactionSource = source,
                    indication = null,
                    role = Role.Button,
                    enabled = mode != MicMode.Thinking,
                    onClickLabel = if (listening) "Stop listening" else "Speak",
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(R.drawable.ic_nav_ask),
                    contentDescription = if (listening) "Stop listening" else "Speak",
                    tint = Paper,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    if (listening) "Stop" else "Speak",
                    style = MaterialTheme.typography.titleMedium,
                    color = Paper,
                )
            }
        }
    }
}

@Composable
private fun Waveform(mode: MicMode, levels: List<Float>) {
    val still = LocalReduceMotion.current
    val sweep = if (mode == MicMode.Thinking && !still) {
        val t = rememberInfiniteTransition(label = "think")
        val v by t.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
            label = "thinkSweep",
        )
        v
    } else {
        -1f
    }
    val live = if (mode == MicMode.Listening) Caution500 else Line
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clearAndSetSemantics { },
    ) {
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (WAVE_BARS - 1)) / WAVE_BARS
        val mid = size.height / 2
        val padded = List(WAVE_BARS - levels.size) { 0f } + levels.takeLast(WAVE_BARS)
        padded.forEachIndexed { i, v ->
            val h = (4.dp.toPx() + v * (size.height - 4.dp.toPx()))
            val x = i * (w + gap)
            val lit = sweep >= 0f && kotlin.math.abs(i / WAVE_BARS.toFloat() - sweep) < 0.12f
            drawRoundRect(
                color = if (lit) Ink2 else live,
                topLeft = Offset(x, mid - h / 2),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2),
            )
        }
    }
}

/** Examples, set as speech. The point is permission: it does not have to be tidy. */
@Composable
private fun Examples() {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text("Say it in your own words. For example:", style = MaterialTheme.typography.titleMedium, color = Ink2)
        listOf(
            "they said my Aadhaar is in a parcel case",
            "a policeman wants twenty thousand rupees now",
            "he told me not to tell my family",
        ).forEach { Quote(label = null, text = it, ink = Ink2) }
    }
}

/** Speech on the page: a hanging quotation mark, the words set as a line to be read aloud. */
@Composable
private fun Quote(label: String?, text: String, ink: Color) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text("“", style = MaterialTheme.typography.displaySmall, color = Guard300, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f).padding(top = 6.dp)) {
            label?.let { Text(it, style = KType.utility, color = Muted) }
            Text(text, style = MaterialTheme.typography.titleMedium, color = ink)
        }
    }
}

@Composable
private fun AnswerSheet(
    r: ScanResult,
    lang: String,
    ctx: android.content.Context,
    speaker: Speaker,
) {
    val scam = r.verdict == Verdict.SCAM || r.verdict == Verdict.SUSPICIOUS
    val tone = when (r.verdict) {
        Verdict.SCAM -> Tone.Danger
        Verdict.SUSPICIOUS -> Tone.Checking
        else -> Tone.Neutral
    }
    val reply = if (scam) Copy.askScamAnswer(lang) else Copy.askSafeAnswer(lang)
    val lines = remember(r) { scanLines(r, "What you said") }
    val stage = rememberStage(r, verdictSchedule(tallyRows(lines.size)))
    val revealed = (stage - 2).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Sheet(padding = Space.xl, spacing = Space.md) {
            VerdictHead(
                sign = when (r.verdict) {
                    Verdict.SCAM -> "Scam"
                    Verdict.SUSPICIOUS -> "Suspicious"
                    else -> "No scam signs"
                },
                headline = when (r.verdict) {
                    Verdict.SCAM -> "This is the digital arrest scam"
                    Verdict.SUSPICIOUS -> "This sounds like the scam"
                    else -> "No scam signs heard"
                },
                tone = tone,
                locked = revealed >= tallyRows(lines.size) && stage >= 2,
            )

            StageIn(visible = stage >= 1, from = 24) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        painterResource(R.drawable.ic_speaker),
                        null,
                        tint = tone.solid(),
                        modifier = Modifier.padding(top = 3.dp).size(24.dp),
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(reply, style = MaterialTheme.typography.titleLarge.script(lang), color = tone.ink())
                }
            }

            if (lines.isNotEmpty()) {
                EvidenceTally(
                    lines = lines,
                    total = r.score,
                    tone = tone,
                    revealed = revealed,
                    scale = ScaleSpec(
                        marks = listOf(NoticeMarkers.SPOKEN_SUSPICIOUS_AT, NoticeMarkers.SPOKEN_SCAM_AT),
                        describe = "Score ${r.score} of 100. Suspicious from ${NoticeMarkers.SPOKEN_SUSPICIOUS_AT}, " +
                            "scam from ${NoticeMarkers.SPOKEN_SCAM_AT}.",
                    ),
                )
            }

            BigAction(
                "Say that again",
                style = ActionStyle.Ghost,
                icon = R.drawable.ic_speaker,
                textStyle = MaterialTheme.typography.titleMedium,
            ) { speaker.say(reply, lang) }
        }

        if (scam) {
            BigAction(
                "Call 1930 helpline",
                style = ActionStyle.Danger,
                icon = R.drawable.ic_phone,
                critical = true,
            ) { dialNumber(ctx, "1930") }
            Prefs.guardian(ctx)?.let { guardian ->
                BigAction(
                    "Call my family",
                    style = ActionStyle.Secondary,
                    icon = R.drawable.ic_family,
                    critical = true,
                ) { dialNumber(ctx, guardian) }
            }
        }
    }
}
