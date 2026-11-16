package com.example.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Everything the app knows about one city's weather. Temperatures are always stored in Celsius. */
data class CityWeather(
    val tempC: Double,
    val code: Int, // WMO weather code
    val isDay: Boolean,
    /** Minutes until rain starts within the next 3 hours: 0 = this hour, null = none expected. */
    val rainInMinutes: Int?,
    val maxC24: Double,
    val minC24: Double,
    val fetchedMillis: Long
)

/** WMO weather codes as returned by Open-Meteo, turned into an icon and a short label. */
object WeatherCodes {
    data class Info(val icon: String, val label: String, val severe: Boolean, val wet: Boolean)

    fun info(code: Int, isDay: Boolean): Info = when (code) {
        0 -> Info(if (isDay) "☀️" else "🌙", "Clear", severe = false, wet = false)
        1 -> Info(if (isDay) "🌤️" else "🌙", "Mostly clear", false, false)
        2 -> Info("⛅", "Partly cloudy", false, false)
        3 -> Info("☁️", "Overcast", false, false)
        45, 48 -> Info("🌫️", "Fog", false, false)
        51, 53, 55 -> Info("🌦️", "Drizzle", false, true)
        56, 57 -> Info("🌧️", "Freezing drizzle", true, true)
        61, 63, 65 -> Info("🌧️", "Rain", false, true)
        66, 67 -> Info("🌧️", "Freezing rain", true, true)
        71, 73 -> Info("❄️", "Snow", false, true)
        75 -> Info("❄️", "Heavy snow", true, true)
        77 -> Info("❄️", "Snow grains", false, true)
        80, 81 -> Info("🌦️", "Showers", false, true)
        82 -> Info("🌧️", "Violent showers", true, true)
        85 -> Info("❄️", "Snow showers", false, true)
        86 -> Info("❄️", "Heavy snow showers", true, true)
        95 -> Info("⛈️", "Thunderstorm", true, true)
        96, 99 -> Info("⛈️", "Thunderstorm with hail", true, true)
        else -> Info("☁️", "Unknown", false, false)
    }
}

object WeatherUnits {
    const val CELSIUS = "C"
    const val FAHRENHEIT = "F"

    /** "13°" or "55°": rounded, with no unit letter to keep city rows short. */
    fun temp(celsius: Double, unit: String): String {
        val value = if (unit == FAHRENHEIT) celsius * 9.0 / 5.0 + 32.0 else celsius
        val rounded = value.roundToInt()
        return "${if (rounded == 0) 0 else rounded}°"
    }

    /** Short row text such as "☁️ 3°". */
    fun summary(w: CityWeather, unit: String): String = "${WeatherCodes.info(w.code, w.isDay).icon} ${temp(w.tempC, unit)}"
}

/** A line shown in the dock: a forecast warning or an official alert. [level] 1 = heads-up, 2 = warning, 3 = severe. */
data class WeatherNotice(val city: String, val text: String, val level: Int)

/** Warnings worked out from the forecast, so they work for any city in the world. */
object WeatherWarnings {
    const val HEAT_C = 32.0
    const val EXTREME_COLD_C = -15.0
    const val MAX_AGE_MILLIS = 3 * 3_600_000L // older data is hidden rather than shown as if current

    fun isFresh(w: CityWeather, nowMillis: Long): Boolean = nowMillis - w.fetchedMillis <= MAX_AGE_MILLIS

    fun forCity(label: String, w: CityWeather, unit: String, includeMinor: Boolean): List<WeatherNotice> {
        val city = label.substringBefore(',').trim()
        val info = WeatherCodes.info(w.code, w.isDay)
        val out = mutableListOf<WeatherNotice>()
        if (info.severe) out += WeatherNotice(city, info.label, 2)
        if (w.maxC24 >= HEAT_C) out += WeatherNotice(city, "Heat up to ${WeatherUnits.temp(w.maxC24, unit)}", 2)
        if (w.minC24 <= EXTREME_COLD_C) out += WeatherNotice(city, "Extreme cold ${WeatherUnits.temp(w.minC24, unit)}", 2)
        val rain = w.rainInMinutes
        if (includeMinor && !info.wet && rain != null && rain in 1..180) {
            val rounded = ((rain + 4) / 5) * 5 // round up to 5 minutes: precise-sounding minutes would be false precision
            out += WeatherNotice(city, "Rain in $rounded min", 1)
        }
        return out
    }
}

/** An alert issued by an official service (US National Weather Service or Environment Canada). */
data class OfficialAlert(val title: String, val level: Int, val expiresMillis: Long?)

object AlertParsers {
    /** api.weather.gov/alerts/active?point=lat,lon */
    fun parseNws(json: String, nowMillis: Long): List<OfficialAlert> = runCatching {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        (0 until features.length()).mapNotNull { i ->
            val p = features.getJSONObject(i).optJSONObject("properties") ?: return@mapNotNull null
            if (p.optString("status") != "Actual" || p.optString("messageType") == "Cancel") return@mapNotNull null
            val expires = (p.optString("ends").takeIf { it.isNotBlank() && it != "null" } ?: p.optString("expires"))
                .takeIf { it.isNotBlank() && it != "null" }
                ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            if (expires != null && expires <= nowMillis) return@mapNotNull null
            val level = when (p.optString("severity")) {
                "Extreme" -> 3
                "Severe" -> 2
                else -> 1
            }
            OfficialAlert(p.optString("event").ifBlank { return@mapNotNull null }, level, expires)
        }
    }.getOrDefault(emptyList())

