package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClockSettings
import com.example.data.Countdown
import com.example.data.PlannerCodec
import com.example.data.Routine
import com.example.ui.theme.ClockVisualTheme
import com.example.util.Holidays
import java.time.ZonedDateTime
import java.util.Locale

/** What the dock has to say right now. Pure so it can be tested without a screen. */
data class DockContent(
    val dayInfo: String?,
    val countdowns: List<Pair<Countdown, String>>,
    val dueRoutines: List<Routine>
) {
    val isEmpty: Boolean get() = dayInfo == null && countdowns.isEmpty() && dueRoutines.isEmpty()
}

/**
 * [plainWeek] false (small phones) hides the week chip on an ordinary day, so the dock takes no height unless there
 * is something to say: a holiday, a countdown or a routine item that is due.
 */
fun buildDockContent(settings: ClockSettings, now: ZonedDateTime, deviceCountry: String, plainWeek: Boolean = true): DockContent {
    val today = now.toLocalDate()
    val holiday = Holidays.nameOn(today, Holidays.resolveRegion(settings.holidayRegion, deviceCountry))
    val dayInfo = if (settings.showDayInfo && (plainWeek || holiday != null)) {
        "Wk ${Holidays.isoWeek(today)}" + (holiday?.let { " · $it" } ?: "")
    } else null
    val countdowns = PlannerCodec.countdownsFromJson(settings.countdownsJson)
        .filter { it.isUpcoming(now) }
        .sortedBy { it.remainingMillis(now) }
        .take(2)
        .map { it to it.describe(now) }
    val routines = PlannerCodec.routinesFromJson(settings.routinesJson)
        .filter { it.isDue(now) }
        .sortedBy { it.minuteOfDay }
        .take(3)
    return DockContent(dayInfo, countdowns, routines)
}

/**
 * Small chips shared by every layout: week number and holiday, countdowns (a shrinking bar for the last three hours
 * of a timed one) and routine items that are due, which disappear when tapped. Only the chips take touches.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoDock(
    settings: ClockSettings,
    now: ZonedDateTime,
    theme: ClockVisualTheme,
    onRoutineDone: (id: Long, epochDay: Long) -> Unit,
    modifier: Modifier = Modifier,
    plainWeek: Boolean = true,
    ambientText: String? = null,
    onAmbientClick: () -> Unit = {},
    weatherNotices: List<com.example.data.WeatherNotice> = emptyList(),
    /** Small screens have room for only a chip or two; the most important ones are kept (see [DockPriority]). */
    maxChips: Int = 6
) {
    val country = remember { Locale.getDefault().country }
    val content = buildDockContent(settings, now, country, plainWeek)
    if (content.isEmpty && ambientText == null && weatherNotices.isEmpty()) return

    // Each chip carries a priority; the top [maxChips] are kept, then shown in their natural order.
    val items = buildList<DockItem> {
        content.dayInfo?.let { add(DockItem(DockPriority.WEEK) { DockChip(it, theme, tag = "dock_day_info") }) }
        weatherNotices.forEachIndexed { i, n ->
            // Serious ones get the accent outline; a plain "rain soon" heads-up stays quiet.
            add(DockItem(if (n.level >= 2) DockPriority.WEATHER_SERIOUS else DockPriority.WEATHER_MINOR) {
                DockChip(
                    text = (if (n.level >= 2) "⚠️ " else "") + "${n.city}: ${n.text}",
                    theme = theme, highlight = n.level >= 2, tag = "dock_weather_$i"
                )
            })
        }
        ambientText?.let { add(DockItem(DockPriority.AMBIENT) { DockChip(it, theme, tag = "dock_ambient", onClick = onAmbientClick) }) }
        content.countdowns.forEach { (event, text) ->
            add(DockItem(DockPriority.COUNTDOWN) {
                DockChip(
                    text = "${event.label} · $text", theme = theme, bar = event.barFraction(now), tag = "dock_countdown_${event.id}"
                )
            })
        }
        content.dueRoutines.forEach { r ->
            add(DockItem(DockPriority.ROUTINE) {
                DockChip(
                    text = "○ ${r.label}", theme = theme, highlight = true, tag = "dock_routine_${r.id}",
                    onClick = { onRoutineDone(r.id, now.toLocalDate().toEpochDay()) }
                )
            })
        }
    }
    val shown = selectDockItems(items.map { it.priority }, maxChips).map { items[it] }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        FlowRow(
            modifier = Modifier.widthIn(max = 560.dp).testTag("info_dock"),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            shown.forEach { it.content() }
        }
    }
}

/** Lower number = more important. */
object DockPriority {
    const val WEATHER_SERIOUS = 0
    const val ROUTINE = 1
    const val WEATHER_MINOR = 2
    const val AMBIENT = 3
    const val COUNTDOWN = 4
    const val WEEK = 5
}

private class DockItem(val priority: Int, val content: @Composable () -> Unit)

/**
 * Which chips to keep: the [max] most important (ties keep their original order), returned as indexes in the
 * original order so the row still reads naturally.
 */
fun selectDockItems(priorities: List<Int>, max: Int): List<Int> =
    priorities.indices.sortedWith(compareBy({ priorities[it] }, { it })).take(max.coerceAtLeast(0)).sorted()

@Composable
private fun DockChip(
    text: String,
    theme: ClockVisualTheme,
    tag: String,
    bar: Float? = null,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(50)
    val color = theme.bgStart.copy(alpha = 0.88f)
    val border = BorderStroke(1.dp, if (highlight) theme.accentColor else theme.dividerColor)
    val body: @Composable () -> Unit = {
        Column(Modifier.width(IntrinsicSize.Max)) {
            Text(
                text, fontSize = 13.sp, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = if (highlight) theme.accentColor else theme.textPrimary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = if (onClick != null) 9.dp else 6.dp)
            )
            if (bar != null) {
                // Shrinks toward the right edge as the event approaches.
                Box(Modifier.fillMaxWidth().height(3.dp).background(theme.textPrimary.copy(alpha = 0.10f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(bar.coerceIn(0f, 1f)).background(theme.accentColor))
                }
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, border = border, modifier = Modifier.testTag(tag)) { body() }
    } else {
        Surface(shape = shape, color = color, border = border, modifier = Modifier.testTag(tag)) { body() }
    }
}
