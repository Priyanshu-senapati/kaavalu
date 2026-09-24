package com.dasen.kaavalu

import com.dasen.kaavalu.scan.NoticeMarkers
import com.dasen.kaavalu.scan.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verbatim ML Kit output from photographing a fake Directorate of Enforcement "arrest
 * order" on a real phone, mangled spelling and all. Captured from logcat rather than typed
 * out, because the mangling is the point.
 *
 * This document scored 20 out of 100 and came back merely "suspicious", because every
 * marker that should have caught it needed two adjacent, correctly spelled words:
 *   "arrest warrant"          -> OCR gave "afrest w artant"
 *   "enforcement directorate" -> the letterhead actually reads "Directorate of Enforcement"
 *   "money laundering"        -> OCR gave "Money Lamdering"
 * Real notices are photographed, not pasted, so OCR noise is the normal case and exact
 * phrase matching is the wrong tool.
 */
class RealOcrTest {

    private val edArrestOrder = """
        GOVERNMENT OF INDIA DIRECTORATE OF ENFORCEMENT HEADQUARTERS INVESTIGATION UNIT2
        BLOCK-B, PRAVARTAN BHAWAN, DR. APJABDUL KALAM ROAD, NEW DELH-I10011 FNo.
        ECIRHIU-DL08 2024 ARREST ORDER Dated:08 08.2024 Whereas.I Jogender Assistant
        Director. Directorate of Enforcement have reason to believe that the Birendra Singh
        Yadav Sio or Do Col GL yadavis about 76 vears has been found to be guilty of an
        offence punishable under the provisions of Prevention of Money Lamdering Act,
        2002(15 of 2003) Now, therefore, in exercise of the powers conferred on me under sub
        Section (I) of section, 19 of the Prerention of Money Laundering Act. 2002 (15 of
        2003), | hereby issue the afrest w artant for the said of Mr. Amit Sharma, at 11:30
        hours 08.08.24 and he has been inform ed me of the grounds for arrest. A copy of the
        grounds of artest(containing 14 pages) have been served upon him. Dated:- pay of
        Angust' Two Thousand Twenty Four JOGENDER ASSISTANT DIRECTOR HIU-2(3)(2)
    """.trimIndent()

    @Test
    fun theDocumentThatSlippedThroughIsNowCaught() {
        val r = NoticeMarkers.evaluate(edArrestOrder)
        assertEquals(
            "scored ${r.score}, matched ${r.found}",
            Verdict.SCAM,
            r.verdict,
        )
    }

    @Test
    fun itCatchesTheDocumentOnSeveralIndependentGrounds() {
        // One lucky match is a coincidence. This has to hold up when a judge asks why.
        // Three, not more: the scorer keeps only the strongest marker per id, so the two
        // threat lines in this document count once each rather than padding the list.
        val r = NoticeMarkers.evaluate(edArrestOrder)
        assertTrue("only matched ${r.found}", r.found.size >= 3)
        assertTrue(
            "needs at least two different kinds of pressure, had ${r.cues}",
            r.cues.count { it.isCore } >= 2,
        )
    }

    @Test
    fun ocrMisspellingOfLaunderingStillMatches() {
        val mangled = NoticeMarkers.evaluate("provisions of Prevention of Money Lamdering Act")
        assertTrue("matched ${mangled.found}", mangled.found.isNotEmpty())
    }

    /**
     * The honest limit of this approach, written down so nobody assumes otherwise.
     *
     * In "afrest w artant" both words are corrupted, and no amount of token matching
     * recovers that. Fuzzy matching would, but a fuzzy "arrest" also fires on "arrested",
     * "forest" and half the words in a news article, which buys a miss here at the price
     * of false alarms everywhere. What saves the real document is redundancy: an arrest
     * order says "arrest" many times, and OCR does not mangle every one of them.
     */
    @Test
    fun aDoublyMangledPhraseIsMissedButTheDocumentIsStillCaught() {
        val justThatPhrase = NoticeMarkers.evaluate("I hereby issue the afrest w artant")
        assertEquals(
            "both words corrupted, nothing to match on",
            Verdict.UNCLEAR,
            justThatPhrase.verdict,
        )

        // The same phrase inside the real document, where "grounds for arrest" survived.
        assertEquals(Verdict.SCAM, NoticeMarkers.evaluate(edArrestOrder).verdict)
    }

    @Test
    fun theLetterheadWordOrderIsTheOneRealDocumentsUse() {
        // "Directorate of Enforcement", not "Enforcement Directorate". The reversed form
        // is the one that reads naturally in English and the one that never appears on
        // the document.
        val r = NoticeMarkers.evaluate(
            "GOVERNMENT OF INDIA DIRECTORATE OF ENFORCEMENT HEADQUARTERS INVESTIGATION",
        )
        assertTrue("nothing matched the real letterhead", r.found.isNotEmpty())
    }
}
