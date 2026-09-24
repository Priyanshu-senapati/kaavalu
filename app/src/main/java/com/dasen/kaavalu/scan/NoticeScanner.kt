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
 * What kind of pressure a marker represents. A real scam notice always stacks several of
 * these: an authority, a threat, a demand for money, a demand for silence, a deadline.
 * A single one on its own is almost always innocent — "police station" on a courier slip,
 * "CBI" in a newspaper clipping — which is why the combination is scored separately.
 */
enum class Cue {
    /** Claims to be an agency, a court or an officer. */
    AUTHORITY,

    /** Threatens arrest, jail, a frozen account, a blocked SIM. */
    THREAT,

    /** Asks for money, a fee, a transfer, a QR scan. */
    MONEY,

    /** Demands that you tell nobody. */
    SECRECY,

    /** Puts a clock on it. */
    URGENCY,

    /** Keeps you on the call, away from family, police or your bank. */
    ISOLATION,

    /** Names your Aadhaar, PAN, SIM or bank account. */
    IDENTITY,

    /** The parcel/courier cover story, or a WhatsApp/Telegram/QR contact route. */
    STORY,
    ;

    /**
     * The five cues that carry the scam on their own. STORY and IDENTITY are supporting
     * evidence: plenty of honest letters mention a parcel or an Aadhaar number, so they
     * add points but never earn the combination bonus by themselves.
     */
    val isCore: Boolean get() = this in CORE

    private companion object {
        val CORE = setOf(AUTHORITY, THREAT, MONEY, SECRECY, URGENCY, ISOLATION)
    }
}

/**
 * [id] lets the written and spoken lists describe the same idea with different wording and
 * different weights: "Ask Kaavalu" hears "they said something about my Aadhaar", a printed
 * notice says "YOUR AADHAAR IS LINKED TO". Only the strongest hit per id is counted, so the
 * same fact never scores twice.
 */
data class Marker(
    val id: String,
    val pattern: Regex,
    val weight: Int,
    val cue: Cue,
    val why: String,
)

enum class Verdict {
    /** Enough of the script is present to say so plainly. */
    SCAM,

    /** Some of the script is present. Say so; never say "safe". */
    SUSPICIOUS,

    /** Nothing known matched. That is not the same as genuine, and the copy must say so. */
    UNCLEAR,

    /** Nothing legible came back from OCR. Do not score an empty string as innocent. */
    UNREADABLE,
}

data class ScanResult(
    val score: Int,
    val found: List<String>,
    val text: String,
    val verdict: Verdict,
    val cues: Set<Cue> = emptySet(),
) {
    /** Whether this is worth putting on the risk engine's bus. */
    val flagged: Boolean get() = verdict == Verdict.SCAM || verdict == Verdict.SUSPICIOUS
}

/**
 * On-device OCR over a photo or screenshot of a "warrant" or "notice". The models are
 * bundled, so this works with the phone in airplane mode.
 *
 * Feed it a full-resolution image. A camera *thumbnail* carries far too few pixels for OCR
 * to read body text, and text the scanner never sees is a marker that can never fire.
 */
class NoticeScanner {

    // Lazy: building a recognizer touches the Android runtime, and the marker list has to
    // stay usable, and unit testable, without it.
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari by lazy {
        TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    }

    suspend fun scan(image: InputImage): ScanResult {
        // One recognizer failing must not lose the other one's text: a Devanagari model that
        // is still unpacking would otherwise take the whole scan down with it.
        val latinText = runCatching { latin.process(image).await().text }.getOrDefault("")
        val devanagariText = runCatching { devanagari.process(image).await().text }.getOrDefault("")
        val text = (latinText + "\n" + devanagariText).trim()

        val result = NoticeMarkers.evaluate(text)

        // Diagnostics, restored after the merge dropped them. When a scan gives the wrong
        // answer, this is the only way to tell a failed read from a short marker list, and
        // the character count is what distinguishes a real photo from a camera thumbnail.
        //   adb logcat -s KaavaluScan
        Log.d(
            TAG,
            "read ${text.length} chars (latin ${latinText.length}, " +
                "devanagari ${devanagariText.length}) -> ${result.score} ${result.verdict}",
        )
        text.lineSequence().joinToString(" ").chunked(900).forEachIndexed { i, part ->
            Log.d(TAG, "text[$i]: $part")
        }
        Log.d(TAG, "matched: ${result.found}")

        if (result.flagged) SignalBus.emit(Signal.NoticeFlagged(result.score, result.found))
        return result
    }

