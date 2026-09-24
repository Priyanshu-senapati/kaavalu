package com.dasen.kaavalu.scan

import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.SignalBus
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

data class Marker(val pattern: Regex, val weight: Int, val why: String)

data class ScanResult(val score: Int, val found: List<String>, val text: String) {
    val flagged: Boolean get() = score >= 40
}

/**
 * On-device OCR over a photo or screenshot of a "warrant" or "notice". The models are
 * bundled, so this works with the phone in airplane mode.
 *
 * Use with ActivityResultContracts.TakePicturePreview() or PickVisualMedia, then
 * InputImage.fromBitmap(bitmap, 0).
 */
class NoticeScanner {

    // Lazy: building a recognizer touches the Android runtime, and the marker list has to
    // stay usable, and unit testable, without it.
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    suspend fun scan(image: InputImage): ScanResult {
        val text = latin.process(image).await().text + "\n" + devanagari.process(image).await().text
        val result = NoticeMarkers.evaluate(text)
        if (result.flagged) SignalBus.emit(Signal.NoticeFlagged(result.score, result.found))
        return result
    }
}

/**
 * The scam markers, kept apart from the camera: text in, score out. That makes the list
 * testable on the JVM, and lets "Ask Kaavalu" run the same markers over what the user
 * says out loud. One description of the scam, two ways in.
 */
object NoticeMarkers {

    /** For a scanned notice: the written markers only. */
    fun evaluate(raw: String): ScanResult = score(raw, MARKERS)

    /**
     * For "Ask Kaavalu". Someone describing the call out loud says "they said my Aadhaar
     * is in a parcel case", not "NON-BAILABLE ARREST WARRANT", so the written markers alone
     * miss the very script this app exists to catch. These cues are added on top.
     */
    fun evaluateSpoken(raw: String): ScanResult = score(raw, MARKERS + SPOKEN)

    private fun score(raw: String, markers: List<Marker>): ScanResult {
        val t = raw.lowercase()
        val hits = markers.filter { it.pattern.containsMatchIn(t) }
        return ScanResult(hits.sumOf { it.weight }.coerceAtMost(100), hits.map { it.why }, raw)
    }

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    val MARKERS = listOf(
        Marker(
            r("""digital\s*arrest|डिजिटल\s*अरेस्ट"""), 40,
            "Says \"digital arrest\". This does not exist in Indian law.",
        ),
        Marker(
            r("""do not (disclose|share|tell)|confidential|secrecy|national secret|गोपनीय"""), 20,
            "Demands secrecy. Real agencies never forbid you from telling your family.",
        ),
        Marker(
            r("""safe account|verification account|rbi account|refundable|transfer the (amount|funds)|rtgs"""), 25,
            "Asks you to move money. No agency asks for this.",
        ),
        Marker(
            r("""video call|skype|stay on (the )?call|remain online"""), 15,
            "Tells you to stay on a video call. Agencies do not work this way.",
        ),
        Marker(
            r("""arrest warrant|warrant of arrest|non[- ]bailable|गिरफ्तार|गिरफ़्तार"""), 15,
            "Threatens immediate arrest.",
        ),
        Marker(
            r("""money laundering|pmla|ndps|narcotics|drugs? parcel|contraband|मनी लॉन्ड्रिंग"""), 15,
            "Accuses you of laundering or drug crimes, a common script.",
        ),
        Marker(
            r("""\bcbi\b|enforcement directorate|\bncb\b|customs|\btrai\b|cyber ?crime"""), 10,
            "Names a central agency to frighten you.",
        ),
    )

    /**
     * Spoken-only cues, never applied to a scanned notice where they would over-trigger.
     * "They said my Aadhaar is in a parcel case" is the opening line of the real script and
     * contains none of the written markers above.
     */
    private val SPOKEN = listOf(
        Marker(
            r("""aadhaar|aadhar|आधार|ಆಧಾರ್"""), 15,
            "They brought up your Aadhaar. Agencies do not call about it.",
        ),
        Marker(
            r("""parcel|courier|पार्सल|कूरियर|ಪಾರ್ಸೆಲ್"""), 15,
            "The parcel story is the most common opening line of this scam.",
        ),
        Marker(
            r("""under arrest|arrest me|arrest you|warrant|गिरफ़्तार|गिरफ्तार|ಬಂಧನ"""), 15,
            "They threatened arrest. No agency arrests anyone over a call.",
        ),
        Marker(
            r("""\bpolice\b|officer|inspector|पुलिस|अधिकारी|ಪೊಲೀಸ್|ಅಧಿಕಾರಿ"""), 10,
            "They claimed to be police or an officer.",
        ),
        Marker(
            r("""bank account|my account|\botp\b|खाता|ಖಾತೆ"""), 10,
            "They asked about your bank account.",
        ),
    )
}
