package com.dasen.kaavalu.respond

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Notifications
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.core.RiskState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * What happened to a family alert. "Sent" means the phone handed every part of the SMS to
 * the network; delivery reports are carrier-dependent, so the app never claims more than that.
 */
enum class Delivery { NONE, SENDING, SENT, FAILED, NO_NUMBER, NO_PERMISSION }

/**
 * The last tier. SMS, not push, because it needs no data connection and reaches a family
 * member on any phone. The message carries the reasons, never the caller's details.
 *
 * It used to send and forget. An alert that fails — no balance, no signal, permission
 * revoked — failed silently, and the person on the warning screen believed their family had
 * been told. Every part now reports back, and a failure is said out loud: on the warning
 * screen, and as a notification that dials the family instead.
 */
object Guardian {

    private val _status = MutableStateFlow(Delivery.NONE)

    /** The real alert, for the warning screen. */
    val status: StateFlow<Delivery> = _status.asStateFlow()

    private val _testStatus = MutableStateFlow(Delivery.NONE)

    /** The test alert, for the Setup screen. Kept apart so a test never reads as a warning. */
    val testStatus: StateFlow<Delivery> = _testStatus.asStateFlow()

    fun alert(ctx: Context, s: RiskState) {
        send(ctx, compose(ctx, s), _status, raiseOnFailure = true)
    }

    /**
     * The "protection has stopped" text. Reported nowhere on screen: the person holding the
     * phone gets their own notification, and this is not a warning about a call.
     */
    fun sendHealthAlert(ctx: Context, body: String) {
        send(ctx, body, flow = null, raiseOnFailure = false)
    }

    /**
     * Proof that the alert path works, sent on demand. A guardian SMS that silently fails
     * is worse than no guardian at all, and you only find out on the day it matters.
     */
    fun sendTest(ctx: Context) {
        send(ctx, Copy.guardianTestSms(Prefs.language(ctx), Prefs.userName(ctx)), _testStatus, raiseOnFailure = false)
    }

    /** A new session starts with no alert outstanding. */
    fun reset() {
        _status.value = Delivery.NONE
    }

    /**
     * The two signals with the most points, NOT the two most recent. Duration steps fire
     * last, so "on this call for over 20 minutes" would otherwise crowd out the video call
     * and the UPI app, which are the reasons a son or daughter actually picks up the phone.
     */
    fun compose(ctx: Context, s: RiskState): String {
        val lang = Prefs.language(ctx)
        val why = s.contributions
            .sortedByDescending { it.points }
            .take(2)
            .joinToString("; ") { Copy.reasonFor(lang, it.key, it.arg) }
        return Copy.guardianSms(lang, Prefs.userName(ctx), why, s.score)
    }

    private fun send(
        ctx: Context,
        body: String,
        flow: MutableStateFlow<Delivery>?,
        raiseOnFailure: Boolean,
    ) {
        val to = Prefs.guardian(ctx)
        if (to.isNullOrBlank()) {
            Log.w(TAG, "no guardian number saved, nothing to alert")
            flow?.value = Delivery.NO_NUMBER
            return
        }
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SEND_SMS not granted")
            flow?.value = Delivery.NO_PERMISSION
            if (raiseOnFailure) Notifications.guardianFailed(ctx)
            return
        }
        val app = ctx.applicationContext
        ensureReceiver(app)
        flow?.value = Delivery.SENDING
        runCatching {
            val sms = app.getSystemService(SmsManager::class.java)
            val parts = sms.divideMessage(body)
            val id = nextId.incrementAndGet()
            outstanding[id] = Outstanding(flow, raiseOnFailure, AtomicInteger(parts.size))
            val sent = ArrayList(
                parts.indices.map { i ->
                    PendingIntent.getBroadcast(
                        app,
                        id * 100 + i,
                        Intent(ACTION_SENT).setPackage(app.packageName).putExtra(EXTRA_ID, id),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
                    )
                },
            )
            sms.sendMultipartTextMessage(to, null, parts, sent, null)
        }.onFailure {
            Log.e(TAG, "guardian SMS failed: ${it.message}")
            fail(app, flow, raiseOnFailure)
        }
    }

    private class Outstanding(
        val flow: MutableStateFlow<Delivery>?,
        val raiseOnFailure: Boolean,
        val remaining: AtomicInteger,
    ) {
        @Volatile var failed = false
    }

    private val outstanding = java.util.concurrent.ConcurrentHashMap<Int, Outstanding>()
    private val nextId = AtomicInteger(0)
    @Volatile private var registered = false

    private fun ensureReceiver(app: Context) {
        if (registered) return
        synchronized(this) {
            if (registered) return
            app.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context, intent: Intent) {
                        val id = intent.getIntExtra(EXTRA_ID, -1)
                        val o = outstanding[id] ?: return
                        if (resultCode != Activity.RESULT_OK) {
                            Log.w(TAG, "SMS part failed with result $resultCode")
                            o.failed = true
                        }
                        if (o.remaining.decrementAndGet() > 0) return
                        outstanding.remove(id)
                        if (o.failed) fail(app, o.flow, o.raiseOnFailure) else o.flow?.value = Delivery.SENT
                    }
                },
                IntentFilter(ACTION_SENT),
                Context.RECEIVER_NOT_EXPORTED,
            )
            registered = true
        }
    }

    private fun fail(ctx: Context, flow: MutableStateFlow<Delivery>?, raise: Boolean) {
        flow?.value = Delivery.FAILED
        if (raise) Notifications.guardianFailed(ctx)
    }

    private const val TAG = "KaavaluGuardian"
    private const val ACTION_SENT = "com.dasen.kaavalu.GUARDIAN_SMS_SENT"
    private const val EXTRA_ID = "id"
}
