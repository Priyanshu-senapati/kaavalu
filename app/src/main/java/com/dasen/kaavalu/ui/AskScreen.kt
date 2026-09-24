package com.dasen.kaavalu.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.respond.Speaker
import com.dasen.kaavalu.scan.ScamMarkers
import com.dasen.kaavalu.scan.Verdict

/**
 * Ask Kaavalu. The user describes the call aloud and is answered aloud.
 *
 * Two things were wrong in the first version and are fixed here: the transcript was matched
 * only against markers written for printed warrants, so real descriptions of real calls
 * scored nothing and got a reassuring answer; and the screen never showed which words it
 * reacted to, so a wrong answer was impossible to argue with.
 */
@Composable
fun AskScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lang = remember { Prefs.language(ctx) }
    val speaker = remember { Speaker(ctx) }

    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var alarming by remember { mutableStateOf(false) }
    var matched by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
            SpeechRecognizer.createSpeechRecognizer(ctx)
        } else {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { recognizer?.destroy() }
            speaker.shutdown()
        }
    }

    fun respondTo(text: String) {
        heard = text
        val result = ScamMarkers.evaluate(text)
        // Someone only opens this screen because a call already frightened them. SUSPICIOUS
        // is enough to warn: staying quiet is the expensive mistake here, not a false alarm.
        val worrying = result.verdict == Verdict.SCAM || result.verdict == Verdict.SUSPICIOUS
        Log.d(TAG, "heard [$text] -> ${result.score} ${result.verdict} ${result.found}")
        matched = result.found
        alarming = worrying
        val reply = if (worrying) Copy.askScamAnswer(lang) else Copy.askSafeAnswer(lang)
        answer = reply
        speaker.say(reply, lang)
    }

    fun listen() {
        val r = recognizer
        if (r == null) {
            error = "This phone has no speech recognition installed."
            return
        }
        error = null
        answer = null
        matched = emptyList()
        heard = ""
        listening = true
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit

            override fun onError(code: Int) {
                listening = false
                error = when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH ->
                        "I did not catch that. Please say it again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Microphone permission is needed."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech needs the offline language pack or a connection."
                    else -> "Speech recognition failed. Please try again."
                }
            }

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) {
                    error = "I did not catch that. Please say it again."
                } else {
                    respondTo(text)
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "$lang-IN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        runCatching { r.startListening(intent) }.onFailure {
            listening = false
            error = "Could not start listening."
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Ask Kaavalu", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Tell me about the call in your own words. For example: " +
                "\"someone from CBI says my Aadhaar is in a parcel case\".",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center,
        )

        MicButton(listening) { listen() }

        Text(
            if (listening) "Listening..." else "Tap and speak",
            style = MaterialTheme.typography.titleMedium,
            color = if (listening) Alarm else Muted,
        )

        if (heard.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("You said", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Text(heard, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        error?.let {
            Text(it, color = Alarm, style = MaterialTheme.typography.bodyMedium)
        }

        answer?.let { text ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (alarming) Alarm else Guard,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)

                    // Say which words triggered it, so a wrong answer can be argued with.
                    if (matched.isNotEmpty()) {
                        Text(
                            "What I noticed:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gold,
                        )
                        matched.forEach { why ->
                            Row(verticalAlignment = Alignment.Top) {
                                Spacer(
                                    Modifier
                                        .padding(top = 8.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    why,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    if (alarming) {
                        Button(
                            onClick = {
                                runCatching {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Gold,
                                contentColor = Ink,
                            ),
                        ) { Text("Call cyber helpline 1930") }

                        Prefs.guardian(ctx)?.let { number ->
                            Button(
                                onClick = {
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Alarm,
                                ),
                            ) { Text("Call my family") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** A big, obvious target. It breathes while listening so there is no doubt it is live. */
@Composable
private fun MicButton(listening: Boolean, onTap: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "mic")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale",
    )
    Box(
        Modifier
            .size(168.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(if (listening) Alarm else Guard)
            .clickable(onClick = onTap)
            .semantics {
                contentDescription =
                    if (listening) "Listening. Tap to stop." else "Tap and describe the call."
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_nav_ask),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(68.dp),
        )
    }
}

private const val TAG = "KaavaluAsk"
