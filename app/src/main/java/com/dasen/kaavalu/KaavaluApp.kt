package com.dasen.kaavalu

import android.app.Application
import android.content.Context
import android.content.Intent
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.core.SessionRecord
import com.dasen.kaavalu.core.CallHistory
import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.SignalBus
import kotlinx.coroutines.launch
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
        recordHistory()
    }

    /**
     * Every unknown-caller call goes into the history, rewritten as each signal lands, and
     * survives the engine's reset. A session only has contributions when the caller was
     * unknown, so known callers never appear. "I know this person" takes the call back out.
     */
    private fun recordHistory() {
        val lock = Any()
        var current: Long? = null
        // A state update already in flight when the caller is marked safe must not put the
        // call back. Removed calls are remembered for the life of the process.
        val removed = mutableSetOf<Long>()
        appScope.launch {
            engine.state.collect { s ->
                if (s.contributions.isEmpty()) return@collect
                val r = SessionRecord.of(s, engine.config.timeScale)
                synchronized(lock) {
                    if (r.startedAt in removed) return@synchronized
                    current = r.startedAt
                    val list = CallHistory.decode(Prefs.history(this@KaavaluApp))
                    Prefs.setHistory(this@KaavaluApp, CallHistory.encode(CallHistory.upsert(list, r)))
                }
            }
        }
        appScope.launch {
            SignalBus.signals.collect { signal ->
                if (signal != Signal.MarkedSafe) return@collect
                synchronized(lock) {
                    val started = current ?: return@synchronized
                    removed += started
                    val list = CallHistory.decode(Prefs.history(this@KaavaluApp))
                    Prefs.setHistory(this@KaavaluApp, CallHistory.encode(CallHistory.remove(list, started)))
                    current = null
                }
            }
        }
    }

    companion object {
        fun engineOf(c: Context): RiskEngine =
            (c.applicationContext as KaavaluApp).engine

        /**
         * Back to a phone that has never been set up: for a new person, or to show setup to
         * someone from the first screen. There are no accounts to sign out of — everything
         * Kaavalu knows lives on this phone — so this clears it all and stops protection
         * until setup is finished again. Android permissions stay granted: an app cannot
         * revoke its own, and setup will simply show each step as already on.
         */
        fun startOver(c: Context) {
            val ctx = c.applicationContext
            engineOf(ctx).reset()
            com.dasen.kaavalu.respond.Guardian.reset()
            ctx.stopService(Intent(ctx, GuardianService::class.java))
            com.dasen.kaavalu.service.ProtectionHealth.cancel(ctx)
            ctx.getSystemService(android.app.NotificationManager::class.java)?.cancelAll()
            // The last photographed notice sits in the cache; it belongs to the old setup.
            java.io.File(ctx.cacheDir, "scans").deleteRecursively()
            Prefs.clearAll(ctx)
        }

        fun startGuarding(c: Context) {
            val ctx = c.applicationContext
            ctx.startForegroundService(Intent(ctx, GuardianService::class.java))
            com.dasen.kaavalu.service.ProtectionHealth.schedule(ctx)
        }
    }
}
