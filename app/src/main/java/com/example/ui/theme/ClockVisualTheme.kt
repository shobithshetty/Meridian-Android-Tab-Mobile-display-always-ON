package com.example.ui.theme

import android.content.Context
import android.graphics.Color as AndroidColor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.example.data.ClockSettings
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos

data class ClockVisualTheme(
    val bgStart: Color,
    val bgEnd: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val numberColor: Color,
    val accentColor: Color,
    val onAccentColor: Color,
    val cardBackground: Color,
    val dividerColor: Color,
    val circadianCueColor: Color = accentColor
)

data class ThemePresetOption(
    val name: String,
    val subtitle: String,
    val theme: ClockVisualTheme
)

val CosmosMidnightTheme = ClockVisualTheme(
    bgStart = Color(0xFF000000),
    bgEnd = Color(0xFF03050B),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFC4CEE0),
    numberColor = Color(0xFFEDE9FE),
    accentColor = Color(0xFFC4B5FD),
    onAccentColor = Color(0xFF07060E),
    cardBackground = Color(0xFF0B0E18),
    dividerColor = Color(0xFF3A4668),
    circadianCueColor = Color(0xFFC4B5FD)
)

val DawnForestTheme = ClockVisualTheme(
    bgStart = Color(0xFF000000),
    bgEnd = Color(0xFF020905),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFC4DEC8),
    numberColor = Color(0xFFA7F3D0),
    accentColor = Color(0xFF6EE7B7),
    onAccentColor = Color(0xFF031009),
    cardBackground = Color(0xFF08140D),
    dividerColor = Color(0xFF2D543C),
    circadianCueColor = Color(0xFF6EE7B7)
)

val NordicWhiteTheme = ClockVisualTheme(
    bgStart = Color(0xFFF8FAFC),
    bgEnd = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    numberColor = Color(0xFF0F3D91),
    accentColor = Color(0xFF1D4ED8),
    onAccentColor = Color(0xFFFFFFFF),
    cardBackground = Color(0xFFFFFFFF),
    dividerColor = Color(0xFFCBD5E1),
    circadianCueColor = Color(0xFF1D4ED8)
)

val SunsetWarmTheme = ClockVisualTheme(
    bgStart = Color(0xFF000000),
    bgEnd = Color(0xFF0A0306),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFF0C7D6),
    numberColor = Color(0xFFFFD3B0),
    accentColor = Color(0xFFFB923C),
    onAccentColor = Color(0xFF14060A),
    cardBackground = Color(0xFF15080F),
    dividerColor = Color(0xFF662E49),
    circadianCueColor = Color(0xFFFB923C)
)

val CyberNeonTheme = ClockVisualTheme(
    bgStart = Color(0xFF000000),
    bgEnd = Color(0xFF04030D),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA8B8D0),
    numberColor = Color(0xFF00F5D4),
    accentColor = Color(0xFF38BDF8),
    onAccentColor = Color(0xFF030712),
    cardBackground = Color(0xFF09081A),
    dividerColor = Color(0xFF342E6D),
    circadianCueColor = Color(0xFF38BDF8)
)

val SolarAmberTheme = ClockVisualTheme(
    bgStart = Color(0xFF000000),
    bgEnd = Color(0xFF090602),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFD9BE8C),
    numberColor = Color(0xFFFBBF24),
    accentColor = Color(0xFFF59E0B),
    onAccentColor = Color(0xFF120B02),
    cardBackground = Color(0xFF130D05),
    dividerColor = Color(0xFF60421E),
    circadianCueColor = Color(0xFFF59E0B)
)

val ObsidianOledTheme = ClockVisualTheme(
    bgStart = Color(0xFF000000),
    bgEnd = Color(0xFF000000),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB3B3B3),
    numberColor = Color(0xFFFFFFFF),
    accentColor = Color(0xFFFFFFFF),
    onAccentColor = Color(0xFF000000),
    cardBackground = Color(0xFF0D0D0D),
    dividerColor = Color(0xFF3A3A3A),
    circadianCueColor = Color(0xFFFFFFFF)
)

