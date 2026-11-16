package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClockSettings
import com.example.data.FriendClock
import com.example.ui.ClockViewModel
import com.example.ui.theme.ChronometerDisplayFont
import com.example.ui.theme.ChronometerMonoFont
import com.example.ui.theme.ClockVisualTheme
import com.example.util.TimeAndAlertUtils
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** Stable names stored in [ClockSettings.nightDimMode]; the dim is a black scrim over the whole clock. */
object NightDim {
    const val OFF = "OFF"
    const val AUTO = "AUTO"
    const val BEDSIDE = "BEDSIDE"

    val options: List<Triple<String, String, String>> = listOf(
        Triple(OFF, "Off", "Full brightness always"),
        Triple(AUTO, "Auto", "Dims from 10pm to 6am"),
        Triple(BEDSIDE, "Bedside", "Very dark all the time")
    )

    /** Scrim opacity (0 = none) for [mode] at the given local [hour]. */
    fun scrimAlpha(mode: String, hour: Int): Float = when (mode) {
        BEDSIDE -> 0.78f
        AUTO -> if (hour >= 22 || hour < 6) 0.5f else 0f
        else -> 0f
    }
}

/** Stable names stored in [ClockSettings.layoutStyle]. */
object LayoutStyle {
    const val BOLD = "BOLD"
    const val CALM = "CALM"
    const val DASHBOARD = "DASHBOARD"
    const val CLASSIC = "CLASSIC"
    // Watch faces: each is a different design, all drawn inside the same feature-complete frame.
    const val ANALOG = "ANALOG"
    const val FLIP = "FLIP"
    const val ORBIT = "ORBIT"
    const val LCD = "LCD"
    const val ARC = "ARC"
    const val WORDS = "WORDS"
    const val TIMELINE = "TIMELINE"

    val options: List<Triple<String, String, String>> = listOf(
        Triple(BOLD, "Bold", "Giant time on a colour wash"),
        Triple(CALM, "Calm", "Quiet, centred, minimal"),
        Triple(DASHBOARD, "Dashboard", "Clean cards, everything visible"),
        Triple(CLASSIC, "Classic", "Original outlined layout"),
        Triple(ANALOG, "Analog", "Round dial with hands"),
        Triple(FLIP, "Flip", "Split-flap desk clock"),
        Triple(ORBIT, "Orbit", "Cities orbiting on rings"),
        Triple(LCD, "Retro LCD", "Seven-segment display"),
        Triple(ARC, "Day arc", "Sun crossing the sky"),
        Triple(WORDS, "Word clock", "Time spelled in letters"),
        Triple(TIMELINE, "Timeline", "24-hour bars per city")
    )

    val faceIds: Set<String> = setOf(ANALOG, FLIP, ORBIT, LCD, ARC, WORDS, TIMELINE)
}

// ---------------------------------------------------------------------------------------------
// Responsive metrics: one place decides how every style adapts to phone/tablet and orientation
// ---------------------------------------------------------------------------------------------

internal class LsMetrics(val w: Dp, val h: Dp) {
    val landscape: Boolean = w > h
    val shortLandscape: Boolean = landscape && h < 480.dp   // phone held sideways / bedside dock
    val phone: Boolean = minOf(w, h) < 600.dp
    /** Text scale: 1.0 at a 600dp short side, never below 0.85 so phones stay legible. */
    val u: Float = (minOf(w, h).value / 600f).coerceIn(0.85f, 1.5f)
    val cityColumns: Int = when {
        shortLandscape -> if (w >= 700.dp) 7 else 3
        landscape -> 6
        phone -> 2
        else -> 3
    }
    val compactCells: Boolean = shortLandscape
    val pad: Dp = (minOf(w, h) * 0.05f).coerceIn(14.dp, 48.dp)
    fun sp(v: Float): TextUnit = (v * u).sp
}

@Composable
internal fun LsBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxWithConstraintsScope.(LsMetrics) -> Unit
) {
    BoxWithConstraints(modifier = modifier) { content(LsMetrics(maxWidth, maxHeight)) }
}

