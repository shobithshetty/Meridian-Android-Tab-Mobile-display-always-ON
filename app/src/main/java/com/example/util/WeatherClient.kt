package com.example.util

import com.example.data.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * The only code in the app that uses the internet, and it only runs when the user turns weather on.
 * What is sent: a city name (to look up coordinates) and city coordinates (for the forecast and alerts).
 * Never sent: the device's own location, the user's name, the account, or any device identifier.
 */
object WeatherClient {
    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODE = "https://geocoding-api.open-meteo.com/v1/search"
    private const val NWS = "https://api.weather.gov/alerts/active"
    private const val EC = "https://api.weather.gc.ca/collections/weather-alerts/items"
    private const val MAX_BYTES = 2_000_000 // bigger than any real reply; stops a runaway response from filling memory

    /** Countries with a free official alert feed we can read. */
    fun hasOfficialAlerts(countryCode: String): Boolean = countryCode == "US" || countryCode == "CA"

    fun forecastUrl(points: List<GeoPoint>): String {
        val lat = points.joinToString(",") { "%.3f".format(Locale.US, it.lat) }
        val lon = points.joinToString(",") { "%.3f".format(Locale.US, it.lon) }
        return "$FORECAST?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day" +
            "&hourly=precipitation_probability,precipitation,temperature_2m" +
            "&forecast_days=2&timezone=auto&temperature_unit=celsius"
    }

    fun geocodeUrl(name: String): String =
        "$GEOCODE?name=${URLEncoder.encode(name, "UTF-8")}&count=10&language=en&format=json"

    fun nwsUrl(p: GeoPoint): String = "$NWS?point=${"%.3f".format(Locale.US, p.lat)},${"%.3f".format(Locale.US, p.lon)}"

    /** A tiny box around the point: Environment Canada's API filters alerts by bounding box. */
    fun ecUrl(p: GeoPoint): String {
        val d = 0.02
        val box = listOf(p.lon - d, p.lat - d, p.lon + d, p.lat + d).joinToString(",") { "%.3f".format(Locale.US, it) }
        return "$EC?f=json&limit=20&bbox=$box"
    }

    suspend fun forecast(points: List<GeoPoint>): String = get(forecastUrl(points))

    suspend fun officialAlerts(p: GeoPoint): String? = when (p.countryCode) {
        "US" -> get(nwsUrl(p), mapOf("Accept" to "application/geo+json"))
        "CA" -> get(ecUrl(p))
        else -> null
    }

    /**
     * Finds [name] and prefers the match that is in [timezoneId], so a saved "Berlin" in Europe/Berlin is never
     * confused with a Berlin elsewhere. Returns null when nothing matches.
     */
    suspend fun geocode(name: String, timezoneId: String): GeoPoint? = pickGeo(get(geocodeUrl(name)), timezoneId)

    /** Chooses the right place from a geocoding reply; kept separate from the network call so it can be tested. */
    fun pickGeo(json: String, timezoneId: String): GeoPoint? = runCatching {
        val results = JSONObject(json).optJSONArray("results") ?: return null
        val hits = (0 until results.length()).map { results.getJSONObject(it) }
        val best = hits.firstOrNull { it.optString("timezone") == timezoneId } ?: hits.firstOrNull() ?: return null
        GeoPoint(best.getDouble("latitude"), best.getDouble("longitude"), best.optString("country_code"))
    }.getOrNull()

    private suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "MeridianClock") // generic app name only
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) throw IOException("Reply too large")
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            }
        } finally {
            connection.disconnect()
        }
    }
}
