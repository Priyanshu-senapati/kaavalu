package com.dasen.kaavalu

import com.dasen.kaavalu.core.Contribution
import com.dasen.kaavalu.core.RiskState
import com.dasen.kaavalu.core.Tier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guardian SMS is the one thing that reaches a person who can actually stop the scam,
 * and it gets about four seconds of their attention. What it says matters.
 */
class GuardianMessageTest {

    /** A session as it really unfolds: the duration ticks land last. */
    private val session = RiskState(
        score = 95,
        tier = Tier.GUARDIAN,
        contributions = listOf(
            Contribution("unknown", 0, 15, 0L),
            Contribution("video", 0, 25, 0L),
            Contribution("dur10", 10, 10, 0L),
            Contribution("dur20", 20, 15, 0L),
            Contribution("payment", 0, 30, 0L),
        ),
        sessionActive = true,
    )

    private fun body(lang: String): String {
        val why = session.contributions
            .sortedByDescending { it.points }
            .take(2)
            .joinToString("; ") { Copy.reasonFor(lang, it.key, it.arg) }
        return Copy.guardianSms(lang, "Amma", why, session.score)
    }

    @Test
    fun itNamesTheStrongestSignalsNotTheMostRecentOnes() {
        val sms = body("en")
        assertTrue("the UPI app is the reason to call", sms.contains("banking or UPI app"))
        assertTrue("the video call is the other one", sms.contains("Video call from an unknown number"))
        assertFalse(
            "a duration tick must not crowd out a real signal",
            sms.contains("On this call for over"),
        )
    }

    @Test
    fun itCarriesTheNameScoreAndACallToAction() {
        val sms = body("en")
        assertTrue(sms.contains("Amma"))
        assertTrue(sms.contains("95/100"))
        assertTrue(sms.contains("call them now"))
    }

    @Test
    fun itReadsInOneLanguageThroughout() {
        // Latin letters in the Kannada and Hindi messages would mean a half-translated SMS.
        for (lang in listOf("kn", "hi")) {
            val sms = body(lang)
            val latinWords = Regex("""[A-Za-z]{4,}""").findAll(sms)
                .map { it.value }
                .filter { it != "Amma" }      // the parent's own name stays as entered
                .toList()
            assertTrue("$lang SMS still contains English: $latinWords", latinWords.isEmpty())
        }
    }

    @Test
    fun everyLanguageFitsInsideACoupleOfSmsParts() {
        // divideMessage would split anything longer; a guardian should see it in one glance.
        for ((lang, _) in Copy.languages) {
            assertTrue("$lang SMS is too long", body(lang).length < 320)
        }
    }
}
