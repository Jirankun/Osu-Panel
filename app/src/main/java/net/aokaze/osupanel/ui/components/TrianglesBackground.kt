/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) — Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════
// trianglesLine — outline/stroke triangles (original osu!lazer style)
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Segitiga lazer — replika `TrianglesV2` osu!lazer (outline/stroke).
 *
 * Two ways to use it:
 *
 * 1. **Modifier** — attach directly to any element:
 *    ```kotlin
 *    Box(Modifier.trianglesLine()) { ... }
 *    ```
 * 2. **Component** — fill it yourself with [Spacer]/[Box]:
 *    ```kotlin
 *    Box { TrianglesLine(Modifier.fillMaxSize()) }
 *    ```
 */
@Composable
fun Modifier.trianglesLine(
    color: Color = Color.White,
    alpha: Float = 0.45f,
    scaleAdjust: Float = 0.4f,
    velocity: Float = 0.8f,
    spawnRatio: Float = 2f,
    strokeWidth: Dp = 0.8.dp,
    depth: Float = 1f,
    fixedSizePx: Float? = null,
): Modifier {
    val particles = remember(scaleAdjust, color, depth) {
        val rng = Random(SEED)
        Array(MAX_PARTICLES) {
            val d = rng.nextFloat()
            val far = d * d
            val sizeF = lerp(1f, lerp(0.45f, 1.15f, far), depth)
            val bright = lerp(1f, lerp(0.35f, 1f, far), depth)
            val rgbF = lerp(1f, 0.82f, depth * far)
            TriangleParticle(
                x = rng.nextFloat(),
                speed = normalSpeed(rng),
                phase = rng.nextFloat(),
                sizeF = sizeF,
                bright = bright,
                color = Color(
                    red = (color.red * rgbF).coerceIn(0f, 1f),
                    green = (color.green * rgbF).coerceIn(0f, 1f),
                    blue = (color.blue * rgbF).coerceIn(0f, 1f),
                ),
            )
        }
    }
    val paths = remember { Array(MAX_PARTICLES) { Path() } }

    val transition = rememberInfiniteTransition(label = "trianglesTime")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = TIME_CYCLE_MS, easing = LinearEasing),
        ),
        label = "trianglesProgress",
    )

    return drawWithCache {
        val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt)
        onDrawBehind {
            drawTrianglesLine(
                timeMs = progress * TIME_CYCLE_MS,
                particles = particles,
                paths = paths,
                alpha = alpha,
                stroke = stroke,
                scaleAdjust = scaleAdjust,
                velocity = velocity,
                spawnRatio = spawnRatio,
                fixedSizePx = fixedSizePx,
            )
        }
    }
}

/** Standalone component — wraps [Modifier.trianglesLine] with [Spacer]. */
@Composable
fun TrianglesLine(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    alpha: Float = 0.45f,
    scaleAdjust: Float = 0.4f,
    velocity: Float = 0.8f,
    spawnRatio: Float = 2f,
    strokeWidth: Dp = 0.8.dp,
    depth: Float = 1f,
    fixedSizePx: Float? = null,
) {
    Spacer(
        modifier.trianglesLine(
            color = color,
            alpha = alpha,
            scaleAdjust = scaleAdjust,
            velocity = velocity,
            spawnRatio = spawnRatio,
            strokeWidth = strokeWidth,
            depth = depth,
            fixedSizePx = fixedSizePx,
        ),
    )
}

