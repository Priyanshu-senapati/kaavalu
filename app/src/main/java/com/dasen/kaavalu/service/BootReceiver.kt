package com.dasen.kaavalu.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dasen.kaavalu.Prefs

/** Protection that stops at the first reboot is not protection. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.onboardingDone(ctx)) return
        runCatching { ctx.startForegroundService(Intent(ctx, GuardianService::class.java)) }
        // Alarms do not survive a reboot. Re-arm the daily check, and run it now: an update
        // that reset a permission is most often followed by exactly this restart.
        ProtectionHealth.schedule(ctx)
        ProtectionHealth.check(ctx)
    }
}
