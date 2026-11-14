package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ClockSettings
import com.example.ui.ClockViewModel
import com.example.ui.theme.ChronometerMonoFont
import com.example.ui.theme.ClockVisualTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PacedBreathingBubble(
    settings: ClockSettings,
    activeTheme: ClockVisualTheme,
    viewModel: ClockViewModel,
    modifier: Modifier = Modifier,
    forceExpanded: Boolean = false
) {
    val isExpanded = settings.showBreathingGuide || forceExpanded

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = activeTheme.cardBackground),
        shape = RoundedCornerShape(20.dp),
        border = hdBorderStroke(
            accentColor = activeTheme.accentColor,
            dividerColor = activeTheme.dividerColor,
            width = 1.1.dp
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(enabled = !forceExpanded) { viewModel.toggleBreathingGuide(!isExpanded) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Breathing focus icon",
                        tint = activeTheme.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BREATHING ANCHOR ASSIST",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = activeTheme.accentColor
                    )
                }

                if (!forceExpanded) {
                    Text(
                        text = if (isExpanded) "CLOSE" else "OPEN GUIDED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeTheme.accentColor
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Inhale focus, exhale urgency. Let's align with a continuous 4-second box breath:",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = activeTheme.textSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Continuous 60/120fps 16-second box-breathing cycle (4s Inhale -> 4s Hold -> 4s Exhale -> 4s Hold)
                    val infiniteTransition = rememberInfiniteTransition(label = "BoxBreathCycle")
                    val cycleProgress by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 16f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 16000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "BoxBreathProgressSeconds"
                    )

                    val phase = (cycleProgress / 4f).toInt().coerceIn(0, 3)
                    val localPhaseFraction = ((cycleProgress % 4f) / 4f).coerceIn(0f, 1f)
                    val countdownSec = (4 - (cycleProgress % 4f).toInt()).coerceIn(1, 4)

                    val phaseText = when (phase) {
                        0 -> "INHALE DEEPLY..."
                        1 -> "HOLD..."
                        2 -> "EXHALE SLOWLY..."
                        else -> "HOLD..."
                    }

                    // Smooth sine-in-out curve for zero-jerk motion
                    fun smoothSine(t: Float): Float = (0.5f - 0.5f * cos(t * PI)).toFloat()

                    val breathScale = when (phase) {
                        0 -> 0.58f + 0.42f * smoothSine(localPhaseFraction)
                        1 -> 1.0f
                        2 -> 1.0f - 0.42f * smoothSine(localPhaseFraction)
                        else -> 0.58f
                    }

                    val highlightColor = remember(activeTheme.accentColor) {
                        lerp(activeTheme.accentColor, Color.White, 0.55f)
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(156.dp)
                            .fillMaxWidth()
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = this.center
                            val maxRadius = (size.minDimension * 0.46f).coerceAtLeast(24f)
                            val orbRadius = maxRadius * breathScale

                            // Outer ambient aura
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.0f to activeTheme.accentColor.copy(alpha = 0.28f),
                                        0.55f to activeTheme.accentColor.copy(alpha = 0.12f),
                                        0.85f to activeTheme.accentColor.copy(alpha = 0.03f),
                                        1.0f to Color.Transparent
                                    ),
                                    center = center,
                                    radius = orbRadius * 1.38f
                                ),
                                radius = orbRadius * 1.38f,
                                center = center
                            )

                            // Concentric anti-aliased harmonic rings
                            drawCircle(
                                color = activeTheme.accentColor.copy(alpha = 0.30f),
                                radius = orbRadius * 1.14f,
                                center = center,
                                style = Stroke(width = 1.5.dp.toPx())
                            )

                            // Multi-stop luminous orb body (zero color banding)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.0f to highlightColor,
                                        0.48f to activeTheme.accentColor,
                                        0.88f to activeTheme.accentColor.copy(alpha = 0.72f),
                                        1.0f to activeTheme.accentColor.copy(alpha = 0.38f)
                                    ),
                                    center = center,
                                    radius = orbRadius
                                ),
                                radius = orbRadius,
                                center = center
                            )

                            // Precision 16-second progress arc + 4 quadrant hairline ticks around outer perimeter
                            val ringRadius = maxRadius * 1.05f
                            for (q in 0 until 4) {
                                val tickAngle = (q * 90.0 - 90.0) * PI / 180.0
                                val innerTick = ringRadius - 3.dp.toPx()
                                val outerTick = ringRadius + 3.dp.toPx()
                                val cA = cos(tickAngle).toFloat()
                                val sA = sin(tickAngle).toFloat()
                                drawLine(
                                    color = activeTheme.dividerColor.copy(alpha = 0.7f),
                                    start = androidx.compose.ui.geometry.Offset(center.x + innerTick * cA, center.y + innerTick * sA),
                                    end = androidx.compose.ui.geometry.Offset(center.x + outerTick * cA, center.y + outerTick * sA),
                                    strokeWidth = 1.4.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            // Crisp 1.2dp specular inner rim around orb body
                            drawCircle(
                                color = highlightColor.copy(alpha = 0.55f),
                                radius = orbRadius,
                                center = center,
                                style = Stroke(width = 1.2.dp.toPx())
                            )

                            drawArc(
                                color = highlightColor.copy(alpha = 0.78f),
                                startAngle = -90f,
                                sweepAngle = (cycleProgress / 16f) * 360f,
                                useCenter = false,
                                topLeft = center - androidx.compose.ui.geometry.Offset(ringRadius, ringRadius),
                                size = androidx.compose.ui.geometry.Size(ringRadius * 2f, ringRadius * 2f),
                                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Text(
                            text = "${countdownSec}s",
                            color = activeTheme.onAccentColor,
                            fontSize = (18f * (0.75f + 0.35f * breathScale)).sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = ChronometerMonoFont,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }

                    Text(
                        text = phaseText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = activeTheme.accentColor,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}
