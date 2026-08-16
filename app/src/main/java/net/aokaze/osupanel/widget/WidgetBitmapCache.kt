/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Disk cache for the rendered widget bitmaps (PNG).
 *
 * Widgets render cover + avatar (fetched from the network) into a single
 * signature bitmap. Without a disk copy, an offline widget update loses the
 * photos — the network fetch fails, so the re-render has no cover/avatar.
 *
 * Saving the last successful render to disk lets the widget ALWAYS show the
 * full data + photo, even with no network. When the network is back the
 * providers re-fetch fresh images, re-render, and overwrite this cache.
 */
object WidgetBitmapCache {

    private const val DIR = "widget_bitmaps"

    private fun file(context: Context, key: String): File =
        File(context.filesDir, "$DIR/$key.png")

    /** Load a previously saved render, or null when absent/corrupt. */
    fun load(context: Context, key: String): Bitmap? {
        return runCatching {
            val f = file(context, key)
            if (f.exists() && f.length() > 0) BitmapFactory.decodeFile(f.absolutePath) else null
        }.getOrNull()
    }

    /** Persist a render so it survives process death / offline updates. */
    fun save(context: Context, key: String, bitmap: Bitmap) {
        runCatching {
            val target = file(context, key)
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    /** Remove all cached renders (logout — widgets must not show old account data). */
    fun clearAll(context: Context) {
        runCatching {
            File(context.filesDir, DIR).listFiles()?.forEach { it.delete() }
        }
    }
}
