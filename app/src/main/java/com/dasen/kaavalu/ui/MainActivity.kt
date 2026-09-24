package com.dasen.kaavalu.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.KaavaluApp
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R

/**
 * The five places in the app. Before this existed every screen was a dead end with one
 * "Back" button at the bottom of a long scroll, which is the fastest way to make someone
 * decide an app is not for them.
 */
enum class Screen(val label: String, val icon: Int) {
    HOME("Home", R.drawable.ic_nav_home),
    SCAN("Check notice", R.drawable.ic_nav_scan),
    ASK("Ask", R.drawable.ic_nav_ask),
    SETUP("Setup", R.drawable.ic_nav_setup),
    DEMO("Demo", R.drawable.ic_nav_demo),
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = KaavaluApp.engineOf(this)

        // Protection should already be running when the home screen opens.
        if (Prefs.onboardingDone(this)) KaavaluApp.startGuarding(this)

        setContent {
            KaavaluTheme {
                var onboarded by remember { mutableStateOf(Prefs.onboardingDone(this)) }
                var screen by remember { mutableStateOf(Screen.HOME) }

                if (!onboarded) {
                    // First run is a ladder, not a tab bar: the nav would only offer ways to
                    // leave before protection is actually on.
                    Onboarding(firstRun = true) {
                        Prefs.setOnboardingDone(this, true)
                        KaavaluApp.startGuarding(this)
                        onboarded = true
                        screen = Screen.HOME
                    }
                    return@KaavaluTheme
                }

                BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

                Scaffold(
                    containerColor = Paper,
                    bottomBar = { KaavaluNavBar(screen) { screen = it } },
                ) { insets ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Paper)
                            .padding(
                                top = insets.calculateTopPadding(),
                                bottom = insets.calculateBottomPadding(),
                            ),
                    ) {
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                            },
                            label = "screen",
                        ) { current ->
                            when (current) {
                                Screen.HOME -> Home(engine) { screen = it }
                                Screen.SCAN -> ScanScreen { screen = Screen.HOME }
                                Screen.ASK -> AskScreen { screen = Screen.HOME }
                                Screen.SETUP -> Onboarding(firstRun = false) { screen = Screen.HOME }
                                Screen.DEMO -> DemoConsole(engine) { screen = Screen.HOME }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KaavaluNavBar(current: Screen, onSelect: (Screen) -> Unit) {
    NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
        Screen.entries.forEach { screen ->
            NavigationBarItem(
                selected = current == screen,
                onClick = { onSelect(screen) },
                icon = {
                    Icon(
                        painterResource(screen.icon),
                        contentDescription = screen.label,
                        modifier = Modifier.padding(2.dp),
                    )
                },
                label = { Text(screen.label, maxLines = 1) },
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
}
