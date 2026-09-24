package com.dasen.kaavalu

/**
 * Every user-facing sentence, keyed by the language the family chose during setup.
 *
 * This is deliberately NOT res/values-kn: the parent's phone is often set to English
 * while the parent reads Kannada, so the app must follow the choice made at setup,
 * not the device locale.
 *
 * TODO before the demo: have a native Kannada speaker and a Hindi speaker read every
 * line below out loud. A warning that sounds odd is a warning that gets ignored.
 */
object Copy {

    val languages = listOf("en" to "English", "hi" to "हिंदी", "kn" to "ಕನ್ನಡ")

    fun interruptTitle(lang: String) = when (lang) {
        "kn" -> "ನಿಲ್ಲಿ. ಈ ಕರೆ ವಂಚನೆಯ ಲಕ್ಷಣಗಳನ್ನು ಹೊಂದಿದೆ."
        "hi" -> "रुकिए। इस कॉल में ठगी के लक्षण हैं।"
        else -> "Stop. This call has the signs of a scam."
    }

    fun interruptBody(lang: String) = when (lang) {
        "kn" -> "ಯಾವ ಪೊಲೀಸ್, ಸಿಬಿಐ ಅಥವಾ ಕಸ್ಟಮ್ಸ್ ಅಧಿಕಾರಿಯೂ ಫೋನ್ ಅಥವಾ ವಿಡಿಯೋ ಕರೆಯಲ್ಲಿ ಯಾರನ್ನೂ ಬಂಧಿಸುವುದಿಲ್ಲ. ನೀವು ಕರೆ ಕಡಿತಗೊಳಿಸಿ ಕುಟುಂಬದವರೊಂದಿಗೆ ಮಾತನಾಡಬಹುದು."
        "hi" -> "कोई भी पुलिस, सीबीआई या कस्टम्स अधिकारी फ़ोन या वीडियो कॉल पर गिरफ़्तार नहीं करता। आप कॉल काट सकते हैं और अपने परिवार से बात कर सकते हैं।"
        else -> "No police, CBI or customs officer arrests anyone over a phone or video call. " +
            "You are allowed to hang up and talk to your family."
    }

    /** Spoken aloud over the call. Short sentences: the victim is frightened and not reading. */
    fun spoken(lang: String) = when (lang) {
        "kn" -> "ಯಾವ ಪೊಲೀಸರೂ ವಿಡಿಯೋ ಕರೆಯಲ್ಲಿ ಯಾರನ್ನೂ ಬಂಧಿಸುವುದಿಲ್ಲ. ಈ ಕರೆಯನ್ನು ಕಡಿತಗೊಳಿಸಿ. ಯಾರಿಗೂ ಹಣ ಕಳುಹಿಸಬೇಡಿ."
        "hi" -> "कोई भी पुलिस या सीबीआई वीडियो कॉल पर गिरफ़्तार नहीं करती। यह कॉल काट दीजिए। किसी को पैसे मत भेजिए।"
        else -> "No police or CBI officer arrests anyone over a video call. Please end this call. Do not send money to anyone."
    }

    fun watchTitle(lang: String) = when (lang) {
        "kn" -> "ಎಚ್ಚರ: ಅಪರಿಚಿತ ಸಂಖ್ಯೆಯಿಂದ ದೀರ್ಘ ಕರೆ"
        "hi" -> "सावधान: अनजान नंबर से लंबी कॉल"
        else -> "Careful: long call from an unknown number"
    }

    fun watchBody(lang: String) = when (lang) {
        "kn" -> "ನಿಜವಾದ ಪೊಲೀಸರು ಕರೆಯಲ್ಲಿ ಯಾರನ್ನೂ ಬಂಧಿಸುವುದಿಲ್ಲ. ನೀವು ಕರೆ ಕಡಿತಗೊಳಿಸಬಹುದು."
        "hi" -> "असली पुलिस कॉल पर किसी को गिरफ़्तार नहीं करती। आप कॉल काट सकते हैं।"
        else -> "Real police never arrest anyone over a call. You can hang up."
    }

    fun callFamily(lang: String) = when (lang) {
        "kn" -> "ನನ್ನ ಕುಟುಂಬಕ್ಕೆ ಕರೆ ಮಾಡಿ"
        "hi" -> "मेरे परिवार को कॉल करें"
        else -> "Call my family"
    }

    fun callHelpline(lang: String) = when (lang) {
        "kn" -> "ಸೈಬರ್ ಸಹಾಯವಾಣಿ 1930 ಗೆ ಕರೆ ಮಾಡಿ"
        "hi" -> "साइबर हेल्पलाइन 1930 पर कॉल करें"
        else -> "Call cyber helpline 1930"
    }

    fun knownPerson(lang: String) = when (lang) {
        "kn" -> "ಈ ವ್ಯಕ್ತಿ ನನಗೆ ಗೊತ್ತು"
        "hi" -> "मैं इस व्यक्ति को जानता हूँ"
        else -> "I know this person"
    }

