package com.dasen.kaavalu.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.ScanCopy
import com.dasen.kaavalu.R
import com.dasen.kaavalu.scan.Cue
import com.dasen.kaavalu.scan.NoticeMarkers
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
 * The screen is one inspection instrument rather than a column of cards. The same window
 * starts as a viewfinder, fills with the photo while a band of light reads it, then shrinks
 * to a strip at the head of the verdict, so the user sees the thing they gave the phone
 * turn into the answer about it.
 *
 * The camera writes a full-size JPEG through a FileProvider rather than handing back the
 * preview thumbnail. That one change is the difference between OCR reading a warrant and OCR
 * reading nothing at all and the screen then reporting "no scam markers found".
 */
@Composable
fun ScanScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { NoticeScanner() }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var showText by rememberSaveable { mutableStateOf(false) }

    val photoUri = remember {
        val dir = File(ctx.cacheDir, "scans").apply { mkdirs() }
        FileProvider.getUriForFile(ctx, ctx.packageName + ".scans", File(dir, "notice.jpg"))
    }

    fun read(uri: Uri) {
        busy = true
        error = null
        showText = false
        result = null
        scope.launch {
            // The picture first, so the reading has something to happen over.
            preview = decodePreview(ctx, uri)
            runCatching {
                withContext(Dispatchers.Default) {
                    scanner.scan(InputImage.fromFilePath(ctx, uri))
                }
            }.onSuccess { result = it }
                .onFailure {
                    result = null
                    error = "That image didn’t open. Pick the screenshot from your gallery instead."
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
        else error = "Kaavalu needs the camera to photograph the notice. You can pick a screenshot instead."
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(::read) }

    fun photograph() {
        if (ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            camera.launch(photoUri)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    val phase: ScanPhase = when {
        busy -> ScanPhase.Reading
        error != null -> ScanPhase.Failed(error!!)
        result != null -> ScanPhase.Done(result!!)
        else -> ScanPhase.Ready
    }

    ScreenColumn(spacing = Space.lg) {
        ScreenTitle(
            title = "Is this notice real?",
            lead = "Checked on this phone. Nothing is uploaded.",
        )

        Inspector(phase, preview)

        // Everything below the window follows the phase. One AnimatedContent, so the
        // capture buttons give way to the verdict rather than both being on screen at once.
        val inMs = motion(Motion.EMPHASIZED)
        val outMs = motion(Motion.QUICK)
        AnimatedContent(
            targetState = phase,
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(tween(inMs, easing = Motion.enter)) + slideInVertically(tween(inMs, easing = Motion.enter)) { it / 16 })
                    .togetherWith(fadeOut(tween(outMs)))
                    .using(SizeTransform(clip = false) { _, _ -> tween(inMs, easing = Motion.enter) })
            },
            label = "scanBody",
        ) { state ->
            when (state) {
                ScanPhase.Ready, is ScanPhase.Failed -> Column(verticalArrangement = Arrangement.spacedBy(Space.section)) {
                    CaptureActions(onPhotograph = ::photograph, onPick = ::pick)
                    WhatItLooksFor()
                }

                ScanPhase.Reading -> Column(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(Space.xxs),
                ) {
                    Text("Reading the notice…", style = MaterialTheme.typography.headlineSmall, color = Ink)
                    Text(
                        "Checking the words for ${NoticeMarkers.MARKERS.map { it.id }.distinct().size} scam signs, on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }

                is ScanPhase.Done -> Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    val r = state.result
                    VerdictSheet(r)

                    if (r.verdict == Verdict.SCAM || r.verdict == Verdict.SUSPICIOUS) {
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
                    if (r.verdict == Verdict.UNREADABLE) {
                        CaptureActions(onPhotograph = ::photograph, onPick = ::pick, retake = true)
                    }

                    if (r.text.isNotBlank()) {
                        BigAction(
                            if (showText) "Hide the text Kaavalu read" else "Show the text Kaavalu read",
                            style = ActionStyle.Ghost,
                            textStyle = MaterialTheme.typography.titleMedium,
                        ) { showText = !showText }
                        AnimatedVisibility(
                            visible = showText,
                            enter = fadeIn(tween(motion(Motion.QUICK))) + expandVertically(tween(motion(Motion.STANDARD), easing = Motion.enter)),
                            exit = fadeOut(tween(motion(Motion.QUICK))) + shrinkVertically(tween(motion(Motion.QUICK))),
                        ) {
                            Well {
                                Text(r.text.take(900), style = MaterialTheme.typography.bodySmall, color = Ink2)
                            }
                        }
                    }

                    if (r.verdict != Verdict.UNREADABLE) {
                        BigAction("Check another notice", style = ActionStyle.Secondary) {
                            result = null
                            error = null
                            preview = null
                        }
                    }
                }
            }
        }
    }
}

private suspend fun decodePreview(ctx: android.content.Context, uri: Uri): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(ctx.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > 1400) {
                    val k = 1400f / longest
                    decoder.setTargetSize((info.size.width * k).toInt(), (info.size.height * k).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.asImageBitmap()
        }.getOrNull()
    }

/**
 * The inspection window. One object across every phase: a viewfinder when empty, the photo
 * under a reading sweep while OCR runs, a narrow strip of the photo once there is a verdict.
 */
@Composable
private fun Inspector(phase: ScanPhase, preview: ImageBitmap?) {
    val tall = phase is ScanPhase.Ready || phase is ScanPhase.Failed || phase is ScanPhase.Reading
    val height = when {
        phase is ScanPhase.Done && preview == null -> 0.dp
        tall -> 232.dp
        else -> 104.dp
    }
    val shape = RoundedCornerShape(Radius.lg)
    Box(
        Modifier
            .fillMaxWidth()
            .animateContentSize(kSpring())
            .height(height)
            .clip(shape)
            .background(if (preview != null && phase !is ScanPhase.Ready) Ink else PaperDeep),
    ) {
        val showPhoto = preview != null && (phase is ScanPhase.Reading || phase is ScanPhase.Done)
        if (showPhoto) {
            Image(
                preview!!,
                contentDescription = "The notice you gave Kaavalu",
                contentScale = ContentScale.Crop,
                // Anchored at the top: a notice announces itself in its heading.
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        when (phase) {
            ScanPhase.Ready -> {
                FrameCorners(Ink2.copy(alpha = 0.55f))
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = Space.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(painterResource(R.drawable.ic_nav_scan), null, tint = Muted, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "Fill the frame with the page. Keep it sharp.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
            }

            ScanPhase.Reading -> {
                if (!showPhoto) FrameCorners(Ink2.copy(alpha = 0.55f))
                ReadingSweep(if (showPhoto) Gold else GoldDeep)
                SignTag(
                    "Reading",
                    container = Caution,
                    content = Color.White,
                    live = true,
                    modifier = Modifier.align(Alignment.BottomStart).padding(Space.md),
                )
            }

            is ScanPhase.Failed -> {
                FrameCorners(Caution500)
                Row(
                    Modifier.align(Alignment.Center).padding(horizontal = Space.xl),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(painterResource(R.drawable.ic_warning), null, tint = Caution900, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        phase.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Caution900,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }

            is ScanPhase.Done -> Unit
        }
    }
}

@Composable
private fun CaptureActions(onPhotograph: () -> Unit, onPick: () -> Unit, retake: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        BigAction(
            if (retake) "Take the photo again" else "Photograph the notice",
            icon = R.drawable.ic_camera,
            critical = true,
            onClick = onPhotograph,
        )
        BigAction(
            "Choose a screenshot",
            style = ActionStyle.Secondary,
            icon = R.drawable.ic_image,
            critical = true,
            onClick = onPick,
        )
    }
}

internal fun cueName(c: Cue, lang: String = "en") = ScanCopy.cue(lang, c.name)

/**
 * A scan's evidence as tally lines, with the combination bonus as its own honest line.
 *
 * Read through ScanCopy by marker id, not straight off the marker's English text: the
 * verdict used to answer in English on a phone whose every other screen was Kannada.
 */
internal fun scanLines(r: ScanResult, where: String, lang: String = "en"): List<EvidenceLine> =
    r.evidence.map {
        EvidenceLine(
            it.points,
            ScanCopy.markerReason(lang, it.id, it.why),
            "$where · ${cueName(it.cue, lang)}",
        )
    } +
        if (r.comboBonus > 0) {
            listOf(
                EvidenceLine(
                    r.comboBonus,
                    ScanCopy.combination(lang),
                    "$where · " + ScanCopy.cue(lang, "COMBINATION"),
                ),
            )
        } else {
            emptyList()
        }

/** The schedule a verdict unfolds on: head, instruction, then each line of evidence. */
internal fun verdictSchedule(lines: Int): List<Long> = buildList {
    add(Choreo.HEADLINE_IN + 60)
    add(Choreo.REASONS_IN)
    repeat(lines) { add(Choreo.REASONS_IN) }
}

/**
 * Four verdicts, not two. "No known phrases" is never an all-clear, and a notice the phone
 * could not read is never scored as innocent.
 */
@Composable
private fun VerdictSheet(r: ScanResult) {
    val lang = Prefs.language(LocalContext.current)
    val lines = remember(r, lang) { scanLines(r, ScanCopy.noticeScan(lang), lang) }
    val stage = rememberStage(r, verdictSchedule(tallyRows(lines.size)))
    val revealed = (stage - 2).coerceAtLeast(0)
    val locked = revealed >= tallyRows(lines.size) && stage >= 2

    val tone = when (r.verdict) {
        Verdict.SCAM -> Tone.Danger
        Verdict.SUSPICIOUS -> Tone.Checking
        else -> Tone.Neutral
    }
    val sign = ScanCopy.verdictSign(lang, r.verdict.name)
    val headline = ScanCopy.verdictHeadline(lang, r.verdict.name)

    Sheet(padding = Space.xl, spacing = Space.md) {
        VerdictHead(sign, headline, tone, locked = locked)

        when (r.verdict) {
            Verdict.SCAM, Verdict.SUSPICIOUS -> StageIn(visible = stage >= 1, from = 24) {
                Instruction(ScanCopy.instruction(lang), tone)
            }

            Verdict.UNCLEAR -> Text(
                ScanCopy.unclearBody(lang),
                style = MaterialTheme.typography.bodyLarge,
                color = Ink2,
            )

            Verdict.UNREADABLE -> Text(
                ScanCopy.unreadableBody(lang),
                style = MaterialTheme.typography.bodyLarge,
                color = Ink2,
            )
        }

        if (lines.isNotEmpty()) {
            Spacer(Modifier.height(Space.xxs))
            Text(
                "What Kaavalu found",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                modifier = Modifier.semantics { heading() },
            )
            EvidenceTally(
                lines = lines,
                total = r.score,
                tone = tone,
                revealed = revealed,
                scale = ScaleSpec(
                    marks = listOf(NoticeMarkers.NOTICE_SUSPICIOUS_AT, NoticeMarkers.NOTICE_SCAM_AT),
                    describe = "Score ${r.score} of 100. Suspicious from ${NoticeMarkers.NOTICE_SUSPICIOUS_AT}, " +
                        "scam from ${NoticeMarkers.NOTICE_SCAM_AT}.",
                ),
            )
        }
    }
}

/** The one thing to do, set as an instruction rather than boxed as a callout. */
@Composable
internal fun Instruction(text: String, tone: Tone) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            painterResource(R.drawable.ic_warning),
            null,
            tint = tone.solid(),
            modifier = Modifier.padding(top = 2.dp).size(24.dp),
        )
        Spacer(Modifier.width(Space.sm))
        Text(text, style = MaterialTheme.typography.titleLarge, color = tone.ink())
    }
}

/**
 * The pressures a scam notice stacks. Shown while the window is empty, so the user knows
 * what is being checked before anything is checked. Set as a two-column reference list,
 * the cue names aligned on one edge.
 */
@Composable
private fun WhatItLooksFor() {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        SectionTitle("What Kaavalu looks for")
        Text(
            "Scam notices use several of these together. One on its own is usually harmless.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
        )
        Sheet(padding = Space.lg, spacing = 0.dp) {
            listOf(
                "Authority" to "Names an agency, a court or an officer",
                "Threat" to "Arrest, jail, a blocked SIM, a frozen account",
                "Money" to "A fee, a transfer, a QR code, an OTP",
                "Secrecy" to "Tells you not to involve your family",
                "Urgency" to "Puts a clock on it",
            ).forEachIndexed { i, (cue, what) ->
                if (i > 0) HRule()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.sm).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(cue, style = MaterialTheme.typography.titleSmall, color = Guard500, modifier = Modifier.width(104.dp))
                    Text(what, style = MaterialTheme.typography.bodyMedium, color = Ink2, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * What the scan is doing. Each phase carries its own payload so the outgoing content keeps
 * rendering what it was showing, rather than reading state the screen has already cleared.
 */
private sealed interface ScanPhase {
    data object Ready : ScanPhase
    data object Reading : ScanPhase
    data class Failed(val message: String) : ScanPhase
    data class Done(val result: ScanResult) : ScanPhase
}
