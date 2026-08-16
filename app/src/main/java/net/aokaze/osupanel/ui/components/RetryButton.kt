/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.aokaze.osupanel.R

/**
 * Full-screen error retry button — the same lazer style as the Login button:
 * pill shape + TrianglesV2 background.
 *
 * - While [isLoading] a spinner sits on the RIGHT of the label and the
 *   button is disabled (loading is confined to the button — no full-screen
 *   spinner).
 * - If the load runs past [timeoutMillis] (10s) the spinner stops and the
 *   label switches to "Retrying..."; tapping again restarts the load.
 *
 * Shared by the Beatmap detail and Profile error screens (one implementation).
 */
@Composable
fun RetryButton(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    timeoutMillis: Long = 10_000,
) {
    val colorScheme = MaterialTheme.colorScheme

    // 10s timeout: stop the loading indicator on the button and switch the
    // label to "Retrying...". Restarts whenever the load state changes.
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) {
            timedOut = false
            delay(timeoutMillis)
            if (isLoading) timedOut = true
        }
    }
    val loading = isLoading && !timedOut

    Surface(
        onClick = onClick,
        enabled = !loading,
        shape = RoundedCornerShape(50),
        color = if (loading) colorScheme.onSurface.copy(alpha = 0.12f) else colorScheme.primary,
        contentColor = if (loading) colorScheme.onSurface.copy(alpha = 0.38f) else colorScheme.onPrimary,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        // Laser triangles — exactly the same modifier as the Login button.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .trianglesBackground(),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (timedOut) stringResource(R.string.retrying) else stringResource(R.string.try_again),
                    fontWeight = FontWeight.SemiBold,
                )
                // Loading on the RIGHT of the label.
                if (loading) {
                    Spacer(Modifier.width(8.dp))
                    OsuSpinner(size = 20.dp)
                }
            }
        }
    }
}
