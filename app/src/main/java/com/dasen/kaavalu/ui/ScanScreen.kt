package com.dasen.kaavalu.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.scan.NoticeScanner
import com.dasen.kaavalu.scan.ScanResult
import com.dasen.kaavalu.scan.Verdict
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Is this notice real?" Scammers send fake warrants, RBI letters and KYC messages over
 * WhatsApp. The OCR models are bundled, so this works in airplane mode.
 *
 * The important rule on this screen: never show a reassuring result for an image we could
 * not actually read. An all-clear the app has not earned is worse than no answer.
 */
@Composable
fun ScanScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { NoticeScanner() }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun runScan(block: suspend () -> ScanResult) {
        busy = true
        error = null
        result = null
        scope.launch {
            runCatching { withContext(Dispatchers.Default) { block() } }
                .onSuccess { result = it }
                .onFailure { error = it.message ?: "Could not read that image." }
            busy = false
        }
    }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap != null) runScan { scanner.scan(InputImage.fromBitmap(bitmap, 0)) }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) runScan { scanner.scan(InputImage.fromFilePath(ctx, uri)) }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Is this notice real?", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Photograph the notice, or pick the screenshot they sent you. " +
                "Nothing leaves this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )

        Button(
            onClick = { camera.launch(null) },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Guard),
        ) { Text("Take a photo") }

        OutlinedButton(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp),
        ) { Text("Pick a screenshot") }

        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Guard, strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text("Reading the notice...", style = MaterialTheme.typography.bodyLarge)
            }
        }

        error?.let {
            VerdictCard(
                background = Caution,
                heading = "Could not read that image",
                body = it,
                points = emptyList(),
            )
        }

        result?.let { r ->
            when (r.verdict) {
                Verdict.SCAM -> VerdictCard(
                    background = Alarm,
                    heading = "This looks like a scam",
                    body = "Do not reply, do not call the number on it, and do not send " +
                        "any money. Show this to a family member.",
                    points = r.found,
                    score = r.score,
                )

                Verdict.SUSPICIOUS -> VerdictCard(
                    background = Caution,
                    heading = "Some warning signs",
                    body = "This is not certain, but parts of it match how scam messages " +
                        "are written. Check with your family before you act on it.",
                    points = r.found,
                    score = r.score,
                )

                // The bug that mattered: an unreadable image used to look like an all-clear.
                Verdict.NO_TEXT -> VerdictCard(
                    background = Caution,
                    heading = "No text could be read",
                    body = "Kaavalu could not read any words in that image, so it has not " +
                        "checked anything. Try a brighter, closer photo of the text.",
                    points = emptyList(),
                )

                Verdict.NOTHING_FOUND -> VerdictCard(
                    background = Guard,
                    heading = "No scam wording found",
                    body = "Kaavalu read the text and none of the usual scam phrases are " +
                        "in it. That does not prove it is genuine. If anyone asks you for " +
                        "money or an OTP, it is a scam whatever the letter says.",
                    points = emptyList(),
                )
            }

            if (r.text.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("What the phone read", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            r.text.take(700),
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VerdictCard(
    background: Color,
    heading: String,
    body: String,
    points: List<String>,
    score: Int? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = background),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(heading, style = MaterialTheme.typography.titleLarge, color = Color.White)
            score?.let {
                Text(
                    "Notice score $it / 100",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.95f),
            )
            points.forEach { why ->
                Row(verticalAlignment = Alignment.Top) {
                    Spacer(
                        Modifier
                            .padding(top = 8.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(why, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
            }
        }
    }
}
