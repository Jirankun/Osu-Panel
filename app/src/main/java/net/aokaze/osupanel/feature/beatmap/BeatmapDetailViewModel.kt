/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.beatmap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
    val isLoading: Boolean = true,
    val scoresLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Beatmap Detail ViewModel — counterpart of `_BeatmapDetailPageState`
 * Flutter: beatmapset + selected difficulty (from best/recent scores,
 * fallback difficulty first) + leaderboard + the user's score.
 */
class BeatmapDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OsuPanelApp).container
    private val repository = container.contentRepository

    private val _state = MutableStateFlow(BeatmapDetailUiState())
    val state: StateFlow<BeatmapDetailUiState> = _state.asStateFlow()

    private var loadedBeatmapsetId: Int? = null

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
                    runCatching { scores = repository.getBeatmapScores(targetId) }
                    if (currentUserId != null) {
                        myScore = repository.getUserBeatmapScore(targetId, currentUserId)
                    }
                }

                _state.value = BeatmapDetailUiState(
                    beatmapset = bms,
                    selectedBeatmapId = targetId,
                    playedBeatmapIds = playedIds,
                    scores = scores,
                    myScore = myScore,
                    creatorAvatarUrl = creatorAvatar,
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
        loadedBeatmapsetId = null
        load(beatmapsetId, currentUserId)
    }

    fun switchDifficulty(beatmapId: Int, currentUserId: Int?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selectedBeatmapId = beatmapId,
                scoresLoading = true,
                scores = emptyList(),
                myScore = null,
            )
            var scores = emptyList<ScoreDto>()
            runCatching { scores = repository.getBeatmapScores(beatmapId) }
            val my = if (currentUserId != null) {
                repository.getUserBeatmapScore(beatmapId, currentUserId)
            } else null
            _state.value = _state.value.copy(
                scores = scores,
                myScore = my,
                scoresLoading = false,
            )
        }
    }

    private fun classify(e: Throwable): String =
        net.aokaze.osupanel.data.remote.classifyError(getApplication(), e).message
}
