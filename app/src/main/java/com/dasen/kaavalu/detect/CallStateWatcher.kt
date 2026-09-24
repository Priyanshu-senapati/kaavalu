package com.dasen.kaavalu.detect

import android.content.Context
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.dasen.kaavalu.core.Channel
import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.SignalBus

/** Turns "the phone went off-hook" into a session start, using what screening already learned. */
class CallStateWatcher(private val ctx: Context) {

    private val tm = ctx.getSystemService(TelephonyManager::class.java)
    private var registered = false

    private val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            // Logged on every transition: when a real call produced no warning, the silence
            // here was indistinguishable from the watcher never having registered at all.
            Log.d(TAG, "call state $state (${stateName(state)})")
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> CallContext.take()?.let { p ->
                    Log.d(TAG, "answered: number=${p.number} known=${p.known} unverified=${p.unverified}")
                    SignalBus.emit(
                        Signal.CallStarted(p.number, Channel.CELLULAR, p.known, unverified = p.unverified)
                    )
                    val count = CallContext.recordAndCount(p.number)
                    if (!p.known && count >= 2) SignalBus.emit(Signal.RepeatCaller(count))
                }
                TelephonyManager.CALL_STATE_IDLE -> SignalBus.emit(Signal.CallEnded)
            }
        }
    }

    /** Needs READ_PHONE_STATE. If it was denied, protection degrades instead of crashing. */
    fun start() {
        if (registered || tm == null) return
        runCatching { tm.registerTelephonyCallback(ctx.mainExecutor, callback) }
            .onSuccess { registered = true }
            .onFailure { Log.w(TAG, "call state unavailable: ${it.message}") }
    }

    fun stop() {
        if (!registered) return
        runCatching { tm?.unregisterTelephonyCallback(callback) }
        registered = false
    }

    private companion object {
        const val TAG = "KaavaluCallState"

        fun stateName(state: Int) = when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "idle"
            TelephonyManager.CALL_STATE_RINGING -> "ringing"
            TelephonyManager.CALL_STATE_OFFHOOK -> "answered"
            else -> "unknown"
        }
    }
}
