package com.dasen.kaavalu.scan

/**
 * What a scam text actually looks like, in one place.
 *
 * The first version of this list only knew how a formal "CBI arrest warrant" reads, so a
 * perfectly obvious KYC or courier scam scored nothing and the app said "no strong scam
 * markers found" — a green all-clear on a real scam. The list below covers the scripts
 * that are actually in circulation in India, and the same markers are used for a scanned
 * notice and for what the user says out loud, because a scam does not change shape
 * depending on how it reaches you.
 */

data class Marker(val pattern: Regex, val weight: Int, val why: String)

/** What Kaavalu is prepared to say about a piece of text. Never a bare yes or no. */
enum class Verdict {
    /** Nothing could be read at all. Never reassure the user in this case. */
    NO_TEXT,

    /** Text was read and nothing matched. */
    NOTHING_FOUND,

    /** Some warning signs, not enough to call it. */
    SUSPICIOUS,

    /** This reads like a scam. */
    SCAM,
}

data class ScanResult(
    val score: Int,
    val found: List<String>,
    val text: String,
    val verdict: Verdict,
) {
    val flagged: Boolean get() = verdict == Verdict.SCAM
    /** Worth raising the risk of the next unknown call. */
    val worthRemembering: Boolean get() = verdict == Verdict.SCAM || verdict == Verdict.SUSPICIOUS
}

object ScamMarkers {

    const val SCAM_AT = 35

    /**
     * 15, not 18, so that "an officer called about my bank account" (8 + 8) is called
     * suspicious rather than clean. A genuine delivery message still scores 14 and stays
     * quiet, so this is the widest the band can open without crying wolf.
     */
    const val SUSPICIOUS_AT = 15

