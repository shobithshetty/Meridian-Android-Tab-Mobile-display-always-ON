package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClockSettings
import com.example.data.PlannerCodec
import com.example.ui.ClockViewModel
import com.example.ui.theme.ClockVisualTheme
import com.example.util.Holidays
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private fun formatMinute(minuteOfDay: Int): String =
    if (minuteOfDay < 0) "All day" else "%d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

/** Countdowns, the routine checklist and date/holiday options. Identical for every layout style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerSettingsSection(settings: ClockSettings, theme: ClockVisualTheme, viewModel: ClockViewModel) {
    var addingCountdown by remember { mutableStateOf(false) }
    var addingRoutine by remember { mutableStateOf(false) }
    val countdowns = remember(settings.countdownsJson) { PlannerCodec.countdownsFromJson(settings.countdownsJson) }
    val routines = remember(settings.routinesJson) { PlannerCodec.routinesFromJson(settings.routinesJson) }

    @Composable
    fun Heading(text: String) = Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = theme.textSecondary
    )

    @Composable
    fun Row(label: String, detail: String, tag: String, onRemove: () -> Unit) {
        Surface(
            shape = RoundedCornerShape(12.dp), color = theme.cardBackground,
            border = BorderStroke(1.dp, theme.dividerColor), modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.foundation.layout.Row(
                Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, fontSize = 11.sp, color = theme.textSecondary)
                }
                TextButton(onClick = onRemove, modifier = Modifier.testTag(tag)) { Text("Remove", color = theme.accentColor, fontSize = 12.sp) }
            }
        }
    }

    @Composable
    fun AddButton(text: String, tag: String, onClick: () -> Unit) = Surface(
        onClick = onClick, shape = RoundedCornerShape(12.dp), color = theme.cardBackground,
        border = BorderStroke(1.dp, theme.accentColor), modifier = Modifier.fillMaxWidth().testTag(tag)
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = theme.accentColor, modifier = Modifier.padding(12.dp))
    }

    @Composable
    fun Choice(label: String, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) = Surface(
        onClick = onClick, shape = RoundedCornerShape(12.dp), color = theme.cardBackground,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) theme.accentColor else theme.dividerColor),
        modifier = modifier.defaultMinSize(minHeight = 44.dp).testTag(tag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) theme.accentColor else theme.textPrimary)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Heading("COUNTDOWNS")
        countdowns.forEach { c ->
            val date = LocalDate.ofEpochDay(c.epochDay).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))
            Row(c.label, "$date · ${formatMinute(c.minuteOfDay)}", "remove_countdown_${c.id}") { viewModel.removeCountdown(c.id) }
        }
        AddButton("+ Add countdown", "add_countdown") { addingCountdown = true }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Heading("ROUTINE CHECKLIST (SHOWS DAILY FROM ITS TIME UNTIL TICKED)")
        routines.forEach { r ->
            Row(r.label, "From ${formatMinute(r.minuteOfDay)} every day", "remove_routine_${r.id}") { viewModel.removeRoutine(r.id) }
        }
        AddButton("+ Add routine item", "add_routine") { addingRoutine = true }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Heading("WEEK NUMBER AND HOLIDAYS")
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice("Show", settings.showDayInfo, "day_info_on", Modifier.weight(1f)) { viewModel.updateShowDayInfo(true) }
            Choice("Hide", !settings.showDayInfo, "day_info_off", Modifier.weight(1f)) { viewModel.updateShowDayInfo(false) }
        }
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Holidays.regions.forEach { (id, label) ->
                Choice(label, settings.holidayRegion == id, "holiday_region_${id.lowercase()}", Modifier.weight(1f)) {
                    viewModel.updateHolidayRegion(id)
                }
            }
        }
        Text(
            "Fixed-date and Easter holidays only; festivals that move with the moon are not listed.",
            fontSize = 10.sp, color = theme.textSecondary
        )
    }

    if (addingCountdown) {
        AddPlannerItemDialog(theme, isCountdown = true, onDismiss = { addingCountdown = false }) { label, day, minute ->
            viewModel.addCountdown(label, day, minute)
            addingCountdown = false
        }
    }
    if (addingRoutine) {
        AddPlannerItemDialog(theme, isCountdown = false, onDismiss = { addingRoutine = false }) { label, _, minute ->
            viewModel.addRoutine(label, minute)
            addingRoutine = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlannerItemDialog(
    theme: ClockVisualTheme,
    isCountdown: Boolean,
    onDismiss: () -> Unit,
    onSave: (label: String, epochDay: Long, minuteOfDay: Int) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var allDay by remember { mutableStateOf(isCountdown) }
    var pickingDate by remember { mutableStateOf(false) }
    val now = remember { ZonedDateTime.now() }
    val timeState = rememberTimePickerState(initialHour = (now.hour + 1) % 24, initialMinute = 0, is24Hour = false)
    val dateState = rememberDatePickerState(initialSelectedDateMillis = now.toLocalDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    val chosenDay = dateState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: now.toLocalDate()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = theme.cardBackground,
        title = { Text(if (isCountdown) "New countdown" else "New routine item", color = theme.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label, onValueChange = { label = it.take(30) }, singleLine = true,
                    label = { Text(if (isCountdown) "What is it? (e.g. Trip)" else "What to do? (e.g. Take meds)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary, unfocusedTextColor = theme.textPrimary,
                        focusedBorderColor = theme.accentColor, unfocusedBorderColor = theme.dividerColor,
                        focusedLabelColor = theme.accentColor, unfocusedLabelColor = theme.textSecondary,
                        cursorColor = theme.accentColor
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("planner_label_field")
                )
                if (isCountdown) {
                    TextButton(onClick = { pickingDate = true }, modifier = Modifier.testTag("planner_pick_date")) {
                        Text("Date: " + chosenDay.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")), color = theme.accentColor)
                    }
                    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = !allDay, onCheckedChange = { allDay = !it },
                            colors = CheckboxDefaults.colors(checkedColor = theme.accentColor, uncheckedColor = theme.textSecondary, checkmarkColor = theme.onAccentColor)
                        )
                        Text("At a specific time", color = theme.textPrimary, fontSize = 14.sp)
                    }
                }
                if (!isCountdown || !allDay) TimeInput(
                    state = timeState,
                    colors = TimePickerDefaults.colors(
                        timeSelectorSelectedContainerColor = theme.accentColor,
                        timeSelectorSelectedContentColor = theme.onAccentColor,
                        timeSelectorUnselectedContainerColor = theme.textPrimary.copy(alpha = 0.12f),
                        timeSelectorUnselectedContentColor = theme.textPrimary,
                        periodSelectorBorderColor = theme.dividerColor,
                        periodSelectorSelectedContainerColor = theme.accentColor.copy(alpha = 0.25f),
                        periodSelectorSelectedContentColor = theme.accentColor,
                        periodSelectorUnselectedContentColor = theme.textPrimary
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minute = if (isCountdown && allDay) -1 else timeState.hour * 60 + timeState.minute
                    onSave(label, if (isCountdown) chosenDay.toEpochDay() else 0L, minute)
                },
                modifier = Modifier.testTag("planner_save")
            ) { Text("Save", color = theme.accentColor) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textSecondary) } }
    )

    if (pickingDate) {
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            colors = DatePickerDefaults.colors(containerColor = theme.cardBackground),
            confirmButton = { TextButton(onClick = { pickingDate = false }) { Text("OK", color = theme.accentColor) } }
        ) {
            DatePicker(
                state = dateState,
                colors = DatePickerDefaults.colors(
                    containerColor = theme.cardBackground,
                    titleContentColor = theme.textSecondary, headlineContentColor = theme.textPrimary,
                    weekdayContentColor = theme.textSecondary, subheadContentColor = theme.textSecondary,
                    navigationContentColor = theme.textPrimary, yearContentColor = theme.textPrimary,
                    currentYearContentColor = theme.accentColor, selectedYearContainerColor = theme.accentColor,
                    selectedYearContentColor = theme.onAccentColor, dayContentColor = theme.textPrimary,
                    selectedDayContainerColor = theme.accentColor, selectedDayContentColor = theme.onAccentColor,
                    todayContentColor = theme.accentColor, todayDateBorderColor = theme.accentColor
                )
            )
        }
    }
}
