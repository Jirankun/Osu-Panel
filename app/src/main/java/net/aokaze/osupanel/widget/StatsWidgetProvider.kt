/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import kotlin.math.roundToInt
import net.aokaze.osupanel.R
import net.aokaze.osupanel.data.local.WidgetDataStore

/**
 * Osu! Panel — Rank & Level Home Screen Widget.
 *
 * Just three things: global rank, country rank, and the level with its progress
 * bar toward the next level. Data is read from [WidgetDataStore] (written
 * by the main app on login / app open / refresh). Tap → opens the app.
 */
class StatsWidgetProvider : AppWidgetProvider() {

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
        val views = RemoteViews(context.packageName, R.layout.stats_widget_layout)
        val prefs = context.getSharedPreferences(
            WidgetDataStore.PREFS_NAME,
            Context.MODE_PRIVATE,
        )

        // Fallbacks always exist — never show an empty field.
        val globalRank = prefs.getString(WidgetDataStore.KEY_GLOBAL_RANK, null)
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.widget_rank_na)
        val countryRank = prefs.getString(WidgetDataStore.KEY_COUNTRY_RANK, null)
            ?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.widget_rank_na)
        val level = prefs.getInt(WidgetDataStore.KEY_LEVEL, 0)
        val progress = prefs.getFloat(WidgetDataStore.KEY_LEVEL_PROGRESS, 0f)
            .coerceIn(0f, 1f)
        val progressPercent = (progress * 100).roundToInt()

        views.setTextViewText(R.id.rank_global_value, globalRank)
        views.setTextViewText(R.id.rank_country_value, countryRank)
        views.setTextViewText(R.id.level_text, context.getString(R.string.widget_level_value, level))
        views.setTextViewText(R.id.level_percent_text, "$progressPercent%")
        views.setProgressBar(R.id.level_progress, 100, progressPercent, false)

        // Tap widget → buka aplikasi utama.
        views.setOnClickPendingIntent(
            R.id.stats_widget_root,
            WidgetSupport.openAppPendingIntent(context, 1),
        )

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