val AllPresetThemes: List<ThemePresetOption> = listOf(
    ThemePresetOption("Midnight Cosmos", "Indigo & Violet", CosmosMidnightTheme),
    ThemePresetOption("Forest Dawn", "Emerald & Mint", DawnForestTheme),
    ThemePresetOption("Nordic Light", "Crisp Daylight", NordicWhiteTheme),
    ThemePresetOption("Sunset Aura", "Plum & Peach", SunsetWarmTheme),
    ThemePresetOption("Cyber Neon", "Synthwave Cyan", CyberNeonTheme),
    ThemePresetOption("Solar Amber", "Warm Gold", SolarAmberTheme),
    ThemePresetOption("Obsidian OLED", "True Pitch Black", ObsidianOledTheme)
)

fun getThemeByName(name: String): ClockVisualTheme {
    return AllPresetThemes.firstOrNull { it.name == name }?.theme ?: CosmosMidnightTheme
}

fun buildCustomVisualTheme(
    bgArgb: Long,
    textArgb: Long,
    numberArgb: Long
): ClockVisualTheme {
    val baseBg = Color((bgArgb.toInt()) or -0x1000000)
    val primaryText = Color((textArgb.toInt()) or -0x1000000)
    val digitsColor = Color((numberArgb.toInt()) or -0x1000000)

    val bgLum = baseBg.luminance()
    val isDarkBg = bgLum < 0.5f

    val bgEnd = shiftHsvValue(baseBg, if (isDarkBg) 0.04f else -0.06f)
    val cardBg = shiftHsvValue(baseBg, if (isDarkBg) 0.08f else -0.04f)
    val divider = shiftHsvValue(baseBg, if (isDarkBg) 0.24f else -0.22f)

    // Ensure accentColor remains clearly visible against baseBg
    val candidateAccent = digitsColor
    val accentLumDiff = kotlin.math.abs(candidateAccent.luminance() - bgLum)
    val effectiveAccent = if (accentLumDiff >= 0.18f) {
        candidateAccent
    } else {
        if (isDarkBg) shiftHsvValue(candidateAccent, 0.45f) else shiftHsvValue(candidateAccent, -0.45f)
    }

    val onAccent = if (effectiveAccent.luminance() > 0.48f) {
        Color(0xFF070B14)
    } else {
        Color(0xFFFFFFFF)
    }

    return ClockVisualTheme(
        bgStart = baseBg,
        bgEnd = bgEnd,
        textPrimary = primaryText,
        textSecondary = lerp(primaryText, baseBg, 0.28f),
        numberColor = digitsColor,
        accentColor = effectiveAccent,
        onAccentColor = onAccent,
        cardBackground = cardBg,
        dividerColor = divider,
        circadianCueColor = effectiveAccent
    )
}

enum class CircadianPhase {
    COOL_MORNING,
    DAYLIGHT_NEUTRAL,
    WARM_EVENING,
    SOOTHING_NIGHT
}

data class CircadianProfile(
    val phase: CircadianPhase,
    val label: String,
    val warmthFactor: Float, // -1.0 (coolest morning) .. 0.0 (daylight) .. +1.0 (warmest evening/night)
    val blendStrength: Float
)

private fun smoothStep01(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return (0.5f - 0.5f * cos(t * PI)).toFloat()
}

/**
 * Computes a continuous, soothing circadian color temperature curve from local time:
 * - 05:00 – 11:30: Cool Morning (crisp icy cerulean/slate tones, peak around 07:30-09:00)
 * - 11:30 – 16:30: Clear Daylight (neutral balanced tones)
 * - 16:30 – 21:30: Warm Evening (golden amber/peach warmth, gently reducing blue light)
 * - 21:30 – 05:00: Soothing Night (deep amber-charcoal comfort for restful viewing)
 */
