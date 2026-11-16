package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.composed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ClockVisualTheme
import kotlinx.coroutines.delay

/** One timed focus block. Kept in memory only: it is a "right now" tool, not a log. */
data class FocusSession(val label: String, val startMillis: Long, val durationMillis: Long) {
    val endMillis: Long get() = startMillis + durationMillis
}

/**
 * Lets the shared header (used by every layout style) open the focus dialog without each style needing its own
 * plumbing. MainActivity provides the real action; the default does nothing, e.g. in previews and tests.
 */
val LocalFocusLauncher = staticCompositionLocalOf<() -> Unit> { {} }

private val FocusDurations = listOf(15, 25, 45, 60, 90)

@Composable
fun FocusSessionDialog(theme: ClockVisualTheme, onDismiss: () -> Unit, onStart: (minutes: Int, label: String) -> Unit) {
    var minutes by remember { mutableIntStateOf(25) }
    var label by remember { mutableStateOf("") }
    Surface(
        shape = RoundedCornerShape(22.dp), color = theme.cardBackground,
        border = BorderStroke(1.dp, theme.dividerColor),
        // Swallow taps so tapping inside the card doesn't dismiss the scrim behind it.
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(0.92f).clickableNoRipple { }
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Start a focus session", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
            OutlinedTextField(
                value = label, onValueChange = { label = it.take(40) }, singleLine = true,
                label = { Text("What are you focusing on?") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = theme.textPrimary, unfocusedTextColor = theme.textPrimary,
                    focusedBorderColor = theme.accentColor, unfocusedBorderColor = theme.dividerColor,
                    focusedLabelColor = theme.accentColor, unfocusedLabelColor = theme.textSecondary,
                    cursorColor = theme.accentColor
                ),
                modifier = Modifier.fillMaxWidth().testTag("focus_label_field")
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusDurations.forEach { d ->
                    val selected = d == minutes
                    Surface(
                        onClick = { minutes = d }, shape = CircleShape,
                        color = if (selected) theme.accentColor else theme.textPrimary.copy(alpha = 0.10f),
                        modifier = Modifier.weight(1f).testTag("focus_minutes_$d")
                    ) {
                        Text(
                            "$d", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            color = if (selected) theme.onAccentColor else theme.textPrimary,
                            modifier = Modifier.padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            Text("minutes", fontSize = 11.sp, color = theme.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textSecondary) }
                TextButton(onClick = { onStart(minutes, label) }, modifier = Modifier.testTag("focus_start_button")) {
                    Text("Start", color = theme.accentColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Slim running-session bar shown over any layout style. */
@Composable
fun FocusSessionPill(
    session: FocusSession,
    nowMillis: Long,
    theme: ClockVisualTheme,
    onDone: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remaining = ((session.endMillis - nowMillis) / 1000).coerceAtLeast(0)
    val total = (session.durationMillis / 1000).coerceAtLeast(1)
    val mm = remaining / 60
    val ss = remaining % 60
    Surface(
        shape = RoundedCornerShape(50), color = theme.bgStart.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, theme.accentColor.copy(alpha = 0.6f)), modifier = modifier.testTag("focus_pill")
    ) {
        // IntrinsicSize.Max makes the pill only as wide as its row, so it stays compact instead of spanning the screen.
        Column(Modifier.width(IntrinsicSize.Max)) {
            Row(Modifier.padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(theme.accentColor))
                Text(
                    session.label, fontSize = 13.sp, color = theme.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp).padding(start = 8.dp)
                )
                Text(
                    "%d:%02d".format(mm, ss), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                // Plain text buttons (not Material TextButton) so the pill stays slim; each still has a 40dp-tall tap area.
                Text(
                    "Done", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = theme.accentColor,
                    modifier = Modifier.clickable(onClick = onDone).testTag("focus_done").padding(horizontal = 8.dp, vertical = 10.dp)
                )
                Text(
                    "Stop", fontSize = 13.sp, color = theme.textSecondary,
                    modifier = Modifier.clickable(onClick = onStop).testTag("focus_stop").padding(horizontal = 8.dp, vertical = 10.dp)
                )
            }
            // Thin progress line along the bottom of the pill.
            Box(Modifier.fillMaxWidth().height(3.dp).background(theme.textPrimary.copy(alpha = 0.10f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(((total - remaining).toFloat() / total).coerceIn(0f, 1f)).background(theme.accentColor))
            }
        }
    }
}

/** Shown when a session ends by itself; fades away after 30 seconds if ignored. */
@Composable
fun FocusCompleteBanner(label: String, theme: ClockVisualTheme, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(label) {
        delay(30_000)
        onDismiss()
    }
    Surface(
        shape = RoundedCornerShape(20.dp), color = theme.cardBackground,
        border = BorderStroke(1.5.dp, theme.accentColor), modifier = modifier.testTag("focus_complete_banner")
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Focus complete", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.accentColor)
            Text(label, fontSize = 14.sp, color = theme.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onDismiss) { Text("Nice", color = theme.textPrimary) }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}
