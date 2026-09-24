package com.dasen.kaavalu.respond

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dasen.kaavalu.Copy
import com.dasen.kaavalu.KaavaluApp
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.core.Signal
import com.dasen.kaavalu.core.SignalBus

private val Alarm = Color(0xFF8E1F1A)
private val Gold = Color(0xFFFFD58A)

/**
 * The interrupt. Large type, high contrast, three choices and no way to read it as routine.
 * It never claims certainty: it shows the points and says what they mean.
 */
class InterruptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        val engine = KaavaluApp.engineOf(this)
        val lang = Prefs.language(this)

        setContent {
            val s by engine.state.collectAsStateWithLifecycle()
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Alarm)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.height(24.dp))

                Text(
                    Copy.interruptTitle(lang),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 38.sp,
                )
                Text(Copy.interruptBody(lang), color = Color.White, fontSize = 20.sp, lineHeight = 28.sp)

                Text(
                    Copy.whyHeading(lang, s.score),
                    color = Gold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                s.contributions.forEach { c ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "+${c.points}",
                            color = Gold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(44.dp),
                        )
                        Column {
                            Text(
                                Copy.reasonFor(lang, c.key, c.arg),
                                color = Color.White,
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                            )
                            Text(
                                Copy.sourceFor(lang, c.key),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                Text(
                    Copy.uncertainty(lang),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )

                Spacer(Modifier.height(8.dp))

                BigButton(Copy.callFamily(lang), Color.White, Alarm) {
                    Prefs.guardian(this@InterruptActivity)?.let { dial(it) }
                }
                BigButton(Copy.callHelpline(lang), Gold, Color.Black) { dial("1930") }
                TextButton(
                    onClick = {
                        s.caller?.let { Prefs.trust(this@InterruptActivity, it) }
                        SignalBus.emit(Signal.MarkedSafe)
                        finish()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(Copy.knownPerson(lang), color = Color.White, fontSize = 16.sp)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    private fun dial(number: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        }
    }
}

@Composable
private fun BigButton(label: String, background: Color, foreground: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = background, contentColor = foreground),
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
