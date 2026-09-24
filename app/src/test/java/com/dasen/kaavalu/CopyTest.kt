package com.dasen.kaavalu

import com.dasen.kaavalu.core.Contribution
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The warning has to read in one language. A breakdown line left in English while the rest
 * of the screen is Kannada is the kind of thing a jury notices and a frightened user does
 * not read at all.
 */
class CopyTest {

    private val keys = listOf(
        "unknown", "unverified", "video", "repeat",
        "dur10", "dur20", "dur40", "payment", "remote", "notice",
        "international", "install",
    )

    @Test
    fun everySignalHasAReasonAndSourceInEveryLanguage() {
        for ((lang, _) in Copy.languages) {
            for (key in keys) {
                val reason = Copy.reasonFor(lang, key, 20)
                val source = Copy.sourceFor(lang, key)
                assertTrue("$lang/$key reason is blank", reason.isNotBlank())
                assertNotEquals("$lang/$key fell through to the raw key", key, reason)
                assertTrue("$lang/$key source is blank", source.isNotBlank())
            }
        }
    }

    @Test
    fun theTimelineSpeaksEveryLanguageToo() {
        // Added with the timeline. Without this the new strings sit outside the guarantee
        // the rest of Copy.kt is held to, and an untranslated line ships unnoticed.
        for ((lang, _) in Copy.languages) {
            assertTrue(Copy.timelineHeading(lang).isNotBlank())
            assertTrue(Copy.whyThisMatters(lang).isNotBlank())
            for (tier in listOf("WATCH", "INTERRUPT", "GUARDIAN")) {
                val label = Copy.escalationLabel(lang, tier)
                assertTrue("$lang/$tier has no label", label.isNotBlank())
                if (lang != "en") {
                    assertNotEquals(
                        "$lang/$tier is still English",
                        Copy.escalationLabel("en", tier),
                        label,
                    )
                }
            }
        }
        // CALM is not an escalation and must not appear on the timeline.
        assertTrue(Copy.escalationLabel("en", "CALM").isEmpty())
    }

    /** Every scam kind gets a spoken answer in every language, and none of them is blank. */
    @Test
    fun everyScamKindHasASpokenAnswer() {
        for ((lang, _) in Copy.languages) {
            for (kind in com.dasen.kaavalu.scan.ScamKind.entries) {
                val answer = Copy.askAnswer(lang, scam = true, kind = kind.name)
                assertTrue("$lang/$kind has no answer", answer.isNotBlank())
                if (lang != "en") {
                    assertNotEquals("$lang/$kind is still English", Copy.askAnswer("en", true, kind.name), answer)
                }
            }
        }
        // The arrest line belongs to the arrest scams only.
        assertTrue(!Copy.askAnswer("en", true, "JOB").contains("arrest"))
        assertTrue(!Copy.askAnswer("en", true, "SEXTORTION").contains("arrest"))
    }

    @Test
    fun breakdownLinesAreTranslatedNotLeftInEnglish() {
        for (key in keys) {
            val english = Copy.reasonFor("en", key, 20)
            assertNotEquals("kn still reads as English for $key", english, Copy.reasonFor("kn", key, 20))
            assertNotEquals("hi still reads as English for $key", english, Copy.reasonFor("hi", key, 20))
        }
    }

    @Test
    fun durationAndRepeatLinesCarryTheirNumber() {
        val duration = Contribution("dur40", 40, 15, 0L)
        assertTrue(Copy.reasonFor("en", duration.key, duration.arg).contains("40"))
        assertTrue(Copy.reasonFor("kn", duration.key, duration.arg).contains("40"))

        val repeat = Contribution("repeat", 3, 10, 0L)
        assertTrue(Copy.reasonFor("en", repeat.key, repeat.arg).contains("3"))
        assertTrue(Copy.reasonFor("hi", repeat.key, repeat.arg).contains("3"))
    }
}
