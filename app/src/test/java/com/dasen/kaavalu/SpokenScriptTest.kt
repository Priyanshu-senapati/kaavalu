package com.dasen.kaavalu

import com.dasen.kaavalu.scan.NoticeMarkers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Ask Kaavalu" hears a frightened person describing a call, not a printed warrant. The
 * sentence below is the one from the pitch, and it has to be recognised.
 */
class SpokenScriptTest {

    private fun scam(text: String) = NoticeMarkers.evaluateSpoken(text).score >= 25

    @Test
    fun thePitchExampleIsRecognised() {
        assertTrue(
            scam("someone from CBI called and said my Aadhaar is in a parcel case"),
        )
    }

    @Test
    fun otherWordingsOfTheSameScriptAreRecognised() {
        assertTrue(scam("a police officer says there is a warrant against me"))
        assertTrue(scam("they said a courier in my name had drugs and I must stay on the call"))
        assertTrue(scam("an officer is asking about my bank account and my aadhaar number"))
    }

    @Test
    fun ordinaryCallsAreNotFlagged() {
        assertFalse(scam("my daughter called about dinner on sunday"))
        assertFalse(scam("the electricity office called about the meter reading"))
    }

    @Test
    fun spokenCuesDoNotLeakIntoTheNoticeScanner() {
        // "parcel" and "police" alone must not flag a scanned document.
        val ordinary = "Your parcel has been dispatched. Contact the police station for lost items."
        assertFalse(NoticeMarkers.evaluate(ordinary).flagged)
    }
}