    /** The honesty line. Kaavalu never claims certainty. */
    fun uncertainty(lang: String) = when (lang) {
        "kn" -> "ಇದು ವಂಚನೆ ಎಂದು ಕಾವಲು ಖಚಿತವಾಗಿ ಹೇಳಲಾಗದು. ಆದರೆ ಈ ಲಕ್ಷಣಗಳು ಒಟ್ಟಾಗಿ ಡಿಜಿಟಲ್ ಅರೆಸ್ಟ್ ವಂಚನೆಗಳ ರೀತಿಯಲ್ಲಿವೆ."
        "hi" -> "कावलु यह पक्का नहीं कह सकता कि यह ठगी है। लेकिन ये सब लक्षण मिलकर वैसे ही हैं जैसे डिजिटल अरेस्ट ठगी होती है।"
        else -> "Kaavalu can't be certain this is a scam. These signals together are how digital arrest scams usually unfold."
    }

    fun whyHeading(lang: String, score: Int) = when (lang) {
        "kn" -> "ಅಪಾಯ $score/100. ನೀವು ಇದನ್ನು ಏಕೆ ನೋಡುತ್ತಿದ್ದೀರಿ:"
        "hi" -> "जोखिम $score/100. आप यह क्यों देख रहे हैं:"
        else -> "Risk $score/100. Why you are seeing this:"
    }

    /**
     * The breakdown lines on the warning screen, by signal key. This is the panel the jury
     * reads when you say "this is how the phone knows", so it has to be in the same
     * language as the rest of the warning.
     *
     * [arg] carries the number a line needs: minutes for a duration step, call count for a
     * repeat caller.
     */
    fun reasonFor(lang: String, key: String, arg: Int): String = when {
        key == "unknown" -> when (lang) {
            "kn" -> "ನಿಮ್ಮ ಸಂಪರ್ಕಗಳಲ್ಲಿ ಇಲ್ಲದ ಸಂಖ್ಯೆಯಿಂದ ಕರೆ"
            "hi" -> "आपके संपर्कों में न होने वाले नंबर से कॉल"
            else -> "Call from a number not in your contacts"
        }
        key == "unverified" -> when (lang) {
            "kn" -> "ಈ ಸಂಖ್ಯೆಯನ್ನು ನೆಟ್‌ವರ್ಕ್ ಪರಿಶೀಲಿಸಲು ಸಾಧ್ಯವಾಗಲಿಲ್ಲ"
            "hi" -> "नेटवर्क इस नंबर की पुष्टि नहीं कर सका"
            else -> "The network could not verify this number"
        }
        key == "video" -> when (lang) {
            "kn" -> "ಅಪರಿಚಿತ ಸಂಖ್ಯೆಯಿಂದ ವಿಡಿಯೋ ಕರೆ"
            "hi" -> "अनजान नंबर से वीडियो कॉल"
            else -> "Video call from an unknown number"
        }
        key == "repeat" -> when (lang) {
            "kn" -> "ಈ ಅಪರಿಚಿತ ಸಂಖ್ಯೆ ಇಂದು $arg ಬಾರಿ ಕರೆ ಮಾಡಿದೆ"
            "hi" -> "इस अनजान नंबर ने आज $arg बार कॉल किया है"
            else -> "This unknown number has called $arg times today"
        }
        key.startsWith("dur") -> when (lang) {
            "kn" -> "ಈ ಕರೆಯಲ್ಲಿ $arg ನಿಮಿಷಗಳಿಗಿಂತ ಹೆಚ್ಚು ಕಾಲ"
            "hi" -> "इस कॉल पर $arg मिनट से ज़्यादा"
            else -> "On this call for over $arg minutes"
        }
        key == "payment" -> when (lang) {
            "kn" -> "ಈ ಕರೆಯ ಸಮಯದಲ್ಲಿ ಬ್ಯಾಂಕಿಂಗ್ ಅಥವಾ ಯುಪಿಐ ಆ್ಯಪ್ ತೆರೆಯಲಾಗಿದೆ"
            "hi" -> "इस कॉल के दौरान बैंकिंग या यूपीआई ऐप खोला गया"
            else -> "A banking or UPI app was opened during this call"
        }
        key == "remote" -> when (lang) {
            "kn" -> "ಈ ಕರೆಯ ಸಮಯದಲ್ಲಿ ಸ್ಕ್ರೀನ್ ಹಂಚಿಕೆ ಆ್ಯಪ್ ತೆರೆಯಲಾಗಿದೆ"
            "hi" -> "इस कॉल के दौरान स्क्रीन शेयर ऐप खोला गया"
            else -> "A screen-sharing app was opened during this call"
        }
        key == "notice" -> when (lang) {
            "kn" -> "ನೀವು ಸ್ಕ್ಯಾನ್ ಮಾಡಿದ ನೋಟಿಸ್ ನಕಲಿಯಂತೆ ಕಾಣುತ್ತದೆ"
            "hi" -> "आपने जो नोटिस स्कैन किया वह नकली लगता है"
            else -> "You scanned a notice that looks fake"
        }
        else -> key
    }