@Composable
internal fun lsTimeSize(m: LsMetrics, widthFrac: Float, heightFrac: Float): TextUnit {
    val density = LocalDensity.current
    return with(density) { minOf(m.w * widthFrac, m.h * heightFrac).toSp() }
}

// ---------------------------------------------------------------------------------------------
// Shared building blocks (one source of truth for clock text, chime state and city rows)
// ---------------------------------------------------------------------------------------------

internal data class LsTime(val hm: String, val ampm: String, val seconds: String, val date: String)

internal data class LsChime(val label: String, val sub: String, val progress: Float)

internal data class LsCity(
    val key: Int,
    val name: String,
    val time: String,
    val ampm: String,
    val offset: String,
    val night: Boolean,
    val home: Boolean,
    val pinned: Boolean,
    val friend: FriendClock
)

@Composable
internal fun rememberLsTime(now: ZonedDateTime, use24: Boolean): LsTime {
    val hmFmt = remember(use24) { DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "hh:mm", Locale.US) }
    val apFmt = remember { DateTimeFormatter.ofPattern("a", Locale.US) }
    val ssFmt = remember { DateTimeFormatter.ofPattern("ss", Locale.US) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE · MMMM d", Locale.US) }
    return LsTime(
        hm = hmFmt.format(now),
        ampm = if (use24) "" else apFmt.format(now),
        seconds = ssFmt.format(now),
        date = dateFmt.format(now).uppercase(Locale.US)
    )
}

@Composable
internal fun rememberLsChime(settings: ClockSettings, viewModel: ClockViewModel): LsChime {
    val remaining by viewModel.remainingChimeSeconds.collectAsState()
    val (mins, secs) = TimeAndAlertUtils.formatMinutesAndSeconds(remaining)
    val total = (settings.chimeIntervalMinutes.coerceAtLeast(1) * 60).coerceAtLeast(1)
    val progress = ((total - remaining).toFloat() / total.toFloat()).coerceIn(0f, 1f)
    return if (settings.chimeEnabled) {
        LsChime("Next chime in $mins:$secs", "Focus alert · every ${settings.chimeIntervalMinutes} min", progress)
    } else {
        LsChime("Chimes muted", "Interval alerts are off", 0f)
    }
}

@Composable
internal fun rememberLsCities(
    now: ZonedDateTime,
    settings: ClockSettings,
    friendClocks: List<FriendClock>
): List<LsCity> {
    return remember(friendClocks, settings.clockSortOrder, settings.digital24HourFormat, now.minute, now.zone.id) {
        val timeFmt = DateTimeFormatter.ofPattern(if (settings.digital24HourFormat) "HH:mm" else "h:mm", Locale.US)
        val apFmt = DateTimeFormatter.ofPattern("a", Locale.US)
        val sorted = TimeAndAlertUtils.getSortedFriendClocks(friendClocks, settings.clockSortOrder, now)
        val pinned = sorted.filter { it.showInWidget }
        (if (pinned.isNotEmpty()) pinned else sorted).map { c ->
            val zone = TimeAndAlertUtils.safeZoneId(c.timezoneId)
            val t = now.withZoneSameInstant(zone)
            LsCity(
                key = c.id,
                name = c.cityLabel.ifBlank { c.friendName },
                time = timeFmt.format(t),
                ampm = if (settings.digital24HourFormat) "" else apFmt.format(t),
                offset = TimeAndAlertUtils.formatCompactRelativeOffset(now, c.timezoneId),
                night = t.hour >= 19 || t.hour < 6,
                home = zone.id == now.zone.id,
                pinned = c.showInWidget,
                friend = c
            )
        }
    }
}

/** Controls stay hidden on an always-on screen: tap to reveal, auto-hide after a few seconds. */
@Composable
internal fun rememberControlsReveal(): MutableState<Boolean> {
    val state = remember { mutableStateOf(false) }
    LaunchedEffect(state.value) {
        if (state.value) {
            delay(6_000)
            state.value = false
        }
    }
    return state
}

internal fun Modifier.tapToToggle(state: MutableState<Boolean>): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        state.value = !state.value
    }
}

