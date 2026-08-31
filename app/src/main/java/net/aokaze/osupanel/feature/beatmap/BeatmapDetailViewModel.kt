/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.beatmap

import android.app.Application
import android.util.Log
import net.aokaze.osupanel.feature.base.BaseViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.aokaze.osupanel.OsuPanelApp
import net.aokaze.osupanel.data.local.DataCache
import net.aokaze.osupanel.data.model.BeatmapsetDto
import net.aokaze.osupanel.data.model.ScoreDto

data class BeatmapDetailUiState(
    val beatmapset: BeatmapsetDto? = null,
    val selectedBeatmapId: Int? = null,
    val playedBeatmapIds: Set<Int> = emptySet(),
    val scores: List<ScoreDto> = emptyList(),
    val myScore: ScoreDto? = null,
    val creatorAvatarUrl: String? = null,
    val isFavourited: Boolean = false,
    val isLoading: Boolean = true,
    val scoresLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Beatmap Detail ViewModel — beatmapset + selected difficulty (from best/recent
 * scores, fallback difficulty first) + leaderboard + the user's score.
 */
class BeatmapDetailViewModel(application: Application) : BaseViewModel(application) {

    private val container = (application as OsuPanelApp).container
    private val repository = container.contentRepository

    private val _state = MutableStateFlow(BeatmapDetailUiState())
    val state: StateFlow<BeatmapDetailUiState> = _state.asStateFlow()

    private var loadedBeatmapsetId: Int? = null

    /**
     * Per-difficulty score cache (in-memory, per session).
     * Key = beatmapId, Value = Pair(scores, myScore).
     * Avoids re-fetching leaderboard when the user simply switches difficulty.
     */
    private val scoreCache = mutableMapOf<Int, Pair<List<ScoreDto>, ScoreDto?>>()

    fun load(beatmapsetId: Int, currentUserId: Int?) {
        if (loadedBeatmapsetId == beatmapsetId && _state.value.beatmapset != null) return
        loadedBeatmapsetId = beatmapsetId
        viewModelScope.launch {
            _state.value = BeatmapDetailUiState(isLoading = true)
            try {
                // Per-session cache: beatmapset, best/recent scores (same keys
                // as Profile/Maps — already loaded → 0 requests), and the
                // creator profile. The refresh button invalidates all these keys.
                val bms = repository.getBeatmapset(
                    beatmapsetId,
                    cacheKey = DataCache.beatmapset(beatmapsetId),
                )
                val beatmaps = bms.beatmaps

                // Find the difficulty previously played from best + recent scores.
                var best = emptyList<ScoreDto>()
                var recent = emptyList<ScoreDto>()
                if (currentUserId != null) {
                    runCatching {
                        best = repository.getUserScores(
                            currentUserId, "best", limit = 100,
                            cacheKey = DataCache.bestScores(currentUserId),
                        )
                    }
                    runCatching {
                        recent = repository.getUserScores(
                            currentUserId, "recent", limit = 50,
                            cacheKey = DataCache.recentScores(currentUserId),
                        )
                    }
                }

                val playedIds = (best + recent)
                    .filter { it.beatmapset?.id == beatmapsetId }
                    .mapNotNull { it.beatmap?.id }
                    .toSet()

                var targetId = (best + recent)
                    .firstOrNull { it.beatmapset?.id == beatmapsetId }
                    ?.beatmap?.id

                // Fallback: check the first few difficulties directly.
                if (targetId == null && currentUserId != null) {
                    for (b in beatmaps.take(5)) {
                        if (repository.getUserBeatmapScore(b.id, currentUserId) != null) {
                            targetId = b.id
                            break
                        }
                    }
                }
                targetId = targetId ?: beatmaps.firstOrNull()?.id

                // Creator avatar (optional — failure is fine).
                // Note: the beatmapset v2 API response sends `user_id`
                // (legacy) as the creator ID; `creator_id` is often null.
                val creatorUserId = bms.creatorId ?: bms.userId
                var creatorAvatar: String? = null
                creatorUserId?.let { creatorId ->
                    runCatching {
                        creatorAvatar = repository.getUser(
                            creatorId,
                            cacheKey = DataCache.profile(creatorId),
                        ).avatarUrl
                    }
                }

                var scores = emptyList<ScoreDto>()
                var myScore: ScoreDto? = null
                if (targetId != null) {
                    runCatching { scores = repository.getBeatmapScores(targetId, cacheKey = DataCache.beatmapScores(targetId)) }
                    if (currentUserId != null) {
                        myScore = repository.getUserBeatmapScore(targetId, currentUserId, cacheKey = DataCache.userBeatmapScore(targetId, currentUserId))
                    }
                    // Cache initial difficulty so switchDifficulty is instant.
                    scoreCache[targetId] = scores to myScore
                }

                // Check if this mapset is in user's favourites.
                var favourited = false
                if (currentUserId != null) {
                    runCatching {
                        val favs = repository.getMyFavourites(
                            cacheKey = DataCache.favourites(currentUserId),
                        )
                        favourited = favs.any { it.id == beatmapsetId }
                    }
                }

                _state.value = BeatmapDetailUiState(
                    beatmapset = bms,
                    selectedBeatmapId = targetId,
                    playedBeatmapIds = playedIds,
                    scores = scores,
                    myScore = myScore,
                    creatorAvatarUrl = creatorAvatar,
                    isFavourited = favourited,
                    isLoading = false,
                    error = null,
                )
            } catch (e: Throwable) {
                _state.value = BeatmapDetailUiState(
                    isLoading = false,
                    error = classify(e),
                )
            }
        }
    }

    fun refresh(beatmapsetId: Int, currentUserId: Int?) {
        // Reset the guard + invalidate ALL cached data (beatmapset,
        // best/recent scores, creator profile) so load() really refetches
        // everything — same as ProfileViewModel.refresh().
        DataCache.invalidate(DataCache.beatmapset(beatmapsetId))
        currentUserId?.let {
            DataCache.invalidate(DataCache.bestScores(it))
            DataCache.invalidate(DataCache.recentScores(it))
        }
        scoreCache.clear()
        DataCache.invalidate(DataCache.beatmapScores(beatmapsetId))
        loadedBeatmapsetId = null
        load(beatmapsetId, currentUserId)
    }

    fun switchDifficulty(beatmapId: Int, currentUserId: Int?) {
        // Check in-memory cache first — instant switch, no API call.
        val cached = scoreCache[beatmapId]
        if (cached != null) {
            _state.value = _state.value.copy(
                selectedBeatmapId = beatmapId,
                scores = cached.first,
                myScore = cached.second,
                scoresLoading = false,
            )
            return
        }
        // Not cached — fetch from API, then cache.
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedBeatmapId = beatmapId,
                scoresLoading = true,
                scores = emptyList(),
                myScore = null,
            )
            var scores = emptyList<ScoreDto>()
            runCatching { scores = repository.getBeatmapScores(beatmapId, cacheKey = DataCache.beatmapScores(beatmapId)) }
            val my = if (currentUserId != null) {
                repository.getUserBeatmapScore(beatmapId, currentUserId, cacheKey = DataCache.userBeatmapScore(beatmapId, currentUserId))
            } else null
            scoreCache[beatmapId] = scores to my
            _state.value = _state.value.copy(
                scores = scores,
                myScore = my,
                scoresLoading = false,
            )
        }
    }

    fun toggleFavourite(beatmapsetId: Int, currentUserId: Int?) {
        val currentlyFav = _state.value.isFavourited
        _state.value = _state.value.copy(isFavourited = !currentlyFav)
        viewModelScope.launch {
            runCatching {
                if (currentlyFav) {
                    repository.removeFavourite(beatmapsetId)
                } else {
                    repository.addFavourite(beatmapsetId)
                }
                // Invalidate favourites cache.
                currentUserId?.let { DataCache.invalidate(DataCache.favourites(it)) }
            }.onFailure { e ->
                Log.e("BeatmapDetail", "toggleFavourite failed for $beatmapsetId", e)
                // Revert on error.
                _state.value = _state.value.copy(isFavourited = currentlyFav)
            }
        }
    }

}
