package com.example.util

import com.example.data.FriendClock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

object TimeAndAlertUtils {

    /**
     * Computes exact relative offset including half-hour (:30) and 45-minute (:45) zones.
     * Example outputs: "+5h 30m ahead", "-3h 30m behind", "+45m ahead", "Same time".
     */
    fun formatRelativeOffset(localTime: ZonedDateTime, targetZoneId: String): String {
        val targetZone = safeZoneId(targetZoneId)
        val targetTime = localTime.withZoneSameInstant(targetZone)
        val diffSeconds = targetTime.offset.totalSeconds - localTime.offset.totalSeconds
        if (diffSeconds == 0) return "Same time"

        val sign = if (diffSeconds > 0) "+" else "-"
        val direction = if (diffSeconds > 0) "ahead" else "behind"
        val totalMinutes = abs(diffSeconds) / 60
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60

        return when {
            hours > 0 && mins > 0 -> "${sign}${hours}h ${mins}m $direction"
            hours > 0 -> "${sign}${hours}h $direction"
            else -> "${sign}${mins}m $direction"
        }
    }

    /**
     * Compact relative offset label for compact badges and Home Screen AppWidget rows.
     * Example outputs: "+5:30", "-4h", "+45m", "±0h".
     */
    fun formatCompactRelativeOffset(localTime: ZonedDateTime, targetZoneId: String): String {
        val targetZone = safeZoneId(targetZoneId)
        val targetTime = localTime.withZoneSameInstant(targetZone)
        val diffSeconds = targetTime.offset.totalSeconds - localTime.offset.totalSeconds
        if (diffSeconds == 0) return "±0h"

        val sign = if (diffSeconds > 0) "+" else "-"
        val totalMinutes = abs(diffSeconds) / 60
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60

        return when {
            hours > 0 && mins > 0 -> String.format(Locale.US, "%s%d:%02d", sign, hours, mins)
            hours > 0 -> "${sign}${hours}h"
            else -> "${sign}${mins}m"
        }
    }

    /**
     * Computes accurate calendar-day difference across month and year boundaries (Dec 31 -> Jan 1).
     */
    fun computeDayDifferenceBadge(localTime: ZonedDateTime, targetTime: ZonedDateTime): String {
        val dayDiff = ChronoUnit.DAYS.between(localTime.toLocalDate(), targetTime.toLocalDate())
        return when {
            dayDiff > 0L -> "+${dayDiff}d"
            dayDiff < 0L -> "${dayDiff}d"
            else -> "Same Day"
        }
    }

    /**
     * Validates user-entered hour (0..23) and minute (0..59) strings safely.
     */
    fun validateAlarmTime(hourInput: String, minuteInput: String): Pair<Int, Int>? {
        val h = hourInput.trim().toIntOrNull() ?: return null
        val m = minuteInput.trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h to m
    }

    /**
     * Computes the next chime trigger epoch millis.
     * Aligns to top-of-hour subdivisions ONLY when alignToTopHour is enabled AND 60 is evenly
     * divisible by intervalMinutes (e.g., 15m -> :00/:15/:30/:45, 20m -> :00/:20/:40).
     * For 16m, 17m, 18m, 19m (or unaligned mode), schedules exact elapsed intervals so alerts
     * never prematurely reset at :00.
     */
    fun computeNextChimeEpochMillis(
        now: ZonedDateTime,
        intervalMinutes: Int,
        alignToTopHour: Boolean
    ): Long {
        val safeInterval = intervalMinutes.coerceIn(1, 120)
        return if (alignToTopHour && (60 % safeInterval == 0)) {
            val nextSlotMinute = ((now.minute / safeInterval) + 1) * safeInterval
            val nextBoundary = now.truncatedTo(ChronoUnit.HOURS).plusMinutes(nextSlotMinute.toLong())
            nextBoundary.toInstant().toEpochMilli()
        } else {
            now.toInstant().toEpochMilli() + safeInterval * 60_000L
        }
    }

    /**
     * Ceiling remaining seconds so a fresh 15m timer displays 15:00 instead of 14:00.
     */
    fun computeRemainingChimeSeconds(nowMillis: Long, nextChimeEpochMillis: Long): Int {
        val diffMs = nextChimeEpochMillis - nowMillis
        if (diffMs <= 0L) return 0
        return ((diffMs + 999L) / 1000L).toInt()
    }