    /** Which phone capability produced the line above it. */
    fun sourceFor(lang: String, key: String): String = when {
        key == "unknown" -> when (lang) {
            "kn" -> "ಸಂಪರ್ಕಗಳ ಪರಿಶೀಲನೆ (ಫೋನ್‌ನಲ್ಲಿಯೇ)"
            "hi" -> "संपर्क जाँच (इसी फ़ोन पर)"
            else -> "Contacts check (on device)"
        }
        key == "unverified" -> when (lang) {
            "kn" -> "ಕರೆ ಸ್ಕ್ರೀನಿಂಗ್"
            "hi" -> "कॉल स्क्रीनिंग"
            else -> "Call screening"
        }
        key == "video" -> when (lang) {
            "kn" -> "ವಾಟ್ಸಾಪ್ ಕರೆ ಅಧಿಸೂಚನೆ"
            "hi" -> "व्हाट्सऐप कॉल सूचना"
            else -> "WhatsApp call notification"
        }
        key == "repeat" -> when (lang) {
            "kn" -> "ಕರೆ ಇತಿಹಾಸ (ಫೋನ್‌ನಲ್ಲಿಯೇ)"
            "hi" -> "कॉल इतिहास (इसी फ़ोन पर)"
            else -> "Call history (on device)"
        }
        key.startsWith("dur") -> when (lang) {
            "kn" -> "ಕರೆ ಸಮಯ"
            "hi" -> "कॉल टाइमर"
            else -> "Call timer"
        }
        key == "payment" || key == "remote" -> when (lang) {
            "kn" -> "ಆ್ಯಪ್ ಬಳಕೆಯ ಅನುಮತಿ"
            "hi" -> "ऐप उपयोग एक्सेस"
            else -> "App usage access"
        }
        key == "notice" -> when (lang) {
            "kn" -> "ನೋಟಿಸ್ ಸ್ಕ್ಯಾನರ್ (OCR)"
            "hi" -> "नोटिस स्कैनर (OCR)"
            else -> "Notice scanner (OCR)"
        }
        else -> "Kaavalu"
    }

    /**
     * The guardian SMS, whole. The person reading it is the family member who did the
     * setup, so it goes out in the language they chose: half-English, half-Kannada reads
     * like a broken app at the exact moment it has to be believed.
     */
    fun guardianSms(lang: String, name: String, why: String, score: Int): String = when (lang) {
        "kn" -> "ಕಾವಲು ಎಚ್ಚರಿಕೆ: $name ಈಗ ವಂಚನೆಯ ಕರೆಯಲ್ಲಿ ಇರಬಹುದು. $why. ಅಪಾಯ $score/100. " +
            "ದಯವಿಟ್ಟು ಈಗಲೇ ಅವರಿಗೆ ಕರೆ ಮಾಡಿ."
        "hi" -> "कावलु चेतावनी: $name अभी ठगी वाली कॉल पर हो सकते हैं। $why. जोखिम $score/100. " +
            "कृपया उन्हें अभी कॉल करें।"
        else -> "KAAVALU ALERT: $name may be on a scam call right now. $why. " +
            "Risk $score/100. Please call them now."
    }

    /** Spoken answer for "Ask Kaavalu" when the description matches the digital arrest script. */
    fun askScamAnswer(lang: String) = when (lang) {
        "kn" -> "ಇದು ಪ್ರಸಿದ್ಧ ವಂಚನೆ. ಯಾವ ಸಂಸ್ಥೆಯೂ ಕರೆಯಲ್ಲಿ ಬಂಧಿಸುವುದಿಲ್ಲ. ಹಣ ಕಳುಹಿಸಬೇಡಿ. ಕುಟುಂಬಕ್ಕೆ ಅಥವಾ 1930 ಗೆ ಕರೆ ಮಾಡಿ."
        "hi" -> "यह एक जानी-पहचानी ठगी है। कोई एजेंसी कॉल पर गिरफ़्तार नहीं करती। पैसे मत भेजिए। परिवार को या 1930 पर कॉल कीजिए।"
        else -> "This is a known scam. No agency arrests anyone over a call. Do not send money. Call your family or 1930."
    }

    fun askSafeAnswer(lang: String) = when (lang) {
        "kn" -> "ಇದರಲ್ಲಿ ವಂಚನೆಯ ಸ್ಪಷ್ಟ ಲಕ್ಷಣ ಕಾಣಲಿಲ್ಲ. ಸಂದೇಹವಿದ್ದರೆ ಕರೆ ಕಡಿತಗೊಳಿಸಿ ಕುಟುಂಬಕ್ಕೆ ಕರೆ ಮಾಡಿ."
        "hi" -> "इसमें ठगी के साफ़ लक्षण नहीं दिखे। फिर भी शक हो तो कॉल काटकर परिवार को फ़ोन कीजिए।"
        else -> "I did not hear a clear scam pattern in that. If you are unsure, hang up and call your family."
    }
}
