/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Segitiga lazer — replika `TrianglesV2` osu!lazer
 * (osu.Game/Graphics/Backgrounds/TrianglesV2.cs), MODULAR: it can be
 * attached to any element — buttons, cards, panels, banners, etc.
 *
 * Two ways to use it (identical draw logic, one source):
 *
 * 1. **Modifier** — attach directly to any element:
 *    ```kotlin
 *    Box(Modifier.trianglesBackground()) { ... }
 *    ```
 * 2. **Component** — fill it yourself with [Spacer]/[Box] inside
 *    the container (used by the loading spinner):
 *    ```kotlin
 *    Box { TrianglesBackground(Modifier.fillMaxSize()) }
 *    ```
 *
 * Original algorithm (exactly from the ppy/osu repo):
 * - Bordered equilateral triangles, tip pointing up.
 * - Triangle size = `100 × ScaleAdjust` units (0.866 = equilateral ratio);
 *   1 unit = container height / 60 (local space matches the spinner box).
 * - Jumlah partikel `AimCount = DrawWidth(unit) × 0.02 × SpawnRatio`
 *   (linear to width — a 60-unit box → 2, a wide container → more).
 * - Each particle's speed = Box-Muller normal distribution N(0.5, 0.16²),
 *   min 0.1; movement uses `max(0.5, speed)`.
 * - Upward drift: `Velocity × base_velocity(50) / DrawHeight(60)` rel/s.
 * - Particles spawn at the bottom (y=1), disappear after leaving the top
 *   (y < −height/60), then respawn — a continuous cycle.
 * - Smooth edge fading: alpha ramps up over one triangle height
 *   at the bottom edge (spawn) and fades down at the top edge (exit) —
 *   no sudden "boom" respawns or abrupt disappearances at the edges.
 *
 * DEPTH effect (cheating logic — no real 3D):
 * - Each particle gets a random "distance" 0..1; the `d*d` distribution
 *   biases toward far → many small dim triangles, a few big prominent ones
 *   (depth arrangement like the osu! background).
 * - **Size (layout)**: far = 45% size, near = 115% (perspective).
 * - **Brightness**: far = 35% alpha, near = 100%.
 * - **Color**: far ones are faded (RGB × 0.82 toward dark — an atmospheric
 *   trick); near ones keep full color.
 * - Disable with `depth = 0f` → all particles uniform (legacy behavior).
 *
 * Performance 90fps+:
 * - Paths are reused (fixed array, `rewind()` each frame) — ZERO allocation
 *   per frame (no new Path/Stroke per particle).
 * - Per-particle color (already depth-faded) is built ONCE when the particle
 *   is created; per-frame alpha goes through the `drawPath.alpha` parameter
 *   — NO Color allocation per frame.
 * - Time from `rememberInfiniteTransition`, which guarantees frames keep
 *   coming (never stalls while the screen is idle).
 */
@Composable
fun Modifier.trianglesBackground(
    color: Color = Color.White,
    alpha: Float = 0.45f,
    scaleAdjust: Float = 0.4f,
    velocity: Float = 0.8f,
    spawnRatio: Float = 2f,
    strokeWidth: Dp = 0.8.dp,
    depth: Float = 1f,
): Modifier {
    // Particle attributes + path — built ONCE (stable, no per-frame allocation).
    // `depth` controls the depth effect strength (0 = flat/uniform).
    val particles = remember(scaleAdjust, color, depth) {
        val rng = Random(SEED)
        Array(MAX_PARTICLES) {
            val d = rng.nextFloat()
            val far = d * d                       // biased toward "far" (many small, dim ones)
            val sizeF = lerp(1f, lerp(0.45f, 1.15f, far), depth)   // perspective size
            val bright = lerp(1f, lerp(0.35f, 1f, far), depth)     // brightness
            val rgbF = lerp(1f, 0.82f, depth * far)                // color fade (far)
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

    // Time (ms) — the infinite transition GUARANTEES frames keep being produced.
    // 120-second cycle — long enough that the wrap is invisible.
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
            drawTriangles(
                timeMs = progress * TIME_CYCLE_MS,
                particles = particles,
                paths = paths,
                alpha = alpha,
                stroke = stroke,
                scaleAdjust = scaleAdjust,
                velocity = velocity,
                spawnRatio = spawnRatio,
            )
        }
    }
}

