/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.profile

import android.app.Application
import net.aokaze.osupanel.feature.base.BaseViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.aokaze.osupanel.OsuPanelApp
import net.aokaze.osupanel.data.model.MostPlayedBeatmapDto
import net.aokaze.osupanel.data.model.ScoreDto
import net.aokaze.osupanel.data.model.UserDto
import net.aokaze.osupanel.data.skills.SkillsFetcher
import net.aokaze.osupanel.widget.SignatureRenderer

data class ProfileUiState(
    val user: UserDto? = null,
    val bestScores: List<ScoreDto> = emptyList(),
    val mostPlayed: List<MostPlayedBeatmapDto> = emptyList(),
    /** Skill Pulse radar (7 CirclePulse-style skills from the top 10 plays). */
    val skills: SignatureRenderer.SkillsData? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * Profile ViewModel:
 * full user + best scores + most played + skills radar (separate fetches;
 * one failing does not fail the whole profile).
 */
class ProfileViewModel(application: Application) : BaseViewModel(application) {

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

            // Best scores, most played, and the skills radar are all
            // non-critical (one failing must not fail the whole profile) —
            // fetch them IN PARALLEL so the radar finishes together with the
            // rest of the page instead of popping in late / flashing
            // "No skills data" while waiting.
            coroutineScope {
                val bestDeferred = async {
                    runCatching {
                        repository.getUserScores(
                            userId, "best", limit = 100, offset = 0,
                            cacheKey = net.aokaze.osupanel.data.local.DataCache.bestScores(userId),
                        )
                    }.getOrDefault(emptyList())
                }
                val mostDeferred = async {
                    runCatching {
                        repository.getMostPlayed(
                            userId, limit = 100, offset = 0,
                            cacheKey = net.aokaze.osupanel.data.local.DataCache.mostPlayed(userId),
                        )
                    }.getOrDefault(emptyList())
                }
                // Skills radar — osu!skills (osuskills.com, the source used by
                // osu-stats-signature). Fetched by username; fails silently.
                val skillsDeferred = async {
                    runCatching {
                        SkillsFetcher.fetch(user.username.orEmpty())
                    }.getOrNull()
                }

                val best = bestDeferred.await()
                _state.value = ProfileUiState(
                    user = user,
                    bestScores = best,
                    mostPlayed = mostDeferred.await(),
                    skills = skillsDeferred.await(),
                    isLoading = false,
                )
            }
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

}
