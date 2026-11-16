package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ChronometerDisplayFont
import com.example.ui.theme.ChronometerMonoFont
import com.example.ui.theme.ClockVisualTheme
import com.example.util.TimeAndAlertUtils
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// FACE: RETRO LCD - seven-segment digits with unlit "ghost" segments, like a calculator watch
// ---------------------------------------------------------------------------------------------

/** Segment order: top, top-right, bottom-right, bottom, bottom-left, top-left, middle. */
private val SegmentsFor = mapOf(
    '0' to "1111110", '1' to "0110000", '2' to "1101101", '3' to "1111001", '4' to "0110011",
    '5' to "1011011", '6' to "1011111", '7' to "1110000", '8' to "1111111", '9' to "1111011"
)

private fun DrawScope.segmentDigit(ch: Char, topLeft: Offset, w: Float, h: Float, on: Color, off: Color) {
    val t = w * 0.17f            // segment thickness
    val g = t * 0.12f            // gap
    val pattern = SegmentsFor[ch] ?: "0000000"
    fun hSeg(x: Float, y: Float, lit: Boolean) {
        val p = Path().apply {
            moveTo(x + t * 0.5f + g, y + t / 2); lineTo(x + t + g, y); lineTo(x + w - t - g, y)
            lineTo(x + w - t * 0.5f - g, y + t / 2); lineTo(x + w - t - g, y + t); lineTo(x + t + g, y + t); close()
        }
        drawPath(p, if (lit) on else off)
    }
    fun vSeg(x: Float, y: Float, len: Float, lit: Boolean) {
        val p = Path().apply {
            moveTo(x + t / 2, y + t * 0.5f + g); lineTo(x + t, y + t + g); lineTo(x + t, y + len - t - g)
            lineTo(x + t / 2, y + len - t * 0.5f - g); lineTo(x, y + len - t - g); lineTo(x, y + t + g); close()
        }
        drawPath(p, if (lit) on else off)
    }
    val x0 = topLeft.x; val y0 = topLeft.y
    val half = h / 2f
    fun lit(i: Int) = pattern[i] == '1'
    hSeg(x0, y0, lit(0))                                   // top
    vSeg(x0 + w - t, y0, half + t / 2, lit(1))              // top-right
    vSeg(x0 + w - t, y0 + half - t / 2, half + t / 2, lit(2)) // bottom-right
    hSeg(x0, y0 + h - t, lit(3))                            // bottom
    vSeg(x0, y0 + half - t / 2, half + t / 2, lit(4))       // bottom-left
    vSeg(x0, y0, half + t / 2, lit(5))                      // top-left
    hSeg(x0, y0 + half - t / 2, lit(6))                     // middle
}

@Composable
private fun SegmentTime(d: FaceData, modifier: Modifier) {
    val th = d.theme
    val digits = d.time.hm.filter { it.isDigit() }.padStart(4, '0')
    Canvas(modifier) {
        val dw = size.width / 4.9f
        val dh = size.height
        val gap = dw * 0.18f
        val on = th.numberColor
        val off = th.numberColor.copy(alpha = 0.08f)
        var x = 0f
        for (i in 0 until 4) {
            segmentDigit(digits[i], Offset(x, 0f), dw, dh, on, off)
            x += dw + gap
            if (i == 1) {
                val cx = x + gap * 0.3f
                drawCircle(on, radius = dw * 0.07f, center = Offset(cx, dh * 0.33f))
                drawCircle(on, radius = dw * 0.07f, center = Offset(cx, dh * 0.67f))
                x += gap * 1.6f
            }
        }
    }
}

