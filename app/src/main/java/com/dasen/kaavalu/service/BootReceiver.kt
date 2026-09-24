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
    }
}
