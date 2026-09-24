package com.dasen.kaavalu

/**
 * The notice scanner and Ask Kaavalu, in the family's language.
 *
 * These lines used to be English only, on screens that are otherwise fully translated: a
 * Kannada phone showed a Kannada interrupt and then answered "Asks you to move money. No
 * agency asks for this." when the same user scanned a notice. The marker list keeps its
 * English text for logs and tests; what the user reads comes from here, keyed by marker id.
 *
 * Kept apart from [Copy] because these are the scanner's words, and because 22 markers plus
 * eight cue names would otherwise bury the call and warning copy people edit far more often.
 *
 * TODO before the demo: unreviewed, like the rest. On the review sheet with everything else.
 */
object ScanCopy {

    /**
     * One line per marker id. The written and spoken marker lists share ids with different
     * English wording; the meaning per id is the same, so one translation serves both.
     * [fallback] is the marker's own English, so English never drifts from the source list.
     */
    fun markerReason(lang: String, id: String, fallback: String): String {
        if (lang == "en") return fallback
        val table = if (lang == "kn") KANNADA else HINDI
        return table[id] ?: fallback
    }

    /** The bonus line shown when several kinds of pressure appear together. */
    fun combination(lang: String) = when (lang) {
        "kn" -> "ಒಂದೇ ಬಾರಿಗೆ ಹಲವು ಒತ್ತಡಗಳು. ವಂಚನೆಗಳು ಹೀಗೆ ಜೋಡಿಸುತ್ತವೆ; ನಿಜವಾದ ಪತ್ರಗಳು ಹಾಗೆ ಮಾಡುವುದಿಲ್ಲ."
        "hi" -> "एक साथ कई दबाव। ठगी ऐसे ही परत दर परत बनती है; असली चिट्ठी ऐसी नहीं होती।"
        else -> "Several pressures at once. Scams stack them; honest letters don’t."
    }

    /** What kind of pressure a line represents, shown beside its source. */
    fun cue(lang: String, name: String): String = when (name) {
        "AUTHORITY" -> when (lang) {
            "kn" -> "ಅಧಿಕಾರ"; "hi" -> "रौब"; else -> "Authority"
        }
        "THREAT" -> when (lang) {
            "kn" -> "ಬೆದರಿಕೆ"; "hi" -> "धमकी"; else -> "Threat"
        }
        "MONEY" -> when (lang) {
            "kn" -> "ಹಣ"; "hi" -> "पैसा"; else -> "Money"
        }
        "SECRECY" -> when (lang) {
            "kn" -> "ಗೌಪ್ಯತೆ"; "hi" -> "छिपाव"; else -> "Secrecy"
        }
        "URGENCY" -> when (lang) {
            "kn" -> "ಆತುರ"; "hi" -> "जल्दबाज़ी"; else -> "Urgency"
        }
        "ISOLATION" -> when (lang) {
            "kn" -> "ಒಂಟಿತನ"; "hi" -> "अलगाव"; else -> "Isolation"
        }
        "IDENTITY" -> when (lang) {
            "kn" -> "ಗುರುತು"; "hi" -> "पहचान"; else -> "Identity"
        }
        "STORY" -> when (lang) {
            "kn" -> "ಕಟ್ಟುಕಥೆ"; "hi" -> "बहाना"; else -> "Cover story"
        }
        // Not a Cue: the source label on the combination bonus line.
        "COMBINATION" -> when (lang) {
            "kn" -> "ಸಂಯೋಜನೆ"; "hi" -> "मेल"; else -> "Combination"
        }
        else -> name
    }

    /** Where a line of evidence came from, shown beside the cue. */
    fun noticeScan(lang: String) = when (lang) {
        "kn" -> "ನೋಟಿಸ್ ಪರಿಶೀಲನೆ"; "hi" -> "नोटिस जाँच"; else -> "Notice scan"
    }

    fun whatYouSaid(lang: String) = when (lang) {
        "kn" -> "ನೀವು ಹೇಳಿದ್ದು"; "hi" -> "आपने जो कहा"; else -> "What you said"
    }

    // --- the screen itself ----------------------------------------------------------

    fun screenTitle(lang: String) = when (lang) {
        "kn" -> "ಈ ನೋಟಿಸ್ ನಿಜವೇ?"
        "hi" -> "क्या यह नोटिस असली है?"
        else -> "Is this notice real?"
    }

