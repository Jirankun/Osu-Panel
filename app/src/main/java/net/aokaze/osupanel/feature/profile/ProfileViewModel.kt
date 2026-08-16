/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.aokaze.osupanel.OsuPanelApp
import net.aokaze.osupanel.data.model.MostPlayedBeatmapDto
import net.aokaze.osupanel.data.model.ScoreDto
import net.aokaze.osupanel.data.model.UserDto

data class ProfileUiState(
    val user: UserDto? = null,
    val bestScores: List<ScoreDto> = emptyList(),
    val mostPlayed: List<MostPlayedBeatmapDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Profile ViewModel — counterpart of the Flutter `_ProfilePageState`:
 * full user + best scores + most played (separate fetches; one failing
 * does not fail the whole profile).
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OsuPanelApp).container
    private val repository = container.contentRepository

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private var loadedUserId: Int? = null

    fun load(userId: Int) {
        if (loadedUserId == userId && _state.value.user != null) return
        loadedUserId = userId
        viewModelScope.launch {
            _state.value = ProfileUiState(isLoading = true)
            val user = try {
                repository.getUser(userId, cacheKey = net.aokaze.osupanel.data.local.DataCache.profile(userId))
            } catch (e: Throwable) {
                _state.value = ProfileUiState(
                    isLoading = false,
                    error = classify(e),
                )
                return@launch
            }

            var best = emptyList<ScoreDto>()
            try {
                best = repository.getUserScores(
                    userId, "best", limit = 100, offset = 0,
                    cacheKey = net.aokaze.osupanel.data.local.DataCache.bestScores(userId),
                )
            } catch (_: Throwable) { /* non-critical */ }

            var most = emptyList<MostPlayedBeatmapDto>()
            try {
                most = repository.getMostPlayed(
                    userId, limit = 100, offset = 0,
                    cacheKey = net.aokaze.osupanel.data.local.DataCache.mostPlayed(userId),
                )
            } catch (_: Throwable) { /* non-critical */ }

            _state.value = ProfileUiState(
                user = user,
                bestScores = best,
                mostPlayed = most,
                isLoading = false,
            )
        }
    }

    fun refresh(userId: Int) {
        // Invalidate ALL profile data (user + best scores + most played)
        // so refresh truly updates every section, not just the user.
        net.aokaze.osupanel.data.local.DataCache.invalidate(
            net.aokaze.osupanel.data.local.DataCache.profile(userId),
        )
        net.aokaze.osupanel.data.local.DataCache.invalidate(
            net.aokaze.osupanel.data.local.DataCache.bestScores(userId),
        )
        net.aokaze.osupanel.data.local.DataCache.invalidate(
            net.aokaze.osupanel.data.local.DataCache.mostPlayed(userId),
        )
        // IMPORTANT: reset the `loadedUserId` guard — otherwise load() returns
        // immediately because the user was already loaded → refresh does nothing.
        loadedUserId = null
        load(userId)
    }

    private fun classify(e: Throwable): String =
        net.aokaze.osupanel.data.remote.classifyError(getApplication(), e).message
}
