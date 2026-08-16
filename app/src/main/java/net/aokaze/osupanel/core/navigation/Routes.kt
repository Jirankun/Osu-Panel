/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.core.navigation

/** Route names — the single source of truth for navigation. */
object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"

    /** Profile page (from Dashboard / Rankings). */
    const val PROFILE = "profile"

    /** Beatmapset detail page (from Maps / profile). */
    const val BEATMAP_DETAIL = "beatmap"

    /** WebView info page (from Settings). */
    const val LICENSES = "settings/licenses"
    const val CONTRIBUTORS = "settings/contributors"

    fun profile(userId: Int) = "$PROFILE/$userId"

    fun beatmapDetail(beatmapsetId: Int) = "$BEATMAP_DETAIL/$beatmapsetId"
}
