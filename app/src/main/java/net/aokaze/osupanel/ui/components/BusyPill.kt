/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Lazer pill button with the app's signature loading, same style as the
 * Login/Save buttons:
 *
 * - **Idle** — [idleLabel] (+ optional [leadingIcon] on the left).
 * - **Busy** — the osu! [OsuSpinner] sits on the LEFT of [busyLabel]
 *   ("Saving…", "Uploading…", "Logging in…") and the button is disabled.
 * - **Result** — when [result] becomes non-null the [successLabel] /
 *   [failureLabel] shows IN the button (green success / red failure), holds
 *   for a moment, then fades out back to the idle label.
 */
@Composable
fun BusyPill(
    idleLabel: String,
    busyLabel: String,
    busy: Boolean,
    result: Boolean?,
    successLabel: String,
    failureLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    fillWidth: Boolean = true,
    fontSize: TextUnit = MaterialTheme.typography.bodyLarge.fontSize,
    leadingIcon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val colorScheme = MaterialTheme.colorScheme

    // Result display state — shows the outcome in the button, holds, fades.
    var displayResult by remember { mutableStateOf<Boolean?>(null) }
    var fading by remember { mutableStateOf(false) }
    LaunchedEffect(result) {
        if (result != null) {
            displayResult = result
            fading = false
            delay(RESULT_HOLD_MS)
            fading = true
            delay(RESULT_FADE_MS)
            displayResult = null
        }
    }
    val resultAlpha by animateFloatAsState(
        targetValue = if (fading) 0f else 1f,
        animationSpec = tween(RESULT_FADE_MS.toInt(), easing = FastOutSlowInEasing),
        label = "busyPillResultAlpha",
    )

    val showingResult = displayResult != null
    val isSuccess = displayResult == true
    val background = when {
        showingResult -> if (isSuccess) colorScheme.primary else colorScheme.error
        busy -> color.copy(alpha = 0.4f)
        else -> color
    }
    val label = when {
        showingResult -> if (isSuccess) successLabel else failureLabel
        busy -> busyLabel
        else -> idleLabel
    }

    Surface(
        onClick = onClick,
        enabled = enabled && !busy && !showingResult,
        shape = RoundedCornerShape(50),
        color = background,
        contentColor = if (showingResult && !isSuccess) colorScheme.onError else contentColor,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
            .height(height),
    ) {
        // Laser triangles — same as every lazer pill in the app.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .trianglesLine(alpha = 0.4f, scaleAdjust = 0.35f, spawnRatio = 2.5f),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = if (showingResult) resultAlpha else 1f }
                    .then(if (fillWidth) Modifier.fillMaxSize() else Modifier.wrapContentWidth()),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    // Signature loading: spinner on the LEFT of the label.
                    busy -> {
                        OsuSpinner(size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    // Idle leading icon (e.g. the camera on "Change photo").
                    leadingIcon != null && !showingResult -> {
                        Icon(
                            leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    // Success check mark in front of the success label.
                    showingResult && isSuccess -> {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = fontSize)
            }
        }
    }
}

private const val RESULT_HOLD_MS = 1_600L
private const val RESULT_FADE_MS = 700L
