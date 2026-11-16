package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import com.example.ui.theme.ClockVisualTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Stable names stored in ClockSettings.backdropPattern. Works with every colour theme and layout style. */
object Backdrop {
    const val NONE = "NONE"
    const val CAMO = "CAMO"

    val options: List<Triple<String, String, String>> = listOf(
        Triple(NONE, "Plain", "Just the theme colours"),
        Triple(CAMO, "Camo", "Soft camouflage in the theme colours")
    )
}

/** One irregular camo patch, in 0..1 screen coordinates so it scales to any screen size. */
private class CamoBlob(val cx: Float, val cy: Float, val radius: Float, val layer: Int, val wobble: List<Float>)

private fun buildCamo(): List<CamoBlob> {
    val rnd = Random(20261115) // fixed seed: the pattern never changes between launches
    return List(30) { i ->
        CamoBlob(
            cx = rnd.nextFloat(), cy = rnd.nextFloat(),
            radius = 0.08f + rnd.nextFloat() * 0.12f,
            layer = i % 3,
            wobble = List(9) { 0.6f + rnd.nextFloat() * 0.6f }
        )
    }
}

/**
 * A faint camouflage pattern drawn over the clock. It uses the active theme's accent, number and dark tones,
 * stays at low opacity so digits remain readable, and has no pointer handling so every tap passes through.
 */
@Composable
fun CamoOverlay(theme: ClockVisualTheme, modifier: Modifier = Modifier) {
    val blobs = remember { buildCamo() }
    val layerColors = listOf(
        theme.accentColor.copy(alpha = 0.06f),
        theme.numberColor.copy(alpha = 0.04f),
        Color.Black.copy(alpha = 0.12f)
    )
    Canvas(modifier.fillMaxSize()) {
        val scale = size.maxDimension
        blobs.forEach { b ->
            val n = b.wobble.size
            val pts = List(n) { k ->
                val a = (2.0 * PI * k / n).toFloat()
                val r = b.radius * scale * b.wobble[k]
                androidx.compose.ui.geometry.Offset(b.cx * size.width + cos(a) * r, b.cy * size.height + sin(a) * r * 0.75f)
            }
            val path = Path()
            // Smooth closed curve: quadratic segments through the midpoints of neighbouring points.
            fun mid(p: androidx.compose.ui.geometry.Offset, q: androidx.compose.ui.geometry.Offset) =
                androidx.compose.ui.geometry.Offset((p.x + q.x) / 2f, (p.y + q.y) / 2f)
            val start = mid(pts[n - 1], pts[0])
            path.moveTo(start.x, start.y)
            for (k in 0 until n) {
                val m = mid(pts[k], pts[(k + 1) % n])
                path.quadraticTo(pts[k].x, pts[k].y, m.x, m.y)
            }
            path.close()
            drawPath(path, layerColors[b.layer])
        }
    }
}
