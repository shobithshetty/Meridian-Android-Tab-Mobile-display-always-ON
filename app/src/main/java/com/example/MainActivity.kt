package com.example

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AlarmItem
import com.example.data.AppDatabase
import com.example.data.ClockRepository
import com.example.data.ClockSettings
import com.example.data.FriendClock
import com.example.ui.ClockViewModel
import com.example.ui.ClockViewModelFactory
import com.example.ui.components.*
import com.example.ui.dialogs.*
import com.example.ui.theme.*
import java.time.ZonedDateTime

enum class AdaptiveScreenPosture {
    SHORT_LANDSCAPE, // Mobile Landscape / Bedside Dock (height < 480dp)
    COMPACT,         // Mobile Portrait (width < 600dp)
    MEDIUM,          // Foldables & Portrait Tablets (600dp..839dp)
    EXPANDED         // Landscape Tablets & Desktop (>= 840dp)
}

fun classifyScreenPosture(maxWidth: Dp, maxHeight: Dp): AdaptiveScreenPosture {
    return when {
        maxHeight < 480.dp && maxWidth > maxHeight -> AdaptiveScreenPosture.SHORT_LANDSCAPE
        maxWidth < 600.dp -> AdaptiveScreenPosture.COMPACT
        maxWidth < 840.dp -> AdaptiveScreenPosture.MEDIUM
        else -> AdaptiveScreenPosture.EXPANDED
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        val database = AppDatabase.getDatabase(this)
        val repository = ClockRepository(database.clockDao())

        setContent {
            MyApplicationTheme {
                val clockViewModel: ClockViewModel = viewModel(
                    factory = ClockViewModelFactory(repository, LocalContext.current.applicationContext)
                )
                MeridianScreen(viewModel = clockViewModel)
            }
        }
    }
}

fun Context.findActivity(): ComponentActivity? {
    var cur = this
    while (cur is ContextWrapper) {
        if (cur is ComponentActivity) return cur
        cur = cur.baseContext
    }
    return cur as? ComponentActivity
}

