/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun OsuPanelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Color scheme read from res/values/colors.xml (single source of truth).
    val context = LocalContext.current
    val colorScheme = if (darkTheme) osuDarkColorScheme(context) else osuLightColorScheme(context)

    // Status bar follows the surface color (edge-to-edge)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }

            // Request the highest refresh rate the screen supports (90/120Hz)
            // so animations (spinner + triangles) run as smoothly as possible. The system
            // — this mode is only used when the screen actually supports it;
            // otherwise it safely stays on the default refresh rate.
            runCatching {
                val display = window.windowManager.defaultDisplay
                val best = display.supportedModes.maxByOrNull { it.refreshRate }
                val current = display.mode.refreshRate
                if (best != null && best.refreshRate > current) {
                    window.attributes = window.attributes.apply {
                        preferredDisplayModeId = best.modeId
                    }
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
