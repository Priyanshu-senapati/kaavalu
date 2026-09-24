package com.dasen.kaavalu

import com.dasen.kaavalu.core.CallHistory
import com.dasen.kaavalu.core.Contribution
import com.dasen.kaavalu.core.Escalation
import com.dasen.kaavalu.core.SessionRecord
import com.dasen.kaavalu.core.Tier
import com.dasen.kaavalu.core.peak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Found on a real phone: a friend calling from a deleted number was detected — 15 points,
 * logged — and then there was nothing to look at, because only calls reaching Watch were
 * kept, and only the last one. These pin the history that replaced it.
 */
class CallHistoryTest {

    private fun call(at: Long, score: Int = 15, caller: String = "+917848019879", tiers: List<Tier> = emptyList()) =
        SessionRecord(
            caller = caller,
            score = score,
            contributions = listOf(Contribution("unknown", 0, 15, at)),
            escalations = tiers.map { Escalation(it, at) },
            timeScale = 1.0,
        )

    @Test
    fun aQuietUnknownCallIsKept() {
        val list = CallHistory.upsert(emptyList(), call(1_000))
        assertEquals(1, list.size)
        assertEquals(Tier.CALM, list.single().peak)
    }

    @Test
    fun aLiveCallUpdatesItsOwnEntry() {
        var list = CallHistory.upsert(emptyList(), call(1_000, score = 15))
        list = CallHistory.upsert(list, call(1_000, score = 70, tiers = listOf(Tier.WATCH, Tier.INTERRUPT)))
        assertEquals("one call, rewritten, not two", 1, list.size)
        assertEquals(70, list.single().score)
        assertEquals(Tier.INTERRUPT, list.single().peak)
    }

    @Test
    fun newestFirstAndCapped() {
        var list = emptyList<SessionRecord>()
        for (i in 1..25) list = CallHistory.upsert(list, call(i * 1_000L))
        assertEquals(CallHistory.CAP, list.size)
        assertEquals(25_000L, list.first().startedAt)
        assertEquals("the oldest fall off", 6_000L, list.last().startedAt)
    }

    @Test
    fun markingSafeRemovesOnlyThatCall() {
        val list = listOf(call(3_000), call(2_000), call(1_000))
        val after = CallHistory.remove(list, 2_000)
        assertEquals(listOf(3_000L, 1_000L), after.map { it.startedAt })
    }

    @Test
    fun theHistorySurvivesBeingStored() {
        val list = listOf(call(2_000, score = 85, caller = "+855 12 345 678", tiers = listOf(Tier.WATCH, Tier.GUARDIAN)), call(1_000))
        assertEquals(list, CallHistory.decode(CallHistory.encode(list)))
    }

    @Test
    fun aDamagedEntryIsDroppedNotTheWholeHistory() {
        val good = CallHistory.encode(listOf(call(1_000)))
        val raw = "garbage\n==\n$good"
        assertEquals(1, CallHistory.decode(raw).size)
        assertTrue(CallHistory.decode(null).isEmpty())
    }

    /** A phone that recorded one call under the old single-call key keeps it. */
    @Test
    fun theOldSingleRecordReadsAsAHistoryOfOne() {
        val old = call(1_000).encode()
        assertEquals(1, CallHistory.decode(old).size)
    }
}