/** Draw outline/stroke triangles — the original trianglesLine implementation. */
private fun DrawScope.drawTrianglesLine(
    timeMs: Float,
    particles: Array<TriangleParticle>,
    paths: Array<Path>,
    alpha: Float,
    stroke: Stroke,
    scaleAdjust: Float,
    velocity: Float,
    spawnRatio: Float,
    fixedSizePx: Float? = null,
) {
    val unit = size.height / 60f
    val baseTriW = fixedSizePx ?: (100f * scaleAdjust * unit)
    val baseTriH = baseTriW * 0.866f
    val relH = baseTriH / size.height
    val rate = if (fixedSizePx != null) velocity * 50f / size.height else velocity * 50f / 60f
    val wrap = 1f + relH

    val count = if (fixedSizePx != null) {
        max(1, (size.width * 0.02f * spawnRatio).toInt())
            .coerceAtMost(MAX_PARTICLES)
    } else {
        val drawWidthUnits = size.width / unit
        max(1, (drawWidthUnits * 0.02f * spawnRatio).toInt())
            .coerceAtMost(MAX_PARTICLES)
    }

    for (i in 0 until count) {
        val p = particles[i]
        val travel = (timeMs / 1000f) * rate * max(0.5f, p.speed) + p.phase
        val y = (1f - (travel % wrap)) * size.height
        val x = p.x * size.width

        val triW = baseTriW * p.sizeF
        val triH = baseTriH * p.sizeF

        val fade = min(
            (size.height - y) / triH,
            (y + triH) / triH,
        ).coerceIn(0f, 1f)

        val path = paths[i]
        path.rewind()
        path.moveTo(x, y)
        path.lineTo(x + triW / 2f, y + triH)
        path.lineTo(x - triW / 2f, y + triH)
        path.close()
        drawPath(path, p.color, alpha = alpha * fade * p.bright, style = stroke)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// trianglesFill — filled triangles + shadow 3D floating effect
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Filled triangles with a 3D floating shadow effect.
 * Same shape as [trianglesLine] but solid-filled, denser, and with a drop
 * shadow behind each triangle for a "floating" look.
 *
 * Usage:
 * ```kotlin
 * Box(Modifier.trianglesFill()) { ... }
 * ```
 */
@Composable
fun Modifier.trianglesFill(
    color: Color = Color.White,
    alpha: Float = 0.35f,
    scaleAdjust: Float = 0.35f,
    velocity: Float = 0.7f,
    spawnRatio: Float = 3.5f,
    depth: Float = 1f,
    shadowAlpha: Float = 0.25f,
    shadowOffsetY: Float = 4f,
    fixedSizePx: Float? = null,
): Modifier {
    val particles = remember(scaleAdjust, color, depth) {
        val rng = Random(SEED + 7)
        Array(MAX_PARTICLES) {
            val d = rng.nextFloat()
            val far = d * d
            val sizeF = lerp(1f, lerp(0.45f, 1.15f, far), depth)
            val bright = lerp(1f, lerp(0.35f, 1f, far), depth)
            val rgbF = lerp(1f, 0.82f, depth * far)
            TriangleParticle(
                x = rng.nextFloat(),
                speed = normalSpeed(rng),
                phase = rng.nextFloat(),
                sizeF = sizeF,
                bright = bright,
                color = Color(
                    red = (color.red * rgbF).coerceIn(0f, 1f),
                    green = (color.green * rgbF).coerceIn(0f, 1f),
                    blue = (color.blue * rgbF).coerceIn(0f, 1f),
                ),
            )
        }
    }
    val paths = remember { Array(MAX_PARTICLES) { Path() } }

    val transition = rememberInfiniteTransition(label = "trianglesFillTime")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = TIME_CYCLE_MS, easing = LinearEasing),
        ),
        label = "trianglesFillProgress",
    )

    return drawWithCache {
        onDrawBehind {
            drawTrianglesFill(
                timeMs = progress * TIME_CYCLE_MS,
                particles = particles,
                paths = paths,
                alpha = alpha,
                scaleAdjust = scaleAdjust,
                velocity = velocity,
                spawnRatio = spawnRatio,
                shadowAlpha = shadowAlpha,
                shadowOffsetY = shadowOffsetY,
                fixedSizePx = fixedSizePx,
            )
        }
    }
}

