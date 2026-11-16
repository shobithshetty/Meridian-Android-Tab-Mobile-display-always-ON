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
    val nightDimMode: String = "AUTO" // "OFF", "AUTO" (dims 10pm-6am), "BEDSIDE" (always very dark)
)
