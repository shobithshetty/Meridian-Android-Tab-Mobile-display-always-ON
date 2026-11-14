package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * PerimeterPulseOverlay
 *
 * Ultra-HD, GPU draw-phase ambient screen border pulse animation triggered as a secondary
 * visual cue on interval chimes and alarms.
 *
 * Key visual & rendering refinements:
 * - Zero composition-phase reads during animation (animates strictly inside `drawBehind`).
 * - Concentric cubic-eased rounded contour strokes eliminate rectangular corner seams and
 *   8-bit linear gradient banding on high-resolution tablet/phone screens.
 * - Multi-corner radial blooms + traveling specular filament create a crisp, optical-grade rim glow.
 * - 100% pointer-transparent (`Spacer` with no clickable modifiers).
 */
@Composable
fun PerimeterPulseOverlay(
    isPulsing: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    pulseGeneration: Long = 0L
) {
    val envelopeAnim = remember { Animatable(0f) }
    val sweepAnim = remember { Animatable(0f) }

    LaunchedEffect(isPulsing, pulseGeneration) {
        if (isPulsing || pulseGeneration > 0L) {
            if (!isPulsing && envelopeAnim.value <= 0.005f) return@LaunchedEffect

            val riseEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
            val decayEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

            coroutineScope {
                launch {
                    sweepAnim.snapTo(0f)
                    sweepAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 1700, easing = LinearEasing)
                    )
                }
                launch {
                    envelopeAnim.snapTo(0f)
                    // Primary luminous crest
                    envelopeAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 190, easing = riseEasing)
                    )
                    // Gentle harmonic inhale dip
                    envelopeAnim.animateTo(
                        targetValue = 0.48f,
                        animationSpec = tween(durationMillis = 230, easing = FastOutSlowInEasing)
                    )
                    // Secondary resonant crest
                    envelopeAnim.animateTo(
                        targetValue = 0.88f,
                        animationSpec = tween(durationMillis = 220, easing = riseEasing)
                    )
                    // Silky exponential fade-out
                    envelopeAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 1040, easing = decayEasing)
                    )
                }
            }
        } else {
            envelopeAnim.animateTo(0f, animationSpec = tween(180))
        }
    }

    val specularCoreColor = remember(accentColor) {
        lerp(accentColor, Color.White, 0.58f)
    }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val envelope = envelopeAnim.value
                if (envelope <= 0.003f) return@drawBehind

                val sweepProgress = sweepAnim.value
                val width = size.width
                val height = size.height
                if (width <= 0f || height <= 0f) return@drawBehind

                val minSide = minOf(width, height)
                val maxGlowDepthPx = 68.dp.toPx().coerceAtMost(minSide * 0.14f)
                val outerCornerPx = 28.dp.toPx()

                // 1. Seamless Multi-Layer Concentric Contour Bloom (zero rectangular corner overlap seams)
                val haloLayers = 14
                for (i in haloLayers downTo 1) {
                    val t = i.toFloat() / haloLayers.toFloat() // 1.0 outer -> near 0 inner
                    val falloff = (1f - t).pow(2.35f)
                    val layerAlpha = (envelope * (0.015f + 0.115f * falloff)).coerceIn(0f, 0.45f)
                    val strokeWidth = (maxGlowDepthPx * t).coerceAtLeast(1.5.dp.toPx())
                    val inset = (strokeWidth * 0.5f).coerceAtMost(minSide * 0.45f)
                    val corner = (outerCornerPx * (1f - t * 0.32f)).coerceAtLeast(8.dp.toPx())

                    drawRoundRect(
                        color = accentColor.copy(alpha = layerAlpha),
                        topLeft = Offset(inset, inset),
                        size = Size(
                            (width - inset * 2f).coerceAtLeast(1f),
                            (height - inset * 2f).coerceAtLeast(1f)
                        ),
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = strokeWidth)
                    )
                }

                // 2. Four Smooth Corner Radial Blooms (multi-stop anti-banded illumination)
                val cornerRadiusPx = 180.dp.toPx().coerceAtMost(minSide * 0.42f)
                val cornerAlpha = (envelope * 0.28f).coerceIn(0f, 0.4f)
                val cornerCenters = listOf(
                    Offset(0f, 0f),
                    Offset(width, 0f),
                    Offset(0f, height),
                    Offset(width, height)
                )
                for (center in cornerCenters) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to specularCoreColor.copy(alpha = cornerAlpha),
                                0.28f to accentColor.copy(alpha = cornerAlpha * 0.72f),
                                0.58f to accentColor.copy(alpha = cornerAlpha * 0.24f),
                                0.85f to accentColor.copy(alpha = cornerAlpha * 0.04f),
                                1.0f to Color.Transparent
                            ),
                            center = center,
                            radius = cornerRadiusPx
                        ),
                        radius = cornerRadiusPx,
                        center = center
                    )
                }

                // 3. Rotating Directional Specular Sweep along the Perimeter Rim
                val angleRad = (sweepProgress * 2.0 * Math.PI).toFloat()
                val sweepStart = Offset(
                    x = width * (0.5f + 0.5f * cos(angleRad)),
                    y = height * (0.5f + 0.5f * sin(angleRad))
                )
                val sweepEnd = Offset(
                    x = width * (0.5f - 0.5f * cos(angleRad)),
                    y = height * (0.5f - 0.5f * sin(angleRad))
                )

                val sweepStrokePx = 5.5.dp.toPx()
                val sweepInset = sweepStrokePx / 2f
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            specularCoreColor.copy(alpha = (envelope * 0.92f).coerceIn(0f, 1f)),
                            accentColor.copy(alpha = (envelope * 0.55f).coerceIn(0f, 1f)),
                            accentColor.copy(alpha = (envelope * 0.22f).coerceIn(0f, 1f)),
                            specularCoreColor.copy(alpha = (envelope * 0.78f).coerceIn(0f, 1f))
                        ),
                        start = sweepStart,
                        end = sweepEnd
                    ),
                    topLeft = Offset(sweepInset, sweepInset),
                    size = Size(
                        (width - sweepStrokePx).coerceAtLeast(1f),
                        (height - sweepStrokePx).coerceAtLeast(1f)
                    ),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    style = Stroke(width = sweepStrokePx)
                )

                // 4. Ultra-Crisp Retina Hairline Specular Filament
                val hairlinePx = 1.6.dp.toPx()
                val hairlineInset = hairlinePx / 2f
                drawRoundRect(
                    color = specularCoreColor.copy(alpha = (envelope * 0.96f).coerceIn(0f, 1f)),
                    topLeft = Offset(hairlineInset, hairlineInset),
                    size = Size(
                        (width - hairlinePx).coerceAtLeast(1f),
                        (height - hairlinePx).coerceAtLeast(1f)
                    ),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    style = Stroke(width = hairlinePx)
                )
            }
    )
}
