package com.dasen.kaavalu.core

/**
 * A call from an unknown number, kept after the engine has forgotten it.
 *
 * The engine clears a session thirty minutes after the call ends, which is right for
 * scoring and wrong for what comes next: the person who has already sent money reaches for
 * help an hour later, and the bank and the cyber-crime portal both ask the same questions —
 * when did it start, what number, what happened. This is the answer, kept on the phone.
 *
 * [CallHistory] keeps the last twenty of these, quiet calls included.
 */
data class SessionRecord(
    val caller: String?,
    val score: Int,
    val contributions: List<Contribution>,
    val escalations: List<Escalation>,
    val timeScale: Double,
    /** When the call ended; null while it is still going, or for records made before this. */
    val endedAt: Long? = null,
) {
    val startedAt: Long get() = contributions.firstOrNull()?.at ?: 0L

    /** How long the call lasted in call time (scaled under demo time), once it has ended. */
    val durationMs: Long? get() = endedAt?.let { ((it - startedAt).coerceAtLeast(0) * timeScale).toLong() }

    /** A line format, not JSON: no dependency, and readable in a bug report. */
    fun encode(): String = buildString {
        // The end time rides as an optional fifth field, so older records still read.
        appendLine("v1|${caller.orEmpty().replace("|", "")}|$score|$timeScale|${endedAt ?: ""}")
        contributions.forEach { appendLine("c|${it.key}|${it.arg}|${it.points}|${it.at}") }
        escalations.forEach { appendLine("e|${it.tier.name}|${it.at}") }
    }

    companion object {
        fun of(s: RiskState, timeScale: Double) =
            SessionRecord(s.caller, s.score, s.contributions, s.escalations, timeScale, s.endedAt)

        /** Null for anything it does not recognise: a lost report beats a wrong one. */
        fun decode(raw: String?): SessionRecord? = runCatching {
            val lines = raw?.lines()?.filter { it.isNotBlank() } ?: return null
            val head = lines.first().split("|")
            if (head[0] != "v1") return null
            val contributions = mutableListOf<Contribution>()
            val escalations = mutableListOf<Escalation>()
            lines.drop(1).forEach { line ->
                val f = line.split("|")
                when (f[0]) {
                    "c" -> contributions += Contribution(f[1], f[2].toInt(), f[3].toInt(), f[4].toLong())
                    "e" -> escalations += Escalation(Tier.valueOf(f[1]), f[2].toLong())
                }
            }
            if (contributions.isEmpty()) return null
            SessionRecord(
                head[1].ifBlank { null },
                head[2].toInt(),
                contributions,
                escalations,
                head[3].toDouble(),
                head.getOrNull(4)?.toLongOrNull(),
            )
        }.getOrNull()
    }
}

/**
 * "32 seconds", "4 min 12 sec", "1 hr 5 min". Words rather than a clock reading, because
 * "0:32" next to a time of day reads as a time, and this is read aloud to a bank officer.
 */
fun spokenDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return when {
        h > 0 -> "$h hr $m min"
        m > 0 -> "$m min $sec sec"
        else -> if (sec == 1L) "1 second" else "$sec seconds"
    }
}
