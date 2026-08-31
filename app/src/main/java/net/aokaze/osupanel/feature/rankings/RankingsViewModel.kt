/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.rankings

import android.app.Application
import net.aokaze.osupanel.feature.base.BaseViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.aokaze.osupanel.OsuPanelApp
import net.aokaze.osupanel.data.local.DataCache
import net.aokaze.osupanel.data.model.RankingEntryDto
import net.aokaze.osupanel.data.model.UserDto

/** One list entry — a ranking API result or a search result. */
data class RankingRow(
    val user: UserDto,
    val globalRank: Int? = null,
    val pp: Double = 0.0,
)

data class RankingsUiState(
    val rows: List<RankingRow> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
    /** Pull-to-refresh in progress (indicator in the list). */
    val isRefreshing: Boolean = false,
    // ── Search ──
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val isSearchingApi: Boolean = false,
    val searchResults: List<RankingRow> = emptyList(),
    /** A search has been submitted (keyboard search tap) — false while typing. */
    val hasSearched: Boolean = false,
) {
    /** Rows shown — search results when submitted, otherwise the ranking list. */
    val displayRows: List<RankingRow>
        get() = when {
            searchQuery.isEmpty() -> rows
            !hasSearched -> rows               // still typing — not searched yet
            isSearchingApi -> rows             // searching — keep showing the old list
            searchResults.isNotEmpty() -> searchResults
            else -> emptyList()                // search finished with no results → empty state
        }
}

/**
 * Rankings ViewModel:
 * global performance ranking + country filter + global user search.
 * Search ONLY runs on submit (keyboard search button) —
 * typing does not trigger an API request.
 */
class RankingsViewModel(application: Application) : BaseViewModel(application) {

    private val container = (application as OsuPanelApp).container
    private val repository = container.contentRepository

    private val _state = MutableStateFlow(RankingsUiState())
    val state: StateFlow<RankingsUiState> = _state.asStateFlow()

    private var page = 1
    private var selectedCountry: String? = null
    private var searchJob: Job? = null

    /** Currently selected country code (null = All) — read by the UI. */
    val selectedCountryCode: String? get() = selectedCountry

    init {
        loadRankings()
    }

    fun selectCountry(country: String?) {
        if (selectedCountry == country) return
        selectedCountry = country
        page = 1
        _state.value = _state.value.copy(rows = emptyList(), error = null)
        loadRankings()
    }

    fun onScrollNearEnd() {
        val s = _state.value
        // Anti chain-load does NOT use a time cooldown (the user must be able to
        // scroll and load the next page IMMEDIATELY without waiting). The
        // chain guard lives in the screen: the trigger only fires when the index
        // of the last item CHANGES (distinctUntilChanged) — a user idling at the
        // bottom triggers nothing; only active scrolling triggers it.
        if (!s.isLoading && !s.isLoadingMore && s.hasMore && s.searchQuery.isEmpty()) {
            page++
            loadRankings()
        }
    }

    fun refresh() {
        DataCache.invalidate(DataCache.rankings("osu", "performance", 1, selectedCountry))
        page = 1
        _state.value = _state.value.copy(error = null, isRefreshing = true)
        loadRankings()
    }

    private fun loadRankings() {
        viewModelScope.launch {
            val s = _state.value
            if (s.isLoading || s.isLoadingMore) return@launch
            // First page = full loading; next pages = loadingMore
            // (small indicator under the list, not a full screen).
            val loadingMore = page > 1 && s.rows.isNotEmpty()
            _state.value = if (loadingMore) {
                s.copy(isLoadingMore = true, error = null)
            } else {
                s.copy(isLoading = true, error = null)
            }
            try {
                val cacheKey = DataCache.rankings("osu", "performance", page, selectedCountry)
                val cached = if (page == 1) DataCache.get<List<RankingEntryDto>>(cacheKey) else null
                val entries = if (cached != null) {
                    cached
                } else {
                    val fresh = repository.getRankings(
                        mode = "osu",
                        type = "performance",
                        page = page,
                        country = selectedCountry,
                    )
                    DataCache.set(cacheKey, fresh)
                    fresh
                }
                val rows = entries.map { RankingRow(user = it.user, globalRank = it.globalRank, pp = it.pp) }
                _state.value = if (page == 1) {
                    _state.value.copy(
                        rows = rows,
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = entries.size >= 50,
                        error = null,
                    )
                } else {
                    _state.value.copy(
                        rows = _state.value.rows + rows,
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = entries.size >= 50,
                        error = null,
                    )
                }
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                    error = classify(e),
                )
            }
        }
    }

    fun toggleSearch() {
        if (_state.value.isSearchOpen) {
            closeSearch()
        } else {
            _state.value = _state.value.copy(isSearchOpen = true)
        }
    }

    /** Text changes while typing — ONLY updates the query, NO API request. */
    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        _state.value = _state.value.copy(
            searchQuery = query,
            hasSearched = false,
            searchResults = emptyList(),
            isSearchingApi = false,
        )
    }

    /** Submit search — called when the user presses the keyboard search button. */
    fun onSearchSubmit() {
        val q = _state.value.searchQuery
        _state.value = _state.value.copy(hasSearched = true)
        if (q.length < 2) {
            _state.value = _state.value.copy(
                searchResults = emptyList(),
                isSearchingApi = false,
            )
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            fetchGlobalSearch(q)
        }
    }

    private suspend fun fetchGlobalSearch(query: String) {
        _state.value = _state.value.copy(isSearchingApi = true)
        try {
            val wrapped = mutableListOf<RankingRow>()
            val id = query.toIntOrNull()
            if (id != null) {
                try {
                    val user = repository.getUser(id)
                    wrapped += RankingRow(
                        user = user,
                        globalRank = user.statistics?.globalRank,
                        pp = user.statistics?.pp ?: 0.0,
                    )
                } catch (_: Throwable) {
                    wrapped += RankingRow(
                        user = UserDto(id = id, username = "User #$id"),
                    )
                }
            } else {
                // Partial/similar name → the osu! search endpoint (1 request,
                // ALL matches — not just exact). Search results have no
                // statistics → rank/pp show as N/A.
                wrapped += repository.searchUsers(query).map {
                    RankingRow(
                        user = it,
                        globalRank = it.statistics?.globalRank,
                        pp = it.statistics?.pp ?: 0.0,
                    )
                }
            }
            _state.value = _state.value.copy(
                searchResults = wrapped,
                isSearchingApi = false,
            )
        } catch (_: Throwable) {
            _state.value = _state.value.copy(isSearchingApi = false)
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _state.value = _state.value.copy(
            isSearchOpen = false,
            searchQuery = "",
            hasSearched = false,
            searchResults = emptyList(),
            isSearchingApi = false,
        )
    }

    fun clearSearchQuery() {
        searchJob?.cancel()
        _state.value = _state.value.copy(
            searchQuery = "",
            hasSearched = false,
            searchResults = emptyList(),
            isSearchingApi = false,
        )
    }


    companion object {
        val countryCodes = listOf(
            "ID", "JP", "KR", "US", "GB", "DE", "FR", "BR", "CA", "AU", "RU", "CN",
            "TW", "HK", "SG", "MY", "PH", "TH", "VN", "PL", "FI", "SE", "NO", "DK",
            "NL", "BE", "CH", "AT", "IT", "ES", "PT", "MX", "AR", "CL", "CO", "PE",
        )
    }
}
