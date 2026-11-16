package com.example.ui.components

import androidx.compose.runtime.compositionLocalOf
import com.example.data.CityWeather
import com.example.data.FriendClock
import com.example.data.WeatherNotice
import com.example.data.WeatherUnits
import com.example.data.WeatherWarnings
import com.example.util.TimeAndAlertUtils
import java.time.ZoneId

/** The weather the screen may show right now, plus the unit to show it in. Empty when weather is off. */
data class WeatherUi(val byCity: Map<Int, CityWeather> = emptyMap(), val unit: String = WeatherUnits.CELSIUS) {
    /** "☁️ 3°" for a city, or null if there is no reading or it is too old to trust. */
    fun summaryFor(cityId: Int, nowMillis: Long): String? {
        val w = byCity[cityId] ?: return null
        return if (WeatherWarnings.isFresh(w, nowMillis)) WeatherUnits.summary(w, unit) else null
    }
}

/** Provided once by MainActivity so every layout can show weather without extra plumbing. */
val LocalWeather = compositionLocalOf { WeatherUi() }

/** A city's title with its weather in front, for the older Classic cards that do not use the shared city rows. */
@androidx.compose.runtime.Composable
fun cityTitleWithWeather(friend: FriendClock): String {
    val name = friend.cityLabel.ifBlank { friend.friendName }
    val summary = LocalWeather.current.summaryFor(friend.id, System.currentTimeMillis())
    return if (summary == null) name else "$summary · $name"
}

/**
 * Chips for the dock: official alerts first, then forecast warnings. Rain-soon is only raised for the home city
 * (the one in this device's timezone); other cities only appear for serious conditions such as heat or storms.
 */
fun buildWeatherNotices(
    friends: List<FriendClock>,
    weather: WeatherUi,
    official: List<WeatherNotice>,
    nowMillis: Long,
    deviceZone: ZoneId,
    max: Int = 2
): List<WeatherNotice> {
    val forecast = friends.flatMap { f ->
        val w = weather.byCity[f.id]?.takeIf { WeatherWarnings.isFresh(it, nowMillis) } ?: return@flatMap emptyList()
        val isHome = TimeAndAlertUtils.safeZoneId(f.timezoneId).id == deviceZone.id
        WeatherWarnings.forCity(f.cityLabel.ifBlank { f.friendName }, w, weather.unit, includeMinor = isHome)
    }
    return (official + forecast).distinctBy { it.city to it.text }.sortedByDescending { it.level }.take(max)
}
