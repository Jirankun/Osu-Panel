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

    /** Chat settings (Chatango login / activation / notifications). */
    const val CHAT_SETTINGS = "settings/chat"

    /** QR code screen (from beatmap detail). */
    const val QR_SCREEN = "qr"

    /** Chat screens (from the Chat tab). */
    const val PM_CHAT = "chat/pm"
    const val GROUP_CHAT = "chat/group"
    const val CHAT_EDIT = "chat/edit"

    fun profile(userId: Int) = "$PROFILE/$userId"

    fun beatmapDetail(beatmapsetId: Int) = "$BEATMAP_DETAIL/$beatmapsetId"

    fun qrScreen(beatmapsetId: Int) = "$QR_SCREEN/$beatmapsetId"

    fun pmChat(user: String) = "$PM_CHAT/$user"

    fun groupChat(group: String) = "$GROUP_CHAT/$group"
}
