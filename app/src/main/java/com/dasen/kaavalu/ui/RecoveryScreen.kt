package com.dasen.kaavalu.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.core.CallHistory
import com.dasen.kaavalu.core.Tier
import com.dasen.kaavalu.core.peak

/**
 * "I already sent money." The screen for the worst moment, when the warning did not stop
 * it or came too late. Money sent by UPI or bank transfer moves through several accounts
 * within hours, and the 1930 helpline can ask banks to hold it while it is still moving, so
 * this is a list in order of how much each step can still save, with the first one set as
 * the only thing on the screen that matters.
 *
 * It ends with the call report: what the phone saw, written up for the bank or the police.
 */
@Composable
fun RecoveryScreen(onBack: () -> Unit, onHistory: () -> Unit = {}) {
    val ctx = LocalContext.current
    // The most recent call that reached Caution, or failing that the most recent call.
    val record = remember {
        val calls = CallHistory.decode(Prefs.history(ctx))
        calls.firstOrNull { it.peak >= Tier.WATCH } ?: calls.firstOrNull()
    }
    val guardian = remember { Prefs.guardian(ctx) }

    ScreenColumn {
        ScreenTitle(
            title = "Act in the next hour",
            lead = "This happens to careful people. What matters now is how fast the bank is told.",
            onBack = onBack,
        )

        Plate(Alarm, watermark = Color.White.copy(alpha = 0.07f), depth = 0.2f) {
            SignTag("Do this first", container = Gold, content = Alarm900)
            Spacer(Modifier.height(Space.xxs))
            Text("Call 1930 now", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(
                "The national cyber-crime helpline can ask banks to hold money while it is still " +
                    "moving between accounts. Have the amount, the time and your bank's name ready.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(Space.xs))
            BigAction("Call 1930", style = ActionStyle.Gold, icon = R.drawable.ic_phone, critical = true) {
                dialNumber(ctx, "1930")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionTitle("Then, in this order")
            Sheet(padding = Space.lg, spacing = 0.dp) {
                RecoveryStep(
                    2,
                    "Call your bank",
                    "Use the number on the back of your card or in your passbook — never a number " +
                        "the caller gave you. Ask them to block the transaction and the account.",
                )
                HRule()
                RecoveryStep(
                    3,
                    "Report it online",
                    "File a complaint on the National Cyber Crime Reporting Portal and keep the " +
                        "complaint number. The bank will ask for it.",
                ) {
                    BigAction("Open cybercrime.gov.in", style = ActionStyle.Secondary) {
                        open(ctx, "https://cybercrime.gov.in")
                    }
                }
                HRule()
                RecoveryStep(
                    4,
                    "Report the caller’s number",
                    "Report it on Sanchar Saathi (Chakshu), so the number can be cut off before " +
                        "it reaches someone else.",
                ) {
                    BigAction("Open Sanchar Saathi", style = ActionStyle.Secondary) {
                        open(ctx, "https://sancharsaathi.gov.in")
                    }
                }
                HRule()
                RecoveryStep(
                    5,
                    "Tell your family",
                    if (guardian != null) "They can help make these calls, and keep you company while you do."
                    else "Someone you trust can help make these calls.",
                ) {
                    guardian?.let {
                        BigAction("Call my family", style = ActionStyle.Secondary, icon = R.drawable.ic_family) {
                            dialNumber(ctx, it)
                        }
                    }
                }
            }
        }

        Instruction(
            "Anyone who offers to get your money back for a fee is running a second scam.",
            Tone.Danger,
        )

        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            SectionTitle("The call report")
            if (record == null) {
                Text(
                    "No suspicious call has been recorded on this phone. You can still report " +
                        "it using the steps above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            } else {
                Text(
                    (if (record.peak >= Tier.WATCH) "What the phone saw during the most recent suspicious call. "
                    else "No call reached Caution. This is the most recent call from an unknown number. ") +
                        "The bank and the police will ask for exactly this.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
                CallRecord(record)
            }
            BigAction("See all recent calls", style = ActionStyle.Ghost, textStyle = MaterialTheme.typography.titleMedium, onClick = onHistory)
        }
    }
}

/** One numbered step. The number is set large, like the three rules: this is an order to follow. */
@Composable
private fun RecoveryStep(
    n: Int,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = Space.md)) {
        Text(
            "$n",
            style = KType.dial,
            color = Guard500,
            textAlign = TextAlign.End,
            modifier = Modifier.width(32.dp),
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Ink2)
            action?.let {
                Spacer(Modifier.height(Space.xxs))
                it()
            }
        }
    }
}

private fun open(ctx: android.content.Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
