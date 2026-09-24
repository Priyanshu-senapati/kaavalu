package com.dasen.kaavalu.respond

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Speaks the warning aloud. USAGE_ASSISTANCE_SONIFICATION rather than USAGE_MEDIA so the
 * warning is not ducked to nothing while a call is in progress.
 */
class Speaker(ctx: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) {
            Log.w(TAG, "text to speech unavailable, falling back to the screen and vibration")
            return
        }
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
    }

    fun say(text: String, lang: String) {
        if (!ready) return
        val wanted = Locale.Builder().setLanguage(lang).setRegion("IN").build()
        tts.language =
            if (tts.isLanguageAvailable(wanted) >= TextToSpeech.LANG_AVAILABLE) wanted
            else Locale.Builder().setLanguage("en").setRegion("IN").build()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kaavalu-warning")
    }

    fun stop() {
        runCatching { tts.stop() }
    }

    fun shutdown() {
        runCatching { tts.shutdown() }
        ready = false
    }

    private companion object {
        const val TAG = "KaavaluSpeaker"
    }
}
