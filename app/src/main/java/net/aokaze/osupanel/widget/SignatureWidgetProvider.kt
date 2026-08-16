/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.util.isNetworkAvailable
import net.aokaze.osupanel.data.local.WidgetDataStore

/**
 * Base class for the two signature-rendered widgets (Profile Large & PP).
 *
 * Both widgets share the exact same update flow, implemented once here:
 * 1. Show the last good render from [WidgetBitmapCache] instantly
 *    (offline-safe, no placeholder flash).
 * 2. In the background, refresh cover & avatar from the network — ONLY when
 *    online; otherwise keep the cached render (never downgrade to a
 *    photo-less one). The fresh render is saved back to the cache.
 *
 * Subclasses only supply their render specifics: layout ids, target image
 * sizes, the cache key, the data to draw, and the render call.
 */
abstract class SignatureWidgetProvider : AppWidgetProvider() {

    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Layout specifics ──
    protected abstract val layoutId: Int
    protected abstract val imageViewId: Int
    protected abstract val clickRootId: Int

    /** Unique PendingIntent request code per widget type. */
    protected abstract val requestCode: Int

    // ── Placeholder canvas (1x px — no SCALE) ──
    protected abstract val placeholderW: Int
    protected abstract val placeholderH: Int
    protected abstract val placeholderTextSize: Float

    // ── Network image targets ──
    protected abstract val coverTargetW: Int
    protected abstract val coverTargetH: Int
    protected abstract val avatarTarget: Int

    // ── Subclass behavior ──
    protected abstract fun cacheKey(prefs: SharedPreferences): String

    protected abstract fun buildData(context: Context, prefs: SharedPreferences, username: String): SignatureRenderer.Data

    /** Renders the final signature (full or mini template). */
    protected abstract fun render(context: Context, data: SignatureRenderer.Data): Bitmap

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val prefs = context.getSharedPreferences(WidgetDataStore.PREFS_NAME, Context.MODE_PRIVATE)
        val username = prefs.getString(WidgetDataStore.KEY_USERNAME, null)
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.widget_username_placeholder)

        val data = buildData(context, prefs, username)

        // 1. Show the last good render instantly (offline-safe, no placeholder flash).
        val cacheKey = cacheKey(prefs)
        val cached = WidgetBitmapCache.load(context, cacheKey)
        val views = RemoteViews(context.packageName, layoutId)
        views.setImageViewBitmap(imageViewId, cached ?: placeholderBitmap(context, username))
        views.setOnClickPendingIntent(clickRootId, WidgetSupport.openAppPendingIntent(context, requestCode))
        appWidgetManager.updateAppWidget(appWidgetId, views)

        // 2. Refresh cover & avatar from the network ONLY when online. Without a
        //    network the cached render is kept — never downgrade to a photo-less one.
        Thread {
            if (!isNetworkAvailable(context)) return@Thread

            val coverUrl = prefs.getString(WidgetDataStore.KEY_COVER_URL, null)
            val avatarUrl = prefs.getString(WidgetDataStore.KEY_AVATAR_URL, null)
            val cover = if (!coverUrl.isNullOrEmpty()) {
                WidgetSupport.loadScaled(coverUrl, coverTargetW, coverTargetH)
            } else null
            val avatar = if (!avatarUrl.isNullOrEmpty()) {
                WidgetSupport.loadScaled(avatarUrl, avatarTarget, avatarTarget)
            } else null

            // Partial fetch while a good render is already showing → keep it intact.
            if (cached != null && (cover == null || avatar == null)) return@Thread

            val bitmap = runCatching {
                render(context, data.copy(cover = cover, avatar = avatar))
            }.getOrElse { cached ?: placeholderBitmap(context, username) }

            WidgetBitmapCache.save(context, cacheKey, bitmap)

            mainHandler.post {
                try {
                    val resultViews = RemoteViews(context.packageName, layoutId)
                    resultViews.setImageViewBitmap(imageViewId, bitmap)
                    resultViews.setOnClickPendingIntent(clickRootId, WidgetSupport.openAppPendingIntent(context, requestCode))
                    appWidgetManager.updateAppWidget(appWidgetId, resultViews)
                } catch (_: Exception) {
                    // Update failed — the placeholder stays visible.
                }
            }
        }.start()
    }

    /** Dark placeholder + username, so the widget is never empty. */
    private fun placeholderBitmap(context: Context, username: String): Bitmap {
        val bmp = Bitmap.createBitmap(placeholderW, placeholderH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(22, 24, 34))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = placeholderTextSize
            textAlign = Paint.Align.CENTER
            typeface = runCatching {
                context.resources.getFont(R.font.comfortaa_bold)
            }.getOrNull() ?: Typeface.DEFAULT_BOLD
        }
        val fm = paint.fontMetrics
        canvas.drawText(
            username,
            placeholderW / 2f,
            placeholderH / 2f - (fm.top + fm.bottom) / 2f,
            paint,
        )
        return bmp
    }
}
