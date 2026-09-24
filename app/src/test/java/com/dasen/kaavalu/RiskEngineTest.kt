package com.dasen.kaavalu

import com.dasen.kaavalu.core.AppKind
import com.dasen.kaavalu.core.Channel
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.Tier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine is the one piece the whole demo rests on, so it is the one piece with tests.
 * Virtual time comes from the test scheduler, which is why RiskEngine takes a clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RiskEngineTest {

    /** The hero scenario from the demo script. It must reach GUARDIAN, every time. */
    @Test
    fun unknownVideoCallThenUpiAppReachesGuardian() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.config = engine.config.copy(timeScale = 60.0)   // 1 second counts as 1 minute

        engine.submit(
            Signal.CallStarted(
                number = "+919800000001",
                channel = Channel.WHATSAPP,
                isKnown = false,
                isVideo = true,
            ),
        )
        runCurrent()
        assertEquals("unknown 15 + video 25", 40, engine.state.value.score)
        assertEquals(Tier.WATCH, engine.state.value.tier)

        advanceTimeBy(21_000)   // 21 simulated minutes: crosses the 10 and 20 minute steps
        runCurrent()
        assertEquals("plus duration 10 + 15", 65, engine.state.value.score)
        assertEquals(Tier.INTERRUPT, engine.state.value.tier)

        engine.submit(Signal.SensitiveAppOpened("com.phonepe.app", AppKind.PAYMENT))
        runCurrent()
        assertEquals("plus payment app 30", 95, engine.state.value.score)
        assertEquals(Tier.GUARDIAN, engine.state.value.tier)

        val breakdown = engine.state.value.contributions
        assertEquals(5, breakdown.size)
        assertTrue("every point must name the signal it came from", breakdown.all { it.key.isNotBlank() })
        assertEquals(
            "the breakdown must read in order, one line per signal",
            listOf("unknown", "video", "dur10", "dur20", "payment"),
            breakdown.map { it.key },
        )
        assertEquals(95, breakdown.sumOf { it.points })
    }

    /** The false-alarm answer: a long call from a stranger alone never interrupts. */
    @Test
    fun longCellularCallAloneStaysAtWatch() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.config = engine.config.copy(timeScale = 60.0)

        engine.submit(
            Signal.CallStarted("+918000000044", Channel.CELLULAR, isKnown = false),
        )
        advanceTimeBy(45_000)   // 45 simulated minutes
        runCurrent()

        assertEquals("15 + 10 + 15 + 15", 55, engine.state.value.score)
        assertEquals(Tier.WATCH, engine.state.value.tier)
    }

    /** Kaavalu stays silent for people you know. */
    @Test
    fun knownCallerOpensNoSession() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }

        engine.submit(Signal.CallStarted("+919900000000", Channel.CELLULAR, isKnown = true))
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(0, engine.state.value.score)
        assertEquals(Tier.CALM, engine.state.value.tier)
        assertTrue(!engine.state.value.sessionActive)
    }

    /** A scammer must not be able to talk the phone back down inside one session. */
    @Test
    fun tierNeverDropsInsideASession() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }

        engine.submit(
            Signal.CallStarted("+919800000001", Channel.WHATSAPP, isKnown = false, isVideo = true),
        )
        engine.submit(Signal.SensitiveAppOpened("com.anydesk.anydeskandroid", AppKind.REMOTE_ACCESS))
        runCurrent()
        assertEquals("15 + 25 + 35", 75, engine.state.value.score)
        assertEquals(Tier.INTERRUPT, engine.state.value.tier)

        // A repeat of the same signal adds nothing and cannot lower the tier.
        engine.submit(Signal.SensitiveAppOpened("com.anydesk.anydeskandroid", AppKind.REMOTE_ACCESS))
        runCurrent()
        assertEquals(75, engine.state.value.score)
        assertEquals(Tier.INTERRUPT, engine.state.value.tier)
    }

    /** The score is capped, so it can never be reported as more than 100. */
    @Test
    fun scoreIsCappedAtOneHundred() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.config = engine.config.copy(timeScale = 60.0)

        engine.submit(
            Signal.CallStarted(
                "+919800000001", Channel.WHATSAPP,
                isKnown = false, isVideo = true, unverified = true,
            ),
        )
        engine.submit(Signal.RepeatCaller(3))
        engine.submit(Signal.SensitiveAppOpened("com.phonepe.app", AppKind.PAYMENT))
        engine.submit(Signal.SensitiveAppOpened("com.anydesk.anydeskandroid", AppKind.REMOTE_ACCESS))
        advanceTimeBy(45_000)
        runCurrent()

        assertEquals(100, engine.state.value.score)
    }
}
