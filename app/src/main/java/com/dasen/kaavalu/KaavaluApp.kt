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

        fun startGuarding(c: Context) {
            val ctx = c.applicationContext
            ctx.startForegroundService(Intent(ctx, GuardianService::class.java))
            com.dasen.kaavalu.service.ProtectionHealth.schedule(ctx)
        }
    }
}
