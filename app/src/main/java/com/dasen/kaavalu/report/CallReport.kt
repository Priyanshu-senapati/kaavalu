package com.dasen.kaavalu.report

import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.core.SessionRecord
import com.dasen.kaavalu.core.spokenDuration
import com.dasen.kaavalu.ui.intoCall
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The call, written up for someone who was not there: the bank's fraud desk, the 1930
 * operator, the cyber-crime portal. Always English, whatever language the warnings use,
 * because this is read by an officer, not by the person being protected.
 *
 * It says only what the phone observed. It never says "this was a scam": that is for the
 * people receiving it to decide.
 */
object CallReport {

    fun build(
        r: SessionRecord,
        name: String,
        format: (Long) -> String = { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH).format(Date(it)) },
    ): String = buildString {
        appendLine("Kaavalu call report")
        appendLine()
        appendLine("Person called: $name")
        appendLine("Call began: ${format(r.startedAt)}")
        r.endedAt?.let {
            appendLine("Call ended: ${format(it)} (lasted ${spokenDuration(r.durationMs ?: 0)})")
        } ?: appendLine("Call ended: not recorded")
        appendLine("Caller's number: ${r.caller?.takeIf { it.isNotBlank() } ?: "not shown"}")
        appendLine("Risk score reached: ${r.score}/100")
        appendLine()
        appendLine("What the phone observed (time into the call):")
        val start = r.startedAt
        val entries = r.contributions.map { Triple(it.at, false, "${Copy.reasonFor("en", it.key, it.arg)} (+${it.points})") } +
            r.escalations.map { Triple(it.at, true, Copy.escalationLabel("en", it.tier.name)) }
        entries
            .sortedWith(compareBy({ it.first }, { it.second }))
            .forEach { (at, _, text) -> appendLine("${intoCall(at - start, r.timeScale)}  $text") }
        appendLine()
        append(
            "Recorded on the phone by the Kaavalu app. These are the signals the phone saw during " +
                "the call; they are not a finding that a crime took place.",
        )
    }
}
