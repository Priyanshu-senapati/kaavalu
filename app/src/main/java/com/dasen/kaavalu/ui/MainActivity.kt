package com.dasen.kaavalu.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.dasen.kaavalu.KaavaluApp
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R
import com.dasen.kaavalu.core.RiskEngine

/**
 * Top-level destinations. Four, each with an icon AND a label: an icon-only bar is
 * unusable for the person this app is built for.
 */
enum class Tab(val label: String, val icon: Int, val description: String) {
    HOME("Protection", R.drawable.ic_nav_home, "Protection status"),
    SCAN("Check notice", R.drawable.ic_nav_scan, "Check whether a notice is real"),
    ASK("Ask", R.drawable.ic_nav_ask, "Ask Kaavalu about a call"),
    DEMO("Demo", R.drawable.ic_nav_demo, "Demo console"),
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = KaavaluApp.engineOf(this)

        if (Prefs.onboardingDone(this)) KaavaluApp.startGuarding(this)

        setContent {
            KaavaluTheme {
                var onboarded by remember { mutableStateOf(Prefs.onboardingDone(this)) }

                if (!onboarded) {
                    Onboarding(
                        onDone = {
                            Prefs.setOnboardingDone(this, true)
                            KaavaluApp.startGuarding(this)
                            onboarded = true
                        },
                    )
                } else {
                    MainScaffold(engine) { onboarded = false }
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(engine: RiskEngine, onRerunSetup: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.HOME) }

    Scaffold(
        containerColor = Paper,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp2()) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            Icon(
                                painter = painterResource(entry.icon),
                                contentDescription = entry.description,
                            )
                        },
                        label = { Text(entry.label) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Guard,
                            indicatorColor = Guard,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (tab) {
            Tab.HOME -> Home(engine, content, onRerunSetup) { tab = it }
            Tab.SCAN -> ScanScreen(content)
            Tab.ASK -> AskScreen(content)
            Tab.DEMO -> DemoConsole(engine, content)
        }
    }
}

/** Tiny helper so the elevation value reads in dp without another import at the call site. */
private fun Int.dp2() = androidx.compose.ui.unit.Dp(this.toFloat())
