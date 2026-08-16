/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.widget

/**
 * Game mode for home screen widgets ("Widget mode" in Settings).
 *
 * The mode value used by the renderer = the SVG resource in `res/raw/`
 * (`mode_{std|catch|taiko|mania}.svg`) — exactly osu-stats-signature's
 * convention. For per-mode stat fetches through the osu! API v2
 * (`GET /users/{id}/{mode}`) [apiMode] is used.
 */
object WidgetMode {

    /** Display order in the Settings dropdown (default first). */
    val ALL: List<String> = listOf("std", "catch", "taiko", "mania")

    /** Display name — consistent with stat-sign's getPlaymodeFullName. */
    fun displayName(mode: String): String = when (mode) {
        "catch" -> "osu!catch"
        "mania" -> "osu!mania"
        "taiko" -> "osu!taiko"
        else -> "osu!"
    }

    /** Mode for the osu! API v2 endpoint (std→osu, catch→fruits). */
    fun apiMode(mode: String): String = when (mode) {
        "catch" -> "fruits"
        "mania" -> "mania"
        "taiko" -> "taiko"
        else -> "osu"
    }
}