    fun screenLead(lang: String) = when (lang) {
        "kn" -> "ಪರಿಶೀಲನೆ ಇದೇ ಫೋನಿನಲ್ಲಿ. ಏನನ್ನೂ ಅಪ್‌ಲೋಡ್ ಮಾಡುವುದಿಲ್ಲ."
        "hi" -> "जाँच इसी फ़ोन पर होती है। कुछ भी अपलोड नहीं होता।"
        else -> "Checked on this phone. Nothing is uploaded."
    }

    fun framingHint(lang: String) = when (lang) {
        "kn" -> "ಪುಟದಿಂದ ಫ್ರೇಮ್ ತುಂಬಿಸಿ. ಸ್ಪಷ್ಟವಾಗಿರಲಿ."
        "hi" -> "पन्ने से पूरा फ़्रेम भरिए। साफ़ रखिए।"
        else -> "Fill the frame with the page. Keep it sharp."
    }

    fun takePhoto(lang: String, retake: Boolean) = when {
        retake && lang == "kn" -> "ಮತ್ತೆ ಫೋಟೋ ತೆಗೆಯಿರಿ"
        retake && lang == "hi" -> "फ़ोटो दोबारा लीजिए"
        retake -> "Take the photo again"
        lang == "kn" -> "ನೋಟಿಸ್ ಫೋಟೋ ತೆಗೆಯಿರಿ"
        lang == "hi" -> "नोटिस की फ़ोटो लीजिए"
        else -> "Photograph the notice"
    }

    fun pickScreenshot(lang: String) = when (lang) {
        "kn" -> "ಸ್ಕ್ರೀನ್‌ಶಾಟ್ ಆರಿಸಿ"
        "hi" -> "स्क्रीनशॉट चुनिए"
        else -> "Choose a screenshot"
    }

    fun evidenceHeading(lang: String) = when (lang) {
        "kn" -> "ಕಾವಲು ಕಂಡುಕೊಂಡದ್ದು"
        "hi" -> "कावलु को क्या मिला"
        else -> "What Kaavalu found"
    }

    // --- the explainer at the foot of the scan screen --------------------------------

    fun looksForHeading(lang: String) = when (lang) {
        "kn" -> "ಕಾವಲು ಏನನ್ನು ಹುಡುಕುತ್ತದೆ"
        "hi" -> "कावलु क्या देखता है"
        else -> "What Kaavalu looks for"
    }

    fun looksForLead(lang: String) = when (lang) {
        "kn" -> "ವಂಚನೆಯ ನೋಟಿಸ್‌ಗಳು ಇವುಗಳಲ್ಲಿ ಹಲವನ್ನು ಒಟ್ಟಿಗೆ ಬಳಸುತ್ತವೆ. ಒಂದೇ ಒಂದು ಇದ್ದರೆ ಸಾಮಾನ್ಯವಾಗಿ ಅಪಾಯವಿಲ್ಲ."
        "hi" -> "ठगी के नोटिस इनमें से कई एक साथ इस्तेमाल करते हैं। अकेला एक लक्षण आमतौर पर बेकार है।"
        else -> "Scam notices use several of these together. One on its own is usually harmless."
    }

    /** One line per core cue, saying what that pressure looks like on paper. */
    fun looksLike(lang: String, cueName: String): String = when (cueName) {
        "AUTHORITY" -> when (lang) {
            "kn" -> "ಸಂಸ್ಥೆ, ನ್ಯಾಯಾಲಯ ಅಥವಾ ಅಧಿಕಾರಿಯ ಹೆಸರು ಹೇಳುತ್ತದೆ"
            "hi" -> "किसी एजेंसी, अदालत या अधिकारी का नाम लेता है"
            else -> "Names an agency, a court or an officer"
        }
        "THREAT" -> when (lang) {
            "kn" -> "ಬಂಧನ, ಜೈಲು, ಸಿಮ್ ಬಂದ್, ಖಾತೆ ಸ್ಥಗಿತ"
            "hi" -> "गिरफ़्तारी, जेल, सिम बंद, खाता फ़्रीज़"
            else -> "Arrest, jail, a blocked SIM, a frozen account"
        }
        "MONEY" -> when (lang) {
            "kn" -> "ಶುಲ್ಕ, ವರ್ಗಾವಣೆ, ಕ್ಯೂಆರ್ ಕೋಡ್, ಓಟಿಪಿ"
            "hi" -> "फ़ीस, पैसे भेजना, क्यूआर कोड, ओटीपी"
            else -> "A fee, a transfer, a QR code, an OTP"
        }
        "SECRECY" -> when (lang) {
            "kn" -> "ಕುಟುಂಬದವರಿಗೆ ಹೇಳಬೇಡಿ ಎನ್ನುತ್ತದೆ"
            "hi" -> "परिवार को न बताने के लिए कहता है"
            else -> "Tells you not to involve your family"
        }
        else -> when (lang) {
            "kn" -> "ಸಮಯದ ಒತ್ತಡ ಹಾಕುತ್ತದೆ"
            "hi" -> "समय की घड़ी लगा देता है"
            else -> "Puts a clock on it"
        }
    }

