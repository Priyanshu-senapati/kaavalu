package com.dasen.kaavalu

import com.dasen.kaavalu.scan.NoticeMarkers
import com.dasen.kaavalu.scan.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scam scripts actually in circulation in India, kept alongside the cue-based
 * regression tests. Every one of these scored zero against the original marker list,
 * which only knew how a formal CBI arrest warrant reads.
 */
class ScamScriptsTest {

    private fun assertScam(label: String, text: String) {
        val r = NoticeMarkers.evaluate(text)
        assertEquals(
            "$label scored ${r.score}, cues ${r.cues}, matched ${r.found}",
            Verdict.SCAM,
            r.verdict,
        )
    }

    @Test
    fun kycAccountBlocked() = assertScam(
        "KYC",
        "Dear customer, your KYC has expired and your account will be blocked today at " +
            "6 PM. Update immediately and share the OTP sent to your number. " +
            "Click here: bit.ly/kyc-updt. Failing which a penalty will apply.",
    )

    @Test
    fun courierCustomsSeizure() = assertScam(
        "courier",
        "Your parcel has been seized by customs. Illegal items found in your consignment " +
            "linked to your Aadhaar. Contact the investigating officer immediately or " +
            "legal action will follow and you will be arrested.",
    )

    /**
     * This one lands on SUSPICIOUS rather than SCAM, and that is the honest answer: a
     * disconnection notice carries a threat, a fee and a link, but no authority, no
     * secrecy and no isolation. Two kinds of pressure, not four. The user is still warned
     * and told to check with family, which is what matters; asserting SCAM here would mean
     * tuning the threshold to the test rather than to the evidence.
     */
    @Test
    fun electricityDisconnectionAtLeastWarns() {
        val r = NoticeMarkers.evaluate(
            "Dear consumer, your electricity will be disconnected tonight at 9:30 due to " +
                "non payment. Immediately click the link and pay the penalty or your " +
                "connection will be cut. bit.ly/x9",
        )
        assertNotEquals(
            "scored ${r.score}, cues ${r.cues}, matched ${r.found}",
            Verdict.UNCLEAR,
            r.verdict,
        )
        assertNotEquals(Verdict.UNREADABLE, r.verdict)
    }

    @Test
    fun remoteAccessRequest() = assertScam(
        "remote access",
        "Sir please download AnyDesk so I can do screen sharing and verify your bank " +
            "account. Do not disconnect the call and do not tell your family.",
    )

    @Test
    fun lotteryAdvanceFee() = assertScam(
        "lottery",
        "Congratulations! You have won prize money of Rs 25,00,000 in the lucky draw. " +
            "To claim, transfer the amount of processing fee immediately and share your " +
            "account number. Offer valid within 24 hours only.",
    )

    @Test
    fun hindiDigitalArrestMessage() = assertScam(
        "hindi",
        "आपके आधार पर एक पार्सल मिला है। यह गोपनीय है, किसी को मत बताना। " +
            "तुरंत पैसे ट्रांसफर कीजिए वरना गिरफ़्तार कर लिया जाएगा।",
    )

    // --- and the things that must never be called a scam ----------------------------

    @Test
    fun anOrdinaryBillIsNotFlagged() {
        val r = NoticeMarkers.evaluate(
            "Karnataka Electricity Board. Your bill for September 2026 is ready. " +
                "Amount due 1,240 rupees. Pay at any service centre or online.",
        )
        assertNotEquals("scored ${r.score}, matched ${r.found}", Verdict.SCAM, r.verdict)
    }

    @Test
    fun aNewspaperClippingIsNotAScam() {
        val r = NoticeMarkers.evaluate(
            "CBI files chargesheet in bank fraud case, the report said on Tuesday.",
        )
        assertNotEquals("scored ${r.score}, matched ${r.found}", Verdict.SCAM, r.verdict)
    }

    @Test
    fun aGenuineDeliveryMessageIsNotAScam() {
        val r = NoticeMarkers.evaluate(
            "Your parcel has been dispatched and will arrive tomorrow between 10 and 12.",
        )
        assertNotEquals("scored ${r.score}, matched ${r.found}", Verdict.SCAM, r.verdict)
    }

    // --- the failure that matters most ----------------------------------------------

    @Test
    fun anUnreadableImageIsNeverAnAllClear() {
        // OCR returning nothing must not read as "we checked it and it is fine".
        assertEquals(Verdict.UNREADABLE, NoticeMarkers.evaluate("").verdict)
        assertEquals(Verdict.UNREADABLE, NoticeMarkers.evaluate("   \n  ").verdict)
        assertNotEquals(Verdict.UNCLEAR, NoticeMarkers.evaluate("").verdict)
    }

    @Test
    fun aScamNeedsMoreThanOneKindOfPressure() {
        // The whole point of scoring cues: one scary word is not a scam.
        val oneWord = NoticeMarkers.evaluate(
            "The police station is on the main road near the bus stand in Jayanagar.",
        )
        assertNotEquals(Verdict.SCAM, oneWord.verdict)
        assertTrue("should not stack cues: ${oneWord.cues}", oneWord.cues.count { it.isCore } < 2)
    }
}
