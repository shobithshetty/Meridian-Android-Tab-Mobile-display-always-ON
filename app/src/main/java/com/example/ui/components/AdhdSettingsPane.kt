package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ClockSettings
import com.example.ui.ClockViewModel
import com.example.ui.dialogs.BatteryOptimizationDialog
import com.example.ui.theme.*
import com.example.util.TimeAndAlertUtils
import com.example.widget.WorldClockWidgetConfigureActivity
import java.time.ZonedDateTime
import java.util.Locale

fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
    return true
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun buildBatterySettingsIntents(packageName: String): List<Intent> = listOf(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    },
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
)

fun buildBatteryListSettingsIntents(packageName: String): List<Intent> = listOf(
    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }
)

private fun launchFirstResolvableIntent(context: Context, intents: List<Intent>): Boolean {
    val hostActivity = context.findActivity()
    val packageManager = context.packageManager
    for (candidate in intents) {
        val intentToLaunch = Intent(candidate).apply {
            if (hostActivity == null) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val resolves = intentToLaunch.resolveActivity(packageManager) != null
        if (resolves) {
            try {
                if (hostActivity != null) {
                    hostActivity.startActivity(intentToLaunch)
                } else {
                    context.startActivity(intentToLaunch)
                }
                return true
            } catch (_: Exception) {
                // Try the next fallback settings intent.
            }
        }
    }
    return false
}

fun openBatteryOptimizationSettings(context: Context): Boolean {
    return launchFirstResolvableIntent(
        context = context,
        intents = buildBatterySettingsIntents(context.packageName)
    )
}

fun openSystemBatteryListSettings(context: Context): Boolean {
    return launchFirstResolvableIntent(
        context = context,
        intents = buildBatteryListSettingsIntents(context.packageName)
    )
}

@Composable
fun SettingsRowToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    activeTheme: ClockVisualTheme,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(activeTheme.cardBackground)
            .border(1.dp, activeTheme.dividerColor, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = activeTheme.textPrimary)
            Text(subtitle, fontSize = 11.sp, color = activeTheme.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = activeTheme.onAccentColor,
                checkedTrackColor = activeTheme.accentColor,
                uncheckedThumbColor = activeTheme.textSecondary,
                uncheckedTrackColor = activeTheme.dividerColor
            )
        )
    }
}

