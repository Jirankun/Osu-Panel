/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.aokaze.osupanel.core.config.Env

/**
 * Update popup ViewModel — called once per app open (when the status
 * is AUTHENTICATED). The [UpdateChecker] result cache ensures the popup
 * shows again on the next open even if the user picked "Not Now".
 */
class UpdateCheckViewModel(application: Application) : AndroidViewModel(application) {

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    init {
        // Instantly show cached popup (SharedPreferences read is fast on main).
        _updateInfo.value = UpdateChecker.loadCachedInfo(getApplication())
    }

    /** Full check — runs network in background; updates popup if newer found. */
    fun checkForUpdate() {
        viewModelScope.launch {
            val result = UpdateChecker.check(getApplication())
            if (result != null) _updateInfo.value = result
        }
    }

    /**
     * "Not Now" / dismiss — just closes; the cache stays so the popup
     * reappears on the next app open (user is always nudged to update).
     */
    fun dismiss() {
        _updateInfo.value = null
    }

    /** "Let's Update" — opens the app detail page in an external browser. */
    fun openUpdatePage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Env.UPDATE_STORE_PAGE_URL))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
        _updateInfo.value = null
    }
}