    // --- the verdicts -------------------------------------------------------------
    // English here is the wording already on the screen, kept verbatim so the three
    // languages stay one voice and nobody has to diff two copies of the same sentence.

    /** The short badge beside the headline. */
    fun verdictSign(lang: String, verdict: String): String = when (verdict) {
        "SCAM" -> when (lang) {
            "kn" -> "ವಂಚನೆ"; "hi" -> "ठगी"; else -> "Scam"
        }
        "SUSPICIOUS" -> when (lang) {
            "kn" -> "ಸಂಶಯ"; "hi" -> "संदेह"; else -> "Suspicious"
        }
        "UNREADABLE" -> when (lang) {
            "kn" -> "ಓದಲಾಗಲಿಲ್ಲ"; "hi" -> "पढ़ा नहीं गया"; else -> "Unreadable"
        }
        else -> when (lang) {
            "kn" -> "ವಂಚನೆಯ ಲಕ್ಷಣ ಇಲ್ಲ"; "hi" -> "ठगी के लक्षण नहीं"; else -> "No scam signs"
        }
    }

    fun verdictHeadline(lang: String, verdict: String): String = when (verdict) {
        "SCAM" -> when (lang) {
            "kn" -> "ಇದು ವಂಚನೆಯ ನೋಟಿಸ್"
            "hi" -> "यह ठगी का नोटिस है"
            else -> "This is a scam notice"
        }
        "SUSPICIOUS" -> when (lang) {
            "kn" -> "ಇದು ವಂಚನೆಯಂತೆ ಕಾಣುತ್ತದೆ"
            "hi" -> "यह ठगी जैसा लगता है"
            else -> "This looks like a scam"
        }
        "UNREADABLE" -> when (lang) {
            "kn" -> "ಕಾವಲುಗೆ ಅದನ್ನು ಓದಲು ಆಗಲಿಲ್ಲ"
            "hi" -> "कावलु उसे पढ़ नहीं सका"
            else -> "Kaavalu couldn’t read that"
        }
        else -> when (lang) {
            "kn" -> "ಪರಿಚಿತ ವಂಚನೆಯ ನುಡಿಗಟ್ಟುಗಳು ಸಿಗಲಿಲ್ಲ"
            "hi" -> "ठगी के जाने-पहचाने वाक्य नहीं मिले"
            else -> "No known scam phrases found"
        }
    }

    /** The instruction under a scam or suspicious verdict. */
    fun instruction(lang: String) = when (lang) {
        "kn" -> "ಉತ್ತರಿಸಬೇಡಿ, ಹಣ ಕೊಡಬೇಡಿ, ಅದರಲ್ಲಿರುವ ಸಂಖ್ಯೆಗೆ ಕರೆ ಮಾಡಬೇಡಿ."
        "hi" -> "जवाब मत दीजिए, पैसे मत दीजिए, उस पर लिखे नंबर पर कॉल मत कीजिए।"
        else -> "Do not reply, do not pay, do not call the number on it."
    }

