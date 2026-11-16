package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
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
import androidx.core.content.ContextCompat
import com.example.data.ClockSettings
import com.example.ui.ClockViewModel
import com.example.ui.theme.ClockVisualTheme

/** Best recent coarse position the device already knows (no new GPS fix, no network request), or null. */
private fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        != PackageManager.PERMISSION_GRANTED
    ) return null
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val best = manager.getProviders(true)
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    return best?.let { it.latitude to it.longitude }
}

/** Keep-awake mode, start-on-boot and the saved location used for sunset dimming. Identical for every layout. */
@Composable
fun AlwaysOnSettingsSection(
    settings: ClockSettings,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel
) {
    val context = LocalContext.current

    fun saveLocationOrExplain() {
        val spot = lastKnownLocation(context)
        if (spot != null) {
            viewModel.updateHomeLocation(spot.first, spot.second)
            Toast.makeText(context, "Location saved for sunrise and sunset", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "No recent location on this device yet. Open a maps app once, then try again.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveLocationOrExplain()
    }

    @Composable
    fun Chip(label: String, subtitle: String, selected: Boolean, tag: String, modifier: Modifier, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = activeTheme.cardBackground,
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) activeTheme.accentColor else activeTheme.dividerColor
            ),
            modifier = modifier.defaultMinSize(minHeight = 52.dp).testTag(tag)
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

    @Composable
    fun Heading(text: String) = Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = activeTheme.textSecondary
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Heading("SCREEN")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeepAwake.options.forEach { (id, label, subtitle) ->
                Chip(label, subtitle, settings.keepAwakeMode == id, "keep_awake_${id.lowercase()}", Modifier.weight(1f)) {
                    viewModel.updateKeepAwakeMode(id)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Heading("HEADS-UP BEFORE A FOCUS BLOCK OR ALARM ENDS")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("On", "Quiet note at 10 and 5 minutes", settings.transitionWarningsEnabled, "warnings_on", Modifier.weight(1f)) {
                viewModel.updateTransitionWarnings(true)
            }
            Chip("Off", "No heads-up", !settings.transitionWarningsEnabled, "warnings_off", Modifier.weight(1f)) {
                viewModel.updateTransitionWarnings(false)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Heading("START ON BOOT")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Off", "Open Meridian myself", !settings.autoStartOnBoot, "boot_off", Modifier.weight(1f)) {
                viewModel.updateAutoStartOnBoot(false)
            }
            Chip(
                "On",
                if (Settings.canDrawOverlays(context)) "Reopens after a restart" else "Needs 'display over other apps'",
                settings.autoStartOnBoot,
                "boot_on",
                Modifier.weight(1f)
            ) {
                viewModel.updateAutoStartOnBoot(true)
                if (!Settings.canDrawOverlays(context)) {
                    // Android only allows reopening from the background with this permission; the user grants it there.
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Heading("SUNRISE & SUNSET (FOR 'SUNSET' DIMMING)")
        Chip(
            label = if (settings.hasHomeLocation) "Location saved" else "Use my location",
            subtitle = if (settings.hasHomeLocation) "Tap to refresh. Worked out on this device only."
            else "Used only to calculate sunset on this device",
            selected = settings.hasHomeLocation,
            tag = "use_location",
            modifier = Modifier.fillMaxWidth()
        ) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) saveLocationOrExplain()
            else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}
