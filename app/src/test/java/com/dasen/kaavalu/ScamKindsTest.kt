package com.dasen.kaavalu

import com.dasen.kaavalu.scan.NoticeMarkers
import com.dasen.kaavalu.scan.ScamKind
import com.dasen.kaavalu.scan.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scams beyond digital arrest, written and spoken, each paired with an honest document
 * that tells the same story without the pressure. The honest ones are the real test: a
 * marker list that flags every job offer is a marker list nobody keeps installed.
 */
class ScamKindsTest {

    private fun written(label: String, text: String, kind: ScamKind) {
        val r = NoticeMarkers.evaluate(text)
        assertTrue("$label: ${r.verdict} ${r.score}, matched ${r.found}", r.flagged)
        assertEquals("$label kind", kind, r.kind)
    }

    private fun spoken(label: String, text: String, kind: ScamKind) {
        val r = NoticeMarkers.evaluateSpoken(text)
        assertTrue("$label: ${r.verdict} ${r.score}, matched ${r.found}", r.flagged)
        assertEquals("$label kind", kind, r.kind)
    }

    private fun clear(label: String, text: String) {
        val r = NoticeMarkers.evaluate(text)
        assertFalse("$label wrongly flagged: ${r.verdict} ${r.score}, matched ${r.found}", r.flagged)
    }

    private fun clearSpoken(label: String, text: String) {
        val r = NoticeMarkers.evaluateSpoken(text)
        assertFalse("$label wrongly flagged: ${r.verdict} ${r.score}, matched ${r.found}", r.flagged)
    }

    // ── Written ────────────────────────────────────────────────────────────────────────

    @Test
    fun taskJobOffer() = written(
        "job",
        "Part time job! Earn Rs 3000 per day by liking YouTube videos. Complete the prepaid " +
            "task and recharge your account to unlock commission. Join our Telegram group now, " +
            "limited seats, reply immediately.",
        ScamKind.JOB,
    )

    @Test
    fun tradingGroup() = written(
        "investment",
        "Welcome to the VIP trading group. Our stock market tips give guaranteed returns of " +
            "30% monthly. Transfer the amount to the institutional account today to get IPO " +
            "allotment. Withdrawal fee applies before profits are released.",
        ScamKind.INVESTMENT,
    )

    @Test
    fun videoCallBlackmail() = written(
        "sextortion",
        "Your obscene video has been recorded. Pay Rs 50,000 within 24 hours or we will send " +
            "it to all your contacts and make it viral on YouTube. Do not tell anyone.",
        ScamKind.SEXTORTION,
    )

    @Test
    fun fakeRefund() = written(
        "customer care",
        "This is the customer care team. Your refund has been initiated. To receive it, share " +
            "the OTP and install AnyDesk so our executive can process it immediately.",
        ScamKind.CUSTOMER_CARE,
    )

    /** The existing scripts still read as what they are. */
    @Test
    fun digitalArrestStaysDigitalArrest() = written(
        "digital arrest",
        "CBI notice. A warrant has been issued against you for money laundering. You are under " +
            "digital arrest. Do not disclose this to your family. Transfer the amount to the RBI " +
            "safe account within 24 hours.",
        ScamKind.DIGITAL_ARREST,
    )

    // ── Spoken, for Ask Kaavalu ────────────────────────────────────────────────────────

    @Test
    fun spokenRelativeInTrouble() = spoken(
        "relative",
        "someone called and said my grandson had an accident and is at the police station, " +
            "he said send money right now and don't tell my son",
        ScamKind.FAMILY_EMERGENCY,
    )

    @Test
    fun spokenBlackmail() = spoken(
        "sextortion",
        "a woman video called me and now they say they recorded me and will send the video to " +
            "my family unless I pay twenty thousand rupees",
        ScamKind.SEXTORTION,
    )

    @Test
    fun spokenInstallAnApp() = spoken(
        "remote",
        "the customer care man told me to download an app and share my screen for the refund",
        ScamKind.CUSTOMER_CARE,
    )

    @Test
    fun spokenStuckWithdrawal() = spoken(
        "investment",
        "I put money in a trading app with guaranteed profit and now they want a withdrawal " +
            "tax before I can get my money",
        ScamKind.INVESTMENT,
    )

    // ── Honest look-alikes stay clear ──────────────────────────────────────────────────

    @Test
    fun aRealOfferLetterIsClear() = clear(
        "offer letter",
        "We are pleased to offer you the position of Accounts Executive. This is a full time " +
            "role based in Bengaluru. Your annual CTC and joining date are given below. Please " +
            "sign and return a copy of this letter.",
    )

    @Test
    fun aFundStatementIsClear() = clear(
        "fund statement",
        "Consolidated account statement for your mutual fund holdings as on 31 March. Market " +
            "linked investments are subject to risk. Past returns do not indicate future returns.",
    )

    @Test
    fun aRealRefundMessageIsClear() = clear(
        "refund credited",
        "Your refund of Rs 499 for order 4031 has been processed to your original payment " +
            "method. It will reflect in 5 to 7 working days.",
    )

    @Test
    fun anOrdinaryHospitalCallIsClear() = clearSpoken(
        "hospital",
        "my son called from the hospital to say his check up went fine and he will come home tomorrow",
    )

    @Test
    fun anOrdinaryAppHelpCallIsClear() = clearSpoken(
        "app help",
        "my daughter helped me install an app for video calls with the grandchildren",
    )
}