    /** api.weather.gc.ca/collections/weather-alerts/items?bbox=... */
    fun parseEnvironmentCanada(json: String, nowMillis: Long): List<OfficialAlert> = runCatching {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        (0 until features.length()).mapNotNull { i ->
            val p = features.getJSONObject(i).optJSONObject("properties") ?: return@mapNotNull null
            val type = p.optString("alert_type")
            if (type != "advisory" && type != "watch" && type != "warning") return@mapNotNull null
            val expires = p.optString("expiration_datetime").takeIf { it.isNotBlank() && it != "null" }
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            if (expires != null && expires <= nowMillis) return@mapNotNull null
            val level = when {
                p.optString("risk_colour_en") == "red" -> 3
                type == "warning" || p.optString("risk_colour_en") == "orange" -> 2
                else -> 1
            }
            val title = p.optString("alert_short_name_en").ifBlank { p.optString("alert_name_en") }
            if (title.isBlank()) null else OfficialAlert(title, level, expires)
        }.distinctBy { it.title } // one alert is often repeated once per affected area
    }.getOrDefault(emptyList())
}

/** Reads Open-Meteo forecast JSON. */
object OpenMeteoParser {
    /**
     * [json] is an array when several places were requested and a single object for one place, so both are handled.
     * Result order matches the request; a place that could not be read is null.
     */
    fun parse(json: String, nowMillis: Long): List<CityWeather?> {
        val trimmed = json.trim()
        val objects = if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            (0 until array.length()).map { array.getJSONObject(it) }
        } else listOf(JSONObject(trimmed))
        return objects.map { runCatching { parseOne(it, nowMillis) }.getOrNull() }
    }

    private fun parseOne(o: JSONObject, nowMillis: Long): CityWeather {
        val current = o.getJSONObject("current")
        val hourly = o.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val precip = hourly.optJSONArray("precipitation")
        val prob = hourly.optJSONArray("precipitation_probability")
        val temps = hourly.getJSONArray("temperature_2m")

        // Times are in the place's own timezone (timezone=auto), so compare them with each other, never with ours.
        val nowLocal = LocalDateTime.parse(current.getString("time"))
        val nowHour = nowLocal.truncatedTo(ChronoUnit.HOURS)
        val idx = (0 until times.length()).firstOrNull { LocalDateTime.parse(times.getString(it)) == nowHour } ?: 0

        var rainIn: Int? = null
        for (i in idx..minOf(idx + 3, times.length() - 1)) {
            val mm = precip?.optDouble(i, 0.0) ?: 0.0
            val chance = prob?.optDouble(i, 100.0) ?: 100.0 // unknown chance: trust the amount alone
            if (mm >= 0.2 && chance >= 40.0) {
                rainIn = if (i == idx) 0 else Duration.between(nowLocal, LocalDateTime.parse(times.getString(i))).toMinutes().toInt().coerceAtLeast(1)
                break
            }
        }

        val end = minOf(idx + 24, temps.length())
        val window = (idx until end).map { temps.getDouble(it) }
        return CityWeather(
            tempC = current.getDouble("temperature_2m"),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            rainInMinutes = rainIn,
            maxC24 = window.maxOrNull() ?: current.getDouble("temperature_2m"),
            minC24 = window.minOrNull() ?: current.getDouble("temperature_2m"),
            fetchedMillis = nowMillis
        )
    }
}

/** A city's coordinates, found once by name and then remembered. */
data class GeoPoint(val lat: Double, val lon: Double, val countryCode: String)

/** What is kept on the device between runs so weather shows instantly and survives being offline. */
data class WeatherCacheData(
    val geo: Map<String, GeoPoint> = emptyMap(),
    val weather: Map<Int, CityWeather> = emptyMap()
)

object WeatherCache {
    fun toJson(c: WeatherCacheData): String = JSONObject().apply {
        put("geo", JSONObject().also { g ->
            c.geo.forEach { (k, v) -> g.put(k, JSONObject().put("lat", v.lat).put("lon", v.lon).put("cc", v.countryCode)) }
        })
        put("weather", JSONObject().also { w ->
            c.weather.forEach { (id, v) ->
                w.put(
                    id.toString(),
                    JSONObject().put("t", v.tempC).put("c", v.code).put("d", v.isDay).put("r", v.rainInMinutes ?: -1)
                        .put("hi", v.maxC24).put("lo", v.minC24).put("at", v.fetchedMillis)
                )
            }
        })
    }.toString()

    fun fromJson(json: String?): WeatherCacheData = runCatching {
        val o = JSONObject(json ?: return WeatherCacheData())
        val geo = o.optJSONObject("geo")?.let { g ->
            g.keys().asSequence().associateWith { k ->
                val v = g.getJSONObject(k)
                GeoPoint(v.getDouble("lat"), v.getDouble("lon"), v.optString("cc"))
            }
        } ?: emptyMap()
        val weather = o.optJSONObject("weather")?.let { w ->
            w.keys().asSequence().associate { k ->
                val v = w.getJSONObject(k)
                k.toInt() to CityWeather(
                    v.getDouble("t"), v.getInt("c"), v.getBoolean("d"),
                    v.getInt("r").takeIf { it >= 0 }, v.getDouble("hi"), v.getDouble("lo"), v.getLong("at")
                )
            }
        } ?: emptyMap()
        WeatherCacheData(geo, weather)
    }.getOrDefault(WeatherCacheData()) // a damaged cache just means "fetch again"
}
