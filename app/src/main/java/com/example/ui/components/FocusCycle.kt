package com.example.ui.components

import com.example.data.AlarmItem
import com.example.util.TimeAndAlertUtils
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.ceil

enum class FocusPhase { WORK, BREAK }

/** Pomodoro rules: work rounds separated by short breaks, with a long break after every fourth round. */
object FocusCycle {
    const val SHORT_BREAK_MINUTES = 5
    const val LONG_BREAK_MINUTES = 15
    const val LONG_BREAK_EVERY = 4

    /** What the user can pick in the dialog: 1 is a plain single session. */
    val roundOptions = listOf(1, 2, 4, 6)

    /** The session that follows [s] when its time is up, or null when the whole cycle is finished. */
    fun next(s: FocusSession, nowMillis: Long): FocusSession? = when (s.phase) {
        FocusPhase.WORK ->
            if (s.round >= s.rounds) null
            else {
                val breakMinutes = if (s.round % LONG_BREAK_EVERY == 0) LONG_BREAK_MINUTES else SHORT_BREAK_MINUTES
                s.copy(phase = FocusPhase.BREAK, startMillis = nowMillis, durationMillis = breakMinutes * 60_000L)
            }
        FocusPhase.BREAK ->
            s.copy(phase = FocusPhase.WORK, round = s.round + 1, startMillis = nowMillis, durationMillis = s.workMillis)
    }

    /** Short text for the running pill, e.g. "Write 2/4" or "Break 2/4". Single sessions show just the label. */
    fun pillLabel(s: FocusSession): String {
        val progress = if (s.rounds > 1) " ${s.round}/${s.rounds}" else ""
        return if (s.phase == FocusPhase.BREAK) "Break$progress" else s.label + progress
    }
}

/** Heads-up cues shortly before a focus block ends or an alarm rings, to soften sudden changes of task. */
object TransitionWarning {
    val thresholdsMinutes = listOf(10, 5)

    /** Whole minutes left, rounded up, so "5 min" shows from 5:00 down to 4:01. */
    fun minutesLeft(remainingMillis: Long): Int = ceil(remainingMillis / 60_000.0).toInt()

    /**
     * The warning threshold (10 or 5) that [remainingMillis] has just reached, or null. [totalMillis] lets a short
     * block skip warnings that would fire the moment it starts (a 5-minute break needs no "5 min left").
     */
    fun thresholdReached(remainingMillis: Long, totalMillis: Long?): Int? {
        if (remainingMillis <= 0) return null
        val left = minutesLeft(remainingMillis)
        return thresholdsMinutes.firstOrNull { it == left && (totalMillis == null || totalMillis > it * 60_000L) }
    }

    /** Milliseconds until the next occurrence of [alarm] (today, or tomorrow if already past), or null if disabled. */
    fun millisUntil(alarm: AlarmItem, now: ZonedDateTime): Long? {
        if (!alarm.isEnabled) return null
        val zone = TimeAndAlertUtils.safeZoneId(alarm.timezoneId)
        val inZone = now.withZoneSameInstant(zone)
        val time = LocalTime.of(alarm.hour.coerceIn(0, 23), alarm.minute.coerceIn(0, 59))
        var target = ZonedDateTime.of(inZone.toLocalDate(), time, zone)
        if (!target.isAfter(inZone)) target = target.plusDays(1)
        return Duration.between(inZone, target).toMillis()
    }
}
