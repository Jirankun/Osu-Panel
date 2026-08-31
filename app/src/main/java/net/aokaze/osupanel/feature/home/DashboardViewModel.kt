/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.home

import android.app.Application
import net.aokaze.osupanel.feature.base.BaseViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.aokaze.osupanel.OsuPanelApp
import net.aokaze.osupanel.data.local.DataCache
import net.aokaze.osupanel.data.model.DailyChallengeResponse
import net.aokaze.osupanel.data.model.ScoreDto
import net.aokaze.osupanel.data.skills.SkillsFetcher
import net.aokaze.osupanel.widget.SignatureRenderer

/** Extra dashboard data — top plays (best, for the skills radar). */
data class DashboardUiState(
    val best: List<ScoreDto> = emptyList(),
    /** osu!skills radar (osuskills.com — the osu-stats-signature source). */
    val skills: SignatureRenderer.SkillsData? = null,
    /** Total weighted pp of the top plays (osu! pp system, osutrack-style). */
    val weightedPp: Double = 0.0,
    /** Daily challenge beatmap. */
    val dailyChallenge: DailyChallengeResponse? = null,
    val isLoading: Boolean = false,
)

/**
 * Dashboard extra data: recent plays, top plays (best), most played.
 *
 * Rate-safe: loaded once per session, cached in [DataCache] with dedicated
 * keys (never overwriting the Maps/Profile lists), and refetched only on a
 * manual refresh. Each section fails independently — one bad endpoint does
 * not kill the whole dashboard.
 */
class DashboardViewModel(application: Application) : BaseViewModel(application) {

    private val container = (application as OsuPanelApp).container
    private val repository = container.contentRepository

    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var loadedUserId: Int? = null

    fun load(userId: Int, username: String = "") {
        // Already loaded (or loading) for this user → nothing to do.
        if (loadedUserId == userId) return
        loadedUserId = userId
        viewModelScope.launch {
            _state.value = DashboardUiState(isLoading = true)
            // Best scores feed the skills radar source + weighted pp — most
            // played is its own card. Recent plays were removed from the
            // dashboard (dedicated section in the Maps nav).
            // Use the SAME cache key as Maps so best scores are fetched
            // only once per session (not twice with different keys).
            val best = runCatching {
                repository.getUserScores(
                    userId, "best", limit = 100,
                    cacheKey = DataCache.bestScores(userId),
                )
            }.getOrDefault(emptyList())
            // osu!skills radar — osuskills.com (the osu-stats-signature source).
            val skills = runCatching {
                SkillsFetcher.fetch(username.ifBlank { return@runCatching null })
            }.getOrNull()
            // Total weighted pp — osu!'s own pp weighting (osutrack-style).
            // `weight` is an object {percentage, pp} — the weighted pp is its
            // `pp` field (surfaced via ScoreDto.weightedPp).
            val weightedPp = best.sumOf { it.weightedPp ?: 0.0 }
            val dailyChallenge = runCatching {
                repository.getDailyChallenge(cacheKey = DataCache.dailyChallenge())
            }.getOrNull()
            _state.value = DashboardUiState(
                best = best,
                skills = skills,
                weightedPp = weightedPp,
                dailyChallenge = dailyChallenge,
                isLoading = false,
            )
        }
    }

    /**
     * Invalidate the per-session cache and reload (dashboard refresh button).
     * Unlike [load], the previous values stay visible while fetching — the
     * weighted PP card must not disappear just because the fresh data has
     * not arrived yet; only the numbers update.
     */
    fun refresh(userId: Int, username: String = "") {
        DataCache.invalidate(DataCache.bestScores(userId))
        DataCache.invalidate(DataCache.dailyChallenge())
        loadedUserId = null
        viewModelScope.launch {
            val prev = _state.value
            val best = runCatching {
                repository.getUserScores(
                    userId, "best", limit = 100,
                    cacheKey = DataCache.bestScores(userId),
                )
            }.getOrDefault(prev.best)
            // Keep the previous radar when the fresh fetch fails.
            val skills = runCatching {
                SkillsFetcher.fetch(username.ifBlank { return@runCatching null })
            }.getOrNull() ?: prev.skills
            val weightedPp = best.sumOf { it.weightedPp ?: 0.0 }
            val dailyChallenge = runCatching {
                repository.getDailyChallenge(cacheKey = DataCache.dailyChallenge(), force = true)
            }.getOrNull() ?: prev.dailyChallenge
            _state.value = DashboardUiState(
                best = best,
                skills = skills,
                weightedPp = weightedPp,
                dailyChallenge = dailyChallenge,
                isLoading = false,
            )
        }
    }


}
