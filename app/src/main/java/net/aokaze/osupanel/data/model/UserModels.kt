/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * osu! user DTO — counterpart of the Flutter `UserModel`.
 * All fields optional + defaults to stay safe with compact responses
 * (e.g. /friends, /search which omit statistics).
 */
@Serializable
data class UserDto(
    val id: Int,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val country: CountryDto? = null,
    @SerialName("is_supporter") val isSupporter: Boolean = false,
    @SerialName("support_level") val supportLevel: Int = 0,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("is_restricted") val isRestricted: Boolean = false,
    @SerialName("join_date") val joinDate: String? = null,
    val cover: CoverDto? = null,
    @SerialName("profile_colour") val profileColour: String? = null,
    val statistics: UserStatisticsDto? = null,
    val badges: List<BadgeDto> = emptyList(),
    val groups: List<UserGroupDto> = emptyList(),
    val playstyle: List<String> = emptyList(),
    val medals: List<UserMedalDto> = emptyList(),
    @SerialName("user_achievements") val userAchievements: List<UserMedalDto> = emptyList(),
    @SerialName("rank_history") val rankHistory: RankHistoryDto? = null,
    val kudosu: KudosuDto? = null,
) {
    /** Cover URL (falls back to null when absent). */
    val coverUrl: String? get() = cover?.url

    /**
     * Combined achievement source — handles two osu! API formats:
     * 1. `medals` → [{ medal: {...}, achieved_at }]
     * 2. `user_achievements` → [{ achievement_id, achieved_at }]
     */
    val achievements: List<UserMedalDto>
        get() = if (userAchievements.isNotEmpty()) userAchievements else medals
}

@Serializable
data class CoverDto(
    val url: String? = null,
)

/** Country name from the osu! API (`country: { code, name }`). */
@Serializable
data class CountryDto(
    val code: String? = null,
    val name: String? = null,
)

/** Achieved-medal entry for a user (two formats, see [UserDto.achievements]). */
@Serializable
data class UserMedalDto(
    @SerialName("achieved_at") val achievedAt: String? = null,
    val medal: MedalDto? = null,
    @SerialName("achievement_id") val achievementId: Int? = null,
)

@Serializable
data class MedalDto(
    val name: String = "",
    val description: String = "",
    @SerialName("icon_url") val iconUrl: String? = null,
    val slug: String? = null,
    val grouping: String = "Unknown",
    val rarity: Double? = null,
    @SerialName("achievement_id") val achievementId: Int? = null,
)

@Serializable
data class BadgeDto(
    @SerialName("awarded_at") val awardedAt: String? = null,
    val description: String = "",
    @SerialName("image_url") val imageUrl: String = "",
)

@Serializable
data class UserGroupDto(
    val id: Int = 0,
    val name: String? = null,
    val colour: String? = null,
    @SerialName("short_name") val shortName: String? = null,
)

@Serializable
data class UserStatisticsDto(
    /** level may be a plain int OR an object { current, progress }. */
    val level: JsonElement = JsonPrimitive(0),
    val pp: Double = 0.0,
    val accuracy: Double = 0.0,
    @SerialName("ranked_score") val rankedScore: Long = 0,
    @SerialName("total_score") val totalScore: Long = 0,
    @SerialName("play_count") val playCount: Int = 0,
    @SerialName("play_time") val playTime: Int = 0,
    @SerialName("total_hits") val totalHits: Long = 0,
    @SerialName("maximum_combo") val maximumCombo: Long = 0,
    @SerialName("global_rank") val globalRank: Int? = null,
    @SerialName("country_rank") val countryRank: Int? = null,
    @SerialName("replays_watched_by_others") val replaysWatchedByOthers: Int = 0,
    @SerialName("grade_counts") val gradeCounts: GradeCountsDto? = null,
    @SerialName("rank_history") val rankHistory: RankHistoryDto? = null,
) {
    /** Current level (direct int or `current` from the object). */
    val levelCurrent: Int
        get() {
            val prim = level as? JsonPrimitive
            if (prim != null) return prim.intOrNull ?: 0
            val obj = level as? JsonObject ?: return 0
            return (obj["current"] as? JsonPrimitive)?.intOrNull ?: 0
        }

    /**
     * Progress level ternormalisasi 0..1.
     * The API sends progress as a percentage (0-100) — divide by 100 so
     * it fits LinearProgressIndicator.
     */
    val levelProgress: Double
        get() {
            val obj = level as? JsonObject ?: return 0.0
            val raw = (obj["progress"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
            return if (raw > 1.0) raw / 100.0 else raw.coerceIn(0.0, 1.0)
        }
}

@Serializable
data class GradeCountsDto(
    val ss: Int = 0,
    val ssh: Int = 0,
    val s: Int = 0,
    val sh: Int = 0,
    val a: Int = 0,
)

@Serializable
data class RankHistoryDto(
    val data: List<Int> = emptyList(),
)

@Serializable
data class KudosuDto(
    val total: Int? = null,
    val available: Int? = null,
)
