package com.dasen.kaavalu

import com.dasen.kaavalu.core.AppKind
import com.dasen.kaavalu.core.CallerOrigin
import com.dasen.kaavalu.core.Channel
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.core.SensitiveApps
import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.Tier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two call signals added after the digital arrest script: a foreign number, and an app
 * being installed mid-call. Both are cheap to see and hard to explain innocently while a
 * stranger is on the line, and both must stay silent in the ordinary cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WiderDetectionTest {

    @Test
    fun foreignCountryCodesAreInternational() {
        assertTrue(CallerOrigin.isInternational("+855 12 345 678"))    // Cambodia
        assertTrue(CallerOrigin.isInternational("+92 300 1234567"))    // Pakistan
        assertTrue(CallerOrigin.isInternational("+971501234567"))      // UAE
        assertTrue(CallerOrigin.isInternational("00855123456789"))     // dialled with 00
    }

    @Test
    fun indianAndUnknownOriginsAreNot() {
        assertFalse(CallerOrigin.isInternational("+91 98765 43210"))
        assertFalse(CallerOrigin.isInternational("0091 98765 43210"))
        assertFalse(CallerOrigin.isInternational("9876543210"))        // no code: local
        assertFalse(CallerOrigin.isInternational("08012345678"))       // STD code
        assertFalse(CallerOrigin.isInternational("~Ravi"))             // WhatsApp name
        assertFalse(CallerOrigin.isInternational(""))
        assertFalse(CallerOrigin.isInternational(null))
        assertFalse(CallerOrigin.isInternational("+1"))                // too short to be a number
    }

    @Test
    fun anUnknownForeignVideoCallScoresTheCountryCode() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.submit(
            Signal.CallStarted("+855 12 345 678", Channel.WHATSAPP, isKnown = false, isVideo = true),
        )
        runCurrent()
        val s = engine.state.value
        assertEquals("unknown 15 + video 25 + international 20", 60, s.score)
        assertEquals(listOf("unknown", "video", "international"), s.contributions.map { it.key })
        assertEquals(Tier.WATCH, s.tier)
    }

    @Test
    fun anIndianUnknownCallerIsNotScoredAsForeign() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.submit(Signal.CallStarted("+919800000001", Channel.CELLULAR, isKnown = false))
        runCurrent()
        assertTrue(engine.state.value.contributions.none { it.key == "international" })
    }

    /** A relative abroad is saved in the contacts, and a known caller opens no session at all. */
    @Test
    fun aKnownCallerAbroadStaysSilent() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.submit(Signal.CallStarted("+14155550100", Channel.WHATSAPP, isKnown = true, isVideo = true))
        runCurrent()
        assertEquals(0, engine.state.value.score)
        assertFalse(engine.state.value.sessionActive)
    }

    @Test
    fun theSystemInstallerIsRecognised() {
        assertEquals(AppKind.INSTALLER, SensitiveApps.kindOf("com.google.android.packageinstaller"))
        assertEquals(AppKind.INSTALLER, SensitiveApps.kindOf("com.android.packageinstaller"))
        assertEquals(null, SensitiveApps.kindOf("com.android.vending"))
    }

    /** The malware route: a foreign video call walks the victim through installing an APK. */
    @Test
    fun installingAnAppMidCallInterrupts() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.submit(
            Signal.CallStarted("+855 12 345 678", Channel.WHATSAPP, isKnown = false, isVideo = true),
        )
        engine.submit(Signal.SensitiveAppOpened("com.google.android.packageinstaller", AppKind.INSTALLER))
        runCurrent()
        val s = engine.state.value
        assertEquals("60 + install 25", 85, s.score)
        assertEquals(Tier.GUARDIAN, s.tier)
        assertEquals("install", s.contributions.last().key)
    }

    /** Opening the installer twice is one fact, not two. */
    @Test
    fun theInstallerCountsOnce() = runTest {
        val engine = RiskEngine(backgroundScope) { testScheduler.currentTime }
        engine.submit(Signal.CallStarted("+918000000044", Channel.CELLULAR, isKnown = false))
        repeat(3) {
            engine.submit(Signal.SensitiveAppOpened("com.google.android.packageinstaller", AppKind.INSTALLER))
        }
        runCurrent()
        assertEquals(15 + 25, engine.state.value.score)
    }
}