    private companion object {
        const val TAG = "KaavaluScan"
    }
}

/**
 * The scam markers, kept apart from the camera: text in, score out. That makes the list
 * testable on the JVM, and lets "Ask Kaavalu" run the same markers over what the user
 * says out loud. One description of the scam, two ways in.
 */
object NoticeMarkers {

    /** Below this many readable characters, OCR did not get a document, it got a blur. */
    const val MIN_READABLE_CHARS = 14

    /** A scanned notice: the written markers. */
    fun evaluate(raw: String): ScanResult = score(raw, MARKERS)

    /**
     * For "Ask Kaavalu". Someone describing the call out loud says "they said my Aadhaar
     * is in a parcel case", not "NON-BAILABLE ARREST WARRANT", so the written markers alone
     * miss the very script this app exists to catch. These cues are added on top, and where
     * they share an id with a written marker the stronger of the two wins.
     */
    fun evaluateSpoken(raw: String): ScanResult = score(raw, MARKERS + SPOKEN, spoken = true)

    private fun score(raw: String, markers: List<Marker>, spoken: Boolean = false): ScanResult {
        val t = normalise(raw)
        if (t.length < MIN_READABLE_CHARS) {
            return ScanResult(0, emptyList(), raw, Verdict.UNREADABLE)
        }

        // Only the strongest hit per id, so "aadhaar" written and "aadhaar" spoken are one fact.
        val hits = markers
            .filter { it.pattern.containsMatchIn(t) }
            .groupBy { it.id }
            .map { (_, sameIdea) -> sameIdea.maxBy { it.weight } }

        val cues = hits.map { it.cue }.toSet()
        val base = hits.sumOf { it.weight }
        val total = (base + comboBonus(cues)).coerceAtMost(100)

        val scamAt = if (spoken) SPOKEN_SCAM_AT else NOTICE_SCAM_AT
        val suspiciousAt = if (spoken) SPOKEN_SUSPICIOUS_AT else NOTICE_SUSPICIOUS_AT
        val verdict = when {
            total >= scamAt -> Verdict.SCAM
            total >= suspiciousAt -> Verdict.SUSPICIOUS
            else -> Verdict.UNCLEAR
        }

        return ScanResult(
            score = total,
            // Strongest reason first: the user reads two lines, not nine.
            found = hits.sortedByDescending { it.weight }.map { it.why },
            text = raw,
            verdict = verdict,
            cues = cues,
        )
    }

    /**
     * The scam is a *combination*, and scoring it as one is what stops an honest letter that
     * happens to say "police" from being flagged while a notice that never uses the words
     * "digital arrest" still is.
     */
    private fun comboBonus(cues: Set<Cue>): Int = when (cues.count { it.isCore }) {
        0, 1 -> 0
        2 -> 12
        3 -> 24
        else -> 34
    }

    /**
     * OCR gives ragged line breaks, stray punctuation and runs of spaces, and a phrase split
     * across two lines is a phrase the regex never sees. Flatten it all to single spaces.
     */
    private fun normalise(raw: String): String = raw
        .lowercase()
        .replace('’', '\'')
        .replace(Regex("""[^\p{L}\p{N}'ऀ-ॿಀ-೿]+"""), " ")
        .trim()

