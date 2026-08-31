/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Shared 6-axis skills radar — the ONE radar shape used by the dashboard and
 * the profile skills card, so both always show the same osu!skills
 * (osuskills.com — the source used by osu-stats-signature) with the same
 * values: STA/ACC/PRE/REA/AGI/TEN.
 *
 * [values] = 0..100 per axis, [labels] = the 6 axis labels.
 */
@Composable
fun SkillRadar(
    values: List<Float>,
    labels: List<String>,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    Canvas(modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = min(size.width, size.height) / 2f
        val r = maxR * 0.70f

        // Rings at 25 / 50 / 75 / 100%.
        for (ring in 1..4) {
            val rr = r * ring / 4f
            val ringPath = Path()
            for (i in 0..6) {
                val (x, y) = radarVertex(cx, cy, rr, i)
                if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
            }
            ringPath.close()
            drawPath(
                ringPath,
                color = scheme.outlineVariant.copy(alpha = 0.6f),
                style = Stroke(width = 1f),
            )
        }

        // Axis lines origin → vertex.
        for (i in 0 until 6) {
            val (x, y) = radarVertex(cx, cy, r, i)
            drawLine(
                color = scheme.outlineVariant.copy(alpha = 0.5f),
                start = Offset(cx, cy),
                end = Offset(x, y),
                strokeWidth = 1f,
            )
        }

        // Skill polygon — radius per axis = value / 100.
        val poly = Path()
        for (i in 0..6) {
            val percent = (values[i % 6].coerceIn(0f, 100f) / 100f)
            val (x, y) = radarVertex(cx, cy, r * percent, i)
            if (i == 0) poly.moveTo(x, y) else poly.lineTo(x, y)
        }
        poly.close()
        drawPath(poly, color = tint.copy(alpha = 0.32f))
        drawPath(poly, color = tint, style = Stroke(width = 2f))

        // Axis labels (native canvas text — same as the widget radar).
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = scheme.onSurfaceVariant.toArgb()
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        labels.forEachIndexed { i, label ->
            val (x, y) = radarVertex(cx, cy, r * 1.2f, i)
            drawContext.canvas.nativeCanvas.drawText(label, x, y + paint.textSize / 3f, paint)
        }
    }
}

/** The i-th hexagon point — first vertex at top-left (-120°), 60° steps. */
private fun radarVertex(cx: Float, cy: Float, radius: Float, i: Int): Pair<Float, Float> {
    val angle = (-120f + i * 60f) / 180f * PI.toFloat()
    return Pair(cx + cos(angle) * radius, cy + sin(angle) * radius)
}
