/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.maps

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
import net.aokaze.osupanel.data.model.MostPlayedBeatmapDto
import net.aokaze.osupanel.data.model.ScoreDto

/** Status satu tab Maps. */
data class MapsTabState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    val loaded: Boolean = false,
    /** Pull-to-refresh in progress (indicator in the list). */
    val isRefreshing: Boolean = false,
)

/**
 * Maps ViewModel — counterpart of the Flutter per-tab state
 * (Last Play / Best Scores / Most Played / Loved) with pagination
 * 100 per page + per-session cache.
 */
class MapsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OsuPanelApp).container
    private val repository = container.contentRepository

    private val _recent = MutableStateFlow(MapsTabState<ScoreDto>())
    val recent: StateFlow<MapsTabState<ScoreDto>> = _recent.asStateFlow()

    private val _best = MutableStateFlow(MapsTabState<ScoreDto>())
    val best: StateFlow<MapsTabState<ScoreDto>> = _best.asStateFlow()

    private val _mostPlayed = MutableStateFlow(MapsTabState<MostPlayedBeatmapDto>())
    val mostPlayed: StateFlow<MapsTabState<MostPlayedBeatmapDto>> = _mostPlayed.asStateFlow()

    private val _loved = MutableStateFlow(MapsTabState<BeatmapsetDto>())
    val loved: StateFlow<MapsTabState<BeatmapsetDto>> = _loved.asStateFlow()

    fun load(userId: Int) {
        loadRecent(userId)
        loadBest(userId)
        loadMostPlayed(userId)
        loadLoved(userId)
    }

    fun refresh(userId: Int) {
        DataCache.invalidate(DataCache.recentScores(userId))
        DataCache.invalidate(DataCache.bestScores(userId))
        DataCache.invalidate(DataCache.mostPlayed(userId))
        DataCache.invalidate(DataCache.favourites(userId))
        loadRecent(userId, force = true)
        loadBest(userId, force = true)
        loadMostPlayed(userId, force = true)
        loadLoved(userId, force = true)
    }

    fun loadMoreRecent(userId: Int) = loadMore(
        userId,
        _recent,
        type = "recent",
        pageSize = PAGE_SIZE,
        key = DataCache.recentScores(userId),
    ) { offset -> repository.getUserScores(userId, "recent", limit = PAGE_SIZE, offset = offset) }

    fun loadMoreBest(userId: Int) = loadMore(
        userId,
        _best,
        type = "best",
        pageSize = PAGE_SIZE,
        key = DataCache.bestScores(userId),
    ) { offset -> repository.getUserScores(userId, "best", limit = PAGE_SIZE, offset = offset) }

    fun loadMoreMostPlayed(userId: Int) = loadMore(
        userId,
        _mostPlayed,
        type = "most_played",
        pageSize = PAGE_SIZE,
        key = DataCache.mostPlayed(userId),
    ) { offset -> repository.getMostPlayed(userId, limit = PAGE_SIZE, offset = offset) }

    fun loadMoreLoved(userId: Int) = loadMore(
        userId,
        _loved,
        type = "loved",
        pageSize = PAGE_SIZE,
        key = DataCache.favourites(userId),
    ) { offset -> repository.getFavourites(userId, limit = PAGE_SIZE, offset = offset) }

    private fun loadRecent(userId: Int, force: Boolean = false) = loadFirst(
        _recent,
        key = DataCache.recentScores(userId),
        force = force,
    ) { repository.getUserScores(userId, "recent", limit = PAGE_SIZE, offset = 0) }

    private fun loadBest(userId: Int, force: Boolean = false) = loadFirst(
        _best,
        key = DataCache.bestScores(userId),
        force = force,
    ) { repository.getUserScores(userId, "best", limit = PAGE_SIZE, offset = 0) }

    private fun loadMostPlayed(userId: Int, force: Boolean = false) = loadFirst(
        _mostPlayed,
        key = DataCache.mostPlayed(userId),
        force = force,
    ) { repository.getMostPlayed(userId, limit = PAGE_SIZE, offset = 0) }

    private fun loadLoved(userId: Int, force: Boolean = false) = loadFirst(
        _loved,
        key = DataCache.favourites(userId),
        force = force,
    ) { repository.getFavourites(userId, limit = PAGE_SIZE, offset = 0) }

    /** Load the first page (with cache). */
    private fun <T> loadFirst(
        state: MutableStateFlow<MapsTabState<T>>,
        key: String,
        force: Boolean,
        loader: suspend () -> List<T>,
    ) {
        viewModelScope.launch {
            if (state.value.loaded && !force) return@launch
            // isRefreshing is only set when data already exists (refresh), not
            // during the first load (full loading).
            state.value = state.value.copy(
                isLoading = true,
                error = null,
                isRefreshing = state.value.loaded,
            )
            try {
                val items = if (!force && DataCache.has(key)) {
                    DataCache.get(key) ?: emptyList()
                } else {
                    val fresh = loader()
                    DataCache.set(key, fresh)
                    fresh
                }
                state.value = MapsTabState(
                    items = items,
                    hasMore = items.size >= PAGE_SIZE,
                    loaded = true,
                )
            } catch (e: Throwable) {
                state.value = state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = classify(e),
                    loaded = true,
                )
            }
        }
    }

    /** Load the next page (append). */
    private fun <T> loadMore(
        userId: Int,
        state: MutableStateFlow<MapsTabState<T>>,
        type: String,
        pageSize: Int,
        key: String,
        loader: suspend (offset: Int) -> List<T>,
    ) {
        if (state.value.isLoadingMore || !state.value.hasMore || state.value.isLoading) return
        viewModelScope.launch {
            state.value = state.value.copy(isLoadingMore = true)
            try {
                val offset = state.value.items.size
                val more = loader(offset)
                state.value = if (more.isEmpty()) {
                    state.value.copy(isLoadingMore = false, hasMore = false)
                } else {
                    val all = state.value.items + more
                    DataCache.set(key, all)
                    state.value.copy(
                        items = all,
                        isLoadingMore = false,
                        hasMore = more.size >= pageSize,
                    )
                }
            } catch (_: Throwable) {
                state.value = state.value.copy(isLoadingMore = false)
            }
        }
    }

    private fun classify(e: Throwable): String =
        net.aokaze.osupanel.data.remote.classifyError(getApplication(), e).message

    companion object {
        private const val PAGE_SIZE = 100
    }
}