    fun formatMinutesAndSeconds(remainingTotalSeconds: Int): Pair<Int, String> {
        val safe = remainingTotalSeconds.coerceAtLeast(0)
        val mins = safe / 60
        val secs = safe % 60
        return mins to String.format(Locale.US, "%02d", secs)
    }

    fun safeZoneId(timezoneId: String): ZoneId {
        return try {
            ZoneId.of(timezoneId)
        } catch (_: Exception) {
            ZoneId.systemDefault()
        }
    }

    /**
     * Checks if a given ZonedDateTime falls within a scheduled quiet hours window on active days.
     * Handles overnight spans (e.g. 21:30 to 07:30) correctly.
     */
    fun isWithinQuietHours(
        now: ZonedDateTime,
        enabled: Boolean,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        activeDaysOfWeek: String
    ): Boolean {
        if (!enabled) return false

        val dayOfWeekValue = now.dayOfWeek.value // 1 = Monday .. 7 = Sunday
        val isDayActive = when {
            activeDaysOfWeek.isBlank() || activeDaysOfWeek.equals("ALL", ignoreCase = true) -> true
            else -> {
                val allowedDays = activeDaysOfWeek.split(',')
                    .mapNotNull { it.trim().toIntOrNull() }
                dayOfWeekValue in allowedDays
            }
        }
        if (!isDayActive) return false

        val currentMinutesOfDay = now.hour * 60 + now.minute
        val startMinutesOfDay = (startHour.coerceIn(0, 23) * 60) + startMinute.coerceIn(0, 59)
        val endMinutesOfDay = (endHour.coerceIn(0, 23) * 60) + endMinute.coerceIn(0, 59)

        return if (startMinutesOfDay <= endMinutesOfDay) {
            // Same day range, e.g. 13:00 to 15:00
            currentMinutesOfDay in startMinutesOfDay until endMinutesOfDay
        } else {
            // Overnight range, e.g. 21:30 to 07:30
            currentMinutesOfDay >= startMinutesOfDay || currentMinutesOfDay < endMinutesOfDay
        }
    }

    fun formatQuietHoursSummary(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        activeDaysOfWeek: String,
        use24Hour: Boolean
    ): String {
        val startFormatted = formatTimeOfDay(startHour, startMinute, use24Hour)
        val endFormatted = formatTimeOfDay(endHour, endMinute, use24Hour)
        val daysLabel = when {
            activeDaysOfWeek.isBlank() || activeDaysOfWeek.equals("ALL", ignoreCase = true) -> "Daily"
            activeDaysOfWeek == "1,2,3,4,5" -> "Weekdays"
            activeDaysOfWeek == "6,7" -> "Weekends"
            else -> {
                val dayNames = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                val allowed = activeDaysOfWeek.split(',').mapNotNull { it.trim().toIntOrNull() }
                allowed.mapNotNull { dayNames.getOrNull(it) }.joinToString(", ")
            }
        }
        return "$startFormatted – $endFormatted ($daysLabel)"
    }

    private fun formatTimeOfDay(hour: Int, minute: Int, use24Hour: Boolean): String {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        return if (use24Hour) {
            String.format(Locale.US, "%02d:%02d", h, m)
        } else {
            val amPm = if (h >= 12) "PM" else "AM"
            val displayH = when {
                h == 0 -> 12
                h > 12 -> h - 12
                else -> h
            }
            String.format(Locale.US, "%d:%02d %s", displayH, m, amPm)
        }
    }

    fun getSortedFriendClocks(
        clocks: List<FriendClock>,
        sortOrder: String,
        now: ZonedDateTime
    ): List<FriendClock> {
        return when (sortOrder) {
            "NAME" -> clocks.sortedBy { (it.cityLabel.ifBlank { it.friendName }).lowercase(Locale.ROOT) }
            "OFFSET" -> clocks.sortedWith(
                compareBy<FriendClock> { clock ->
                    safeZoneId(clock.timezoneId).rules.getOffset(now.toInstant()).totalSeconds
                }.thenBy { (it.cityLabel.ifBlank { it.friendName }).lowercase(Locale.ROOT) }
            )
            else -> clocks
        }
    }

