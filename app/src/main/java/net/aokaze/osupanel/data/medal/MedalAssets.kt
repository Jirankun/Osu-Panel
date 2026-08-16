/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.medal

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Process
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache + preload bitmap SEMUA medal.
 *
 * All medal PNGs from assets are decoded ONCE in the background when the app
 * starts (called from [MedalService.init]) and held in a global cache.
 * Result: medal tiles appear IMMEDIATELY with their real image in the UI — no
 * fallback icon, no per-scroll decode, no "loading" flash.
 */
object MedalAssets {

    /**
     * Cache global: path asset → ImageBitmap.
     *
     * Cache size is computed to HOLD ALL medals (352 × 209×209×4 ≈ 59MB):
     * with a smaller cache, off-screen tiles are evicted and re-decoded
     * when scrolled back → lag + "reloading" images. With every
     * medal kept in cache, scrolling purely reads the cache (no scroll-time decode).
     */
    private val cache = object : LruCache<String, ImageBitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * 4
    }

    /**
     * Last decode-failure time per path (elapsedRealtime ms).
     * Failures are NOT permanent — the path is retried after [RETRY_DELAY_MS]
     * so tiles that fail during preload recover by themselves without a restart.
     */
    private val failedAt = ConcurrentHashMap<String, Long>()

    /** Delay before retrying a path that failed to decode. */
    private const val RETRY_DELAY_MS = 5_000L

    /** Maximum bitmap side size (px) — enough for 48–56dp tiles + 100dp dialog. */
    private const val MAX_DIMENSION = 256

    /**
     * Preloads all medals on a low-priority background thread — it does not
     * steal CPU from the UI during app start. Called once after the JSON index
     * finishes loading; after this ALL medal tiles appear instantly.
     */
    fun preloadAll(context: Context, paths: Collection<String>) {
        Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            for (path in paths) {
                get(context, path)
            }
        }.start()
    }

    /**
     * Read the cache ONLY (no decode, no IO) — for an instant first frame.
     * Null if not preloaded yet; call [get] to decode.
     */
    fun peek(path: String): ImageBitmap? = cache.get(path)

    /**
     * Fetch a medal bitmap — check the cache first; if it was not preloaded,
     * decode from assets (with subsampling to save memory).
     *
     * Decode failures are recorded with a timestamp and retried after
     * [RETRY_DELAY_MS] — transient failures (e.g. while preloading all medals)
     * never leave a tile empty forever.
     */
    fun get(context: Context, path: String): ImageBitmap? {
        cache.get(path)?.let { return it }

        val lastFailure = failedAt[path]
        if (lastFailure != null && SystemClock.elapsedRealtime() - lastFailure < RETRY_DELAY_MS) {
            return null
        }

        val bitmap = decodeAsset(context, path)
        if (bitmap != null) {
            cache.put(path, bitmap)
            failedAt.remove(path)
        } else {
            failedAt[path] = SystemClock.elapsedRealtime()
        }
        return bitmap
    }

    private fun decodeAsset(context: Context, path: String): ImageBitmap? {
        return try {
            // Read bounds first to compute subsampling (the asset is opened again —
            // the first stream was consumed for bounds).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }

            var sample = 1
            val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxSide / (sample * 2) >= MAX_DIMENSION) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
            bitmap?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}