    private const val NOTICE_SCAM_AT = 55
    private const val NOTICE_SUSPICIOUS_AT = 28
    private const val SPOKEN_SCAM_AT = 45
    private const val SPOKEN_SUSPICIOUS_AT = 22

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    /**
     * Written markers. Every pattern here was widened from the v1 list after a real fake
     * notice scored 25 and was reported back as "not a scam": the phrase "digital arrest"
     * almost never appears on the document itself, it is what the caller says out loud.
     */
    val MARKERS = listOf(
        Marker(
            "digital-arrest",
            r("""digital\s*arrest|डिजिटल\s*अरेस्ट|ಡಿಜಿಟಲ್\s*ಅರೆಸ್ಟ್"""),
            40, Cue.THREAT,
            "Says \"digital arrest\". This does not exist in Indian law.",
        ),
        Marker(
            "secrecy",
            r(
                """do not (disclose|share|tell|inform|reveal)|not to (disclose|inform|tell)""" +
                    """|strictly confidential|confidential(ity)? (order|clause)|official secrets""" +
                    """|national secret|keep this (matter )?secret|गोपनीय|किसी को (मत|ना) बता""" +
                    """|ಯಾರಿಗೂ ಹೇಳಬೇಡಿ|ಗೌಪ್ಯ"""
            ),
            22, Cue.SECRECY,
            "Demands secrecy. Real agencies never forbid you from telling your family.",
        ),
        Marker(
            "move-money",
            r(
                """safe (account|custody)|verification account|rbi account|refundable""" +
                    """|transfer (the )?(amount|funds|money|balance)|remit the|rtgs|neft|imps""" +
                    """|deposit .{0,24}(account|amount)|scan (the |this )?qr|upi id""" +
                    """|सुरक्षित खाता|ಸುರಕ್ಷಿತ ಖಾತೆ"""
            ),
            26, Cue.MONEY,
            "Asks you to move money. No agency asks for this.",
        ),
        Marker(
            "stay-on-call",
            r(
                """video call|skype|stay on (the )?call|remain online|remain on (the )?(video )?call""" +
                    """|keep (the )?camera on|24\s*(x\s*)?7\s+(monitoring|surveillance|supervision)""" +
                    """|do not (end|disconnect|cut|hang up) (the )?call|कॉल (मत|ना) काट|ಕರೆ ಕಡಿತಗೊಳಿಸಬೇಡಿ"""
            ),
            16, Cue.ISOLATION,
            "Tells you to stay on a video call. Agencies do not work this way.",
        ),
        Marker(
            "no-lawyer",
            r(
                """do not (contact|go to|approach|visit) (the |your )?(police|lawyer|advocate|bank|family)""" +
                    """|without (informing|telling) (anyone|your family)|no lawyer (is )?(allowed|required)"""
            ),
            20, Cue.ISOLATION,
            "Tells you not to contact police, a lawyer, your bank or your family. That alone is the scam.",
        ),
        // A single token, not a phrase. Photographing a real ED order produced OCR output
        // of "afrest w artant" for "arrest warrant", so every pattern needing those two
        // words adjacent and correctly spelled matched nothing at all. A document that is
        // about arresting you says "arrest" many times, and OCR does not mangle them all.
        Marker(
            "arrest",
            r(
                """\barrest(ed|s|ing)?\b|grounds for arrest|taken into custody""" +
                    """|गिरफ़?्तार|ಬಂಧನ|ಬಂಧಿಸ"""
            ),
            18, Cue.THREAT,
            "Talks about arresting you. No agency arrests anyone over a phone or a message.",
        ),
        Marker(
            "warrant",
            r("""\bwarrant\b|non[- ]?bailable|issued against you|वारंट"""),
            16, Cue.THREAT,
            "Presents itself as a warrant or an order against you.",
        ),
        Marker(
            "crime-story",
            r(
                // la[a-z]{0,2}dering so that OCR reading "Money Lamdering" still matches.
                """money\s+la[a-z]{0,2}dering|prevention of money|pmla|ndps|narcotics""" +
                    """|\bdrugs?\b|contraband|psychotropic""" +
                    """|human trafficking|fake passport|मनी लॉन्ड्रिंग|ಮನಿ ಲಾಂಡರಿಂಗ್"""
            ),
            16, Cue.THREAT,
            "Accuses you of laundering or drug crimes, a common script.",
        ),
        Marker(
            "agency",
            r(
                // "Directorate of Enforcement" is how the real letterhead reads. The
                // reversed form alone never matched a photographed ED notice.
                """\bcbi\b|central bureau of investigation|directorate\s+of\s+enforcement""" +
                    """|enforcement directorate|\becir\b|\bncb\b""" +
                    """|narcotics control bureau|\btrai\b|department of telecommunication""" +
                    """|cyber ?crime|cyber cell|\bcustoms\b|income tax department|intelligence bureau""" +
                    """|सीबीआई|प्रवर्तन निदेशालय|ಸಿಬಿಐ"""
            ),
            12, Cue.AUTHORITY,
            "Names a central agency to frighten you.",
        ),
        Marker(
            "court",
            r(
                """\bf\.?i\.?r\.?\b|\bf i r\b|first information report|summons|hon.?ble (court|supreme court|judge)""" +
                    """|supreme court of india|court notice|charge ?sheet|case (no|number)""" +
                    """|under section \d+|प्राथमिकी|समन|ಎಫ್\s*ಐ\s*ಆರ್|ನ್ಯಾಯಾಲಯ"""
            ),
            12, Cue.AUTHORITY,
            "Quotes an FIR, a case number or a court order to look official.",
        ),
        Marker(
            "officer",
            r(
                """\bpolice\b|sub[- ]inspector|\binspector\b|deputy commissioner|investigating officer""" +
                    """|badge (no|number)|पुलिस|अधिकारी|ಪೊಲೀಸ್|ಅಧಿಕಾರಿ"""
            ),
            10, Cue.AUTHORITY,
            "Claims to be from the police or an officer.",
        ),
        Marker(
            "deadline",
            r(
                """within (24|48|72)\s*(hours|hrs)|within \d+ (hours|hrs|minutes|mins)""" +
                    """|immediate(ly)? (action|payment|response|compliance)|last (warning|notice|chance)""" +
                    """|final notice|failing which|non[- ]compliance will|before \d+ ?(am|pm)""" +
                    """|तुरंत|अंतिम चेतावनी|ಕೂಡಲೇ|ಅಂತಿಮ ಎಚ್ಚರಿಕೆ"""
            ),
            14, Cue.URGENCY,
            "Puts a countdown on it. Urgency is how you are stopped from checking.",
        ),
        Marker(
            "fee",
            r(
                """pay (a |the )?(fine|penalty|amount immediately)|penalty of (rs|inr|\d)""" +
                    """|compounding (fee|charge)|clearance (fee|charge)|processing fee|security deposit""" +
                    """|settlement amount|जुर्माना|ದಂಡ"""
            ),
            18, Cue.MONEY,
            "Demands a fine or a fee. A genuine penalty is never collected over a phone.",
        ),
        Marker(
            "identity",
            r("""aadhaa?r|\bpan card\b|आधार|ಆಧಾರ್"""),
            10, Cue.IDENTITY,
            "Brings up your Aadhaar or PAN. Agencies do not send notices about them.",
        ),
        // Three scripts the list had no words for at all. KYC-expiry and OTP requests are
        // the commonest fraud messages in India, and remote access is the mechanism by
        // which the money actually leaves: the risk engine already scores AnyDesk at +35
        // from app usage, but the scanner could not see it named in a message.
        Marker(
            "kyc",
            r(
                """\bkyc\b|re[- ]?kyc|account (will be |is |shall be )?(blocked|suspended|frozen|deactivat)""" +
                    """|update your (details|kyc|account)|खाता बंद|केवाईसी|ಖಾತೆ ಸ್ಥಗಿತ"""
            ),
            20, Cue.THREAT,
            "The KYC or blocked-account story. A bank does not do this by message.",
        ),
        Marker(
            "otp",
            r(
                """\botp\b|one time password|\bcvv\b|\bupi pin\b|share the (code|pin|otp)""" +
                    """|verification code|ओटीपी|ಓಟಿಪಿ"""
            ),
            22, Cue.MONEY,
            "Asks for an OTP, PIN or CVV. Nobody legitimate ever asks for these.",
        ),
        Marker(
            "remote-access",
            r(
                """anydesk|team ?viewer|quick ?support|rust ?desk|remote (access|desktop|control)""" +
                    """|screen ?shar|share your screen|स्क्रीन शेयर"""
            ),
            20, Cue.MONEY,
            "Wants remote control of your phone. That hands over your banking apps.",
        ),
        Marker(
            "sim-block",
            r(
                """sim (card )?(will be |is being )?(blocked|deactivated|disconnected|suspended)""" +
                    """|(mobile |phone )?number will be (blocked|disconnected|suspended)""" +
                    """|services will be (barred|terminated)|connection will be cut"""
            ),
            14, Cue.THREAT,
            "Threatens to block your SIM or number. TRAI never sends this.",
        ),
        Marker(
            "parcel",
            r("""parcel|courier|consignment|पार्सल|कूरियर|ಪಾರ್ಸೆಲ್"""),
            8, Cue.STORY,
            "Mentions a parcel or courier, the usual opening of this scam.",
        ),
        Marker(
            "side-channel",
            r(
                """whatsapp (number|video|call)|telegram|contact (us )?on whatsapp""" +
                    """|click (the|this|below) link|reply to this message"""
            ),
            10, Cue.STORY,
            "Pushes you onto WhatsApp, Telegram or a link. Official notices never do.",
        ),
    )

