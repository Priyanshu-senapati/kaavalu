package com.dasen.kaavalu.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dasen.kaavalu.KaavaluApp
import com.dasen.kaavalu.Prefs
import com.dasen.kaavalu.R

/**
 * The places in the app. Four are destinations in the bar. The demo console is a room off
 * Setup: it is for the family member rehearsing the warning, not for the person it protects,
 * so it does not get a door on every screen.
 */
enum class Screen(val label: String, val icon: Int, val inBar: Boolean = true) {
    HOME("Home", R.drawable.ic_nav_home),
    SCAN("Check", R.drawable.ic_nav_scan),
    ASK("Ask", R.drawable.ic_nav_ask),
    SETUP("Setup", R.drawable.ic_nav_setup),
    DEMO("Demo", R.drawable.ic_nav_demo, inBar = false),
    RECOVER("Help", R.drawable.ic_phone, inBar = false),
    HISTORY("Recent calls", R.drawable.ic_nav_home, inBar = false),
}

private val Tabs = Screen.entries.filter { it.inBar }

/** Which tab is lit for [screen]. The demo console lives under Setup, recovery under Home. */
private fun tabOf(screen: Screen) = when (screen) {
    Screen.DEMO -> Screen.SETUP
    Screen.RECOVER, Screen.HISTORY -> Screen.HOME
    else -> screen
}

@OptIn(ExperimentalLayoutApi::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = KaavaluApp.engineOf(this)

        // Protection should already be running when the home screen opens.
        if (Prefs.onboardingDone(this)) KaavaluApp.startGuarding(this)

        setContent {
            KaavaluTheme {
                var onboarded by remember { mutableStateOf(Prefs.onboardingDone(this)) }
                // Saveable: rotating the phone used to drop the user back on Home, which on a
                // half-finished scan reads as the app having crashed.
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

                if (!onboarded) {
                    // First run is a ladder, not a tab bar: the nav would only offer ways to
                    // leave before protection is actually on.
                    Box(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
                        Onboarding(firstRun = true) {
                            Prefs.setOnboardingDone(this@MainActivity, true)
                            KaavaluApp.startGuarding(this@MainActivity)
                            onboarded = true
                            screen = Screen.HOME
                        }
                    }
                    return@KaavaluTheme
                }

                BackHandler(enabled = screen != Screen.HOME) {
                    screen = if (screen == Screen.DEMO) Screen.SETUP else Screen.HOME
                }

                // The app draws edge to edge, so the keyboard does not resize it: without this
                // the field being typed into sat behind the keyboard on a real phone. While
                // typing, the tab bar steps aside so the page ends at the keyboard's edge.
                val typing = WindowInsets.isImeVisible
                Column(Modifier.fillMaxSize().background(Paper).imePadding()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .statusBarsPadding(),
                    ) {
                        // transitionSpec is not a composable scope, so the durations are
                        // resolved out here where the reduced-motion setting can be read.
                        val fadeMs = motion(Motion.STANDARD)
                        val riseMs = motion(Motion.EMPHASIZED)
                        val outMs = motion(Motion.QUICK)
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = {
                                // Directional, because the tabs are laid out left to right and
                                // the motion should agree with the bar. A twelfth of the width:
                                // enough to say which way you moved, not so much that switching
                                // tabs becomes a journey.
                                val forward = targetState.ordinal > initialState.ordinal
                                val dir = if (forward) 1 else -1
                                (
                                    fadeIn(tween(fadeMs)) +
                                        slideInHorizontally(tween(riseMs, easing = Motion.enter)) { dir * it / 12 }
                                    ).togetherWith(
                                    fadeOut(tween(outMs)) +
                                        slideOutHorizontally(tween(outMs, easing = Motion.exit)) { -dir * it / 24 },
                                )
                            },
                            label = "screen",
                        ) { current ->
                            when (current) {
                                Screen.HOME -> Home(engine) { screen = it }
                                Screen.SCAN -> ScanScreen { screen = Screen.HOME }
                                Screen.ASK -> AskScreen { screen = Screen.HOME }
                                Screen.SETUP -> Onboarding(
                                    firstRun = false,
                                    onOpenDemo = { screen = Screen.DEMO },
                                ) { screen = Screen.HOME }
                                Screen.DEMO -> DemoConsole(engine) { screen = Screen.SETUP }
                                Screen.RECOVER -> RecoveryScreen(
                                    onBack = { screen = Screen.HOME },
                                    onHistory = { screen = Screen.HISTORY },
                                )
                                Screen.HISTORY -> HistoryScreen { screen = Screen.HOME }
                            }
                        }
                    }
                    if (!typing) KaavaluNavBar(tabOf(screen)) { screen = it }
                }
            }
        }
    }
}

/**
 * Four destinations, one signal. The lit tab carries a bar along its top edge that slides
 * to the next tab when you move, so the eye follows the change rather than hunting for it.
 * Labels are always shown, and each target is the full width of its column.
 */
@Composable
private fun KaavaluNavBar(current: Screen, onSelect: (Screen) -> Unit) {
    val index = Tabs.indexOf(current).coerceAtLeast(0)
    val at by animateFloatAsState(index.toFloat(), kSpring(), label = "navSignal")
    Column(
        Modifier
            .fillMaxWidth()
            .background(Sheet)
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(Line, size = Size(size.width, 1.dp.toPx()))
                    val slot = size.width / Tabs.size
                    val w = slot * 0.44f
                    drawRect(
                        Guard,
                        topLeft = Offset(slot * at + (slot - w) / 2f, 0f),
                        size = Size(w, Stroke.signal.toPx()),
                    )
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tabs.forEach { tab ->
                NavItem(tab, selected = tab == current, modifier = Modifier.weight(1f)) { onSelect(tab) }
            }
        }
    }
}

@Composable
private fun NavItem(tab: Screen, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source, depth = 0.92f)
    val ink by animateColorAsState(
        if (selected) Guard else Muted,
        tween(motion(Motion.STANDARD)),
        label = "navInk",
    )
    Column(
        modifier
            .heightIn(min = 68.dp)
            .semantics { this.selected = selected }
            .clickable(interactionSource = source, indication = null, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(Space.sm))
        Icon(
            painterResource(tab.icon),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(26.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
        Spacer(Modifier.height(Space.xxs))
        Text(
            tab.label,
            style = KType.utility.copy(fontWeight = if (selected) FontWeight(640) else FontWeight(500)),
            color = if (selected) Ink else Muted,
            maxLines = 1,
        )
        Spacer(Modifier.height(Space.sm))
    }
}