/**
 * Always-on screens can burn in. Every minute the content glides to a new spot within +/-4dp,
 * far too small to notice but enough that no pixel stays lit in one place all night.
 */
@Composable
fun Modifier.lsBurnInDrift(minute: Int): Modifier {
    val dx by animateDpAsState(((minute * 37) % 9 - 4).dp, tween(50_000), label = "burnInX")
    val dy by animateDpAsState(((minute * 53) % 9 - 4).dp, tween(50_000), label = "burnInY")
    return this.offset(x = dx, y = dy)
}

/** Tap runs [onTap] (e.g. toggle controls); a long press on a city opens its actions. Shared by every layout style. */
internal fun Modifier.lsCityGestures(onTap: () -> Unit, onLongPress: () -> Unit): Modifier =
    pointerInput(Unit) {
        detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
    }

/** Per-city actions, identical in every layout style: pin to the clock, or remove. */
@Composable
internal fun LsCityActionsDialog(city: LsCity?, theme: ClockVisualTheme, viewModel: ClockViewModel, onDismiss: () -> Unit) {
    if (city == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardBackground,
        title = { Text(city.name, color = theme.textPrimary) },
        text = {
            Column {
                Text(
                    if (city.pinned) "Pinned: when any city is pinned, only pinned cities show on the clock."
                    else "Pin it to show only your favourite cities on the clock, or remove it.",
                    color = theme.textSecondary
                )
                TextButton(onClick = { viewModel.toggleFriendShowInWidget(city.friend); onDismiss() }) {
                    Text(if (city.pinned) "Unpin from clock" else "Pin to clock", color = theme.accentColor)
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.removeFriendById(city.key); onDismiss() }) { Text("Remove", color = theme.accentColor) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textSecondary) } }
    )
}

/** Eye-break countdown (only when the feature is on); tapping starts a break now. Same in every style. */
@Composable
internal fun LsEyeBreak(settings: ClockSettings, theme: ClockVisualTheme, m: LsMetrics, viewModel: ClockViewModel, modifier: Modifier = Modifier) {
    if (!settings.eyeBreakEnabled) return
    val secs by viewModel.remainingEyeBreakSeconds.collectAsState()
    val (mm, ss) = TimeAndAlertUtils.formatMinutesAndSeconds(secs)
    Text(
        text = "Eye break in $mm:$ss · tap to start",
        fontSize = m.sp(11f), color = theme.textSecondary, maxLines = 1,
        modifier = modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { viewModel.triggerEyeBreakNow() }
            .padding(vertical = 8.dp)
    )
}

@Composable
internal fun LsProgressRing(progress: Float, color: Color, track: Color, size: Dp, stroke: Dp = 3.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = stroke.toPx()
        val inset = s / 2f
        val arcSize = Size(this.size.width - s, this.size.height - s)
        drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = s))
        drawArc(color = color, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
            topLeft = Offset(inset, inset), size = arcSize, style = Stroke(width = s, cap = StrokeCap.Round))
    }
}

@Composable
internal fun LsPill(text: String, theme: ClockVisualTheme, m: LsMetrics, onClick: () -> Unit, filled: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) theme.accentColor else theme.textPrimary.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            fontSize = m.sp(13f),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            color = if (filled) theme.onAccentColor else theme.textPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

