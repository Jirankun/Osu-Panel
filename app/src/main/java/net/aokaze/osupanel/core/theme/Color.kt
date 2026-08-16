/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.core.theme

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import net.aokaze.osupanel.R

/**
 * All app colors are defined in `res/values/colors.xml`
 * (single source of truth) — this file only reads them into
 * [ColorScheme] Material 3.
 */

/** Warna brand osu! (res/values/colors.xml → osu_pink). */
fun osuPink(context: Context): Color = Color(context.getColor(R.color.osu_pink))

/** Light color scheme — read from `light_*` in colors.xml. */
fun osuLightColorScheme(context: Context): androidx.compose.material3.ColorScheme =
    lightColorScheme(
        primary = Color(context.getColor(R.color.light_primary)),
        onPrimary = Color(context.getColor(R.color.light_on_primary)),
        primaryContainer = Color(context.getColor(R.color.light_primary_container)),
        onPrimaryContainer = Color(context.getColor(R.color.light_on_primary_container)),
        secondary = Color(context.getColor(R.color.light_secondary)),
        onSecondary = Color(context.getColor(R.color.light_on_secondary)),
        secondaryContainer = Color(context.getColor(R.color.light_secondary_container)),
        onSecondaryContainer = Color(context.getColor(R.color.light_on_secondary_container)),
        tertiary = Color(context.getColor(R.color.light_tertiary)),
        onTertiary = Color(context.getColor(R.color.light_on_tertiary)),
        tertiaryContainer = Color(context.getColor(R.color.light_tertiary_container)),
        onTertiaryContainer = Color(context.getColor(R.color.light_on_tertiary_container)),
        error = Color(context.getColor(R.color.light_error)),
        onError = Color(context.getColor(R.color.light_on_error)),
        errorContainer = Color(context.getColor(R.color.light_error_container)),
        onErrorContainer = Color(context.getColor(R.color.light_on_error_container)),
        background = Color(context.getColor(R.color.light_background)),
        onBackground = Color(context.getColor(R.color.light_on_background)),
        surface = Color(context.getColor(R.color.light_surface)),
        onSurface = Color(context.getColor(R.color.light_on_surface)),
        surfaceVariant = Color(context.getColor(R.color.light_surface_variant)),
        onSurfaceVariant = Color(context.getColor(R.color.light_on_surface_variant)),
        outline = Color(context.getColor(R.color.light_outline)),
    )

/** Dark color scheme — read from `dark_*` in colors.xml. */
fun osuDarkColorScheme(context: Context): androidx.compose.material3.ColorScheme =
    darkColorScheme(
        primary = Color(context.getColor(R.color.dark_primary)),
        onPrimary = Color(context.getColor(R.color.dark_on_primary)),
        primaryContainer = Color(context.getColor(R.color.dark_primary_container)),
        onPrimaryContainer = Color(context.getColor(R.color.dark_on_primary_container)),
        secondary = Color(context.getColor(R.color.dark_secondary)),
        onSecondary = Color(context.getColor(R.color.dark_on_secondary)),
        secondaryContainer = Color(context.getColor(R.color.dark_secondary_container)),
        onSecondaryContainer = Color(context.getColor(R.color.dark_on_secondary_container)),
        tertiary = Color(context.getColor(R.color.dark_tertiary)),
        onTertiary = Color(context.getColor(R.color.dark_on_tertiary)),
        tertiaryContainer = Color(context.getColor(R.color.dark_tertiary_container)),
        onTertiaryContainer = Color(context.getColor(R.color.dark_on_tertiary_container)),
        error = Color(context.getColor(R.color.dark_error)),
        onError = Color(context.getColor(R.color.dark_on_error)),
        errorContainer = Color(context.getColor(R.color.dark_error_container)),
        onErrorContainer = Color(context.getColor(R.color.dark_on_error_container)),
        background = Color(context.getColor(R.color.dark_background)),
        onBackground = Color(context.getColor(R.color.dark_on_background)),
        surface = Color(context.getColor(R.color.dark_surface)),
        onSurface = Color(context.getColor(R.color.dark_on_surface)),
        surfaceVariant = Color(context.getColor(R.color.dark_surface_variant)),
        onSurfaceVariant = Color(context.getColor(R.color.dark_on_surface_variant)),
        outline = Color(context.getColor(R.color.dark_outline)),
    )
