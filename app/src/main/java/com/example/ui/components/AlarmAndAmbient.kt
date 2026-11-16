package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AmbientKind
import com.example.ui.theme.ClockVisualTheme

/** Stable names stored in [com.example.data.ClockSettings.alarmStyle]. */
object AlarmStyle {
    const val GENTLE = "GENTLE"
    const val SINGLE = "SINGLE"

    val options: List<Triple<String, String, String>> = listOf(
        Triple(GENTLE, "Gentle", "Fades in and repeats until you dismiss or snooze"),
        Triple(SINGLE, "Single chime", "One chime, then silence")
    )
}

/** How a gentle alarm gets louder: quiet at first, full volume after [RAMP_MS], repeating until answered. */
object AlarmRamp {
    const val START_VOLUME = 0.12f
    const val RAMP_MS = 30_000L
    const val REPEAT_MS = 4_000L
    const val GIVE_UP_MS = 3 * 60_000L
    const val SNOOZE_MINUTES = 5

    fun volumeAt(elapsedMillis: Long): Float {
        val progress = (elapsedMillis.toFloat() / RAMP_MS).coerceIn(0f, 1f)
        return START_VOLUME + (1f - START_VOLUME) * progress
    }
}

/** An alarm that is sounding right now. [snoozes] counts how many times it has been put off. */
data class RingingAlarm(val label: String, val snoozes: Int = 0)

/** What is playing in the background: which sound, how loud, and when it fades out (null = keeps playing). */
data class AmbientState(val kind: AmbientKind, val volume: Float, val endsAtMillis: Long?)

object AmbientVolume {
    val options: List<Pair<String, Float>> = listOf("Low" to 0.25f, "Medium" to 0.5f, "High" to 0.8f)
}

/** Sleep timer rules: sound plays at full chosen volume, then fades to silence over the last [FADE_MS]. */
object SleepTimer {
    const val FADE_MS = 20_000L
    val minuteOptions: List<Int?> = listOf(15, 30, 60, 90, null)

    fun fadeFactor(remainingMillis: Long): Float = (remainingMillis.toFloat() / FADE_MS).coerceIn(0f, 1f)

    fun label(minutes: Int?): String = if (minutes == null) "No timer" else "$minutes min"

    fun remainingText(remainingMillis: Long): String {
        val minutes = (remainingMillis.coerceAtLeast(0) + 59_999L) / 60_000L
        return "${minutes}m"
    }
}

/** Lets the shared header open the ambient sound dialog, like [LocalFocusLauncher] does for focus sessions. */
val LocalAmbientLauncher = staticCompositionLocalOf<() -> Unit> { {} }

/** Shown while an alarm rings. Snooze puts it off for a few minutes; Dismiss stops it for good. */
@Composable
fun AlarmRingingBanner(
    alarm: RingingAlarm,
    theme: ClockVisualTheme,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp), color = theme.cardBackground,
        border = BorderStroke(2.dp, theme.accentColor), modifier = modifier.testTag("alarm_ringing_banner")
    ) {
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Alarm", fontSize = 13.sp, letterSpacing = 2.sp, color = theme.textSecondary)
            Text(
                alarm.label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = onSnooze, shape = CircleShape, color = theme.textPrimary.copy(alpha = 0.12f),
                    modifier = Modifier.testTag("alarm_snooze")
                ) {
                    Text(
                        "Snooze ${AlarmRamp.SNOOZE_MINUTES} min", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = theme.textPrimary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                Surface(
                    onClick = onDismiss, shape = CircleShape, color = theme.accentColor,
                    modifier = Modifier.testTag("alarm_dismiss")
                ) {
                    Text(
                        "Dismiss", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = theme.onAccentColor, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

/** Pick a sound, how long it plays and how loud. "Stop" appears while something is already playing. */
@Composable
fun AmbientDialog(
    theme: ClockVisualTheme,
    current: AmbientState?,
    nowMillis: Long,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    onStart: (kind: AmbientKind, volume: Float, minutes: Int?) -> Unit
) {
    var kind by remember { mutableStateOf(current?.kind ?: AmbientKind.RAIN) }
    var volume by remember { mutableFloatStateOf(current?.volume ?: 0.5f) }
    var minutes by remember { mutableStateOf<Int?>(30) }

    @Composable
    fun Chip(text: String, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) = Surface(
        onClick = onClick, shape = CircleShape,
        color = if (selected) theme.accentColor else theme.textPrimary.copy(alpha = 0.10f),
        modifier = modifier.testTag(tag)
    ) {
        Text(
            text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center,
            color = if (selected) theme.onAccentColor else theme.textPrimary,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp).fillMaxWidth()
        )
    }

    Surface(
        shape = RoundedCornerShape(22.dp), color = theme.cardBackground,
        border = BorderStroke(1.dp, theme.dividerColor),
        // Swallow taps so tapping inside the card doesn't dismiss the scrim behind it.
        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(0.92f)
            .composed { clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { } }
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Background sound", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
            if (current != null) {
                val left = current.endsAtMillis?.let { " · ${SleepTimer.remainingText(it - nowMillis)} left" } ?: ""
                Text("Playing: ${current.kind.label}$left", fontSize = 12.sp, color = theme.accentColor)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmbientKind.entries.forEach { k ->
                    Chip(k.label, k == kind, "ambient_kind_${k.id.lowercase()}", Modifier.weight(1f)) { kind = k }
                }
            }
            Text(kind.blurb, fontSize = 11.sp, color = theme.textSecondary)
            Text("SLEEP TIMER", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = theme.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SleepTimer.minuteOptions.forEach { m ->
                    Chip(
                        if (m == null) "None" else "$m", m == minutes, "ambient_timer_${m ?: "none"}", Modifier.weight(1f)
                    ) { minutes = m }
                }
            }
            Text(
                if (minutes == null) "Plays until you stop it." else "Fades out and stops after $minutes minutes.",
                fontSize = 11.sp, color = theme.textSecondary
            )
            Text("VOLUME", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = theme.textSecondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmbientVolume.options.forEach { (name, level) ->
                    Chip(name, level == volume, "ambient_volume_${name.lowercase()}", Modifier.weight(1f)) { volume = level }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (current != null) {
                    TextButton(onClick = onStop, modifier = Modifier.testTag("ambient_stop")) { Text("Stop sound", color = theme.textSecondary) }
                }
                TextButton(onClick = onDismiss) { Text("Close", color = theme.textSecondary) }
                TextButton(onClick = { onStart(kind, volume, minutes) }, modifier = Modifier.testTag("ambient_start")) {
                    Text(if (current != null) "Change" else "Play", color = theme.accentColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
