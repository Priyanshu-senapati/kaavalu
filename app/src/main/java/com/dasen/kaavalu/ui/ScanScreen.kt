package com.dasen.kaavalu.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.scan.NoticeScanner
import com.dasen.kaavalu.scan.ScanResult
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Is this notice real?" Scammers send fake arrest warrants and RBI letters over WhatsApp.
 * The OCR models are bundled, so this whole screen works in airplane mode.
 */
@Composable
fun ScanScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { NoticeScanner() }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(bitmap: Bitmap?) {
        if (bitmap == null) return
        busy = true
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    scanner.scan(InputImage.fromBitmap(bitmap, 0))
                }
            }.onSuccess { result = it }
                .onFailure { error = it.message ?: "Could not read that image." }
            busy = false
        }
    }

    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { run(it) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    scanner.scan(InputImage.fromFilePath(ctx, uri))
                }
            }.onSuccess { result = it }
                .onFailure { error = it.message ?: "Could not read that image." }
            busy = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Is this notice real?", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Photograph the notice, or pick the screenshot they sent you. Nothing leaves this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )

        Button(
            onClick = { camera.launch(null) },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Guard),
        ) { Text("Take a photo") }

        Button(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Guard),
        ) { Text("Pick a screenshot") }

        if (busy) Text("Reading the notice...", style = MaterialTheme.typography.bodyLarge)
        error?.let { Text(it, color = Alarm, style = MaterialTheme.typography.bodyMedium) }

        result?.let { r ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (r.flagged) Alarm else Guard,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (r.flagged) "This looks like a scam notice"
                        else "No strong scam markers found",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                    Text(
                        "Notice score " + r.score + "/100",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    r.found.forEach {
                        Text("- " + it, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (r.found.isEmpty()) {
                        Text(
                            "Kaavalu found none of the usual scam phrases. That does not prove the notice is genuine: if in doubt, call your family.",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (r.text.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("What the phone read", style = MaterialTheme.typography.titleMedium)
                        Text(
                            r.text.take(600),
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
        Spacer(Modifier.height(24.dp))
    }
}
