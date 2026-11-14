package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class LocationSyncStatus(
    val label: String,
    val cityName: String,
    val timezoneId: String,
    val currentZonedTime: ZonedDateTime,
    val isDaylightSavings: Boolean,
    val dstOffsetMinutes: Int,
    val zoneAbbreviation: String,
    val nextTransitionDescription: String,
    val utcOffsetString: String
)

data class SystemClockSyncState(
    val currentLocalZonedTime: ZonedDateTime,
    val isLocalDaylightSavings: Boolean,
    val localDstOffsetMinutes: Int,
    val localZoneId: String,
    val localNextTransition: String,
    val lastSyncTimestamp: Long,
    val syncProviderName: String,
    val clockJumpDetected: Boolean,
    val lastDetectedDriftMillis: Long,
    val isReceiverRegistered: Boolean
)

object RealtimeClockSyncManager {

    private val _syncEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val syncEvents: SharedFlow<String> = _syncEvents.asSharedFlow()

    private val _syncState = MutableStateFlow(buildCurrentSyncState())
    val syncState: StateFlow<SystemClockSyncState> = _syncState.asStateFlow()

    private var lastRecordedWallClockMillis = System.currentTimeMillis()
    private var lastRecordedElapsedRealtimeMillis = SystemClock.elapsedRealtime()
    private var isReceiverRegistered = false

    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            handleSystemTimeOrZoneChange(action)
        }
    }

    fun initialize(context: Context) {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    addAction(Intent.ACTION_TIME_TICK)
                    addAction(Intent.ACTION_DATE_CHANGED)
                }
                ContextCompat.registerReceiver(
                    context.applicationContext,
                    timeChangeReceiver,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
                )
                isReceiverRegistered = true
            } catch (_: Exception) {
            }
        }
        resyncNow("INITIALIZE")
    }

    fun unregister(context: Context) {
        if (isReceiverRegistered) {
            try {
                context.applicationContext.unregisterReceiver(timeChangeReceiver)
            } catch (_: Exception) {
            }
            isReceiverRegistered = false
        }
    }

    /**
     * Called on periodic clock ticks to detect sudden wall-clock jumps
     * (manual user adjustments, network NTP steps, or 1-hour DST forward/backward transitions).
     */
    fun verifyClockContinuity(
        nowWallMillis: Long = System.currentTimeMillis(),
        nowElapsedMillis: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        val wallDelta = nowWallMillis - lastRecordedWallClockMillis
        val elapsedDelta = nowElapsedMillis - lastRecordedElapsedRealtimeMillis

        lastRecordedWallClockMillis = nowWallMillis
        lastRecordedElapsedRealtimeMillis = nowElapsedMillis

        val drift = wallDelta - elapsedDelta
        val jumpDetected = abs(drift) > 2500L
        if (jumpDetected) {
            resyncNow(
                reason = "CLOCK_JUMP_DETECTED (drift ${drift}ms)",
                clockJumpDetected = true,
                driftMillis = drift
            )
        }
        return jumpDetected
    }

    fun resyncNow(
        reason: String = "MANUAL_RESYNC",
        clockJumpDetected: Boolean = false,
        driftMillis: Long = 0L
    ) {
        lastRecordedWallClockMillis = System.currentTimeMillis()
        lastRecordedElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        val newState = buildCurrentSyncState(
            clockJumpDetected = clockJumpDetected,
            lastDetectedDriftMillis = driftMillis
        )
        _syncState.value = newState
        _syncEvents.tryEmit(reason)
    }

    private fun handleSystemTimeOrZoneChange(action: String) {
        val reason = when (action) {
            Intent.ACTION_TIMEZONE_CHANGED -> "SYSTEM_TIMEZONE_CHANGED"
            Intent.ACTION_TIME_CHANGED -> "SYSTEM_TIME_SET"
            Intent.ACTION_DATE_CHANGED -> "SYSTEM_DATE_CHANGED"
            Intent.ACTION_TIME_TICK -> "SYSTEM_MINUTE_TICK"
            else -> action
        }
        resyncNow(reason)
    }

    fun buildCurrentSyncState(
        clockJumpDetected: Boolean = false,
        lastDetectedDriftMillis: Long = 0L
    ): SystemClockSyncState {
        val now = ZonedDateTime.now()
        val zone = now.zone
        val rules = zone.rules
        val instant = now.toInstant()

        val isDst = rules.isDaylightSavings(instant)
        val dstDuration = rules.getDaylightSavings(instant)
        val dstMinutes = (dstDuration.seconds / 60).toInt()

        val nextTransitionDesc = formatNextTransition(zone, instant)

        return SystemClockSyncState(
            currentLocalZonedTime = now,
            isLocalDaylightSavings = isDst,
            localDstOffsetMinutes = dstMinutes,
            localZoneId = zone.id,
            localNextTransition = nextTransitionDesc,
            lastSyncTimestamp = System.currentTimeMillis(),
            syncProviderName = "Android System Provider (NITZ/NTP Synced)",
            clockJumpDetected = clockJumpDetected,
            lastDetectedDriftMillis = lastDetectedDriftMillis,
            isReceiverRegistered = isReceiverRegistered
        )
    }

    /**
     * Resolves real-time Daylight Saving Time status, current offset, and scheduled transition
     * for any timezone, specifically guaranteeing accuracy for Toronto, Bangalore, Germany,
     * Qatar, Kuwait, Saudi Arabia, and any configured user clocks.
     */
    fun getLocationSyncStatus(
        label: String,
        cityName: String,
        timezoneId: String,
        referenceInstant: Instant = Instant.now()
    ): LocationSyncStatus {
        val zone = TimeAndAlertUtils.safeZoneId(timezoneId)
        val zonedDateTime = ZonedDateTime.ofInstant(referenceInstant, zone)
        val rules = zone.rules

        val isDst = rules.isDaylightSavings(referenceInstant)
        val dstDuration = rules.getDaylightSavings(referenceInstant)
        val dstMinutes = (dstDuration.seconds / 60).toInt()

        val zoneAbbr = try {
            DateTimeFormatter.ofPattern("z", Locale.US).format(zonedDateTime)
        } catch (_: Exception) {
            ""
        }

        val offsetSeconds = zonedDateTime.offset.totalSeconds
        val sign = if (offsetSeconds >= 0) "+" else "-"
        val absSecs = abs(offsetSeconds)
        val hours = absSecs / 3600
        val mins = (absSecs % 3600) / 60
        val utcOffsetString = if (mins > 0) {
            String.format(Locale.US, "UTC%s%d:%02d", sign, hours, mins)
        } else {
            "UTC$sign$hours"
        }

        val nextTransitionDesc = formatNextTransition(zone, referenceInstant)

        return LocationSyncStatus(
            label = label,
            cityName = cityName,
            timezoneId = timezoneId,
            currentZonedTime = zonedDateTime,
            isDaylightSavings = isDst,
            dstOffsetMinutes = dstMinutes,
            zoneAbbreviation = zoneAbbr,
            nextTransitionDescription = nextTransitionDesc,
            utcOffsetString = utcOffsetString
        )
    }

    /**
     * Generates a complete synchronization and DST snapshot for the specified global locations.
     */
    fun getSpecifiedGlobalLocationsSyncStatus(
        referenceInstant: Instant = Instant.now()
    ): List<LocationSyncStatus> {
        val targets = listOf(
            Triple("Toronto, Canada", "Toronto", "America/Toronto"),
            Triple("Bangalore, India", "Bangalore", "Asia/Kolkata"),
            Triple("Berlin, Germany", "Germany", "Europe/Berlin"),
            Triple("Doha, Qatar", "Qatar", "Asia/Qatar"),
            Triple("Kuwait City, Kuwait", "Kuwait", "Asia/Kuwait"),
            Triple("Riyadh, Saudi Arabia", "Saudi Arabia", "Asia/Riyadh")
        )

        return targets.map { (label, name, tzId) ->
            getLocationSyncStatus(label, name, tzId, referenceInstant)
        }
    }

    private fun formatNextTransition(zone: ZoneId, instant: Instant): String {
        val rules = zone.rules
        val nextTransition = try {
            rules.nextTransition(instant)
        } catch (_: Exception) {
            null
        }

        if (nextTransition == null) {
            return "No DST scheduled (Constant offset)"
        }

        val transitionInstant = nextTransition.instant
        val transitionZoned = ZonedDateTime.ofInstant(transitionInstant, zone)
        val formattedDate = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' hh:mm a", Locale.US).format(transitionZoned)

        val shiftType = when {
            nextTransition.isGap -> "Spring Forward (+1h DST)"
            nextTransition.isOverlap -> "Fall Back (-1h Standard)"
            else -> "Clock Adjustment"
        }

        return "$formattedDate ($shiftType)"
    }
}