fun computeCircadianProfile(localTime: ZonedDateTime): CircadianProfile {
    val hourFloat = localTime.hour + localTime.minute / 60f

    return when {
        // Morning cool window: 05:00 -> 11:30
        hourFloat in 5.0f..11.5f -> {
            val strength = when {
                hourFloat < 6.5f -> smoothStep01(5.0f, 6.5f, hourFloat)
                hourFloat <= 9.5f -> 1.0f
                else -> 1.0f - smoothStep01(9.5f, 11.5f, hourFloat)
            }
            CircadianProfile(
                phase = CircadianPhase.COOL_MORNING,
                label = "Cool Morning",
                warmthFactor = -strength,
                blendStrength = strength
            )
        }
        // Clear Midday window: 11:30 -> 16:30
        hourFloat > 11.5f && hourFloat < 16.5f -> {
            CircadianProfile(
                phase = CircadianPhase.DAYLIGHT_NEUTRAL,
                label = "Clear Daylight",
                warmthFactor = 0f,
                blendStrength = 0f
            )
        }
        // Golden Evening warm transition: 16:30 -> 21:30
        hourFloat in 16.5f..21.5f -> {
            val strength = smoothStep01(16.5f, 19.5f, hourFloat)
            CircadianProfile(
                phase = CircadianPhase.WARM_EVENING,
                label = "Warm Evening",
                warmthFactor = strength,
                blendStrength = strength
            )
        }
        // Soothing Late Night amber: 21:30 -> 05:00
        else -> {
            val strength = if (hourFloat >= 21.5f) {
                1.0f
            } else {
                // Smoothly ease out between 03:30 and 05:00 toward cool dawn
                if (hourFloat < 3.5f) 1.0f else 1.0f - smoothStep01(3.5f, 5.0f, hourFloat)
            }
            CircadianProfile(
                phase = CircadianPhase.SOOTHING_NIGHT,
                label = "Soothing Night",
                warmthFactor = strength,
                blendStrength = strength
            )
        }
    }
}

/**
 * Shifts ClockVisualTheme smoothly based on time of day while preserving zero-haze contrast:
 * - Obsidian OLED: Keeps ALL UI surfaces, borders, text, switches, and numbers 100% pure B&W monochrome;
 *   shifts ONLY `circadianCueColor` (used on the small circadian badge & precision countdown ring).
 * - Other Dark Themes: Keeps pitch-black backgrounds, solid dark cards, borders, text, and numerals locked;
 *   shifts `accentColor` & `circadianCueColor` (cooler cyan/blue in morning, warmer amber/orange in evening).
 * - Light Themes: Preserves bright readability while shifting accent and subtle ambient warmth/coolness.
 */
