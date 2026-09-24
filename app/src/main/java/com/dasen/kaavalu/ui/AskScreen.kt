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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedButton
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

    // Declared after respondTo so it can call it directly: a local function has to exist
    // before it is referenced.
    val voiceDialog = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        Log.d(TAG, "voice dialog returned: $text")
        if (!text.isNullOrBlank()) respondTo(text) else error = "Nothing was heard."
    }

    /**
     * [explicitLanguage] false lets the recogniser use whatever the phone is already set
     * up for. Asking for "en-IN" specifically fails outright when only the generic English
     * pack is installed, which is a silly reason to lose the feature.
     */
    fun recogniserIntent(preferOffline: Boolean, explicitLanguage: Boolean = true) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            if (explicitLanguage) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "$lang-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "$lang-IN")
            }
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (preferOffline) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

    /**
     * Offline first, because protection must not depend on a connection. But offline
     * recognition needs a language pack the phone may simply not have, and it fails with
     * a code rather than falling back on its own, so the second attempt drops the offline
     * preference before giving up.
     */
    fun listen(preferOffline: Boolean = true) {
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
                Log.w(TAG, "recogniser error $code (${describe(code)}), offline=$preferOffline")

                // The offline pack is missing or unusable: try again over the network
                // before telling the user anything.
                val retryOnline = preferOffline && code in RETRYABLE
                if (retryOnline) {
                    Log.d(TAG, "retrying without EXTRA_PREFER_OFFLINE")
                    listen(preferOffline = false)
                    return
                }
                error = "${describe(code)} (code $code)"
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
        // The retry also drops the explicit language, which is the other common cause.
        val intent = recogniserIntent(preferOffline, explicitLanguage = preferOffline)
        runCatching { r.startListening(intent) }.onFailure {
            listening = false
            Log.e(TAG, "startListening threw: ${it.message}")
            error = "Could not start listening: ${it.message}"
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
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    it,
                    color = Alarm,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                // Last resort: Google's own voice dialog. It uses a different path to the
                // recogniser, so it often works when the in-app one will not.
                OutlinedButton(
                    onClick = {
                        runCatching { voiceDialog.launch(recogniserIntent(preferOffline = false)) }
                            .onFailure { e ->
                                error = "No voice input app on this phone: ${e.message}"
                            }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) { Text("Use Google voice input instead") }
            }
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

/** Error codes worth a second attempt over the network before bothering the user. */
private val RETRYABLE = setOf(
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
    SpeechRecognizer.ERROR_CLIENT,
)

/**
 * Plain words for every code, because "Speech recognition failed" told the user nothing
 * and told us nothing either. The code is shown alongside so a report is actionable.
 */
private fun describe(code: Int): String = when (code) {
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "The speech service timed out"
    SpeechRecognizer.ERROR_NETWORK -> "No connection, and no offline language pack"
    SpeechRecognizer.ERROR_AUDIO -> "The microphone could not be read"
    SpeechRecognizer.ERROR_SERVER -> "The speech service refused the request"
    SpeechRecognizer.ERROR_CLIENT -> "The speech service is not set up on this phone"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I did not hear anything. Tap and speak"
    SpeechRecognizer.ERROR_NO_MATCH -> "I did not catch that. Please say it again"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Still finishing the last one. Try again"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed"
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many tries. Wait a moment"
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "The speech service disconnected"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "This language is not supported here"
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "The language pack is not downloaded"
    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "Could not check language support"
    else -> "Speech recognition failed"
}
