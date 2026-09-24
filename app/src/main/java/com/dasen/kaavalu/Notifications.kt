package com.dasen.kaavalu

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dasen.kaavalu.core.RiskState
import com.dasen.kaavalu.respond.InterruptActivity

object Notifications {

    private const val GUARD = "guarding"
    private const val WARN = "warnings"
    const val GUARD_ID = 1
    private const val WARN_ID = 2
    private const val GUARDIAN_FAILED_ID = 3

    fun createChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(GUARD, c.getString(R.string.channel_guarding), NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(WARN, c.getString(R.string.channel_warnings), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                description = "Warnings while a suspicious call is in progress"
            }
        )
    }

    fun guarding(c: Context) = NotificationCompat.Builder(c, GUARD)
        .setSmallIcon(R.drawable.ic_shield)
        .setContentTitle("Kaavalu is protecting this phone")
        .setContentIntent(openApp(c))
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()

    fun watch(c: Context, s: RiskState) {
        val lang = Prefs.language(c)
        post(
            c,
            NotificationCompat.Builder(c, WARN)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(Copy.watchTitle(lang))
                .setContentText(Copy.watchBody(lang))
                .setStyle(NotificationCompat.BigTextStyle().bigText(Copy.watchBody(lang)))
                .setContentIntent(openApp(c))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        )
    }

    /**
     * Backup for the full-screen interrupt. On Android 14+ a full-screen intent from a
     * non-calling app is downgraded to a heads-up notification, so this has to carry the
     * whole warning on its own: title, reason and a tap target.
     */
    fun interruptFallback(c: Context, s: RiskState) {
        val lang = Prefs.language(c)
        val pi = PendingIntent.getActivity(
            c, 0, Intent(c, InterruptActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val why = s.contributions.lastOrNull()
            ?.let { Copy.reasonFor(lang, it.key, it.arg) }
            .orEmpty()
        post(
            c,
            NotificationCompat.Builder(c, WARN)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(Copy.interruptTitle(lang))
                .setContentText("Risk ${s.score}/100: $why. Tap to see why.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(Copy.interruptBody(lang)))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVibrate(longArrayOf(0, 600, 300, 600, 300, 600))
                .setFullScreenIntent(pi, true)
                .setContentIntent(pi)
        )
    }

    /**
     * The family alert did not go out. Tapping it dials the family directly: when the text
     * failed, the call is the only way left, and it should be one tap away.
     */
    fun guardianFailed(c: Context) {
        val lang = Prefs.language(c)
        val number = Prefs.guardian(c)
        val tap = number?.let {
            PendingIntent.getActivity(
                c, 3,
                Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$it")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } ?: openApp(c)
        c.getSystemService(NotificationManager::class.java).notify(
            GUARDIAN_FAILED_ID,
            NotificationCompat.Builder(c, WARN)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(Copy.guardianStatus(lang, "FAILED"))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    fun clearWarning(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(WARN_ID)
    }

    private fun openApp(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 1, Intent(c, com.dasen.kaavalu.ui.MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(c: Context, b: NotificationCompat.Builder) {
        // POST_NOTIFICATIONS may have been denied; notify() is a no-op then, never a crash.
        c.getSystemService(NotificationManager::class.java).notify(WARN_ID, b.build())
    }
}
