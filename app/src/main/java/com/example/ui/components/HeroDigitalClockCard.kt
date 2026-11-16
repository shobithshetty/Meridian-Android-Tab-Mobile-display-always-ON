package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AlarmItem
import com.example.data.ClockSettings
import com.example.data.FriendClock
import com.example.ui.ClockViewModel
import com.example.ui.theme.ChronometerDisplayFont
import com.example.ui.theme.ChronometerMonoFont
import com.example.ui.theme.CircadianPhase
import com.example.ui.theme.ClockVisualTheme
import com.example.ui.theme.computeCircadianProfile
import com.example.util.TimeAndAlertUtils
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StationTopHeaderBar(
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    activeTheme: ClockVisualTheme,
    onToggleWorldClock: () -> Unit,
    onOpenThemeStudio: () -> Unit,
    onToggleChime: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncInspector: () -> Unit = {},
    onOpenBreathing: () -> Unit = {}
) {
    val isDst = remember(currentLocalTime.zone.id, currentLocalTime.hour, currentLocalTime.minute) {
        try {
            currentLocalTime.zone.rules.isDaylightSavings(currentLocalTime.toInstant())
        } catch (_: Exception) {
            false
        }
    }

    @Composable
    fun HeaderTitleAndBadges(modifier: Modifier = Modifier) {
        Column(modifier = modifier) {
            Text(
                text = stringResource(R.string.station_header_title),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.6.sp,
                color = activeTheme.accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Zone: ${currentLocalTime.zone.id}",
                    fontSize = 11.sp,
                    color = activeTheme.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = activeTheme.cardBackground,
                    border = BorderStroke(
                        0.85.dp,
                        if (isDst) Color(0xFFF59E0B) else activeTheme.dividerColor
                    )
                ) {
                    Text(
                        text = if (isDst) "DST ACTIVE" else "STANDARD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ChronometerMonoFont,
                        color = if (isDst) Color(0xFFF59E0B) else activeTheme.textPrimary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }

                if (settings.circadianShiftEnabled) {
                    val circadian = remember(currentLocalTime.hour, currentLocalTime.minute) {
                        computeCircadianProfile(currentLocalTime)
                    }
                    val cueTint = when (circadian.phase) {
                        CircadianPhase.DAYLIGHT_NEUTRAL -> activeTheme.accentColor
                        else -> activeTheme.circadianCueColor
                    }
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = activeTheme.cardBackground,
                        border = BorderStroke(0.85.dp, cueTint)
                    ) {
                        Text(
                            text = circadian.label.uppercase(Locale.US),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ChronometerMonoFont,
                            color = cueTint,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun HeaderActionIcons(includeSwitch: Boolean) {
        val launchFocus = LocalFocusLauncher.current
        IconButton(
            onClick = launchFocus,
            modifier = Modifier
                .size(48.dp)
                .testTag("open_focus_session_button")
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Start a focus session",
                tint = activeTheme.accentColor
            )
        }

        val launchAmbient = LocalAmbientLauncher.current
        IconButton(
            onClick = launchAmbient,
            modifier = Modifier
                .size(48.dp)
                .testTag("open_ambient_sound_button")
        ) {
            Icon(
                imageVector = Icons.Default.Bedtime,
                contentDescription = "Background sound and sleep timer",
                tint = activeTheme.accentColor
            )
        }

        IconButton(
            onClick = onOpenSyncInspector,
            modifier = Modifier
                .size(48.dp)
                .testTag("open_clock_sync_inspector_button")
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Inspect Real-time Clock & DST Synchronization",
                tint = activeTheme.accentColor
            )
        }

        IconButton(
            onClick = onOpenBreathing,
            modifier = Modifier
                .size(48.dp)
                .testTag("open_breathing_guide_button")
        ) {
            Icon(
                imageVector = Icons.Default.SelfImprovement,
                contentDescription = stringResource(R.string.cd_open_breathing_guide),
                tint = activeTheme.accentColor
            )
        }

        IconButton(
            onClick = onToggleWorldClock,
            modifier = Modifier
                .size(48.dp)
                .testTag("toggle_world_widget_button")
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = stringResource(R.string.cd_toggle_world_widget),
                tint = if (settings.showWorldClockBesideMain) activeTheme.accentColor else activeTheme.textSecondary
            )
        }

        IconButton(
            onClick = onOpenThemeStudio,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = stringResource(R.string.cd_open_theme_studio),
                tint = activeTheme.accentColor
            )
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .size(48.dp)
                .testTag("open_settings_hub_button")
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Open Settings & Tools Hub",
                tint = activeTheme.accentColor
            )
        }

        if (includeSwitch) {
            Switch(
                checked = settings.chimeEnabled,
                onCheckedChange = onToggleChime,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = activeTheme.onAccentColor,
                    checkedTrackColor = activeTheme.accentColor,
                    uncheckedThumbColor = activeTheme.textSecondary,
                    uncheckedTrackColor = activeTheme.dividerColor
                )
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 460.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderTitleAndBadges(modifier = Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    HeaderActionIcons(includeSwitch = true)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderTitleAndBadges(modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.chimeEnabled,
                        onCheckedChange = onToggleChime,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = activeTheme.onAccentColor,
                            checkedTrackColor = activeTheme.accentColor,
                            uncheckedThumbColor = activeTheme.textSecondary,
                            uncheckedTrackColor = activeTheme.dividerColor
                        )
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeaderActionIcons(includeSwitch = false)
                }
            }
        }
    }
}

