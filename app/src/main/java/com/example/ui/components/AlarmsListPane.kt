package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AlarmItem
import com.example.ui.theme.ChronometerDisplayFont
import com.example.ui.theme.ClockVisualTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AlarmLocalItem(
    alarm: AlarmItem,
    use24Hour: Boolean,
    activeTheme: ClockVisualTheme,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val safeHour = alarm.hour.coerceIn(0, 23)
    val safeMinute = alarm.minute.coerceIn(0, 59)
    val time = LocalTime.of(safeHour, safeMinute)
    val pattern = if (use24Hour) "HH:mm" else "hh:mm a"
    val formatter = remember(use24Hour) { DateTimeFormatter.ofPattern(pattern, Locale.US) }
    val timeString = time.format(formatter)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) activeTheme.cardBackground else activeTheme.bgEnd
        ),
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.dp,
            highlighted = alarm.isEnabled
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeString,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = ChronometerDisplayFont,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    color = if (alarm.isEnabled) activeTheme.numberColor else activeTheme.textSecondary
                )
                Text(
                    text = if (alarm.label.isNotBlank()) "${alarm.label} • ${alarm.timezoneId}" else alarm.timezoneId,
                    fontSize = 12.sp,
                    color = activeTheme.textSecondary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = activeTheme.onAccentColor,
                        checkedTrackColor = activeTheme.accentColor,
                        uncheckedThumbColor = activeTheme.textSecondary,
                        uncheckedTrackColor = activeTheme.dividerColor
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Alarm",
                        tint = activeTheme.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmsListPane(
    alarms: List<AlarmItem>,
    use24Hour: Boolean,
    activeTheme: ClockVisualTheme,
    onAddClicked: () -> Unit,
    onToggleAlarm: (AlarmItem) -> Unit,
    onDeleteAlarm: (AlarmItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "WORLD ALARMS",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = activeTheme.accentColor,
                letterSpacing = 1.sp
            )

            Button(
                onClick = onAddClicked,
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeTheme.accentColor,
                    contentColor = activeTheme.onAccentColor
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("open_add_alarm_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Alarm icon", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ADD ALARM", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (alarms.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No world alarms scheduled yet. Tap '+ ADD ALARM' to set an alert in any city's local time.",
                    color = activeTheme.textSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                alarms.forEach { alarm ->
                    AlarmLocalItem(
                        alarm = alarm,
                        use24Hour = use24Hour,
                        activeTheme = activeTheme,
                        onToggle = { onToggleAlarm(alarm) },
                        onDelete = { onDeleteAlarm(alarm) }
                    )
                }
            }
        }
    }
}