    /**
     * Resolves human-friendly aliases for global cities and countries to enhance search accuracy.
     */
    fun getZoneAliases(zoneId: String): List<String> {
        return when (zoneId) {
            "America/Toronto" -> listOf("Toronto", "Canada", "Ontario")
            "Asia/Kolkata", "Asia/Calcutta" -> listOf("Bangalore", "Bengaluru", "India", "Mumbai", "Delhi", "Kolkata")
            "Europe/Berlin" -> listOf("Germany", "Berlin", "Deutschland", "Frankfurt", "Munich", "Hamburg")
            "Asia/Qatar" -> listOf("Qatar", "Doha")
            "Asia/Kuwait" -> listOf("Kuwait", "Kuwait City")
            "Asia/Riyadh" -> listOf("Saudi Arabia", "Saudi", "Riyadh", "Jeddah", "Mecca", "Medina")
            "America/New_York" -> listOf("New York", "USA", "United States", "NYC")
            "Europe/London" -> listOf("London", "UK", "United Kingdom", "Britain", "England")
            "Asia/Tokyo" -> listOf("Tokyo", "Japan")
            "Europe/Paris" -> listOf("Paris", "France")
            "Australia/Sydney" -> listOf("Sydney", "Australia")
            "Asia/Dubai" -> listOf("Dubai", "UAE", "United Arab Emirates", "Abu Dhabi")
            "Asia/Singapore" -> listOf("Singapore")
            "America/Los_Angeles" -> listOf("Los Angeles", "San Francisco", "California", "USA")
            "Asia/Seoul" -> listOf("Seoul", "Korea", "South Korea")
            "America/Sao_Paulo" -> listOf("Sao Paulo", "Brazil")
            else -> emptyList()
        }
    }

    /**
     * Matches timezone queries against ID, city name, and international country aliases.
     */
    fun matchesTimezoneQuery(zoneId: String, query: String): Boolean {
        val q = query.trim()
        if (q.isBlank()) return true
        if (zoneId.contains(q, ignoreCase = true)) return true
        if (zoneId.replace('_', ' ').contains(q, ignoreCase = true)) return true
        val aliases = getZoneAliases(zoneId)
        return aliases.any { it.contains(q, ignoreCase = true) }
    }

    data class EyeExercise(
        val id: String,
        val title: String,
        val benefit: String,
        val instruction: String,
        val durationSeconds: Int
    )

    val EyeExercises: List<EyeExercise> = listOf(
        EyeExercise(
            id = "rule_20_20_20",
            title = "20-20-20 Distant Focus",
            benefit = "Relaxes focusing muscles strained by close screens",
            instruction = "Look away from your monitor at an object at least 20 feet (6 meters) away—like a window view or distant wall—and let your gaze soften.",
            durationSeconds = 20
        ),
        EyeExercise(
            id = "slow_blink",
            title = "Slow Blink Hydration Reset",
            benefit = "Restores natural tear film and prevents dry eyes",
            instruction = "Close your eyes gently for 2 full seconds, open smoothly, then repeat 10 slow, unhurried blinks while relaxing your jaw.",
            durationSeconds = 20
        ),
        EyeExercise(
            id = "figure_eight",
            title = "Horizontal Figure-8 Trace",
            benefit = "Improves smooth ocular mobility and relieves stiffness",
            instruction = "Keep your head still and slowly trace an imaginary sideways figure-8 across the room with your eyes, switching directions halfway.",
            durationSeconds = 30
        ),
        EyeExercise(
            id = "near_far_focus",
            title = "Near-to-Far Accommodation",
            benefit = "Exercises lens flexibility between close and distant targets",
            instruction = "Hold your thumb 10 inches in front of your face, focus on it for 3 seconds, then shift focus to a distant landmark for 3 seconds. Repeat 5 times.",
            durationSeconds = 30
        ),
        EyeExercise(
            id = "palming_rest",
            title = "Warm Palming & Darkness Rest",
            benefit = "Soothes optic fatigue and lowers visual overstimulation",
            instruction = "Rub your palms together until warm, lightly cup them over closed eyes without pressing on your eyelids, and take 4 slow breaths.",
            durationSeconds = 30
        ),
        EyeExercise(
            id = "compass_stretch",
            title = "Four-Point Gaze Stretch",
            benefit = "Releases tension around extraocular muscles",
            instruction = "Without moving your head, look smoothly up for 2 seconds, down for 2 seconds, left for 2 seconds, and right for 2 seconds. Blink between sets.",
            durationSeconds = 20
        )
    )

    fun computeNextEyeBreakEpochMillis(nowMillis: Long, intervalMinutes: Int): Long {
        val safeMinutes = intervalMinutes.coerceIn(1, 180)
        return nowMillis + safeMinutes * 60_000L
    }
}