fun applyCircadianShift(
    baseTheme: ClockVisualTheme,
    localTime: ZonedDateTime,
    enabled: Boolean,
    isCustomTheme: Boolean = false,
    isMonochromeOled: Boolean = (baseTheme == ObsidianOledTheme)
): ClockVisualTheme {
    if (!enabled) return baseTheme

    val profile = computeCircadianProfile(localTime)
    if (profile.blendStrength <= 0.005f || profile.phase == CircadianPhase.DAYLIGHT_NEUTRAL) {
        return baseTheme
    }

    val targetCueColor = when (profile.phase) {
        CircadianPhase.COOL_MORNING -> Color(0xFF38BDF8)
        CircadianPhase.WARM_EVENING -> Color(0xFFF59E0B)
        CircadianPhase.SOOTHING_NIGHT -> Color(0xFFFB923C)
        CircadianPhase.DAYLIGHT_NEUTRAL -> baseTheme.circadianCueColor
    }

    // Strict Obsidian OLED Monochrome Rule: Keep UI strictly B&W, tint ONLY small badge/ring cue
    if (isMonochromeOled) {
        val shiftedCue = lerp(baseTheme.circadianCueColor, targetCueColor, (0.85f * profile.blendStrength).coerceIn(0f, 1f))
        return baseTheme.copy(circadianCueColor = shiftedCue)
    }

    val isLightTheme = baseTheme.bgStart.luminance() > 0.5f
    val customScale = if (isCustomTheme) 0.72f else 1.0f

    if (!isLightTheme) {
        // Dark themes: Keep pitch-black bgStart, bgEnd, cardBackground, dividerColor, and text locked.
        // Shift accent hue (cool blue in morning, warm amber/orange in evening/night).
        val accentFraction = (0.78f * profile.blendStrength * customScale).coerceIn(0f, 1f)
        val shiftedAccent = lerp(baseTheme.accentColor, targetCueColor, accentFraction)
        val shiftedOnAccent = if (shiftedAccent.luminance() > 0.45f) {
            Color(0xFF000000)
        } else {
            Color(0xFFFFFFFF)
        }
        return baseTheme.copy(
            accentColor = shiftedAccent,
            onAccentColor = shiftedOnAccent,
            circadianCueColor = shiftedAccent
        )
    }

    // Light theme (Nordic Light): preserve high-luminance white surfaces while shifting accent & subtle tint
    val s = profile.blendStrength * customScale
    val bgFraction = 0.28f * s
    val accentFraction = 0.65f * s

    val targetBgEnd: Color
    val targetAccent: Color
    val targetNumber: Color
    val targetDivider: Color

    when (profile.phase) {
        CircadianPhase.COOL_MORNING -> {
            targetBgEnd = Color(0xFFDBEAFE)
            targetDivider = Color(0xFF93C5FD)
            targetAccent = Color(0xFF0284C7)
            targetNumber = Color(0xFF0369A1)
        }
        CircadianPhase.WARM_EVENING -> {
            targetBgEnd = Color(0xFFFED7AA)
            targetDivider = Color(0xFFFDBA74)
            targetAccent = Color(0xFFD97706)
            targetNumber = Color(0xFF9A3412)
        }
        CircadianPhase.SOOTHING_NIGHT -> {
            targetBgEnd = Color(0xFFFDE68A)
            targetDivider = Color(0xFFF59E0B)
            targetAccent = Color(0xFFB45309)
            targetNumber = Color(0xFF7C2D12)
        }
        CircadianPhase.DAYLIGHT_NEUTRAL -> return baseTheme
    }

    val shiftedAccent = lerp(baseTheme.accentColor, targetAccent, accentFraction.coerceIn(0f, 1f))
    val shiftedOnAccent = if (shiftedAccent.luminance() > 0.48f) {
        Color(0xFF0A0908)
    } else {
        Color(0xFFFFFFFF)
    }

    return baseTheme.copy(
        bgEnd = lerp(baseTheme.bgEnd, targetBgEnd, bgFraction.coerceIn(0f, 1f)),
        numberColor = lerp(baseTheme.numberColor, targetNumber, (0.45f * s).coerceIn(0f, 1f)),
        accentColor = shiftedAccent,
        onAccentColor = shiftedOnAccent,
        dividerColor = lerp(baseTheme.dividerColor, targetDivider, (0.45f * s).coerceIn(0f, 1f)),
        circadianCueColor = shiftedAccent
    )
}

@Composable
fun rememberAnimatedVisualTheme(targetTheme: ClockVisualTheme): ClockVisualTheme {
    val animSpec = tween<Color>(durationMillis = 850, easing = FastOutSlowInEasing)
    val bgStart by animateColorAsState(targetTheme.bgStart, animSpec, label = "ThemeBgStart")
    val bgEnd by animateColorAsState(targetTheme.bgEnd, animSpec, label = "ThemeBgEnd")
    val textPrimary by animateColorAsState(targetTheme.textPrimary, animSpec, label = "ThemeTextPrimary")
    val textSecondary by animateColorAsState(targetTheme.textSecondary, animSpec, label = "ThemeTextSecondary")
    val numberColor by animateColorAsState(targetTheme.numberColor, animSpec, label = "ThemeNumber")
    val accentColor by animateColorAsState(targetTheme.accentColor, animSpec, label = "ThemeAccent")
    val onAccentColor by animateColorAsState(targetTheme.onAccentColor, animSpec, label = "ThemeOnAccent")
    val cardBackground by animateColorAsState(targetTheme.cardBackground, animSpec, label = "ThemeCardBg")
    val dividerColor by animateColorAsState(targetTheme.dividerColor, animSpec, label = "ThemeDivider")
    val circadianCueColor by animateColorAsState(targetTheme.circadianCueColor, animSpec, label = "ThemeCircadianCue")

    return ClockVisualTheme(
        bgStart = bgStart,
        bgEnd = bgEnd,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        numberColor = numberColor,
        accentColor = accentColor,
        onAccentColor = onAccentColor,
        cardBackground = cardBackground,
        dividerColor = dividerColor,
        circadianCueColor = circadianCueColor
    )
}

