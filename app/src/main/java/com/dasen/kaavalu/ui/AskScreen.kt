package com.dasen.kaavalu.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.respond.Speaker
import com.dasen.kaavalu.scan.NoticeMarkers
import com.dasen.kaavalu.scan.ScanResult
import com.dasen.kaavalu.scan.Verdict

/**
 * Ask Kaavalu. The user describes the call aloud in their own language and is told,
 * aloud, what to do. The transcript is matched against the same marker list the notice
 * scanner uses: one description of the scam, two ways in.
 *
 * Three things here exist because the v1 screen heard the words and still said "safe":
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
    var heard by remember { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var typing by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }

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
        val scored = NoticeMarkers.evaluateSpoken(text)
        result = scored
        val reply = when (scored.verdict) {
            Verdict.SCAM, Verdict.SUSPICIOUS -> Copy.askScamAnswer(lang)
            else -> Copy.askSafeAnswer(lang)
        }
        speaker.say(reply, lang)
    }

    /**
     * [offline] is only set after an online attempt has already failed: it is the airplane-mode
     * escape hatch, not the normal path.
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
        listening = true

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onEndOfSpeech() { listening = false; level = 0f }

            override fun onRmsChanged(rmsdB: Float) {
                // Roughly -2..10 dB in practice. Normalised so the ring has something to show.
                level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
            }

            override fun onError(code: Int) {
                listening = false
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
                    -> "I did not catch that. Speak again, or type what they said."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Microphone permission is needed."
                    else -> "Speech recognition failed. You can type it instead."
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                level = 0f
                val all = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                if (all.isEmpty()) {
                    error = "I did not catch that. Speak again, or type what they said."
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
            error = "Could not start listening. Type it instead."
            typing = true
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) listen(offline = false)
        else {
            error = "Microphone permission is needed. You can type it instead."
            typing = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        ScreenHeader("Ask Kaavalu", "Tell me about the call in your own words.", onBack)

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MicButton(listening, level) {
                if (listening) {
                    runCatching { recognizer?.stopListening() }
                    listening = false
                } else if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    listen(offline = false)
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        Text(
            when {
                listening -> "Listening. Say what they told you."
                heard.isBlank() -> "Tap and say something like: \"a man said he is from CBI and my Aadhaar is in a parcel case\"."
                else -> "Tap to ask again."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            modifier = Modifier.fillMaxWidth(),
        )

        if (partial.isNotBlank()) {
            Panel { Text("“$partial”", style = MaterialTheme.typography.bodyLarge, color = Muted) }
        }
        if (heard.isNotBlank()) {
            Panel {
                Text("You said", style = MaterialTheme.typography.labelMedium, color = Muted)
                Text("“$heard”", style = MaterialTheme.typography.bodyLarge)
            }
        }
        error?.let {
            Panel(background = CautionSoft) {
                Text(it, color = Caution, style = MaterialTheme.typography.bodyMedium)
            }
        }

        result?.let { r -> AnswerPanel(r, lang, ctx, speaker) }

        // The keyboard is never the point, but it is always there: a demo that depends on a
        // microphone in a loud hall is a demo that fails in front of the jury.
        TextButton(onClick = { typing = !typing }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_keyboard), null, Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(if (typing) "Hide the keyboard" else "Type it instead")
            }
        }
        if (typing) {
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text("What did they say?") },
                shape = RoundedCornerShape(14.dp),
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            BigAction("Check these words", enabled = typed.isNotBlank()) { answer(typed) }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AnswerPanel(
    r: ScanResult,
    lang: String,
    ctx: android.content.Context,
    speaker: Speaker,
) {
    val scam = r.verdict == Verdict.SCAM || r.verdict == Verdict.SUSPICIOUS
    val colour = when (r.verdict) {
        Verdict.SCAM -> Alarm
        Verdict.SUSPICIOUS -> Caution
        else -> Guard
    }
    val reply = if (scam) Copy.askScamAnswer(lang) else Copy.askSafeAnswer(lang)

    Panel(background = if (scam) AlarmSoft else Color.White) {
        Text(
            when (r.verdict) {
                Verdict.SCAM -> "That is the digital arrest scam"
                Verdict.SUSPICIOUS -> "That sounds like the scam"
                else -> "I did not hear the scam pattern"
            },
            style = MaterialTheme.typography.titleLarge,
            color = colour,
        )
        Meter(r.score / 100f, colour)
        Text(reply, style = MaterialTheme.typography.bodyLarge)

        if (r.found.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text("What I recognised", style = MaterialTheme.typography.labelMedium, color = Muted)
            r.found.forEach { ReasonRow(null, it, colour) }
        }

        TextButton(onClick = { speaker.say(reply, lang) }) { Text("Say that again") }

        if (scam) {
            BigAction("Call 1930 now", container = Alarm, icon = R.drawable.ic_phone) {
                dialFrom(ctx, "1930")
            }
            Prefs.guardian(ctx)?.let { guardian ->
                BigAction(
                    "Call my family",
                    container = Color.White,
                    content = Guard,
                    icon = R.drawable.ic_family,
                ) { dialFrom(ctx, guardian) }
            }
        }
    }
}

/** A microphone that reacts to the room. Silence on a "Listening..." label reads as broken. */
@Composable
private fun MicButton(listening: Boolean, level: Float, onClick: () -> Unit) {
    val ring by animateFloatAsState(if (listening) 1f + level * 0.35f else 1f, label = "ring")
    Box(Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size(180.dp)
                    .scale(ring)
                    .alpha(0.20f + level * 0.25f)
                    .clip(CircleShape)
                    .background(Alarm),
            )
        }
        Box(
            Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(if (listening) Alarm else Guard)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(R.drawable.ic_nav_ask),
                    contentDescription = if (listening) "Stop listening" else "Speak",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (listening) "Listening" else "Speak",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

private fun dialFrom(ctx: android.content.Context, number: String) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
