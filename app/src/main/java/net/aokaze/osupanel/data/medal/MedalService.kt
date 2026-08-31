/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.medal

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Data for one medal from `All-Medal.json`.
 * JSON is bundled into native assets (`assets/medals/<grouping>/All-Medal.json`).
 */
@Serializable
data class MedalData(
    val slug: String = "",
    val name: String = "",
    val grouping: String = "Unknown",
    val mode: String? = null,
    val file: String = "",
    @SerialName("medal_name") val medalName: String = "",
    val description: String = "",
    @SerialName("achievement_id") val achievementId: JsonElement? = null,
) {
    /** achievement_id as Int — JSON may carry a number OR a string. */
    val achievementIdInt: Int?
        get() = when (val v = achievementId) {
            is JsonPrimitive -> v.intOrNull ?: v.content.toIntOrNull()
            else -> null
        }

    /** Path asset lokal: `medals/{grouping}/{file}` */
    val localAssetPath: String get() = "medals/$grouping/$file"

    /** Path fallback via slug: `medals/{grouping}/{slug}.png` */
    val localAssetPathBySlug: String get() = "medals/$grouping/$slug.png"

    /** Display name — medal_name first (official osu! name), falls back to name. */
    val displayName: String get() = if (medalName.isNotEmpty()) medalName else name
}


/** Medal display item: data + achieved/not-achieved status. */
data class MedalDisplay(
    val medal: MedalData,
    val achieved: Boolean,
    val achievedAt: String? = null,
)

/**
 * Medal service. Loads ALL `All-Medal.json` files from native assets and
 * provides fast access by slug / achievement_id / grouping.
 *
 * Used by the dashboard & profile to show ALL medals
 * (achieved ones bright, unachieved ones grey).
 */
object MedalService {

    private val json = Json { ignoreUnknownKeys = true }

    private val medalsBySlug = LinkedHashMap<String, MedalData>()
    private val medalsByGrouping = LinkedHashMap<String, List<MedalData>>()
    private var initialized = false

    /** All registered medal folders. */
    private val medalFolders = listOf(
        "Beatmap Packs",
        "Beatmap Challenge Packs",
        "Beatmap Spotlights",
        "Hush-Hush",
        "Hush-Hush (Expert)",
        "Mod Introduction",
        "Skill & Dedication",
    )

    /** Load all JSON from assets — call once from the Application. */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            for (folder in medalFolders) {
                loadJson(context, folder)
            }
            initialized = true
        }
        // Preload ALL medal images in the background → tiles appear instantly
        // in the UI with no scroll-time decode and no fallback icon.
        MedalAssets.preloadAll(context, medalsBySlug.values.map { it.localAssetPath })
    }

    private fun loadJson(context: Context, folder: String) {
        val path = "medals/$folder/All-Medal.json"
        val raw = try {
            context.assets.open(path).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            // A folder may be empty — not an error.
            return
        }
        val list = try {
            json.decodeFromString<List<MedalData>>(raw)
        } catch (e: Exception) {
            android.util.Log.d("MedalService", "JSON parse error in $path: $e")
            return
        }
        for (m in list) {
            medalsBySlug[m.slug] = m
            if (!medalsByGrouping.containsKey(folder)) {
                medalsByGrouping[folder] = emptyList()
            }
            medalsByGrouping[folder] = medalsByGrouping[folder]!! + m
        }
    }

    fun bySlug(slug: String): MedalData? = medalsBySlug[slug]

    fun byAchievementId(id: Int): MedalData? =
        medalsBySlug.values.firstOrNull { it.achievementIdInt == id }

    fun byGrouping(grouping: String): List<MedalData> =
        medalsByGrouping[grouping] ?: emptyList()

    val allMedals: List<MedalData> get() = medalsBySlug.values.toList()

    val isReady: Boolean get() = initialized

    val count: Int get() = medalsBySlug.size

    /**
     * Builds a list of ALL medals with achieved status.
     */
    fun buildAllMedalDisplay(
        achievedIds: Set<Int>,
        achievedSlugs: Set<String>,
        achievedAtById: Map<Int, String>,
    ): List<MedalDisplay> = allMedals.map { m ->
        val achieved = (m.achievementIdInt?.let { achievedIds.contains(it) } == true) ||
            (m.slug.isNotEmpty() && achievedSlugs.contains(m.slug))
        MedalDisplay(
            medal = m,
            achieved = achieved,
            achievedAt = if (achieved) m.achievementIdInt?.let { achievedAtById[it] } else null,
        )
    }
}
