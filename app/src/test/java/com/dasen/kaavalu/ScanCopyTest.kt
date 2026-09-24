package com.dasen.kaavalu

import com.dasen.kaavalu.scan.Cue
import com.dasen.kaavalu.scan.NoticeMarkers
import com.dasen.kaavalu.scan.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scanner used to answer in English on a phone whose every other screen was Kannada.
 * These hold the line: a marker added without translations fails the build here rather
 * than showing up in English on a jury's screen.
 */
class ScanCopyTest {

    private val markerIds: Set<String> =
        (NoticeMarkers.MARKERS + NoticeMarkers.SPOKEN).map { it.id }.toSet()

    @Test
    fun everyMarkerTheScannerCanProduceHasBothTranslations() {
        val missing = markerIds - ScanCopy.IDS
        assertTrue(
            "these markers would be shown in English on a Hindi or Kannada phone: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun noTranslationIsLeftAsItsEnglishFallback() {
        for (id in markerIds) {
            val english = "THE FALLBACK"
            for (lang in listOf("hi", "kn")) {
                assertNotEquals(
                    "$lang/$id fell through to the English fallback",
                    english,
                    ScanCopy.markerReason(lang, id, english),
                )
            }
        }
    }

    @Test
    fun englishAlwaysComesFromTheMarkerItself() {
        // So the English on screen can never drift from the English in the marker list.
        assertEquals(
            "a marker's own words",
            ScanCopy.markerReason("en", "arrest", "a marker's own words"),
        )
    }

    @Test
    fun everyVerdictSpeaksEveryLanguage() {
        for ((lang, _) in Copy.languages) {
            for (v in Verdict.entries) {
                assertTrue(ScanCopy.verdictSign(lang, v.name).isNotBlank())
                assertTrue(ScanCopy.verdictHeadline(lang, v.name).isNotBlank())
                if (lang != "en") {
                    assertNotEquals(
                        "$lang/${v.name} headline is still English",
                        ScanCopy.verdictHeadline("en", v.name),
                        ScanCopy.verdictHeadline(lang, v.name),
                    )
                }
            }
            assertTrue(ScanCopy.instruction(lang).isNotBlank())
            assertTrue(ScanCopy.unclearBody(lang).isNotBlank())
            assertTrue(ScanCopy.unreadableBody(lang).isNotBlank())
            assertTrue(ScanCopy.combination(lang).isNotBlank())
        }
    }

    @Test
    fun everyCueHasAName() {
        for ((lang, _) in Copy.languages) {
            for (c in Cue.entries) {
                val name = ScanCopy.cue(lang, c.name)
                assertTrue("$lang/${c.name} has no name", name.isNotBlank())
                assertNotEquals("$lang/${c.name} fell through to the raw enum", c.name, name)
            }
            // The combination line borrows this function for its own source label.
            assertNotEquals("COMBINATION", ScanCopy.cue(lang, "COMBINATION"))
        }
    }

    @Test
    fun aRealScanComesBackFullyTranslated() {
        val notice = "CENTRAL BUREAU OF INVESTIGATION. You are under digital arrest. " +
            "Do not disclose this to anyone. Transfer the amount to the RBI safe account " +
            "immediately or a non-bailable warrant will be issued."
        val r = NoticeMarkers.evaluate(notice)
        assertEquals(Verdict.SCAM, r.verdict)
        assertTrue("nothing matched", r.evidence.isNotEmpty())

        for (e in r.evidence) {
            for (lang in listOf("hi", "kn")) {
                val line = ScanCopy.markerReason(lang, e.id, e.why)
                assertNotEquals("$lang line for ${e.id} is still the English one", e.why, line)
                assertTrue(line.isNotBlank())
            }
        }
    }
}
