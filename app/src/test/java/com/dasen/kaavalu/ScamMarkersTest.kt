package com.dasen.kaavalu

import com.dasen.kaavalu.scan.ScamMarkers
import com.dasen.kaavalu.scan.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real scripts in circulation in India, not just the formal CBI warrant the first version
 * of the list was written against. Every one of these was missed before.
 */
class ScamMarkersTest {

    private fun verdict(text: String) = ScamMarkers.evaluate(text).verdict

    private fun assertScam(label: String, text: String) {
        val r = ScamMarkers.evaluate(text)
        assertEquals("$label scored ${r.score}, matched ${r.found}", Verdict.SCAM, r.verdict)
    }

    @Test
    fun theDigitalArrestWarrant() = assertScam(
        "CBI warrant",
        """
        CENTRAL BUREAU OF INVESTIGATION
        NON-BAILABLE ARREST WARRANT
        You are under digital arrest. Remain online on this video call.
        Do not disclose this to anyone. Transfer the amount to the RBI safe account.
        """.trimIndent(),
    )

    @Test
    fun theKycMessage() = assertScam(
        "KYC scam",
        "Dear customer, your KYC has expired and your account will be blocked today. " +
            "Update immediately and share the OTP sent to your number.",
    )

    @Test
    fun theCourierScam() = assertScam(
        "courier scam",
        "Your parcel has been seized by customs. Illegal items found in your consignment " +
            "linked to your Aadhaar. Contact officer immediately or legal action will follow.",
    )

    @Test
    fun theElectricityDisconnectionScam() = assertScam(
        "electricity scam",
        "Dear consumer, your electricity will be disconnected tonight at 9:30 due to " +
            "non payment. Immediately click the link and update or pay the penalty. bit.ly/x9",
    )

    @Test
    fun theRemoteAccessScam() = assertScam(
        "remote access",
        "Sir please download AnyDesk so I can do screen sharing and verify your bank account. " +
            "Do not disconnect the call.",
    )

    @Test
    fun theLotteryScam() = assertScam(
        "lottery",
        "Congratulations! You have won prize money of Rs 25,00,000 in the lucky draw. " +
            "To claim, transfer the amount of processing fee and share your account number urgently.",
    )

    @Test
    fun hindiDigitalArrestMessage() = assertScam(
        "hindi script",
        "आपके आधार पर एक पार्सल मिला है। यह गोपनीय है, किसी को मत बताना। " +
            "तुरंत पैसे ट्रांसफर कीजिए वरना गिरफ़्तार कर लिया जाएगा।",
    )

    // --- and the things that must NOT be called a scam -------------------------------

    @Test
    fun anOrdinaryBillIsClean() {
        assertEquals(
            Verdict.NOTHING_FOUND,
            verdict("Karnataka Electricity Board. Your bill for September 2026 is ready. Rs 1,240."),
        )
    }

    @Test
    fun aNewspaperClippingIsNotAScam() {
        assertNotEquals(
            Verdict.SCAM,
            verdict("CBI files chargesheet in bank fraud case, says the report."),
        )
    }

    @Test
    fun aGenuineDeliveryMessageIsNotCalledAScam() {
        assertNotEquals(
            Verdict.SCAM,
            verdict("Your parcel has been dispatched and will arrive tomorrow."),
        )
    }

    // --- the bug that mattered most -------------------------------------------------

    @Test
    fun unreadableImageIsNeverAnAllClear() {
        // OCR returning nothing must not look like "we checked and it is fine".
        val r = ScamMarkers.evaluate("")
        assertEquals(Verdict.NO_TEXT, r.verdict)
        assertNotEquals(Verdict.NOTHING_FOUND, r.verdict)
    }

    @Test
    fun aPartialMatchIsCalledSuspiciousNotClean() {
        // Enough to raise an eyebrow, not enough to call it: the user still gets a warning.
        val r = ScamMarkers.evaluate("An officer called about my bank account.")
        assertTrue(
            "scored ${r.score}, matched ${r.found}",
            r.verdict == Verdict.SUSPICIOUS || r.verdict == Verdict.SCAM,
        )
    }
}