@Composable
fun HeroDigitalClockCard(
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel? = null,
    modifier: Modifier = Modifier,
    compactVertical: Boolean = false,
    fillContainer: Boolean = false,
    dynamicPaddingHorizontal: androidx.compose.ui.unit.Dp = 20.dp,
    dynamicPaddingVertical: androidx.compose.ui.unit.Dp = 20.dp
) {
    Card(
        modifier = if (fillContainer) modifier.fillMaxWidth().fillMaxHeight() else modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = activeTheme.cardBackground
        ),
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.25.dp,
            highlighted = true
        )
    ) {
        Column(
            modifier = if (fillContainer) {
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = dynamicPaddingHorizontal, vertical = dynamicPaddingVertical)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = if (compactVertical) 12.dp else 18.dp
                    )
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (fillContainer) Arrangement.SpaceBetween else Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = activeTheme.accentColor,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "MAIN CLOCK",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = activeTheme.onAccentColor,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = if (settings.chimeEnabled) {
                        "Every ${settings.chimeIntervalMinutes}m Focus Alert"
                    } else {
                        "Interval Alerts Muted"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = activeTheme.textSecondary
                )
            }

            LargeDigitalClockDisplay(
                currentLocalTime = currentLocalTime,
                showSeconds = settings.showSeconds,
                use24Hour = settings.digital24HourFormat,
                activeTheme = activeTheme,
                compactVertical = compactVertical,
                fillContainer = fillContainer
            )

            // Integrated Snooze & Alert Countdown Sub-Deck (prominently visible right below main clock)
            if (viewModel != null) {
                IntegratedAlertSnoozeBar(
                    settings = settings,
                    activeTheme = activeTheme,
                    viewModel = viewModel,
                    currentLocalTime = currentLocalTime
                )
            }
        }
    }
}

