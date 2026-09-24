package com.dasen.kaavalu.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.respond.Guardian
import com.dasen.kaavalu.ui.MainActivity
import com.dasen.kaavalu.ui.Permissions

/**
 * Protection that switches itself off, silently, is the failure nobody finds until the day
 * it matters. It happens more than it should: Android revokes the permissions of apps that
 * are not opened for a few months — and the parent never opens this one, by design — and
 * some phone makers turn notification access or battery exemptions back off after updates.
 *
 * Once a day, and after every boot, this checks the two safeguards protection cannot work
 * without. When they go from on to off it tells the person holding the phone, and texts the
 * family member who set it up, once, so the one person who can fix it knows to.
 */
object ProtectionHealth {

    /** What to do after a check, given what was true last time. Pure, so it can be tested. */
    data class Decision(val notifyPhone: Boolean, val textFamily: Boolean, val markAlerted: Boolean?)

    /**
     * [wasAlerted] is whether the family has already been told about this outage. The phone
     * is reminded every check while protection is off; the family is texted once per outage,
     * and told again only if it recovers and then breaks a second time.
     */
    fun decide(healthy: Boolean, wasAlerted: Boolean): Decision = when {
        healthy -> Decision(notifyPhone = false, textFamily = false, markAlerted = if (wasAlerted) false else null)
        wasAlerted -> Decision(notifyPhone = true, textFamily = false, markAlerted = null)
        else -> Decision(notifyPhone = true, textFamily = true, markAlerted = true)
    }

    fun check(ctx: Context) {
        if (!Prefs.onboardingDone(ctx)) return
        val healthy = Permissions.essentialsGranted(ctx)
        val d = decide(healthy, Prefs.healthAlerted(ctx))
        Log.d(TAG, "health check: healthy=$healthy decision=$d")
        if (healthy) {
            // Protection may have been killed while its permissions stayed intact.
            runCatching { KaavaluRestart.start(ctx) }
        }
        if (d.notifyPhone) notifyPhone(ctx)
        if (d.textFamily) Guardian.sendHealthAlert(ctx, Copy.healthSms(Prefs.language(ctx), Prefs.userName(ctx)))
        d.markAlerted?.let { Prefs.setHealthAlerted(ctx, it) }
        if (healthy) cancelPhoneNotice(ctx)
    }

    /** Daily, inexact: this is a safety net, not a clock, and it should cost no battery. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HOUR,
            AlarmManager.INTERVAL_DAY,
            pending(ctx),
        )
    }

    private fun pending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, HealthCheckReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notifyPhone(ctx: Context) {
        val lang = Prefs.language(ctx)
        val open = PendingIntent.getActivity(
            ctx, 4, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        ctx.getSystemService(NotificationManager::class.java).notify(
            NOTICE_ID,
            NotificationCompat.Builder(ctx, "warnings")
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(Copy.healthTitle(lang))
                .setContentText(Copy.healthBody(lang))
                .setStyle(NotificationCompat.BigTextStyle().bigText(Copy.healthBody(lang)))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    private fun cancelPhoneNotice(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTICE_ID)
    }

    private const val TAG = "KaavaluHealth"
    private const val NOTICE_ID = 5
}

/** The alarm's landing point. Does the check and nothing else. */
class HealthCheckReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        ProtectionHealth.check(ctx)
    }
}

/** Starting the service from the background can be refused on Android 12+; that is fine. */
private object KaavaluRestart {
    fun start(ctx: Context) {
        ctx.startForegroundService(Intent(ctx, GuardianService::class.java))
    }
}
