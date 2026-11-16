package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClockSettings
import com.example.data.FriendClock
import com.example.ui.ClockViewModel
import com.example.ui.theme.ChronometerDisplayFont
import com.example.ui.theme.ChronometerMonoFont
import com.example.ui.theme.ClockVisualTheme
import com.example.util.TimeAndAlertUtils
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Everything a watch face needs, prepared once. Every face receives the same data, so every face can show the
 * same information; only the way it is drawn differs.
 */
internal class FaceData(
    val now: ZonedDateTime,
    val settings: ClockSettings,
    val theme: ClockVisualTheme,
    val time: LsTime,
    val chime: LsChime,
    val cities: List<LsCity>,
    val toggleControls: () -> Unit,
    val openCity: (LsCity) -> Unit
)

/**
 * The shared shell around every watch face: background, tap-to-reveal controls, header, chime actions, per-city
 * menu (pin / remove), eye-break line and burn-in drift. A face only supplies its artwork via [content], so no
 * face can miss a feature.
 */
@Composable
internal fun LsFaceFrame(
    currentLocalTime: ZonedDateTime,
    settings: ClockSettings,
    friendClocks: List<FriendClock>,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    onAddFriendClicked: () -> Unit,
    onOpenCustomThemeStudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSyncInspector: () -> Unit,
    onOpenBreathing: () -> Unit,
    backdrop: DrawScope.(ClockVisualTheme) -> Unit = {},
    content: @Composable BoxScope.(LsMetrics, FaceData) -> Unit
) {
    val reveal = rememberControlsReveal()
    var pendingCity by remember { mutableStateOf<LsCity?>(null) }
    val time = rememberLsTime(currentLocalTime, settings.digital24HourFormat)
    val chime = rememberLsChime(settings, viewModel)
    val cities = rememberLsCities(currentLocalTime, settings, friendClocks)
    val data = FaceData(
        now = currentLocalTime, settings = settings, theme = activeTheme, time = time, chime = chime, cities = cities,
        toggleControls = { reveal.value = !reveal.value },
        openCity = { pendingCity = it }
    )

    LsBox(
        Modifier
            .fillMaxSize()
            .background(activeTheme.bgStart)
            .drawBehind { backdrop(activeTheme) }
            .tapToToggle(reveal)
    ) { m ->
        Column(
            Modifier
                .fillMaxSize()
                .lsBurnInDrift(currentLocalTime.minute)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = m.pad, vertical = m.pad * 0.7f)
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) { content(m, data) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                LsEyeBreak(settings, activeTheme, m, viewModel)
            }
        }
        LsCityActionsDialog(pendingCity, activeTheme, viewModel, onDismiss = { pendingCity = null })
        LsChimeActions(
            reveal.value, activeTheme, m, viewModel, onAddFriendClicked,
            if (m.landscape) Modifier.align(Alignment.CenterEnd).padding(end = 24.dp)
            else Modifier.align(Alignment.TopCenter).padding(top = 150.dp)
        )
        LsControlsHeader(
            reveal.value, currentLocalTime, settings, activeTheme, viewModel,
            onOpenCustomThemeStudio, onOpenSettings, onOpenSyncInspector, onOpenBreathing,
            Modifier.align(Alignment.TopCenter)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Small shared pieces for faces
// ---------------------------------------------------------------------------------------------

@Composable
internal fun dpSp(dp: Dp): TextUnit = with(LocalDensity.current) { dp.toSp() }

/** A tappable list of cities: tap toggles controls, long-press opens the city menu. */
@Composable
internal fun FaceCityList(
    d: FaceData,
    m: LsMetrics,
    columns: Int,
    modifier: Modifier = Modifier,
    mono: Boolean = false
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        d.cities.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { c ->
                    Row(
                        Modifier
                            .weight(1f)
                            .lsCityGestures(d.toggleControls) { d.openCity(c) }
                            .padding(vertical = 5.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(6.dp).clip(CircleShape)
                                .background(if (c.night) Color(0xFF8B9BFF) else d.theme.accentColor)
                        )
                        Text(
                            c.name.uppercase(), fontSize = m.sp(10f), letterSpacing = 1.2.sp,
                            color = if (c.home) d.theme.textPrimary else d.theme.textSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = 6.dp)
                        )
                        Text(
                            c.time + if (c.ampm.isNotEmpty()) " ${c.ampm}" else "",
                            fontSize = m.sp(13f), fontWeight = FontWeight.SemiBold, color = d.theme.textPrimary, maxLines = 1,
                            fontFamily = if (mono) ChronometerMonoFont else ChronometerDisplayFont,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FaceChimeLine(d: FaceData, m: LsMetrics, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(d.chime.label, fontSize = m.sp(15f), fontWeight = FontWeight.SemiBold, color = d.theme.textPrimary, maxLines = 1)
        Text(d.chime.sub.uppercase(), fontSize = m.sp(9f), letterSpacing = 1.5.sp, color = d.theme.textSecondary, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// FACE: ANALOG - a real dial with hands; the chime countdown is a ring around the edge
// ---------------------------------------------------------------------------------------------

@Composable
internal fun AnalogFace(m: LsMetrics, d: FaceData) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight * 1.15f
        val dial = if (wide) minOf(maxHeight, maxWidth * 0.52f) else minOf(maxWidth, maxHeight * 0.56f)

        @Composable
        fun Info(modifier: Modifier) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        d.time.hm, fontSize = m.sp(if (wide) 44f else 36f), fontWeight = FontWeight.Light,
                        color = d.theme.textPrimary, fontFamily = ChronometerDisplayFont, maxLines = 1,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    if (d.time.ampm.isNotEmpty()) {
                        Text(d.time.ampm, fontSize = m.sp(14f), color = d.theme.accentColor, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                    }
                }
                Text(d.time.date, fontSize = m.sp(11f), letterSpacing = 3.sp, color = d.theme.textSecondary, maxLines = 1)
                FaceChimeLine(d, m)
                FaceCityList(d, m, columns = if (wide) 1 else 2)
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                AnalogDial(d, Modifier.size(dial))
                Info(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnalogDial(d, Modifier.size(dial))
                Info(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AnalogDial(d: FaceData, modifier: Modifier) {
    val th = d.theme
    val ink = th.textPrimary
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        // Chime countdown: a ring that fills as the next chime approaches.
        val ring = r * 0.97f
        drawCircle(ink.copy(alpha = 0.10f), radius = ring, center = c, style = Stroke(r * 0.03f))
        drawArc(
            color = th.accentColor, startAngle = -90f, sweepAngle = 360f * d.chime.progress, useCenter = false,
            topLeft = Offset(c.x - ring, c.y - ring), size = Size(ring * 2, ring * 2),
            style = Stroke(r * 0.03f, cap = StrokeCap.Round)
        )
        // Tick marks: 60 small, 12 large.
        for (i in 0 until 60) {
            val a = (i * 6 - 90) * PI / 180.0
            val big = i % 5 == 0
            val quarter = i % 15 == 0
            val outer = r * 0.88f
            val inner = outer - r * (if (quarter) 0.15f else if (big) 0.09f else 0.035f)
            drawLine(
                color = if (big) ink else ink.copy(alpha = 0.4f),
                start = Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                end = Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                strokeWidth = if (big) r * 0.025f else r * 0.01f, cap = StrokeCap.Round
            )
        }
        fun hand(angleDeg: Float, length: Float, width: Float, color: Color, tail: Float = 0f) {
            val a = (angleDeg - 90f) * PI / 180.0
            val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
            drawLine(color, Offset(c.x - dx * tail, c.y - dy * tail), Offset(c.x + dx * length, c.y + dy * length), strokeWidth = width, cap = StrokeCap.Round)
        }
        val h = d.now.hour % 12; val mi = d.now.minute; val s = d.now.second
        hand((h + mi / 60f) * 30f, r * 0.5f, r * 0.05f, ink)
        hand((mi + s / 60f) * 6f, r * 0.76f, r * 0.032f, ink)
        if (d.settings.showSeconds) hand(s * 6f, r * 0.84f, r * 0.012f, th.accentColor, tail = r * 0.14f)
        drawCircle(th.accentColor, radius = r * 0.045f, center = c)
        drawCircle(th.bgStart, radius = r * 0.018f, center = c)
    }
}

// ---------------------------------------------------------------------------------------------
// FACE: FLIP - big split-flap cards, like a desk flip clock
// ---------------------------------------------------------------------------------------------

@Composable
private fun FlipCard(ch: String, w: Dp, h: Dp, th: ClockVisualTheme) {
    Box(
        Modifier.size(w, h).clip(RoundedCornerShape(w * 0.12f)).background(th.cardBackground),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = ch, label = "flip") { value ->
            Text(
                value, fontSize = dpSp(h * 0.66f), fontWeight = FontWeight.Bold, fontFamily = ChronometerDisplayFont,
                color = th.numberColor, maxLines = 1, textAlign = TextAlign.Center,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
        // Lower half is shaded and a hairline marks the hinge, which is what makes it read as a flap.
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.5f).background(Color.Black.copy(alpha = 0.18f)))
        Box(Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(th.bgStart))
    }
}

@Composable
internal fun FlipFace(m: LsMetrics, d: FaceData) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val digits = d.time.hm.filter { it.isDigit() }.padStart(4, '0')
        val cardW = minOf(maxWidth / 5.2f, maxHeight * 0.30f)
        val cardH = cardW * 1.38f
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(cardW * 0.07f), verticalAlignment = Alignment.CenterVertically) {
                FlipCard(digits[0].toString(), cardW, cardH, d.theme)
                FlipCard(digits[1].toString(), cardW, cardH, d.theme)
                Column(Modifier.padding(horizontal = cardW * 0.04f), verticalArrangement = Arrangement.spacedBy(cardW * 0.2f)) {
                    Box(Modifier.size(cardW * 0.1f).clip(CircleShape).background(d.theme.accentColor))
                    Box(Modifier.size(cardW * 0.1f).clip(CircleShape).background(d.theme.accentColor))
                }
                FlipCard(digits[2].toString(), cardW, cardH, d.theme)
                FlipCard(digits[3].toString(), cardW, cardH, d.theme)
                if (d.time.ampm.isNotEmpty()) {
                    Text(d.time.ampm, fontSize = m.sp(16f), fontWeight = FontWeight.Bold, color = d.theme.accentColor, modifier = Modifier.padding(start = 8.dp))
                }
            }
            Text(d.time.date, fontSize = m.sp(13f), letterSpacing = 5.sp, color = d.theme.textSecondary, maxLines = 1)
            FaceChimeLine(d, m, Modifier.align(Alignment.CenterHorizontally))
            // World clocks as small flip cards.
            val cols = if (m.landscape) 6 else if (m.phone) 2 else 3
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.cities.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { c ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(d.theme.cardBackground)
                                    .lsCityGestures(d.toggleControls) { d.openCity(c) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(c.name.uppercase(), fontSize = m.sp(9f), letterSpacing = 1.2.sp, color = d.theme.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    c.time + if (c.ampm.isNotEmpty()) " ${c.ampm}" else "", fontSize = m.sp(18f), fontWeight = FontWeight.Bold,
                                    fontFamily = ChronometerDisplayFont, color = d.theme.numberColor, maxLines = 1
                                )
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// FACE: ORBIT - every city is a dot on its own ring, positioned by its local time of day
// ---------------------------------------------------------------------------------------------

@Composable
internal fun OrbitFace(m: LsMetrics, d: FaceData) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight * 1.15f
        val size = if (wide) minOf(maxHeight, maxWidth * 0.58f) else minOf(maxWidth, maxHeight * 0.6f)
        val th = d.theme

        @Composable
        fun Orbits() {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                val labelStyle = TextStyle(fontSize = dpSp(size * 0.032f), color = th.textSecondary, fontWeight = FontWeight.Medium)
                Canvas(Modifier.fillMaxSize()) {
                    val c = center
                    val r = this.size.minDimension / 2f
                    val n = d.cities.size.coerceAtLeast(1)
                    // Chime countdown as an outer arc.
                    drawArc(
                        th.accentColor, -90f, 360f * d.chime.progress, false,
                        Offset(c.x - r * 0.985f, c.y - r * 0.985f), Size(r * 1.97f, r * 1.97f), style = Stroke(r * 0.018f, cap = StrokeCap.Round)
                    )
                    d.cities.forEachIndexed { i, city ->
                        val ringR = r * (0.42f + 0.5f * (i + 1f) / n)
                        drawCircle(th.textPrimary.copy(alpha = 0.13f), radius = ringR, center = c, style = Stroke(r * 0.006f))
                        val zone = TimeAndAlertUtils.safeZoneId(city.friend.timezoneId)
                        val t = d.now.withZoneSameInstant(zone)
                        val frac = (t.hour * 60 + t.minute) / 1440f   // midnight at the top
                        val a = (frac * 2 * PI - PI / 2).toFloat()
                        val p = Offset(c.x + cos(a) * ringR, c.y + sin(a) * ringR)
                        if (city.home) drawLine(th.accentColor.copy(alpha = 0.35f), c, p, strokeWidth = r * 0.008f)
                        drawCircle(if (city.night) Color(0xFF8B9BFF) else th.accentColor, radius = r * (if (city.home) 0.045f else 0.032f), center = p)
                        val label = city.name.substringBefore(',').take(3).uppercase()
                        val layout = measurer.measure(label, labelStyle)
                        // Cities that share a time sit on one spoke, so alternate label sides to keep them apart.
                        val x = if (i % 2 == 0) p.x + r * 0.05f else p.x - r * 0.05f - layout.size.width
                        drawText(layout, topLeft = Offset(x, p.y - layout.size.height / 2f))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.time.hm, fontSize = dpSp(size * 0.15f), fontWeight = FontWeight.Light, color = th.textPrimary, fontFamily = ChronometerDisplayFont, maxLines = 1)
                    if (d.time.ampm.isNotEmpty()) Text(d.time.ampm, fontSize = dpSp(size * 0.045f), color = th.accentColor)
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Orbits()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(d.time.date, fontSize = m.sp(12f), letterSpacing = 4.sp, color = th.textSecondary, maxLines = 1)
                    FaceChimeLine(d, m)
                    FaceCityList(d, m, columns = 1)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Orbits()
                Text(d.time.date, fontSize = m.sp(12f), letterSpacing = 4.sp, color = th.textSecondary, maxLines = 1)
                FaceChimeLine(d, m)
                FaceCityList(d, m, columns = 2)
            }
        }
    }
}
