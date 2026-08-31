/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.core.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import net.aokaze.osupanel.R

/**
 * Functional osu! colors (score rank, beatmap status, grade, mode
 * statistik) — SATU sumber kebenaran di `res/values/colors.xml`,
 * accents) loaded once via [init] (called from `OsuPanelApp.onCreate`,
 * same pattern as `MedalService.init`).
 *
 * Use anywhere: `OsuColors.blue`, `OsuColors.rankColor(...)`,
 * `OsuColors.statusColor(...)` — NO hardcoded hex colors in screens anymore.
 */
object OsuColors {

    private lateinit var appContext: Context

    /** Store the app context — call once in Application.onCreate. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    // ── Stat accents (dashboard & profile stat icons) ──
    val blue: Color by lazy { color(R.color.accent_blue) }
    val green: Color by lazy { color(R.color.accent_green) }
    val purple: Color by lazy { color(R.color.accent_purple) }
    val amber: Color by lazy { color(R.color.accent_amber) }
    val amber600: Color by lazy { color(R.color.accent_amber_600) }
    val orange: Color by lazy { color(R.color.accent_orange) }
    val teal: Color by lazy { color(R.color.accent_teal) }
    val pink: Color by lazy { color(R.color.accent_pink) }
    val cyan: Color by lazy { color(R.color.accent_cyan) }
    val gray: Color by lazy { color(R.color.accent_gray) }
    val yellowLight: Color by lazy { color(R.color.accent_yellow_light) }
    val orangeLight: Color by lazy { color(R.color.accent_orange_light) }
    val pink300: Color by lazy { color(R.color.accent_pink_300) }
    val blue400: Color by lazy { color(R.color.accent_blue_400) }
    val blue800: Color by lazy { color(R.color.accent_blue_800) }
    val green400: Color by lazy { color(R.color.accent_green_400) }
    val green800: Color by lazy { color(R.color.accent_green_800) }
    val richtextLink: Color by lazy { color(R.color.richtext_link) }
    val dailyChallengeBg: Color by lazy { color(R.color.daily_challenge_bg) }

    // ── Score rank colors (score badge) ──
    fun rankColor(rank: String, scheme: ColorScheme): Color = when (rank.uppercase()) {
        "SSH", "SS" -> amber
        "SH", "S" -> orange
        "A" -> green
        "B" -> blue
        "C" -> purple
        else -> scheme.onSurface
    }

    // ── Status beatmap (badge RANKED / Loved / …) ──
    fun statusColor(status: String?): Pair<Color, Color> = when (status?.lowercase()) {
        "ranked" -> green to Color.White
        "loved" -> pink to Color.White
        "qualified" -> blue to Color.White
        "approved" -> cyan to Color.White
        else -> gray to Color.White
    }

    // ── Grade counts (profile bar chart) ──
    val gradeSS: Color get() = amber
    val gradeSSH: Color get() = yellowLight
    val gradeS: Color get() = orange
    val gradeSH: Color get() = orangeLight
    val gradeA: Color get() = green

    private fun color(id: Int): Color = Color(appContext.getColor(id))
}
