package com.example.ui.dialogs

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.FriendClock
import com.example.ui.ClockViewModel
import com.example.ui.components.hdBorderStroke
import com.example.ui.theme.ChronometerDisplayFont
import com.example.ui.theme.ChronometerMonoFont
import com.example.ui.theme.ClockVisualTheme
import com.example.util.TimeAndAlertUtils
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AddFriendClockDialog(
    availableTimezones: List<String>,
    activeTheme: ClockVisualTheme,
    onDismiss: () -> Unit,
    onConfirm: (friendName: String, cityLabel: String, timezoneId: String) -> Unit
) {
    var friendNameInput by remember { mutableStateOf("") }
    var cityLabelInput by remember { mutableStateOf("London") }
    var selectedTz by remember { mutableStateOf("Europe/London") }
    var tzSearchQuery by remember { mutableStateOf("") }

    data class PopularZoneChoice(
        val chipName: String,
        val defaultName: String,
        val defaultLabel: String,
        val tzId: String
    )

    val popularPresets = remember {
        listOf(
            PopularZoneChoice("Toronto", "Toronto", "Toronto, Canada", "America/Toronto"),
            PopularZoneChoice("Bangalore", "Bangalore", "Bangalore, India", "Asia/Kolkata"),
            PopularZoneChoice("Germany", "Germany", "Berlin, Germany", "Europe/Berlin"),
            PopularZoneChoice("Qatar", "Qatar", "Doha, Qatar", "Asia/Qatar"),
            PopularZoneChoice("Kuwait", "Kuwait", "Kuwait City, Kuwait", "Asia/Kuwait"),
            PopularZoneChoice("Saudi Arabia", "Saudi Arabia", "Riyadh, Saudi Arabia", "Asia/Riyadh"),
            PopularZoneChoice("New York", "New York", "New York, USA", "America/New_York"),
            PopularZoneChoice("London", "London", "London, UK", "Europe/London"),
            PopularZoneChoice("Tokyo", "Tokyo", "Tokyo, Japan", "Asia/Tokyo"),
            PopularZoneChoice("Paris", "Paris", "Paris, France", "Europe/Paris"),
            PopularZoneChoice("Sydney", "Sydney", "Sydney, Australia", "Australia/Sydney"),
            PopularZoneChoice("Dubai", "Dubai", "Dubai, UAE", "Asia/Dubai"),
            PopularZoneChoice("Singapore", "Singapore", "Singapore", "Asia/Singapore"),
            PopularZoneChoice("San Francisco", "San Francisco", "San Francisco, USA", "America/Los_Angeles"),
            PopularZoneChoice("Seoul", "Seoul", "Seoul, South Korea", "Asia/Seoul"),
            PopularZoneChoice("Sao Paulo", "Sao Paulo", "Sao Paulo, Brazil", "America/Sao_Paulo")
        )
    }

    val filteredTimezones = remember(availableTimezones, tzSearchQuery) {
        if (tzSearchQuery.isBlank()) {
            availableTimezones
        } else {
            availableTimezones.filter { zone ->
                TimeAndAlertUtils.matchesTimezoneQuery(zone, tzSearchQuery)
            }
        }
    }

    val selectedPreviewText = remember(selectedTz) {
        try {
            val z = ZoneId.of(selectedTz)
            val now = ZonedDateTime.now(z)
            val offset = z.rules.getOffset(Instant.now()).id.let { if (it == "Z") "+00:00" else it }
            "${now.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))} • UTC$offset"
        } catch (_: Exception) {
            ""
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = activeTheme.bgEnd),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, activeTheme.dividerColor),
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(0.94f)
            .imePadding()
            .padding(vertical = 12.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ADD WORLD CLOCK CITY",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = activeTheme.textPrimary
                    )
                    Text(
                        text = "Pick a city or search ${availableTimezones.size} global zones",
                        fontSize = 11.sp,
                        color = activeTheme.textSecondary
                    )
                }
                if (selectedPreviewText.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = activeTheme.accentColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, activeTheme.accentColor)
                    ) {
                        Text(
                            text = selectedPreviewText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.numberColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Scrollable Popular Cities Chips (never clips on compact phones)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "POPULAR CITIES & COUNTRIES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = activeTheme.textSecondary,
                    letterSpacing = 0.8.sp
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(popularPresets, key = { it.chipName + "_" + it.tzId }) { preset ->
                        val isSelected = selectedTz == preset.tzId
                        Surface(
                            onClick = {
                                selectedTz = preset.tzId
                                cityLabelInput = preset.defaultLabel
                                friendNameInput = preset.defaultName
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) activeTheme.accentColor else activeTheme.cardBackground,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) activeTheme.accentColor else activeTheme.dividerColor
                            ),
                            modifier = Modifier.defaultMinSize(minHeight = 38.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = preset.chipName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) activeTheme.onAccentColor else activeTheme.textPrimary
                                )
                            }
                        }
                    }
                }
            }

            // City & Optional Label Inputs (standard accessible height, zero clipping)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = cityLabelInput,
                    onValueChange = { cityLabelInput = it },
                    label = { Text("City Name", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedLabelColor = activeTheme.accentColor,
                        focusedBorderColor = activeTheme.accentColor,
                        unfocusedLabelColor = activeTheme.textSecondary,
                        unfocusedBorderColor = activeTheme.dividerColor,
                        focusedTextColor = activeTheme.textPrimary,
                        unfocusedTextColor = activeTheme.textPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("city_label_input")
                )

                OutlinedTextField(
                    value = friendNameInput,
                    onValueChange = { friendNameInput = it },
                    label = { Text("Label (Optional)", fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedLabelColor = activeTheme.accentColor,
                        focusedBorderColor = activeTheme.accentColor,
                        unfocusedLabelColor = activeTheme.textSecondary,
                        unfocusedBorderColor = activeTheme.dividerColor,
                        focusedTextColor = activeTheme.textPrimary,
                        unfocusedTextColor = activeTheme.textPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("relative_name_input")
                )
            }

            // Global Timezone Search (accessible M3 OutlinedTextField without clipped 48dp constraint)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GLOBAL TIMEZONE SEARCH",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeTheme.accentColor,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = selectedTz,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = activeTheme.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedTextField(
                    value = tzSearchQuery,
                    onValueChange = { tzSearchQuery = it },
                    placeholder = { Text("Search city or region (e.g. Tokyo, Kolkata, UTC)", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search Timezones",
                            tint = activeTheme.textSecondary
                        )
                    },
                    trailingIcon = {
                        if (tzSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { tzSearchQuery = "" }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear Search",
                                    tint = activeTheme.textSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeTheme.accentColor,
                        unfocusedBorderColor = activeTheme.dividerColor,
                        focusedTextColor = activeTheme.textPrimary,
                        unfocusedTextColor = activeTheme.textPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("timezone_search_input")
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(activeTheme.cardBackground)
                        .padding(6.dp)
                ) {
                    if (filteredTimezones.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No timezones match '$tzSearchQuery'",
                                fontSize = 12.sp,
                                color = activeTheme.textSecondary
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(filteredTimezones, key = { it }) { timezoneOpt ->
                                val tzSelected = timezoneOpt == selectedTz
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (tzSelected) activeTheme.accentColor.copy(alpha = 0.22f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            selectedTz = timezoneOpt
                                            val (resolvedName, resolvedCity) = when (timezoneOpt) {
                                                "America/Toronto" -> "Toronto" to "Toronto, Canada"
                                                "Asia/Kolkata", "Asia/Calcutta" -> "Bangalore" to "Bangalore, India"
                                                "Europe/Berlin" -> "Germany" to "Berlin, Germany"
                                                "Asia/Qatar" -> "Qatar" to "Doha, Qatar"
                                                "Asia/Kuwait" -> "Kuwait" to "Kuwait City, Kuwait"
                                                "Asia/Riyadh" -> "Saudi Arabia" to "Riyadh, Saudi Arabia"
                                                else -> {
                                                    val guessedCity = timezoneOpt.substringAfterLast('/').replace('_', ' ')
                                                    guessedCity to guessedCity
                                                }
                                            }
                                            cityLabelInput = resolvedCity
                                            if (friendNameInput.isBlank()) {
                                                friendNameInput = resolvedName
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = timezoneOpt,
                                            fontSize = 12.sp,
                                            fontWeight = if (tzSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (tzSelected) activeTheme.textPrimary else activeTheme.textSecondary
                                        )
                                        val zoneAlias = when (timezoneOpt) {
                                            "America/Toronto" -> "Toronto • Canada"
                                            "Asia/Kolkata", "Asia/Calcutta" -> "Bangalore • India"
                                            "Europe/Berlin" -> "Germany • Berlin"
                                            "Asia/Qatar" -> "Qatar • Doha"
                                            "Asia/Kuwait" -> "Kuwait • Kuwait City"
                                            "Asia/Riyadh" -> "Saudi Arabia • Riyadh"
                                            else -> null
                                        }
                                        if (zoneAlias != null) {
                                            Text(
                                                text = zoneAlias,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = activeTheme.accentColor
                                            )
                                        }
                                    }
                                    if (tzSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected Zone",
                                            tint = activeTheme.accentColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("CANCEL", fontWeight = FontWeight.Bold, color = activeTheme.textSecondary)
                }

                Spacer(modifier = Modifier.width(8.dp))

                val resolvedCity = cityLabelInput.trim().ifBlank {
                    selectedTz.substringAfterLast('/').replace('_', ' ')
                }
                val resolvedName = friendNameInput.trim().ifBlank { resolvedCity }

                Button(
                    onClick = { onConfirm(resolvedName, resolvedCity, selectedTz) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeTheme.accentColor,
                        contentColor = activeTheme.onAccentColor
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_add_city_button")
                ) {
                    Text("ADD CITY", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun ExportBackupDialog(
    jsonString: String,
    activeTheme: ClockVisualTheme,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = activeTheme.bgEnd),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, activeTheme.dividerColor),
        modifier = Modifier
            .widthIn(max = 540.dp)
            .fillMaxWidth(0.92f)
            .imePadding()
            .padding(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "EXPORT OFFLINE BACKUP",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = activeTheme.accentColor
            )

            Text(
                text = "Copy this JSON snapshot to back up your world cities, custom theme colors, 15–20m alert intervals, custom audio settings, and alarms.",
                fontSize = 12.sp,
                color = activeTheme.textSecondary
            )

            OutlinedTextField(
                value = jsonString,
                onValueChange = {},
                readOnly = true,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = activeTheme.textPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = activeTheme.accentColor,
                    unfocusedBorderColor = activeTheme.dividerColor
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Bold, color = activeTheme.textSecondary)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(jsonString))
                        Toast.makeText(context, "Backup copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeTheme.accentColor,
                        contentColor = activeTheme.onAccentColor
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("COPY BACKUP", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun ImportBackupDialog(
    activeTheme: ClockVisualTheme,
    onDismiss: () -> Unit,
    onImportConfirm: (String) -> Unit
) {
    var importText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = activeTheme.bgEnd),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, activeTheme.dividerColor),
        modifier = Modifier
            .widthIn(max = 540.dp)
            .fillMaxWidth(0.92f)
            .imePadding()
            .padding(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "IMPORT OFFLINE BACKUP",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = activeTheme.accentColor
            )

            Text(
                text = "Paste a previously exported JSON backup below to restore your world clocks, custom colors, alerts, and alarms.",
                fontSize = 12.sp,
                color = activeTheme.textSecondary
            )

            OutlinedTextField(
                value = importText,
                onValueChange = { importText = it },
                placeholder = { Text("Paste JSON backup here...", fontSize = 11.sp, color = activeTheme.textSecondary) },
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = activeTheme.textPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = activeTheme.accentColor,
                    unfocusedBorderColor = activeTheme.dividerColor
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        val pasted = clipboardManager.getText()?.text ?: ""
                        if (pasted.isNotEmpty()) {
                            importText = pasted
                            Toast.makeText(context, "Pasted from clipboard!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = activeTheme.accentColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PASTE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = activeTheme.accentColor)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("CANCEL", fontWeight = FontWeight.Bold, color = activeTheme.textSecondary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (importText.isNotBlank()) {
                                onImportConfirm(importText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeTheme.accentColor,
                            contentColor = activeTheme.onAccentColor
                        ),
                        shape = RoundedCornerShape(10.dp),
                        enabled = importText.isNotBlank(),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("RESTORE", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryOptimizationDialog(
    activeTheme: ClockVisualTheme,
    isIgnoringOptimization: Boolean,
    showSettingsUnavailableError: Boolean = false,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBatteryList: () -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = activeTheme.bgEnd),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, activeTheme.dividerColor),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(12.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = if (isIgnoringOptimization) Color(0xFF1B432C) else Color(0xFF4A2B14),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isIgnoringOptimization) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "Battery Status",
                            tint = if (isIgnoringOptimization) Color(0xFF81C784) else Color(0xFFFFB74D),
                            modifier = Modifier.padding(8.dp).size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.battery_dialog_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.textPrimary
                        )
                        Text(
                            text = if (isIgnoringOptimization) {
                                stringResource(R.string.battery_status_unrestricted)
                            } else {
                                stringResource(R.string.battery_status_restricted)
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isIgnoringOptimization) Color(0xFF81C784) else Color(0xFFFFB74D)
                        )
                    }
                }

                HorizontalDivider(color = activeTheme.dividerColor)

                Text(
                    text = stringResource(R.string.battery_dialog_description),
                    fontSize = 12.sp,
                    color = activeTheme.textPrimary,
                    lineHeight = 17.sp
                )

                Surface(
                    color = activeTheme.cardBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.battery_dialog_steps),
                        fontSize = 11.sp,
                        color = activeTheme.textSecondary,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (showSettingsUnavailableError) {
                    Surface(
                        color = Color(0xFF4A1818),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFF87171)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("battery_settings_unavailable_banner")
                    ) {
                        Text(
                            text = stringResource(R.string.battery_settings_unavailable_error),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFEE2E2),
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeTheme.accentColor,
                            contentColor = activeTheme.onAccentColor
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("battery_dialog_open_settings_button")
                    ) {
                        Text(
                            text = stringResource(R.string.battery_btn_open_settings),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onOpenBatteryList,
                            border = BorderStroke(1.dp, activeTheme.dividerColor),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = activeTheme.cardBackground,
                                contentColor = activeTheme.textPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("battery_dialog_list_button")
                        ) {
                            Text(
                                text = stringResource(R.string.battery_btn_battery_list),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("battery_dialog_close_button")
                        ) {
                            Text("CLOSE", color = activeTheme.textSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddAlarmDialog(
    availableTimezones: List<String>,
    friendClocks: List<FriendClock> = emptyList(),
    activeTheme: ClockVisualTheme,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int, timezoneId: String, label: String) -> Unit
) {
    var labelInput by remember { mutableStateOf("") }
    var hourInput by remember { mutableStateOf("08") }
    var minuteInput by remember { mutableStateOf("00") }

    val defaultTz = remember(friendClocks) {
        friendClocks.firstOrNull()?.timezoneId ?: ZoneId.systemDefault().id
    }
    var selectedTz by remember { mutableStateOf(defaultTz) }
    var timezoneTab by remember { mutableIntStateOf(0) }
    var tzSearchQuery by remember { mutableStateOf("") }

    // Live input validation fixing DateTimeException crash on values like "99"
    val parsedValidTime = remember(hourInput, minuteInput) {
        TimeAndAlertUtils.validateAlarmTime(hourInput, minuteInput)
    }
    val isHourInvalid = hourInput.toIntOrNull()?.let { it !in 0..23 } ?: true
    val isMinuteInvalid = minuteInput.toIntOrNull()?.let { it !in 0..59 } ?: true

    val currentTimeInSelectedTz = remember(selectedTz) {
        try {
            val zone = ZoneId.of(selectedTz)
            ZonedDateTime.now(zone).format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))
        } catch (_: Exception) {
            ""
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = activeTheme.bgEnd),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, activeTheme.dividerColor),
        modifier = Modifier
            .widthIn(max = 540.dp)
            .fillMaxWidth(0.94f)
            .imePadding()
            .padding(vertical = 12.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "ADD WORLD ALARM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = activeTheme.textPrimary
                    )
                }

                if (currentTimeInSelectedTz.isNotEmpty()) {
                    Surface(
                        color = activeTheme.cardBackground,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, activeTheme.accentColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = currentTimeInSelectedTz,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.numberColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = labelInput,
                onValueChange = { labelInput = it },
                label = { Text("Alarm Label (e.g., Standup Call)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = activeTheme.accentColor,
                    focusedLabelColor = activeTheme.accentColor,
                    unfocusedBorderColor = activeTheme.dividerColor,
                    unfocusedLabelColor = activeTheme.textSecondary,
                    focusedTextColor = activeTheme.textPrimary,
                    unfocusedTextColor = activeTheme.textPrimary
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = hourInput,
                    onValueChange = { raw ->
                        if (raw.length <= 2) hourInput = raw.filter { it.isDigit() }
                    },
                    label = { Text("Hour (0–23)") },
                    isError = isHourInvalid,
                    supportingText = if (isHourInvalid) {
                        { Text("Enter 0–23", fontSize = 10.sp) }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeTheme.accentColor,
                        focusedLabelColor = activeTheme.accentColor,
                        unfocusedBorderColor = activeTheme.dividerColor,
                        unfocusedLabelColor = activeTheme.textSecondary,
                        focusedTextColor = activeTheme.numberColor,
                        unfocusedTextColor = activeTheme.numberColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("alarm_hour_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = minuteInput,
                    onValueChange = { raw ->
                        if (raw.length <= 2) minuteInput = raw.filter { it.isDigit() }
                    },
                    label = { Text("Minute (0–59)") },
                    isError = isMinuteInvalid,
                    supportingText = if (isMinuteInvalid) {
                        { Text("Enter 0–59", fontSize = 10.sp) }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeTheme.accentColor,
                        focusedLabelColor = activeTheme.accentColor,
                        unfocusedBorderColor = activeTheme.dividerColor,
                        unfocusedLabelColor = activeTheme.textSecondary,
                        focusedTextColor = activeTheme.numberColor,
                        unfocusedTextColor = activeTheme.numberColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("alarm_minute_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = timezoneTab == 0,
                    onClick = { timezoneTab = 0 },
                    label = { Text("Saved Cities (${friendClocks.size})", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = activeTheme.accentColor,
                        selectedLabelColor = activeTheme.onAccentColor,
                        selectedLeadingIconColor = activeTheme.onAccentColor,
                        containerColor = activeTheme.cardBackground,
                        labelColor = activeTheme.textPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = timezoneTab == 1,
                    onClick = { timezoneTab = 1 },
                    label = { Text("All Zones", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = activeTheme.accentColor,
                        selectedLabelColor = activeTheme.onAccentColor,
                        selectedLeadingIconColor = activeTheme.onAccentColor,
                        containerColor = activeTheme.cardBackground,
                        labelColor = activeTheme.textPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            if (timezoneTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp)
                        .background(activeTheme.cardBackground, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val localTzId = ZoneId.systemDefault().id
                    val isLocalSelected = selectedTz == localTzId
                    Surface(
                        onClick = { selectedTz = localTzId },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isLocalSelected) activeTheme.accentColor.copy(alpha = 0.16f) else Color.Transparent,
                        border = if (isLocalSelected) BorderStroke(1.5.dp, activeTheme.accentColor) else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Local Timezone ($localTzId)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = activeTheme.textPrimary
                            )
                            if (isLocalSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = activeTheme.accentColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    friendClocks.forEach { friend ->
                        val isSelected = selectedTz == friend.timezoneId
                        Surface(
                            onClick = {
                                selectedTz = friend.timezoneId
                                if (labelInput.isBlank()) {
                                    labelInput = friend.cityLabel.ifBlank { friend.friendName }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) activeTheme.accentColor.copy(alpha = 0.16f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.5.dp, activeTheme.accentColor) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = friend.cityLabel.ifBlank { friend.friendName },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = activeTheme.textPrimary
                                    )
                                    Text(
                                        text = friend.timezoneId,
                                        fontSize = 10.sp,
                                        color = activeTheme.textSecondary
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = activeTheme.accentColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = tzSearchQuery,
                    onValueChange = { tzSearchQuery = it },
                    placeholder = { Text("Search city or timezone...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeTheme.accentColor,
                        unfocusedBorderColor = activeTheme.dividerColor,
                        focusedTextColor = activeTheme.textPrimary,
                        unfocusedTextColor = activeTheme.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                val filtered = remember(availableTimezones, tzSearchQuery) {
                    if (tzSearchQuery.isBlank()) availableTimezones
                    else availableTimezones.filter { TimeAndAlertUtils.matchesTimezoneQuery(it, tzSearchQuery) }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(activeTheme.cardBackground)
                        .padding(6.dp)
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(filtered, key = { it }) { tz ->
                            val isSelected = tz == selectedTz
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) activeTheme.accentColor.copy(alpha = 0.2f) else Color.Transparent)
                                    .clickable { selectedTz = tz }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tz,
                                        color = if (isSelected) activeTheme.accentColor else activeTheme.textPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                    val zoneAlias = when (tz) {
                                        "America/Toronto" -> "Toronto • Canada"
                                        "Asia/Kolkata", "Asia/Calcutta" -> "Bangalore • India"
                                        "Europe/Berlin" -> "Germany • Berlin"
                                        "Asia/Qatar" -> "Qatar • Doha"
                                        "Asia/Kuwait" -> "Kuwait • Kuwait City"
                                        "Asia/Riyadh" -> "Saudi Arabia • Riyadh"
                                        else -> null
                                    }
                                    if (zoneAlias != null) {
                                        Text(
                                            text = zoneAlias,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = activeTheme.accentColor
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = activeTheme.accentColor, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("CANCEL", color = activeTheme.textSecondary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        parsedValidTime?.let { (h, m) ->
                            onConfirm(h, m, selectedTz, labelInput)
                        }
                    },
                    enabled = parsedValidTime != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeTheme.accentColor,
                        contentColor = activeTheme.onAccentColor
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_add_alarm_button")
                ) {
                    Text("ADD ALARM", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun SettingsHubDialog(
    currentLocalTime: ZonedDateTime,
    settings: com.example.data.ClockSettings,
    friendClocks: List<FriendClock>,
    alarms: List<com.example.data.AlarmItem>,
    activeTheme: ClockVisualTheme,
    viewModel: com.example.ui.ClockViewModel,
    initialTab: Int = 3,
    onDismiss: () -> Unit,
    onAddAlarmClicked: () -> Unit,
    onExportClicked: () -> Unit,
    onImportClicked: () -> Unit,
    onOpenCustomThemeStudio: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.25.dp,
            highlighted = true
        ),
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .fillMaxHeight(0.88f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "SETTINGS & TOOLS HUB",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeTheme.numberColor
                        )
                        Text(
                            text = "Configure quiet hours, themes, alarms & companion tools",
                            fontSize = 11.sp,
                            color = activeTheme.textSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Settings",
                        tint = activeTheme.textPrimary
                    )
                }
            }

            // Tab Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(activeTheme.bgEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple("SETTINGS", Icons.Default.Tune, 3),
                    Triple("ALARMS", Icons.Default.Notifications, 2),
                    Triple("DIAGNOSTICS", Icons.Default.BugReport, 4),
                    Triple("PLANNER", Icons.Default.DateRange, 0),
                    Triple("BREATH", Icons.Default.Favorite, 1)
                ).forEach { (label, icon, idx) ->
                    val isSelected = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 44.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) activeTheme.accentColor else Color.Transparent)
                            .clickable { selectedTab = idx },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) activeTheme.onAccentColor else activeTheme.accentColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) activeTheme.onAccentColor else activeTheme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Scrollable Content Pane
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> com.example.ui.components.TimeOverlapPlannerPane(
                        friendClocks = friendClocks,
                        currentLocalTime = currentLocalTime,
                        use24Hour = settings.digital24HourFormat,
                        activeTheme = activeTheme
                    )
                    1 -> com.example.ui.components.PacedBreathingBubble(
                        settings = settings,
                        activeTheme = activeTheme,
                        viewModel = viewModel,
                        forceExpanded = true
                    )
                    2 -> com.example.ui.components.AlarmsListPane(
                        alarms = alarms,
                        use24Hour = settings.digital24HourFormat,
                        activeTheme = activeTheme,
                        onAddClicked = onAddAlarmClicked,
                        onToggleAlarm = { viewModel.toggleAlarm(it) },
                        onDeleteAlarm = { viewModel.deleteAlarm(it) }
                    )
                    4 -> DiagnosticDashboardContent(
                        activeTheme = activeTheme
                    )
                    else -> com.example.ui.components.AdhdSettingsPane(
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
}

@Composable
fun PostCrashRecoveryDialog(
    crashLogText: String,
    activeTheme: ClockVisualTheme,
    onDismiss: () -> Unit,
    onClearAndDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = activeTheme.bgEnd),
        border = BorderStroke(1.5.dp, Color(0xFFF87171)),
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(0.92f)
            .padding(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "App Recovered From Error",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = activeTheme.textPrimary
                )
            }

            Text(
                text = "The application previously encountered an unexpected issue and safely generated an anonymous diagnostic report to help us fix it.",
                fontSize = 11.sp,
                color = activeTheme.textSecondary
            )

            // Prominent Privacy Assurance Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = activeTheme.cardBackground,
                border = BorderStroke(1.dp, activeTheme.accentColor.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Privacy Shield",
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Privacy Notice & Disclosure: This diagnostic JSON contains ONLY anonymous technical data (device model, Android OS version, screen size, and stack traces). Absolutely NO Personally Identifiable Information (PII) — zero accounts, contacts, location, or private user files.",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = activeTheme.textPrimary
                    )
                }
            }

            OutlinedTextField(
                value = crashLogText,
                onValueChange = {},
                readOnly = true,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = activeTheme.textPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = activeTheme.dividerColor,
                    unfocusedBorderColor = activeTheme.dividerColor
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onClearAndDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("CLEAR & DISMISS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = activeTheme.textSecondary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(crashLogText))
                            Toast.makeText(context, "Diagnostic JSON copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("COPY JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val shareIntent = com.example.util.CrashReportManager.buildShareIntent(crashLogText)
                            context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostic JSON Report"))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeTheme.accentColor,
                            contentColor = activeTheme.onAccentColor
                        ),
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SHARE REPORT", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticLogViewerDialog(
    activeTheme: ClockVisualTheme,
    onDismiss: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = activeTheme.bgEnd),
        border = BorderStroke(1.2.dp, activeTheme.accentColor),
        modifier = Modifier
            .widthIn(max = 680.dp)
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.85f)
            .padding(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Crash Diagnostics Dashboard",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = activeTheme.textPrimary
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = activeTheme.textPrimary)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                DiagnosticDashboardContent(activeTheme = activeTheme)
            }
        }
    }
}

@Composable
fun DiagnosticDashboardContent(
    activeTheme: ClockVisualTheme,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var refreshKey by remember { mutableIntStateOf(0) }

    val recentCrashes = remember(refreshKey) {
        com.example.util.CrashReportManager.getRecentCrashReports(context)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Prominent Privacy Notice & Disclosure Banner at the Top
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = activeTheme.cardBackground,
            border = BorderStroke(1.2.dp, activeTheme.accentColor.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Privacy Shield",
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "PRIVACY NOTICE & TRANSPARENCY DISCLOSURE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp,
                        color = activeTheme.numberColor
                    )
                }
                Text(
                    text = "These diagnostic reports contain ONLY anonymous technical data (device model, Android OS version, screen size, and stack traces). Absolutely ZERO Personally Identifiable Information (PII) — no user accounts, names, emails, contacts, GPS/location data, or personal files are ever collected, stored, or transmitted.",
                    fontSize = 11.sp,
                    color = activeTheme.textPrimary,
                    lineHeight = 15.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = activeTheme.accentColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "🔒 100% On-Device",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = activeTheme.accentColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "🛡️ Zero PII Guaranteed",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = activeTheme.dividerColor.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = "📋 Max 5 Rolling Logs",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                        )
                    }
                }
            }
        }

        // 2. Summary & Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CRASH LOGS HISTORY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = activeTheme.numberColor,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = if (recentCrashes.isEmpty()) {
                        "No crashes recorded (System healthy)"
                    } else {
                        "Showing last ${recentCrashes.size} of 5 recorded incidents (newest first)"
                    },
                    fontSize = 10.5.sp,
                    color = activeTheme.textSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (recentCrashes.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val shareIntent = com.example.util.CrashReportManager.buildShareAllIntent(context)
                            context.startActivity(Intent.createChooser(shareIntent, "Share All Crash Logs (JSON)"))
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SHARE ALL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = {
                            com.example.util.CrashReportManager.clearAllCrashReports(context)
                            refreshKey++
                            Toast.makeText(context, "Crash history cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                    ) {
                        Text("CLEAR ALL", fontSize = 10.sp, color = activeTheme.textSecondary)
                    }
                }

                Button(
                    onClick = {
                        com.example.util.CrashReportManager.recordSimulatedIncident(context, "Manual Diagnostic Test")
                        refreshKey++
                        Toast.makeText(context, "Simulated diagnostic incident recorded!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeTheme.accentColor.copy(alpha = 0.22f),
                        contentColor = activeTheme.accentColor
                    ),
                    modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ SIMULATE CRASH", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 3. Crash List Cards or Clean Empty State
        if (recentCrashes.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = activeTheme.cardBackground,
                border = BorderStroke(1.dp, activeTheme.dividerColor.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "System Operating Normally",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeTheme.textPrimary
                    )
                    Text(
                        text = "Zero crashes have been recorded. You can tap '+ SIMULATE CRASH' above to test the diagnostic capture and share sheets safely.",
                        fontSize = 11.sp,
                        color = activeTheme.textSecondary
                    )
                }
            }
        } else {
            recentCrashes.forEachIndexed { index, reportObj ->
                CrashReportCardItem(
                    index = index,
                    totalCount = recentCrashes.size,
                    report = reportObj,
                    activeTheme = activeTheme,
                    onCopy = { jsonStr ->
                        clipboardManager.setText(AnnotatedString(jsonStr))
                        Toast.makeText(context, "Incident #${index + 1} JSON copied!", Toast.LENGTH_SHORT).show()
                    },
                    onShare = { jsonStr ->
                        val intent = com.example.util.CrashReportManager.buildShareIntent(jsonStr)
                        context.startActivity(Intent.createChooser(intent, "Share Crash Report #${index + 1}"))
                    }
                )
            }
        }
    }
}

@Composable
private fun CrashReportCardItem(
    index: Int,
    totalCount: Int,
    report: org.json.JSONObject,
    activeTheme: ClockVisualTheme,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(index == 0) }

    val token = report.optString("anonymousSessionId", "ANON-0000")
    val timestamp = report.optString("timestamp", "Recent")
    val isCrash = report.optBoolean("isCrash", true)

    val errorObj = report.optJSONObject("errorDetails")
    val exceptionType = errorObj?.optString("exceptionType", "Application Error") ?: "Application Diagnostic"
    val exceptionMsg = errorObj?.optString("exceptionMessage", "None provided") ?: "None provided"
    val stackTrace = errorObj?.optString("stackTrace", "No stack trace available.") ?: "No stack trace available."

    val deviceObj = report.optJSONObject("deviceEnvironment")
    val deviceModel = deviceObj?.optString("deviceModel", "Unknown Model") ?: "Unknown Model"
    val osVersion = deviceObj?.optString("androidOsVersion", "Android") ?: "Android"
    val screenSize = deviceObj?.optString("screenSize", "") ?: ""
    val density = deviceObj?.optInt("screenDensityDpi", 0) ?: 0
    val threadName = report.optString("threadName", "MainThread")

    val simpleTypeName = exceptionType.substringAfterLast('.')

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
        border = BorderStroke(1.2.dp, if (index == 0) activeTheme.accentColor.copy(alpha = 0.6f) else activeTheme.dividerColor.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            // Header Row (Clickable to Toggle Expand/Collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (index == 0) activeTheme.accentColor else activeTheme.dividerColor.copy(alpha = 0.35f)
                    ) {
                        Text(
                            text = if (index == 0) "#1 NEWEST" else "#${index + 1}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (index == 0) activeTheme.onAccentColor else activeTheme.textPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isCrash) Color(0xFFEF4444).copy(alpha = 0.18f) else Color(0xFF10B981).copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = simpleTypeName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCrash) Color(0xFFF87171) else Color(0xFF34D399),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                        )
                    }

                    Text(
                        text = token,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = activeTheme.textSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isExpanded) "Hide" else "Details",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = activeTheme.accentColor
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Collapsed Quick Summary
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$deviceModel • $osVersion${if (screenSize.isNotEmpty()) " • $screenSize" else ""}",
                    fontSize = 10.5.sp,
                    color = activeTheme.textSecondary
                )
                Text(
                    text = timestamp.take(19).replace('T', ' '),
                    fontSize = 9.5.sp,
                    color = activeTheme.textSecondary
                )
            }

            // Expanded Details Section
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = activeTheme.dividerColor.copy(alpha = 0.35f), thickness = 1.dp)

                    // Error Message
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = activeTheme.bgEnd,
                        border = BorderStroke(1.dp, activeTheme.dividerColor)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Text(
                                text = "Incident Message:",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeTheme.accentColor
                            )
                            Text(
                                text = exceptionMsg,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = activeTheme.textPrimary
                            )
                        }
                    }

                    // Metadata Badges Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = activeTheme.bgEnd,
                            border = BorderStroke(1.dp, activeTheme.dividerColor),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text("Model", fontSize = 8.5.sp, color = activeTheme.textSecondary)
                                Text(deviceModel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = activeTheme.textPrimary)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = activeTheme.bgEnd,
                            border = BorderStroke(1.dp, activeTheme.dividerColor),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text("OS Version", fontSize = 8.5.sp, color = activeTheme.textSecondary)
                                Text(osVersion, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = activeTheme.textPrimary)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = activeTheme.bgEnd,
                            border = BorderStroke(1.dp, activeTheme.dividerColor),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text("Screen / Density", fontSize = 8.5.sp, color = activeTheme.textSecondary)
                                Text(if (density > 0) "$screenSize @ ${density}dpi" else screenSize, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = activeTheme.textPrimary)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = activeTheme.bgEnd,
                            border = BorderStroke(1.dp, activeTheme.dividerColor),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Text("Thread", fontSize = 8.5.sp, color = activeTheme.textSecondary)
                                Text(threadName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = activeTheme.textPrimary)
                            }
                        }
                    }

                    // Stack trace box
                    Text(
                        text = "Sanitized Technical Stack Trace (No PII):",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeTheme.textSecondary
                    )

                    OutlinedTextField(
                        value = stackTrace,
                        onValueChange = {},
                        readOnly = true,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp, max = 160.dp),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = activeTheme.textPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = activeTheme.dividerColor,
                            unfocusedBorderColor = activeTheme.dividerColor
                        )
                    )

                    // Individual Card Actions: Copy JSON, Share Entry
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onCopy(report.toString(2)) },
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("COPY JSON", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = { onShare(report.toString(2)) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = activeTheme.accentColor,
                                contentColor = activeTheme.onAccentColor
                            ),
                            modifier = Modifier.defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SHARE ENTRY", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClockSyncInspectionDialog(
    currentLocalTime: ZonedDateTime,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val syncState by viewModel.clockSyncState.collectAsState()
    val globalLocations = remember(currentLocalTime.minute) {
        viewModel.getGlobalLocationsSyncStatus()
    }
    var syncNotice by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = activeTheme.bgEnd),
        shape = RoundedCornerShape(20.dp),
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.2.dp,
            highlighted = true
        ),
        modifier = Modifier
            .widthIn(max = 620.dp)
            .fillMaxWidth(0.95f)
            .padding(vertical = 14.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dialog Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "CLOCK & DST REAL-TIME SYNC",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeTheme.textPrimary
                        )
                        Text(
                            text = "Continuous sync via device system time provider",
                            fontSize = 11.sp,
                            color = activeTheme.textSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_clock_sync_dialog_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = activeTheme.textSecondary
                    )
                }
            }

            // Provider Status Card
            Surface(
                color = activeTheme.cardBackground,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, activeTheme.dividerColor)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF10B981))
                            )
                            Text(
                                text = "SYSTEM TIME PROVIDER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = Color(0xFF10B981)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = activeTheme.accentColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = if (syncState.isLocalDaylightSavings) "DST ACTIVE (+1h)" else "STANDARD TIME",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeTheme.accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Time Source: ${syncState.syncProviderName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = activeTheme.textPrimary
                    )

                    Text(
                        text = "Local Device Zone: ${syncState.localZoneId}",
                        fontSize = 11.sp,
                        color = activeTheme.textSecondary
                    )

                    Text(
                        text = "Next Regional Shift: ${syncState.localNextTransition}",
                        fontSize = 11.sp,
                        color = activeTheme.textSecondary
                    )
                }
            }

            // Sync notice banner if triggered
            syncNotice?.let { notice ->
                Surface(
                    color = activeTheme.accentColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, activeTheme.accentColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = activeTheme.accentColor, modifier = Modifier.size(16.dp))
                        Text(text = notice, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = activeTheme.textPrimary)
                    }
                }
            }

            Text(
                text = "GLOBAL LOCATIONS DST & REGIONAL ADJUSTMENTS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = activeTheme.textSecondary,
                letterSpacing = 0.8.sp
            )

            // Locations List (Toronto, Bangalore, Germany, Qatar, Kuwait, Saudi Arabia)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                globalLocations.forEach { loc ->
                    Surface(
                        color = activeTheme.cardBackground,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, activeTheme.dividerColor.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = loc.label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeTheme.textPrimary
                                    )
                                    Text(
                                        text = "${loc.timezoneId} • ${loc.utcOffsetString}",
                                        fontSize = 10.sp,
                                        fontFamily = ChronometerMonoFont,
                                        color = activeTheme.textSecondary
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = loc.currentZonedTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US)),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = ChronometerDisplayFont,
                                        style = TextStyle(fontFeatureSettings = "tnum"),
                                        color = activeTheme.numberColor
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when {
                                            loc.isDaylightSavings -> Color(0xFFF59E0B).copy(alpha = 0.18f)
                                            loc.nextTransitionDescription.contains("No DST", ignoreCase = true) -> activeTheme.dividerColor.copy(alpha = 0.3f)
                                            else -> activeTheme.accentColor.copy(alpha = 0.18f)
                                        }
                                    ) {
                                        Text(
                                            text = when {
                                                loc.isDaylightSavings -> "DST Active"
                                                loc.nextTransitionDescription.contains("No DST", ignoreCase = true) -> "No DST"
                                                else -> "Standard"
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                loc.isDaylightSavings -> Color(0xFFF59E0B)
                                                loc.nextTransitionDescription.contains("No DST", ignoreCase = true) -> activeTheme.textSecondary
                                                else -> activeTheme.accentColor
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Next adjustment: ${loc.nextTransitionDescription}",
                                fontSize = 10.sp,
                                color = activeTheme.textSecondary
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.forceClockSync()
                        syncNotice = "Resynced against system clock at ${DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.US).format(ZonedDateTime.now())}"
                        Toast.makeText(context, "System clock resynchronized!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("force_resync_clock_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("FORCE RESYNC NOW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeTheme.accentColor,
                        contentColor = activeTheme.onAccentColor
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("dismiss_clock_sync_button")
                ) {
                    Text("CLOSE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun BreathingGuideDialog(
    settings: com.example.data.ClockSettings,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = activeTheme.bgEnd),
        shape = RoundedCornerShape(22.dp),
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.25.dp,
            highlighted = true
        ),
        modifier = Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth(0.92f)
            .padding(14.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SelfImprovement,
                        contentDescription = null,
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "GUIDED BREATHING FOCUS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeTheme.textPrimary
                        )
                        Text(
                            text = "Continuous 4-second box breathing rhythm",
                            fontSize = 11.sp,
                            color = activeTheme.textSecondary
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("close_breathing_dialog_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Guided Breathing",
                        tint = activeTheme.textPrimary
                    )
                }
            }

            com.example.ui.components.PacedBreathingBubble(
                settings = settings,
                activeTheme = activeTheme,
                viewModel = viewModel,
                forceExpanded = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeTheme.accentColor,
                        contentColor = activeTheme.onAccentColor
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("DONE", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}