/** Time, AM/PM and seconds, sized from one number so every style scales the same way. */
@Composable
internal fun LsTimeBlock(
    time: LsTime,
    showSeconds: Boolean,
    timeSize: TextUnit,
    weight: FontWeight,
    style: TextStyle,
    ampmColor: Color,
    secondsColor: Color,
    tracking: TextUnit
) {
    // Auto-fit: if the digits are wider than the space left beside the AM/PM column, shrink until they fit.
    // The estimate in timeSize is only a starting point, so this works for any font, font scale or screen.
    var fit by remember(timeSize) { mutableStateOf(1f) }
    val size = timeSize * fit
    Row(verticalAlignment = Alignment.Top) {
        // Weighted children are measured after the unweighted AM/PM column, so that column can never be squeezed to zero.
        Text(
            text = time.hm,
            fontSize = size,
            lineHeight = size * 0.9f,
            fontFamily = ChronometerDisplayFont,
            fontWeight = weight,
            maxLines = 1,
            softWrap = false,
            style = style.copy(fontFeatureSettings = "tnum", letterSpacing = tracking * fit),
            modifier = Modifier.weight(1f, fill = false),
            onTextLayout = { if (it.didOverflowWidth && fit > 0.25f) fit *= 0.93f }
        )
        Column(Modifier.padding(start = 6.dp, top = (size.value * 0.12f).dp)) {
            if (time.ampm.isNotEmpty()) {
                Text(time.ampm, fontSize = size * 0.14f, fontWeight = FontWeight.Bold, color = ampmColor, maxLines = 1, softWrap = false)
            }
            if (showSeconds) {
                Text(time.seconds, fontSize = size * 0.1f, fontFamily = ChronometerMonoFont, color = secondsColor, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
internal fun LsControlsHeader(
    visible: Boolean,
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    onOpenCustomThemeStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncInspector: () -> Unit,
    onOpenBreathing: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(activeTheme.bgStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            StationTopHeaderBar(
                currentLocalTime = currentLocalTime,
                settings = settings,
                activeTheme = activeTheme,
                onToggleWorldClock = { viewModel.toggleWorldClockBesideMain(!settings.showWorldClockBesideMain) },
                onOpenThemeStudio = onOpenCustomThemeStudio,
                onToggleChime = { viewModel.toggleChimeEnabled(it) },
                onOpenSettings = onOpenSettings,
                onOpenSyncInspector = onOpenSyncInspector,
                onOpenBreathing = onOpenBreathing
            )
        }
    }
}

/** Snooze / restart / add-city actions, centred on a card so they stay legible on any background. */
@Composable
internal fun LsChimeActions(
    visible: Boolean,
    theme: ClockVisualTheme,
    m: LsMetrics,
    viewModel: ClockViewModel,
    onAddCity: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(shape = RoundedCornerShape(28.dp), color = theme.bgStart.copy(alpha = 0.9f)) {
            val a: @Composable () -> Unit = { LsPill("Snooze 5m", theme, m, { viewModel.snoozeChime(5) }) }
            val b: @Composable () -> Unit = { LsPill("+10m", theme, m, { viewModel.snoozeChime(10) }) }
            val c: @Composable () -> Unit = { LsPill("Restart", theme, m, { viewModel.resetCurrentInterval() }) }
            val d: @Composable () -> Unit = { LsPill("+ Add city", theme, m, onAddCity, filled = true) }
            if (m.w < 520.dp) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { a(); b() }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { c(); d() }
                }
            } else {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    a(); b(); c(); d()
                }
            }
        }
    }
}

private val LsDay = Color(0xFFFFD36B)
private val LsNight = Color(0xFFA9B4FF)

// ---------------------------------------------------------------------------------------------
// BOLD: giant time over a colour wash, ticker of cities, chime progress along the bottom edge
// ---------------------------------------------------------------------------------------------

