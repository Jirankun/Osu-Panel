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

    /** Check for updates (once per session — no refetch once found). */
    fun checkForUpdate() {
        if (_updateInfo.value != null) return
        viewModelScope.launch {
            _updateInfo.value = UpdateChecker.check(getApplication())
        }
    }

    /**
     * "Not Now" / dismiss — just closes; the cache stays (shows again later).
     * The popup is recorded as shown today → it won't show again until tomorrow.
     */
    fun dismiss() {
        UpdateChecker.recordShownToday(getApplication())
        _updateInfo.value = null
    }

    /** "Let's Update" — opens the app detail page in an external browser. */
    fun openUpdatePage() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Env.UPDATE_STORE_PAGE_URL))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
        UpdateChecker.recordShownToday(getApplication())
        _updateInfo.value = null
    }
}