/** Standalone component — wraps [Modifier.trianglesFill] with [Spacer]. */
@Composable
fun TrianglesFill(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    alpha: Float = 0.35f,
    scaleAdjust: Float = 0.35f,
    velocity: Float = 0.7f,
    spawnRatio: Float = 3.5f,
    depth: Float = 1f,
    shadowAlpha: Float = 0.25f,
    shadowOffsetY: Float = 4f,
    fixedSizePx: Float? = null,
) {
    Spacer(
        modifier.trianglesFill(
            color = color,
            alpha = alpha,
            scaleAdjust = scaleAdjust,
            velocity = velocity,
            spawnRatio = spawnRatio,
            depth = depth,
            shadowAlpha = shadowAlpha,
            shadowOffsetY = shadowOffsetY,
            fixedSizePx = fixedSizePx,
        ),
    )
}

/** Draw filled triangles with shadow — the trianglesFill implementation. */
private fun DrawScope.drawTrianglesFill(
    timeMs: Float,
    particles: Array<TriangleParticle>,
    paths: Array<Path>,
    alpha: Float,
    scaleAdjust: Float,
    velocity: Float,
    spawnRatio: Float,
    shadowAlpha: Float,
    shadowOffsetY: Float,
    fixedSizePx: Float? = null,
) {
    val unit = size.height / 60f
    val baseTriW = fixedSizePx ?: (100f * scaleAdjust * unit)
    val baseTriH = baseTriW * 0.866f
    val relH = baseTriH / size.height
    val rate = if (fixedSizePx != null) velocity * 50f / size.height else velocity * 50f / 60f
    val wrap = 1f + relH

    val count = if (fixedSizePx != null) {
        max(1, (size.width * 0.02f * spawnRatio).toInt())
            .coerceAtMost(MAX_PARTICLES)
    } else {
        val drawWidthUnits = size.width / unit
        max(1, (drawWidthUnits * 0.02f * spawnRatio).toInt())
            .coerceAtMost(MAX_PARTICLES)
    }

    for (i in 0 until count) {
        val p = particles[i]
        val travel = (timeMs / 1000f) * rate * max(0.5f, p.speed) + p.phase
        val y = (1f - (travel % wrap)) * size.height
        val x = p.x * size.width

        val triW = baseTriW * p.sizeF
        val triH = baseTriH * p.sizeF

        val fade = min(
            (size.height - y) / triH,
            (y + triH) / triH,
        ).coerceIn(0f, 1f)

        val path = paths[i]
        path.rewind()
        path.moveTo(x, y)
        path.lineTo(x + triW / 2f, y + triH)
        path.lineTo(x - triW / 2f, y + triH)
        path.close()

        val finalAlpha = alpha * fade * p.bright

        // Shadow — drawn offset below for 3D floating effect
        if (shadowAlpha > 0f && finalAlpha > 0.01f) {
            withTransform({
                translate(left = 0f, top = shadowOffsetY * p.sizeF)
            }) {
                drawPath(
                    path,
                    Color.Black,
                    alpha = shadowAlpha * fade * p.bright,
                )
            }
        }

        // Filled triangle
        drawPath(path, p.color, alpha = finalAlpha)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Shared internals
// ═══════════════════════════════════════════════════════════════════════════

/** Attributes of one triangle particle (static — only the y position changes). */
private data class TriangleParticle(
    val x: Float,
    val speed: Float,
    val phase: Float,
    val sizeF: Float,
    val bright: Float,
    val color: Color,
)

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun normalSpeed(rng: Random): Float {
    val u1 = 1f - rng.nextFloat()
    val u2 = 1f - rng.nextFloat()
    val randStdNormal = sqrt(-2.0 * ln(u1.toDouble())) * sin(2.0 * PI * u2)
    return max(0.1f, (0.5f + 0.16f * randStdNormal.toFloat()))
}

private const val SEED = 64140L
private const val MAX_PARTICLES = 64
private const val TIME_CYCLE_MS = 120_000
