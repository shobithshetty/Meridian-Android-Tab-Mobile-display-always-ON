package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Crisp solid architectural border stroke with zero whitish glass gradient haze.
 */
fun hdBorderStroke(
    accentColor: Color,
    dividerColor: Color,
    width: Dp = 1.dp,
    highlighted: Boolean = false
): BorderStroke {
    val borderColor = if (highlighted) accentColor else dividerColor
    return BorderStroke(
        width = width,
        color = borderColor
    )
}

/**
 * Clean, zero-haze display backdrop (true solid #000000 when bgStart == bgEnd, or clean vertical gradient
 * with zero radial mist overlay).
 */
fun Modifier.hdBackdropSurface(
    bgStart: Color,
    bgEnd: Color,
    accentColor: Color
): Modifier = this.drawBehind {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return@drawBehind

    if (bgStart == bgEnd) {
        drawRect(color = bgStart)
    } else {
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to bgStart,
                    0.50f to lerp(bgStart, bgEnd, 0.50f),
                    1.0f to bgEnd
                )
            )
        )
    }
}

/**
 * Precision Canvas-rendered countdown ring with crisp hairline tick markers,
 * anti-aliased gradient progress arc, and a luminous active tip dot.
 */
@Composable
fun HdPrecisionCountdownRing(
    progress: Float,
    trackColor: Color,
    indicatorColor: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.5.dp,
    showTicks: Boolean = true
) {
    val clamped = progress.coerceIn(0f, 1f)
    val tipHighlight = remember(indicatorColor) { lerp(indicatorColor, Color.White, 0.55f) }

    Canvas(modifier = modifier.fillMaxSize()) {
        val strokePx = strokeWidth.toPx()
        val inset = strokePx / 2f + 1.5.dp.toPx()
        val arcDiameter = (size.minDimension - inset * 2f).coerceAtLeast(4f)
        val topLeft = Offset(
            (size.width - arcDiameter) / 2f,
            (size.height - arcDiameter) / 2f
        )
        val arcSize = Size(arcDiameter, arcDiameter)
        val radius = arcDiameter / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Subtle crisp perimeter track
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx)
        )

        // Crisp 12-hour precision hairline tick marks around inner ring
        if (showTicks && radius > 12f) {
            val tickOuter = radius - strokePx * 0.85f
            val tickInner = (tickOuter - 2.2.dp.toPx()).coerceAtLeast(2f)
            for (i in 0 until 12) {
                val angle = (i * 30.0 - 90.0) * PI / 180.0
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                drawLine(
                    color = trackColor.copy(alpha = if (i % 3 == 0) 0.85f else 0.45f),
                    start = Offset(center.x + tickInner * cosA, center.y + tickInner * sinA),
                    end = Offset(center.x + tickOuter * cosA, center.y + tickOuter * sinA),
                    strokeWidth = if (i % 3 == 0) 1.2.dp.toPx() else 0.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        if (clamped > 0.002f) {
            val sweep = clamped * 360f
            drawArc(
                color = indicatorColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Crisp specular endpoint dot
            val endAngleRad = ((-90f + sweep) * PI / 180.0)
            val tipPos = Offset(
                x = center.x + radius * cos(endAngleRad).toFloat(),
                y = center.y + radius * sin(endAngleRad).toFloat()
            )
            drawCircle(
                color = tipHighlight,
                radius = strokePx * 0.48f,
                center = tipPos
            )
        }
    }
}
