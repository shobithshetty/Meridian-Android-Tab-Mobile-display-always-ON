package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ClockRepository
import com.example.data.ClockSettings
import com.example.data.FriendClock
import com.example.ui.theme.AllPresetThemes
import com.example.ui.theme.colorToHex
import com.example.ui.theme.colorToHsv
import com.example.ui.theme.hsvToColor
import com.example.ui.theme.parseHexColorOrNull
import com.example.ui.theme.resolveVisualTheme
import com.example.util.CrashReportManager
import com.example.util.RealtimeClockSyncManager
import com.example.util.TimeAndAlertUtils
import com.example.widget.WorldClockAppWidgetProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Meridian", appName)
    }

    @Test
    fun `launch main activity`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
        }
    }

    @Test
    fun `adaptive screen posture classifies mobile portrait, phone landscape, foldable, and tablet`() {
        assertEquals(
            AdaptiveScreenPosture.COMPACT,
            classifyScreenPosture(maxWidth = 390.dp, maxHeight = 844.dp)
        )
        assertEquals(
            AdaptiveScreenPosture.SHORT_LANDSCAPE,
            classifyScreenPosture(maxWidth = 844.dp, maxHeight = 390.dp)
        )
        assertEquals(
            AdaptiveScreenPosture.MEDIUM,
            classifyScreenPosture(maxWidth = 720.dp, maxHeight = 1024.dp)
        )
        assertEquals(
            AdaptiveScreenPosture.EXPANDED,
            classifyScreenPosture(maxWidth = 1280.dp, maxHeight = 800.dp)
        )
    }

    @Test
    fun `15 to 20 minute interval scheduling handles aligned and non-divisible intervals accurately`() {
        val base = ZonedDateTime.of(2026, 10, 26, 10, 50, 0, 0, ZoneId.of("UTC"))

        // 15m aligned from 10:50 -> 11:00 (10 mins = 600s)
        val next15 = TimeAndAlertUtils.computeNextChimeEpochMillis(base, 15, alignToTopHour = true)
        assertEquals(600, TimeAndAlertUtils.computeRemainingChimeSeconds(base.toInstant().toEpochMilli(), next15))

        // 17m from 10:50 -> 11:07 (exact 17 mins = 1020s, never prematurely resetting at 11:00)
        val next17 = TimeAndAlertUtils.computeNextChimeEpochMillis(base, 17, alignToTopHour = true)
        assertEquals(17 * 60, TimeAndAlertUtils.computeRemainingChimeSeconds(base.toInstant().toEpochMilli(), next17))

        // 20m unaligned from 10:50 -> 11:10 (exact 20 mins = 1200s)
        val next20Unaligned = TimeAndAlertUtils.computeNextChimeEpochMillis(base, 20, alignToTopHour = false)
        assertEquals(20 * 60, TimeAndAlertUtils.computeRemainingChimeSeconds(base.toInstant().toEpochMilli(), next20Unaligned))

        // Countdown display at exact 15m start shows 15 mins 00 secs (not 14 mins 00 secs)
        val (mins, secs) = TimeAndAlertUtils.formatMinutesAndSeconds(15 * 60)
        assertEquals(15, mins)
        assertEquals("00", secs)
    }

    @Test
    fun `timezone half-hour offsets, new year date boundary, and alarm validation work accurately`() {
        val utcNow = ZonedDateTime.of(2026, 12, 31, 22, 0, 0, 0, ZoneId.of("UTC"))
        assertEquals("+5h 30m ahead", TimeAndAlertUtils.formatRelativeOffset(utcNow, "Asia/Kolkata"))
        assertEquals("+5h 45m ahead", TimeAndAlertUtils.formatRelativeOffset(utcNow, "Asia/Kathmandu"))

        val tokyoNewYear = utcNow.withZoneSameInstant(ZoneId.of("Asia/Tokyo")) // Jan 1, 2027 07:00
        assertEquals("+1d", TimeAndAlertUtils.computeDayDifferenceBadge(utcNow, tokyoNewYear))
        assertEquals("-1d", TimeAndAlertUtils.computeDayDifferenceBadge(tokyoNewYear, utcNow))

        assertEquals(8 to 30, TimeAndAlertUtils.validateAlarmTime("08", "30"))
        assertNull(TimeAndAlertUtils.validateAlarmTime("99", "00"))
        assertNull(TimeAndAlertUtils.validateAlarmTime("12", "65"))
    }

    @Test
    fun `new installs default to the Bold layout with the Aurora theme and every style id is unique`() {
        val defaults = ClockSettings()
        assertEquals("BOLD", defaults.layoutStyle)
        assertEquals("Aurora", defaults.themeName)
        val ids = com.example.ui.components.LayoutStyle.options.map { it.first }
        assertEquals(11, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(defaults.layoutStyle in ids)
        // Every watch face must be routed through the shared frame, and the four original styles must not be.
        val faces = com.example.ui.components.LayoutStyle.faceIds
        assertEquals(7, faces.size)
        assertTrue(faces.all { it in ids })
        assertTrue(faces.all { com.example.ui.components.isWatchFace(it) })
        assertTrue(listOf("BOLD", "CALM", "DASHBOARD", "CLASSIC").none { com.example.ui.components.isWatchFace(it) })
    }

    @Test
    fun `day strip is on by default and places an alarm at its home-zone minute of day`() {
        assertTrue(ClockSettings().rhythmStripEnabled)
        val home = java.time.ZonedDateTime.of(2026, 11, 15, 22, 0, 0, 0, java.time.ZoneId.of("America/Toronto"))
        // 09:00 in Tokyo on the same instant-day is 19:00 the previous evening in Toronto (UTC-5 vs UTC+9 = 14h).
        val tokyo = com.example.data.AlarmItem(hour = 9, minute = 0, timezoneId = "Asia/Tokyo")
        assertEquals(19 * 60, com.example.ui.components.alarmMinuteOfDay(tokyo, home))
        val local = com.example.data.AlarmItem(hour = 7, minute = 30, timezoneId = "America/Toronto")
        assertEquals(7 * 60 + 30, com.example.ui.components.alarmMinuteOfDay(local, home))
    }

    @Test
    fun `a focus session ends exactly its duration after it starts`() {
        val s = com.example.ui.components.FocusSession(label = "Write", startMillis = 1_000L, durationMillis = 25 * 60_000L)
        assertEquals(1_000L + 1_500_000L, s.endMillis)
    }

    @Test
    fun `night dimming and backdrop have safe defaults and the dim follows the clock`() {
        val defaults = ClockSettings()
        assertEquals("AUTO", defaults.nightDimMode)
        assertEquals("NONE", defaults.backdropPattern)
        val dim = com.example.ui.components.NightDim
        assertEquals(0f, dim.scrimAlpha(dim.AUTO, 12))
        assertTrue(dim.scrimAlpha(dim.AUTO, 23) > 0f)
        assertTrue(dim.scrimAlpha(dim.AUTO, 3) > 0f)
        assertEquals(0f, dim.scrimAlpha(dim.OFF, 3))
        assertTrue(dim.scrimAlpha(dim.BEDSIDE, 12) > dim.scrimAlpha(dim.AUTO, 23))
    }

    @Test
    fun `sun times match known London values and handle polar day and night`() {
        val lat = 51.5074
        val lon = -0.1278
        val june = com.example.util.SunTimes.compute(java.time.LocalDate.of(2024, 6, 21), lat, lon)
        fun minutes(i: java.time.Instant) = i.atZone(java.time.ZoneOffset.UTC).let { it.hour * 60 + it.minute }
        // Published values: sunrise 03:43 UTC, sunset 20:21 UTC. Allow 10 minutes for the simple formula.
        assertTrue(kotlin.math.abs(minutes(june.sunrise!!) - (3 * 60 + 43)) <= 10)
        assertTrue(kotlin.math.abs(minutes(june.sunset!!) - (20 * 60 + 21)) <= 10)
        val december = com.example.util.SunTimes.compute(java.time.LocalDate.of(2024, 12, 21), lat, lon)
        assertTrue(kotlin.math.abs(minutes(december.sunrise!!) - (8 * 60 + 4)) <= 10)
        assertTrue(kotlin.math.abs(minutes(december.sunset!!) - (15 * 60 + 53)) <= 10)

        fun at(h: Int, m: Int = 0) = java.time.LocalDateTime.of(2024, 6, 21, h, m).toInstant(java.time.ZoneOffset.UTC)
        assertFalse(com.example.util.SunTimes.isDarkAt(at(12), lat, lon))
        assertTrue(com.example.util.SunTimes.isDarkAt(at(23), lat, lon))
        assertTrue(com.example.util.SunTimes.isDarkAt(at(2), lat, lon))

        val midnightSun = com.example.util.SunTimes.compute(java.time.LocalDate.of(2024, 6, 21), 69.65, 18.96)
        assertTrue(midnightSun.sunrise == null && midnightSun.alwaysUp)
        val polarNight = com.example.util.SunTimes.compute(java.time.LocalDate.of(2024, 12, 21), 69.65, 18.96)
        assertTrue(polarNight.sunrise == null && !polarNight.alwaysUp)
    }

    @Test
    fun `sunset dimming falls back to the fixed hours without a location and keep awake honours charging`() {
        val dim = com.example.ui.components.NightDim
        assertTrue(dim.scrimAlpha(dim.SUN, 23, null) > 0f)
        assertEquals(0f, dim.scrimAlpha(dim.SUN, 12, null))
        assertEquals(0f, dim.scrimAlpha(dim.SUN, 23, false))
        assertTrue(dim.scrimAlpha(dim.SUN, 12, true) > 0f)
        val keep = com.example.ui.components.KeepAwake
        assertTrue(keep.shouldHold(keep.ALWAYS, charging = false))
        assertFalse(keep.shouldHold(keep.CHARGING, charging = false))
        assertTrue(keep.shouldHold(keep.CHARGING, charging = true))
        assertEquals("ALWAYS", ClockSettings().keepAwakeMode)
        assertFalse(ClockSettings().autoStartOnBoot)
    }

    @Test
    fun `pomodoro cycles through work and breaks and ends after the last round`() {
        val cycle = com.example.ui.components.FocusCycle
        val phase = com.example.ui.components.FocusPhase.entries.associateBy { it.name }.let { m ->
            object { val WORK = m.getValue("WORK"); val BREAK = m.getValue("BREAK") }
        }
        var s: com.example.ui.components.FocusSession? = com.example.ui.components.FocusSession(
            label = "Write", startMillis = 0L, durationMillis = 25 * 60_000L, rounds = 4, workMillis = 25 * 60_000L
        )
        val seen = mutableListOf<Pair<com.example.ui.components.FocusPhase, Long>>()
        var guard = 0
        while (s != null && guard++ < 20) {
            seen += s.phase to s.durationMillis / 60_000L
            s = cycle.next(s, 0L)
        }
        // work 25, break 5, work 25, break 5, work 25, break 5, work 25 (last round: no break after it)
        assertEquals(7, seen.size)
        assertEquals(phase.WORK to 25L, seen[0])
        assertEquals(phase.BREAK to 5L, seen[1])
        assertEquals(phase.WORK to 25L, seen.last())
        // A single session has nothing after it.
        val single = com.example.ui.components.FocusSession("x", 0L, 60_000L)
        assertEquals(null, cycle.next(single, 0L))
        // The break after the 4th round of a 6-round cycle is the long one.
        val fourth = com.example.ui.components.FocusSession("x", 0L, 60_000L, round = 4, rounds = 6, workMillis = 60_000L)
        assertEquals(15L, cycle.next(fourth, 0L)!!.durationMillis / 60_000L)
        assertEquals("Break 2/4", cycle.pillLabel(com.example.ui.components.FocusSession("x", 0L, 1L, phase.BREAK, 2, 4)))
    }

    @Test
    fun `transition warnings fire at 10 and 5 minutes but not for blocks too short to need them`() {
        val tw = com.example.ui.components.TransitionWarning
        assertEquals(10, tw.thresholdReached(10 * 60_000L, 25 * 60_000L))
        assertEquals(10, tw.thresholdReached(9 * 60_000L + 30_000L, 25 * 60_000L))
        assertEquals(5, tw.thresholdReached(4 * 60_000L + 1_000L, 25 * 60_000L))
        assertEquals(null, tw.thresholdReached(7 * 60_000L, 25 * 60_000L))
        // A 5-minute break must not warn "5 min left" the moment it starts.
        assertEquals(null, tw.thresholdReached(5 * 60_000L, 5 * 60_000L))
        assertEquals(null, tw.thresholdReached(0L, 25 * 60_000L))
        val now = java.time.ZonedDateTime.of(2026, 11, 16, 9, 0, 0, 0, java.time.ZoneId.of("UTC"))
        val alarm = com.example.data.AlarmItem(hour = 9, minute = 10, timezoneId = "UTC")
        assertEquals(10 * 60_000L, tw.millisUntil(alarm, now))
        assertEquals(null, tw.millisUntil(alarm.copy(isEnabled = false), now))
        // Already passed today means tomorrow.
        assertEquals(23 * 3_600_000L + 50 * 60_000L, tw.millisUntil(alarm.copy(hour = 8, minute = 50), now))
    }

    @Test
    fun `countdowns describe time left, shrink in the last three hours and hide when over`() {
        val zone = java.time.ZoneId.of("UTC")
        val now = java.time.ZonedDateTime.of(2026, 11, 16, 12, 0, 0, 0, zone)
        val today = now.toLocalDate().toEpochDay()
        fun timed(h: Int, m: Int, dayOffset: Long = 0) = com.example.data.Countdown(1, "x", today + dayOffset, h * 60 + m)
        assertEquals("12 min", timed(12, 12).describe(now))
        assertEquals("1h 20m", timed(13, 20).describe(now))
        assertEquals("2h", timed(14, 0).describe(now))
        assertEquals("21h", timed(9, 0, 1).describe(now)) // under 24h away, so hours, not "tomorrow"
        assertEquals("tomorrow", timed(15, 0, 1).describe(now))
        assertEquals("5 days", timed(9, 0, 5).describe(now))
        val allDay = com.example.data.Countdown(2, "Trip", today + 12, -1)
        assertEquals("12 days", allDay.describe(now))
        assertEquals("today", allDay.copy(epochDay = today).describe(now))
        assertTrue(allDay.isUpcoming(now) && !allDay.copy(epochDay = today - 1).isUpcoming(now))
        // Bar: full at 3h out, half at 1.5h, none beyond 3h or for all-day.
        assertEquals(0.5f, timed(13, 30).barFraction(now)!!, 0.001f)
        assertEquals(null, timed(16, 0).barFraction(now))
        assertEquals(null, allDay.barFraction(now))
        // A timed event stays for 5 minutes after it starts, then goes.
        assertTrue(timed(11, 57).isUpcoming(now))
        assertTrue(!timed(11, 50).isUpcoming(now))
    }

    @Test
    fun `routine items are due after their time until ticked, then return the next day`() {
        val zone = java.time.ZoneId.of("UTC")
        val morning = java.time.ZonedDateTime.of(2026, 11, 16, 7, 0, 0, 0, zone)
        val noon = morning.withHour(12)
        val today = morning.toLocalDate().toEpochDay()
        val meds = com.example.data.Routine(1, "Meds", 8 * 60)
        assertTrue(!meds.isDue(morning))
        assertTrue(meds.isDue(noon))
        assertTrue(!meds.copy(doneEpochDay = today).isDue(noon))
        assertTrue(meds.copy(doneEpochDay = today).isDue(noon.plusDays(1)))
    }

    @Test
    fun `holidays follow the rules for each region and week numbers are iso`() {
        val h = com.example.util.Holidays
        fun d(y: Int, m: Int, day: Int) = java.time.LocalDate.of(y, m, day)
        assertEquals(d(2026, 4, 5), h.easterSunday(2026))
        assertEquals(d(2025, 4, 20), h.easterSunday(2025))
        assertEquals("Good Friday", h.nameOn(d(2026, 4, 3), "CA"))
        assertEquals("Easter Monday", h.nameOn(d(2026, 4, 6), "GB"))
        assertEquals("Remembrance Day", h.nameOn(d(2026, 11, 11), "CA"))
        assertEquals("Thanksgiving", h.nameOn(d(2026, 10, 12), "CA")) // 2nd Monday of October
        assertEquals("Thanksgiving", h.nameOn(d(2026, 11, 26), "US")) // 4th Thursday of November
        assertEquals("Victoria Day", h.nameOn(d(2026, 5, 18), "CA"))
        assertEquals("Memorial Day", h.nameOn(d(2026, 5, 25), "US"))
        assertEquals("Spring bank holiday", h.nameOn(d(2026, 5, 25), "GB"))
        assertEquals("Republic Day", h.nameOn(d(2026, 1, 26), "IN"))
        assertEquals(null, h.nameOn(d(2026, 11, 16), "CA"))
        assertEquals(null, h.nameOn(d(2026, 12, 25), null))
        assertEquals(47, h.isoWeek(d(2026, 11, 16)))
        assertEquals("CA", h.resolveRegion("AUTO", "ca"))
        assertEquals(null, h.resolveRegion("AUTO", "FR"))
        assertEquals(null, h.resolveRegion("OFF", "CA"))
        assertEquals("US", h.resolveRegion("US", "CA"))
    }

    @Test
    fun `planner items survive json and a damaged value never crashes`() {
        val codec = com.example.data.PlannerCodec
        val counts = listOf(com.example.data.Countdown(1, "Trip \"North\"", 20000, -1), com.example.data.Countdown(2, "Call", 20001, 930))
        assertEquals(counts, codec.countdownsFromJson(codec.countdownsToJson(counts)))
        val routines = listOf(com.example.data.Routine(3, "Meds", 480, 20000))
        assertEquals(routines, codec.routinesFromJson(codec.routinesToJson(routines)))
        assertEquals(emptyList<com.example.data.Countdown>(), codec.countdownsFromJson("not json"))
        assertEquals(emptyList<com.example.data.Routine>(), codec.routinesFromJson("{\"oops\":1}"))
        val dock = com.example.ui.components.buildDockContent(
            ClockSettings(countdownsJson = "garbage", showDayInfo = false), java.time.ZonedDateTime.now(), "CA"
        )
        assertTrue(dock.isEmpty)
        // Small phones hide the plain week chip so an empty dock takes no height; a holiday still shows.
        val ordinary = java.time.ZonedDateTime.of(2026, 11, 16, 12, 0, 0, 0, java.time.ZoneId.of("UTC"))
        val holiday = java.time.ZonedDateTime.of(2026, 11, 11, 12, 0, 0, 0, java.time.ZoneId.of("UTC"))
        assertTrue(com.example.ui.components.buildDockContent(ClockSettings(), ordinary, "CA", plainWeek = false).isEmpty)
        assertEquals("Wk 47", com.example.ui.components.buildDockContent(ClockSettings(), ordinary, "CA", plainWeek = true).dayInfo)
        assertEquals("Wk 46 · Remembrance Day", com.example.ui.components.buildDockContent(ClockSettings(), holiday, "CA", plainWeek = false).dayInfo)
    }

    @Test
    fun `gentle alarm volume ramps from quiet to full and defaults are safe`() {
        val ramp = com.example.ui.components.AlarmRamp
        assertEquals(ramp.START_VOLUME, ramp.volumeAt(0), 0.0001f)
        assertEquals(1f, ramp.volumeAt(ramp.RAMP_MS), 0.0001f)
        assertEquals(1f, ramp.volumeAt(ramp.RAMP_MS * 5), 0.0001f)
        val half = ramp.volumeAt(ramp.RAMP_MS / 2)
        assertTrue(half > ramp.START_VOLUME && half < 1f)
        assertTrue(ramp.volumeAt(-1000) >= ramp.START_VOLUME) // a clock step backwards must not go quieter than the start
        assertEquals("GENTLE", ClockSettings().alarmStyle)
        assertEquals("", ClockSettings().autoBackupUri) // off until the user picks a folder
    }

    @Test
    fun `sleep timer fades out over the last seconds only`() {
        val t = com.example.ui.components.SleepTimer
        assertEquals(1f, t.fadeFactor(10 * 60_000L), 0.0001f)
        assertEquals(1f, t.fadeFactor(t.FADE_MS), 0.0001f)
        assertEquals(0.5f, t.fadeFactor(t.FADE_MS / 2), 0.0001f)
        assertEquals(0f, t.fadeFactor(0), 0.0001f)
        assertEquals(0f, t.fadeFactor(-5), 0.0001f)
        assertEquals("12m", t.remainingText(11 * 60_000L + 1L))
        assertEquals("No timer", t.label(null))
    }

    @Test
    fun `automatic backup runs once a day after 3am and keeps only the newest seven`() {
        val b = com.example.util.AutoBackup
        val zone = java.time.ZoneId.of("UTC")
        fun at(h: Int, day: Int = 16) = java.time.ZonedDateTime.of(2026, 11, day, h, 0, 0, 0, zone)
        val today = at(9).toLocalDate().toEpochDay()
        assertTrue(!b.isDue("", -1, at(9)))                // no folder chosen
        assertTrue(b.isDue("content://x", -1, at(9)))      // never backed up
        assertTrue(!b.isDue("content://x", today, at(9)))  // already done today
        assertTrue(!b.isDue("content://x", today - 1, at(2))) // before 3am: wait
        assertTrue(b.isDue("content://x", today - 1, at(3)))
        assertEquals("meridian-backup-2026-11-16.json", b.fileName(java.time.LocalDate.of(2026, 11, 16)))
        val names = (1..10).map { b.fileName(java.time.LocalDate.of(2026, 11, it)) } + listOf("notes.txt", "meridian-backup-x.json")
        val doomed = b.namesToDelete(names)
        assertEquals(3, doomed.size)
        assertEquals(setOf("meridian-backup-2026-11-01.json", "meridian-backup-2026-11-02.json", "meridian-backup-2026-11-03.json"), doomed.toSet())
        assertTrue("notes.txt" !in doomed && "meridian-backup-x.json" !in doomed) // never touches the user's other files
    }

    @Test
    fun `noise generators stay in range, are repeatable and tilt from bright to deep`() {
        val kinds = com.example.audio.AmbientKind.entries
        fun sample(kind: com.example.audio.AmbientKind, seed: Long = 7L) =
            ShortArray(44_100).also { com.example.audio.NoiseSource(kind, seed).fill(it) }
        fun crossings(s: ShortArray) = (1 until s.size).count { (s[it] >= 0) != (s[it - 1] >= 0) }.toDouble() / s.size
        fun rms(s: ShortArray) = kotlin.math.sqrt(s.sumOf { it.toDouble() * it } / s.size) / Short.MAX_VALUE
        kinds.forEach { k ->
            val s = sample(k)
            assertTrue("$k has sound", rms(s) > 0.02)
            assertTrue("$k is not too loud", rms(s) < 0.6)
            assertTrue("$k repeatable", s.contentEquals(sample(k)))
        }
        // More zero crossings means brighter sound: white > pink > brown.
        val white = crossings(sample(com.example.audio.AmbientKind.WHITE))
        val pink = crossings(sample(com.example.audio.AmbientKind.PINK))
        val brown = crossings(sample(com.example.audio.AmbientKind.BROWN))
        assertTrue("white=$white pink=$pink brown=$brown", white > pink && pink > brown)
        assertEquals(com.example.audio.AmbientKind.PINK, com.example.audio.AmbientKind.fromId("nonsense"))
    }

    private fun forecastJson(currentTime: String, code: Int, times: List<String>, precip: List<Double>, prob: List<Int?>?, temps: List<Double>): String {
        val probPart = prob?.let { ",\"precipitation_probability\":[${it.joinToString(",") { p -> p?.toString() ?: "null" }}]" } ?: ""
        return """{"current":{"time":"$currentTime","temperature_2m":13.3,"weather_code":$code,"is_day":1},
            "hourly":{"time":[${times.joinToString(",") { "\"$it\"" }}],"precipitation":[${precip.joinToString(",")}]$probPart,
            "temperature_2m":[${temps.joinToString(",")}]}}"""
    }

    @Test
    fun `open-meteo forecasts are read for one place or many and find rain that is coming`() {
        val hours = listOf("2026-11-16T14:00", "2026-11-16T15:00", "2026-11-16T16:00", "2026-11-16T17:00", "2026-11-16T18:00", "2026-11-16T19:00")
        val rainLater = forecastJson("2026-11-16T14:15", 3, hours, listOf(0.0, 0.0, 1.2, 0.4, 0.0, 0.0), listOf(0, 10, 80, 70, 0, 0), listOf(13.0, 12.0, 12.0, 11.0, 10.0, 9.0))
        val single = com.example.data.OpenMeteoParser.parse(rainLater, 1_000L)
        assertEquals(1, single.size)
        val w = single[0]!!
        assertEquals(13.3, w.tempC, 0.001)
        assertEquals(105, w.rainInMinutes) // 14:15 -> 16:00
        assertEquals(13.0, w.maxC24, 0.001)
        assertEquals(9.0, w.minC24, 0.001)
        assertEquals(1_000L, w.fetchedMillis)

        val many = com.example.data.OpenMeteoParser.parse("[$rainLater,$rainLater]", 5L)
        assertEquals(2, many.size)
        // Raining in this hour counts as "now" (0); a dry spell has no rain time at all; no chance data trusts the amount.
        val now = com.example.data.OpenMeteoParser.parse(forecastJson("2026-11-16T14:15", 61, hours, listOf(2.0, 0.0, 0.0, 0.0, 0.0, 0.0), listOf(90, 0, 0, 0, 0, 0), List(6) { 10.0 }), 0L)[0]!!
        assertEquals(0, now.rainInMinutes)
        val dry = com.example.data.OpenMeteoParser.parse(forecastJson("2026-11-16T14:15", 0, hours, List(6) { 0.0 }, listOf(0, 0, 0, 0, 0, 0), List(6) { 10.0 }), 0L)[0]!!
        assertEquals(null, dry.rainInMinutes)
        val noChance = com.example.data.OpenMeteoParser.parse(forecastJson("2026-11-16T14:15", 3, hours, listOf(0.0, 1.0, 0.0, 0.0, 0.0, 0.0), null, List(6) { 10.0 }), 0L)[0]!!
        assertEquals(45, noChance.rainInMinutes)
        // Light drizzle below the 0.2 mm threshold, or a low chance, is ignored.
        val drizzle = com.example.data.OpenMeteoParser.parse(forecastJson("2026-11-16T14:15", 3, hours, listOf(0.1, 0.1, 5.0, 0.0, 0.0, 0.0), listOf(80, 80, 20, 0, 0, 0), List(6) { 10.0 }), 0L)[0]!!
        assertEquals(null, drizzle.rainInMinutes)
        // A broken place becomes null instead of breaking the others.
        assertEquals(null, com.example.data.OpenMeteoParser.parse("[{\"oops\":1}]", 0L)[0])
    }

    @Test
    fun `temperatures round and convert, and warnings follow the rules`() {
        val u = com.example.data.WeatherUnits
        assertEquals("13°", u.temp(13.4, "C"))
        assertEquals("56°", u.temp(13.4, "F"))
        assertEquals("0°", u.temp(-0.3, "C")) // never "-0"
        assertEquals("-5°", u.temp(-5.0, "C"))
        fun w(code: Int = 3, rain: Int? = null, max: Double = 20.0, min: Double = 10.0, fetched: Long = 0L) =
            com.example.data.CityWeather(15.0, code, true, rain, max, min, fetched)
        val rules = com.example.data.WeatherWarnings
        assertEquals(emptyList<com.example.data.WeatherNotice>(), rules.forCity("Toronto, Canada", w(), "C", true))
        assertEquals("Rain in 45 min", rules.forCity("Toronto, Canada", w(rain = 42), "C", true).single().text) // rounds up to 5
        assertEquals(emptyList<com.example.data.WeatherNotice>(), rules.forCity("London, UK", w(rain = 42), "C", false)) // minor only for home
        assertEquals(emptyList<com.example.data.WeatherNotice>(), rules.forCity("Toronto, Canada", w(code = 61, rain = 42), "C", true)) // already raining
        assertEquals("Thunderstorm", rules.forCity("Doha, Qatar", w(code = 95), "C", false).single().text)
        assertEquals(2, rules.forCity("Doha, Qatar", w(code = 95), "C", false).single().level)
        assertEquals("Heat up to 104°", rules.forCity("Doha, Qatar", w(max = 40.0), "F", false).single().text)
        assertEquals("Extreme cold -20°", rules.forCity("Oslo, Norway", w(min = -20.0), "C", false).single().text)
        assertEquals("Toronto", rules.forCity("Toronto, Canada", w(code = 95), "C", false).single().city)
        assertTrue(rules.isFresh(w(fetched = 0L), rules.MAX_AGE_MILLIS))
        assertTrue(!rules.isFresh(w(fetched = 0L), rules.MAX_AGE_MILLIS + 1))
        assertTrue(com.example.data.WeatherCodes.info(0, false).icon != com.example.data.WeatherCodes.info(0, true).icon) // moon at night
    }

    @Test
    fun `official alerts are read from both services and expired or test ones are skipped`() {
        val now = java.time.Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()
        val nws = """{"features":[
            {"properties":{"status":"Actual","messageType":"Alert","event":"Flood Warning","severity":"Severe","ends":"2026-11-17T23:48:00-06:00"}},
            {"properties":{"status":"Actual","messageType":"Update","event":"Tornado Warning","severity":"Extreme","expires":"2026-11-16T14:00:00-06:00","ends":null}},
            {"properties":{"status":"Actual","messageType":"Alert","event":"Frost Advisory","severity":"Minor","expires":"2026-11-15T01:00:00-06:00"}},
            {"properties":{"status":"Test","messageType":"Alert","event":"Test Message","severity":"Minor","expires":"2026-11-20T01:00:00-06:00"}},
            {"properties":{"status":"Actual","messageType":"Cancel","event":"Wind Advisory","severity":"Minor","expires":"2026-11-20T01:00:00-06:00"}}]}"""
        val found = com.example.data.AlertParsers.parseNws(nws, now)
        assertEquals(listOf("Flood Warning", "Tornado Warning"), found.map { it.title })
        assertEquals(listOf(2, 3), found.map { it.level })
        val ec = """{"features":[
            {"properties":{"alert_type":"advisory","alert_short_name_en":"Fog (advisory)","risk_colour_en":"yellow","expiration_datetime":"2026-11-16T19:58:02.461Z"}},
            {"properties":{"alert_type":"advisory","alert_short_name_en":"Fog (advisory)","risk_colour_en":"yellow","expiration_datetime":"2026-11-16T19:58:02.461Z"}},
            {"properties":{"alert_type":"warning","alert_short_name_en":"Snow squall (warning)","risk_colour_en":"red","expiration_datetime":"2026-11-16T18:00:00.000Z"}},
            {"properties":{"alert_type":"ended","alert_short_name_en":"Rain (ended)","expiration_datetime":"2026-11-16T18:00:00.000Z"}},
            {"properties":{"alert_type":"warning","alert_short_name_en":"Old warning","risk_colour_en":"orange","expiration_datetime":"2026-11-15T18:00:00.000Z"}}]}"""
        val ca = com.example.data.AlertParsers.parseEnvironmentCanada(ec, now)
        assertEquals(listOf("Fog (advisory)", "Snow squall (warning)"), ca.map { it.title }) // duplicate merged, ended and expired dropped
        assertEquals(listOf(1, 3), ca.map { it.level })
        assertEquals(emptyList<com.example.data.OfficialAlert>(), com.example.data.AlertParsers.parseNws("garbage", now))
        assertEquals(emptyList<com.example.data.OfficialAlert>(), com.example.data.AlertParsers.parseEnvironmentCanada("{}", now))
    }

    @Test
    fun `weather cache survives a round trip and a damaged one is ignored`() {
        val data = com.example.data.WeatherCacheData(
            geo = mapOf("Toronto|America/Toronto" to com.example.data.GeoPoint(43.7, -79.4, "CA")),
            weather = mapOf(7 to com.example.data.CityWeather(3.5, 61, false, null, 6.0, -1.0, 99L), 8 to com.example.data.CityWeather(20.0, 0, true, 45, 25.0, 12.0, 100L))
        )
        assertEquals(data, com.example.data.WeatherCache.fromJson(com.example.data.WeatherCache.toJson(data)))
        assertEquals(com.example.data.WeatherCacheData(), com.example.data.WeatherCache.fromJson("not json"))
        assertEquals(com.example.data.WeatherCacheData(), com.example.data.WeatherCache.fromJson(null))
    }

    @Test
    fun `weather requests carry only coordinates and names, and weather is off by default`() {
        val client = com.example.util.WeatherClient
        val points = listOf(com.example.data.GeoPoint(43.70643, -79.39864, "CA"), com.example.data.GeoPoint(51.5074, -0.1278, "GB"))
        val url = client.forecastUrl(points)
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=43.706,51.507&longitude=-79.399,-0.128"))
        assertTrue("timezone=auto" in url && "temperature_unit=celsius" in url)
        assertTrue(client.geocodeUrl("São Paulo").contains("S%C3%A3o+Paulo"))
        assertEquals("https://api.weather.gov/alerts/active?point=43.706,-79.399", client.nwsUrl(points[0]))
        assertTrue(client.ecUrl(points[0]).contains("bbox=-79.419,43.686,-79.379,43.726"))
        assertTrue(client.hasOfficialAlerts("US") && client.hasOfficialAlerts("CA") && !client.hasOfficialAlerts("GB"))
        // Every outgoing URL is made only from the pieces above: no place for a name, email, id or device value.
        listOf(url, client.nwsUrl(points[0]), client.ecUrl(points[0])).forEach { u ->
            assertTrue(u.startsWith("https://"))
            assertTrue("@" !in u && "android_id" !in u.lowercase() && "email" !in u.lowercase())
        }
        val defaults = ClockSettings()
        assertTrue(!defaults.weatherEnabled)
        assertEquals("C", defaults.weatherUnit)
    }

    @Test
    fun `small screens keep only the most important dock chips and show them in natural order`() {
        val p = com.example.ui.components.DockPriority
        // week chip, ambient, serious weather, countdown, routine
        val priorities = listOf(p.WEEK, p.AMBIENT, p.WEATHER_SERIOUS, p.COUNTDOWN, p.ROUTINE)
        assertEquals(listOf(2), com.example.ui.components.selectDockItems(priorities, 1)) // storm warning wins
        assertEquals(listOf(2, 4), com.example.ui.components.selectDockItems(priorities, 2)) // plus the due routine
        assertEquals(listOf(0, 1, 2, 3, 4), com.example.ui.components.selectDockItems(priorities, 6)) // all, unchanged order
        assertEquals(emptyList<Int>(), com.example.ui.components.selectDockItems(priorities, 0))
        // Ties keep their original order.
        assertEquals(listOf(0), com.example.ui.components.selectDockItems(listOf(p.COUNTDOWN, p.COUNTDOWN), 1))
    }

    @Test
    fun `dock weather notices put official alerts first, hide old data and only warn about rain for home`() {
        val zone = java.time.ZoneId.of("America/Toronto")
        val toronto = com.example.data.FriendClock(1, "Toronto", "America/Toronto", "Toronto, Canada")
        val london = com.example.data.FriendClock(2, "London", "Europe/London", "London, UK")
        val now = 10_000_000L
        fun cw(rain: Int?, max: Double = 15.0, fetched: Long = now) = com.example.data.CityWeather(10.0, 3, true, rain, max, 5.0, fetched)
        val ui = com.example.ui.components.WeatherUi(mapOf(1 to cw(rain = 30), 2 to cw(rain = 30)), "C")
        val official = listOf(com.example.data.WeatherNotice("Toronto", "Flood Warning", 2))
        val notices = com.example.ui.components.buildWeatherNotices(listOf(toronto, london), ui, official, now, zone)
        assertEquals(listOf("Flood Warning", "Rain in 30 min"), notices.map { it.text }) // London's rain-soon is not raised
        // Old readings (over 3h) are not turned into warnings or shown on cities.
        val stale = com.example.ui.components.WeatherUi(mapOf(1 to cw(rain = 30, fetched = 0L)), "C")
        assertEquals(emptyList<com.example.data.WeatherNotice>(), com.example.ui.components.buildWeatherNotices(listOf(toronto), stale, emptyList(), now + 4 * 3_600_000L, zone))
        assertEquals(null, stale.summaryFor(1, now + 4 * 3_600_000L))
        assertTrue(ui.summaryFor(1, now)!!.endsWith("10°"))
        // At most two chips, most serious first.
        val many = com.example.ui.components.buildWeatherNotices(listOf(toronto), ui, listOf(
            com.example.data.WeatherNotice("A", "x", 1), com.example.data.WeatherNotice("B", "y", 3), com.example.data.WeatherNotice("C", "z", 2)), now, zone)
        assertEquals(2, many.size)
        assertEquals(3, many[0].level)
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "missing fixture $name" }.use { it.readBytes().toString(Charsets.UTF_8) }

    @Test
    fun `real service responses are read correctly`() {
        // These files are genuine replies saved from the live services (geometry removed from the alert ones).
        val forecast = com.example.data.OpenMeteoParser.parse(fixture("openmeteo_two_places.json"), 123L)
        assertEquals(2, forecast.size)
        forecast.forEach { w ->
            assertTrue(w != null)
            assertTrue("temperature in a sane range", w!!.tempC in -60.0..60.0)
            assertTrue(w.code in 0..99)
            assertTrue(w.maxC24 >= w.minC24)
            assertEquals(123L, w.fetchedMillis)
        }
        val now = java.time.Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()
        val nws = com.example.data.AlertParsers.parseNws(fixture("nws_alerts_texas.json"), now)
        assertEquals(listOf("Coastal Flood Advisory", "High Surf Advisory", "Rip Current Statement"), nws.map { it.title })
        assertTrue(nws.all { it.level == 1 && it.expiresMillis != null })
        // The same alerts are gone once their end time has passed.
        assertEquals(emptyList<com.example.data.OfficialAlert>(), com.example.data.AlertParsers.parseNws(fixture("nws_alerts_texas.json"), java.time.Instant.parse("2026-11-19T00:00:00Z").toEpochMilli()))
        val ec = com.example.data.AlertParsers.parseEnvironmentCanada(fixture("ec_alerts_canada.json"), now)
        assertEquals(listOf("Freezing drizzle (advisory)"), ec.map { it.title }) // three identical area alerts merge into one
        assertEquals(1, ec.single().level)
        val berlin = com.example.util.WeatherClient.pickGeo(fixture("geocode_berlin.json"), "Europe/Berlin")!!
        assertEquals("DE", berlin.countryCode)
        assertEquals(52.5, berlin.lat, 0.5)
        assertEquals(13.4, berlin.lon, 0.5)
        assertEquals(null, com.example.util.WeatherClient.pickGeo("{}", "Europe/Berlin"))
        assertEquals(null, com.example.util.WeatherClient.pickGeo("garbage", "Europe/Berlin"))
    }

    /** Calls the real services through the app's own network code. Off by default; run with MERIDIAN_LIVE_WEATHER=1. */
    @Test
    fun `live weather services work end to end when asked`() {
        org.junit.Assume.assumeTrue(System.getenv("MERIDIAN_LIVE_WEATHER") == "1")
        kotlinx.coroutines.runBlocking {
            val client = com.example.util.WeatherClient
            val toronto = client.geocode("Toronto", "America/Toronto")!!
            assertEquals("CA", toronto.countryCode)
            val newYork = client.geocode("New York", "America/New_York")!!
            assertEquals("US", newYork.countryCode)
            val readings = com.example.data.OpenMeteoParser.parse(client.forecast(listOf(toronto, newYork)), System.currentTimeMillis())
            assertEquals(2, readings.size)
            readings.forEach { assertTrue(it != null && it.tempC in -60.0..60.0) }
            val now = System.currentTimeMillis()
            // Either may legitimately be empty on a quiet day; the point is that both calls succeed and parse.
            com.example.data.AlertParsers.parseEnvironmentCanada(client.officialAlerts(toronto)!!, now)
            com.example.data.AlertParsers.parseNws(client.officialAlerts(newYork)!!, now)
            println("LIVE OK: Toronto ${readings[0]!!.tempC}C code ${readings[0]!!.code}; New York ${readings[1]!!.tempC}C code ${readings[1]!!.code}")
        }
    }

    @Test
    fun `custom theme studio resolves independent background, text, and number colors plus hsv hex conversion`() {
        assertEquals(9, AllPresetThemes.size)
        assertTrue(AllPresetThemes.any { it.name == "Aurora" })
        assertTrue(AllPresetThemes.any { it.name == "Field Gold" })

        val customSettings = ClockSettings(
            themeName = "Custom",
            customBgColorArgb = 0xFF101828L,
            customTextColorArgb = 0xFFF1F5F9L,
            customNumberColorArgb = 0xFF00F5D4L
        )
        val resolved = resolveVisualTheme(customSettings)
        assertEquals(Color(0xFF101828), resolved.bgStart)
        assertEquals(Color(0xFFF1F5F9), resolved.textPrimary)
        assertEquals(Color(0xFF00F5D4), resolved.numberColor)

        val parsed = parseHexColorOrNull("#FF8800")
        assertNotNull(parsed)
        assertEquals("#FF8800", colorToHex(parsed!!))
        val hsv = colorToHsv(parsed)
        val roundTrip = hsvToColor(hsv[0], hsv[1], hsv[2])
        assertEquals("#FF8800", colorToHex(roundTrip))
    }

    @Test
    fun `repository seeds starter cities once without deleting user cities and home widget builds cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ClockRepository(db.clockDao())

        repo.checkAndPrepopulate()
        val initialCities = repo.getFriendClocksDirect()
        assertTrue(initialCities.size >= 4)

        // Add an extra city and verify subsequent startup checks never delete London/Tokyo/New York
        repo.insertFriend(FriendClock(friendName = "Sydney", cityLabel = "Sydney", timezoneId = "Australia/Sydney"))
        val countBefore = repo.getFriendClocksDirect().size
        repo.checkAndPrepopulate()
        val countAfter = repo.getFriendClocksDirect().size
        assertEquals(countBefore, countAfter)

        // Verify Home Screen Widget RemoteViews builds cleanly with pinned cities & custom theme
        val remoteViews = WorldClockAppWidgetProvider.buildWidgetRemoteViews(
            context = context,
            appWidgetId = 42,
            settings = ClockSettings(themeName = "Custom"),
            sortedClocks = repo.getFriendClocksDirect()
        )
        assertNotNull(remoteViews)
        db.close()
    }

    @Test
    fun `scheduled quiet hours with custom time window and selectable days of week function accurately`() {
        // Monday 22:30 (Day 1)
        val mondayNight = ZonedDateTime.of(2026, 11, 9, 22, 30, 0, 0, ZoneId.of("America/New_York"))
        // Sunday 22:30 (Day 7)
        val sundayNight = ZonedDateTime.of(2026, 11, 8, 22, 30, 0, 0, ZoneId.of("America/New_York"))

        // Overnight 21:00 to 08:00, weekdays only (1,2,3,4,5)
        val isQuietMonday = TimeAndAlertUtils.isWithinQuietHours(
            now = mondayNight,
            enabled = true,
            startHour = 21,
            startMinute = 0,
            endHour = 8,
            endMinute = 0,
            activeDaysOfWeek = "1,2,3,4,5"
        )
        assertTrue(isQuietMonday)

        val isQuietSunday = TimeAndAlertUtils.isWithinQuietHours(
            now = sundayNight,
            enabled = true,
            startHour = 21,
            startMinute = 0,
            endHour = 8,
            endMinute = 0,
            activeDaysOfWeek = "1,2,3,4,5"
        )
        // Sunday is not in 1..5, so quiet hours is false
        org.junit.Assert.assertFalse(isQuietSunday)
    }

    @Test
    fun `crash report manager gathers device model, OS version, screen size, and stack traces into JSON without PII`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CrashReportManager.clearCrashReport(context)

        val testException = IllegalStateException("Test crash for diagnostic report: /data/user/0/com.example/files/private_info")
        val json = CrashReportManager.generateCrashReportJson(testException, "MainThread")

        // 1. Validates valid JSON structure & metadata
        assertEquals("1.0", json.getString("reportSchemaVersion"))
        assertEquals("CRASH_INCIDENT", json.getString("reportType"))
        assertTrue(json.getString("anonymousSessionId").startsWith("ANON-"))

        // 2. Clear Privacy Notice Disclosure
        val privacyNotice = json.getString("privacyNotice")
        assertTrue(privacyNotice.contains("Privacy Notice & Disclosure"))
        assertTrue(privacyNotice.contains("ONLY anonymous technical data"))
        assertTrue(privacyNotice.contains("NOT contain any Personally Identifiable Information (PII)"))
        assertTrue(json.getBoolean("piiExcluded"))
        assertEquals("NO_PII_COLLECTED_OR_STORED", json.getString("piiStatus"))

        // 3. Device environment specs: model, OS version, screen size
        val deviceEnv = json.getJSONObject("deviceEnvironment")
        assertTrue(deviceEnv.has("deviceModel"))
        assertTrue(deviceEnv.has("androidOsVersion"))
        assertTrue(deviceEnv.getString("androidOsVersion").startsWith("Android"))
        assertTrue(deviceEnv.has("screenSize"))
        assertTrue(deviceEnv.getString("screenSize").contains("x"))
        assertTrue(deviceEnv.has("screenDensityDpi"))

        // 4. Stack trace gathered and sanitized (No PII / paths redacted)
        val errorDetails = json.getJSONObject("errorDetails")
        assertEquals("java.lang.IllegalStateException", errorDetails.getString("exceptionType"))
        val sanitizedTrace = errorDetails.getString("stackTrace")
        assertTrue(sanitizedTrace.contains("IllegalStateException"))
        // Check that /data/user/0/com.example was sanitized
        org.junit.Assert.assertFalse(sanitizedTrace.contains("/data/user/0/com.example"))
        assertTrue(sanitizedTrace.contains("[APP_PRIVATE_DIR]"))

        // 5. Test recordCrash, file persistence, retrieval, and clear
        CrashReportManager.recordCrash(Thread.currentThread(), testException, context)
        assertTrue(CrashReportManager.hasPendingCrash(context))
        val savedReport = CrashReportManager.getLastCrashReport(context)
        assertNotNull(savedReport)
        assertTrue(savedReport!!.contains("deviceModel"))
        assertTrue(savedReport.contains("androidOsVersion"))
        assertTrue(savedReport.contains("screenSize"))
        assertTrue(savedReport.contains("PRIVACY NOTICE & DISCLOSURE".lowercase(java.util.Locale.ROOT)) || savedReport.contains("Privacy Notice & Disclosure"))

        CrashReportManager.clearCrashReport(context)
        org.junit.Assert.assertFalse(CrashReportManager.hasPendingCrash(context))
        assertNull(CrashReportManager.getLastCrashReport(context))
    }

    @Test
    fun `crash report manager maintains rolling history of exactly 5 newest crash logs in FIFO order`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CrashReportManager.clearAllCrashReports(context)
        assertEquals(0, CrashReportManager.getRecentCrashReports(context).size)

        // Record 6 incidents in sequence
        for (i in 1..6) {
            val ex = RuntimeException("Crash incident number $i")
            CrashReportManager.recordCrash(Thread.currentThread(), ex, context)
        }

        val reports = CrashReportManager.getRecentCrashReports(context)
        // Must be capped at exactly MAX_STORED_CRASHES = 5
        assertEquals(5, reports.size)

        // Index 0 must be the newest incident (#6)
        val newestError = reports[0].getJSONObject("errorDetails").getString("exceptionMessage")
        assertTrue(newestError.contains("Crash incident number 6"))

        // Index 4 must be incident #2 (incident #1 dropped due to 5-item FIFO limit)
        val oldestRetained = reports[4].getJSONObject("errorDetails").getString("exceptionMessage")
        assertTrue(oldestRetained.contains("Crash incident number 2"))

        // Clear all wipes history
        CrashReportManager.clearAllCrashReports(context)
        assertEquals(0, CrashReportManager.getRecentCrashReports(context).size)
    }

    @Test
    fun `simulated diagnostic incident records safely without PII and builds share bundle`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CrashReportManager.clearAllCrashReports(context)

        CrashReportManager.recordSimulatedIncident(context, "Test UI Incident")
        val reports = CrashReportManager.getRecentCrashReports(context)
        assertEquals(1, reports.size)

        val report = reports[0]
        assertTrue(report.getString("privacyNotice").contains("Personally Identifiable Information"))
        assertTrue(report.getJSONObject("deviceEnvironment").has("screenSize"))

        val shareIntent = CrashReportManager.buildShareAllIntent(context)
        assertNotNull(shareIntent)
        assertEquals("application/json", shareIntent.type)
        assertTrue(shareIntent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.contains("Test UI Incident") == true)

        CrashReportManager.clearAllCrashReports(context)
    }

    @Test
    fun `samsung galaxy tab a7 screen dimensions classify as expanded widescreen tablet posture`() {
        // Samsung Galaxy Tab A7 (10.4 inch, 2000x1200 px @ 224 dpi)
        // In DP: width = 2000 / 1.4 = ~1428dp in landscape, 857dp in portrait
        val landscapeTabPosture = classifyScreenPosture(maxWidth = 1428.dp, maxHeight = 857.dp)
        assertEquals(AdaptiveScreenPosture.EXPANDED, landscapeTabPosture)

        val portraitTabPosture = classifyScreenPosture(maxWidth = 857.dp, maxHeight = 1428.dp)
        assertEquals(AdaptiveScreenPosture.EXPANDED, portraitTabPosture)
    }

    @Test
    fun `requested cities and countries Toronto Bangalore Germany Qatar Kuwait Saudi Arabia are present and searchable`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ClockRepository(db.clockDao())
        repo.checkAndPrepopulate()

        val clocks = repo.getFriendClocksDirect()
        val friendNames = clocks.map { it.friendName }
        val cityLabels = clocks.map { it.cityLabel }

        assertTrue(friendNames.contains("Toronto") || cityLabels.any { it.contains("Toronto") })
        assertTrue(friendNames.contains("Bangalore") || cityLabels.any { it.contains("Bangalore") })
        assertTrue(friendNames.contains("Germany") || cityLabels.any { it.contains("Germany") })
        assertTrue(friendNames.contains("Qatar") || cityLabels.any { it.contains("Qatar") })
        assertTrue(friendNames.contains("Kuwait") || cityLabels.any { it.contains("Kuwait") })
        assertTrue(friendNames.contains("Saudi Arabia") || cityLabels.any { it.contains("Saudi Arabia") })

        // Validate country and city search query matching
        assertTrue(TimeAndAlertUtils.matchesTimezoneQuery("America/Toronto", "Toronto"))
        assertTrue(TimeAndAlertUtils.matchesTimezoneQuery("Asia/Kolkata", "Bangalore"))
        assertTrue(TimeAndAlertUtils.matchesTimezoneQuery("Europe/Berlin", "Germany"))
        assertTrue(TimeAndAlertUtils.matchesTimezoneQuery("Asia/Qatar", "Qatar"))
        assertTrue(TimeAndAlertUtils.matchesTimezoneQuery("Asia/Kuwait", "Kuwait"))
        assertTrue(TimeAndAlertUtils.matchesTimezoneQuery("Asia/Riyadh", "Saudi Arabia"))

        db.close()
    }

    @Test
    fun `realtime clock sync manager handles DST transitions and regional constant offsets accurately`() {
        val summerInstant = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant()
        val winterInstant = ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneId.of("UTC")).toInstant()

        val summerStatuses = RealtimeClockSyncManager.getSpecifiedGlobalLocationsSyncStatus(summerInstant)
            .associateBy { it.cityName }
        val winterStatuses = RealtimeClockSyncManager.getSpecifiedGlobalLocationsSyncStatus(winterInstant)
            .associateBy { it.cityName }

        // Toronto & Germany observe DST in summer and standard time in winter
        val torontoSummer = summerStatuses.getValue("Toronto")
        val torontoWinter = winterStatuses.getValue("Toronto")
        assertTrue(torontoSummer.isDaylightSavings)
        assertEquals("UTC-4", torontoSummer.utcOffsetString)
        assertFalse(torontoWinter.isDaylightSavings)
        assertEquals("UTC-5", torontoWinter.utcOffsetString)

        val germanySummer = summerStatuses.getValue("Germany")
        val germanyWinter = winterStatuses.getValue("Germany")
        assertTrue(germanySummer.isDaylightSavings)
        assertEquals("UTC+2", germanySummer.utcOffsetString)
        assertFalse(germanyWinter.isDaylightSavings)
        assertEquals("UTC+1", germanyWinter.utcOffsetString)

        // Bangalore, Qatar, Kuwait, and Saudi Arabia maintain constant offsets year-round
        val bangaloreSummer = summerStatuses.getValue("Bangalore")
        val bangaloreWinter = winterStatuses.getValue("Bangalore")
        assertFalse(bangaloreSummer.isDaylightSavings)
        assertFalse(bangaloreWinter.isDaylightSavings)
        assertEquals("UTC+5:30", bangaloreSummer.utcOffsetString)

        listOf("Qatar", "Kuwait", "Saudi Arabia").forEach { region ->
            val summer = summerStatuses.getValue(region)
            val winter = winterStatuses.getValue(region)
            assertFalse(summer.isDaylightSavings)
            assertFalse(winter.isDaylightSavings)
            assertEquals("UTC+3", summer.utcOffsetString)
            assertEquals("UTC+3", winter.utcOffsetString)
        }
    }

    @Test
    fun `realtime clock sync manager registers system receiver and detects clock jumps`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        RealtimeClockSyncManager.initialize(context)
        assertTrue(RealtimeClockSyncManager.syncState.value.isReceiverRegistered)

        val baseWall = System.currentTimeMillis()
        val baseElapsed = android.os.SystemClock.elapsedRealtime()
        RealtimeClockSyncManager.verifyClockContinuity(baseWall, baseElapsed)

        // Normal 250ms tick -> no jump
        assertFalse(RealtimeClockSyncManager.verifyClockContinuity(baseWall + 250L, baseElapsed + 250L))

        // Simulated manual clock / DST 1-hour jump -> detected and resynced
        assertTrue(RealtimeClockSyncManager.verifyClockContinuity(baseWall + 3_600_250L, baseElapsed + 500L))
        assertTrue(RealtimeClockSyncManager.syncState.value.clockJumpDetected)
        assertTrue(kotlin.math.abs(RealtimeClockSyncManager.syncState.value.lastDetectedDriftMillis) > 2500L)

        RealtimeClockSyncManager.unregister(context)
        RealtimeClockSyncManager.resyncNow("TEST_CLEANUP")
        assertFalse(RealtimeClockSyncManager.syncState.value.isReceiverRegistered)
    }

    @Test
    fun `previewVisualFlash increments pulseTriggerCount and activates flashing state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ClockRepository(db.clockDao())
        val vm = com.example.ui.ClockViewModel(repo, context)

        assertEquals(0L, vm.pulseTriggerCount.value)
        vm.previewVisualFlash()
        assertEquals(1L, vm.pulseTriggerCount.value)
        vm.previewVisualFlash()
        assertEquals(2L, vm.pulseTriggerCount.value)

        db.close()
    }

    @Test
    fun `circadian time-of-day color shift produces cooler morning tones and warmer evening tones`() {
        val zone = ZoneId.of("America/Toronto")
        val morningTime = ZonedDateTime.of(2026, 11, 13, 8, 30, 0, 0, zone)
        val middayTime = ZonedDateTime.of(2026, 11, 13, 13, 30, 0, 0, zone)
        val eveningTime = ZonedDateTime.of(2026, 11, 13, 19, 30, 0, 0, zone)
        val nightTime = ZonedDateTime.of(2026, 11, 13, 23, 0, 0, 0, zone)

        val morningProfile = com.example.ui.theme.computeCircadianProfile(morningTime)
        val middayProfile = com.example.ui.theme.computeCircadianProfile(middayTime)
        val eveningProfile = com.example.ui.theme.computeCircadianProfile(eveningTime)
        val nightProfile = com.example.ui.theme.computeCircadianProfile(nightTime)

        assertEquals(com.example.ui.theme.CircadianPhase.COOL_MORNING, morningProfile.phase)
        assertTrue(morningProfile.warmthFactor < -0.5f)

        assertEquals(com.example.ui.theme.CircadianPhase.DAYLIGHT_NEUTRAL, middayProfile.phase)
        assertEquals(0f, middayProfile.warmthFactor, 0.001f)

        assertEquals(com.example.ui.theme.CircadianPhase.WARM_EVENING, eveningProfile.phase)
        assertTrue(eveningProfile.warmthFactor > 0.5f)

        assertEquals(com.example.ui.theme.CircadianPhase.SOOTHING_NIGHT, nightProfile.phase)
        assertTrue(nightProfile.warmthFactor > 0.8f)

        val settings = ClockSettings(themeName = "Midnight Cosmos", circadianShiftEnabled = true)
        val morningTheme = resolveVisualTheme(settings, localTime = morningTime)
        val middayTheme = resolveVisualTheme(settings, localTime = middayTime)
        val eveningTheme = resolveVisualTheme(settings, localTime = eveningTime)

        // Midday returns base theme untouched
        assertEquals(com.example.ui.theme.CosmosMidnightTheme.bgStart, middayTheme.bgStart)

        // Pitch-black backgrounds remain locked while accent hue shifts cool-morning vs warm-evening
        assertEquals(com.example.ui.theme.CosmosMidnightTheme.bgStart, morningTheme.bgStart)
        assertEquals(com.example.ui.theme.CosmosMidnightTheme.bgStart, eveningTheme.bgStart)
        assertTrue(morningTheme.accentColor.blue > morningTheme.accentColor.red)
        assertTrue(eveningTheme.accentColor.red > eveningTheme.accentColor.blue)

        // Obsidian OLED stays strictly pure black-and-white monochrome (zero green tint) and tints ONLY circadianCueColor
        val oledTheme = com.example.ui.theme.ObsidianOledTheme
        assertEquals(Color(0xFF000000), oledTheme.bgStart)
        assertEquals(Color(0xFFFFFFFF), oledTheme.accentColor)
        assertEquals(oledTheme.accentColor.red, oledTheme.accentColor.green, 0.001f)
        assertEquals(oledTheme.accentColor.green, oledTheme.accentColor.blue, 0.001f)

        val oledSettings = ClockSettings(themeName = "Obsidian OLED", circadianShiftEnabled = true)
        val oledMorning = resolveVisualTheme(oledSettings, localTime = morningTime)
        val oledEvening = resolveVisualTheme(oledSettings, localTime = eveningTime)
        assertEquals(Color(0xFF000000), oledMorning.bgStart)
        assertEquals(Color(0xFF000000), oledEvening.bgStart)
        assertEquals(Color(0xFFFFFFFF), oledMorning.accentColor)
        assertEquals(Color(0xFFFFFFFF), oledEvening.accentColor)
        assertEquals(Color(0xFFFFFFFF), oledMorning.numberColor)
        assertEquals(Color(0xFFFFFFFF), oledEvening.numberColor)
        assertTrue(oledMorning.circadianCueColor.blue > oledMorning.circadianCueColor.red)
        assertTrue(oledEvening.circadianCueColor.red > oledEvening.circadianCueColor.blue)

        // Light theme remains bright and readable in both morning and evening
        val lightSettings = ClockSettings(themeName = "Nordic Light", circadianShiftEnabled = true)
        val lightEvening = resolveVisualTheme(lightSettings, localTime = eveningTime)
        assertTrue(lightEvening.bgStart.red > 0.9f)
        assertTrue(lightEvening.textPrimary.red < 0.3f)

        // Disabled circadian shift preserves exact base theme colors at all hours
        val disabledSettings = ClockSettings(themeName = "Midnight Cosmos", circadianShiftEnabled = false)
        assertEquals(
            com.example.ui.theme.CosmosMidnightTheme.bgStart,
            resolveVisualTheme(disabledSettings, localTime = eveningTime).bgStart
        )
    }

    @Test
    fun `battery optimization settings uses zero-permission App Info and Battery List intents with safe fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val primaryIntents = com.example.ui.components.buildBatterySettingsIntents(context.packageName)
        assertEquals(2, primaryIntents.size)
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, primaryIntents[0].action)
        assertEquals("package:${context.packageName}", primaryIntents[0].dataString)
        assertEquals(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, primaryIntents[1].action)
        assertTrue(primaryIntents.none { it.action == android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS })

        val listIntents = com.example.ui.components.buildBatteryListSettingsIntents(context.packageName)
        assertEquals(2, listIntents.size)
        assertEquals(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, listIntents[0].action)
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, listIntents[1].action)

        // When no system Settings activity resolves in the test environment, returns false safely without throwing
        assertFalse(com.example.ui.components.openBatteryOptimizationSettings(context))
        assertFalse(com.example.ui.components.openSystemBatteryListSettings(context))

        // Register a mock Settings activity handler for ACTION_APPLICATION_DETAILS_SETTINGS and verify launch succeeds
        val pm = org.robolectric.Shadows.shadowOf(context.packageManager)
        val settingsComponent = android.content.ComponentName("com.android.settings", "com.android.settings.applications.InstalledAppDetails")
        pm.addActivityIfNotPresent(settingsComponent)
        pm.addIntentFilterForActivity(
            settingsComponent,
            android.content.IntentFilter(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                addCategory(android.content.Intent.CATEGORY_DEFAULT)
                addDataScheme("package")
            }
        )

        assertTrue(com.example.ui.components.openBatteryOptimizationSettings(context))
    }

    @Test
    fun `eye break reminder supports configurable 20m or 50m intervals, exercise rotation, snooze, and backup`() = runBlocking {
        assertTrue(TimeAndAlertUtils.EyeExercises.size >= 5)
        val baseEpoch = 1_700_000_000_000L
        assertEquals(baseEpoch + 20 * 60_000L, TimeAndAlertUtils.computeNextEyeBreakEpochMillis(baseEpoch, 20))
        assertEquals(baseEpoch + 50 * 60_000L, TimeAndAlertUtils.computeNextEyeBreakEpochMillis(baseEpoch, 50))
        assertEquals(baseEpoch + 180 * 60_000L, TimeAndAlertUtils.computeNextEyeBreakEpochMillis(baseEpoch, 999))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = ClockRepository(db.clockDao())
        repo.checkAndPrepopulate()

        val vm = com.example.ui.ClockViewModel(repo, context)
        vm.toggleEyeBreakEnabled(true)
        vm.updateEyeBreakInterval(50)
        kotlinx.coroutines.delay(150)

        assertTrue(vm.remainingEyeBreakSeconds.value in 2990..3000)

        assertNull(vm.activeEyeBreakPrompt.value)
        vm.triggerEyeBreakNow()
        val firstPrompt = vm.activeEyeBreakPrompt.value
        assertNotNull(firstPrompt)

        vm.nextEyeBreakExercise()
        val secondPrompt = vm.activeEyeBreakPrompt.value
        assertNotNull(secondPrompt)
        assertTrue(secondPrompt!!.exercise.id != firstPrompt!!.exercise.id)

        vm.dismissEyeBreakPrompt(snoozeMinutes = 5)
        assertNull(vm.activeEyeBreakPrompt.value)
        assertTrue(vm.remainingEyeBreakSeconds.value in 295..300)

        val backupJson = vm.exportBackup()
        assertTrue(backupJson.contains("\"eyeBreakIntervalMinutes\""))

        db.close()
    }
}
