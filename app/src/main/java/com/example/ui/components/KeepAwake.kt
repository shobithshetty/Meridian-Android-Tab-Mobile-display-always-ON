package com.example.ui.components

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Stable names stored in [com.example.data.ClockSettings.keepAwakeMode]. */
object KeepAwake {
    const val ALWAYS = "ALWAYS"
    const val CHARGING = "CHARGING"

    val options: List<Triple<String, String, String>> = listOf(
        Triple(ALWAYS, "Always on", "Screen never times out"),
        Triple(CHARGING, "Only charging", "Screen sleeps on battery")
    )

    /** Whether the screen should be held on, given the chosen [mode] and whether the device is [charging]. */
    fun shouldHold(mode: String, charging: Boolean): Boolean = mode != CHARGING || charging
}

private fun Context.isCharging(): Boolean {
    val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    return status != 0
}

/** Keeps the window awake according to [mode], reacting live to the charger being plugged in or removed. */
@Composable
fun KeepScreenOnEffect(mode: String) {
    val context = LocalContext.current
    var charging by remember { mutableStateOf(context.isCharging()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { charging = c.isCharging() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    DisposableEffect(mode, charging) {
        val window = (context as? Activity)?.window
        if (KeepAwake.shouldHold(mode, charging)) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { }
    }
}
