/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.repository

import net.aokaze.osupanel.data.local.DataCache
import net.aokaze.osupanel.data.model.BeatmapDto
import net.aokaze.osupanel.data.model.BeatmapsetDto
import net.aokaze.osupanel.data.model.DailyChallengeResponse
import net.aokaze.osupanel.data.model.FavouriteBody
import net.aokaze.osupanel.data.model.MostPlayedBeatmapDto
import net.aokaze.osupanel.data.model.RankingEntryDto
import net.aokaze.osupanel.data.model.ScoreDto
import net.aokaze.osupanel.data.model.UserDto
import net.aokaze.osupanel.data.remote.OsuApi

/**
 * Content repository — every endpoint except auth.
 *
 * Cache: per-session in-memory [DataCache], invalidated on refresh.
 * `force` = true → skip the cache (pull-to-refresh).
 */
class ContentRepository(private val osuApi: OsuApi) {

    /** GET /users/{userId}/scores/{type} — recent | best | firsts */
    suspend fun getUserScores(
        userId: Int,
        type: String,
        limit: Int = 100,
        offset: Int = 0,
        cacheKey: String? = null,
        force: Boolean = false,
    ): List<ScoreDto> = cachedList(cacheKey, force) {
        osuApi.getUserScores(userId, type, limit = limit, offset = offset)
    }

    /** GET /users/{userId}/beatmapsets/most_played */
    suspend fun getMostPlayed(
        userId: Int,
        limit: Int = 100,
        offset: Int = 0,
        cacheKey: String? = null,
        force: Boolean = false,
    ): List<MostPlayedBeatmapDto> = cachedList(cacheKey, force) {
        osuApi.getMostPlayed(userId, limit = limit, offset = offset)
    }

    /** GET /users/{userId}/beatmapsets/favourite — tab "Loved". */
    suspend fun getFavourites(
        userId: Int,
        limit: Int = 100,
        offset: Int = 0,
        cacheKey: String? = null,
        force: Boolean = false,
    ): List<BeatmapsetDto> = cachedList(cacheKey, force) {
        osuApi.getFavourites(userId, limit = limit, offset = offset)
    }

    /** GET /rankings/{mode}/{type} — rankings page. */
    suspend fun getRankings(
        mode: String,
        type: String,
        page: Int,
        country: String?,
        cacheKey: String? = null,
        force: Boolean = false,
    ): List<RankingEntryDto> {
        if (!force && cacheKey != null && DataCache.has(cacheKey)) {
            return DataCache.get(cacheKey) ?: emptyList()
        }
        val response = osuApi.getRankings(mode, type, page = page, country = country)
        if (cacheKey != null) DataCache.set(cacheKey, response.ranking)
        return response.ranking
    }

    /** GET /users/{userId} — full profile. */
    suspend fun getUser(userId: Int, cacheKey: String? = null, force: Boolean = false): UserDto {
        if (!force && cacheKey != null && DataCache.has(cacheKey)) {
            return DataCache.get(cacheKey) ?: osuApi.getUser(userId)
        }
        val user = osuApi.getUser(userId)
        if (cacheKey != null) DataCache.set(cacheKey, user)
        return user
    }

    /** GET /users/{userId}/{mode} */
    suspend fun getUserByMode(userId: Int, mode: String): UserDto =
        osuApi.getUserByMode(userId, mode)

    /** GET /users/@{username} */
    suspend fun getUserByUsername(username: String): UserDto =
        osuApi.getUserByUsername(username)

    /** GET /beatmapsets/{beatmapsetId} — detail + difficulty list. */
    suspend fun getBeatmapset(
        beatmapsetId: Int,
        cacheKey: String? = null,
        force: Boolean = false,
    ): BeatmapsetDto {
        if (!force && cacheKey != null && DataCache.has(cacheKey)) {
            return DataCache.get(cacheKey) ?: osuApi.getBeatmapset(beatmapsetId)
        }
        val bms = osuApi.getBeatmapset(beatmapsetId)
        if (cacheKey != null) DataCache.set(cacheKey, bms)
        return bms
    }

    /** GET /beatmaps/{beatmapId}/scores — leaderboard. */
    suspend fun getBeatmapScores(beatmapId: Int): List<ScoreDto> =
        osuApi.getBeatmapScores(beatmapId).scores

    /** GET /beatmaps/{beatmapId}/scores/users/{userId} — null bila 404. */
    suspend fun getUserBeatmapScore(beatmapId: Int, userId: Int): ScoreDto? =
        runCatching { osuApi.getUserBeatmapScore(beatmapId, userId)?.score }.getOrNull()

    /** GET /search?mode=user — search users globally. */
    suspend fun searchUsers(query: String): List<UserDto> =
        osuApi.searchUsers(q = query).user?.data ?: emptyList()

    /** GET /rankings/daily_challenge — current daily challenge beatmap. */
    suspend fun getDailyChallenge(
        cacheKey: String? = null,
        force: Boolean = false,
    ): DailyChallengeResponse {
        if (!force && cacheKey != null && DataCache.has(cacheKey)) {
            return DataCache.get(cacheKey) ?: osuApi.getDailyChallenge()
        }
        val result = osuApi.getDailyChallenge()
        if (cacheKey != null) DataCache.set(cacheKey, result)
        return result
    }

    /** POST /beatmapsets/{id}/favourites — add to favourites. */
    suspend fun addFavourite(beatmapsetId: Int) {
        osuApi.addFavourite(beatmapsetId, FavouriteBody(beatmapsetId))
    }

    /** DELETE /beatmapsets/{id}/favourites — remove from favourites. */
    suspend fun removeFavourite(beatmapsetId: Int) {
        osuApi.removeFavourite(beatmapsetId)
    }

    /** GET /me/beatmapset-favourites — current user's favourites. */
    suspend fun getMyFavourites(
        cacheKey: String? = null,
        force: Boolean = false,
    ): List<BeatmapsetDto> = cachedList(cacheKey, force) {
        osuApi.getMyFavourites()
    }

    private suspend fun <T> cachedList(
        key: String?,
        force: Boolean,
        loader: suspend () -> List<T>,
    ): List<T> {
        if (!force && key != null && DataCache.has(key)) {
            return DataCache.get(key) ?: emptyList()
        }
        val result = loader()
        if (key != null) DataCache.set(key, result)
        return result
    }
}
