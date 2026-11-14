package com.example.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AppDatabase
import com.example.data.ClockRepository
import com.example.ui.ClockViewModel
import com.example.ui.ClockViewModelFactory
import com.example.ui.dialogs.AddFriendClockDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.resolveVisualTheme
import com.example.util.TimeAndAlertUtils
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class WorldClockWidgetConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val database = AppDatabase.getDatabase(this)
        val repository = ClockRepository(database.clockDao())

        setContent {
            MyApplicationTheme {
                val viewModel: ClockViewModel = viewModel(
                    factory = ClockViewModelFactory(repository, applicationContext)
                )
                WidgetConfigureScreen(
                    viewModel = viewModel,
                    appWidgetId = appWidgetId,
                    onSaveComplete = {
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            val resultValue = Intent().putExtra(
                                AppWidgetManager.EXTRA_APPWIDGET_ID,
                                appWidgetId
                            )
                            setResult(Activity.RESULT_OK, resultValue)
                        } else {
                            setResult(Activity.RESULT_OK)
                        }
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@Composable
private fun WidgetConfigureScreen(
    viewModel: ClockViewModel,
    appWidgetId: Int,
    onSaveComplete: () -> Unit,
    onCancel: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val friendClocks by viewModel.friendClocks.collectAsStateWithLifecycle()
    val now by viewModel.currentLocalTime.collectAsStateWithLifecycle()
    val activeTheme = resolveVisualTheme(settings, localTime = now)
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showAddCityDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(activeTheme.bgStart, activeTheme.bgEnd)))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = activeTheme.accentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = null,
                        tint = activeTheme.accentColor,
                        modifier = Modifier.padding(10.dp).size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.widget_configure_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = activeTheme.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.widget_configure_subtitle),
                        fontSize = 12.sp,
                        color = activeTheme.textSecondary
                    )
                }
            }

            // Live Home Screen Widget Preview
            val previewAlpha = (settings.homeWidgetBgAlpha / 255f).coerceIn(0.35f, 1f)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = activeTheme.bgEnd.copy(alpha = previewAlpha)
                ),
                border = BorderStroke(1.5.dp, activeTheme.dividerColor)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val timeFmt = remember(settings.digital24HourFormat) {
                        DateTimeFormatter.ofPattern(
                            if (settings.digital24HourFormat) "HH:mm" else "hh:mm a",
                            Locale.US
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "WIDGET PREVIEW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeTheme.accentColor,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = now.zone.id,
                                fontSize = 11.sp,
                                color = activeTheme.textSecondary
                            )
                        }
                        Text(
                            text = now.format(timeFmt),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeTheme.numberColor
                        )
                    }

                    val pinned = friendClocks.filter { it.showInWidget }
                    if (pinned.isEmpty()) {
                        Text(
                            text = "Select at least one city below to show inside the widget.",
                            fontSize = 11.sp,
                            color = activeTheme.textSecondary
                        )
                    } else {
                        pinned.take(3).forEach { city ->
                            val cityTime = now.withZoneSameInstant(TimeAndAlertUtils.safeZoneId(city.timezoneId))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = activeTheme.cardBackground.copy(alpha = previewAlpha)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = city.cityLabel.ifBlank { city.friendName },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = activeTheme.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (settings.worldClockShowOffset) {
                                            Text(
                                                text = TimeAndAlertUtils.formatRelativeOffset(now, city.timezoneId),
                                                fontSize = 10.sp,
                                                color = activeTheme.textSecondary
                                            )
                                        }
                                    }
                                    Text(
                                        text = cityTime.format(timeFmt),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeTheme.numberColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Opacity & Format Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
                border = BorderStroke(1.dp, activeTheme.dividerColor.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val opacityPct = ((settings.homeWidgetBgAlpha / 255f) * 100).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Widget Background Opacity",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.textPrimary
                        )
                        Text(
                            text = "$opacityPct%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.accentColor
                        )
                    }
                    Slider(
                        value = settings.homeWidgetBgAlpha.toFloat(),
                        onValueChange = { viewModel.updateHomeWidgetBgAlpha(it.toInt()) },
                        valueRange = 90f..255f,
                        colors = SliderDefaults.colors(
                            thumbColor = activeTheme.accentColor,
                            activeTrackColor = activeTheme.accentColor,
                            inactiveTrackColor = activeTheme.dividerColor
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("24-Hour Time Format", fontSize = 13.sp, color = activeTheme.textPrimary)
                        Switch(
                            checked = settings.digital24HourFormat,
                            onCheckedChange = { viewModel.toggle24HourFormat(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show Relative UTC Offset", fontSize = 13.sp, color = activeTheme.textPrimary)
                        Switch(
                            checked = settings.worldClockShowOffset,
                            onCheckedChange = { viewModel.toggleWorldClockShowOffset(it) }
                        )
                    }
                }
            }

            // Pinned Cities List
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
                border = BorderStroke(1.dp, activeTheme.dividerColor.copy(alpha = 0.5f))
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = activeTheme.accentColor, modifier = Modifier.size(18.dp))
                            Text(
                                text = "VISIBLE CITIES IN WIDGET",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeTheme.textPrimary
                            )
                        }
                        TextButton(
                            onClick = { showAddCityDialog = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = activeTheme.accentColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add City", color = activeTheme.accentColor, fontWeight = FontWeight.Bold)
                        }
                    }

                    friendClocks.forEach { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(activeTheme.bgEnd.copy(alpha = 0.45f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = city.cityLabel.ifBlank { city.friendName },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeTheme.textPrimary
                                )
                                Text(
                                    text = "${city.friendName} • ${city.timezoneId}",
                                    fontSize = 11.sp,
                                    color = activeTheme.textSecondary
                                )
                            }
                            Checkbox(
                                checked = city.showInWidget,
                                onCheckedChange = { viewModel.toggleFriendShowInWidget(city) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = activeTheme.accentColor,
                                    checkmarkColor = activeTheme.onAccentColor,
                                    uncheckedColor = activeTheme.textSecondary
                                )
                            )
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, activeTheme.dividerColor)
                ) {
                    Text("Cancel", color = activeTheme.textPrimary, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                WorldClockAppWidgetProvider.updateSingleWidgetNow(context, appWidgetId)
                            }
                            WorldClockAppWidgetProvider.updateAllWidgetsNow(context)
                            onSaveComplete()
                        }
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("save_widget_config_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeTheme.accentColor,
                        contentColor = activeTheme.onAccentColor
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.widget_save_apply), fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        if (showAddCityDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                AddFriendClockDialog(
                    availableTimezones = viewModel.getAvailableTimeZones(),
                    activeTheme = activeTheme,
                    onDismiss = { showAddCityDialog = false },
                    onConfirm = { name, city, tz ->
                        viewModel.addNewFriend(name, city, tz)
                        showAddCityDialog = false
                    }
                )
            }
        }
    }
}
