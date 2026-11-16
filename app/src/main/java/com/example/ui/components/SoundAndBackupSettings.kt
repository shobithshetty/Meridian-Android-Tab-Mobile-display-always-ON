package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClockSettings
import com.example.ui.ClockViewModel
import com.example.ui.theme.ClockVisualTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Alarm ringing style and the scheduled backup folder. Identical for every layout style. */
@Composable
fun SoundAndBackupSettingsSection(settings: ClockSettings, theme: ClockVisualTheme, viewModel: ClockViewModel) {
    val context = LocalContext.current

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            // Keep access across restarts; without this the folder stops working as soon as the app is closed.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.setAutoBackupFolder(uri.toString())
            Toast.makeText(context, "Backup folder saved. A copy is made once a day, after 3am.", Toast.LENGTH_LONG).show()
        }
    }

    @Composable
    fun Heading(text: String) = Text(
        text, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = theme.textSecondary
    )

    @Composable
    fun Chip(label: String, subtitle: String, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) = Surface(
        onClick = onClick, shape = RoundedCornerShape(12.dp), color = theme.cardBackground,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) theme.accentColor else theme.dividerColor),
        modifier = modifier.defaultMinSize(minHeight = 52.dp).testTag(tag)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = if (selected) theme.accentColor else theme.textPrimary)
            Text(subtitle, fontSize = 9.sp, color = theme.textSecondary, maxLines = 2)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Heading("WHEN AN ALARM RINGS")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AlarmStyle.options.forEach { (id, label, subtitle) ->
                Chip(label, subtitle, settings.alarmStyle == id, "alarm_style_${id.lowercase()}", Modifier.weight(1f)) {
                    viewModel.updateAlarmStyle(id)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Heading("AUTOMATIC BACKUP")
        val hasFolder = settings.autoBackupUri.isNotBlank()
        val lastText = if (settings.lastAutoBackupEpochDay >= 0) {
            "Last backup: " + LocalDate.ofEpochDay(settings.lastAutoBackupEpochDay).format(DateTimeFormatter.ofPattern("EEE d MMM"))
        } else "No backup made yet"
        Chip(
            label = if (hasFolder) "Daily backup is on" else "Choose a backup folder",
            subtitle = if (hasFolder) "$lastText. Keeps the newest 7. Runs while Meridian is open."
            else "A copy of your settings each night, saved outside the app so it survives an uninstall",
            selected = hasFolder, tag = "backup_choose_folder", modifier = Modifier.fillMaxWidth()
        ) { pickFolder.launch(null) }
        if (hasFolder) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Back up now", "Make a copy right away", false, "backup_now", Modifier.weight(1f)) {
                    viewModel.runAutoBackup()
                    Toast.makeText(context, "Backing up…", Toast.LENGTH_SHORT).show()
                }
                Chip("Turn off", "Stop daily backups", false, "backup_off", Modifier.weight(1f)) {
                    viewModel.setAutoBackupFolder("")
                }
            }
        }
    }
}
