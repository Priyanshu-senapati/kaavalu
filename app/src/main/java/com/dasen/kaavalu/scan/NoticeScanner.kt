package com.dasen.kaavalu.scan

import android.util.Log
import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.SignalBus
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * On-device OCR over a photo or screenshot of a notice, warrant or scam message. The models
 * are bundled, so this works with the phone in airplane mode.
 *
 * Scoring lives in [ScamMarkers] so it can be unit tested without a camera, and so the
 * voice screen can run the identical markers over what the user says.
 */
class NoticeScanner {

    // Lazy: building a recognizer touches the Android runtime, which a JVM test does not have.
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    suspend fun scan(image: InputImage): ScanResult {
        val latinText = runCatching { latin.process(image).await().text }
            .onFailure { Log.w(TAG, "latin OCR failed: ${it.message}") }
            .getOrDefault("")
        val devanagariText = runCatching { devanagari.process(image).await().text }
            .onFailure { Log.w(TAG, "devanagari OCR failed: ${it.message}") }
            .getOrDefault("")

        val text = listOf(latinText, devanagariText).filter { it.isNotBlank() }.joinToString("\n")
        val result = ScamMarkers.evaluate(text)

        // Diagnostics: when a scan gets the wrong answer, this is the only way to know
        // whether OCR failed or the marker list is short. adb logcat -s KaavaluScan
        Log.d(TAG, "read ${text.length} chars, score ${result.score}, verdict ${result.verdict}")
        Log.d(TAG, "text: ${text.replace('\n', ' ').take(500)}")
        Log.d(TAG, "matched: ${result.found}")

        if (result.worthRemembering) {
            SignalBus.emit(Signal.NoticeFlagged(result.score, result.found))
        }
        return result
    }

    private companion object {
        const val TAG = "KaavaluScan"
    }
}
