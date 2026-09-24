package com.dasen.kaavalu.ui

import android.content.Intent
import android.net.Uri
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.respond.Speaker
import com.dasen.kaavalu.scan.NoticeMarkers

/**
 * Ask Kaavalu. The user describes the call aloud in their own language and is told,
 * aloud, what to do. The transcript is matched against the same marker list the notice
 * scanner uses: one description of the scam, two ways in.
 */
@Composable
fun AskScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val lang = remember { Prefs.language(ctx) }
    val speaker = remember { Speaker(ctx) }

    var listening by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

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

    fun respondTo(text: String) {
        heard = text
        val result = NoticeMarkers.evaluateSpoken(text)
        val reply =
            if (result.score >= 25) Copy.askScamAnswer(lang) else Copy.askSafeAnswer(lang)
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
        heard = ""
        listening = true
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { listening = false }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit

            override fun onError(code: Int) {
                listening = false
                error = when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "I did not catch that. Please try again."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
                    else -> "Speech recognition failed. Please try again."
                }
            }

            override fun onResults(results: android.os.Bundle?) {
                listening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text.isNullOrBlank()) {
                    error = "I did not catch that. Please try again."
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang + "-IN")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        runCatching { r.startListening(intent) }.onFailure {
            listening = false
            error = "Could not start listening."
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Ask Kaavalu", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Tell me about the call in your own words.",
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
        )

        Button(
            onClick = { listen() },
            modifier = Modifier.size(180.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (listening) Alarm else Guard,
            ),
        ) {
            Text(if (listening) "Listening..." else "Speak", style = MaterialTheme.typography.titleLarge)
        }

        if (heard.isNotBlank()) {
            Text("You said: " + heard, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        error?.let { Text(it, color = Alarm, style = MaterialTheme.typography.bodyMedium) }

        answer?.let { text ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Alarm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
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
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Gold,
                            contentColor = Ink,
                        ),
                    ) { Text("Call 1930") }
                }
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(Modifier.height(24.dp))
    }
}
