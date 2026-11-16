package com.example.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * Public holidays from rules bundled in the app, so nothing needs the network. Only fixed-date, "nth weekday" and
 * Easter-based holidays are covered; lunar festivals (Diwali, Eid) move every year and are deliberately not listed.
 * Observed-day shifts (a holiday moved to the next Monday) are not applied either.
 */
object Holidays {
    const val AUTO = "AUTO"
    const val OFF = "OFF"
    val regions: List<Pair<String, String>> = listOf(
        AUTO to "Auto", "US" to "US", "CA" to "Canada", "GB" to "UK", "IN" to "India", OFF to "Off"
    )

    /** The holiday region to use: a fixed [setting], or for AUTO the device's country when we have its rules. */
    fun resolveRegion(setting: String, deviceCountry: String): String? = when (setting) {
        OFF -> null
        AUTO -> deviceCountry.uppercase().takeIf { c -> regions.any { it.first == c } }
        else -> setting
    }

    fun isoWeek(date: LocalDate): Int = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** The holiday name on [date] for [region], or null on an ordinary day. */
    fun nameOn(date: LocalDate, region: String?): String? {
        if (region == null) return null
        val y = date.year
        val easter = easterSunday(y)
        return when (region) {
            "US" -> when (date) {
                LocalDate.of(y, 1, 1) -> "New Year's Day"
                nth(y, 1, DayOfWeek.MONDAY, 3) -> "Martin Luther King Jr. Day"
                nth(y, 2, DayOfWeek.MONDAY, 3) -> "Presidents' Day"
                last(y, 5, DayOfWeek.MONDAY) -> "Memorial Day"
                LocalDate.of(y, 6, 19) -> "Juneteenth"
                LocalDate.of(y, 7, 4) -> "Independence Day"
                nth(y, 9, DayOfWeek.MONDAY, 1) -> "Labor Day"
                nth(y, 10, DayOfWeek.MONDAY, 2) -> "Columbus Day"
                LocalDate.of(y, 11, 11) -> "Veterans Day"
                nth(y, 11, DayOfWeek.THURSDAY, 4) -> "Thanksgiving"
                LocalDate.of(y, 12, 25) -> "Christmas Day"
                else -> null
            }
            "CA" -> when (date) {
                LocalDate.of(y, 1, 1) -> "New Year's Day"
                easter.minusDays(2) -> "Good Friday"
                LocalDate.of(y, 5, 24).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) -> "Victoria Day"
                LocalDate.of(y, 7, 1) -> "Canada Day"
                nth(y, 9, DayOfWeek.MONDAY, 1) -> "Labour Day"
                LocalDate.of(y, 9, 30) -> "Truth and Reconciliation Day"
                nth(y, 10, DayOfWeek.MONDAY, 2) -> "Thanksgiving"
                LocalDate.of(y, 11, 11) -> "Remembrance Day"
                LocalDate.of(y, 12, 25) -> "Christmas Day"
                LocalDate.of(y, 12, 26) -> "Boxing Day"
                else -> null
            }
            "GB" -> when (date) {
                LocalDate.of(y, 1, 1) -> "New Year's Day"
                easter.minusDays(2) -> "Good Friday"
                easter.plusDays(1) -> "Easter Monday"
                nth(y, 5, DayOfWeek.MONDAY, 1) -> "Early May bank holiday"
                last(y, 5, DayOfWeek.MONDAY) -> "Spring bank holiday"
                last(y, 8, DayOfWeek.MONDAY) -> "Summer bank holiday"
                LocalDate.of(y, 12, 25) -> "Christmas Day"
                LocalDate.of(y, 12, 26) -> "Boxing Day"
                else -> null
            }
            "IN" -> when (date) {
                LocalDate.of(y, 1, 26) -> "Republic Day"
                LocalDate.of(y, 8, 15) -> "Independence Day"
                LocalDate.of(y, 10, 2) -> "Gandhi Jayanti"
                LocalDate.of(y, 12, 25) -> "Christmas Day"
                else -> null
            }
            else -> null
        }
    }

    /** Western Easter Sunday (the "Anonymous Gregorian" algorithm). */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    private fun nth(year: Int, month: Int, dow: DayOfWeek, n: Int): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, dow))

    private fun last(year: Int, month: Int, dow: DayOfWeek): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(dow))

    /** Whole days from [from] to [to]; negative when [to] is earlier. */
    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)
}
