package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.AudioSynthEngine
import com.example.data.ClockSettings
import com.example.ui.ClockViewModel
import com.example.ui.theme.ChronometerDisplayFont
import com.example.ui.theme.ChronometerMonoFont
import com.example.ui.theme.ClockVisualTheme
import com.example.util.TimeAndAlertUtils
import kotlin.math.roundToInt

@Composable
fun IntervalAlertControlCard(
    settings: ClockSettings,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    modifier: Modifier = Modifier,
    compactMode: Boolean = false
) {
    val remainingSeconds by viewModel.remainingChimeSeconds.collectAsStateWithLifecycle()
    var showSoundPicker by rememberSaveable { mutableStateOf(false) }

    val totalCycleSeconds = (settings.chimeIntervalMinutes.coerceAtLeast(1) * 60).coerceAtLeast(1)
    val elapsedSeconds = (totalCycleSeconds - remainingSeconds).coerceIn(0, totalCycleSeconds)
    val progressRatio = (elapsedSeconds.toFloat() / totalCycleSeconds.toFloat()).coerceIn(0f, 1f)

    val (minsLeft, formattedSecsLeft) = TimeAndAlertUtils.formatMinutesAndSeconds(remainingSeconds)

    val activeSoundDisplay = when (settings.soundSourceType) {
        "CUSTOM_FILE" -> settings.customSoundLabel.ifBlank { "Custom Audio File" }
        "RINGTONE" -> settings.customSoundLabel.ifBlank { "System Ringtone" }
        else -> settings.selectedSoundName
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
        shape = RoundedCornerShape(20.dp),
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(if (compactMode) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Live HD Precision Countdown Ring
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(if (compactMode) 56.dp else 66.dp)
                ) {
                    HdPrecisionCountdownRing(
                        progress = if (settings.chimeEnabled) progressRatio else 0f,
                        trackColor = activeTheme.dividerColor,
                        indicatorColor = activeTheme.circadianCueColor,
                        strokeWidth = 4.5.dp
                    )
                    Text(
                        text = "${settings.chimeIntervalMinutes}m",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ChronometerMonoFont,
                        color = activeTheme.numberColor
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.alert_interval_title),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        color = activeTheme.accentColor
                    )

                    if (settings.chimeEnabled) {
                        Text(
                            text = if (minsLeft == 0) {
                                "Next alert in ${remainingSeconds}s"
                            } else {
                                "Alert in ${minsLeft}m ${formattedSecsLeft}s"
                            },
                            fontSize = if (compactMode) 15.sp else 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = ChronometerMonoFont,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                            color = activeTheme.numberColor
                        )
                    } else {
                        Text(
                            text = "Interval alerts paused",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ChronometerDisplayFont,
                            color = activeTheme.textSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = activeTheme.textSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = activeSoundDisplay,
                            fontSize = 11.sp,
                            color = activeTheme.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { showSoundPicker = !showSoundPicker },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Choose Custom Alert Sound",
                            tint = if (showSoundPicker) activeTheme.accentColor else activeTheme.textPrimary
                        )
                    }

                    IconButton(
                        onClick = { viewModel.testPlaySound() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = activeTheme.accentColor.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("test_sound_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.cd_test_alert_sound),
                            tint = activeTheme.accentColor
                        )
                    }
                }
            }

            // Exact 15..20 Minute Interval Selector Chips
            IntervalMinuteSelectorSection(
                currentMinutes = settings.chimeIntervalMinutes,
                activeTheme = activeTheme,
                onSelectMinutes = { viewModel.updateChimeInterval(it) }
            )

            // Snooze & Reset Interval Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { viewModel.snoozeChime(5) },
                    enabled = settings.chimeEnabled,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, activeTheme.accentColor.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(
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
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Snooze 5m",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.snoozeChime(10) },
                    enabled = settings.chimeEnabled,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = activeTheme.textPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("snooze_10m_button")
                ) {
                    Text(
                        text = "+10m",
                        fontSize = 12.sp,
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
                        tint = if (settings.chimeEnabled) activeTheme.accentColor else activeTheme.textSecondary.copy(alpha = 0.4f)
                    )
                }
            }

            // Expandable Custom Sound Picker
            AnimatedVisibility(visible = showSoundPicker) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = activeTheme.bgEnd,
                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(12.dp)) {
                        CustomSoundSelectorSection(
                            settings = settings,
                            activeTheme = activeTheme,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IntervalMinuteSelectorSection(
    currentMinutes: Int,
    activeTheme: ClockVisualTheme,
    onSelectMinutes: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (15..20).forEach { minuteVal ->
                val selected = currentMinutes == minuteVal
                Surface(
                    onClick = { onSelectMinutes(minuteVal) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) activeTheme.accentColor else activeTheme.bgEnd,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selected) activeTheme.accentColor else activeTheme.dividerColor
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("interval_chip_$minuteVal")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${minuteVal}m",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (selected) activeTheme.onAccentColor else activeTheme.numberColor
                        )
                    }
                }
            }
        }

        val sliderVal = currentMinutes.coerceIn(15, 20).toFloat()
        Slider(
            value = sliderVal,
            onValueChange = { newVal ->
                val rounded = newVal.roundToInt().coerceIn(15, 20)
                if (rounded != currentMinutes) {
                    onSelectMinutes(rounded)
                }
            },
            valueRange = 15f..20f,
            steps = 4,
            colors = SliderDefaults.colors(
                thumbColor = activeTheme.accentColor,
                activeTrackColor = activeTheme.accentColor,
                inactiveTrackColor = activeTheme.dividerColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .testTag("interval_slider_15_20")
        )
    }
}

