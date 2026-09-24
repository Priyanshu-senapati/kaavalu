package com.dasen.kaavalu

import com.dasen.kaavalu.core.Contribution
import com.dasen.kaavalu.core.Escalation
import com.dasen.kaavalu.core.RiskState
import com.dasen.kaavalu.core.SessionRecord
import com.dasen.kaavalu.core.Tier
import com.dasen.kaavalu.report.CallReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report goes to a bank or the police after money has left. It has to survive being
 * stored, read in order, and never claim more than the phone actually saw.
 */
class CallReportTest {

    private val t0 = 1_000_000L
    private val state = RiskState(
        score = 95,
        tier = Tier.GUARDIAN,
        contributions = listOf(
            Contribution("unknown", 0, 15, t0),
            Contribution("international", 0, 20, t0),
            Contribution("dur20", 20, 15, t0 + 20 * 60_000),
            Contribution("payment", 0, 30, t0 + 23 * 60_000),
        ),
        escalations = listOf(
            Escalation(Tier.WATCH, t0 + 20 * 60_000),
            Escalation(Tier.INTERRUPT, t0 + 23 * 60_000),
            Escalation(Tier.GUARDIAN, t0 + 23 * 60_000),
        ),
        sessionActive = true,
        caller = "+855 12 345 678",
    )

    @Test
    fun aRecordSurvivesBeingStored() {
        val r = SessionRecord.of(state, 1.0)
        assertEquals(r, SessionRecord.decode(r.encode()))
    }

    @Test
    fun anythingUnrecognisedIsDroppedNotGuessed() {
        assertNull(SessionRecord.decode(null))
        assertNull(SessionRecord.decode(""))
        assertNull(SessionRecord.decode("v9|x|1|1.0"))
        assertNull(SessionRecord.decode("garbage"))
    }

    @Test
    fun theReportReadsInOrderWithTimesIntoTheCall() {
        val text = CallReport.build(SessionRecord.of(state, 1.0), "Amma") { "25 Sep 2026, 3:58 PM" }
        assertTrue(text.contains("Caller's number: +855 12 345 678"))
        assertTrue(text.contains("Risk score reached: 95/100"))
        val lines = text.lines()
        val payment = lines.indexOfFirst { it.startsWith("23:00") && it.contains("UPI") }
        val interrupted = lines.indexOfFirst { it.startsWith("23:00") && it.contains("interrupted") }
        assertTrue("the signal comes before the response to it", payment in 0 until interrupted)
        assertTrue(lines.any { it.startsWith("0:00") && it.contains("foreign number") })
    }

    @Test
    fun theReportNeverClaimsACrime() {
        val text = CallReport.build(SessionRecord.of(state, 1.0), "Amma") { "" }
        assertTrue(text.contains("not a finding that a crime took place"))
    }
}