@Composable
fun MeridianScreen(viewModel: ClockViewModel) {
    val currentLocalTime by viewModel.currentLocalTime.collectAsStateWithLifecycle()
    val isFlashing by viewModel.isFlashing.collectAsStateWithLifecycle()
    val pulseTriggerCount by viewModel.pulseTriggerCount.collectAsStateWithLifecycle()
    val circadianPreviewHour by viewModel.circadianPreviewHour.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val friendClocks by viewModel.friendClocks.collectAsStateWithLifecycle()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val activeEyeBreakPrompt by viewModel.activeEyeBreakPrompt.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    LaunchedEffect(settings.orientationMode) {
        val targetOrientation = when (settings.orientationMode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        activity?.let {
            try {
                if (it.requestedOrientation != targetOrientation) {
                    it.requestedOrientation = targetOrientation
                }
            } catch (_: Exception) {
            }
        }
    }

    val ambientLux = rememberAmbientLux()
    val effectiveThemeTime = remember(currentLocalTime.hour, currentLocalTime.minute, circadianPreviewHour) {
        val previewHr = circadianPreviewHour
        if (previewHr != null) {
            currentLocalTime.withHour(previewHr).withMinute(0)
        } else {
            currentLocalTime
        }
    }
    val targetTheme = remember(
        settings,
        ambientLux,
        effectiveThemeTime.hour,
        effectiveThemeTime.minute
    ) {
        resolveVisualTheme(
            settings = settings,
            ambientLux = ambientLux,
            localTime = effectiveThemeTime
        )
    }
    val activeTheme = rememberAnimatedVisualTheme(targetTheme)
    val isLightBg = activeTheme.bgStart.luminance() > 0.5f

    LaunchedEffect(isLightBg, activity) {
        activity?.let { act ->
            try {
                if (isLightBg) {
                    act.enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    )
                } else {
                    act.enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    var isAddingFriendDialogShowing by rememberSaveable { mutableStateOf(false) }
    var isAddingAlarmDialogShowing by rememberSaveable { mutableStateOf(false) }
    var isCustomThemeStudioShowing by rememberSaveable { mutableStateOf(false) }
    var isSettingsHubShowing by rememberSaveable { mutableStateOf(false) }
    var isBackupExportDialogShowing by rememberSaveable { mutableStateOf(false) }
    var isBackupImportDialogShowing by rememberSaveable { mutableStateOf(false) }
    var isClockSyncDialogShowing by rememberSaveable { mutableStateOf(false) }
    var isBreathingDialogShowing by rememberSaveable { mutableStateOf(false) }
    var isFocusDialogShowing by rememberSaveable { mutableStateOf(false) }
    var isAmbientDialogShowing by rememberSaveable { mutableStateOf(false) }
    val ringingAlarm by viewModel.ringingAlarm.collectAsState()
    val ambient by viewModel.ambient.collectAsState()
    val weatherByCity by viewModel.weather.collectAsState()
    val officialAlerts by viewModel.officialAlerts.collectAsState()
    val weatherUi = remember(weatherByCity, settings.weatherUnit) { WeatherUi(weatherByCity, settings.weatherUnit) }
    val focusSession by viewModel.focusSession.collectAsState()
    val focusCompletedLabel by viewModel.focusCompletedLabel.collectAsState()

    var pendingCrashLog by remember { mutableStateOf(com.example.util.CrashReportManager.getLastCrashReport(context)) }

    var companionTabSelection by rememberSaveable { mutableIntStateOf(3) }

    val anyOverlayOpen = isAddingFriendDialogShowing ||
        isAddingAlarmDialogShowing ||
        isCustomThemeStudioShowing ||
        isSettingsHubShowing ||
        isBackupExportDialogShowing ||
        isBackupImportDialogShowing ||
        isClockSyncDialogShowing ||
        isBreathingDialogShowing ||
        isFocusDialogShowing ||
        isAmbientDialogShowing ||
        activeEyeBreakPrompt != null ||
        pendingCrashLog != null

    BackHandler(enabled = anyOverlayOpen) {
        when {
            pendingCrashLog != null -> pendingCrashLog = null
            activeEyeBreakPrompt != null -> viewModel.dismissEyeBreakPrompt()
            isBreathingDialogShowing -> isBreathingDialogShowing = false
            isFocusDialogShowing -> isFocusDialogShowing = false
            isAmbientDialogShowing -> isAmbientDialogShowing = false
            isClockSyncDialogShowing -> isClockSyncDialogShowing = false
            isSettingsHubShowing -> {
                viewModel.setCircadianPreviewHour(null)
                isSettingsHubShowing = false
            }
            isCustomThemeStudioShowing -> isCustomThemeStudioShowing = false
            isAddingFriendDialogShowing -> isAddingFriendDialogShowing = false
            isAddingAlarmDialogShowing -> isAddingAlarmDialogShowing = false
            isBackupExportDialogShowing -> isBackupExportDialogShowing = false
            isBackupImportDialogShowing -> isBackupImportDialogShowing = false
        }
    }

    CompositionLocalProvider(
        LocalFocusLauncher provides { isFocusDialogShowing = true },
        LocalAmbientLauncher provides { isAmbientDialogShowing = true },
        LocalWeather provides weatherUi
    ) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = activeTheme.bgStart
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .hdBackdropSurface(
                    bgStart = activeTheme.bgStart,
                    bgEnd = activeTheme.bgEnd,
                    accentColor = activeTheme.accentColor
                )
        ) {
            val posture = classifyScreenPosture(maxWidth = maxWidth, maxHeight = maxHeight)

            // Every posture (phone/tablet, portrait/landscape) uses the chosen style; only CLASSIC keeps the legacy layouts.
            val usesStyledLayout = settings.layoutStyle != LayoutStyle.CLASSIC

            // Transition warnings: a quiet "10 / 5 min left" pill before a focus block ends or an alarm rings.
            val nowMillis = currentLocalTime.toInstant().toEpochMilli()
            val warning: Pair<String, String>? = if (!settings.transitionWarningsEnabled) null else {
                focusSession?.let { fs ->
                    TransitionWarning.thresholdReached(fs.endMillis - nowMillis, fs.durationMillis)?.let { m ->
                        "f-${fs.startMillis}-$m" to "$m min left: ${FocusCycle.pillLabel(fs)}"
                    }
                } ?: alarms.firstNotNullOfOrNull { a ->
                    TransitionWarning.millisUntil(a, currentLocalTime)?.let { left ->
                        TransitionWarning.thresholdReached(left, null)?.let { m ->
                            "a-${a.id}-${currentLocalTime.toLocalDate()}-$m" to
                                "Alarm in $m min" + if (a.label.isNotBlank()) ": ${a.label}" else ""
                        }
                    }
                }
            }
            var warningText by remember { mutableStateOf<String?>(null) }
            val warningsShown = remember { mutableSetOf<String>() }
            LaunchedEffect(warning?.first) {
                val w = warning
                if (w != null && warningsShown.add(w.first)) {
                    warningText = w.second
                    kotlinx.coroutines.delay(8_000)
                    warningText = null
                }
            }

            // The layout sits below a row of week/holiday, countdown and routine chips. The row takes real space
            // (instead of floating over the layout) so it can never cover a header on a narrow phone. It takes no
            // space at all when there is nothing to show, and leaves room for the focus pill and warning above it.
            // Small screens are tight on height: a phone only gets the row when there is real news (not just the week
            // number), and a short landscape screen overlays the chips on its free top centre instead of reserving a row.
            val ambientChipText = ambient?.let { a ->
                "♪ ${a.kind.label}" + (a.endsAtMillis?.let { " · ${SleepTimer.remainingText(it - nowMillis)}" } ?: "")
            }
            val weatherNotices = remember(friendClocks, weatherUi, officialAlerts, currentLocalTime.minute) {
                if (!settings.weatherEnabled) emptyList()
                else buildWeatherNotices(friendClocks, weatherUi, officialAlerts, nowMillis, java.time.ZoneId.systemDefault())
            }
            val isPhone = minOf(maxWidth, maxHeight) < 600.dp
            val overlayDock = posture == AdaptiveScreenPosture.SHORT_LANDSCAPE
            val dockTopPadding = 6.dp + (if (focusSession != null) 50.dp else 0.dp) + (if (warningText != null) 40.dp else 0.dp)
            Column(Modifier.fillMaxSize()) {
                if (!overlayDock) InfoDock(
                    settings = settings, now = currentLocalTime, theme = activeTheme,
                    onRoutineDone = { id, day -> viewModel.completeRoutine(id, day) },
                    ambientText = ambientChipText, onAmbientClick = { isAmbientDialogShowing = true },
                    weatherNotices = weatherNotices,
                    maxChips = if (isPhone) 2 else 6,
                    plainWeek = !isPhone,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(top = dockTopPadding, bottom = 4.dp, start = 16.dp, end = 16.dp)
                )
            // Nested so existing `this@BoxWithConstraints` sizes below describe the area under the chip row.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (usesStyledLayout) {
                when {
                    isWatchFace(settings.layoutStyle) -> WatchFaceLayout(
                        style = settings.layoutStyle,
                        currentLocalTime = currentLocalTime,
                        settings = settings,
                        friendClocks = friendClocks,
                        activeTheme = activeTheme,
                        viewModel = viewModel,
                        onAddFriendClicked = { isAddingFriendDialogShowing = true },
                        onOpenCustomThemeStudio = { isCustomThemeStudioShowing = true },
                        onOpenSettings = { isSettingsHubShowing = true },
                        onOpenSyncInspector = { isClockSyncDialogShowing = true },
                        onOpenBreathing = { isBreathingDialogShowing = true }
                    )
                    settings.layoutStyle == LayoutStyle.CALM -> CalmClockLayout(
                        currentLocalTime = currentLocalTime,
                        settings = settings,
                        friendClocks = friendClocks,
                        activeTheme = activeTheme,
                        viewModel = viewModel,
                        onAddFriendClicked = { isAddingFriendDialogShowing = true },
                        onOpenCustomThemeStudio = { isCustomThemeStudioShowing = true },
                        onOpenSettings = { isSettingsHubShowing = true },
                        onOpenSyncInspector = { isClockSyncDialogShowing = true },
                        onOpenBreathing = { isBreathingDialogShowing = true }
                    )
                    settings.layoutStyle == LayoutStyle.DASHBOARD -> DashboardClockLayout(
                        currentLocalTime = currentLocalTime,
                        settings = settings,
                        friendClocks = friendClocks,
                        activeTheme = activeTheme,
                        viewModel = viewModel,
                        onAddFriendClicked = { isAddingFriendDialogShowing = true },
                        onOpenCustomThemeStudio = { isCustomThemeStudioShowing = true },
                        onOpenSettings = { isSettingsHubShowing = true },
                        onOpenSyncInspector = { isClockSyncDialogShowing = true },
                        onOpenBreathing = { isBreathingDialogShowing = true }
                    )
                    else -> BoldClockLayout(
                        currentLocalTime = currentLocalTime,
                        settings = settings,
                        friendClocks = friendClocks,
                        activeTheme = activeTheme,
                        viewModel = viewModel,
                        onAddFriendClicked = { isAddingFriendDialogShowing = true },
                        onOpenCustomThemeStudio = { isCustomThemeStudioShowing = true },
                        onOpenSettings = { isSettingsHubShowing = true },
                        onOpenSyncInspector = { isClockSyncDialogShowing = true },
                        onOpenBreathing = { isBreathingDialogShowing = true }
                    )
                }
            } else Box(Modifier.fillMaxSize().lsBurnInDrift(currentLocalTime.minute)) { when (posture) {
                AdaptiveScreenPosture.COMPACT -> CompactPhoneDashboard(
                    containerWidth = this@BoxWithConstraints.maxWidth,
                    currentLocalTime = currentLocalTime,
                    settings = settings,
                    friendClocks = friendClocks,
                    alarms = alarms,
                    activeTheme = activeTheme,
                    viewModel = viewModel,
                    companionTabSelection = companionTabSelection,
                    onCompanionTabSelected = { companionTabSelection = it },
                    onAddFriendClicked = { isAddingFriendDialogShowing = true },
                    onAddAlarmClicked = { isAddingAlarmDialogShowing = true },
                    onOpenCustomThemeStudio = { isCustomThemeStudioShowing = true },
                    onOpenSettings = { isSettingsHubShowing = true },
                    onOpenSyncInspector = { isClockSyncDialogShowing = true },
                    onOpenBreathing = { isBreathingDialogShowing = true },
                    onExportClicked = { isBackupExportDialogShowing = true },
                    onImportClicked = { isBackupImportDialogShowing = true }
                )

                AdaptiveScreenPosture.SHORT_LANDSCAPE -> MobileLandscapeDockDashboard(
                    containerWidth = this@BoxWithConstraints.maxWidth,
                    currentLocalTime = currentLocalTime,
                    settings = settings,
                    friendClocks = friendClocks,
                    alarms = alarms,
                    activeTheme = activeTheme,
                    viewModel = viewModel,
                    companionTabSelection = companionTabSelection,
                    onCompanionTabSelected = { companionTabSelection = it },
                    onAddFriendClicked = { isAddingFriendDialogShowing = true },
                    onAddAlarmClicked = { isAddingAlarmDialogShowing = true },
                    onOpenCustomThemeStudio = { isCustomThemeStudioShowing = true },
                    onOpenSettings = { isSettingsHubShowing = true },
                    onOpenSyncInspector = { isClockSyncDialogShowing = true },
                    onOpenBreathing = { isBreathingDialogShowing = true },
                    onExportClicked = { isBackupExportDialogShowing = true },
                    onImportClicked = { isBackupImportDialogShowing = true }
                )

                AdaptiveScreenPosture.MEDIUM -> MediumTabletDashboard(
                    containerWidth = this@BoxWithConstraints.maxWidth,
                    containerHeight = this@BoxWithConstraints.maxHeight,
                    currentLocalTime = currentLocalTime,
                    settings = settings,
                    friendClocks = friendClocks,
                    alarms = alarms,
                    activeTheme = activeTheme,
                    viewModel = viewModel,
                    onAddFriendClicked = { isAddingFriendDialogShowing = true },
                    onOpenCustomThemeStudio = { isCustomThemeStudioShowing = true },
                    onOpenSettings = { isSettingsHubShowing = true },
                    onOpenSyncInspector = { isClockSyncDialogShowing = true },
                    onOpenBreathing = { isBreathingDialogShowing = true }
                )

                AdaptiveScreenPosture.EXPANDED -> ExpandedWidescreenDashboard(
                    containerWidth = this@BoxWithConstraints.maxWidth,
                    containerHeight = this@BoxWithConstraints.maxHeight,
                    currentLocalTime = currentLocalTime,
                    settings = settings,
                    friendClocks = friendClocks,
                    alarms = alarms,
                    activeTheme = activeTheme,
                    viewModel = viewModel,
                    onAddFriendClicked = { isAddingFriendDialogShowing = true },
                    onOpenCustomThemeStudio = { isCustomThemeStudioShowing = true },
                    onOpenSettings = { isSettingsHubShowing = true },
                    onOpenSyncInspector = { isClockSyncDialogShowing = true },
                    onOpenBreathing = { isBreathingDialogShowing = true }
                )
            } }
            } // BoxWithConstraints under the chip row
            } // Column

            if (overlayDock) InfoDock(
                settings = settings, now = currentLocalTime, theme = activeTheme,
                onRoutineDone = { id, day -> viewModel.completeRoutine(id, day) },
                ambientText = ambientChipText, onAmbientClick = { isAmbientDialogShowing = true },
                weatherNotices = weatherNotices,
                maxChips = 1, // floats over the free top centre, so it must stay narrow
                plainWeek = false,
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(top = dockTopPadding, start = 16.dp, end = 16.dp)
            )

            // Optional camo backdrop over any theme/layout (low opacity, taps pass through).
            if (settings.backdropPattern == Backdrop.CAMO) CamoOverlay(activeTheme)

            // Night dimming: a black scrim over the clock (not over dialogs). It has no pointer handling, so taps pass through.
            // Always-on clock: hold the screen awake (always, or only while charging, per setting).
            KeepScreenOnEffect(settings.keepAwakeMode)
            val sunIsDown = if (settings.nightDimMode == NightDim.SUN && settings.hasHomeLocation) {
                remember(currentLocalTime.minute, currentLocalTime.hour, settings.homeLatitude, settings.homeLongitude) {
                    com.example.util.SunTimes.isDarkAt(
                        currentLocalTime.toInstant(), settings.homeLatitude, settings.homeLongitude
                    )
                }
            } else null
            val dimAlpha = NightDim.scrimAlpha(settings.nightDimMode, currentLocalTime.hour, sunIsDown)
            if (dimAlpha > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = dimAlpha))
                )
            }

            // Day strip along the bottom edge of every layout style (not dimmed away: it is thin and informative).
            if (settings.rhythmStripEnabled) {
                RhythmStrip(
                    now = currentLocalTime, alarms = alarms, focus = focusSession, theme = activeTheme,
                    modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // Focus session: running bar and completion banner sit above every layout style (and above the dim).
            focusSession?.let { fs ->
                FocusSessionPill(
                    session = fs,
                    nowMillis = currentLocalTime.toInstant().toEpochMilli(),
                    theme = activeTheme,
                    onDone = { viewModel.advanceFocus() },
                    onStop = { viewModel.finishFocusSession(completed = false) },
                    modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 6.dp)
                )
            }

            // Transition warning pill (state is computed above the layout so the chip row can make room for it).
            warningText?.let { text ->
                TransitionWarningBanner(
                    text = text, theme = activeTheme,
                    modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(top = if (focusSession != null) 56.dp else 6.dp)
                )
            }
            focusCompletedLabel?.let { done ->
                FocusCompleteBanner(
                    label = done, theme = activeTheme, onDismiss = { viewModel.dismissFocusComplete() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // A ringing alarm sits above everything else so it can always be snoozed or dismissed.
            ringingAlarm?.let { ringing ->
                AlarmRingingBanner(
                    alarm = ringing, theme = activeTheme,
                    onSnooze = { viewModel.snoozeAlarm() }, onDismiss = { viewModel.dismissAlarm() },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Modal Scrim Overlays
            if (isAmbientDialogShowing) {
                ModalScrimBox(onDismiss = { isAmbientDialogShowing = false }) {
                    AmbientDialog(
                        theme = activeTheme, current = ambient, nowMillis = nowMillis,
                        onDismiss = { isAmbientDialogShowing = false },
                        onStop = { viewModel.stopAmbient(); isAmbientDialogShowing = false },
                        onStart = { kind, volume, minutes ->
                            viewModel.startAmbient(kind, volume, minutes)
                            isAmbientDialogShowing = false
                        }
                    )
                }
            }
            if (isFocusDialogShowing) {
                ModalScrimBox(onDismiss = { isFocusDialogShowing = false }) {
                    FocusSessionDialog(
                        theme = activeTheme,
                        onDismiss = { isFocusDialogShowing = false },
                        onStart = { minutes, label, rounds ->
                            viewModel.startFocusSession(minutes, label, rounds)
                            isFocusDialogShowing = false
                        }
                    )
                }
            }
            if (isAddingFriendDialogShowing) {
                ModalScrimBox(onDismiss = { isAddingFriendDialogShowing = false }) {
                    AddFriendClockDialog(
                        availableTimezones = viewModel.getAvailableTimeZones(),
                        activeTheme = activeTheme,
                        onDismiss = { isAddingFriendDialogShowing = false },
                        onConfirm = { name, city, tz ->
                            viewModel.addNewFriend(name, city, tz)
                            isAddingFriendDialogShowing = false
                        }
                    )
                }
            }

            if (isAddingAlarmDialogShowing) {
                ModalScrimBox(onDismiss = { isAddingAlarmDialogShowing = false }) {
                    AddAlarmDialog(
                        availableTimezones = viewModel.getAvailableTimeZones(),
                        friendClocks = friendClocks,
                        activeTheme = activeTheme,
                        onDismiss = { isAddingAlarmDialogShowing = false },
                        onConfirm = { hour, minute, tz, label ->
                            viewModel.addAlarm(hour, minute, tz, label)
                            isAddingAlarmDialogShowing = false
                        }
                    )
                }
            }

            if (isCustomThemeStudioShowing) {
                ModalScrimBox(onDismiss = { isCustomThemeStudioShowing = false }) {
                    CustomThemeStudioDialog(
                        initialBgArgb = settings.customBgColorArgb,
                        initialTextArgb = settings.customTextColorArgb,
                        initialNumberArgb = settings.customNumberColorArgb,
                        activeTheme = activeTheme,
                        onDismiss = { isCustomThemeStudioShowing = false },
                        onApplyColors = { bgArgb, textArgb, numArgb ->
                            viewModel.saveCustomThemeColors(bgArgb, textArgb, numArgb)
                            isCustomThemeStudioShowing = false
                        }
                    )
                }
            }

            if (isSettingsHubShowing) {
                ModalScrimBox(onDismiss = {
                    viewModel.setCircadianPreviewHour(null)
                    isSettingsHubShowing = false
                }) {
                    SettingsHubDialog(
                        currentLocalTime = currentLocalTime,
                        settings = settings,
                        friendClocks = friendClocks,
                        alarms = alarms,
                        activeTheme = activeTheme,
                        viewModel = viewModel,
                        initialTab = companionTabSelection,
                        onDismiss = {
                            viewModel.setCircadianPreviewHour(null)
                            isSettingsHubShowing = false
                        },
                        onAddAlarmClicked = {
                            isSettingsHubShowing = false
                            isAddingAlarmDialogShowing = true
                        },
                        onExportClicked = {
                            isSettingsHubShowing = false
                            isBackupExportDialogShowing = true
                        },
                        onImportClicked = {
                            isSettingsHubShowing = false
                            isBackupImportDialogShowing = true
                        },
                        onOpenCustomThemeStudio = {
                            isSettingsHubShowing = false
                            isCustomThemeStudioShowing = true
                        }
                    )
                }
            }

            pendingCrashLog?.let { crashText ->
                ModalScrimBox(onDismiss = { pendingCrashLog = null }) {
                    com.example.ui.dialogs.PostCrashRecoveryDialog(
                        crashLogText = crashText,
                        activeTheme = activeTheme,
                        onDismiss = { pendingCrashLog = null },
                        onClearAndDismiss = {
                            com.example.util.CrashReportManager.clearCrashReport(context)
                            pendingCrashLog = null
                        }
                    )
                }
            }

            if (isBackupExportDialogShowing) {
                ModalScrimBox(onDismiss = { isBackupExportDialogShowing = false }) {
                    ExportBackupDialog(
                        jsonString = viewModel.exportBackup(),
                        activeTheme = activeTheme,
                        onDismiss = { isBackupExportDialogShowing = false }
                    )
                }
            }

            if (isBackupImportDialogShowing) {
                ModalScrimBox(onDismiss = { isBackupImportDialogShowing = false }) {
                    ImportBackupDialog(
                        activeTheme = activeTheme,
                        onDismiss = { isBackupImportDialogShowing = false },
                        onImportConfirm = { backupJson ->
                            viewModel.importBackup(
                                backupJson,
                                onSuccess = {
                                    isBackupImportDialogShowing = false
                                    Toast.makeText(context, "Backup restored!", Toast.LENGTH_LONG).show()
                                },
                                onError = { errorMsg ->
                                    Toast.makeText(context, "Restore failed: $errorMsg", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    )
                }
            }

            if (isClockSyncDialogShowing) {
                ModalScrimBox(onDismiss = { isClockSyncDialogShowing = false }) {
                    com.example.ui.dialogs.ClockSyncInspectionDialog(
                        currentLocalTime = currentLocalTime,
                        activeTheme = activeTheme,
                        viewModel = viewModel,
                        onDismiss = { isClockSyncDialogShowing = false }
                    )
                }
            }

            if (isBreathingDialogShowing) {
                ModalScrimBox(onDismiss = { isBreathingDialogShowing = false }) {
                    com.example.ui.dialogs.BreathingGuideDialog(
                        settings = settings,
                        activeTheme = activeTheme,
                        viewModel = viewModel,
                        onDismiss = { isBreathingDialogShowing = false }
                    )
                }
            }

            activeEyeBreakPrompt?.let { eyePrompt ->
                ModalScrimBox(onDismiss = { viewModel.dismissEyeBreakPrompt() }) {
                    com.example.ui.dialogs.EyeBreakReminderDialog(
                        promptState = eyePrompt,
                        intervalMinutes = settings.eyeBreakIntervalMinutes,
                        activeTheme = activeTheme,
                        onAnotherExercise = { viewModel.nextEyeBreakExercise() },
                        onSnooze = { viewModel.dismissEyeBreakPrompt(snoozeMinutes = 5) },
                        onDismiss = { viewModel.dismissEyeBreakPrompt() }
                    )
                }
            }

            // Ultra-HD Peripheral Visual Edge Pulse on Alert (rendered above scrims so previews & alerts stay crisp)
            PerimeterPulseOverlay(
                isPulsing = isFlashing,
                accentColor = activeTheme.accentColor,
                pulseGeneration = pulseTriggerCount
            )
        }
    }
    }
}

@Composable
private fun ModalScrimBox(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .clickable(enabled = true, onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun CompactPhoneDashboard(
    containerWidth: Dp,
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    friendClocks: List<FriendClock>,
    alarms: List<AlarmItem>,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    companionTabSelection: Int,
    onCompanionTabSelected: (Int) -> Unit,
    onAddFriendClicked: () -> Unit,
    onAddAlarmClicked: () -> Unit,
    onOpenCustomThemeStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncInspector: () -> Unit,
    onOpenBreathing: () -> Unit,
    onExportClicked: () -> Unit,
    onImportClicked: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
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

        // Main Digital Clock with Integrated Snooze Bar + Adjacent Configurable World Clock Widget
        item {
            if (settings.showWorldClockBesideMain &&
                settings.worldClockWidgetLayout == "SIDE_BY_SIDE" &&
                containerWidth >= 460.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    HeroDigitalClockCard(
                        currentLocalTime = currentLocalTime,
                        settings = settings,
                        activeTheme = activeTheme,
                        viewModel = viewModel,
                        modifier = Modifier.weight(0.95f)
                    )
                    WorldClockWidgetPanel(
                        friendClocks = friendClocks,
                        currentLocalTime = currentLocalTime,
                        settings = settings,
                        activeTheme = activeTheme,
                        viewModel = viewModel,
                        onAddCityClicked = onAddFriendClicked,
                        columnsInGrid = 1,
                        modifier = Modifier.weight(1.05f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroDigitalClockCard(
                        currentLocalTime = currentLocalTime,
                        settings = settings,
                        activeTheme = activeTheme,
                        viewModel = viewModel
                    )
                    if (settings.showWorldClockBesideMain) {
                        WorldClockWidgetPanel(
                            friendClocks = friendClocks,
                            currentLocalTime = currentLocalTime,
                            settings = settings,
                            activeTheme = activeTheme,
                            viewModel = viewModel,
                            onAddCityClicked = onAddFriendClicked,
                            columnsInGrid = if (containerWidth >= 390.dp && settings.worldClockWidgetLayout == "GRID") 2 else 1
                        )
                    }
                }
            }
        }

        item {
            CompanionWorkspaceCard(
                currentLocalTime = currentLocalTime,
                settings = settings,
                friendClocks = friendClocks,
                alarms = alarms,
                activeTheme = activeTheme,
                viewModel = viewModel,
                selectedTab = companionTabSelection,
                onTabSelected = onCompanionTabSelected,
                onAddAlarmClicked = onAddAlarmClicked,
                onExportClicked = onExportClicked,
                onImportClicked = onImportClicked,
                onOpenCustomThemeStudio = onOpenCustomThemeStudio
            )
        }
    }
}

@Composable
private fun MobileLandscapeDockDashboard(
    containerWidth: Dp,
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    friendClocks: List<FriendClock>,
    alarms: List<AlarmItem>,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    companionTabSelection: Int,
    onCompanionTabSelected: (Int) -> Unit,
    onAddFriendClicked: () -> Unit,
    onAddAlarmClicked: () -> Unit,
    onOpenCustomThemeStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncInspector: () -> Unit,
    onOpenBreathing: () -> Unit,
    onExportClicked: () -> Unit,
    onImportClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Bedside Chronometer & Integrated Alert Sub-Deck
        Column(
            modifier = Modifier
                .weight(0.95f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
            HeroDigitalClockCard(
                currentLocalTime = currentLocalTime,
                settings = settings,
                activeTheme = activeTheme,
                viewModel = viewModel,
                compactVertical = true
            )
        }

        // Right World Clock Widget & Companion Tools
        Column(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (settings.showWorldClockBesideMain) {
                WorldClockWidgetPanel(
                    friendClocks = friendClocks,
                    currentLocalTime = currentLocalTime,
                    settings = settings,
                    activeTheme = activeTheme,
                    viewModel = viewModel,
                    onAddCityClicked = onAddFriendClicked,
                    columnsInGrid = if (containerWidth >= 720.dp) 2 else 1
                )
            }
            CompanionWorkspaceCard(
                currentLocalTime = currentLocalTime,
                settings = settings,
                friendClocks = friendClocks,
                alarms = alarms,
                activeTheme = activeTheme,
                viewModel = viewModel,
                selectedTab = companionTabSelection,
                onTabSelected = onCompanionTabSelected,
                onAddAlarmClicked = onAddAlarmClicked,
                onExportClicked = onExportClicked,
                onImportClicked = onImportClicked,
                onOpenCustomThemeStudio = onOpenCustomThemeStudio
            )
        }
    }
}

@Composable
private fun MediumTabletDashboard(
    containerWidth: Dp,
    containerHeight: Dp,
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    friendClocks: List<FriendClock>,
    alarms: List<AlarmItem>,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    onAddFriendClicked: () -> Unit,
    onOpenCustomThemeStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncInspector: () -> Unit,
    onOpenBreathing: () -> Unit
) {
    val aspectRatio = if (containerHeight.value > 0f) containerWidth.value / containerHeight.value else 1.6f
    val isLandscape = aspectRatio >= 1.15f || containerWidth >= containerHeight

    // Dynamic horizontal & vertical paddings derived from container dimensions
    val screenPaddingHorizontal = (containerWidth * 0.024f).coerceIn(14.dp, 30.dp)
    val screenPaddingVertical = (containerHeight * 0.020f).coerceIn(10.dp, 22.dp)

    // Dynamic card spacing: scales up with aspect ratio
    val cardHorizontalSpacing = when {
        aspectRatio >= 1.7f -> 24.dp
        aspectRatio >= 1.5f -> 20.dp
        else -> 16.dp
    }
    val cardVerticalSpacing = (containerHeight * 0.024f).coerceIn(12.dp, 24.dp)

    // Card internal padding
    val cardPaddingH = (containerWidth * 0.022f).coerceIn(16.dp, 30.dp)
    val cardPaddingV = (containerHeight * 0.024f).coerceIn(14.dp, 26.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = screenPaddingHorizontal, vertical = screenPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(cardVerticalSpacing)
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

        // Pure Clock Dashboard: Main Clock with integrated snooze + full-screen World Clock grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLandscape && settings.showWorldClockBesideMain) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(cardHorizontalSpacing),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.96f)
                            .fillMaxHeight()
                    ) {
                        HeroDigitalClockCard(
                            currentLocalTime = currentLocalTime,
                            settings = settings,
                            activeTheme = activeTheme,
                            viewModel = viewModel,
                            fillContainer = true,
                            dynamicPaddingHorizontal = cardPaddingH,
                            dynamicPaddingVertical = cardPaddingV
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.04f)
                            .fillMaxHeight()
                    ) {
                        WorldClockWidgetPanel(
                            friendClocks = friendClocks,
                            currentLocalTime = currentLocalTime,
                            settings = settings,
                            activeTheme = activeTheme,
                            viewModel = viewModel,
                            onAddCityClicked = onAddFriendClicked,
                            columnsInGrid = if (containerWidth >= 780.dp) 2 else 1,
                            fillContainer = true,
                            dynamicRowSpacing = cardVerticalSpacing,
                            dynamicColSpacing = cardHorizontalSpacing
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(cardVerticalSpacing)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(if (settings.showWorldClockBesideMain) 0.46f else 1f)
                            .fillMaxWidth()
                    ) {
                        HeroDigitalClockCard(
                            currentLocalTime = currentLocalTime,
                            settings = settings,
                            activeTheme = activeTheme,
                            viewModel = viewModel,
                            fillContainer = true,
                            dynamicPaddingHorizontal = cardPaddingH,
                            dynamicPaddingVertical = cardPaddingV
                        )
                    }

                    if (settings.showWorldClockBesideMain) {
                        Box(
                            modifier = Modifier
                                .weight(0.54f)
                                .fillMaxWidth()
                        ) {
                            WorldClockWidgetPanel(
                                friendClocks = friendClocks,
                                currentLocalTime = currentLocalTime,
                                settings = settings,
                                activeTheme = activeTheme,
                                viewModel = viewModel,
                                onAddCityClicked = onAddFriendClicked,
                                columnsInGrid = if (containerWidth >= 600.dp) 2 else 1,
                                fillContainer = true,
                                dynamicRowSpacing = cardVerticalSpacing,
                                dynamicColSpacing = cardHorizontalSpacing
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedWidescreenDashboard(
    containerWidth: Dp,
    containerHeight: Dp,
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    friendClocks: List<FriendClock>,
    alarms: List<AlarmItem>,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    onAddFriendClicked: () -> Unit,
    onOpenCustomThemeStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncInspector: () -> Unit,
    onOpenBreathing: () -> Unit
) {
    val aspectRatio = if (containerHeight.value > 0f) containerWidth.value / containerHeight.value else 1.6f
    val isLandscape = aspectRatio >= 1.15f || containerWidth >= containerHeight

    // Dynamic horizontal & vertical paddings derived from container dimensions
    val screenPaddingHorizontal = (containerWidth * 0.026f).coerceIn(16.dp, 32.dp)
    val screenPaddingVertical = (containerHeight * 0.022f).coerceIn(12.dp, 24.dp)

    // Dynamic card spacing: scales up with aspect ratio
    val cardHorizontalSpacing = when {
        aspectRatio >= 1.7f -> 24.dp
        aspectRatio >= 1.5f -> 20.dp
        else -> 16.dp
    }
    val cardVerticalSpacing = (containerHeight * 0.024f).coerceIn(12.dp, 24.dp)

    // Card internal padding
    val cardPaddingH = (containerWidth * 0.024f).coerceIn(18.dp, 32.dp)
    val cardPaddingV = (containerHeight * 0.026f).coerceIn(16.dp, 30.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = screenPaddingHorizontal, vertical = screenPaddingVertical),
        verticalArrangement = Arrangement.spacedBy(cardVerticalSpacing)
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

        // Balanced Widescreen Clocks: Left Main Clock + Integrated Snooze; Right World Clock Grid filling 100% of available height
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLandscape && settings.showWorldClockBesideMain) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(cardHorizontalSpacing)
                ) {
                    // Main Digital Clock & Integrated Snooze Section (Fills left height with grand numerals)
                    Box(
                        modifier = Modifier
                            .weight(if (containerWidth >= 1100.dp) 0.94f else 0.96f)
                            .fillMaxHeight()
                    ) {
                        HeroDigitalClockCard(
                            currentLocalTime = currentLocalTime,
                            settings = settings,
                            activeTheme = activeTheme,
                            viewModel = viewModel,
                            fillContainer = true,
                            dynamicPaddingHorizontal = cardPaddingH,
                            dynamicPaddingVertical = cardPaddingV
                        )
                    }

                    // World Clock Grid Section (Fills right height with adaptive city grid, zero empty bottom gaps)
                    Box(
                        modifier = Modifier
                            .weight(if (containerWidth >= 1100.dp) 1.06f else 1.04f)
                            .fillMaxHeight()
                    ) {
                        WorldClockWidgetPanel(
                            friendClocks = friendClocks,
                            currentLocalTime = currentLocalTime,
                            settings = settings,
                            activeTheme = activeTheme,
                            viewModel = viewModel,
                            onAddCityClicked = onAddFriendClicked,
                            columnsInGrid = if (containerWidth >= 960.dp) 2 else 1,
                            fillContainer = true,
                            dynamicRowSpacing = cardVerticalSpacing,
                            dynamicColSpacing = cardHorizontalSpacing
                        )
                    }
                }
            } else if (!isLandscape && settings.showWorldClockBesideMain) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(cardVerticalSpacing)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.46f)
                            .fillMaxWidth()
                    ) {
                        HeroDigitalClockCard(
                            currentLocalTime = currentLocalTime,
                            settings = settings,
                            activeTheme = activeTheme,
                            viewModel = viewModel,
                            fillContainer = true,
                            dynamicPaddingHorizontal = cardPaddingH,
                            dynamicPaddingVertical = cardPaddingV
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(0.54f)
                            .fillMaxWidth()
                    ) {
                        WorldClockWidgetPanel(
                            friendClocks = friendClocks,
                            currentLocalTime = currentLocalTime,
                            settings = settings,
                            activeTheme = activeTheme,
                            viewModel = viewModel,
                            onAddCityClicked = onAddFriendClicked,
                            columnsInGrid = if (containerWidth >= 750.dp) 2 else 1,
                            fillContainer = true,
                            dynamicRowSpacing = cardVerticalSpacing,
                            dynamicColSpacing = cardHorizontalSpacing
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HeroDigitalClockCard(
                        currentLocalTime = currentLocalTime,
                        settings = settings,
                        activeTheme = activeTheme,
                        viewModel = viewModel,
                        fillContainer = true,
                        dynamicPaddingHorizontal = cardPaddingH,
                        dynamicPaddingVertical = cardPaddingV
                    )
                }
            }
        }
    }
}
