package com.example.ui.components

import androidx.compose.runtime.Composable
import com.example.data.ClockSettings
import com.example.data.FriendClock
import com.example.ui.ClockViewModel
import com.example.ui.theme.ClockVisualTheme
import java.time.ZonedDateTime

/** True for the layout styles that are drawn through the shared [LsFaceFrame] (everything except Bold/Calm/Dashboard/Classic). */
fun isWatchFace(style: String): Boolean = style in LayoutStyle.faceIds

/**
 * Entry point used by MainActivity: wraps the chosen face in the shared frame so every face has the same
 * controls and features. To add a face: add an id to [LayoutStyle], a case here, and a composable.
 */
@Composable
fun WatchFaceLayout(
    style: String,
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    friendClocks: List<FriendClock>,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    onAddFriendClicked: () -> Unit,
    onOpenCustomThemeStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncInspector: () -> Unit,
    onOpenBreathing: () -> Unit
) {
    LsFaceFrame(
        currentLocalTime, settings, friendClocks, activeTheme, viewModel,
        onAddFriendClicked, onOpenCustomThemeStudio, onOpenSettings, onOpenSyncInspector, onOpenBreathing
    ) { m, d ->
        when (style) {
            LayoutStyle.ANALOG -> AnalogFace(m, d)
            LayoutStyle.FLIP -> FlipFace(m, d)
            LayoutStyle.ORBIT -> OrbitFace(m, d)
            LayoutStyle.LCD -> LcdFace(m, d)
            LayoutStyle.ARC -> ArcFace(m, d)
            LayoutStyle.WORDS -> WordsFace(m, d)
            LayoutStyle.TIMELINE -> TimelineFace(m, d)
            else -> AnalogFace(m, d)
        }
    }
}
