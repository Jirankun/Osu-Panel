/* MIT License — Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio */
package net.aokaze.osupanel.feature.base

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import net.aokaze.osupanel.data.remote.classifyError

/**
 * Base ViewModel with shared helpers — eliminates duplicate [classify]
 * functions across ProfileViewModel, RankingsViewModel,
 * BeatmapDetailViewModel, and MapsViewModel.
 */
open class BaseViewModel(application: Application) : AndroidViewModel(application) {

    /** Classify a throwable into a user-friendly message. */
    protected fun classify(e: Throwable): String =
        classifyError(getApplication(), e).message
}
