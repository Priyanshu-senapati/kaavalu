package com.dasen.kaavalu

import android.app.Application
import android.content.Context
import android.content.Intent
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.service.GuardianService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class KaavaluApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var engine: RiskEngine
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        // Start the engine before any detector can emit.
        engine = RiskEngine(appScope).also { it.start() }
    }

    companion object {
        fun engineOf(c: Context): RiskEngine =
            (c.applicationContext as KaavaluApp).engine

        fun startGuarding(c: Context) {
            val ctx = c.applicationContext
            ctx.startForegroundService(Intent(ctx, GuardianService::class.java))
        }
    }
}
