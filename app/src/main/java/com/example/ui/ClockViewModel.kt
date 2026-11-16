package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioSynthEngine
import com.example.data.AlarmItem
import com.example.data.ClockRepository
import com.example.data.ClockSettings
import com.example.data.FriendClock
import com.example.util.LocationSyncStatus
import com.example.util.RealtimeClockSyncManager
import com.example.util.SystemClockSyncState
import com.example.util.TimeAndAlertUtils
import com.example.widget.WorldClockAppWidgetProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime

data class EyeBreakPromptState(
    val exercise: TimeAndAlertUtils.EyeExercise,
    val exerciseIndex: Int,
    val triggeredAtMillis: Long
)

class ClockViewModel(
    private val repository: ClockRepository,
    context: Context
) : ViewModel() {

    private val context: Context = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        if (context.attributionTag == null) {
            context.createAttributionContext("default")
        } else {
            context
        }
    } else {
        context
    }

    private val _currentLocalTime = MutableStateFlow(ZonedDateTime.now())
    val currentLocalTime: StateFlow<ZonedDateTime> = _currentLocalTime.asStateFlow()

    private val _isFlashing = MutableStateFlow(false)
    val isFlashing: StateFlow<Boolean> = _isFlashing.asStateFlow()

    private val _pulseTriggerCount = MutableStateFlow(0L)
    val pulseTriggerCount: StateFlow<Long> = _pulseTriggerCount.asStateFlow()

    private val _circadianPreviewHour = MutableStateFlow<Int?>(null)
    val circadianPreviewHour: StateFlow<Int?> = _circadianPreviewHour.asStateFlow()

    private var flashJob: Job? = null

    val settings: StateFlow<ClockSettings> = repository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ClockSettings()
        )

    val friendClocks: StateFlow<List<FriendClock>> = repository.allFriendClocks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val alarms: StateFlow<List<AlarmItem>> = repository.allAlarms
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var nextChimeEpochMillis: Long = TimeAndAlertUtils.computeNextChimeEpochMillis(
        ZonedDateTime.now(),
        15,
        true
    )

    private val _remainingChimeSeconds = MutableStateFlow(
        TimeAndAlertUtils.computeRemainingChimeSeconds(
            System.currentTimeMillis(),
            nextChimeEpochMillis
        )
    )
    val remainingChimeSeconds: StateFlow<Int> = _remainingChimeSeconds.asStateFlow()

    private var nextEyeBreakEpochMillis: Long = TimeAndAlertUtils.computeNextEyeBreakEpochMillis(
        System.currentTimeMillis(),
        20
    )

    private val _remainingEyeBreakSeconds = MutableStateFlow(
        TimeAndAlertUtils.computeRemainingChimeSeconds(
            System.currentTimeMillis(),
            nextEyeBreakEpochMillis
        )
    )
    val remainingEyeBreakSeconds: StateFlow<Int> = _remainingEyeBreakSeconds.asStateFlow()

    private val _activeEyeBreakPrompt = MutableStateFlow<EyeBreakPromptState?>(null)
    val activeEyeBreakPrompt: StateFlow<EyeBreakPromptState?> = _activeEyeBreakPrompt.asStateFlow()

    private var eyeExerciseCursor = 0

    private var tickJob: Job? = null
    private val lastFiredAlarms = mutableMapOf<Int, Long>()
    private var isTimerRunning = true

    private val isRunningTests: Boolean by lazy {
        try {
            Class.forName("org.robolectric.Robolectric")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    private val cachedAvailableZones: List<String> by lazy {
        val curated = listOf(
            "UTC",
            "America/New_York",
            "America/Chicago",
            "America/Denver",
            "America/Los_Angeles",
            "America/Toronto",
            "America/Vancouver",
            "America/Edmonton",
            "America/St_Johns",
            "America/Mexico_City",
            "America/Sao_Paulo",
            "Europe/London",
            "Europe/Paris",
            "Europe/Berlin",
            "Europe/Madrid",
            "Europe/Rome",
            "Europe/Athens",
            "Europe/Istanbul",
            "Asia/Dubai",
            "Asia/Riyadh",
            "Asia/Qatar",
            "Asia/Kuwait",
            "Asia/Kolkata",
            "Asia/Kathmandu",
            "Asia/Bangkok",
            "Asia/Singapore",
            "Asia/Shanghai",
            "Asia/Hong_Kong",
            "Asia/Tokyo",
            "Asia/Seoul",
            "Australia/Adelaide",
            "Australia/Sydney",
            "Australia/Melbourne",
            "Pacific/Auckland",
            "Pacific/Honolulu"
        )
        val allSystemZones = ZoneId.getAvailableZoneIds()
            .filter { id ->
                id == "UTC" || (id.contains('/') &&
                    !id.startsWith("SystemV/") &&
                    !id.startsWith("Etc/") &&
                    !id.startsWith("US/") &&
                    !id.startsWith("Canada/") &&
                    !id.startsWith("Brazil/") &&
                    !id.startsWith("Mexico/"))
            }
        (curated + allSystemZones).distinct().sorted()
    }

    init {
        viewModelScope.launch {
            repository.checkAndPrepopulate()
            val initialSettings = repository.getSettingsDirect()
            recalculateNextChimeSchedule(initialSettings)
            recalculateNextEyeBreakSchedule(initialSettings)
        }

        // Observe interval / alignment / chimeEnabled changes to keep countdown ring immediately synced
        viewModelScope.launch {
            settings
                .map { Triple(it.chimeIntervalMinutes, it.alignToTopHour, it.chimeEnabled) }
                .distinctUntilChanged()
                .collect {
                    recalculateNextChimeSchedule(settings.value)
                }
        }

        // Observe eye break toggle & interval changes
        viewModelScope.launch {
            settings
                .map { it.eyeBreakEnabled to it.eyeBreakIntervalMinutes }
                .distinctUntilChanged()
                .collect {
                    recalculateNextEyeBreakSchedule(settings.value)
                }
        }

        if (!isRunningTests) {
            RealtimeClockSyncManager.initialize(context)
            startClockTicker()
        }

        // Listen for system clock, timezone, DST changes, and clock jumps
        viewModelScope.launch {
            RealtimeClockSyncManager.syncEvents.collect {
                val activeSettings = settings.value
                recalculateNextChimeSchedule(activeSettings)
                _currentLocalTime.value = ZonedDateTime.now()
                if (!isRunningTests) {
                    WorldClockAppWidgetProvider.updateAllWidgets(context)
                }
            }
        }
    }

    val clockSyncState: StateFlow<SystemClockSyncState> = RealtimeClockSyncManager.syncState

    fun forceClockSync() {
        RealtimeClockSyncManager.resyncNow("USER_MANUAL_FORCE_SYNC")
        recalculateNextChimeSchedule(settings.value)
        _currentLocalTime.value = ZonedDateTime.now()
        if (!isRunningTests) {
            WorldClockAppWidgetProvider.updateAllWidgets(context)
        }
    }

    fun getGlobalLocationsSyncStatus(): List<LocationSyncStatus> {
        return RealtimeClockSyncManager.getSpecifiedGlobalLocationsSyncStatus()
    }

    private fun recalculateNextChimeSchedule(activeSettings: ClockSettings) {
        val now = ZonedDateTime.now()
        nextChimeEpochMillis = TimeAndAlertUtils.computeNextChimeEpochMillis(
            now = now,
            intervalMinutes = activeSettings.chimeIntervalMinutes,
            alignToTopHour = activeSettings.alignToTopHour
        )
        _remainingChimeSeconds.value = TimeAndAlertUtils.computeRemainingChimeSeconds(
            now.toInstant().toEpochMilli(),
            nextChimeEpochMillis
        )
    }

    private fun recalculateNextEyeBreakSchedule(activeSettings: ClockSettings) {
        val nowMillis = System.currentTimeMillis()
        nextEyeBreakEpochMillis = TimeAndAlertUtils.computeNextEyeBreakEpochMillis(
            nowMillis = nowMillis,
            intervalMinutes = activeSettings.eyeBreakIntervalMinutes
        )
        _remainingEyeBreakSeconds.value = TimeAndAlertUtils.computeRemainingChimeSeconds(
            nowMillis,
            nextEyeBreakEpochMillis
        )
    }

    private fun startClockTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isTimerRunning) {
                val now = ZonedDateTime.now()
                _currentLocalTime.value = now

                val clockJumped = RealtimeClockSyncManager.verifyClockContinuity()
                if (clockJumped) {
                    recalculateNextChimeSchedule(settings.value)
                    recalculateNextEyeBreakSchedule(settings.value)
                }

                checkAndTriggerChime(now)
                checkAndTriggerEyeBreak(now)
                checkAndTriggerAlarms(now)

                if (isRunningTests) {
                    delay(10_000)
                } else {
                    delay(250)
                }
            }
        }
    }

    private fun checkAndTriggerAlarms(nowLocal: ZonedDateTime) {
        val currentAlarms = alarms.value
        if (currentAlarms.isEmpty()) return

        val currentTimeMillis = System.currentTimeMillis()

        for (alarm in currentAlarms) {
            if (!alarm.isEnabled) continue

            val alarmZone = TimeAndAlertUtils.safeZoneId(alarm.timezoneId)
            val nowInAlarmZone = nowLocal.withZoneSameInstant(alarmZone)

            if (nowInAlarmZone.hour == alarm.hour && nowInAlarmZone.minute == alarm.minute) {
                val lastFired = lastFiredAlarms[alarm.id] ?: 0L
                if (currentTimeMillis - lastFired > 60_000) {
                    lastFiredAlarms[alarm.id] = currentTimeMillis
                    AudioSynthEngine.playConfiguredAlert(context, settings.value)
                    if (settings.value.flashScreenEnabled) {
                        triggerVisualFlash()
                    }
                }
            }
        }
    }

    private fun checkAndTriggerChime(now: ZonedDateTime) {
        val activeSettings = settings.value
        val nowMillis = now.toInstant().toEpochMilli()

        if (!activeSettings.chimeEnabled) {
            return
        }

        if (nextChimeEpochMillis <= 0L) {
            recalculateNextChimeSchedule(activeSettings)
            return
        }

        _remainingChimeSeconds.value = TimeAndAlertUtils.computeRemainingChimeSeconds(
            nowMillis,
            nextChimeEpochMillis
        )

        if (nowMillis >= nextChimeEpochMillis) {
            // Schedule next cycle immediately so no duplicate trigger occurs
            nextChimeEpochMillis = TimeAndAlertUtils.computeNextChimeEpochMillis(
                now = now,
                intervalMinutes = activeSettings.chimeIntervalMinutes,
                alignToTopHour = activeSettings.alignToTopHour
            )
            _remainingChimeSeconds.value = TimeAndAlertUtils.computeRemainingChimeSeconds(
                nowMillis,
                nextChimeEpochMillis
            )

            val isQuiet = TimeAndAlertUtils.isWithinQuietHours(
                now = now,
                enabled = activeSettings.quietHoursEnabled,
                startHour = activeSettings.quietHoursStartHour,
                startMinute = activeSettings.quietHoursStartMinute,
                endHour = activeSettings.quietHoursEndHour,
                endMinute = activeSettings.quietHoursEndMinute,
                activeDaysOfWeek = activeSettings.quietHoursDaysOfWeek
            )

            if (isQuiet) {
                if (activeSettings.flashScreenEnabled) {
                    triggerVisualFlash()
                }
                return
            }

            AudioSynthEngine.playConfiguredAlert(context, activeSettings)

            if (activeSettings.flashScreenEnabled) {
                triggerVisualFlash()
            }
        }
    }

    private fun checkAndTriggerEyeBreak(now: ZonedDateTime) {
        val activeSettings = settings.value
        if (!activeSettings.eyeBreakEnabled) {
            return
        }

        val nowMillis = now.toInstant().toEpochMilli()
        if (nextEyeBreakEpochMillis <= 0L) {
            recalculateNextEyeBreakSchedule(activeSettings)
            return
        }

        _remainingEyeBreakSeconds.value = TimeAndAlertUtils.computeRemainingChimeSeconds(
            nowMillis,
            nextEyeBreakEpochMillis
        )

        if (nowMillis >= nextEyeBreakEpochMillis) {
            nextEyeBreakEpochMillis = TimeAndAlertUtils.computeNextEyeBreakEpochMillis(
                nowMillis = nowMillis,
                intervalMinutes = activeSettings.eyeBreakIntervalMinutes
            )
            _remainingEyeBreakSeconds.value = TimeAndAlertUtils.computeRemainingChimeSeconds(
                nowMillis,
                nextEyeBreakEpochMillis
            )

            if (_activeEyeBreakPrompt.value == null) {
                presentEyeBreakExercise(nowMillis = nowMillis, playFeedback = true, now = now)
            }
        }
    }

    private fun presentEyeBreakExercise(
        nowMillis: Long = System.currentTimeMillis(),
        playFeedback: Boolean = false,
        now: ZonedDateTime = ZonedDateTime.now()
    ) {
        val exercises = TimeAndAlertUtils.EyeExercises
        val idx = ((eyeExerciseCursor % exercises.size) + exercises.size) % exercises.size
        eyeExerciseCursor = (idx + 1) % exercises.size
        _activeEyeBreakPrompt.value = EyeBreakPromptState(
            exercise = exercises[idx],
            exerciseIndex = idx,
            triggeredAtMillis = nowMillis
        )

        if (playFeedback) {
            val activeSettings = settings.value
            val isQuiet = TimeAndAlertUtils.isWithinQuietHours(
                now = now,
                enabled = activeSettings.quietHoursEnabled,
                startHour = activeSettings.quietHoursStartHour,
                startMinute = activeSettings.quietHoursStartMinute,
                endHour = activeSettings.quietHoursEndHour,
                endMinute = activeSettings.quietHoursEndMinute,
                activeDaysOfWeek = activeSettings.quietHoursDaysOfWeek
            )
            if (!isQuiet) {
                AudioSynthEngine.playConfiguredAlert(context, activeSettings)
            }
            if (activeSettings.flashScreenEnabled) {
                triggerVisualFlash()
            }
        }
    }

    private fun triggerVisualFlash() {
        flashJob?.cancel()
        _pulseTriggerCount.value = _pulseTriggerCount.value + 1L
        flashJob = viewModelScope.launch {
            _isFlashing.value = true
            delay(1750)
            _isFlashing.value = false
        }
    }

    fun previewVisualFlash() {
        triggerVisualFlash()
    }

    private fun persistSettings(updated: ClockSettings, syncWidgets: Boolean = false) {
        viewModelScope.launch {
            repository.saveSettings(updated)
            if (syncWidgets && !isRunningTests) {
                WorldClockAppWidgetProvider.updateAllWidgets(context)
            }
        }
    }

    fun testPlaySound() {
        AudioSynthEngine.playConfiguredAlert(context, settings.value)
        triggerVisualFlash()
    }

    // ---- Interval & Custom Alert Sound Mutations ----
    fun updateChimeInterval(minutes: Int) {
        val clamped = minutes.coerceIn(1, 120)
        val updated = settings.value.copy(chimeIntervalMinutes = clamped)
        recalculateNextChimeSchedule(updated)
        persistSettings(updated)
    }

    fun updateSelectedSound(soundName: String) {
        val updated = settings.value.copy(
            soundSourceType = "BUILTIN",
            selectedSoundName = soundName
        )
        persistSettings(updated)
        AudioSynthEngine.playSound(context, soundName)
    }

    fun updateCustomAudioFile(uriString: String, displayLabel: String) {
        val updated = settings.value.copy(
            soundSourceType = "CUSTOM_FILE",
            customSoundUri = uriString,
            customSoundLabel = displayLabel.ifBlank { "Custom Audio File" }
        )
        persistSettings(updated)
        AudioSynthEngine.playUriPreview(context, "CUSTOM_FILE", uriString, updated.selectedSoundName)
    }

    fun updateSystemRingtone(uriString: String, displayLabel: String) {
        val updated = settings.value.copy(
            soundSourceType = "RINGTONE",
            customSoundUri = uriString,
            customSoundLabel = displayLabel.ifBlank { "System Ringtone" }
        )
        persistSettings(updated)
        AudioSynthEngine.playUriPreview(context, "RINGTONE", uriString, updated.selectedSoundName)
    }

    fun toggleChimeEnabled(enabled: Boolean) {
        val updated = settings.value.copy(chimeEnabled = enabled)
        if (enabled) {
            recalculateNextChimeSchedule(updated)
        }
        persistSettings(updated)
    }

    /**
     * Snoozes the interval alert by adding additional minutes to the next scheduled chime epoch.
     */
    fun snoozeChime(snoozeMinutes: Int = 5) {
        val additionalMillis = snoozeMinutes.coerceIn(1, 60) * 60_000L
        val currentTarget = if (nextChimeEpochMillis > System.currentTimeMillis()) {
            nextChimeEpochMillis
        } else {
            System.currentTimeMillis()
        }
        nextChimeEpochMillis = currentTarget + additionalMillis
        _remainingChimeSeconds.value = TimeAndAlertUtils.computeRemainingChimeSeconds(
            System.currentTimeMillis(),
            nextChimeEpochMillis
        )
    }

    /**
     * Restarts the current interval alert cycle from the beginning.
     */
    fun resetCurrentInterval() {
        val activeSettings = settings.value
        val now = ZonedDateTime.now()
        val nowMillis = now.toInstant().toEpochMilli()
        val intervalMillis = activeSettings.chimeIntervalMinutes.coerceIn(15, 20) * 60_000L
        nextChimeEpochMillis = nowMillis + intervalMillis
        _remainingChimeSeconds.value = TimeAndAlertUtils.computeRemainingChimeSeconds(
            nowMillis,
            nextChimeEpochMillis
        )
    }

    fun toggleFlashEnabled(enabled: Boolean) {
        persistSettings(settings.value.copy(flashScreenEnabled = enabled))
    }

    fun toggleAlignmentEnabled(enabled: Boolean) {
        val updated = settings.value.copy(alignToTopHour = enabled)
        recalculateNextChimeSchedule(updated)
        persistSettings(updated)
    }

    fun toggleQuietHoursEnabled(enabled: Boolean) {
        persistSettings(settings.value.copy(quietHoursEnabled = enabled))
    }

    fun updateQuietHours(start: Int, end: Int) {
        persistSettings(
            settings.value.copy(
                quietHoursStartHour = start.coerceIn(0, 23),
                quietHoursEndHour = end.coerceIn(0, 23)
            )
        )
    }

    fun updateQuietHoursSchedule(
        startH: Int,
        startM: Int,
        endH: Int,
        endM: Int,
        activeDays: String
    ) {
        persistSettings(
            settings.value.copy(
                quietHoursStartHour = startH.coerceIn(0, 23),
                quietHoursStartMinute = startM.coerceIn(0, 59),
                quietHoursEndHour = endH.coerceIn(0, 23),
                quietHoursEndMinute = endM.coerceIn(0, 59),
                quietHoursDaysOfWeek = if (activeDays.isBlank()) "ALL" else activeDays
            )
        )
    }

    // ---- Eye Break & Screen Rest Reminder ----
    fun toggleEyeBreakEnabled(enabled: Boolean) {
        val updated = settings.value.copy(eyeBreakEnabled = enabled)
        if (enabled) {
            recalculateNextEyeBreakSchedule(updated)
        }
        persistSettings(updated)
    }

    fun updateEyeBreakInterval(minutes: Int) {
        val clamped = minutes.coerceIn(1, 180)
        val updated = settings.value.copy(eyeBreakIntervalMinutes = clamped)
        recalculateNextEyeBreakSchedule(updated)
        persistSettings(updated)
    }

    fun triggerEyeBreakNow() {
        presentEyeBreakExercise(nowMillis = System.currentTimeMillis(), playFeedback = false)
    }

    fun nextEyeBreakExercise() {
        val exercises = TimeAndAlertUtils.EyeExercises
        val currentIdx = _activeEyeBreakPrompt.value?.exerciseIndex ?: -1
        val nextIdx = (currentIdx + 1) % exercises.size
        eyeExerciseCursor = (nextIdx + 1) % exercises.size
        _activeEyeBreakPrompt.value = EyeBreakPromptState(
            exercise = exercises[nextIdx],
            exerciseIndex = nextIdx,
            triggeredAtMillis = System.currentTimeMillis()
        )
    }

    fun dismissEyeBreakPrompt(snoozeMinutes: Int? = null) {
        _activeEyeBreakPrompt.value = null
        val nowMillis = System.currentTimeMillis()
        val delayMinutes = snoozeMinutes?.coerceIn(1, 60)
            ?: settings.value.eyeBreakIntervalMinutes.coerceIn(1, 180)
        nextEyeBreakEpochMillis = nowMillis + delayMinutes * 60_000L
        _remainingEyeBreakSeconds.value = TimeAndAlertUtils.computeRemainingChimeSeconds(
            nowMillis,
            nextEyeBreakEpochMillis
        )
    }

    // ---- Display & Theme Customizations ----
    fun toggleShowSeconds(show: Boolean) {
        persistSettings(settings.value.copy(showSeconds = show), syncWidgets = true)
    }

    fun toggle24HourFormat(enable24h: Boolean) {
        persistSettings(settings.value.copy(digital24HourFormat = enable24h), syncWidgets = true)
    }

    fun updateWallpaperTheme(themeName: String) {
        persistSettings(settings.value.copy(themeName = themeName), syncWidgets = true)
    }

    fun saveCustomThemeColors(bgArgb: Long, textArgb: Long, numberArgb: Long) {
        val updated = settings.value.copy(
            themeName = "Custom",
            customBgColorArgb = bgArgb,
            customTextColorArgb = textArgb,
            customNumberColorArgb = numberArgb
        )
        persistSettings(updated, syncWidgets = true)
    }

    // ---- World Clock Widget Configuration ----
    fun toggleWorldClockBesideMain(show: Boolean) {
        persistSettings(settings.value.copy(showWorldClockBesideMain = show))
    }

    fun updateWorldClockWidgetLayout(layout: String) {
        persistSettings(settings.value.copy(worldClockWidgetLayout = layout))
    }

    fun toggleWorldClockShowOffset(show: Boolean) {
        persistSettings(settings.value.copy(worldClockShowOffset = show), syncWidgets = true)
    }

    fun toggleWorldClockShowDate(show: Boolean) {
        persistSettings(settings.value.copy(worldClockShowDate = show))
    }

    fun toggleWorldClockShowSeconds(show: Boolean) {
        persistSettings(settings.value.copy(worldClockShowSeconds = show), syncWidgets = true)
    }

    fun updateHomeWidgetBgAlpha(alpha: Int) {
        persistSettings(settings.value.copy(homeWidgetBgAlpha = alpha.coerceIn(80, 255)), syncWidgets = true)
    }

    fun updateOrientationMode(mode: Int) {
        persistSettings(settings.value.copy(orientationMode = mode))
    }

    fun toggleAutoAmbientSensor(enabled: Boolean) {
        persistSettings(settings.value.copy(autoAmbientSensorEnabled = enabled))
    }

    fun toggleCircadianShift(enabled: Boolean) {
        if (!enabled) {
            _circadianPreviewHour.value = null
        }
        persistSettings(settings.value.copy(circadianShiftEnabled = enabled))
    }

    fun setCircadianPreviewHour(hour: Int?) {
        _circadianPreviewHour.value = hour?.coerceIn(0, 23)
    }

    fun updateCurrentFocusText(text: String) {
        persistSettings(settings.value.copy(currentFocusText = text))
    }

    fun toggleBreathingGuide(show: Boolean) {
        persistSettings(settings.value.copy(showBreathingGuide = show))
    }

    fun updateAffirmationIndex(index: Int) {
        persistSettings(settings.value.copy(currentAffirmationIndex = index))
    }

    fun updateLayoutStyle(style: String) {
        persistSettings(settings.value.copy(layoutStyle = style))
    }

    fun updateBackdropPattern(pattern: String) {
        persistSettings(settings.value.copy(backdropPattern = pattern))
    }

    fun updateNightDimMode(mode: String) {
        persistSettings(settings.value.copy(nightDimMode = mode))
    }

    fun updateClockSortOrder(order: String) {
        persistSettings(settings.value.copy(clockSortOrder = order), syncWidgets = true)
    }

    // ---- World / Friend Clock Operations ----
    fun addNewFriend(name: String, city: String, timezoneId: String) {
        viewModelScope.launch {
            val validTz = TimeAndAlertUtils.safeZoneId(timezoneId).id
            val resolvedCity = city.ifBlank { validTz.substringAfterLast('/').replace('_', ' ') }
            val resolvedName = name.ifBlank { resolvedCity }
            repository.insertFriend(
                FriendClock(
                    friendName = resolvedName,
                    cityLabel = resolvedCity,
                    timezoneId = validTz,
                    showInWidget = true
                )
            )
            if (!isRunningTests) {
                WorldClockAppWidgetProvider.updateAllWidgets(context)
            }
        }
    }

    fun toggleFriendShowInWidget(friendClock: FriendClock) {
        viewModelScope.launch {
            repository.updateFriend(friendClock.copy(showInWidget = !friendClock.showInWidget))
            if (!isRunningTests) {
                WorldClockAppWidgetProvider.updateAllWidgets(context)
            }
        }
    }

    fun removeFriend(friendClock: FriendClock) {
        viewModelScope.launch {
            repository.deleteFriend(friendClock)
            if (!isRunningTests) {
                WorldClockAppWidgetProvider.updateAllWidgets(context)
            }
        }
    }

    fun removeFriendById(id: Int) {
        viewModelScope.launch {
            repository.deleteFriendById(id)
            if (!isRunningTests) {
                WorldClockAppWidgetProvider.updateAllWidgets(context)
            }
        }
    }

    // ---- Alarm Operations ----
    fun addAlarm(hour: Int, minute: Int, timezoneId: String, label: String = "") {
        viewModelScope.launch {
            repository.insertAlarm(
                AlarmItem(
                    hour = hour.coerceIn(0, 23),
                    minute = minute.coerceIn(0, 59),
                    timezoneId = TimeAndAlertUtils.safeZoneId(timezoneId).id,
                    label = label
                )
            )
        }
    }

    fun toggleAlarm(alarm: AlarmItem) {
        viewModelScope.launch {
            repository.updateAlarm(alarm.copy(isEnabled = !alarm.isEnabled))
        }
    }

    fun deleteAlarm(alarm: AlarmItem) {
        viewModelScope.launch {
            repository.deleteAlarm(alarm)
        }
    }

    fun getAvailableTimeZones(): List<String> = cachedAvailableZones

    // ---- Complete Offline Backup & Restore ----
    fun exportBackup(): String {
        return try {
            val json = JSONObject()
            json.put("backupVersion", 2)
            json.put("exportedAt", System.currentTimeMillis())

            val s = settings.value
            val sJson = JSONObject().apply {
                put("chimeIntervalMinutes", s.chimeIntervalMinutes)
                put("selectedSoundName", s.selectedSoundName)
                put("soundSourceType", s.soundSourceType)
                put("customSoundUri", s.customSoundUri)
                put("customSoundLabel", s.customSoundLabel)
                put("chimeEnabled", s.chimeEnabled)
                put("flashScreenEnabled", s.flashScreenEnabled)
                put("alignToTopHour", s.alignToTopHour)
                put("quietHoursEnabled", s.quietHoursEnabled)
                put("quietHoursStartHour", s.quietHoursStartHour)
                put("quietHoursEndHour", s.quietHoursEndHour)
                put("showSeconds", s.showSeconds)
                put("digital24HourFormat", s.digital24HourFormat)
                put("themeName", s.themeName)
                put("customBgColorArgb", s.customBgColorArgb)
                put("customTextColorArgb", s.customTextColorArgb)
                put("customNumberColorArgb", s.customNumberColorArgb)
                put("showWorldClockBesideMain", s.showWorldClockBesideMain)
                put("worldClockWidgetLayout", s.worldClockWidgetLayout)
                put("worldClockShowOffset", s.worldClockShowOffset)
                put("worldClockShowDate", s.worldClockShowDate)
                put("worldClockShowSeconds", s.worldClockShowSeconds)
                put("homeWidgetBgAlpha", s.homeWidgetBgAlpha)
                put("orientationMode", s.orientationMode)
                put("autoAmbientSensorEnabled", s.autoAmbientSensorEnabled)
                put("circadianShiftEnabled", s.circadianShiftEnabled)
                put("currentFocusText", s.currentFocusText)
                put("showBreathingGuide", s.showBreathingGuide)
                put("currentAffirmationIndex", s.currentAffirmationIndex)
                put("clockSortOrder", s.clockSortOrder)
                put("eyeBreakEnabled", s.eyeBreakEnabled)
                put("eyeBreakIntervalMinutes", s.eyeBreakIntervalMinutes)
                put("layoutStyle", s.layoutStyle)
                put("nightDimMode", s.nightDimMode)
                put("backdropPattern", s.backdropPattern)
            }
            json.put("settings", sJson)

            val clkArray = JSONArray()
            friendClocks.value.forEach { clk ->
                val clkJson = JSONObject().apply {
                    put("friendName", clk.friendName)
                    put("timezoneId", clk.timezoneId)
                    put("cityLabel", clk.cityLabel)
                    put("showInWidget", clk.showInWidget)
                }
                clkArray.put(clkJson)
            }
            json.put("friendClocks", clkArray)

            val alarmArray = JSONArray()
            alarms.value.forEach { alarm ->
                val alarmJson = JSONObject().apply {
                    put("hour", alarm.hour)
                    put("minute", alarm.minute)
                    put("timezoneId", alarm.timezoneId)
                    put("isEnabled", alarm.isEnabled)
                    put("label", alarm.label)
                }
                alarmArray.put(alarmJson)
            }
            json.put("alarms", alarmArray)

            json.toString(4)
        } catch (_: Exception) {
            ""
        }
    }

    fun importBackup(backupString: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = JSONObject(backupString)

                val sJson = json.optJSONObject("settings")
                if (sJson != null) {
                    val current = settings.value
                    val importedSettings = ClockSettings(
                        id = 1,
                        chimeIntervalMinutes = sJson.optInt("chimeIntervalMinutes", current.chimeIntervalMinutes).coerceIn(1, 120),
                        selectedSoundName = sJson.optString("selectedSoundName", current.selectedSoundName),
                        soundSourceType = sJson.optString("soundSourceType", current.soundSourceType),
                        customSoundUri = sJson.optString("customSoundUri", current.customSoundUri),
                        customSoundLabel = sJson.optString("customSoundLabel", current.customSoundLabel),
                        chimeEnabled = sJson.optBoolean("chimeEnabled", current.chimeEnabled),
                        flashScreenEnabled = sJson.optBoolean("flashScreenEnabled", current.flashScreenEnabled),
                        alignToTopHour = sJson.optBoolean("alignToTopHour", current.alignToTopHour),
                        quietHoursEnabled = sJson.optBoolean("quietHoursEnabled", current.quietHoursEnabled),
                        quietHoursStartHour = sJson.optInt("quietHoursStartHour", current.quietHoursStartHour).coerceIn(0, 23),
                        quietHoursEndHour = sJson.optInt("quietHoursEndHour", current.quietHoursEndHour).coerceIn(0, 23),
                        showSeconds = sJson.optBoolean("showSeconds", current.showSeconds),
                        digital24HourFormat = sJson.optBoolean("digital24HourFormat", current.digital24HourFormat),
                        themeName = sJson.optString("themeName", current.themeName),
                        customBgColorArgb = sJson.optLong("customBgColorArgb", current.customBgColorArgb),
                        customTextColorArgb = sJson.optLong("customTextColorArgb", current.customTextColorArgb),
                        customNumberColorArgb = sJson.optLong("customNumberColorArgb", current.customNumberColorArgb),
                        showWorldClockBesideMain = sJson.optBoolean("showWorldClockBesideMain", current.showWorldClockBesideMain),
                        worldClockWidgetLayout = sJson.optString("worldClockWidgetLayout", current.worldClockWidgetLayout),
                        worldClockShowOffset = sJson.optBoolean("worldClockShowOffset", current.worldClockShowOffset),
                        worldClockShowDate = sJson.optBoolean("worldClockShowDate", current.worldClockShowDate),
                        worldClockShowSeconds = sJson.optBoolean("worldClockShowSeconds", current.worldClockShowSeconds),
                        homeWidgetBgAlpha = sJson.optInt("homeWidgetBgAlpha", current.homeWidgetBgAlpha).coerceIn(80, 255),
                        orientationMode = sJson.optInt("orientationMode", current.orientationMode),
                        autoAmbientSensorEnabled = sJson.optBoolean("autoAmbientSensorEnabled", current.autoAmbientSensorEnabled),
                        circadianShiftEnabled = sJson.optBoolean("circadianShiftEnabled", current.circadianShiftEnabled),
                        currentFocusText = sJson.optString("currentFocusText", current.currentFocusText),
                        showBreathingGuide = sJson.optBoolean("showBreathingGuide", current.showBreathingGuide),
                        currentAffirmationIndex = sJson.optInt("currentAffirmationIndex", current.currentAffirmationIndex),
                        clockSortOrder = sJson.optString("clockSortOrder", current.clockSortOrder),
                        eyeBreakEnabled = sJson.optBoolean("eyeBreakEnabled", current.eyeBreakEnabled),
                        eyeBreakIntervalMinutes = sJson.optInt("eyeBreakIntervalMinutes", current.eyeBreakIntervalMinutes).coerceIn(1, 180),
                        layoutStyle = sJson.optString("layoutStyle", current.layoutStyle),
                        nightDimMode = sJson.optString("nightDimMode", current.nightDimMode),
                        backdropPattern = sJson.optString("backdropPattern", current.backdropPattern)
                    )
                    repository.saveSettings(importedSettings)
                    recalculateNextChimeSchedule(importedSettings)
                    recalculateNextEyeBreakSchedule(importedSettings)
                }

                val clkArray = json.optJSONArray("friendClocks")
                if (clkArray != null) {
                    repository.deleteAllFriends()
                    for (i in 0 until clkArray.length()) {
                        val cObj = clkArray.optJSONObject(i)
                        if (cObj != null) {
                            val name = cObj.optString("friendName", "")
                            val timezoneId = TimeAndAlertUtils.safeZoneId(cObj.optString("timezoneId", "UTC")).id
                            val city = cObj.optString("cityLabel", "")
                            val showInWidget = cObj.optBoolean("showInWidget", true)
                            if (name.isNotEmpty() || city.isNotEmpty()) {
                                repository.insertFriend(
                                    FriendClock(
                                        friendName = name.ifBlank { city },
                                        timezoneId = timezoneId,
                                        cityLabel = city.ifBlank { name },
                                        showInWidget = showInWidget
                                    )
                                )
                            }
                        }
                    }
                }

                val alarmArray = json.optJSONArray("alarms")
                if (alarmArray != null) {
                    alarms.value.forEach { repository.deleteAlarm(it) }
                    for (i in 0 until alarmArray.length()) {
                        val aObj = alarmArray.optJSONObject(i)
                        if (aObj != null) {
                            repository.insertAlarm(
                                AlarmItem(
                                    hour = aObj.optInt("hour", 8).coerceIn(0, 23),
                                    minute = aObj.optInt("minute", 0).coerceIn(0, 59),
                                    timezoneId = TimeAndAlertUtils.safeZoneId(aObj.optString("timezoneId", "UTC")).id,
                                    isEnabled = aObj.optBoolean("isEnabled", true),
                                    label = aObj.optString("label", "")
                                )
                            )
                        }
                    }
                }

                if (!isRunningTests) {
                    WorldClockAppWidgetProvider.updateAllWidgets(context)
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Invalid backup JSON format")
            }
        }
    }

    override fun onCleared() {
        isTimerRunning = false
        tickJob?.cancel()
        AudioSynthEngine.stopActivePlayback()
        if (!isRunningTests) {
            RealtimeClockSyncManager.unregister(context)
        }
        super.onCleared()
    }
}

class ClockViewModelFactory(
    private val repository: ClockRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ClockViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ClockViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