    fun unclearBody(lang: String) = when (lang) {
        "kn" -> "ಅದು ನಿಜವೆಂದು ಸಾಬೀತು ಮಾಡುವುದಿಲ್ಲ. ನಿಜವಾದ ಸಂಸ್ಥೆ ಅಂಚೆಯಲ್ಲಿ ಪತ್ರ ಕಳುಹಿಸುತ್ತದೆ, ವಾಟ್ಸಾಪ್‌ನಲ್ಲಿ ಅಲ್ಲ. ಅದು ಹೇಳಿದ್ದನ್ನು ಮಾಡುವ ಮೊದಲು ಕುಟುಂಬದವರನ್ನು ಕೇಳಿ."
        "hi" -> "इससे यह साबित नहीं होता कि यह असली है। असली एजेंसी डाक से चिट्ठी भेजती है, व्हाट्सऐप पर नहीं। इसमें जो कहा गया है वह करने से पहले घर में पूछ लीजिए।"
        else -> "That doesn’t prove it’s real. A real agency sends a letter by post, not on " +
            "WhatsApp. Ask your family before you do anything it asks."
    }

    fun unreadableBody(lang: String) = when (lang) {
        "kn" -> "ಬಹುತೇಕ ಯಾವ ಪದವೂ ಸಿಗಲಿಲ್ಲ. ಹತ್ತಿರದಿಂದ, ಒಳ್ಳೆಯ ಬೆಳಕಿನಲ್ಲಿ, ಪುಟದ ಮೇಲೆ ನೆರಳು ಬೀಳದಂತೆ ಮತ್ತೊಮ್ಮೆ ತೆಗೆಯಿರಿ — ಅಥವಾ ಮೂಲ ಸ್ಕ್ರೀನ್‌ಶಾಟ್ ಆರಿಸಿ, ಅದು ಯಾವಾಗಲೂ ಸ್ಪಷ್ಟವಾಗಿರುತ್ತದೆ."
        "hi" -> "लगभग कोई शब्द नहीं मिला। पास से, अच्छी रोशनी में, पन्ने पर परछाईं डाले बिना दोबारा लीजिए — या असली स्क्रीनशॉट चुनिए, वह हमेशा साफ़ होता है।"
        else -> "Almost no text came back. Retake it closer, in good light, without a shadow " +
            "across the page — or pick the original screenshot, which is always sharper."
    }

    // --- the marker lines ----------------------------------------------------------

    private val HINDI = mapOf(
        "digital-arrest" to "\"डिजिटल अरेस्ट\" कहता है। भारतीय कानून में ऐसा कुछ होता ही नहीं।",
        "secrecy" to "चुप रहने को कहता है। असली एजेंसी कभी परिवार को बताने से नहीं रोकती।",
        "move-money" to "पैसे भेजने को कहता है। कोई एजेंसी ऐसा नहीं मांगती।",
        "stay-on-call" to "वीडियो कॉल पर बने रहने को कहता है। एजेंसियाँ ऐसे काम नहीं करतीं।",
        "no-lawyer" to "पुलिस, वकील, बैंक या परिवार से बात करने से रोकता है। अकेले यही बात ठगी है।",
        "arrest" to "गिरफ़्तारी की बात करता है। कोई एजेंसी फ़ोन या संदेश पर गिरफ़्तार नहीं करती।",
        "warrant" to "खुद को वारंट या आपके खिलाफ़ आदेश बताता है।",
        "crime-story" to "मनी लॉन्ड्रिंग या ड्रग्स का आरोप लगाता है, यह जाना-पहचाना तरीका है।",
        "agency" to "डराने के लिए किसी केंद्रीय एजेंसी का नाम लेता है।",
        "court" to "असली दिखने के लिए एफ़आईआर, केस नंबर या अदालती आदेश का हवाला देता है।",
        "officer" to "खुद को पुलिस या अधिकारी बताता है।",
        "deadline" to "समय की घड़ी लगा देता है। जल्दबाज़ी इसलिए, ताकि आप जाँच न सकें।",
        "fee" to "जुर्माना या फ़ीस मांगता है। असली जुर्माना कभी फ़ोन पर नहीं लिया जाता।",
        "identity" to "आपके आधार या पैन की बात करता है। एजेंसियाँ इन पर नोटिस नहीं भेजतीं।",
        "kyc" to "केवाईसी या खाता बंद होने की कहानी। बैंक संदेश से ऐसा नहीं करता।",
        "otp" to "ओटीपी, पिन या सीवीवी मांगता है। कोई भी सही आदमी यह नहीं मांगता।",
        "remote-access" to "आपके फ़ोन का रिमोट कंट्रोल चाहता है। इससे आपके बैंक ऐप उनके हाथ चले जाते हैं।",
        "sim-block" to "सिम या नंबर बंद करने की धमकी देता है। ट्राई ऐसा कभी नहीं भेजता।",
        "parcel" to "पार्सल या कूरियर का ज़िक्र करता है, इसी से यह ठगी शुरू होती है।",
        "side-channel" to "व्हाट्सऐप, टेलीग्राम या किसी लिंक पर ले जाता है। सरकारी नोटिस ऐसा नहीं करते।",
        "bank" to "आपके बैंक खाते, कार्ड या ओटीपी के बारे में पूछता है। कोई सही आदमी ऐसा नहीं पूछता।",
        "money-amount" to "एक रकम बताता है। असली मामला फ़ोन पर पैसे देकर नहीं निपटता।",
    )