@Composable
fun BoldClockLayout(
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    friendClocks: List<FriendClock>,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    onAddFriendClicked: () -> Unit,
    onOpenCustomThemeStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncInspector: () -> Unit,
    onOpenBreathing: () -> Unit
) {
    val reveal = rememberControlsReveal()
    var pendingDelete by remember { mutableStateOf<LsCity?>(null) }
    val time = rememberLsTime(currentLocalTime, settings.digital24HourFormat)
    val chime = rememberLsChime(settings, viewModel)
    val cities = rememberLsCities(currentLocalTime, settings, friendClocks)
    val ink = activeTheme.textPrimary
    val inkSoft = activeTheme.textSecondary

    LsBox(
        modifier = Modifier
            .fillMaxSize()
            .background(activeTheme.bgStart)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(activeTheme.accentColor.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(0f, 0f), radius = size.maxDimension * 0.6f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(activeTheme.numberColor.copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(size.width, size.height), radius = size.maxDimension * 0.55f
                    )
                )
            }
            .tapToToggle(reveal)
    ) { m ->
        val wf = when { m.landscape -> 0.34f; m.phone -> 0.26f; else -> 0.30f }
        val hf = when { m.shortLandscape -> 0.36f; m.landscape -> 0.31f; else -> 0.22f }
        val timeSize = lsTimeSize(m, wf, hf)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .lsBurnInDrift(currentLocalTime.minute)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = m.pad, vertical = m.pad * 0.7f)
        ) {
            if (m.landscape) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(
                        text = "MERIDIAN · ${currentLocalTime.zone.id.substringAfterLast('/').replace('_', ' ').uppercase(Locale.US)}",
                        fontSize = m.sp(12f), letterSpacing = 3.sp, fontWeight = FontWeight.Medium, color = inkSoft,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(chime.label, fontSize = m.sp(20f), fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                        if (!m.shortLandscape) {
                            Text(chime.sub.uppercase(Locale.US), fontSize = m.sp(10f), letterSpacing = 2.sp, color = inkSoft, maxLines = 1)
                        }
                        LsEyeBreak(settings, activeTheme, m, viewModel)
                    }
                }
            } else {
                Text("MERIDIAN", fontSize = m.sp(12f), letterSpacing = 4.sp, fontWeight = FontWeight.Medium, color = inkSoft)
            }

            Spacer(Modifier.weight(1f))

            LsTimeBlock(
                time = time, showSeconds = settings.showSeconds, timeSize = timeSize, weight = FontWeight.ExtraBold,
                style = TextStyle(color = ink), ampmColor = ink, secondsColor = inkSoft, tracking = (-0.04f * timeSize.value).sp
            )
            Text(
                text = time.date,
                fontSize = m.sp(if (m.shortLandscape) 14f else if (m.landscape) 20f else 15f),
                letterSpacing = 5.sp, fontWeight = FontWeight.Light, color = inkSoft, maxLines = 1,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (!m.landscape) {
                Column(Modifier.padding(top = 14.dp)) {
                    Text(chime.label, fontSize = m.sp(20f), fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                    Text(chime.sub.uppercase(Locale.US), fontSize = m.sp(10f), letterSpacing = 2.sp, color = inkSoft, maxLines = 1)
                    LsEyeBreak(settings, activeTheme, m, viewModel)
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(ink.copy(alpha = 0.22f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
                    }
                    .padding(vertical = if (m.compactCells) 6.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (m.compactCells) 4.dp else 10.dp)
            ) {
                cities.chunked(m.cityColumns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { city ->
                            Column(Modifier.weight(1f).lsCityGestures({ reveal.value = !reveal.value }) { pendingDelete = city }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (city.night) LsNight else LsDay))
                                    Text(
                                        text = city.name.uppercase(Locale.US),
                                        fontSize = m.sp(10f), letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium,
                                        color = if (city.home) ink else inkSoft,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 5.dp)
                                    )
                                }
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        city.time, fontSize = m.sp(if (m.compactCells) 20f else if (m.landscape) 28f else 24f),
                                        fontWeight = FontWeight.Bold, fontFamily = ChronometerDisplayFont, color = ink, maxLines = 1,
                                        style = TextStyle(fontFeatureSettings = "tnum")
                                    )
                                    if (city.ampm.isNotEmpty()) {
                                        Text(city.ampm, fontSize = m.sp(10f), color = inkSoft, maxLines = 1,
                                            modifier = Modifier.padding(start = 3.dp, bottom = 4.dp))
                                    }
                                }
                                if (!m.compactCells) {
                                    Text(city.offset, fontSize = m.sp(9f), fontFamily = ChronometerMonoFont, color = inkSoft, maxLines = 1)
                                }
                            }
                        }
                        repeat(m.cityColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.14f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(chime.progress).background(ink))
            }
        }

        LsCityActionsDialog(pendingDelete, activeTheme, viewModel, onDismiss = { pendingDelete = null })
        LsChimeActions(reveal.value, activeTheme, m, viewModel, onAddFriendClicked, if (m.landscape) Modifier.align(Alignment.CenterEnd).padding(end = 24.dp) else Modifier.align(Alignment.TopCenter).padding(top = 150.dp))
        LsControlsHeader(
            reveal.value, currentLocalTime, settings, activeTheme, viewModel,
            onOpenCustomThemeStudio, onOpenSettings, onOpenSyncInspector, onOpenBreathing,
            Modifier.align(Alignment.TopCenter)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// CALM: centred, quiet, one soft accent; cities in a slim card row
// ---------------------------------------------------------------------------------------------

@Composable
fun CalmClockLayout(
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    friendClocks: List<FriendClock>,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    onAddFriendClicked: () -> Unit,
    onOpenCustomThemeStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncInspector: () -> Unit,
    onOpenBreathing: () -> Unit
) {
    val reveal = rememberControlsReveal()
    var pendingDelete by remember { mutableStateOf<LsCity?>(null) }
    val time = rememberLsTime(currentLocalTime, settings.digital24HourFormat)
    val chime = rememberLsChime(settings, viewModel)
    val cities = rememberLsCities(currentLocalTime, settings, friendClocks)
    val ink = activeTheme.textPrimary
    val inkSoft = activeTheme.textSecondary

    LsBox(
        modifier = Modifier
            .fillMaxSize()
            .background(activeTheme.bgStart)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(activeTheme.accentColor.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.42f), radius = size.maxDimension * 0.5f
                    )
                )
            }
            .tapToToggle(reveal)
    ) { m ->
        val wf = when { m.landscape -> 0.21f; m.phone -> 0.25f; else -> 0.27f }
        val hf = when { m.shortLandscape -> 0.36f; m.landscape -> 0.30f; else -> 0.18f }
        val timeSize = lsTimeSize(m, wf, hf)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .lsBurnInDrift(currentLocalTime.minute)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = m.pad, vertical = m.pad * 0.7f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!m.shortLandscape) {
                Text("MERIDIAN", fontSize = m.sp(11f), letterSpacing = 5.sp, color = inkSoft.copy(alpha = 0.7f))
            }
            Spacer(Modifier.weight(1f))

            LsTimeBlock(
                time = time, showSeconds = settings.showSeconds, timeSize = timeSize, weight = FontWeight.Light,
                style = TextStyle(brush = Brush.verticalGradient(listOf(activeTheme.numberColor, activeTheme.accentColor))),
                ampmColor = activeTheme.accentColor, secondsColor = inkSoft, tracking = (-0.03f * timeSize.value).sp
            )
            Text(
                time.date, fontSize = m.sp(if (m.shortLandscape) 13f else if (m.landscape) 18f else 14f), letterSpacing = 6.sp,
                fontWeight = FontWeight.Light, color = inkSoft, maxLines = 1, modifier = Modifier.padding(top = 8.dp)
            )

            Surface(shape = CircleShape, color = ink.copy(alpha = 0.05f), modifier = Modifier.padding(top = if (m.shortLandscape) 8.dp else 18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(start = 8.dp, end = 18.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    LsProgressRing(chime.progress, activeTheme.accentColor, ink.copy(alpha = 0.12f), if (m.shortLandscape) 24.dp else 30.dp)
                    Column {
                        Text(chime.label, fontSize = m.sp(14f), color = ink, maxLines = 1)
                        if (!m.shortLandscape) Text(chime.sub, fontSize = m.sp(10f), color = inkSoft, maxLines = 1)
                    }
                }
            }
            LsEyeBreak(settings, activeTheme, m, viewModel)

            Spacer(Modifier.weight(1f))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (m.compactCells) 6.dp else 10.dp)) {
                cities.chunked(m.cityColumns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (m.compactCells) 6.dp else 10.dp)) {
                        row.forEach { city ->
                            Surface(
                                shape = RoundedCornerShape(if (m.compactCells) 12.dp else 16.dp),
                                color = if (city.home) activeTheme.accentColor.copy(alpha = 0.10f) else ink.copy(alpha = 0.04f),
                                modifier = Modifier.weight(1f).lsCityGestures({ reveal.value = !reveal.value }) { pendingDelete = city }
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = if (m.compactCells) 6.dp else 10.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(city.name, fontSize = m.sp(11f), color = inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (city.night) Color(0xFF7F8CFF) else activeTheme.accentColor))
                                    }
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            city.time, fontSize = m.sp(if (m.compactCells) 18f else 24f), fontWeight = FontWeight.Light,
                                            fontFamily = ChronometerDisplayFont, color = ink, maxLines = 1,
                                            style = TextStyle(fontFeatureSettings = "tnum")
                                        )
                                        if (city.ampm.isNotEmpty()) {
                                            Text(city.ampm, fontSize = m.sp(9f), color = inkSoft, maxLines = 1, modifier = Modifier.padding(start = 3.dp, bottom = 4.dp))
                                        }
                                    }
                                    if (!m.compactCells) {
                                        Text(city.offset, fontSize = m.sp(9f), fontFamily = ChronometerMonoFont, color = inkSoft.copy(alpha = 0.7f), maxLines = 1)
                                    }
                                }
                            }
                        }
                        repeat(m.cityColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        LsCityActionsDialog(pendingDelete, activeTheme, viewModel, onDismiss = { pendingDelete = null })
        LsChimeActions(reveal.value, activeTheme, m, viewModel, onAddFriendClicked, if (m.landscape) Modifier.align(Alignment.CenterEnd).padding(end = 24.dp) else Modifier.align(Alignment.TopCenter).padding(top = 150.dp))
        LsControlsHeader(
            reveal.value, currentLocalTime, settings, activeTheme, viewModel,
            onOpenCustomThemeStudio, onOpenSettings, onOpenSyncInspector, onOpenBreathing,
            Modifier.align(Alignment.TopCenter)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// DASHBOARD: two soft cards (clock + chime, world grid); header controls always visible
// ---------------------------------------------------------------------------------------------

@Composable
fun DashboardClockLayout(
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    friendClocks: List<FriendClock>,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    onAddFriendClicked: () -> Unit,
    onOpenCustomThemeStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncInspector: () -> Unit,
    onOpenBreathing: () -> Unit
) {
    val time = rememberLsTime(currentLocalTime, settings.digital24HourFormat)
    val chime = rememberLsChime(settings, viewModel)
    val cities = rememberLsCities(currentLocalTime, settings, friendClocks)
    val ink = activeTheme.textPrimary
    val inkSoft = activeTheme.textSecondary
    val surface = activeTheme.cardBackground
    val inset = ink.copy(alpha = 0.06f)
    var pendingDelete by remember { mutableStateOf<LsCity?>(null) }

    LsBox(Modifier.fillMaxSize().background(activeTheme.bgStart)) { m ->
        LsCityActionsDialog(pendingDelete, activeTheme, viewModel, onDismiss = { pendingDelete = null })
        val gap = m.pad * 0.5f
        // Cards are narrower than the screen, so time takes a smaller share of the width than in Bold/Calm.
        val wf = when { m.landscape -> 0.13f; m.phone -> 0.24f; else -> 0.26f }
        val hf = when { m.shortLandscape -> 0.30f; m.landscape -> 0.26f; else -> 0.14f }
        val timeSize = lsTimeSize(m, wf, hf)
        val cardPad = if (m.shortLandscape) 12.dp else 20.dp
        // City cells are short on phones (landscape, or portrait where 4 rows share the card), so drop the offset line.
        val tight = m.shortLandscape || (m.phone && !m.landscape)
        // City cells: phone portrait has 2 columns; a short phone-landscape card has no room for the offset line.
        val dashColumns = when {
            m.shortLandscape -> 3
            m.landscape -> 2
            m.phone -> 2
            else -> 3
        }

        Column(
            modifier = Modifier.fillMaxSize().lsBurnInDrift(currentLocalTime.minute).windowInsetsPadding(WindowInsets.safeDrawing).padding(m.pad),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            StationTopHeaderBar(
                currentLocalTime = currentLocalTime,
                settings = settings,
                activeTheme = activeTheme,
                onToggleWorldClock = { viewModel.toggleWorldClockBesideMain(!settings.showWorldClockBesideMain) },
                onOpenThemeStudio = onOpenCustomThemeStudio,
                onToggleChime = { viewModel.toggleChimeEnabled(it) },
                onOpenSettings = onOpenSettings,
                onOpenSyncInspector = onOpenSyncInspector,
                onOpenBreathing = onOpenBreathing
            )

            @Composable
            fun HeroCard(modifier: Modifier) {
                Surface(shape = RoundedCornerShape(26.dp), color = surface, modifier = modifier) {
                    Column(Modifier.fillMaxSize().padding(cardPad), verticalArrangement = Arrangement.SpaceBetween) {
                        if (!m.shortLandscape) Text("MAIN CLOCK", fontSize = m.sp(10f), letterSpacing = 3.sp, color = inkSoft)
                        Column {
                            LsTimeBlock(
                                time = time, showSeconds = settings.showSeconds, timeSize = timeSize, weight = FontWeight.Light,
                                style = TextStyle(color = ink), ampmColor = activeTheme.accentColor, secondsColor = inkSoft,
                                tracking = (-0.03f * timeSize.value).sp
                            )
                            Text(time.date, fontSize = m.sp(if (m.shortLandscape) 11f else 14f), letterSpacing = 3.sp, color = inkSoft, maxLines = 1)
                        }
                        Surface(shape = RoundedCornerShape(16.dp), color = inset) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (m.shortLandscape) 6.dp else 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LsProgressRing(chime.progress, activeTheme.accentColor, ink.copy(alpha = 0.12f), if (m.shortLandscape) 28.dp else 34.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(chime.label, fontSize = m.sp(14f), fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
                                    if (!m.shortLandscape) Text(chime.sub, fontSize = m.sp(10f), color = inkSoft, maxLines = 1)
                                }
                                if (m.w >= 420.dp) {
                                    LsPill("Snooze 5m", activeTheme, m, { viewModel.snoozeChime(5) })
                                }
                                LsPill("+10m", activeTheme, m, { viewModel.snoozeChime(10) }, filled = true)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LsPill("Restart", activeTheme, m, { viewModel.resetCurrentInterval() })
                            LsEyeBreak(settings, activeTheme, m, viewModel)
                        }
                    }
                }
            }

            @Composable
            fun WorldCard(modifier: Modifier) {
                Surface(shape = RoundedCornerShape(26.dp), color = surface, modifier = modifier) {
                    Column(Modifier.fillMaxSize().padding(cardPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("WORLD CLOCK", fontSize = m.sp(10f), letterSpacing = 3.sp, color = inkSoft)
                            LsPill("+ Add city", activeTheme, m, onAddFriendClicked, filled = true)
                        }
                        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            cities.chunked(dashColumns).forEach { row ->
                                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { city ->
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (city.home) activeTheme.accentColor.copy(alpha = 0.14f) else inset,
                                            modifier = Modifier.weight(1f).fillMaxHeight().lsCityGestures({}) { pendingDelete = city }
                                        ) {
                                            Column(
                                                Modifier.padding(horizontal = 12.dp, vertical = if (m.shortLandscape) 6.dp else 10.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text(city.name, fontSize = m.sp(12f), fontWeight = FontWeight.Medium, color = inkSoft,
                                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (city.night) Color(0xFF8B9BFF) else activeTheme.accentColor))
                                                }
                                                Row(verticalAlignment = Alignment.Bottom) {
                                                    Text(city.time, fontSize = m.sp(if (tight) 19f else 26f), fontFamily = ChronometerDisplayFont,
                                                        color = ink, maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"))
                                                    if (city.ampm.isNotEmpty()) {
                                                        Text(city.ampm, fontSize = m.sp(9f), color = inkSoft, maxLines = 1, modifier = Modifier.padding(start = 3.dp, bottom = 4.dp))
                                                    }
                                                }
                                                if (!tight) {
                                                    Text(city.offset, fontSize = m.sp(10f), fontFamily = ChronometerMonoFont, color = inkSoft.copy(alpha = 0.75f), maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                    repeat(dashColumns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }

            if (m.landscape) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    HeroCard(Modifier.weight(1.1f).fillMaxHeight())
                    WorldCard(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                    HeroCard(Modifier.weight(0.42f).fillMaxWidth())
                    WorldCard(Modifier.weight(0.58f).fillMaxWidth())
                }
            }
        }
    }
}
