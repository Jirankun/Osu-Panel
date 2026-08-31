/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.remote

import net.aokaze.osupanel.data.model.BeatmapDto
import net.aokaze.osupanel.data.model.BeatmapScoresResponse
import net.aokaze.osupanel.data.model.BeatmapsetDto
import net.aokaze.osupanel.data.model.DailyChallengeResponse
import net.aokaze.osupanel.data.model.FavouriteBody
import net.aokaze.osupanel.data.model.UserBeatmapScoreResponse
import net.aokaze.osupanel.data.model.BeatmapsetSearchResponse
import net.aokaze.osupanel.data.model.MostPlayedBeatmapDto
import net.aokaze.osupanel.data.model.RankingsResponse
import net.aokaze.osupanel.data.model.ScoreDto
import net.aokaze.osupanel.data.model.SearchResponse
import net.aokaze.osupanel.data.model.UserDto
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * All osu! API v2 endpoints. All requests go through the interceptor that
 * injects `Authorization: Bearer` and handles 401 refresh.
 */
interface OsuApi {

    /** GET /me — only for tokens from the Authorization Code Grant. */
    @GET("me")
    suspend fun getMe(): UserDto

    /** GET /users/{userId} */
    @GET("users/{userId}")
    suspend fun getUser(@Path("userId") userId: Int): UserDto

    /** GET /users/{userId}/{mode} — stats per game mode. */
    @GET("users/{userId}/{mode}")
    suspend fun getUserByMode(
        @Path("userId") userId: Int,
        @Path("mode") mode: String,
    ): UserDto

    /** GET /users/@{username} */
    @GET("users/@{username}")
    suspend fun getUserByUsername(@Path("username") username: String): UserDto

    /** GET /users/{userId}/scores/{type} — recent | best | firsts */
    @GET("users/{userId}/scores/{type}")
    suspend fun getUserScores(
        @Path("userId") userId: Int,
        @Path("type") type: String,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): List<ScoreDto>

    /** GET /friends */
    @GET("friends")
    suspend fun getFriends(): List<UserDto>

    /** GET /rankings/{mode}/{type} — performance | score | country */
    @GET("rankings/{mode}/{type}")
    suspend fun getRankings(
        @Path("mode") mode: String,
        @Path("type") type: String,
        @Query("page") page: Int? = null,
        @Query("country") country: String? = null,
    ): RankingsResponse

    /** GET /beatmaps/{beatmapId} */
    @GET("beatmaps/{beatmapId}")
    suspend fun getBeatmap(@Path("beatmapId") beatmapId: Int): BeatmapDto

    /** GET /beatmapsets/{beatmapsetId} — full detail + difficulty list. */
    @GET("beatmapsets/{beatmapsetId}")
    suspend fun getBeatmapset(@Path("beatmapsetId") beatmapsetId: Int): BeatmapsetDto

    /** GET /beatmaps/{beatmapId}/scores — leaderboard per difficulty. */
    @GET("beatmaps/{beatmapId}/scores")
    suspend fun getBeatmapScores(@Path("beatmapId") beatmapId: Int): BeatmapScoresResponse

    /** GET /beatmapsets/search?… */
    @GET("beatmapsets/search")
    suspend fun searchBeatmapsets(@QueryMap query: Map<String, String>): BeatmapsetSearchResponse

    /** GET /search?mode=user&q=… — search users globally. */
    @GET("search")
    suspend fun searchUsers(
        @Query("mode") mode: String = "user",
        @Query("q") q: String,
    ): SearchResponse

    /**
     * GET /users/{userId}/beatmapsets/most_played
     * Each item = { beatmap_id, count, beatmap, beatmapset }.
     */
    @GET("users/{userId}/beatmapsets/most_played")
    suspend fun getMostPlayed(
        @Path("userId") userId: Int,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): List<MostPlayedBeatmapDto>

    /** GET /users/{userId}/beatmapsets/favourite */
    @GET("users/{userId}/beatmapsets/favourite")
    suspend fun getFavourites(
        @Path("userId") userId: Int,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): List<BeatmapsetDto>

    /**
     * GET /beatmaps/{beatmapId}/scores/users/{userId} → { position, score }
     * 404 → null (the user has never played that map).
     */
    @GET("beatmaps/{beatmapId}/scores/users/{userId}")
    suspend fun getUserBeatmapScore(
        @Path("beatmapId") beatmapId: Int,
        @Path("userId") userId: Int,
    ): UserBeatmapScoreResponse?

    /** GET /rankings/daily-challenge — current daily challenge beatmap. */
    @GET("rankings/daily-challenge")
    suspend fun getDailyChallenge(): DailyChallengeResponse

    /**
     * POST /beatmapsets/{beatmapsetId}/favourites
     * Toggle favourite — POST to add, DELETE to remove.
     * Body: { "beatmapset_id": id }
     */
    @POST("beatmapsets/{beatmapsetId}/favourites")
    suspend fun addFavourite(
        @Path("beatmapsetId") beatmapsetId: Int,
        @retrofit2.http.Body body: FavouriteBody,
    )

    @DELETE("beatmapsets/{beatmapsetId}/favourites")
    suspend fun removeFavourite(
        @Path("beatmapsetId") beatmapsetId: Int,
    )

    /** GET /me/beatmapset-favourites — current user favourites. */
    @GET("me/beatmapset-favourites")
    suspend fun getMyFavourites(): List<BeatmapsetDto>
}
