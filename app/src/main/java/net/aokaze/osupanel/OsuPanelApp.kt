/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import net.aokaze.osupanel.core.theme.OsuColors
import net.aokaze.osupanel.data.medal.MedalService
import net.aokaze.osupanel.di.AppContainer
import java.util.Locale

class OsuPanelApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    /**
     * Global Coil ImageLoader — used by ALL image views (AsyncImage,
     * SubcomposeAsyncImage, dsb). `ImageDecoderDecoder` menambah dukungan
     * animated GIFs (avatars / covers / other images may be GIFs).
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(ImageDecoderDecoder.Factory())
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Functional colors from resources (single source of truth).
        OsuColors.init(this)
        // Load the medal index from assets once — used by dashboard & profile.
        MedalService.init(this)
        // Local bookmark storage (no API involved).
        net.aokaze.osupanel.data.local.BookmarkStore.init(this)
        // Restore persisted language preference.
        val lang = getSharedPreferences("language_prefs", MODE_PRIVATE)
            .getString("app_language", "system") ?: "system"
        if (lang != "system") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(Locale(lang)))
        }
    }
}
