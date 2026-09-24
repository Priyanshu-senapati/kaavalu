package com.dasen.kaavalu.detect

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.dasen.kaavalu.core.RiskEngine
import com.dasen.kaavalu.core.SensitiveApps
import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.SignalBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Polls app launches ONLY while a suspicious session is open, so the battery cost outside
 * an unknown call is effectively zero. UsageStatsManager is used instead of an Accessibility
 * Service: it tells us which app came to the foreground without reading the screen.
 */
class AppUsageWatcher(
    ctx: Context,
    private val scope: CoroutineScope,
    private val engine: RiskEngine,
) {

    private val usm = ctx.getSystemService(UsageStatsManager::class.java)
    private var job: Job? = null

    fun start() {
        scope.launch {
            engine.state.map { it.sessionActive }.distinctUntilChanged().collect { active ->
                job?.cancel()
                job = if (active) scope.launch { poll() } else null
            }
        }
    }

    private suspend fun poll() {
        var since = System.currentTimeMillis() - 5_000
        val event = UsageEvents.Event()
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            val events = runCatching { usm?.queryEvents(since, now) }
                .onFailure { Log.w(TAG, "usage access missing: ${it.message}") }
                .getOrNull()
            while (events?.hasNextEvent() == true) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    SensitiveApps.kindOf(event.packageName)?.let { kind ->
                        Log.d(TAG, "sensitive app in foreground: ${event.packageName}")
                        SignalBus.emit(Signal.SensitiveAppOpened(event.packageName, kind))
                    }
                }
            }
            since = now
            delay(POLL_MS)
        }
    }

    private companion object {
        const val TAG = "KaavaluUsage"
        const val POLL_MS = 2_000L
    }
}
