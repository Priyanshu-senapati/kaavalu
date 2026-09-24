package com.dasen.kaavalu.respond

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.core.RiskState

/**
 * The last tier. SMS, not push, because it needs no data connection and reaches a family
 * member on any phone. The message carries the reasons, never the caller's details.
 */
object Guardian {

    fun alert(ctx: Context, s: RiskState) {
        val to = Prefs.guardian(ctx)
        if (to.isNullOrBlank()) {
            Log.w(TAG, "no guardian number saved, nothing to alert")
            return
        }
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SEND_SMS not granted")
            return
        }
        val body = compose(ctx, s)
        runCatching {
            val sms = ctx.getSystemService(SmsManager::class.java)
            sms.sendMultipartTextMessage(to, null, sms.divideMessage(body), null, null)
        }.onFailure { Log.e(TAG, "guardian SMS failed: ${it.message}") }
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

    private const val TAG = "KaavaluGuardian"
}
