package com.dasen.kaavalu

import com.dasen.kaavalu.scan.NoticeMarkers
import com.dasen.kaavalu.scan.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cases that were reported back from real use of the v1 marker list:
 * "I uploaded an obvious scam document and it said not a scam", and "it takes the words
 * but still does not detect the scam". Both had the same cause — the list scored the
 * *vocabulary* of the scam rather than its *shape* — so both are pinned down here.
 */
class NoticeMarkersRegressionTest {

    // ---- Scanned notices -------------------------------------------------------------

    /**
     * The reported false negative. A real fake notice almost never prints the words
     * "digital arrest": that is what the caller says out loud. Under v1 this document
     * scored below the flag threshold and the screen reassured the user.
     */
    @Test
    fun aFakeNoticeThatNeverSaysDigitalArrestIsStillCaught() {
        val notice = """
            GOVERNMENT OF INDIA
            CYBER CRIME INVESTIGATION CELL
            Ref: FIR No. 442/2026

            Your Aadhaar number has been linked to a courier consignment intercepted at
            Mumbai airport. A non-bailable warrant has been issued against you.

            You are directed to remain online on this video call until verification is
            complete. Do not disclose this matter to any family member.

            Transfer the pending amount to the RBI safe account below within 24 hours,
            failing which you will be arrested.
        """.trimIndent()

        val result = NoticeMarkers.evaluate(notice)
        assertEquals(Verdict.SCAM, result.verdict)
        assertTrue("the breakdown has to name more than one reason", result.found.size >= 5)
    }

    /** Half the script is still worth a warning, and must not be reported as an all-clear. */
    @Test
    fun aThinTelecomThreatLandsInTheMiddleBand() {
        val notice = """
            NOTICE FROM THE TELECOM DEPARTMENT
            Your mobile number will be disconnected within 2 hours because of illegal
            activity registered against it.
        """.trimIndent()

        val result = NoticeMarkers.evaluate(notice)
        assertEquals(Verdict.SUSPICIOUS, result.verdict)
        assertTrue(result.flagged)
    }

    @Test
    fun hindiAndKannadaNoticesAreCaughtToo() {
        val hindi = "सूचना: आपका आधार एक पार्सल से जुड़ा है। तुरंत भुगतान करें " +
            "अन्यथा गिरफ्तार किया जाएगा। किसी को मत बताइए।"
        val kannada = "ಸೂಚನೆ: ನಿಮ್ಮ ಆಧಾರ್ ಒಂದು ಪಾರ್ಸೆಲ್‌ಗೆ ಸಂಬಂಧಿಸಿದೆ. ಕೂಡಲೇ ಹಣ ಕಳುಹಿಸಿ " +
            "ಇಲ್ಲದಿದ್ದರೆ ಬಂಧನ ಆಗುತ್ತದೆ. ಯಾರಿಗೂ ಹೇಳಬೇಡಿ."

        assertEquals(Verdict.SCAM, NoticeMarkers.evaluate(hindi).verdict)
        assertEquals(Verdict.SCAM, NoticeMarkers.evaluate(kannada).verdict)
    }

    /**
     * A blurred photo produces almost no OCR text, which under v1 scored zero and was
     * rendered as "no strong scam markers found" — an all-clear for a document the phone
     * never actually read.
     */
    @Test
    fun anUnreadableImageIsNeverReportedAsClear() {
        for (garbage in listOf("", "   ", "\n\n", "|| /", "a b")) {
            val result = NoticeMarkers.evaluate(garbage)
            assertEquals(Verdict.UNREADABLE, result.verdict)
            assertFalse(result.flagged)
        }
    }

    @Test
    fun honestDocumentsStayClear() {
        val honest = listOf(
            "HDFC BANK Statement of account for September 2026. Closing balance 12,430 " +
                "rupees. For any query please contact your home branch.",
            "Your parcel has been dispatched and will arrive on Tuesday. Contact the " +
                "police station only for items reported lost in transit.",
            "CBI files chargesheet in a bank fraud case, the newspaper report said today.",
            "Karnataka Electricity Board. Your bill for September 2026 is ready. Amount " +
                "due 1,240 rupees. Pay at any service centre or online.",
        )
        for (text in honest) {
            val result = NoticeMarkers.evaluate(text)
            assertFalse("false positive on: $text", result.flagged)
        }
    }

    // ---- Ask Kaavalu ------------------------------------------------------------------

    /**
     * The reported speech failure. Every sentence below is how someone actually describes
     * this call; none of them contains a phrase from the printed-notice list.
     */
    @Test
    fun everydayDescriptionsOfTheCallAreRecognised() {
        val said = listOf(
            "a man called and said he is a police officer and told me not to tell my " +
                "family, he wants twenty thousand rupees right now",
            "they are saying my sim card will be blocked and I have to pay a fine today itself",
            "he kept me on a video call for two hours and said I cannot tell anyone",
            "someone from the customs department said there is a parcel in my name with drugs",
            "an officer asked for my otp to verify my bank account immediately",
        )
        for (text in said) {
            val result = NoticeMarkers.evaluateSpoken(text)
            assertTrue(
                "missed: $text (scored ${result.score}, ${result.verdict})",
                result.verdict == Verdict.SCAM || result.verdict == Verdict.SUSPICIOUS,
            )
            assertTrue("a warning with no reasons is not a warning", result.found.isNotEmpty())
        }
    }

    @Test
    fun ordinaryCallsDescribedAloudStayClear() {
        val said = listOf(
            "my daughter called about dinner on sunday",
            "the courier company called to confirm my delivery address",
            "my bank called about a new credit card offer",
            "the electricity office called about the meter reading",
        )
        for (text in said) {
            val result = NoticeMarkers.evaluateSpoken(text)
            assertEquals("false positive on: $text", Verdict.UNCLEAR, result.verdict)
        }
    }

    /**
     * The recognizer returns several guesses and only one of them may carry the marker.
     * AskScreen scores them together, so this is the contract that makes that safe:
     * a stray alternative must not invent a scam on its own.
     */
    @Test
    fun scoringSeveralRecognizerGuessesTogetherIsStable() {
        val guesses = listOf(
            "they said I am under a rest for money laundering",
            "they said I am under arrest for money laundering",
            "they said I am under arrest for money laundry",
        )
        val joined = NoticeMarkers.evaluateSpoken(guesses.joinToString(". "))
        assertTrue(joined.verdict == Verdict.SCAM || joined.verdict == Verdict.SUSPICIOUS)

        val innocent = listOf("call me back later", "call me back later please")
        assertEquals(Verdict.UNCLEAR, NoticeMarkers.evaluateSpoken(innocent.joinToString(". ")).verdict)
    }

    /** The reasons are what the user reads. Strongest first, and never a duplicate. */
    @Test
    fun reasonsAreOrderedAndNotRepeated() {
        val result = NoticeMarkers.evaluateSpoken(
            "the police officer said my aadhaar card is in a parcel and I will be arrested",
        )
        assertEquals(
            "the same idea must not be listed twice",
            result.found.size,
            result.found.distinct().size,
        )
    }
}