    fun evaluate(raw: String): ScanResult {
        if (raw.isBlank()) return ScanResult(0, emptyList(), raw, Verdict.NO_TEXT)
        val t = raw.lowercase()
        val hits = MARKERS.filter { it.pattern.containsMatchIn(t) }
        val score = hits.sumOf { it.weight }.coerceAtMost(100)
        val verdict = when {
            score >= SCAM_AT -> Verdict.SCAM
            score >= SUSPICIOUS_AT -> Verdict.SUSPICIOUS
            else -> Verdict.NOTHING_FOUND
        }
        return ScanResult(score, hits.map { it.why }, raw, verdict)
    }

    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    val MARKERS: List<Marker> = listOf(
        // The one phrase that settles it on its own.
        Marker(
            r("""digital\s*arrest|डिजिटल\s*अरेस्ट|ಡಿಜಿಟಲ್\s*ಅರೆಸ್ಟ್"""), 40,
            "Says \"digital arrest\". There is no such thing in Indian law.",
        ),
        // Moving money is the point of every one of these scripts.
        Marker(
            r("""safe account|verification account|rbi account|refundable|transfer the (amount|funds|money)|\brtgs\b|deposit the|security deposit|सुरक्षित खाता|पैसे ट्रांसफर"""), 30,
            "Asks you to move money. No agency or bank ever asks for this.",
        ),
        Marker(
            r("""do not (disclose|share|tell|inform)|tell no ?one|not inform anyone|confidential|secrecy|national secret|गोपनीय|किसी को मत बताना|ಯಾರಿಗೂ ಹೇಳಬೇಡಿ"""), 25,
            "Demands secrecy. Real agencies never forbid you from telling your family.",
        ),
        // Single tokens, not exact phrases. OCR of a real photographed ED order returned
        // "afrest w artant" for "arrest warrant", so any pattern that needs the two words
        // adjacent and correctly spelled matches nothing at all on a real photo.
        Marker(
            r("""\barrest(ed|s|ing)?\b|गिरफ्तार|गिरफ़्तार|ಬಂಧನ"""), 22,
            "Talks about arresting you. No agency arrests anyone over a phone or a message.",
        ),
        Marker(
            r("""\bwarrant\b|\bsummons\b|non[- ]?bailable|\bf\.?i\.?r\.?\b|वारंट"""), 20,
            "Presents itself as a warrant, summons or FIR.",
        ),
        // The agencies these documents impersonate, in the word order they actually print:
        // the real letterhead reads "Directorate of Enforcement", not "Enforcement Directorate".
        Marker(
            r("""directorate\s+of\s+enforcement|enforcement\s+directorate|\becir\b|central bureau of investigation"""), 20,
            "Claims to come from the ED or CBI. They do not send orders to your phone.",
        ),
        Marker(
            r("""assistant director|investigating officer|deputy director|joint director"""), 8,
            "Signed off with an official-sounding rank.",
        ),
        Marker(
            r("""otp|one time password|\bcvv\b|\bpin\b|share the code|verification code|ओटीपी|ಓಟಿಪಿ"""), 22,
            "Asks for an OTP, PIN or CVV. Nobody legitimate will ever ask.",
        ),
        Marker(
            r("""\bkyc\b|re[- ]?kyc|account (will be |is )?(blocked|suspended|frozen|deactivat)|update your (details|kyc)|खाता बंद|केवाईसी|ಖಾತೆ"""), 22,
            "The KYC or blocked-account story. Banks do not do this by message.",
        ),
        Marker(
            r("""aadhaar|aadhar|\bpan card\b|आधार|ಆಧಾರ್"""), 20,
            "Brings up your Aadhaar or PAN. Agencies do not call or message about these.",
        ),
        Marker(
            // la[a-z]{0,2}dering so that OCR reading "Money Lamdering" still matches.
            r("""money\s+la[a-z]{0,2}dering|prevention of money|\bpmla\b|\bndps\b|narcotics|contraband|illegal (activity|transaction)|मनी लॉन्ड्रिंग"""), 20,
            "Accuses you of laundering or drug offences, a standard line in the script.",
        ),
        Marker(
            r("""anydesk|teamviewer|quick ?support|remote (access|desktop)|screen ?shar|स्क्रीन शेयर"""), 20,
            "Wants remote access to your phone. This hands over your bank apps.",
        ),
        Marker(
            r("""stay on (the )?call|remain online|do not (cut|disconnect|end) the call|video call|skype|कॉल मत काटना|ಕರೆ ಕಡಿತಗೊಳಿಸಬೇಡಿ"""), 18,
            "Tells you to stay on the call. Agencies do not work this way.",
        ),
        Marker(
            r("""within (24|48|2|1|12) ?(hours|hrs|घंटे)|immediately|urgent(ly)?|failing which|last warning|final notice|तुरंत|ತಕ್ಷಣ"""), 15,
            "Manufactures a deadline so you act before you think.",
        ),
        Marker(
            r("""click (the |on )?(link|here)|bit\.ly|tinyurl|t\.me|wa\.me|http://|download the app"""), 15,
            "Pushes you to a link or an app download.",
        ),
        Marker(
            r("""\bparcel\b|courier|consignment|customs duty|package (seized|held|detained)|पार्सल|कूरियर|ಪಾರ್ಸೆಲ್"""), 14,
            "The parcel or courier story, the most common opening of this scam.",
        ),
        Marker(
            r("""lottery|lucky (winner|draw)|you have won|prize money|cash reward|लॉटरी|इनाम"""), 14,
            "Prize and lottery bait.",
        ),
        Marker(
            r("""penalty|legal action|court case|non payment|fine of|जुर्माना|कानूनी कार्रवाई"""), 12,
            "Threatens a penalty or legal action to frighten you.",
        ),
        Marker(
            r("""\bcbi\b|enforcement directorate|\bed\b office|\bncb\b|customs|\btrai\b|cyber ?crime|income tax|narcotics bureau|पुलिस विभाग"""), 10,
            "Names a government agency to frighten you.",
        ),
        Marker(
            r("""\bpolice\b|\bofficer\b|inspector|\bsub[- ]?inspector\b|पुलिस|अधिकारी|ಪೊಲೀಸ್|ಅಧಿಕಾರಿ"""), 8,
            "Claims to be the police or an officer.",
        ),
        Marker(
            r("""bank account|your account|account number|बैंक खाता|ಬ್ಯಾಂಕ್ ಖಾತೆ"""), 8,
            "Brings up your bank account.",
        ),
    )
}
