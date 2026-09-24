package com.dasen.kaavalu.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.dasen.kaavalu.KaavaluApp
import com.dasen.kaavalu.Notifications
import com.dasen.kaavalu.detect.AppUsageWatcher
import com.dasen.kaavalu.detect.CallStateWatcher
import com.dasen.kaavalu.respond.Responder

/**
 * One low-importance foreground service keeps the whole guardian alive. The call screening
 * service and the notification listener are bound by the system and only post to the bus.
 */
class GuardianService : Service() {

    private var callWatcher: CallStateWatcher? = null
    private var responder: Responder? = null

    override fun onCreate() {
        super.onCreate()
        val type =
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.GUARD_ID, Notifications.guarding(this), type)

        val app = application as KaavaluApp
        callWatcher = CallStateWatcher(this).also { it.start() }
        AppUsageWatcher(this, app.appScope, app.engine).start()
        responder = Responder(this, app.appScope, app.engine).also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        callWatcher?.stop()
        responder?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