    /**
     * Spoken-only cues, layered on top of the written list. Someone describing a call uses
     * everyday words — "they said they'll arrest me", "he wants money" — and those words
     * are too loose to put on a printed notice without flagging honest letters. They share
     * ids with the written markers where they mean the same thing.
     */
    private val SPOKEN = listOf(
        Marker(
            "identity",
            r("""aadhaa?r|आधार|ಆಧಾರ್|adhar|aadar"""),
            16, Cue.IDENTITY,
            "They brought up your Aadhaar. Agencies do not call about it.",
        ),
        Marker(
            "parcel",
            r("""parcel|courier|package|consignment|पार्सल|कूरियर|ಪಾರ್ಸೆಲ್"""),
            16, Cue.STORY,
            "The parcel story is the most common opening line of this scam.",
        ),
        Marker(
            "arrest",
            r(
                """under arrest|arrest me|arrest you|arrest my|will arrest|warrant|jail|custody""" +
                    """|police case|case against me|गिरफ़?्तार|जेल|ಬಂಧನ|ಜೈಲು|giraftar|jail ja"""
            ),
            18, Cue.THREAT,
            "They threatened arrest or jail. No agency arrests anyone over a call.",
        ),
        Marker(
            "officer",
            r(
                """\bpolice\b|officer|inspector|\bcbi\b|customs|cyber ?crime|from the government""" +
                    """|पुलिस|अधिकारी|ಪೊಲೀಸ್|ಅಧಿಕಾರಿ"""
            ),
            12, Cue.AUTHORITY,
            "They claimed to be police, an agency or an officer.",
        ),
        Marker(
            "bank",
            r(
                """bank account|my account|\botp\b|one time password|account number|card number""" +
                    """|खाता|ಖಾತೆ|khata"""
            ),
            14, Cue.MONEY,
            "They asked about your bank account, card or OTP. Nobody legitimate ever does.",
        ),
        Marker(
            "move-money",
            r(
                """send (them |him |the )?money|transfer (the )?money|pay them|wants? money""" +
                    """|asked for money|पैसे (भेज|मांग)|ಹಣ (ಕಳುಹಿಸ|ಕೇಳ)|paisa|paise"""
            ),
            22, Cue.MONEY,
            "They asked you for money. That is the whole purpose of the call.",
        ),
        Marker(
            "secrecy",
            r(
                """not to tell|don'?t tell|do not tell|can'?t tell (anyone|anybody)""" +
                    """|cannot tell (anyone|anybody)|tell (no one|nobody)|keep it secret""" +
                    """|not to inform|between us|किसी को (मत|ना) बता|ಯಾರಿಗೂ ಹೇಳ"""
            ),
            20, Cue.SECRECY,
            "They told you to keep it from your family. That alone marks it as a scam.",
        ),
        Marker(
            "money-amount",
            r("""\brupees?\b|\blakhs?\b|\bthousand\b|\bcrore\b|रुपये|ಹಣ|ರೂಪಾಯಿ"""),
            14, Cue.MONEY,
            "They named an amount. A real case is never settled with a payment over the phone.",
        ),
        Marker(
            "stay-on-call",
            r(
                """stay on the (call|line)|don'?t cut|do not cut|keep the (camera|video) on""" +
                    """|video call|whole day on the call|कॉल (मत|ना) काट"""
            ),
            14, Cue.ISOLATION,
            "They kept you on the call. Being kept on the line is how you are stopped from checking.",
        ),
        Marker(
            "deadline",
            r("""right now|immediately|within an hour|today itself|abhi|तुरंत|ಕೂಡಲೇ"""),
            12, Cue.URGENCY,
            "They wanted it done immediately. Urgency is the pressure, not the deadline.",
        ),
        Marker(
            "sim-block",
            r("""block my (sim|number)|number will be blocked|sim will be (blocked|closed)"""),
            14, Cue.THREAT,
            "They threatened to block your SIM or number.",
        ),
    )
}
