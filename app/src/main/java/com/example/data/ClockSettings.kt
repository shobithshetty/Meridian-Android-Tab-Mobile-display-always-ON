package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clock_settings")
data class ClockSettings(
    @PrimaryKey val id: Int = 1,
    val chimeIntervalMinutes: Int = 15, // Precision 15..20 minute focus interval (also supports legacy values)
    val selectedSoundName: String = "Zen Bell", // Built-in synth chime name
    val soundSourceType: String = "BUILTIN", // "BUILTIN", "RINGTONE", "CUSTOM_FILE"
    val customSoundUri: String = "", // Persistable SAF URI or Ringtone URI
    val customSoundLabel: String = "", // Display name of chosen file or system tone
    val chimeEnabled: Boolean = true,
    val flashScreenEnabled: Boolean = true,
    val alignToTopHour: Boolean = true, // Aligns to hour boundaries when 60 % interval == 0 (15m, 20m)
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartHour: Int = 22,
    val quietHoursStartMinute: Int = 0,
    val quietHoursEndHour: Int = 8,
    val quietHoursEndMinute: Int = 0,
    val quietHoursDaysOfWeek: String = "ALL", // "ALL" or comma-separated day integers 1..7 (1=Monday, 7=Sunday)
    val showSeconds: Boolean = true,
    val digital24HourFormat: Boolean = false,
    val themeName: String = "Aurora", // 8 presets + "Custom"
    val customBgColorArgb: Long = 0xFF070B14L,
    val customTextColorArgb: Long = 0xFFE2E8F0L,
    val customNumberColorArgb: Long = 0xFF38BDF8L,
    val showWorldClockBesideMain: Boolean = true,
    val worldClockWidgetLayout: String = "SIDE_BY_SIDE", // "SIDE_BY_SIDE", "COMPACT_STRIP", "GRID"
    val worldClockShowOffset: Boolean = true,
    val worldClockShowDate: Boolean = true,
    val worldClockShowSeconds: Boolean = false,
    val homeWidgetBgAlpha: Int = 230, // 80..255 opacity for Android Home Screen Widget
    val orientationMode: Int = 0, // 0 = Auto/Sensor, 1 = Force Portrait, 2 = Force Landscape
    val autoAmbientSensorEnabled: Boolean = false, // Disabled by default so user theme choices stay steady unless opted in
    val circadianShiftEnabled: Boolean = true, // Smoothly shifts palette cooler in the morning and warmer in the evening
    val currentFocusText: String = "Stay in the flow, one thing at a time.",
    val showBreathingGuide: Boolean = false,
    val currentAffirmationIndex: Int = 0,
    val clockSortOrder: String = "DEFAULT", // "DEFAULT", "NAME", "OFFSET"
    val eyeBreakEnabled: Boolean = false,
    val eyeBreakIntervalMinutes: Int = 20, // Configurable 1..180 minute eye break reminder (e.g. 20m, 50m)
    val layoutStyle: String = "BOLD", // "BOLD", "CALM", "DASHBOARD", "CLASSIC" - independent of colour theme
    val rhythmStripEnabled: Boolean = true, // thin 24h day bar along the bottom of every layout
    val backdropPattern: String = "NONE", // "NONE" or "CAMO" - faint pattern over any theme/layout
    val nightDimMode: String = "AUTO", // "OFF", "AUTO" (dims 10pm-6am), "SUN" (sunset to sunrise), "BEDSIDE" (always very dark)
    val weatherEnabled: Boolean = false, // off by default: turning it on is what lets the app use the internet
    val weatherUnit: String = "C", // "C" or "F"
    val weatherAlertsEnabled: Boolean = true, // official alerts (US and Canada), only fetched while weather is on
    val alarmStyle: String = "GENTLE", // "GENTLE" = fades in and repeats until dismissed/snoozed; "SINGLE" = one chime
    val autoBackupUri: String = "", // persisted folder (SAF tree) for daily backups; "" = off. Never exported in a backup.
    val lastAutoBackupEpochDay: Long = -1L,
    val countdownsJson: String = "[]", // see PlannerCodec
    val routinesJson: String = "[]", // see PlannerCodec
    val holidayRegion: String = "AUTO", // "AUTO", "US", "CA", "GB", "IN" or "OFF"
    val showDayInfo: Boolean = true, // week number + holiday chip
    val transitionWarningsEnabled: Boolean = true, // "10 / 5 min left" heads-up before a focus block or alarm ends
    val keepAwakeMode: String = "ALWAYS", // "ALWAYS" keeps the screen on; "CHARGING" only while plugged in
    val autoStartOnBoot: Boolean = false, // reopen Meridian after the device restarts
    val hasHomeLocation: Boolean = false, // true once a coarse location was saved for sunrise/sunset
    val homeLatitude: Double = 0.0,
    val homeLongitude: Double = 0.0
)