@Composable
fun AdhdSettingsPane(
    settings: ClockSettings,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    onExportClicked: () -> Unit,
    onImportClicked: () -> Unit,
    onOpenCustomThemeStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isBatteryDialogShowing by remember { mutableStateOf(false) }
    var isIgnoringOpt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var batteryLaunchFailed by remember { mutableStateOf(false) }
    val ambientLux = rememberAmbientLux()

    LaunchedEffect(isBatteryDialogShowing) {
        if (isBatteryDialogShowing) {
            isIgnoringOpt = isIgnoringBatteryOptimizations(context)
            batteryLaunchFailed = false
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringOpt = isIgnoringBatteryOptimizations(context)
                batteryLaunchFailed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (isBatteryDialogShowing) {
        BatteryOptimizationDialog(
            activeTheme = activeTheme,
            isIgnoringOptimization = isIgnoringOpt,
            showSettingsUnavailableError = batteryLaunchFailed,
            onDismiss = { isBatteryDialogShowing = false },
            onOpenSettings = {
                batteryLaunchFailed = !openBatteryOptimizationSettings(context)
            },
            onOpenBatteryList = {
                batteryLaunchFailed = !openSystemBatteryListSettings(context)
            }
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        SettingsGroup("COLOUR THEME", activeTheme, initiallyOpen = true) {
            // Time-of-Day Circadian Palette Card (Cool Morning -> Warm Evening / Soothing Night)
            val previewHour by viewModel.circadianPreviewHour.collectAsStateWithLifecycle()
            val currentTime by viewModel.currentLocalTime.collectAsStateWithLifecycle()
            val effectiveCircadianTime = remember(currentTime.hour, currentTime.minute, previewHour) {
                val hr = previewHour
                if (hr != null) currentTime.withHour(hr).withMinute(0) else currentTime
            }
            val circadianProfile = remember(effectiveCircadianTime.hour, effectiveCircadianTime.minute) {
                computeCircadianProfile(effectiveCircadianTime)
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
                shape = RoundedCornerShape(14.dp),
                border = hdBorderStroke(
                    accentColor = activeTheme.accentColor,
                    dividerColor = activeTheme.dividerColor,
                    width = 1.dp,
                    highlighted = settings.circadianShiftEnabled
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { viewModel.toggleCircadianShift(!settings.circadianShiftEnabled) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (circadianProfile.warmthFactor > 0.05f) Icons.Default.NightsStay else Icons.Default.WbSunny,
                                    contentDescription = null,
                                    tint = if (settings.circadianShiftEnabled) activeTheme.circadianCueColor else activeTheme.accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Time-of-Day Soothing Palette",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeTheme.textPrimary
                                )
                            }
                            Text(
                                text = if (settings.circadianShiftEnabled) {
                                    "Active: ${circadianProfile.label} • Cooler mornings, warmer evenings"
                                } else {
                                    "Shift cooler in the morning and warmer in the evening"
                                },
                                fontSize = 11.sp,
                                color = if (settings.circadianShiftEnabled) activeTheme.circadianCueColor else activeTheme.textSecondary
                            )
                        }
                        Switch(
                            checked = settings.circadianShiftEnabled,
                            onCheckedChange = { viewModel.toggleCircadianShift(it) },
                            modifier = Modifier.testTag("circadian_shift_toggle"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = activeTheme.onAccentColor,
                                checkedTrackColor = activeTheme.accentColor,
                                uncheckedThumbColor = activeTheme.textSecondary,
                                uncheckedTrackColor = activeTheme.dividerColor
                            )
                        )
                    }

                    if (settings.circadianShiftEnabled) {
                        Text(
                            text = "PREVIEW TIME-OF-DAY SHIFT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeTheme.textSecondary,
                            letterSpacing = 0.8.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            listOf<Pair<String, Int?>>(
                                "Live" to null,
                                "8 AM Cool" to 8,
                                "1 PM Clear" to 13,
                                "7 PM Warm" to 19,
                                "11 PM Night" to 23
                            ).forEach { (label, hrVal) ->
                                val isSel = previewHour == hrVal
                                Surface(
                                    onClick = { viewModel.setCircadianPreviewHour(hrVal) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) activeTheme.accentColor else activeTheme.bgEnd,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSel) activeTheme.accentColor else activeTheme.dividerColor
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 40.dp)
                                        .testTag("circadian_preview_${hrVal ?: "live"}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold,
                                            color = if (isSel) activeTheme.onAccentColor else activeTheme.textPrimary,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Prominent Custom HSV + Hex Color Studio Launcher
            val customPreview = remember(
                settings.customBgColorArgb,
                settings.customTextColorArgb,
                settings.customNumberColorArgb
            ) {
                buildCustomVisualTheme(
                    settings.customBgColorArgb,
                    settings.customTextColorArgb,
                    settings.customNumberColorArgb
                )
            }
            val isCustomActive = settings.themeName == "Custom"

            Surface(
                onClick = onOpenCustomThemeStudio,
                shape = RoundedCornerShape(14.dp),
                color = activeTheme.cardBackground,
                border = BorderStroke(
                    width = if (isCustomActive) 2.dp else 1.dp,
                    color = if (isCustomActive) activeTheme.accentColor else activeTheme.dividerColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .testTag("open_custom_theme_studio_button")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                            listOf(
                                customPreview.bgStart,
                                customPreview.textPrimary,
                                customPreview.numberColor
                            ).forEach { dot ->
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(dot)
                                        .border(1.5.dp, activeTheme.dividerColor, CircleShape)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.theme_customize_colors),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = activeTheme.textPrimary
                            )
                            Text(
                                text = if (isCustomActive) "Custom Theme Active • Tap to edit HSV & Hex" else "Open HSV Color Picker & Hex Studio",
                                fontSize = 10.sp,
                                color = if (isCustomActive) activeTheme.accentColor else activeTheme.textSecondary
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = stringResource(R.string.cd_open_theme_studio),
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 7 Curated Preset Themes Grid (2 per row)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AllPresetThemes.chunked(2).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            val selected = settings.themeName == preset.name
                            Surface(
                                onClick = { viewModel.updateWallpaperTheme(preset.name) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) activeTheme.accentColor else preset.theme.dividerColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 52.dp)
                                    .testTag("theme_preset_${preset.name.lowercase().replace(' ', '_')}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(preset.theme.bgStart, preset.theme.bgEnd)
                                            )
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = preset.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = preset.theme.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = preset.subtitle,
                                                fontSize = 9.sp,
                                                color = preset.theme.textSecondary,
                                                maxLines = 1
                                            )
                                        }
                                        Text(
                                            text = "12:45",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = ChronometerDisplayFont,
                                            style = TextStyle(fontFeatureSettings = "tnum"),
                                            color = preset.theme.numberColor
                                        )
                                    }
                                }
                            }
                        }
                        if (rowPresets.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        SettingsGroup("LAYOUT & BACKDROP", activeTheme, initiallyOpen = false) {
            // Layout style (independent of colour theme): applies on tablets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "LAYOUT STYLE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = activeTheme.textSecondary
                )
                LayoutStyle.options.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowOptions.forEach { (id, label, subtitle) ->
                            val selected = settings.layoutStyle == id
                            Surface(
                                onClick = { viewModel.updateLayoutStyle(id) },
                                shape = RoundedCornerShape(12.dp),
                                color = activeTheme.cardBackground,
                                border = BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) activeTheme.accentColor else activeTheme.dividerColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 52.dp)
                                    .testTag("layout_style_${id.lowercase()}")
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (selected) activeTheme.accentColor else activeTheme.textPrimary
                                    )
                                    Text(
                                        text = subtitle,
                                        fontSize = 9.sp,
                                        color = activeTheme.textSecondary,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Day strip: thin 24h bar along the bottom with alarms and focus sessions
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "DAY STRIP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = activeTheme.textSecondary
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple(true, "On", "Day progress, alarms, focus"),
                        Triple(false, "Off", "No bar along the bottom")
                    ).forEach { (on, label, subtitle) ->
                        val selected = settings.rhythmStripEnabled == on
                        Surface(
                            onClick = { viewModel.updateRhythmStrip(on) },
                            shape = RoundedCornerShape(12.dp),
                            color = activeTheme.cardBackground,
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) activeTheme.accentColor else activeTheme.dividerColor
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 52.dp)
                                .testTag("rhythm_strip_${label.lowercase()}")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selected) activeTheme.accentColor else activeTheme.textPrimary
                                )
                                Text(text = subtitle, fontSize = 9.sp, color = activeTheme.textSecondary, maxLines = 2)
                            }
                        }
                    }
                }
            }

            // Backdrop pattern: optional camo over any colour theme and layout style
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "BACKDROP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = activeTheme.textSecondary
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Backdrop.options.forEach { (id, label, subtitle) ->
                        val selected = settings.backdropPattern == id
                        Surface(
                            onClick = { viewModel.updateBackdropPattern(id) },
                            shape = RoundedCornerShape(12.dp),
                            color = activeTheme.cardBackground,
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) activeTheme.accentColor else activeTheme.dividerColor
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 52.dp)
                                .testTag("backdrop_${id.lowercase()}")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selected) activeTheme.accentColor else activeTheme.textPrimary
                                )
                                Text(text = subtitle, fontSize = 9.sp, color = activeTheme.textSecondary, maxLines = 2)
                            }
                        }
                    }
                }
            }

            // Night dimming: darkens the whole clock for bedrooms and overnight use
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "NIGHT DIMMING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = activeTheme.textSecondary
                )
                NightDim.options.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pair.forEach { (id, label, subtitle) ->
                        val selected = settings.nightDimMode == id
                        Surface(
                            onClick = { viewModel.updateNightDimMode(id) },
                            shape = RoundedCornerShape(12.dp),
                            color = activeTheme.cardBackground,
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) activeTheme.accentColor else activeTheme.dividerColor
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 52.dp)
                                .testTag("night_dim_${id.lowercase()}")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selected) activeTheme.accentColor else activeTheme.textPrimary
                                )
                                Text(text = subtitle, fontSize = 9.sp, color = activeTheme.textSecondary, maxLines = 2)
                            }
                        }
                    }
                }
                }
            }
        }

        SettingsGroup("ALWAYS-ON SCREEN", activeTheme, initiallyOpen = false) {
            AlwaysOnSettingsSection(settings, activeTheme, viewModel)
        }

        SettingsGroup("PLANNER & ROUTINES", activeTheme, initiallyOpen = false) {
            PlannerSettingsSection(settings, activeTheme, viewModel)
        }

        SettingsGroup("SOUND & BACKUP", activeTheme, initiallyOpen = false) {
            SoundAndBackupSettingsSection(settings, activeTheme, viewModel)
        }

        SettingsGroup("WEATHER", activeTheme, initiallyOpen = false) {
            WeatherSettingsSection(settings, activeTheme, viewModel)
        }


        // 2. 15–20 MINUTE PRECISION INTERVAL & CUSTOM SOUND
        SettingsGroup("ALERT INTERVAL & SOUND", activeTheme) {
            IntervalMinuteSelectorSection(
                currentMinutes = settings.chimeIntervalMinutes,
                activeTheme = activeTheme,
                onSelectMinutes = { viewModel.updateChimeInterval(it) }
            )

            CustomSoundSelectorSection(
                settings = settings,
                activeTheme = activeTheme,
                viewModel = viewModel
            )
        }

        // 3. WORLD CLOCK WIDGET & HOME SCREEN WIDGET
        SettingsGroup("WORLD CLOCKS & WIDGET", activeTheme) {

            SettingsRowToggle(
                title = "Display World Clock Alongside Main Clock",
                subtitle = "Keep multi-city live times visible right beside your local clock",
                checked = settings.showWorldClockBesideMain,
                activeTheme = activeTheme,
                onCheckedChange = { viewModel.toggleWorldClockBesideMain(it) }
            )

            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(context, WorldClockWidgetConfigureActivity::class.java)
                    )
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, activeTheme.accentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    tint = activeTheme.accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Configure Android Home Screen Widget",
                    fontWeight = FontWeight.Bold,
                    color = activeTheme.accentColor,
                    fontSize = 12.sp
                )
            }
        }

        // 4. SCREEN ORIENTATION LOCK
        SettingsGroup("SCREEN ORIENTATION", activeTheme) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    0 to "Auto/Sensor",
                    1 to "Portrait",
                    2 to "Landscape"
                ).forEach { (modeVal, label) ->
                    val selected = settings.orientationMode == modeVal
                    Surface(
                        onClick = { viewModel.updateOrientationMode(modeVal) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) activeTheme.accentColor else activeTheme.cardBackground,
                        border = BorderStroke(1.dp, if (selected) activeTheme.accentColor else activeTheme.dividerColor),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) activeTheme.onAccentColor else activeTheme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 5. CLOCK, SENSOR & FOCUS TOGGLES
        SettingsGroup("CLOCK & FOCUS", activeTheme) {

            SettingsRowToggle(
                title = "Display Seconds",
                subtitle = "Show live seconds on main clock",
                checked = settings.showSeconds,
                activeTheme = activeTheme,
                onCheckedChange = { viewModel.toggleShowSeconds(it) }
            )

            SettingsRowToggle(
                title = "24-Hour Format",
                subtitle = "Use 24-hour time across local and world clocks",
                checked = settings.digital24HourFormat,
                activeTheme = activeTheme,
                onCheckedChange = { viewModel.toggle24HourFormat(it) }
            )

            SettingsRowToggle(
                title = "Visual Edge Flash on Alert",
                subtitle = "Ultra-HD peripheral pulse when interval chimes trigger",
                checked = settings.flashScreenEnabled,
                activeTheme = activeTheme,
                onCheckedChange = { viewModel.toggleFlashEnabled(it) }
            )

            OutlinedButton(
                onClick = { viewModel.previewVisualFlash() },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, activeTheme.accentColor.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("preview_visual_pulse_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Preview Visual Edge Pulse",
                    tint = activeTheme.accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Preview Ultra-HD Edge Pulse",
                    fontWeight = FontWeight.Bold,
                    color = activeTheme.accentColor,
                    fontSize = 12.sp
                )
            }

            SettingsRowToggle(
                title = "Align 15m/20m to Top of Hour",
                subtitle = "Snap 15m (:00, :15, :30, :45) or 20m (:00, :20, :40) to clock hour",
                checked = settings.alignToTopHour,
                activeTheme = activeTheme,
                onCheckedChange = { viewModel.toggleAlignmentEnabled(it) }
            )

            // Eye Care & Screen Breaks Configuration Card
            val remainingEyeSeconds by viewModel.remainingEyeBreakSeconds.collectAsStateWithLifecycle()
            val (eyeMinsLeft, eyeSecsFormatted) = TimeAndAlertUtils.formatMinutesAndSeconds(remainingEyeSeconds)

            Card(
                colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
                shape = RoundedCornerShape(14.dp),
                border = hdBorderStroke(
                    accentColor = activeTheme.accentColor,
                    dividerColor = activeTheme.dividerColor,
                    width = 1.dp,
                    highlighted = settings.eyeBreakEnabled
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clickable { viewModel.toggleEyeBreakEnabled(!settings.eyeBreakEnabled) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = if (settings.eyeBreakEnabled) activeTheme.circadianCueColor else activeTheme.accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.eye_break_card_title),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeTheme.textPrimary
                                )
                            }
                            Text(
                                text = if (settings.eyeBreakEnabled) {
                                    "Every ${settings.eyeBreakIntervalMinutes}m • Next pop-up in ${eyeMinsLeft}m ${eyeSecsFormatted}s"
                                } else {
                                    stringResource(R.string.eye_break_card_subtitle_off)
                                },
                                fontSize = 11.sp,
                                color = if (settings.eyeBreakEnabled) activeTheme.circadianCueColor else activeTheme.textSecondary
                            )
                        }
                        Switch(
                            checked = settings.eyeBreakEnabled,
                            onCheckedChange = { viewModel.toggleEyeBreakEnabled(it) },
                            modifier = Modifier.testTag("eye_break_toggle"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = activeTheme.onAccentColor,
                                checkedTrackColor = activeTheme.accentColor,
                                uncheckedThumbColor = activeTheme.textSecondary,
                                uncheckedTrackColor = activeTheme.dividerColor
                            )
                        )
                    }

                    Text(
                        text = stringResource(R.string.eye_break_section_title),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = activeTheme.accentColor,
                        letterSpacing = 0.8.sp
                    )

                    // Quick Interval Chips (including 20m & 50m)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(20, 30, 45, 50, 60).forEach { mins ->
                            val isSelected = settings.eyeBreakIntervalMinutes == mins
                            Surface(
                                onClick = { viewModel.updateEyeBreakInterval(mins) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) activeTheme.accentColor else activeTheme.bgEnd,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) activeTheme.accentColor else activeTheme.dividerColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 44.dp)
                                    .testTag("eye_break_preset_$mins")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${mins}m",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSelected) activeTheme.onAccentColor else activeTheme.textPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Custom Any-Minute Stepper (1..180 min)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = activeTheme.bgEnd,
                        border = BorderStroke(1.dp, activeTheme.dividerColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.updateEyeBreakInterval(settings.eyeBreakIntervalMinutes - 5) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                                    modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 40.dp)
                                ) {
                                    Text("-5m", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = activeTheme.textPrimary)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.updateEyeBreakInterval(settings.eyeBreakIntervalMinutes - 1) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                                    modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                                ) {
                                    Text("-1m", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = activeTheme.textPrimary)
                                }
                            }

                            Text(
                                text = "${settings.eyeBreakIntervalMinutes} min",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = ChronometerMonoFont,
                                color = activeTheme.numberColor
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.updateEyeBreakInterval(settings.eyeBreakIntervalMinutes + 1) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                                    modifier = Modifier.defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                                ) {
                                    Text("+1m", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = activeTheme.textPrimary)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.updateEyeBreakInterval(settings.eyeBreakIntervalMinutes + 5) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                                    modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 40.dp)
                                ) {
                                    Text("+5m", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = activeTheme.textPrimary)
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.triggerEyeBreakNow() },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, activeTheme.accentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("preview_eye_break_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = activeTheme.accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.eye_break_preview_btn),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.accentColor
                        )
                    }
                }
            }

            // Comprehensive Scheduled Quiet Hours / Evening Snooze Section
            Card(
                colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, activeTheme.dividerColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scheduled Quiet Hours & Evening Snooze",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeTheme.textPrimary
                            )
                            val summary: String = remember(
                                settings.quietHoursStartHour,
                                settings.quietHoursStartMinute,
                                settings.quietHoursEndHour,
                                settings.quietHoursEndMinute,
                                settings.quietHoursDaysOfWeek,
                                settings.digital24HourFormat
                            ) {
                                TimeAndAlertUtils.formatQuietHoursSummary(
                                    settings.quietHoursStartHour,
                                    settings.quietHoursStartMinute,
                                    settings.quietHoursEndHour,
                                    settings.quietHoursEndMinute,
                                    settings.quietHoursDaysOfWeek,
                                    settings.digital24HourFormat
                                )
                            }
                            Text(
                                text = if (settings.quietHoursEnabled) "Active: $summary" else "Mutes chimes during selected hours & days",
                                fontSize = 11.sp,
                                color = if (settings.quietHoursEnabled) activeTheme.accentColor else activeTheme.textSecondary
                            )
                        }

                        Switch(
                            checked = settings.quietHoursEnabled,
                            onCheckedChange = { viewModel.toggleQuietHoursEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = activeTheme.onAccentColor,
                                checkedTrackColor = activeTheme.accentColor,
                                uncheckedThumbColor = activeTheme.textSecondary,
                                uncheckedTrackColor = activeTheme.dividerColor
                            )
                        )
                    }

                    if (settings.quietHoursEnabled) {
                        // Start & End Hour Steppers / Quick Chips
                        Text(
                            text = "TIME WINDOW (START – END)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeTheme.accentColor,
                            letterSpacing = 0.8.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple("Early Eve 18:00 – 08:00", 18, 8),
                                Triple("Evening 20:00 – 08:00", 20, 8),
                                Triple("Night 22:00 – 07:00", 22, 7),
                                Triple("Work Focus 13:00 – 16:00", 13, 16)
                            ).forEach { (label, sH, eH) ->
                                val isSelected = settings.quietHoursStartHour == sH && settings.quietHoursEndHour == eH
                                Surface(
                                    onClick = {
                                        viewModel.updateQuietHoursSchedule(
                                            startH = sH,
                                            startM = 0,
                                            endH = eH,
                                            endM = 0,
                                            activeDays = settings.quietHoursDaysOfWeek
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) activeTheme.accentColor else activeTheme.bgEnd,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) activeTheme.accentColor else activeTheme.dividerColor
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) activeTheme.onAccentColor else activeTheme.textPrimary,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Start & End Hour Steppers (Allows setting starting at 17:00, 18:00, etc. anytime)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Start Hour Selector
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = activeTheme.bgEnd,
                                border = BorderStroke(1.dp, activeTheme.dividerColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Start: ${String.format(Locale.US, "%02d:00", settings.quietHoursStartHour)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeTheme.numberColor
                                    )
                                    Row {
                                        IconButton(
                                            onClick = {
                                                val prevH = if (settings.quietHoursStartHour == 0) 23 else settings.quietHoursStartHour - 1
                                                viewModel.updateQuietHoursSchedule(
                                                    startH = prevH,
                                                    startM = 0,
                                                    endH = settings.quietHoursEndHour,
                                                    endM = settings.quietHoursEndMinute,
                                                    activeDays = settings.quietHoursDaysOfWeek
                                                )
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = activeTheme.accentColor)
                                        }
                                        IconButton(
                                            onClick = {
                                                val nextH = (settings.quietHoursStartHour + 1) % 24
                                                viewModel.updateQuietHoursSchedule(
                                                    startH = nextH,
                                                    startM = 0,
                                                    endH = settings.quietHoursEndHour,
                                                    endM = settings.quietHoursEndMinute,
                                                    activeDays = settings.quietHoursDaysOfWeek
                                                )
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = activeTheme.accentColor)
                                        }
                                    }
                                }
                            }

                            // End Hour Selector
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = activeTheme.bgEnd,
                                border = BorderStroke(1.dp, activeTheme.dividerColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "End: ${String.format(Locale.US, "%02d:00", settings.quietHoursEndHour)}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = activeTheme.numberColor
                                    )
                                    Row {
                                        IconButton(
                                            onClick = {
                                                val prevH = if (settings.quietHoursEndHour == 0) 23 else settings.quietHoursEndHour - 1
                                                viewModel.updateQuietHoursSchedule(
                                                    startH = settings.quietHoursStartHour,
                                                    startM = settings.quietHoursStartMinute,
                                                    endH = prevH,
                                                    endM = 0,
                                                    activeDays = settings.quietHoursDaysOfWeek
                                                )
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = activeTheme.accentColor)
                                        }
                                        IconButton(
                                            onClick = {
                                                val nextH = (settings.quietHoursEndHour + 1) % 24
                                                viewModel.updateQuietHoursSchedule(
                                                    startH = settings.quietHoursStartHour,
                                                    startM = settings.quietHoursStartMinute,
                                                    endH = nextH,
                                                    endM = 0,
                                                    activeDays = settings.quietHoursDaysOfWeek
                                                )
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = activeTheme.accentColor)
                                        }
                                    }
                                }
                            }
                        }

                        // Days of Week Selection Chips (Mon .. Sun)
                        Text(
                            text = "ACTIVE DAYS OF WEEK",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = activeTheme.accentColor,
                            letterSpacing = 0.8.sp
                        )

                        val dayLabels = listOf(
                            1 to "Mon",
                            2 to "Tue",
                            3 to "Wed",
                            4 to "Thu",
                            5 to "Fri",
                            6 to "Sat",
                            7 to "Sun"
                        )

                        val currentActiveDays = remember(settings.quietHoursDaysOfWeek) {
                            if (settings.quietHoursDaysOfWeek.isBlank() || settings.quietHoursDaysOfWeek == "ALL") {
                                (1..7).toSet()
                            } else {
                                settings.quietHoursDaysOfWeek.split(',')
                                    .mapNotNull { it.trim().toIntOrNull() }
                                    .toSet()
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            dayLabels.forEach { (dayInt, dayName) ->
                                val isDaySelected = dayInt in currentActiveDays
                                Surface(
                                    onClick = {
                                        val newDays = if (isDaySelected) {
                                            currentActiveDays - dayInt
                                        } else {
                                            currentActiveDays + dayInt
                                        }
                                        val newString = if (newDays.size == 7) {
                                            "ALL"
                                        } else {
                                            newDays.sorted().joinToString(",")
                                        }
                                        viewModel.updateQuietHoursSchedule(
                                            startH = settings.quietHoursStartHour,
                                            startM = settings.quietHoursStartMinute,
                                            endH = settings.quietHoursEndHour,
                                            endM = settings.quietHoursEndMinute,
                                            activeDays = newString
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isDaySelected) activeTheme.accentColor else activeTheme.bgEnd,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDaySelected) activeTheme.accentColor else activeTheme.dividerColor
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = dayName,
                                            fontSize = 10.sp,
                                            fontWeight = if (isDaySelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            color = if (isDaySelected) activeTheme.onAccentColor else activeTheme.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingsRowToggle(
                title = "Auto Room Light Adaptation",
                subtitle = if (ambientLux != null) "Ambient sensor: ${ambientLux.toInt()} lx" else "Adapt preset themes to room brightness",
                checked = settings.autoAmbientSensorEnabled,
                activeTheme = activeTheme,
                onCheckedChange = { viewModel.toggleAutoAmbientSensor(it) }
            )
        }

        // 6. BATTERY RELIABILITY & OFFLINE PORTABILITY
        SettingsGroup("BATTERY & BACKUP", activeTheme) {

            Card(
                colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, activeTheme.dividerColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .clickable { isBatteryDialogShowing = true }
                    .testTag("battery_optimization_card")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = if (isIgnoringOpt) Color(0xFF1B432C) else Color(0xFF4A2B14),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isIgnoringOpt) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = "Battery Status Icon",
                                tint = if (isIgnoringOpt) Color(0xFF81C784) else Color(0xFFFFB74D),
                                modifier = Modifier.padding(6.dp).size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Battery Optimization",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeTheme.textPrimary
                            )
                            Text(
                                text = if (isIgnoringOpt) "Unrestricted • Precise alerts" else "Tap to allow unrestricted background alerts",
                                fontSize = 10.sp,
                                color = if (isIgnoringOpt) Color(0xFF81C784) else Color(0xFFFFB74D),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Battery Info",
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onExportClicked() }
                        .testTag("export_backup_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Backup",
                            tint = activeTheme.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Export Backup",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.textPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
                    border = BorderStroke(1.dp, activeTheme.dividerColor),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImportClicked() }
                        .testTag("import_backup_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Import Backup",
                            tint = activeTheme.accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Import Backup",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = activeTheme.textPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Diagnostic Logs & Crash History Button
            var isDiagnosticViewerOpen by remember { mutableStateOf(false) }
            if (isDiagnosticViewerOpen) {
                com.example.ui.dialogs.DiagnosticLogViewerDialog(
                    activeTheme = activeTheme,
                    onDismiss = { isDiagnosticViewerOpen = false }
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, activeTheme.accentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .clickable { isDiagnosticViewerOpen = true }
                    .testTag("diagnostic_logs_button")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = activeTheme.bgEnd,
                            border = BorderStroke(1.dp, activeTheme.dividerColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = activeTheme.accentColor,
                                modifier = Modifier.padding(6.dp).size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Diagnostic Reports & Crash History (JSON)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeTheme.textPrimary
                            )
                            Text(
                                text = "🔒 Privacy Disclosure: Zero PII — anonymous technical diagnostics only",
                                fontSize = 10.sp,
                                color = activeTheme.accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = activeTheme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
