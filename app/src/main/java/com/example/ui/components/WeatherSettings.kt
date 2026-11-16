package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClockSettings
import com.example.data.WeatherUnits
import com.example.ui.ClockViewModel
import com.example.ui.theme.ClockVisualTheme

/** Weather is the one feature that uses the internet, so it is off until switched on here. Same for every layout. */
@Composable
fun WeatherSettingsSection(settings: ClockSettings, theme: ClockVisualTheme, viewModel: ClockViewModel) {
    val readings by viewModel.weather.collectAsState()
    val alerts by viewModel.officialAlerts.collectAsState()

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
            Text(subtitle, fontSize = 9.sp, color = theme.textSecondary, maxLines = 3)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Heading("WEATHER (USES THE INTERNET)")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Off", "Meridian stays fully offline", !settings.weatherEnabled, "weather_off", Modifier.weight(1f)) {
                viewModel.updateWeatherEnabled(false)
            }
            Chip("On", "Temperature on each city, plus rain, heat and storm warnings", settings.weatherEnabled, "weather_on", Modifier.weight(1f)) {
                viewModel.updateWeatherEnabled(true)
            }
        }
        Text(
            "When on, Meridian sends only your saved cities' names and coordinates to Open-Meteo (open-meteo.com), " +
                "and for US and Canadian cities their coordinates to the US National Weather Service or Environment Canada " +
                "for official alerts. It never sends your own location, name or any device ID.",
            fontSize = 10.sp, color = theme.textSecondary
        )

        if (settings.weatherEnabled) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("°C", "Celsius", settings.weatherUnit == WeatherUnits.CELSIUS, "weather_unit_c", Modifier.weight(1f)) {
                    viewModel.updateWeatherUnit(WeatherUnits.CELSIUS)
                }
                Chip("°F", "Fahrenheit", settings.weatherUnit == WeatherUnits.FAHRENHEIT, "weather_unit_f", Modifier.weight(1f)) {
                    viewModel.updateWeatherUnit(WeatherUnits.FAHRENHEIT)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(
                    "Official alerts on", "US and Canada only", settings.weatherAlertsEnabled, "weather_alerts_on", Modifier.weight(1f)
                ) { viewModel.updateWeatherAlerts(true) }
                Chip(
                    "Official alerts off", "Forecast warnings only", !settings.weatherAlertsEnabled, "weather_alerts_off", Modifier.weight(1f)
                ) { viewModel.updateWeatherAlerts(false) }
            }
            Chip(
                label = "Refresh now",
                subtitle = "Updates every 30 minutes by itself. Showing ${readings.size} cities" +
                    if (settings.weatherAlertsEnabled) ", ${alerts.size} official alert" + (if (alerts.size == 1) "" else "s") else "",
                selected = false, tag = "weather_refresh", modifier = Modifier.fillMaxWidth()
            ) { viewModel.refreshWeatherNow() }
        }
    }
}
