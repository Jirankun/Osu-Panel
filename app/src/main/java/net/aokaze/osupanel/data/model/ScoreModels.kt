/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** Token response from the Cloudflare Worker. */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
)

/** Hit breakdown of a play (per-score `statistics`). */
@Serializable
data class ScoreStatisticsDto(
    @SerialName("count_300") val count300: Int = 0,
    @SerialName("count_100") val count100: Int = 0,
    @SerialName("count_50") val count50: Int = 0,
    @SerialName("count_miss") val countMiss: Int = 0,
) {
    /** Total judged hits — denominator for ratios. */
    val total: Int get() = count300 + count100 + count50 + countMiss
}

/** PP weight of a score — the API sends it as `{percentage, pp}`. */
@Serializable
data class ScoreWeightDto(
    /** How much of the score's raw pp counts (0..100 %). */
    val percentage: Double = 0.0,
    /** The weighted pp this play contributes (`percentage × pp / 100`). */
    val pp: Double = 0.0,
)

/** Satu score / play. */
@Serializable
data class ScoreDto(
    val id: Long,
    @SerialName("best_id") val bestId: Long? = null,
    val accuracy: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("max_combo") val maxCombo: Int = 0,
    val mode: String? = null,
    @SerialName("mode_int") val modeInt: Int? = null,
    val mods: List<String> = emptyList(),
    val pp: Double? = null,
    /** PP weight of this score — osu! API sends it as an OBJECT
     *  `{percentage, pp}`, not a plain number. */
    val weight: ScoreWeightDto? = null,
    val rank: String? = null,
    val score: Long = 0,
    @SerialName("total_score") val totalScore: Long = 0,
    val passed: Boolean? = null,
    @SerialName("user_id") val userId: Int? = null,
    val user: UserDto? = null,
    val beatmap: BeatmapDto? = null,
    val beatmapset: BeatmapsetDto? = null,
    val statistics: ScoreStatisticsDto? = null,
) {
    /** Weighted contribution of this score (`weight.pp` — the number the
     *  API reports as the weighted pp of this play). */
    val weightedPp: Double?
        get() = weight?.pp
}

@Serializable
data class BeatmapDto(
    val id: Int,
    @SerialName("beatmapset_id") val beatmapsetId: Int? = null,
    val version: String = "",
    @SerialName("difficulty_rating") val difficultyRating: Double = 0.0,
    val cs: Double? = null,
    val ar: Double? = null,
    val od: Double? = null,
    val hp: Double? = null,
    val bpm: Double? = null,
    val mode: String? = null,
    @SerialName("mode_int") val modeInt: Int? = null,
    val status: String? = null,
    @SerialName("total_length") val totalLength: Int? = null,
    @SerialName("hit_length") val hitLength: Int? = null,
    @SerialName("max_combo") val maxCombo: Int? = null,
    val rank: String? = null,
    val accuracy: Double? = null,
    @SerialName("count_circles") val countCircles: Int? = null,
    @SerialName("count_sliders") val countSliders: Int? = null,
    @SerialName("count_spinners") val countSpinners: Int? = null,
    val beatmaps: List<BeatmapDto> = emptyList(),
    val beatmapset: BeatmapsetDto? = null,
)

/**
 * Beatmapset — note: the API sends covers under the `covers` key
 * (not `cover`), and some variants (most_played) omit certain
 * bpm/creator_id.
 */
@Serializable
data class BeatmapsetDto(
    val id: Int,
    val artist: String = "",
    @SerialName("artist_unicode") val artistUnicode: String? = null,
    val creator: String? = null,
    @SerialName("creator_id") val creatorId: Int? = null,
    val covers: BeatmapsetCoverDto? = null,
    @SerialName("favourite_count") val favouriteCount: Int? = null,
    @SerialName("play_count") val playCount: Int? = null,
    val status: String? = null,
    val title: String = "",
    @SerialName("title_unicode") val titleUnicode: String? = null,
    @SerialName("user_id") val userId: Int? = null,
    val video: Boolean? = null,
    val bpm: Double? = null,
    val nsfw: Boolean? = null,
    val source: String? = null,
    val tags: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("ranked_date") val rankedDate: String? = null,
    @SerialName("genre_id") val genreId: Int? = null,
    @SerialName("language_id") val languageId: Int? = null,
    val beatmaps: List<BeatmapDto> = emptyList(),
) {
    /** Main cover URL (falls back to null when absent). */
    val coverUrl: String? get() = covers?.cover
}

@Serializable
data class BeatmapsetCoverDto(
    val cover: String? = null,
    @SerialName("cover@2x") val cover2x: String? = null,
    val card: String? = null,
    @SerialName("card@2x") val card2x: String? = null,
    val list: String? = null,
    @SerialName("list@2x") val list2x: String? = null,
    val slimcover: String? = null,
    @SerialName("slimcover@2x") val slimcover2x: String? = null,
)

/** GET /beatmaps/{id}/scores → { score_count, scores } */
@Serializable
data class BeatmapScoresResponse(
    val scores: List<ScoreDto> = emptyList(),
    @SerialName("score_count") val scoreCount: Int? = null,
)

/**
 * GET /beatmaps/{id}/scores/users/{userId} → { position, score }.
 * Note: this endpoint wraps the user's score in a wrapper object;
 * `position` = the user's rank on that beatmap (not just top 50).
 */
@Serializable
data class UserBeatmapScoreResponse(
    val position: Int? = null,
    val score: ScoreDto? = null,
)

/** GET /beatmapsets/search → { beatmapsets, total, ... } */
@Serializable
data class BeatmapsetSearchResponse(
    val beatmapsets: List<BeatmapsetDto> = emptyList(),
    val total: Int? = null,
)

/**
 * GET /rankings/{mode}/{type}
 * Each entry = a user object + stats flattened to the top level
 * (not nested inside `user`). The cursor may contain a number (e.g. page).
 */
@Serializable
data class RankingsResponse(
    val ranking: List<RankingEntryDto> = emptyList(),
    val total: Int? = null,
    val cursor: JsonElement? = null,
)

@Serializable
data class RankingEntryDto(
    val user: UserDto,
    val pp: Double = 0.0,
    @SerialName("global_rank") val globalRank: Int? = null,
    @SerialName("country_rank") val countryRank: Int? = null,
    val accuracy: Double = 0.0,
    @SerialName("play_count") val playCount: Int = 0,
    @SerialName("ranked_score") val rankedScore: Long = 0,
    @SerialName("grade_counts") val gradeCounts: GradeCountsDto? = null,
    val level: JsonElement = JsonPrimitive(0),
)

/** GET /search?mode=user → { user: { data: [...], total } } */
@Serializable
data class SearchResponse(
    val user: UserSearchResultDto? = null,
)

@Serializable
data class UserSearchResultDto(
    val data: List<UserDto> = emptyList(),
    val total: Int? = null,
)

/**
 * GET /users/{id}/beatmapsets/most_played
 * Each item = { beatmap_id, count, beatmap, beatmapset }.
 */
@Serializable
data class MostPlayedBeatmapDto(
    @SerialName("beatmap_id") val beatmapId: Int,
    val count: Int = 0,
    val beatmap: BeatmapDto? = null,
    val beatmapset: BeatmapsetDto? = null,
)

/**
 * POST /beatmapsets/{id}/favourites body.
 * osu! API expects: { "beatmapset_id": <int> }
 */
@Serializable
data class FavouriteBody(
    @SerialName("beatmapset_id") val beatmapsetId: Int,
)
