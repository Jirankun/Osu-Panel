/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.cardgen

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.aokaze.osupanel.OsuPanelApp
import net.aokaze.osupanel.R
import net.aokaze.osupanel.data.model.UserDto
import net.aokaze.osupanel.data.skills.SkillsFetcher
import net.aokaze.osupanel.widget.SignatureDataMapper
import net.aokaze.osupanel.widget.SignatureRenderer
import net.aokaze.osupanel.widget.WidgetMode
import net.aokaze.osupanel.widget.WidgetSupport

/** Templates available in the generator — same as the original stat-sign site. */
const val TEMPLATE_STATS = "stats"
const val TEMPLATE_SKILLS = "skills"
const val TEMPLATE_MINI = "mini"

data class CardGenUiState(
    val mode: String = "std",
    val template: String = TEMPLATE_STATS,
    val bitmap: Bitmap? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Card Generator — build a stat-sign style card for ANY user (not just the
 * logged-in one), right from the Profile page.
 *
 * - Stats: the profile loads the default game mode; switching the mode
 *   fetches `GET /users/{id}/{mode}` (reusing [net.aokaze.osupanel.data.repository.ContentRepository.getUserByMode]).
 * - Images (cover/avatar) are downloaded ONCE and reused for every re-render.
 * - Skills (osuskills.com) are only fetched when the Skills template is picked.
 * - Rendering is exactly the widget's [SignatureRenderer] — same bitmap,
 *   same card, now shareable.
 */
class CardGenViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OsuPanelApp).container
    private val repository = container.contentRepository
    private val appContext = getApplication<Application>()

    private val _state = MutableStateFlow(CardGenUiState())
    val state: StateFlow<CardGenUiState> = _state.asStateFlow()

    private var userId: Int = -1
    private var baseUser: UserDto? = null
    private var cover: Bitmap? = null
    private var avatar: Bitmap? = null
    private var skills: SignatureRenderer.SkillsData? = null
    private var renderJob: Job? = null

    /** Bind the generator to the profile currently open. */
    fun init(userId: Int, user: UserDto) {
        if (this.userId == userId && baseUser != null) return
        this.userId = userId
        baseUser = user
        cover = null
        avatar = null
        skills = null
        refresh()
    }

    fun setMode(mode: String) {
        if (mode == _state.value.mode) return
        _state.value = _state.value.copy(mode = mode, error = null)
        refresh()
    }

    fun setTemplate(template: String) {
        if (template == _state.value.template) return
        _state.value = _state.value.copy(template = template, error = null)
        refresh()
    }

    /** Re-render the current selection (mode/template) on a background thread. */
    private fun refresh() {
        val user = baseUser ?: return
        val mode = _state.value.mode
        val template = _state.value.template
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // 1) Stats: profile data is the default mode; fetch the selected one.
                var targetUser = user
                if (mode != "std") {
                    val modeStats = runCatching {
                        repository.getUserByMode(user.id, WidgetMode.apiMode(mode)).statistics
                    }.getOrNull()
                    // Offline / fetch failed → keep the base stats (still renders).
                    if (modeStats != null) targetUser = user.copy(statistics = modeStats)
                }
                // 2) Cover & avatar — downloaded ONCE, reused for every re-render.
                ensureImages(user)
                // 3) Skills — only for the skills template (osuskills.com).
                val skillsData = if (template == TEMPLATE_SKILLS) {
                    skills ?: SkillsFetcher.fetch(user.username.orEmpty()).also { skills = it }
                } else null
                // 4) Render (SignatureRenderer is thread-safe).
                val data = SignatureDataMapper.buildData(targetUser, mode, template, skillsData)
                    .copy(cover = cover, avatar = avatar)
                val bitmap = withContext(Dispatchers.Default) {
                    if (template == TEMPLATE_MINI) {
                        SignatureRenderer.renderMini(appContext, data)
                    } else {
                        SignatureRenderer.render(appContext, data)
                    }
                }
                _state.value = _state.value.copy(bitmap = bitmap, isLoading = false, error = null)
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: appContext.getString(R.string.error_generic),
                )
            }
        }
    }

    private suspend fun ensureImages(user: UserDto) {
        if (cover != null && avatar != null) return
        withContext(Dispatchers.IO) {
            if (cover == null) {
                cover = user.coverUrl?.let { WidgetSupport.loadScaled(it, 1100, 480) }
            }
            if (avatar == null) {
                avatar = user.avatarUrl?.let { WidgetSupport.loadScaled(it, 180, 180) }
            }
        }
    }

    /** Save the current bitmap to cache + return its content Uri (null on failure). */
    suspend fun prepareShare(): Uri? {
        val bmp = _state.value.bitmap ?: return null
        val user = baseUser ?: return null
        val name = buildString {
            append("osupanel_")
            append((user.username ?: "user").replace(Regex("[^A-Za-z0-9_-]"), "_"))
            append("_").append(_state.value.mode)
            append("_").append(_state.value.template)
        }
        return withContext(Dispatchers.IO) {
            CardShare.save(appContext, bmp, "$name.png")
        }
    }
}