@Composable
internal fun LcdFace(m: LsMetrics, d: FaceData) {
    val th = d.theme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelPad = 20.dp
        val timeW = (maxWidth - panelPad * 2) * 0.86f
        val timeH = minOf(timeW * 0.36f, maxHeight * (if (m.shortLandscape) 0.30f else 0.38f))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            // The "display": an inset panel with the digits.
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(th.cardBackground).padding(panelPad),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(d.time.date, fontSize = m.sp(11f), fontFamily = ChronometerMonoFont, letterSpacing = 2.sp, color = th.textSecondary, maxLines = 1)
                    Text(
                        (if (d.time.ampm.isNotEmpty()) d.time.ampm + "  " else "") + (if (d.settings.showSeconds) ":" + d.time.seconds else ""),
                        fontSize = m.sp(13f), fontFamily = ChronometerMonoFont, color = th.accentColor, maxLines = 1
                    )
                }
                SegmentTime(d, Modifier.size(timeW, timeH))
                // Chime countdown as a segmented bar.
                Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    val cells = 30
                    val filled = (d.chime.progress * cells).toInt()
                    repeat(cells) { i ->
                        Box(Modifier.weight(1f).fillMaxHeight().background(if (i < filled) th.accentColor else th.numberColor.copy(alpha = 0.08f)))
                    }
                }
                Text(d.chime.label, fontSize = m.sp(12f), fontFamily = ChronometerMonoFont, color = th.textSecondary, maxLines = 1)
            }
            FaceCityList(d, m, columns = if (m.shortLandscape) 3 else if (m.landscape) 2 else 1, mono = true)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// FACE: DAY ARC - the sun crosses an arc as the day passes; the night is a dim arc below the horizon
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ArcFace(m: LsMetrics, d: FaceData) {
    val th = d.theme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val arcH = minOf(maxHeight * 0.34f, maxWidth * 0.30f)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            Canvas(Modifier.fillMaxWidth().height(arcH)) {
                val w = size.width; val hh = size.height
                // Day arc above the horizon (full height r); night is a flatter curve (0.22r) below it.
                val nightK = 0.22f
                val r = minOf(w * 0.44f, hh / (1.08f + nightK))
                val c = Offset(w / 2f, r * 1.04f)
                drawArc(th.textPrimary.copy(alpha = 0.2f), 180f, 180f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(r * 0.016f, cap = StrokeCap.Round))
                drawArc(th.textPrimary.copy(alpha = 0.07f), 0f, 180f, false, Offset(c.x - r, c.y - r * nightK), Size(r * 2, r * nightK * 2), style = Stroke(r * 0.016f, cap = StrokeCap.Round))
                drawLine(th.textPrimary.copy(alpha = 0.18f), Offset(c.x - r * 1.08f, c.y), Offset(c.x + r * 1.08f, c.y), strokeWidth = r * 0.008f)
                // Chime countdown: the horizon line fills from the left as the next chime approaches.
                drawLine(th.accentColor, Offset(c.x - r * 1.08f, c.y), Offset(c.x - r * 1.08f + r * 2.16f * d.chime.progress, c.y), strokeWidth = r * 0.022f, cap = StrokeCap.Round)
                // Progress along the day: 6:00 is the left horizon, 12:00 the top, 18:00 the right horizon.
                val hourF = d.now.hour + d.now.minute / 60f
                val rel = ((hourF - 6f + 24f) % 24f) / 12f          // 0..2 across a full day starting at 6:00
                val isDay = rel <= 1f
                // Day: left horizon -> over the top -> right horizon. Night: the same sweep, mirrored below the horizon.
                val t = (if (isDay) rel else rel - 1f) * PI.toFloat()
                val pos = Offset(c.x - cos(t) * r, if (isDay) c.y - sin(t) * r else c.y + sin(t) * r * nightK)
                drawCircle(if (isDay) th.accentColor else Color(0xFFB9C2FF), radius = r * 0.055f, center = pos)
                if (isDay) drawCircle(th.accentColor.copy(alpha = 0.25f), radius = r * 0.11f, center = pos)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(d.time.hm, fontSize = m.sp(if (m.landscape) 64f else 56f), fontWeight = FontWeight.Light, color = th.textPrimary, fontFamily = ChronometerDisplayFont, maxLines = 1)
                if (d.time.ampm.isNotEmpty()) Text(d.time.ampm, fontSize = m.sp(16f), color = th.accentColor, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
            }
            Text(d.time.date, fontSize = m.sp(12f), letterSpacing = 5.sp, color = th.textSecondary, maxLines = 1)
            Text(d.chime.label, fontSize = m.sp(14f), color = th.textPrimary, maxLines = 1)
            FaceCityList(d, m, columns = if (m.shortLandscape) 4 else if (m.landscape) 3 else if (m.phone) 1 else 2)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// FACE: WORD CLOCK - the time is spelled by lighting up letters in a grid ("IT IS QUARTER PAST TEN")
// ---------------------------------------------------------------------------------------------

private val WordGrid = listOf(
    "ITLISASTIME", "ACQUARTERDC", "TWENTYFIVEX", "HALFBTENFTO", "PASTERUNINE",
    "ONESIXTHREE", "FOURFIVETWO", "EIGHTELEVEN", "SEVENTWELVE", "TENSEOCLOCK"
)

/** (row, startColumn, length) of each word in [WordGrid]. */
private class W(val row: Int, val col: Int, val len: Int)

private val W_IT = W(0, 0, 2); private val W_IS = W(0, 3, 2)
private val W_QUARTER = W(1, 2, 7); private val W_TWENTY = W(2, 0, 6); private val W_MFIVE = W(2, 6, 4)
private val W_HALF = W(3, 0, 4); private val W_MTEN = W(3, 5, 3); private val W_TO = W(3, 9, 2)
private val W_PAST = W(4, 0, 4)
private val W_OCLOCK = W(9, 5, 6)
private val HourWords = listOf(
    W(8, 5, 6),            // 12 (index 0)
    W(5, 0, 3), W(6, 8, 3), W(5, 6, 5), W(6, 0, 4), W(6, 4, 4), W(5, 3, 3),   // 1..6
    W(8, 0, 5), W(7, 0, 5), W(4, 7, 4), W(9, 0, 3), W(7, 5, 6)                // 7..11
)

/** Which words are lit for [now]; minutes are floored to the last five so the clock is never early. */
private fun litWords(now: ZonedDateTime): List<W> {
    val idx = now.minute / 5
    val out = mutableListOf(W_IT, W_IS)
    val to = idx >= 7
    val hour = (now.hour + if (to) 1 else 0) % 12
    when (idx) {
        0 -> {}
        1, 11 -> out += W_MFIVE
        2, 10 -> out += W_MTEN
        3, 9 -> out += W_QUARTER
        4, 8 -> out += W_TWENTY
        5, 7 -> { out += W_TWENTY; out += W_MFIVE }
        6 -> out += W_HALF
    }
    if (idx in 1..6) out += W_PAST
    if (to) out += W_TO
    out += HourWords[hour]
    if (idx == 0) out += W_OCLOCK
    return out
}

@Composable
internal fun WordsFace(m: LsMetrics, d: FaceData) {
    val th = d.theme
    val lit = litWords(d.now)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight * 1.15f
        val cell = if (wide) minOf(maxHeight * 0.092f, maxWidth * 0.5f / 11f) else minOf(maxWidth / 11.5f, maxHeight * 0.5f / 10f)

        @Composable
        fun Grid() {
            Column {
                WordGrid.forEachIndexed { r, line ->
                    Row {
                        line.forEachIndexed { c, ch ->
                            val on = lit.any { it.row == r && c >= it.col && c < it.col + it.len }
                            Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                                Text(
                                    ch.toString(), fontSize = dpSp(cell * 0.62f),
                                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                    color = if (on) th.numberColor else th.textSecondary.copy(alpha = 0.22f), maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        @Composable
        fun Side(modifier: Modifier) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(d.time.hm, fontSize = m.sp(26f), color = th.textPrimary, fontFamily = ChronometerDisplayFont, fontWeight = FontWeight.Light, maxLines = 1)
                    if (d.time.ampm.isNotEmpty()) Text(d.time.ampm, fontSize = m.sp(11f), color = th.accentColor, modifier = Modifier.padding(start = 5.dp, bottom = 4.dp))
                }
                Text(d.time.date, fontSize = m.sp(10f), letterSpacing = 3.sp, color = th.textSecondary, maxLines = 1)
                Text(d.chime.label, fontSize = m.sp(13f), color = th.textPrimary, maxLines = 1)
                FaceCityList(d, m, columns = if (wide || m.phone) 1 else 2)
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Grid(); Side(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                Grid(); Side(Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// FACE: TIMELINE - a 24h bar per city (day/night), a moving "now" line, chime ticks along the top
// ---------------------------------------------------------------------------------------------

@Composable
internal fun TimelineFace(m: LsMetrics, d: FaceData) {
    val th = d.theme
    val homeZone: ZoneId = d.now.zone
    val dayStart = d.now.toLocalDate().atStartOfDay(homeZone)
    val intervalMin = d.settings.chimeIntervalMinutes.coerceAtLeast(1)
    val stackChime = m.phone && !m.landscape
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(d.time.hm, fontSize = m.sp(48f), fontWeight = FontWeight.Light, color = th.textPrimary, fontFamily = ChronometerDisplayFont, maxLines = 1)
            Column(Modifier.padding(bottom = 8.dp)) {
                if (d.time.ampm.isNotEmpty()) Text(d.time.ampm, fontSize = m.sp(14f), color = th.accentColor)
                Text(d.time.date, fontSize = m.sp(10f), letterSpacing = 3.sp, color = th.textSecondary, maxLines = 1)
            }
            if (!stackChime) {
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(d.chime.label, fontSize = m.sp(15f), fontWeight = FontWeight.SemiBold, color = th.textPrimary, maxLines = 1)
                    Text(d.chime.sub.uppercase(), fontSize = m.sp(9f), letterSpacing = 1.5.sp, color = th.textSecondary, maxLines = 1)
                }
            }
        }
        // On a narrow phone the chime text has no room beside the clock, so it sits on its own line.
        if (stackChime) Text(d.chime.label + " · " + d.chime.sub.lowercase(), fontSize = m.sp(12f), color = th.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // Rows share the remaining height; each is: label column + 24h bar.
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            d.cities.forEach { city ->
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.width(if (m.phone) 104.dp else 168.dp)
                            .lsCityGestures(d.toggleControls) { d.openCity(city) }
                            .padding(end = 8.dp)
                    ) {
                        Text(city.display.uppercase(), fontSize = m.sp(9f), letterSpacing = 1.2.sp, color = if (city.home) th.textPrimary else th.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(city.time + if (city.ampm.isNotEmpty()) " ${city.ampm}" else "", fontSize = m.sp(14f), fontWeight = FontWeight.SemiBold, color = th.textPrimary, fontFamily = ChronometerDisplayFont, maxLines = 1)
                    }
                    val zone = TimeAndAlertUtils.safeZoneId(city.friend.timezoneId)
                    Canvas(Modifier.weight(1f).fillMaxHeight(0.62f)) {
                        val w = size.width; val h = size.height
                        val colW = w / 24f
                        for (hr in 0 until 24) {
                            val local = dayStart.plusHours(hr.toLong()).withZoneSameInstant(zone).hour
                            val day = local in 7..18
                            drawRoundRect(
                                color = if (day) th.accentColor.copy(alpha = 0.55f) else th.textPrimary.copy(alpha = 0.08f),
                                topLeft = Offset(hr * colW + 1f, 0f), size = Size(colW - 2f, h), cornerRadius = CornerRadius(h * 0.2f)
                            )
                        }
                        // Chime ticks through the day.
                        var mins = 0
                        while (mins < 1440) {
                            val x = mins / 1440f * w
                            drawLine(th.textPrimary.copy(alpha = 0.28f), Offset(x, 0f), Offset(x, h * 0.18f), strokeWidth = 1.5f)
                            mins += intervalMin
                        }
                        // Now line (home time).
                        val nowX = (d.now.hour * 60 + d.now.minute + d.now.second / 60f) / 1440f * w
                        drawLine(th.textPrimary, Offset(nowX, -2f), Offset(nowX, h + 2f), strokeWidth = 3f, cap = StrokeCap.Round)
                    }
                }
            }
        }
        // Hour scale.
        Row(Modifier.fillMaxWidth().padding(start = if (m.phone) 104.dp else 168.dp)) {
            listOf(0, 6, 12, 18, 24).forEach { hr ->
                Text(
                    "%02d".format(hr % 24), fontSize = m.sp(9f), fontFamily = ChronometerMonoFont, color = th.textSecondary,
                    modifier = Modifier.weight(if (hr == 24) 0.0001f else 1f)
                )
            }
        }
    }
}
