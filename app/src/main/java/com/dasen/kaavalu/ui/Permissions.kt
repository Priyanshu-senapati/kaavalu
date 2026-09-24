package com.dasen.kaavalu.ui

import android.Manifest
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * The permission ladder. One screen per step, one plain sentence per step: a parent is not
 * going to grant six permissions to an app that will not say why it wants them.
 */
enum class Step(val title: String, val why: String) {
    CALL_SCREENING(
        "Let Kaavalu see incoming calls",
        "So Kaavalu knows when an unknown number calls. It never blocks or records calls.",
    ),
    RUNTIME(
        "Phone, contacts, SMS and notifications",
        "To tell known callers from strangers, and to text your family in an emergency.",
    ),
    NOTIFICATION_ACCESS(
        "Notification access",
        "To notice WhatsApp calls. Kaavalu never reads your messages.",
    ),
    USAGE_ACCESS(
        "App usage access",
        "To notice if a payment app opens during a suspicious call.",
    ),
    OVERLAY(
        "Display over other apps",
        "So the warning can appear on top of the call.",
    ),
    BATTERY(
        "Keep protection running",
        "So your phone does not switch off the protection to save battery.",
    ),
}

object Permissions {

    val runtimePermissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
    )

    fun isGranted(ctx: Context, step: Step): Boolean = when (step) {
        Step.CALL_SCREENING -> hasCallScreeningRole(ctx)
        Step.RUNTIME -> runtimePermissions.all {
            ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
        Step.NOTIFICATION_ACCESS -> hasNotificationAccess(ctx)
        Step.USAGE_ACCESS -> hasUsageAccess(ctx)
        Step.OVERLAY -> Settings.canDrawOverlays(ctx)
        Step.BATTERY -> isIgnoringBatteryOptimizations(ctx)
    }

    /** The two steps protection genuinely cannot work without. */
    fun essentialsGranted(ctx: Context): Boolean =
        isGranted(ctx, Step.RUNTIME) && isGranted(ctx, Step.NOTIFICATION_ACCESS)

    fun hasCallScreeningRole(ctx: Context): Boolean {
        val rm = ctx.getSystemService(RoleManager::class.java) ?: return false
        return runCatching {
            rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        }.getOrDefault(false)
    }

    fun callScreeningIntent(ctx: Context): Intent? {
        val rm = ctx.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }

    fun hasNotificationAccess(ctx: Context): Boolean =
        ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName,
            )
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun settingsIntent(ctx: Context, step: Step): Intent? = when (step) {
        Step.CALL_SCREENING -> callScreeningIntent(ctx)
        Step.RUNTIME -> null // handled by the runtime permission launcher
        Step.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        Step.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        Step.OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + ctx.packageName),
        )
        @Suppress("BatteryLife")
        Step.BATTERY -> Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:" + ctx.packageName),
        )
    }

    /**
     * Xiaomi, Oppo, Vivo and Realme kill background services regardless of the standard
     * battery exemption. Their autostart screen has to be opened by hand.
     */
    fun autostartIntent(): Intent? {
        val candidates = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
            // Samsung has no autostart list. It puts "unused" apps to sleep from Device Care,
            // which stops the guardian service just as effectively.
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
        )
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val needsIt = listOf("xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "letv", "samsung")
            .any { it in manufacturer }
        if (!needsIt) return null
        return candidates.firstNotNullOfOrNull { (pkg, cls) ->
            Intent().setClassName(pkg, cls)
        }
    }
}
