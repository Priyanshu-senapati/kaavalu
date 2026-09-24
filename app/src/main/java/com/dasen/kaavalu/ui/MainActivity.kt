package com.dasen.kaavalu.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dasen.kaavalu.KaavaluApp
import com.dasen.kaavalu.Prefs

enum class Screen { HOME, ONBOARDING, SCAN, ASK, DEMO }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = KaavaluApp.engineOf(this)

        // Protection should already be running when the home screen opens.
        if (Prefs.onboardingDone(this)) KaavaluApp.startGuarding(this)

        setContent {
            KaavaluTheme {
                var screen by remember {
                    mutableStateOf(if (Prefs.onboardingDone(this)) Screen.HOME else Screen.ONBOARDING)
                }
                when (screen) {
                    Screen.ONBOARDING -> Onboarding(
                        onDone = {
                            Prefs.setOnboardingDone(this, true)
                            KaavaluApp.startGuarding(this)
                            screen = Screen.HOME
                        },
                    )
                    Screen.HOME -> Home(engine) { screen = it }
                    Screen.SCAN -> ScanScreen { screen = Screen.HOME }
                    Screen.ASK -> AskScreen { screen = Screen.HOME }
                    Screen.DEMO -> DemoConsole(engine) { screen = Screen.HOME }
                }
            }
        }
    }
}
