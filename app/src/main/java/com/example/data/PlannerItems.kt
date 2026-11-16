package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * A date (and optionally a time) to count down to, e.g. "Trip" in 12 days or "Meeting" at 3:00 today.
 * [minuteOfDay] is -1 for an all-day event.
 */
data class Countdown(val id: Long, val label: String, val epochDay: Long, val minuteOfDay: Int) {
    val isAllDay: Boolean get() = minuteOfDay < 0

    private fun target(now: ZonedDateTime): ZonedDateTime {
        val time = if (isAllDay) LocalTime.MIDNIGHT else LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        return ZonedDateTime.of(LocalDate.ofEpochDay(epochDay), time, now.zone)
    }

    fun remainingMillis(now: ZonedDateTime): Long = Duration.between(now, target(now)).toMillis()

    /** Still worth showing: an all-day event stays through its day; a timed one for 5 minutes after it starts. */
    fun isUpcoming(now: ZonedDateTime): Boolean =
        if (isAllDay) epochDay >= now.toLocalDate().toEpochDay() else remainingMillis(now) > -5 * 60_000L

    /** Short text such as "today", "tomorrow", "12 days", "1h 20m" or "12 min". */
    fun describe(now: ZonedDateTime): String {
        if (isAllDay) {
            return when (val days = epochDay - now.toLocalDate().toEpochDay()) {
                0L -> "today"
                1L -> "tomorrow"
                else -> "$days days"
            }
        }
        val remaining = remainingMillis(now)
        if (remaining <= 0) return "now"
        val minutes = (remaining + 59_999L) / 60_000L // round up so it never shows "0 min" early
        return when {
            minutes < 60 -> "$minutes min"
            minutes < 24 * 60 -> "${minutes / 60}h ${minutes % 60}m".replace(" 0m", "")
            else -> {
                val days = epochDay - now.toLocalDate().toEpochDay()
                if (days == 1L) "tomorrow" else "$days days"
            }
        }
    }

    /** For a timed event inside its final [windowHours], how much of that window is left (1 = full, 0 = now). */
    fun barFraction(now: ZonedDateTime, windowHours: Int = 3): Float? {
        if (isAllDay) return null
        val window = windowHours * 3_600_000L
        val remaining = remainingMillis(now)
        return if (remaining in 0..window) remaining.toFloat() / window else null
    }
}

/** A recurring daily to-do that appears once its time has passed and stays until ticked off for that day. */
data class Routine(val id: Long, val label: String, val minuteOfDay: Int, val doneEpochDay: Long = -1L) {
    fun isDue(now: ZonedDateTime): Boolean {
        val minute = now.hour * 60 + now.minute
        return minute >= minuteOfDay && doneEpochDay != now.toLocalDate().toEpochDay()
    }
}

/** Plain JSON in a settings column keeps the lists inside the existing backup without new tables. */
object PlannerCodec {
    fun countdownsToJson(list: List<Countdown>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("id", it.id).put("label", it.label).put("epochDay", it.epochDay).put("minuteOfDay", it.minuteOfDay))
        }
    }.toString()

    fun countdownsFromJson(json: String): List<Countdown> = parse(json) {
        Countdown(it.getLong("id"), it.getString("label"), it.getLong("epochDay"), it.optInt("minuteOfDay", -1))
    }

    fun routinesToJson(list: List<Routine>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("id", it.id).put("label", it.label).put("minuteOfDay", it.minuteOfDay).put("doneEpochDay", it.doneEpochDay))
        }
    }.toString()

    fun routinesFromJson(json: String): List<Routine> = parse(json) {
        Routine(it.getLong("id"), it.getString("label"), it.getInt("minuteOfDay"), it.optLong("doneEpochDay", -1L))
    }

    private fun <T> parse(json: String, build: (JSONObject) -> T): List<T> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { build(array.getJSONObject(it)) }
    }.getOrDefault(emptyList()) // a damaged value must never stop the app from opening
}
