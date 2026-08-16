/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.local

/**
 * Simple in-memory cache — counterpart of the Flutter `DataCache`.
 *
 * Prevents API data from reloading while navigating between screens. It
 * persists for the session (app not restarted), cleared only when [invalidate]
 * is called (e.g. pull-to-refresh).
 */
object DataCache {

    private val data = HashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = data[key] as? T

    fun set(key: String, value: Any) {
        data[key] = value
    }

    fun has(key: String): Boolean = data.containsKey(key)

    fun invalidate(key: String) {
        data.remove(key)
    }

    fun clear() = data.clear()

    // ── Cache keys (same as Flutter) ──

    fun recentScores(userId: Int) = "recentScores:$userId"
    fun mostPlayed(userId: Int) = "mostPlayed:$userId"
    fun favourites(userId: Int) = "favourites:$userId"
    fun rankings(mode: String, type: String, page: Int, country: String?) =
        "rankings:$mode:$type:$page:${country ?: "all"}"
    fun profile(userId: Int) = "profile:$userId"
    fun bestScores(userId: Int) = "bestScores:$userId"
    fun mostPlayedBeatmaps(userId: Int) = "mostPlayedBeatmaps:$userId"
    fun beatmapset(beatmapsetId: Int) = "beatmapset:$beatmapsetId"
}