fun resolveVisualTheme(
    settings: ClockSettings,
    ambientLux: Float? = null,
    localTime: ZonedDateTime? = null
): ClockVisualTheme {
    val isCustom = settings.themeName == "Custom"
    val baseTheme = when {
        isCustom -> buildCustomVisualTheme(
            bgArgb = settings.customBgColorArgb,
            textArgb = settings.customTextColorArgb,
            numberArgb = settings.customNumberColorArgb
        )
        settings.autoAmbientSensorEnabled && ambientLux != null -> when {
            ambientLux < 25f -> CosmosMidnightTheme
            ambientLux > 120f -> NordicWhiteTheme
            else -> getThemeByName(settings.themeName)
        }
        else -> getThemeByName(settings.themeName)
    }
    return if (localTime != null) {
        applyCircadianShift(
            baseTheme = baseTheme,
            localTime = localTime,
            enabled = settings.circadianShiftEnabled,
            isCustomTheme = isCustom,
            isMonochromeOled = (!isCustom && baseTheme == ObsidianOledTheme)
        )
    } else {
        baseTheme
    }
}

fun getEffectiveTheme(
    themeName: String,
    autoSensorEnabled: Boolean,
    ambientLux: Float?,
    customBgArgb: Long = 0xFF070B14L,
    customTextArgb: Long = 0xFFE2E8F0L,
    customNumberArgb: Long = 0xFF38BDF8L
): ClockVisualTheme {
    if (themeName == "Custom") {
        return buildCustomVisualTheme(customBgArgb, customTextArgb, customNumberArgb)
    }
    if (autoSensorEnabled && ambientLux != null) {
        return when {
            ambientLux < 25f -> CosmosMidnightTheme
            ambientLux > 120f -> NordicWhiteTheme
            else -> getThemeByName(themeName)
        }
    }
    return getThemeByName(themeName)
}

private fun shiftHsvValue(color: Color, deltaValue: Float): Color {
    val hsv = colorToHsv(color)
    val newV = (hsv[2] + deltaValue).coerceIn(0f, 1f)
    val newS = if (deltaValue > 0f && hsv[2] > 0.92f) (hsv[1] * 0.85f).coerceIn(0f, 1f) else hsv[1]
    return hsvToColor(hsv[0], newS, newV)
}

fun colorToHsv(color: Color): FloatArray {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)
    return hsv
}

fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
    val argb = AndroidColor.HSVToColor(
        floatArrayOf(
            hue.coerceIn(0f, 360f),
            saturation.coerceIn(0f, 1f),
            value.coerceIn(0f, 1f)
        )
    )
    return Color(argb)
}

fun colorToHex(color: Color): String {
    val rgb = color.toArgb() and 0xFFFFFF
    return String.format(Locale.US, "#%06X", rgb)
}

fun colorToOpaqueArgbLong(color: Color): Long {
    val rgb = color.toArgb() and 0xFFFFFF
    return 0xFF000000L or rgb.toLong()
}

fun parseHexColorOrNull(hexInput: String): Color? {
    val cleaned = hexInput.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    val rgb = cleaned.toIntOrNull(16) ?: return null
    return Color((0xFF shl 24) or rgb)
}

@Composable
fun rememberAmbientLux(): Float? {
    val context = LocalContext.current
    var luxValue by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (sensorManager != null && lightSensor != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event != null && event.sensor.type == Sensor.TYPE_LIGHT) {
                        luxValue = event.values.getOrNull(0)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        } else {
            onDispose { }
        }
    }
    return luxValue
}
