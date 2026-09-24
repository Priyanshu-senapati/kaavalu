package com.dasen.kaavalu.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dasen.kaavalu.R

/**
 * The wordmark. English first, because that is what the family reading the screen for the
 * first time can actually read; the Kannada name sits under it as the product's own name
 * rather than as a word the user has to decode before they trust the app.
 */
@Composable
fun Wordmark(modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier) {
        Text(
            "Kaavalu",
            style = if (compact) MaterialTheme.typography.headlineSmall
            else MaterialTheme.typography.displaySmall,
            color = Guard,
        )
        Text(
            "ಕಾವಲು  ·  the watchman",
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
        )
    }
}

/** A screen header with a back affordance. Every screen has one, so nothing is a dead end. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Line, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_back),
                    contentDescription = "Back",
                    tint = Ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
    }
}

/**
 * The plain white panel everything sits on. A Card with an outline rather than a shadow:
 * elevation disappears in the sunlight these phones actually live in.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * A full-width action with a title, a sentence of consequence and an icon. The stacked
 * identical green buttons this replaced told the user nothing about which one to press.
 */
@Composable
fun ActionTile(
    icon: Int,
    title: String,
    detail: String,
    tint: Color = Guard,
    background: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Icon(
            painterResource(R.drawable.ic_chevron),
            null,
            tint = Muted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The one button on a screen that the frightened user is meant to press. */
@Composable
fun BigAction(
    label: String,
    container: Color = Guard,
    content: Color = Color.White,
    icon: Int? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The shield on the home screen. It breathes while protection is live, which is the whole
 * point of an app that is otherwise invisible: something has to say "I am still here".
 */
@Composable
fun BreathingShield(armed: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shield")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (armed) 1.09f else 1f,
        animationSpec = infiniteRepeatable(
            tween(1900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val halo by transition.animateFloat(
        initialValue = if (armed) 0.30f else 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "halo",
    )
    val tint by animateColorAsState(if (armed) Guard else Alarm, label = "tint")

    Box(modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(92.dp)
                .scale(pulse)
                .alpha(halo)
                .clip(CircleShape)
                .background(Color.White),
        )
        Box(
            Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_shield_k),
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(42.dp)
                    .scale(pulse),
            )
        }
    }
}

/** A small key/number pair. Three of them make the "this is working" row on the home screen. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Guard)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
        )
    }
}

/** One reason, as a row the user can read in a glance: why it matters, and what it scored. */
@Composable
fun ReasonRow(points: Int?, text: String, colour: Color = Alarm) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(colour),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (points != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                "+$points",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = colour,
            )
        }
    }
}

/** A thick, readable meter. LinearProgressIndicator's default 4dp is invisible at arm's length. */
@Composable
fun Meter(fraction: Float, colour: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFFE8E2D8)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(colour),
        )
    }
}
