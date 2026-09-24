package com.dasen.kaavalu.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.scan.NoticeScanner
import com.dasen.kaavalu.scan.ScanResult
import com.dasen.kaavalu.scan.Verdict
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Is this notice real?" Scammers send fake arrest warrants and RBI letters over WhatsApp.
 * The OCR models are bundled, so this whole screen works in airplane mode.
 *
 * The camera writes a full-size JPEG through a FileProvider rather than handing back the
 * preview thumbnail. That one change is the difference between OCR reading a warrant and
 * OCR reading nothing at all and the screen then reporting "no scam markers found".
 */
@Composable
fun ScanScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { NoticeScanner() }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showText by remember { mutableStateOf(false) }

    val photoUri = remember {
        val dir = File(ctx.cacheDir, "scans").apply { mkdirs() }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".scans", File(dir, "notice.jpg"))
    }

    fun read(uri: Uri) {
        busy = true
        error = null
        showText = false
        scope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    scanner.scan(InputImage.fromFilePath(ctx, uri))
                }
            }.onSuccess { result = it }
                .onFailure {
                    result = null
                    error = "Could not open that image. Try picking the screenshot instead."
                }
            busy = false
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) read(photoUri) else error = null
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) camera.launch(photoUri)
        else error = "Camera permission is needed to photograph the notice."
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(::read) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        ScreenHeader(
            "Is this notice real?",
            "Nothing leaves this phone. The reading happens here.",
            onBack,
        )

        BigAction("Take a photo of the notice", icon = R.drawable.ic_nav_scan) {
            if (ctx.checkSelfPermission(Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                camera.launch(photoUri)
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
        BigAction("Pick the screenshot they sent", container = Color.White, content = Guard) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        Text(
            "Hold the phone flat, fill the frame with the page, and keep it in focus. " +
                "Kaavalu can only judge the words it can actually read.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
        )

        if (busy) {
            Panel {
                Text("Reading the notice...", style = MaterialTheme.typography.titleMedium)
                Meter(1f, Gold)
            }
        }
        error?.let {
            Panel(background = AlarmSoft) {
                Text(it, color = Alarm, style = MaterialTheme.typography.bodyMedium)
            }
        }

        result?.let { r ->
            VerdictPanel(r)

            if (r.verdict == Verdict.SCAM || r.verdict == Verdict.SUSPICIOUS) {
                BigAction("Call 1930 now", container = Alarm, icon = R.drawable.ic_phone) {
                    dial(ctx, "1930")
                }
                Prefs.guardian(ctx)?.let { guardian ->
                    BigAction(
                        "Call my family",
                        container = Color.White,
                        content = Guard,
                        icon = R.drawable.ic_family,
                    ) { dial(ctx, guardian) }
                }
            }

            if (r.text.isNotBlank()) {
                TextButton(onClick = { showText = !showText }) {
                    Text(if (showText) "Hide what the phone read" else "Show what the phone read")
                }
                if (showText) {
                    Panel {
                        Text(
                            r.text.take(900),
                            style = MaterialTheme.typography.bodySmall,
                            color = Muted,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { result = null; error = null },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Check another notice") }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Four verdicts, not two. The v1 screen had only "scam" and "no strong markers found", and
 * the second one reads as an all-clear — which is exactly the wrong thing to tell someone
 * holding a notice the phone could not read, or one that used none of the stock phrases.
 */
@Composable
private fun VerdictPanel(r: ScanResult) {
    val (headline, tone, colour) = when (r.verdict) {
        Verdict.SCAM -> Triple("This is a scam notice", AlarmSoft, Alarm)
        Verdict.SUSPICIOUS -> Triple("This has the marks of a scam", CautionSoft, Caution)
        Verdict.UNCLEAR -> Triple("No known scam phrases found", Color.White, Guard)
        Verdict.UNREADABLE -> Triple("Kaavalu could not read that", Color.White, Muted)
    }

    Panel(background = tone) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(headline, style = MaterialTheme.typography.titleLarge, color = colour)
        }

        if (r.verdict != Verdict.UNREADABLE) {
            Meter(r.score / 100f, colour)
            Text(
                "Notice score ${r.score} / 100",
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
            )
        }

        when (r.verdict) {
            Verdict.SCAM, Verdict.SUSPICIOUS -> {
                Text(
                    "Do not reply, do not pay, do not call the number on it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colour,
                )
                Spacer(Modifier.height(2.dp))
                r.found.forEach { ReasonRow(null, it, colour) }
            }

            Verdict.UNCLEAR -> Text(
                "Kaavalu found none of the phrases these scams usually use. That is not proof " +
                    "the notice is genuine — a real agency posts a letter, it does not send one " +
                    "on WhatsApp. If in doubt, call your family before you do anything it asks.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )

            Verdict.UNREADABLE -> Text(
                "Almost no text came back from that image. Take the photo again in better light, " +
                    "closer to the page and without a shadow across it — or pick the original " +
                    "screenshot from the gallery, which is always sharper than a photo of a screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
            )
        }
    }
}

private fun dial(ctx: android.content.Context, number: String) {
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
