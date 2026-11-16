package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AlarmItem
import com.example.ui.theme.ClockVisualTheme
import com.example.util.TimeAndAlertUtils
import java.time.LocalTime
import java.time.ZonedDateTime

/** Minutes since midnight in [home]'s zone for an alarm's next firing today, wherever the alarm's own zone is. */
internal fun alarmMinuteOfDay(alarm: AlarmItem, home: ZonedDateTime): Int {
    val zone = TimeAndAlertUtils.safeZoneId(alarm.timezoneId)
    val date = home.withZoneSameInstant(zone).toLocalDate()
    val inHome = ZonedDateTime.of(date, LocalTime.of(alarm.hour.coerceIn(0, 23), alarm.minute.coerceIn(0, 59)), zone)
        .withZoneSameInstant(home.zone)
    return inHome.hour * 60 + inHome.minute
}

/**
 * A thin 24-hour bar along the bottom edge, identical in every layout style: the day so far, 6/12/18 ticks,
 * enabled alarms as dots, any running focus session as a bright segment, and a "now" line.
 */
@Composable
fun RhythmStrip(
    now: ZonedDateTime,
    alarms: List<AlarmItem>,
    focus: FocusSession?,
    theme: ClockVisualTheme,
    modifier: Modifier = Modifier
) {
    val nowMin = now.hour * 60 + now.minute
    val alarmMinutes = alarms.filter { it.isEnabled }.map { alarmMinuteOfDay(it, now) }
    // Focus segment in home-zone minutes of today, clipped to the day.
    val focusRange: Pair<Int, Int>? = focus?.let {
        val start = java.time.Instant.ofEpochMilli(it.startMillis).atZone(now.zone)
        val end = java.time.Instant.ofEpochMilli(it.endMillis).atZone(now.zone)
        if (start.toLocalDate() != now.toLocalDate() && end.toLocalDate() != now.toLocalDate()) null
        else {
            val s = if (start.toLocalDate() == now.toLocalDate()) start.hour * 60 + start.minute else 0
            val e = if (end.toLocalDate() == now.toLocalDate()) end.hour * 60 + end.minute else 1440
            s to e
        }
    }
    Canvas(modifier.fillMaxWidth().height(10.dp).testTag("rhythm_strip")) {
        val w = size.width
        val trackH = size.height * 0.4f
        val y = (size.height - trackH) / 2f
        fun x(min: Int) = min / 1440f * w
        drawRoundRect(theme.textPrimary.copy(alpha = 0.12f), Offset(0f, y), Size(w, trackH), CornerRadius(trackH / 2))
        drawRoundRect(theme.accentColor.copy(alpha = 0.35f), Offset(0f, y), Size(x(nowMin), trackH), CornerRadius(trackH / 2))
        focusRange?.let { (s, e) ->
            drawRoundRect(theme.accentColor, Offset(x(s), y), Size((x(e) - x(s)).coerceAtLeast(2f), trackH), CornerRadius(trackH / 2))
        }
        for (h in listOf(6, 12, 18)) {
            drawLine(theme.textPrimary.copy(alpha = 0.35f), Offset(x(h * 60), y - 1f), Offset(x(h * 60), y + trackH + 1f), strokeWidth = 1.5f)
        }
        alarmMinutes.forEach { m -> drawCircle(theme.numberColor, radius = size.height * 0.28f, center = Offset(x(m), size.height / 2f)) }
        drawLine(theme.textPrimary, Offset(x(nowMin), 0f), Offset(x(nowMin), size.height), strokeWidth = 3f)
    }
}
