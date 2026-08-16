/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import net.aokaze.osupanel.R

/**
 * Spinner khas osu! — replika persis `LoadingSpinner` osu!lazer
 * (osu.Game/Graphics/UserInterface/LoadingSpinner.cs) varian `withBox`.
 *
 * Analisis repo asli ppy/osu + ppy/osu-framework:
 * - **Lazer box**: kotak hitam membulat (corner = width/4, alpha 0.7) di
 *   behind the circle-notch — counterpart of `MainContents` (masking + black box).
 * - **Box BERPUTAR** mengikuti beat kick: `MainContents.RotateTo(+90°,
 *   beatLength, InOutQuart)` each beat. While idle (no music)
 *   `BeatSyncedContainer` uses `TimingControlPoint.DEFAULT` (60 BPM)
 *   → kick **+90° per 1000 ms** with InOutQuart.
 * - **TrianglesV2** inside the box (see [TrianglesBackground]) which
 *   **counter-rotate** (`triangles.Rotation = -MainContents.Rotation`)
 *   so its orientation stays screen-relative.
 * - **Circle-notch**: glyph FontAwesome 5 Solid `circle-notch`, skala 0.6
 *   of the box, `Spin(3150ms, Clockwise)` — constant 360°/3150 ms rotation.
 *   Total rotasi ikon = kick (box) + spin (ikon).
 * - The icon color is custom **pink** (osu! Panel brand) — unlike the game's
 *   white (`Color4.White`).
 * - PopIn: fade 0→0.01 (50 ms), then 0.01→1 (500 ms, OutQuint).
 *
 * Global usage — just import and call:
 * ```
 * OsuSpinner()                        // 40.dp, lazer box + circle pink
 * OsuSpinner(size = 20.dp)            // small — inside buttons
 * OsuSpinner(withBox = false)         // no box, circle only
 * OsuSpinner(size = 48.dp, color = Color.White)  // custom icon color
 * ```
 */
@Composable
fun OsuSpinner(
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    withBox: Boolean = true,
) {
    // Default: custom pink (unlike the game's white); the color is overridable.
    val spinnerColor = if (color == Color.Unspecified) {
        colorResource(R.color.osu_pink)
    } else {
        color
    }
    val boxColor = colorResource(R.color.osu_loading_box)

    val glyphPath = remember { parseCircleNotchPath() }

    // Time (ms) — from an infinite transition that GUARANTEES frames keep
    // coming (withFrameNanos can stall while the screen is idle). 63000 ms cycle:
    // exact multiples of 3150 ms (spin) and 1000 ms (beat) → seamless wrap
    // (spin = 20 turns, kick = 63 steps × 90° ≡ 0° mod 360).
    val transition = rememberInfiniteTransition(label = "osuSpinnerTime")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = OSU_TIME_CYCLE_MS, easing = LinearEasing),
        ),
        label = "osuSpinnerProgress",
    )
    val timeMs = progress * OSU_TIME_CYCLE_MS

    // PopIn exactly like osu!lazer: fade 0→0.01 (50 ms), then 0.01→1 (500 ms, OutQuint).
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.snapTo(0f)
        alpha.animateTo(0.01f, animationSpec = tween(50))
        alpha.animateTo(1f, animationSpec = tween(500, easing = EaseOutQuint))
    }

    // ── Exact osu!lazer LoadingSpinner motion (idle, no track) ──
    // 1) Constant spin: 360° per 3150 ms clockwise.
    val spinAngle = (timeMs / 3150f) * 360f
    // 2) Beat kick: +90° per 1000 ms with InOutQuart.
    val kickAngle = (timeMs / 1000f).toInt() * 90f + inOutQuart((timeMs % 1000f) / 1000f) * 90f

    // Corner radius box persis game: DrawWidth / 4.
    val boxShape = RoundedCornerShape(size / 4f)

    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha.value }
            .size(size),
    ) {
        if (withBox) {
            // ── Lazer box (MainContents) — BERPUTAR via beat kick ──
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = kickAngle }
                    .clip(boxShape)
                    .background(boxColor.copy(alpha = 0.7f)),
            ) {
                // TrianglesV2 — counter-rotates so its orientation stays
                // screen-relative (triangles.Rotation = -MainContents.Rotation).
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = -kickAngle },
                ) {
                    TrianglesBackground(Modifier.fillMaxSize())
                }
            }
        }

        // ── Circle-notch — constant spin + kick, scaled 0.6 of the box ──
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = kickAngle + spinAngle },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val boxPx = minOf(this.size.width, this.size.height)
                val center = Offset(boxPx / 2f, boxPx / 2f)
                val iconScale = boxPx * 0.6f / 512f
                translate(center.x, center.y) {
                    scale(iconScale, iconScale, pivot = Offset.Zero) {
                        // Center the glyph's bounding box (≈256, 257.9).
                        translate(-FA5_CENTER_X, -FA5_CENTER_Y) {
                            drawPath(glyphPath, color = spinnerColor)
                        }
                    }
                }
            }
        }
    }
}

private const val OSU_TIME_CYCLE_MS = 63_000

// Bounding box path FA5 circle-notch: x ∈ [8.003, 504], y ∈ [11.889, 504].
private const val FA5_CENTER_X = 256f
private const val FA5_CENTER_Y = 257.9f

/**
 * Path asli Font Awesome 5 Solid `circle-notch` (codepoint f1ce) — glyph
 * the same one osu!lazer renders via SpriteIcon. Taken from
 * repo FontAwesome 5.15.4 (svgs/solid/circle-notch.svg), viewBox 512×512.
 */
private const val FA5_CIRCLE_NOTCH =
    "M288 39.056v16.659c0 10.804 7.281 20.159 17.686 23.066C383.204 100.434 440 171.518 440 256" +
        "c0 101.689-82.295 184-184 184-101.689 0-184-82.295-184-184 0-84.47 56.786-155.564 134.312-177.219" +
        "C216.719 75.874 224 66.517 224 55.712V39.064c0-15.709-14.834-27.153-30.046-23.234" +
        "C86.603 43.482 7.394 141.206 8.003 257.332c.72 137.052 111.477 246.956 248.531 246.667" +
        "C393.255 503.711 504 392.788 504 256c0-115.633-79.14-212.779-186.211-240.236" +
        "C302.678 11.889 288 23.456 288 39.056z"

private fun parseCircleNotchPath(): Path =
    PathParser().parsePathString(FA5_CIRCLE_NOTCH).toPath()

/** OutQuint easing — counterpart of osu.Framework `Easing.OutQuint`. */
private val EaseOutQuint: Easing = object : Easing {
    override fun transform(fraction: Float): Float = 1f - (1f - fraction).pow(5)
}

/** InOutQuart easing — counterpart of osu.Framework `Easing.InOutQuart`. */
private fun inOutQuart(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return if (x < 0.5f) {
        8f * x * x * x * x
    } else {
        1f - ((-2f * x + 2f).pow(4)) / 2f
    }
}
