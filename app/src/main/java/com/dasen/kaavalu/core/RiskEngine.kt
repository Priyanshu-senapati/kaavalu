package com.dasen.kaavalu.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Tier { CALM, WATCH, INTERRUPT, GUARDIAN }

data class RiskConfig(
    val unknownCaller: Int = 15,
    val unverifiedNumber: Int = 10,
    val videoFromUnknown: Int = 25,
    val durationSteps: List<Pair<Int, Int>> = listOf(10 to 10, 20 to 15, 40 to 15),
    val repeatCaller: Int = 10,
    val paymentAppDuringCall: Int = 30,
    val remoteAccessDuringCall: Int = 35,
    val flaggedNotice: Int = 20,
    val watchAt: Int = 40,
    val interruptAt: Int = 65,
    val guardianAt: Int = 80,
    val postCallWindowMin: Int = 30,
    /** 1.0 in real use. 60.0 in demo: one real second counts as one minute. */
    val timeScale: Double = 1.0,
)

/**
 * One line of the explanation: what was seen, when, and for how many points.
 *
 * The engine stores the signal [key], not a sentence, so the warning screen can render the
 * breakdown in the language the family chose at setup. [arg] carries the number the line
 * needs: minutes for a duration step, call count for a repeat caller.
 */
data class Contribution(val key: String, val arg: Int, val points: Int, val at: Long)

/**
 * The moment the session crossed into a tier. Recorded separately from contributions
 * because "Kaavalu interrupted" is not a signal the phone saw, it is something the app
 * did, and a timeline that shows only the evidence leaves out the response to it.
 */
data class Escalation(val tier: Tier, val at: Long)

data class RiskState(
    val score: Int = 0,
    val tier: Tier = Tier.CALM,
    val contributions: List<Contribution> = emptyList(),
    val escalations: List<Escalation> = emptyList(),
    val sessionActive: Boolean = false,
    val caller: String? = null,
)

/**
 * The only thing that scores. Detectors emit signals; the responder reacts to tiers.
 * The score is NOT a probability: v1 weights are hand-set from how the scam is documented to work.
 *
 * [now] is injectable so the hero-scenario unit test can run on virtual time.
 */
class RiskEngine(
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {

    @Volatile
    var config: RiskConfig = RiskConfig()

    private val _state = MutableStateFlow(RiskState())
    val state: StateFlow<RiskState> = _state.asStateFlow()

    private val fired = mutableSetOf<String>()
    private var callStartedAt = 0L
    private var lastNoticeAt = 0L
    private var ticker: Job? = null
    private var closer: Job? = null

    fun start() {
        scope.launch { SignalBus.signals.collect { submit(it) } }
    }

    /** Public so the demo console and the unit tests can drive the engine without the bus. */
    @Synchronized
    fun submit(s: Signal) {
        val c = config
        when (s) {
            is Signal.CallStarted -> {
                if (s.isKnown) return
                closer?.cancel()
                if (!_state.value.sessionActive) {
                    fired.clear()
                    _state.value = RiskState(sessionActive = true, caller = s.number)
                }
                callStartedAt = now()
                add("unknown", c.unknownCaller)
                if (s.unverified) add("unverified", c.unverifiedNumber)
                if (s.isVideo) add("video", c.videoFromUnknown)
                if (lastNoticeAt > 0L && now() - lastNoticeAt < 48 * HOUR) {
                    add("notice", c.flaggedNotice)
                }
                startTicker()
            }

            is Signal.CallEnded -> {
                if (!_state.value.sessionActive) return
                ticker?.cancel()
                // Victims are often told to transfer money right after the call: keep watching.
                val windowMs = (c.postCallWindowMin * MINUTE / c.timeScale).toLong()
                closer = scope.launch {
                    delay(windowMs)
                    reset()
                }
            }

            is Signal.RepeatCaller -> {
                if (s.count >= 2) add("repeat", c.repeatCaller, arg = s.count)
            }

            is Signal.SensitiveAppOpened -> when (s.kind) {
                AppKind.PAYMENT -> add("payment", c.paymentAppDuringCall)
                AppKind.REMOTE_ACCESS -> add("remote", c.remoteAccessDuringCall)
            }

            is Signal.NoticeFlagged -> {
                lastNoticeAt = now()
                add("notice", c.flaggedNotice)
            }

            Signal.MarkedSafe -> reset()
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val minutes = (now() - callStartedAt) / MINUTE.toDouble() * config.timeScale
                for ((atMin, points) in config.durationSteps) {
                    if (minutes >= atMin) add("dur$atMin", points, arg = atMin)
                }
            }
        }
    }

    /** Each signal counts once per session. The tier never drops inside a session. */
    @Synchronized
    private fun add(key: String, points: Int, arg: Int = 0) {
        val cur = _state.value
        if (!cur.sessionActive || !fired.add(key)) return
        val at = now()
        val score = (cur.score + points).coerceAtMost(100)
        val entry = Contribution(key, arg, points, at)
        val tier = maxOf(cur.tier, tierFor(score))
        _state.value = cur.copy(
            score = score,
            tier = tier,
            contributions = cur.contributions + entry,
            // Every tier crossed, not just the one landed on. A single signal can carry
            // the score from Watch past Interrupt to Guardian, and the responder does
            // raise the interrupt in that case, so a record that named only the final
            // tier would say the warning never happened.
            escalations = cur.escalations + Tier.entries
                .filter { it > cur.tier && it <= tier }
                .map { Escalation(it, at) },
        )
    }

    private fun tierFor(score: Int): Tier = with(config) {
        when {
            score >= guardianAt -> Tier.GUARDIAN
            score >= interruptAt -> Tier.INTERRUPT
            score >= watchAt -> Tier.WATCH
            else -> Tier.CALM
        }
    }

    @Synchronized
    fun reset() {
        ticker?.cancel()
        closer?.cancel()
        fired.clear()
        _state.value = RiskState()
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val TICK_MS = 1_000L
    }
}
