package com.dasen.kaavalu.respond

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Notifications
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.core.RiskState
import com.dasen.kaavalu.core.Tier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The only thing that acts. It watches the tier, never the raw score, so the escalation
 * rules live in exactly one place.
 */
class Responder(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val engine: RiskEngine,
) {

    private val speaker = Speaker(ctx)

    fun start() {
        scope.launch {
            var last = Tier.CALM
            engine.state.collect { s ->
                if (s.tier == last) return@collect
                val previous = last
                last = s.tier
                when (s.tier) {
                    Tier.CALM -> {
                        Guardian.reset()
                        speaker.stop()
                        Notifications.clearWarning(ctx)
                    }
                    Tier.WATCH -> Notifications.watch(ctx, s)
                    Tier.INTERRUPT -> interrupt(s)
                    Tier.GUARDIAN -> {
                        // A single signal can jump two tiers at once; never skip the warning.
                        if (previous < Tier.INTERRUPT) interrupt(s)
                        Guardian.alert(ctx, s)
                    }
                }
            }
        }
    }

    fun shutdown() = speaker.shutdown()

    private fun interrupt(s: RiskState) {
        // Always post the notification: on Android 14+ this is what actually reaches the user
        // if the full-screen intent is downgraded.
        Notifications.interruptFallback(ctx, s)

        // Starting an activity from the background needs "Display over other apps".
        if (Settings.canDrawOverlays(ctx)) {
            runCatching {
                ctx.startActivity(
                    Intent(ctx, InterruptActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }.onFailure { Log.w(TAG, "could not raise the interrupt screen: ${it.message}") }
        } else {
            Log.w(TAG, "overlay permission missing, warning stays in the notification shade")
        }

        val lang = Prefs.language(ctx)
        speaker.say(Copy.spoken(lang), lang)
    }

    private companion object {
        const val TAG = "KaavaluResponder"
    }
}
