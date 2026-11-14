package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FriendClock
import com.example.ui.theme.ChronometerDisplayFont
import com.example.ui.theme.ChronometerMonoFont
import com.example.ui.theme.ClockVisualTheme
import com.example.util.TimeAndAlertUtils
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TimeOverlapPlannerPane(
    friendClocks: List<FriendClock>,
    currentLocalTime: ZonedDateTime,
    use24Hour: Boolean,
    activeTheme: ClockVisualTheme
) {
    var isCustomHourMode by remember { mutableStateOf(false) }
    var customHour by remember { mutableFloatStateOf(currentLocalTime.hour.toFloat()) }

    val clipboardManager = LocalClipboardManager.current
    var copiedToastShowing by remember { mutableStateOf(false) }

    LaunchedEffect(copiedToastShowing) {
        if (copiedToastShowing) {
            delay(2000)
            copiedToastShowing = false
        }
    }

    val simulatedLocalTime = remember(currentLocalTime, isCustomHourMode, customHour) {
        if (isCustomHourMode) {
            currentLocalTime.withHour(customHour.toInt().coerceIn(0, 23)).withMinute(0)
        } else {
            currentLocalTime
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
        shape = RoundedCornerShape(18.dp),
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Meeting Planner Icon",
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MEETING PLANNER & SCRUBBER",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = activeTheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (friendClocks.isNotEmpty()) {
                    Surface(
                        onClick = {
                            val timeFormat = DateTimeFormatter.ofPattern(
                                if (use24Hour) "HH:mm" else "hh:mm a",
                                Locale.US
                            )
                            val localStr = "Local (${currentLocalTime.zone.id}): ${simulatedLocalTime.format(timeFormat)}"
                            val friendStrs = friendClocks.map { friend ->
                                val targetZ = TimeAndAlertUtils.safeZoneId(friend.timezoneId)
                                val friendTime = simulatedLocalTime.withZoneSameInstant(targetZ)
                                "${friend.cityLabel.ifBlank { friend.friendName }}: ${friendTime.format(timeFormat)}"
                            }
                            val summary = (listOf(localStr) + friendStrs).joinToString(" | ")
                            clipboardManager.setText(AnnotatedString(summary))
                            copiedToastShowing = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = activeTheme.accentColor.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, activeTheme.accentColor),
                        modifier = Modifier.defaultMinSize(minHeight = 38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (copiedToastShowing) Icons.Default.Check else Icons.Default.Share,
                                contentDescription = "Copy Summary",
                                tint = activeTheme.accentColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (copiedToastShowing) "COPIED!" else "COPY TIMES",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeTheme.accentColor
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = activeTheme.bgEnd,
                border = BorderStroke(1.dp, activeTheme.dividerColor)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    val timeFormat = remember(use24Hour) {
                        DateTimeFormatter.ofPattern(
                            if (use24Hour) "HH:mm (EEE, MMM d)" else "hh:mm a (EEE, MMM d)",
                            Locale.US
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Hour Scrubber:",
                            fontSize = 11.sp,
                            color = activeTheme.textSecondary
                        )
                        Text(
                            text = simulatedLocalTime.format(timeFormat),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = ChronometerMonoFont,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = activeTheme.numberColor
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Slider(
                        value = customHour,
                        onValueChange = {
                            isCustomHourMode = true
                            customHour = it
                        },
                        valueRange = 0f..23f,
                        steps = 22,
                        colors = SliderDefaults.colors(
                            thumbColor = activeTheme.accentColor,
                            activeTrackColor = activeTheme.accentColor,
                            inactiveTrackColor = activeTheme.dividerColor
                        ),
                        modifier = Modifier.fillMaxWidth().height(34.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val presetHours = remember(currentLocalTime.hour) {
                        listOf(
                            "NOW" to currentLocalTime.hour,
                            "9 AM Work" to 9,
                            "2 PM Meet" to 14,
                            "7 PM Eve" to 19
                        )
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(presetHours, key = { it.first }) { (label, hr) ->
                            val isSel = (label == "NOW" && !isCustomHourMode) ||
                                (isCustomHourMode && customHour.toInt() == hr)
                            Surface(
                                onClick = {
                                    if (label == "NOW") {
                                        isCustomHourMode = false
                                        customHour = currentLocalTime.hour.toFloat()
                                    } else {
                                        isCustomHourMode = true
                                        customHour = hr.toFloat()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) activeTheme.accentColor else Color.Transparent,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSel) activeTheme.accentColor else activeTheme.dividerColor.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) activeTheme.onAccentColor else activeTheme.textSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (friendClocks.isEmpty()) {
                Text(
                    text = "Add world cities to compare timezone overlap and working hours.",
                    fontSize = 11.sp,
                    color = activeTheme.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                )
            } else {
                val fmt = remember(use24Hour) {
                    DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "hh:mm a", Locale.US)
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    friendClocks.forEach { friend ->
                        val targetZone = remember(friend.timezoneId) {
                            TimeAndAlertUtils.safeZoneId(friend.timezoneId)
                        }
                        val friendSimulatedTime = remember(simulatedLocalTime, targetZone) {
                            simulatedLocalTime.withZoneSameInstant(targetZone)
                        }

                        val friendHour = friendSimulatedTime.hour
                        val (statusText, statusColor, statusBg) = when {
                            friendHour in 9..17 -> Triple(
                                "WORK HOURS",
                                Color(0xFF10B981),
                                Color(0xFF10B981).copy(alpha = 0.16f)
                            )
                            friendHour in 7..22 -> Triple(
                                "AWAKE",
                                Color(0xFFF59E0B),
                                Color(0xFFF59E0B).copy(alpha = 0.16f)
                            )
                            else -> Triple(
                                "SLEEP / DND",
                                Color(0xFF8B5CF6),
                                Color(0xFF8B5CF6).copy(alpha = 0.16f)
                            )
                        }

                        // Fixed New Year boundary date-difference bug using ChronoUnit.DAYS
                        val dayBadge = TimeAndAlertUtils.computeDayDifferenceBadge(
                            simulatedLocalTime,
                            friendSimulatedTime
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = activeTheme.bgEnd,
                            border = BorderStroke(1.dp, activeTheme.dividerColor)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = friend.cityLabel.ifBlank { friend.friendName },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeTheme.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${friend.timezoneId} • $dayBadge",
                                        fontSize = 10.sp,
                                        color = activeTheme.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = friendSimulatedTime.format(fmt),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = ChronometerDisplayFont,
                                        style = TextStyle(fontFeatureSettings = "tnum"),
                                        color = activeTheme.numberColor
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = statusBg
                                    ) {
                                        Text(
                                            text = statusText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
