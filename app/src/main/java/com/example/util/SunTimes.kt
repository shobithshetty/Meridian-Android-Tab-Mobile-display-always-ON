package com.example.util

import java.time.Instant
import java.time.LocalDate
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Sunrise and sunset for one day. Either is null during polar day or polar night ([alwaysUp] says which). */
data class SunTimes(val sunrise: Instant?, val sunset: Instant?, val alwaysUp: Boolean) {

    /** True when the sun is below the horizon at [now]. */
    fun isDark(now: Instant): Boolean {
        if (sunrise == null || sunset == null) return !alwaysUp
        return now.isBefore(sunrise) || now.isAfter(sunset)
    }

    companion object {
        private const val J2000_EPOCH_DAY = 10957L // 2000-01-01
        private const val UNIX_EPOCH_JULIAN = 2440587.5

        /**
         * Standard "sunrise equation" (accurate to a few minutes), computed offline from [latitude] and
         * [longitude] in degrees (east positive). [date] is the UTC calendar day of the event.
         */
        fun compute(date: LocalDate, latitude: Double, longitude: Double): SunTimes {
            val n = (date.toEpochDay() - J2000_EPOCH_DAY).toDouble()
            val meanSolarTime = n - longitude / 360.0
            val m = Math.toRadians((357.5291 + 0.98560028 * meanSolarTime) % 360.0)
            val c = 1.9148 * sin(m) + 0.0200 * sin(2 * m) + 0.0003 * sin(3 * m)
            val lambda = Math.toRadians((Math.toDegrees(m) + c + 180.0 + 102.9372) % 360.0)
            val transit = 2451545.0 + meanSolarTime + 0.0053 * sin(m) - 0.0069 * sin(2 * lambda)
            val sinDec = sin(lambda) * sin(Math.toRadians(23.4397))
            val dec = Math.asin(sinDec)
            val lat = Math.toRadians(latitude)
            val cosOmega = (sin(Math.toRadians(-0.833)) - sin(lat) * sinDec) / (cos(lat) * cos(dec))
            if (cosOmega > 1.0) return SunTimes(null, null, alwaysUp = false) // polar night
            if (cosOmega < -1.0) return SunTimes(null, null, alwaysUp = true) // midnight sun
            val omega = Math.toDegrees(acos(cosOmega))
            return SunTimes(
                sunrise = julianToInstant(transit - omega / 360.0),
                sunset = julianToInstant(transit + omega / 360.0),
                alwaysUp = false
            )
        }

        /**
         * Sun times that bracket [now]: the sunset of yesterday/today and the sunrise of today/tomorrow are all
         * checked, so "dark" is right even when the local day boundary differs from UTC.
         */
        fun isDarkAt(now: Instant, latitude: Double, longitude: Double): Boolean {
            val day = now.atZone(java.time.ZoneOffset.UTC).toLocalDate()
            // The date used by the formula is the UTC day on which that sun event falls, so test neighbours too.
            val days = listOf(day.minusDays(1), day, day.plusDays(1)).map { compute(it, latitude, longitude) }
            if (days.any { it.sunrise == null }) return !days[1].alwaysUp
            val events = days.flatMap { listOf(it.sunrise!! to true, it.sunset!! to false) }.sortedBy { it.first }
            val last = events.lastOrNull { !it.first.isAfter(now) } ?: return false
            return !last.second // after a sunrise it is light, after a sunset it is dark
        }

        private fun julianToInstant(julian: Double): Instant {
            val millis = floor((julian - UNIX_EPOCH_JULIAN) * 86_400_000.0).toLong()
            return Instant.ofEpochMilli(millis)
        }
    }
}