@Composable
fun IntegratedAlertSnoozeBar(
    settings: ClockSettings,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    currentLocalTime: ZonedDateTime,
    modifier: Modifier = Modifier
) {
    val remainingSeconds: Int by viewModel.remainingChimeSeconds.collectAsState()
    val (minsLeft, formattedSecsLeft) = TimeAndAlertUtils.formatMinutesAndSeconds(remainingSeconds)

    val isQuietActive: Boolean = remember(
        currentLocalTime.minute,
        settings.quietHoursEnabled,
        settings.quietHoursStartHour,
        settings.quietHoursStartMinute,
        settings.quietHoursEndHour,
        settings.quietHoursEndMinute,
        settings.quietHoursDaysOfWeek
    ) {
        TimeAndAlertUtils.isWithinQuietHours(
            now = currentLocalTime,
            enabled = settings.quietHoursEnabled,
            startHour = settings.quietHoursStartHour,
            startMinute = settings.quietHoursStartMinute,
            endHour = settings.quietHoursEndHour,
            endMinute = settings.quietHoursEndMinute,
            activeDaysOfWeek = settings.quietHoursDaysOfWeek
        )
    }

    val totalCycleSeconds = (settings.chimeIntervalMinutes.coerceAtLeast(1) * 60).coerceAtLeast(1)
    val elapsedSeconds = (totalCycleSeconds - remainingSeconds).coerceIn(0, totalCycleSeconds)
    val progressRatio = (elapsedSeconds.toFloat() / totalCycleSeconds.toFloat()).coerceIn(0f, 1f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = activeTheme.bgEnd,
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Precision HD countdown ring with crisp orbital ticks
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp)
                    ) {
                        HdPrecisionCountdownRing(
                            progress = if (settings.chimeEnabled && !isQuietActive) progressRatio else 0f,
                            trackColor = activeTheme.dividerColor,
                            indicatorColor = if (isQuietActive) Color(0xFF818CF8) else activeTheme.circadianCueColor,
                            strokeWidth = 3.2.dp
                        )
                        Text(
                            text = "${settings.chimeIntervalMinutes}m",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = ChronometerMonoFont,
                            color = activeTheme.numberColor
                        )
                    }

                    Column {
                        if (isQuietActive) {
                            Text(
                                text = "Quiet Hours Active",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA5B4FC)
                            )
                            Text(
                                text = "Chime sounds muted until window ends",
                                fontSize = 10.sp,
                                color = activeTheme.textSecondary
                            )
                        } else if (settings.chimeEnabled) {
                            Text(
                                text = if (minsLeft == 0) "Next alert in ${remainingSeconds}s" else "Alert in ${minsLeft}m ${formattedSecsLeft}s",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = ChronometerMonoFont,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                color = activeTheme.numberColor
                            )
                            Text(
                                text = "Interval cycle (${settings.chimeIntervalMinutes} min)",
                                fontSize = 10.sp,
                                color = activeTheme.textSecondary
                            )
                        } else {
                            Text(
                                text = "Interval Alerts Paused",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeTheme.textSecondary
                            )
                        }
                    }
                }

                // Quick Quiet Hours indicator badge if configured
                if (settings.quietHoursEnabled && !isQuietActive) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = activeTheme.cardBackground,
                        border = BorderStroke(1.dp, activeTheme.dividerColor)
                    ) {
                        Text(
                            text = "🌙 ${settings.quietHoursStartHour}:00",
                            fontSize = 10.sp,
                            color = activeTheme.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (settings.eyeBreakEnabled) {
                val eyeSecsRemaining by viewModel.remainingEyeBreakSeconds.collectAsState()
                val (eyeMinsLeft, eyeSecsFormatted) = TimeAndAlertUtils.formatMinutesAndSeconds(eyeSecsRemaining)
                Surface(
                    onClick = { viewModel.triggerEyeBreakNow() },
                    shape = RoundedCornerShape(10.dp),
                    color = activeTheme.cardBackground,
                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 40.dp)
                        .testTag("eye_break_status_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Eye Rest Reminder Active",
                                tint = activeTheme.circadianCueColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Eye rest in ${eyeMinsLeft}m ${eyeSecsFormatted}s (${settings.eyeBreakIntervalMinutes}m)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ChronometerMonoFont,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                color = activeTheme.textPrimary
                            )
                        }
                        Text(
                            text = "TAKE BREAK",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeTheme.accentColor
                        )
                    }
                }
            }

            // Snooze 5m, +10m, and Restart Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { viewModel.snoozeChime(5) },
                    enabled = settings.chimeEnabled,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (settings.chimeEnabled) activeTheme.accentColor else activeTheme.dividerColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = activeTheme.cardBackground,
                        contentColor = activeTheme.accentColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("snooze_5m_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Snooze,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Snooze 5m",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.snoozeChime(10) },
                    enabled = settings.chimeEnabled,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = activeTheme.cardBackground,
                        contentColor = activeTheme.textPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("snooze_10m_button")
                ) {
                    Text(
                        text = "+10m",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = { viewModel.resetCurrentInterval() },
                    enabled = settings.chimeEnabled,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("reset_interval_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Restart Current Interval",
                        tint = if (settings.chimeEnabled) activeTheme.accentColor else activeTheme.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun LargeDigitalClockDisplay(
    currentLocalTime: ZonedDateTime,
    showSeconds: Boolean,
    use24Hour: Boolean,
    activeTheme: ClockVisualTheme,
    modifier: Modifier = Modifier,
    compactVertical: Boolean = false,
    fillContainer: Boolean = false
) {
    val hhPattern = if (use24Hour) "HH" else "hh"
    val hhFormatter = remember(use24Hour) { DateTimeFormatter.ofPattern(hhPattern, Locale.US) }
    val mmFormatter = remember { DateTimeFormatter.ofPattern("mm", Locale.US) }
    val ssFormatter = remember { DateTimeFormatter.ofPattern("ss", Locale.US) }
    val amPmFormatter = remember { DateTimeFormatter.ofPattern("a", Locale.US) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US) }

    val amPmString = currentLocalTime.format(amPmFormatter)

    // Responsive container-based digit sizing: scales generously to fill widescreen tablets
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val widthDpVal = maxWidth.value
        val scaleFactor = when {
            fillContainer && (!showSeconds || use24Hour) -> 0.26f
            fillContainer -> 0.22f
            showSeconds && !use24Hour -> 0.165f
            showSeconds || !use24Hour -> 0.185f
            else -> 0.22f
        }
        val maxCap = when {
            fillContainer -> 140f
            compactVertical -> 60f
            else -> 92f
        }
        val computedDigitSp = (widthDpVal * scaleFactor).coerceIn(38f, maxCap)
        val secondsSp = (computedDigitSp * 0.34f).coerceIn(14f, 38f)
        val amPmSp = (computedDigitSp * 0.22f).coerceIn(11f, 24f)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val colonAlpha by animateFloatAsState(
                targetValue = if (currentLocalTime.second % 2 == 0) 1f else 0.30f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                label = "HeroColonAlpha"
            )
            // Razor-sharp glyphs with zero radial bloom or shadow blur
            val heroDigitStyle = TextStyle(
                fontFeatureSettings = "tnum",
                letterSpacing = (-1.5).sp
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = if (compactVertical) 2.dp else 6.dp)
            ) {
                Text(
                    text = currentLocalTime.format(hhFormatter),
                    fontSize = computedDigitSp.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ChronometerDisplayFont,
                    color = activeTheme.numberColor,
                    lineHeight = computedDigitSp.sp,
                    style = heroDigitStyle,
                    maxLines = 1
                )

                Text(
                    text = ":",
                    fontSize = computedDigitSp.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ChronometerDisplayFont,
                    color = activeTheme.numberColor.copy(alpha = colonAlpha),
                    lineHeight = computedDigitSp.sp,
                    style = heroDigitStyle,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Text(
                    text = currentLocalTime.format(mmFormatter),
                    fontSize = computedDigitSp.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ChronometerDisplayFont,
                    color = activeTheme.numberColor,
                    lineHeight = computedDigitSp.sp,
                    style = heroDigitStyle,
                    maxLines = 1
                )

                if (showSeconds || !use24Hour) {
                    Column(
                        modifier = Modifier.padding(
                            start = 8.dp,
                            bottom = (computedDigitSp * 0.12f).dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        if (showSeconds) {
                            Text(
                                text = currentLocalTime.format(ssFormatter),
                                fontSize = secondsSp.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = ChronometerMonoFont,
                                color = activeTheme.numberColor.copy(alpha = 0.92f),
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                maxLines = 1
                            )
                        }
                        if (!use24Hour) {
                            Text(
                                text = amPmString,
                                fontSize = amPmSp.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = ChronometerDisplayFont,
                                letterSpacing = 0.8.sp,
                                color = activeTheme.textSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Text(
                text = currentLocalTime.format(dateFormatter).uppercase(Locale.US),
                fontSize = if (compactVertical) 11.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                color = activeTheme.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CompanionWorkspaceCard(
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    friendClocks: List<FriendClock>,
    alarms: List<AlarmItem>,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onAddAlarmClicked: () -> Unit,
    onExportClicked: () -> Unit,
    onImportClicked: () -> Unit,
    onOpenCustomThemeStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Segmented Tabs with >= 48dp accessible touch targets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(activeTheme.bgEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple("PLANNER", Icons.Default.DateRange, 0),
                    Triple("BREATH", Icons.Default.Favorite, 1),
                    Triple("ALARMS", Icons.Default.Notifications, 2),
                    Triple("THEMES", Icons.Default.Palette, 3)
                ).forEach { (label, icon, idx) ->
                    val isSelected = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) activeTheme.accentColor else Color.Transparent)
                            .clickable { onTabSelected(idx) }
                            .testTag("companion_tab_$idx"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = "$label Tab",
                                tint = if (isSelected) activeTheme.onAccentColor else activeTheme.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) activeTheme.onAccentColor else activeTheme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> TimeOverlapPlannerPane(
                    friendClocks = friendClocks,
                    currentLocalTime = currentLocalTime,
                    use24Hour = settings.digital24HourFormat,
                    activeTheme = activeTheme
                )
                1 -> PacedBreathingBubble(
                    settings = settings,
                    activeTheme = activeTheme,
                    viewModel = viewModel,
                    forceExpanded = true
                )
                2 -> AlarmsListPane(
                    alarms = alarms,
                    use24Hour = settings.digital24HourFormat,
                    activeTheme = activeTheme,
                    onAddClicked = onAddAlarmClicked,
                    onToggleAlarm = { viewModel.toggleAlarm(it) },
                    onDeleteAlarm = { viewModel.deleteAlarm(it) }
                )
                else -> AdhdSettingsPane(
                    settings = settings,
                    activeTheme = activeTheme,
                    viewModel = viewModel,
                    onExportClicked = onExportClicked,
                    onImportClicked = onImportClicked,
                    onOpenCustomThemeStudio = onOpenCustomThemeStudio
                )
            }
        }
    }
}
