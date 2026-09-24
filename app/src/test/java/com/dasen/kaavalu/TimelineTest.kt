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
 * The timeline is only worth showing if the order and the times are true. These pin the
 * record itself; how it is drawn is a rendering question.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineTest {

    @Test
    fun everySignalIsStampedWithWhenItWasSeen() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.config = engine.config.copy(timeScale = 60.0)

        engine.submit(
            Signal.CallStarted("+919800000001", Channel.WHATSAPP, isKnown = false, isVideo = true),
        )
        runCurrent()
        advanceTimeBy(21_000)
        runCurrent()
        engine.submit(Signal.SensitiveAppOpened("com.phonepe.app", AppKind.PAYMENT))
        runCurrent()

        val times = engine.state.value.contributions.map { it.at }
        assertEquals(5, times.size)
        assertEquals("a timeline out of order is a lie", times.sorted(), times)
        assertTrue(
            "the duration steps must be stamped later than the call opening",
            times.last() > times.first(),
        )
    }

    @Test
    fun crossingATierIsRecordedWithItsMoment() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.config = engine.config.copy(timeScale = 60.0)

        engine.submit(
            Signal.CallStarted("+919800000001", Channel.WHATSAPP, isKnown = false, isVideo = true),
        )
        runCurrent()
        assertEquals(listOf(Tier.WATCH), engine.state.value.escalations.map { it.tier })

        advanceTimeBy(21_000)
        runCurrent()
        engine.submit(Signal.SensitiveAppOpened("com.phonepe.app", AppKind.PAYMENT))
        runCurrent()

        val s = engine.state.value
        assertEquals(
            "the story is watch, then interrupt, then the family",
            listOf(Tier.WATCH, Tier.INTERRUPT, Tier.GUARDIAN),
            s.escalations.map { it.tier },
        )
        assertEquals(s.escalations.map { it.at }.sorted(), s.escalations.map { it.at })
    }

    /**
     * Found on the phone: the score went 50 to 80 in one step, skipping past Interrupt,
     * and the timeline showed "your family was told" with no warning before it. The
     * responder does raise the interrupt when a tier is jumped, so the record has to say
     * so too, or it contradicts what the user just watched happen.
     */
    @Test
    fun aJumpPastATierStillRecordsIt() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }

        engine.submit(
            Signal.CallStarted("+919800000001", Channel.WHATSAPP, isKnown = false, isVideo = true),
        )
        runCurrent()
        assertEquals(listOf(Tier.WATCH), engine.state.value.escalations.map { it.tier })

        // 40 + 30 = 70 lands on Interrupt; 70 + 35 would pass Guardian in the same breath.
        engine.submit(Signal.SensitiveAppOpened("com.phonepe.app", AppKind.PAYMENT))
        engine.submit(Signal.SensitiveAppOpened("com.anydesk.anydeskandroid", AppKind.REMOTE_ACCESS))
        runCurrent()

        assertEquals(
            "the interrupt must appear even though the score passed straight through it",
            listOf(Tier.WATCH, Tier.INTERRUPT, Tier.GUARDIAN),
            engine.state.value.escalations.map { it.tier },
        )
    }

    @Test
    fun aTierIsNeverRecordedTwice() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }

        engine.submit(
            Signal.CallStarted("+919800000001", Channel.WHATSAPP, isKnown = false, isVideo = true),
        )
        engine.submit(Signal.SensitiveAppOpened("com.anydesk.anydeskandroid", AppKind.REMOTE_ACCESS))
        engine.submit(Signal.SensitiveAppOpened("com.anydesk.anydeskandroid", AppKind.REMOTE_ACCESS))
        runCurrent()

        val tiers = engine.state.value.escalations.map { it.tier }
        assertEquals("each step up happens once", tiers.distinct(), tiers)
    }

    @Test
    fun aQuietSessionHasNothingToShow() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }

        // A plain unknown cellular call scores 15: watched, but never escalated.
        engine.submit(Signal.CallStarted("+918000000044", Channel.CELLULAR, isKnown = false))
        runCurrent()

        assertEquals(Tier.CALM, engine.state.value.tier)
        assertTrue(engine.state.value.escalations.isEmpty())
        assertEquals(1, engine.state.value.contributions.size)
    }

    @Test
    fun endingTheSessionClearsTheRecord() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }

        engine.submit(
            Signal.CallStarted("+919800000001", Channel.WHATSAPP, isKnown = false, isVideo = true),
        )
        runCurrent()
        assertTrue(engine.state.value.escalations.isNotEmpty())

        engine.submit(Signal.MarkedSafe)
        runCurrent()
        assertTrue("a trusted caller leaves no trail", engine.state.value.escalations.isEmpty())
        assertTrue(engine.state.value.contributions.isEmpty())
    }
}
