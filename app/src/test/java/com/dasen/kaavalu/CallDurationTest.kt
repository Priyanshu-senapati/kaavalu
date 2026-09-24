package com.dasen.kaavalu

import com.dasen.kaavalu.core.Channel
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.core.SessionRecord
import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.spokenDuration
import com.dasen.kaavalu.report.CallReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Found on a real phone: a 32-second call was recorded with when it began and nothing about
 * how long it lasted — the first thing a bank asks. And after hanging up, Home still said
 * "Live call", because the session outlives the call by the watch window.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallDurationTest {

    @Test
    fun hangingUpRecordsTheEndButKeepsWatching() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.submit(Signal.CallStarted("+917848019879", Channel.CELLULAR, isKnown = false))
        runCurrent()
        assertTrue(engine.state.value.onCall)

        advanceTimeBy(32_000)
        engine.submit(Signal.CallEnded)
        runCurrent()

        val s = engine.state.value
        assertTrue("the post-call window is still open", s.sessionActive)
        assertFalse("but the person is no longer on a call", s.onCall)
        val r = SessionRecord.of(s, engine.config.timeScale)
        assertEquals(32_000L, r.durationMs)
    }

    @Test
    fun aSecondCallInsideTheWindowIsLiveAgain() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.submit(Signal.CallStarted("+917848019879", Channel.CELLULAR, isKnown = false))
        engine.submit(Signal.CallEnded)
        runCurrent()
        assertFalse(engine.state.value.onCall)

        engine.submit(Signal.CallStarted("+917848019879", Channel.CELLULAR, isKnown = false))
        runCurrent()
        assertTrue(engine.state.value.onCall)
        assertNull(engine.state.value.endedAt)
    }

    @Test
    fun durationIsReportedInWords() {
        assertEquals("32 seconds", spokenDuration(32_000))
        assertEquals("1 second", spokenDuration(1_000))
        assertEquals("4 min 12 sec", spokenDuration(252_000))
        assertEquals("1 hr 5 min", spokenDuration(3_900_000))
    }

    @Test
    fun theReportSaysHowLongTheCallLasted() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.submit(Signal.CallStarted("+917848019879", Channel.CELLULAR, isKnown = false))
        advanceTimeBy(32_000)
        engine.submit(Signal.CallEnded)
        runCurrent()
        val text = CallReport.build(SessionRecord.of(engine.state.value, 1.0), "Amma") { "t" }
        assertTrue(text, text.contains("Call ended: t (lasted 32 seconds)"))
    }

    @Test
    fun theEndTimeSurvivesStorageAndOldRecordsStillRead() {
        val withEnd = SessionRecord(
            "+91", 15,
            listOf(com.dasen.kaavalu.core.Contribution("unknown", 0, 15, 1_000)),
            emptyList(), 1.0, endedAt = 33_000,
        )
        assertEquals(withEnd, SessionRecord.decode(withEnd.encode()))
        val old = "v1|+91|15|1.0\nc|unknown|0|15|1000\n"
        val read = SessionRecord.decode(old)
        assertNull("a record from before this change has no end time", read?.endedAt)
        assertEquals(15, read?.score)
    }
}