@Composable
fun CustomSoundSelectorSection(
    settings: ClockSettings,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel
) {
    val context = LocalContext.current

    // Zero-permission SAF Document Picker for local audio files (.mp3, .wav, .ogg, .m4a)
    val audioDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            val displayName = resolveAudioFileName(context, uri)
            viewModel.updateCustomAudioFile(uri.toString(), displayName)
        }
    }

    // Android System Ringtone / Notification Picker
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (pickedUri != null) {
                val title = try {
                    RingtoneManager.getRingtone(context, pickedUri)?.getTitle(context) ?: "System Ringtone"
                } catch (_: Throwable) {
                    "System Ringtone"
                }
                viewModel.updateSystemRingtone(pickedUri.toString(), title)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.alert_sound_source_title),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = activeTheme.accentColor,
            letterSpacing = 0.8.sp
        )

        // Custom Device Audio File & System Ringtone Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isCustomFile = settings.soundSourceType == "CUSTOM_FILE"
            val isRingtone = settings.soundSourceType == "RINGTONE"

            Surface(
                onClick = { audioDocumentLauncher.launch(arrayOf("audio/*")) },
                shape = RoundedCornerShape(12.dp),
                color = if (isCustomFile) activeTheme.accentColor else activeTheme.cardBackground,
                border = BorderStroke(1.dp, if (isCustomFile) activeTheme.accentColor else activeTheme.dividerColor),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("pick_device_audio_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        tint = if (isCustomFile) activeTheme.onAccentColor else activeTheme.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.sound_pick_device_file),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCustomFile) activeTheme.onAccentColor else activeTheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(
                            RingtoneManager.EXTRA_RINGTONE_TYPE,
                            RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_ALARM
                        )
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        if (isRingtone && settings.customSoundUri.isNotBlank()) {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(settings.customSoundUri))
                        }
                    }
                    try {
                        ringtonePickerLauncher.launch(intent)
                    } catch (_: Exception) {
                        viewModel.updateSelectedSound("System Accent")
                    }
                },
                shape = RoundedCornerShape(12.dp),
                color = if (isRingtone) activeTheme.accentColor else activeTheme.cardBackground,
                border = BorderStroke(1.dp, if (isRingtone) activeTheme.accentColor else activeTheme.dividerColor),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("pick_system_ringtone_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = if (isRingtone) activeTheme.onAccentColor else activeTheme.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.sound_pick_ringtone),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRingtone) activeTheme.onAccentColor else activeTheme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (settings.soundSourceType != "BUILTIN" && settings.customSoundLabel.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = activeTheme.accentColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, activeTheme.accentColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.soundSourceType == "CUSTOM_FILE") "ACTIVE DEVICE AUDIO" else "ACTIVE SYSTEM RINGTONE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.accentColor
                        )
                        Text(
                            text = settings.customSoundLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { viewModel.testPlaySound() }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Preview Selected Custom Sound",
                            tint = activeTheme.accentColor
                        )
                    }
                }
            }
        }

        // Built-in Synthesized Chimes Grid
        Text(
            text = stringResource(R.string.sound_builtin_header),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = activeTheme.textSecondary
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AudioSynthEngine.BuiltInSoundNames.chunked(2).forEach { rowSounds ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowSounds.forEach { soundName ->
                        val selected = settings.soundSourceType == "BUILTIN" && settings.selectedSoundName == soundName
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) activeTheme.accentColor
                                    else activeTheme.bgEnd
                                )
                                .clickable { viewModel.updateSelectedSound(soundName) }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("builtin_sound_${soundName.lowercase().replace(' ', '_')}")
                        ) {
                            Text(
                                text = soundName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) activeTheme.onAccentColor else activeTheme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                                contentDescription = "Select $soundName",
                                tint = if (selected) activeTheme.onAccentColor else activeTheme.accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (rowSounds.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun resolveAudioFileName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Custom Audio"
    } catch (_: Throwable) {
        "Custom Audio"
    }
}
