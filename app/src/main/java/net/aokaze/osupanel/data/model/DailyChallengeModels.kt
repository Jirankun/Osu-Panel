/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /rankings/daily_challenge response.
 * Contains the current daily challenge beatmap info.
 */
@Serializable
data class DailyChallengeResponse(
    @SerialName("beatmap_id") val beatmapId: Int? = null,
    @SerialName("beatmapset_id") val beatmapsetId: Int? = null,
    val beatmap: DailyChallengeBeatmap? = null,
    val beatmapset: DailyChallengeBeatmapset? = null,
)

@Serializable
data class DailyChallengeBeatmap(
    val id: Int = 0,
    val version: String = "",
    @SerialName("difficulty_rating") val difficultyRating: Double = 0.0,
    val mode: String? = null,
    @SerialName("total_length") val totalLength: Int? = null,
)

@Serializable
data class DailyChallengeBeatmapset(
    val id: Int = 0,
    val title: String = "",
    val artist: String = "",
    val creator: String? = null,
    val covers: BeatmapsetCoverDto? = null,
    val bpm: Double? = null,
)
