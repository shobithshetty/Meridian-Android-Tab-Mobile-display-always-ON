package com.example

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ClockRepository
import com.example.data.ClockSettings
import com.example.data.FriendClock
import com.example.ui.ClockViewModel
import com.example.ui.components.BoldClockLayout
import com.example.ui.components.CalmClockLayout
import com.example.ui.components.DashboardClockLayout
import com.example.ui.components.LayoutStyle
import com.example.ui.components.WatchFaceLayout
import com.example.ui.theme.AllPresetThemes
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every layout style on a real screen configuration (phone and tablet, portrait and landscape).
 * Each style must compose without crashing; images are written to build/layout-matrix when the suite is run with
 * -Proborazzi.test.record=true, so they can be reviewed side by side.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class LayoutMatrixBase(private val sizeName: String) {

    @get:Rule val composeTestRule = createComposeRule()

    private val styles = listOf(
        LayoutStyle.BOLD, LayoutStyle.CALM, LayoutStyle.DASHBOARD,
        LayoutStyle.ANALOG, LayoutStyle.FLIP, LayoutStyle.ORBIT, LayoutStyle.LCD,
        LayoutStyle.ARC, LayoutStyle.WORDS, LayoutStyle.TIMELINE
    )

    private val cities = listOf(
        FriendClock(1, "Toronto", "America/Toronto", "Toronto, Canada"),
        FriendClock(2, "Bangalore", "Asia/Kolkata", "Bangalore, India"),
        FriendClock(3, "Berlin", "Europe/Berlin", "Berlin, Germany"),
        FriendClock(4, "Doha", "Asia/Qatar", "Doha, Qatar"),
        FriendClock(5, "London", "Europe/London", "London, UK"),
        FriendClock(6, "New York", "America/New_York", "New York, USA"),
        FriendClock(7, "Tokyo", "Asia/Tokyo", "Tokyo, Japan")
    )

    @Test
    fun everyStyleComposes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val vm = ClockViewModel(ClockRepository(db.clockDao()), context)
        val now = ZonedDateTime.of(2026, 11, 15, 22, 47, 20, 0, ZoneId.of("America/Toronto"))
        val settings = ClockSettings()
        val theme = AllPresetThemes.first().theme

        // One setContent per test: the style is state, changed between captures.
        var current by mutableStateOf(styles.first())
        composeTestRule.setContent {
            MyApplicationTheme {
                val s = settings.copy(layoutStyle = current)
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    when (current) {
                        LayoutStyle.BOLD -> BoldClockLayout(now, s, cities, theme, vm, {}, {}, {}, {}, {})
                        LayoutStyle.CALM -> CalmClockLayout(now, s, cities, theme, vm, {}, {}, {}, {}, {})
                        LayoutStyle.DASHBOARD -> DashboardClockLayout(now, s, cities, theme, vm, {}, {}, {}, {}, {})
                        else -> WatchFaceLayout(current, now, s, cities, theme, vm, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        for (style in styles) {
            current = style
            composeTestRule.waitForIdle()
            composeTestRule.onRoot().captureRoboImage(filePath = "build/layout-matrix/$sizeName-$style.png")
        }
        db.close()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h780dp-port-xxhdpi", sdk = [34])
class LayoutMatrixPhonePortrait : LayoutMatrixBase("phone-portrait")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w780dp-h360dp-land-xxhdpi", sdk = [34])
class LayoutMatrixPhoneLandscape : LayoutMatrixBase("phone-landscape")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w600dp-h1000dp-port-xhdpi", sdk = [34])
class LayoutMatrixTabletPortrait : LayoutMatrixBase("tablet-portrait")

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1000dp-h600dp-land-xhdpi", sdk = [34])
class LayoutMatrixTabletLandscape : LayoutMatrixBase("tablet-landscape")