/**
 * Standalone component — wraps [Modifier.trianglesBackground] with
 * [Spacer]. Used when triangles are filled from outside the container
 * (e.g. inside the loading spinner box via `Modifier.fillMaxSize()`).
 */
@Composable
fun TrianglesBackground(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    alpha: Float = 0.45f,
    scaleAdjust: Float = 0.4f,
    velocity: Float = 0.8f,
    spawnRatio: Float = 2f,
    strokeWidth: Dp = 0.8.dp,
    depth: Float = 1f,
) {
    Spacer(
        modifier.trianglesBackground(
            color = color,
            alpha = alpha,
            scaleAdjust = scaleAdjust,
            velocity = velocity,
            spawnRatio = spawnRatio,
            strokeWidth = strokeWidth,
            depth = depth,
        ),
    )
}

/** The single draw implementation — used by both the modifier & component. */
private fun DrawScope.drawTriangles(
    timeMs: Float,
    particles: Array<TriangleParticle>,
    paths: Array<Path>,
    alpha: Float,
    stroke: Stroke,
    scaleAdjust: Float,
    velocity: Float,
    spawnRatio: Float,
) {
    val unit = size.height / 60f                       // 1 unit = 1/60 tinggi
    val baseTriW = 100f * scaleAdjust * unit           // base triangle quad width
    val baseTriH = 100f * scaleAdjust * 0.866f * unit  // base triangle quad height
    val relH = baseTriH / size.height                  // tinggi relatif (wrap)
    val rate = velocity * 50f / 60f                    // rel/detik (DrawHeight=60)
    val wrap = 1f + relH

    // AimCount persis TrianglesV2: DrawWidth(unit) × 0.02 × SpawnRatio.
    val drawWidthUnits = size.width / unit
    val count = max(1, (drawWidthUnits * 0.02f * spawnRatio).toInt())
        .coerceAtMost(MAX_PARTICLES)

    for (i in 0 until count) {
        val p = particles[i]
        val travel = (timeMs / 1000f) * rate * max(0.5f, p.speed) + p.phase
        val y = (1f - (travel % wrap)) * size.height
        val x = p.x * size.width

        // Depth — perspective size per particle (near big, far small).
        val triW = baseTriW * p.sizeF
        val triH = baseTriH * p.sizeF

        // Smooth edge fade — alpha ramps up over one triangle
        // height at the bottom edge (spawn) and fades down at the top edge
        // (exit). The fade zone follows the particle size (bigger triangles
        // fade longer). Eliminates sudden "boom" respawns.
        val fade = min(
            (size.height - y) / triH,   // fade-in at the bottom (spawn)
            (y + triH) / triH,          // fade-out at the top (exit)
        ).coerceIn(0f, 1f)

        // Path reuse — no per-frame allocation.
        val path = paths[i]
        path.rewind()
        path.moveTo(x, y)
        path.lineTo(x + triW / 2f, y + triH)
        path.lineTo(x - triW / 2f, y + triH)
        path.close()
        // Depth: alpha = base × edge fade × particle brightness.
        // Particle colors are already depth-faded (built once); alpha
        // goes through the drawPath parameter → no per-frame allocation.
        drawPath(path, p.color, alpha = alpha * fade * p.bright, style = stroke)
    }
}

/** Attributes of one triangle particle (static — only the y position changes). */
private data class TriangleParticle(
    val x: Float,
    val speed: Float,
    val phase: Float,
    val sizeF: Float,     // perspective size factor (depth)
    val bright: Float,    // brightness factor (depth)
    val color: Color,     // color already depth-faded (built once)
)

/** Simple float lerp (no extra dependencies). */
private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Speed multiplier persis `CreateTriangle` — Box-Muller N(0.5, 0.16²), min 0.1. */
private fun normalSpeed(rng: Random): Float {
    val u1 = 1f - rng.nextFloat()   // uniform (0,1]
    val u2 = 1f - rng.nextFloat()
    val randStdNormal = sqrt(-2.0 * ln(u1.toDouble())) * sin(2.0 * PI * u2)
    return max(0.1f, (0.5f + 0.16f * randStdNormal.toFloat()))
}

private const val SEED = 64140L
private const val MAX_PARTICLES = 64
private const val TIME_CYCLE_MS = 120_000
