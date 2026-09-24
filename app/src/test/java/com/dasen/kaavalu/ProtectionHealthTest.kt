package com.dasen.kaavalu

import com.dasen.kaavalu.service.ProtectionHealth
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The health check texts a family member, so what it decides matters as much as what it
 * detects: it must speak up once when protection goes off, not every day, and speak up
 * again if protection is fixed and then breaks a second time.
 */
class ProtectionHealthTest {

    @Test
    fun aHealthyPhoneStaysQuiet() {
        val d = ProtectionHealth.decide(healthy = true, wasAlerted = false)
        assertEquals(ProtectionHealth.Decision(notifyPhone = false, textFamily = false, markAlerted = null), d)
    }

    @Test
    fun theFirstCheckAfterProtectionGoesOffTellsBoth() {
        val d = ProtectionHealth.decide(healthy = false, wasAlerted = false)
        assertEquals(ProtectionHealth.Decision(notifyPhone = true, textFamily = true, markAlerted = true), d)
    }

    @Test
    fun theFamilyIsNotTextedEveryDay() {
        val d = ProtectionHealth.decide(healthy = false, wasAlerted = true)
        assertEquals("the phone is reminded, the family is not", true, d.notifyPhone)
        assertEquals(false, d.textFamily)
    }

    @Test
    fun recoveringClearsTheFlagSoTheNextOutageIsReported() {
        val recovered = ProtectionHealth.decide(healthy = true, wasAlerted = true)
        assertEquals(false, recovered.markAlerted)
        val brokeAgain = ProtectionHealth.decide(healthy = false, wasAlerted = false)
        assertEquals(true, brokeAgain.textFamily)
    }
}
