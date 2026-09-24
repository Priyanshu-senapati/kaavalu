package com.dasen.kaavalu

import com.dasen.kaavalu.scan.NoticeMarkers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The marker list is a pure function over OCR text, so it can be tested without a camera.
 * The sample below is written from how these notices are documented to read.
 */
class NoticeScannerTest {

    @Test
    fun fakeCbiWarrantIsFlagged() {
        val notice = """
            CENTRAL BUREAU OF INVESTIGATION
            NON-BAILABLE ARREST WARRANT
            Your Aadhaar is linked to a parcel containing narcotics.
            You are under digital arrest. Remain online on this video call.
            Do not disclose this matter to anyone, it is confidential.
            Transfer the amount to the RBI safe account for verification. It is refundable.
        """.trimIndent()

        val result = NoticeMarkers.evaluate(notice)
        assertTrue("a full scam notice must clear the flag threshold", result.flagged)
        assertTrue(result.found.any { it.contains("digital arrest") })
        assertTrue(result.found.size >= 5)
    }

    @Test
    fun ordinaryLetterIsNotFlagged() {
        val letter = """
            Karnataka Electricity Board
            Your bill for September 2026 is ready.
            Amount due 1,240 rupees. Pay at any service centre or online.
        """.trimIndent()

        assertFalse(NoticeMarkers.evaluate(letter).flagged)
    }

    @Test
    fun oneScaryWordAloneIsNotEnough() {
        // Naming an agency is 10 points. A newspaper clipping should not trigger a warning.
        val clipping = "CBI files chargesheet in bank fraud case, says the report."
        val result = NoticeMarkers.evaluate(clipping)
        assertFalse(result.flagged)
    }
}