    private val KANNADA = mapOf(
        "digital-arrest" to "\"ಡಿಜಿಟಲ್ ಅರೆಸ್ಟ್\" ಎಂದು ಹೇಳುತ್ತದೆ. ಭಾರತದ ಕಾನೂನಿನಲ್ಲಿ ಅಂಥದ್ದು ಇಲ್ಲವೇ ಇಲ್ಲ.",
        "secrecy" to "ಸುಮ್ಮನಿರಲು ಹೇಳುತ್ತದೆ. ನಿಜವಾದ ಸಂಸ್ಥೆ ಕುಟುಂಬಕ್ಕೆ ಹೇಳುವುದನ್ನು ಎಂದಿಗೂ ತಡೆಯುವುದಿಲ್ಲ.",
        "move-money" to "ಹಣ ಕಳುಹಿಸಲು ಹೇಳುತ್ತದೆ. ಯಾವ ಸಂಸ್ಥೆಯೂ ಹೀಗೆ ಕೇಳುವುದಿಲ್ಲ.",
        "stay-on-call" to "ವಿಡಿಯೋ ಕರೆಯಲ್ಲೇ ಇರಲು ಹೇಳುತ್ತದೆ. ಸಂಸ್ಥೆಗಳು ಹೀಗೆ ಕೆಲಸ ಮಾಡುವುದಿಲ್ಲ.",
        "no-lawyer" to "ಪೊಲೀಸ್, ವಕೀಲ, ಬ್ಯಾಂಕ್ ಅಥವಾ ಕುಟುಂಬದವರನ್ನು ಸಂಪರ್ಕಿಸದಂತೆ ತಡೆಯುತ್ತದೆ. ಇದೊಂದೇ ಸಾಕು, ಇದು ವಂಚನೆ.",
        "arrest" to "ಬಂಧಿಸುವ ಮಾತನಾಡುತ್ತದೆ. ಯಾವ ಸಂಸ್ಥೆಯೂ ಫೋನ್ ಅಥವಾ ಸಂದೇಶದ ಮೂಲಕ ಬಂಧಿಸುವುದಿಲ್ಲ.",
        "warrant" to "ತನ್ನನ್ನು ವಾರಂಟ್ ಅಥವಾ ನಿಮ್ಮ ವಿರುದ್ಧದ ಆದೇಶ ಎಂದು ತೋರಿಸಿಕೊಳ್ಳುತ್ತದೆ.",
        "crime-story" to "ಹಣ ಅಕ್ರಮ ವರ್ಗಾವಣೆ ಅಥವಾ ಮಾದಕ ವಸ್ತುಗಳ ಆರೋಪ ಹೊರಿಸುತ್ತದೆ, ಇದು ಸಾಮಾನ್ಯ ತಂತ್ರ.",
        "agency" to "ಹೆದರಿಸಲು ಕೇಂದ್ರ ಸಂಸ್ಥೆಯ ಹೆಸರು ಹೇಳುತ್ತದೆ.",
        "court" to "ಅಧಿಕೃತವಾಗಿ ಕಾಣಲು ಎಫ್‌ಐಆರ್, ಕೇಸ್ ಸಂಖ್ಯೆ ಅಥವಾ ನ್ಯಾಯಾಲಯದ ಆದೇಶವನ್ನು ಉಲ್ಲೇಖಿಸುತ್ತದೆ.",
        "officer" to "ತಾನು ಪೊಲೀಸ್ ಅಥವಾ ಅಧಿಕಾರಿ ಎಂದು ಹೇಳಿಕೊಳ್ಳುತ್ತದೆ.",
        "deadline" to "ಸಮಯದ ಒತ್ತಡ ಹಾಕುತ್ತದೆ. ನೀವು ಪರಿಶೀಲಿಸದಂತೆ ತಡೆಯಲು ಈ ಆತುರ.",
        "fee" to "ದಂಡ ಅಥವಾ ಶುಲ್ಕ ಕೇಳುತ್ತದೆ. ನಿಜವಾದ ದಂಡವನ್ನು ಎಂದಿಗೂ ಫೋನಿನಲ್ಲಿ ವಸೂಲಿ ಮಾಡುವುದಿಲ್ಲ.",
        "identity" to "ನಿಮ್ಮ ಆಧಾರ್ ಅಥವಾ ಪ್ಯಾನ್ ಬಗ್ಗೆ ಮಾತನಾಡುತ್ತದೆ. ಸಂಸ್ಥೆಗಳು ಅವುಗಳ ಬಗ್ಗೆ ನೋಟಿಸ್ ಕಳುಹಿಸುವುದಿಲ್ಲ.",
        "kyc" to "ಕೆವೈಸಿ ಅಥವಾ ಖಾತೆ ಬಂದ್ ಆಗುವ ಕಥೆ. ಬ್ಯಾಂಕ್ ಸಂದೇಶದ ಮೂಲಕ ಹೀಗೆ ಮಾಡುವುದಿಲ್ಲ.",
        "otp" to "ಓಟಿಪಿ, ಪಿನ್ ಅಥವಾ ಸಿವಿವಿ ಕೇಳುತ್ತದೆ. ಸರಿಯಾದ ಯಾರೂ ಇವುಗಳನ್ನು ಕೇಳುವುದಿಲ್ಲ.",
        "remote-access" to "ನಿಮ್ಮ ಫೋನಿನ ನಿಯಂತ್ರಣ ಬೇಕು ಎನ್ನುತ್ತದೆ. ಅದರಿಂದ ನಿಮ್ಮ ಬ್ಯಾಂಕ್ ಆ್ಯಪ್‌ಗಳು ಅವರ ಕೈಗೆ ಹೋಗುತ್ತವೆ.",
        "sim-block" to "ಸಿಮ್ ಅಥವಾ ಸಂಖ್ಯೆ ಬಂದ್ ಮಾಡುವ ಬೆದರಿಕೆ ಹಾಕುತ್ತದೆ. ಟ್ರಾಯ್ ಹೀಗೆ ಎಂದಿಗೂ ಕಳುಹಿಸುವುದಿಲ್ಲ.",
        "parcel" to "ಪಾರ್ಸೆಲ್ ಅಥವಾ ಕೊರಿಯರ್ ಬಗ್ಗೆ ಹೇಳುತ್ತದೆ, ಈ ವಂಚನೆ ಸಾಮಾನ್ಯವಾಗಿ ಹೀಗೇ ಶುರುವಾಗುತ್ತದೆ.",
        "side-channel" to "ವಾಟ್ಸಾಪ್, ಟೆಲಿಗ್ರಾಂ ಅಥವಾ ಲಿಂಕ್‌ಗೆ ಕರೆದೊಯ್ಯುತ್ತದೆ. ಅಧಿಕೃತ ನೋಟಿಸ್‌ಗಳು ಹೀಗೆ ಮಾಡುವುದಿಲ್ಲ.",
        "bank" to "ನಿಮ್ಮ ಬ್ಯಾಂಕ್ ಖಾತೆ, ಕಾರ್ಡ್ ಅಥವಾ ಓಟಿಪಿ ಬಗ್ಗೆ ಕೇಳುತ್ತದೆ. ಸರಿಯಾದ ಯಾರೂ ಹೀಗೆ ಕೇಳುವುದಿಲ್ಲ.",
        "money-amount" to "ಒಂದು ಮೊತ್ತವನ್ನು ಹೇಳುತ್ತದೆ. ನಿಜವಾದ ಪ್ರಕರಣ ಫೋನಿನಲ್ಲಿ ಹಣ ಕೊಟ್ಟು ಮುಗಿಯುವುದಿಲ್ಲ.",
    )

    /** Every marker id the scanner can produce, so a test can prove none is missing. */
    val IDS: Set<String> = HINDI.keys + KANNADA.keys
}
