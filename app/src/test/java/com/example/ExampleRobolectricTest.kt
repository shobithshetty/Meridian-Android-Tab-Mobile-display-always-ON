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
    fun `custom theme studio resolves independent background, text, and number colors plus hsv hex conversion`() {
        assertEquals(7, AllPresetThemes.size)

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
}
