/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.local

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import net.aokaze.osupanel.core.util.accuracyPercent
import net.aokaze.osupanel.core.util.formatDuration
import net.aokaze.osupanel.core.util.formatNumber
import net.aokaze.osupanel.core.util.formatNumberGrouped
import net.aokaze.osupanel.core.util.formatPlaytimeSig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.aokaze.osupanel.data.model.UserDto
import net.aokaze.osupanel.widget.PpWidgetProvider
import net.aokaze.osupanel.widget.ProfileLargeWidgetProvider
import net.aokaze.osupanel.widget.SignatureRenderer
import net.aokaze.osupanel.widget.StatsWidgetProvider
import net.aokaze.osupanel.widget.WidgetBitmapCache
import java.util.Locale

/** Lenient JSON — old stored skill payloads (pre-7-skill) decode safely. */
private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * Single source of data for home screen widgets.
 *
 * The main app (AuthViewModel) stores a user snapshot here every time
 * user data loads successfully — on login, on app open (checkAuthStatus),
 * and on refresh — then asks all three widgets to re-render. The widgets
 * tinggal membaca SharedPreferences di `onUpdate`.
 *
 * Principle: NEVER leave a field empty. When a value is missing (null rank,
 * empty username, etc.), the store saves an empty string and the provider
 * replaces it with a placeholder ("#N/A", "Username", "0") from resources —
 * so the widget always shows something sensible.
 */
object WidgetDataStore {

    /** Dedicated widget SharedPreferences file (separate from app prefs). */
    const val PREFS_NAME = "osu_panel_widgets"

    // Data keys (dibaca provider lewat constant di sini — satu sumber kebenaran).
    const val KEY_USERNAME = "widget_username"
    const val KEY_USER_ID = "widget_user_id"

    // Widget game mode (std/catch/taiko/mania) — see [net.aokaze.osupanel.widget.WidgetMode].
    const val KEY_WIDGET_MODE = "widget_mode"

    // Large widget layout: "stats" (stat columns) / "skills" (osu!skills radar).
    const val KEY_WIDGET_LAYOUT = "widget_layout"
    const val KEY_SKILLS_JSON = "widget_skills_json"
    const val KEY_PP = "widget_pp"
    const val KEY_GLOBAL_RANK = "widget_global_rank"
    const val KEY_COUNTRY_RANK = "widget_country_rank"
    const val KEY_LEVEL = "widget_level"
    const val KEY_LEVEL_PROGRESS = "widget_level_progress"
    const val KEY_COVER_URL = "widget_cover_url"
    const val KEY_AVATAR_URL = "widget_avatar_url"

    // Extra stats (used by the Profile Large widget to stay COMPLETE).
    const val KEY_ACCURACY = "widget_accuracy"
    const val KEY_PLAY_COUNT = "widget_play_count"
    const val KEY_PLAY_TIME = "widget_play_time"
    const val KEY_TOTAL_HITS = "widget_total_hits"
    const val KEY_MAX_COMBO = "widget_max_combo"
    const val KEY_RANKED_SCORE = "widget_ranked_score"
    const val KEY_TOTAL_SCORE = "widget_total_score"
    const val KEY_GRADE_SS = "widget_grade_ss"
    const val KEY_GRADE_S = "widget_grade_s"
    const val KEY_GRADE_A = "widget_grade_a"

    // Signature-widget specific data (stat-sign full template).
    const val KEY_COUNTRY_CODE = "widget_country_code"
    const val KEY_COUNTRY_NAME = "widget_country_name"
    const val KEY_MEDALS_COUNT = "widget_medals_count"
    const val KEY_PLAYTIME_SIG = "widget_playtime_sig"
    const val KEY_REPLAYS = "widget_replays"
    const val KEY_BP = "widget_bp"
    const val KEY_FIRST_PLACE = "widget_first_place"
    const val KEY_PROFILE_COLOUR = "widget_profile_colour"
    const val KEY_GRADE_SSH = "widget_grade_ssh"
    const val KEY_GRADE_SS_RAW = "widget_grade_ss_raw"
    const val KEY_GRADE_SH = "widget_grade_sh"
    const val KEY_GRADE_S_RAW = "widget_grade_s_raw"
    const val KEY_GRADE_A_RAW = "widget_grade_a_raw"

    /** Current widget mode (default osu! standard). */
    fun getWidgetMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WIDGET_MODE, null)?.takeIf { it in net.aokaze.osupanel.widget.WidgetMode.ALL }
            ?: "std"
    }

    /** Large widget layout: "stats" (default) or "skills" (osu!skills radar). */
    fun getWidgetLayout(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WIDGET_LAYOUT, null)?.takeIf { it == "stats" || it == "skills" }
            ?: "stats"
    }

    /** Switch the large widget layout (stats/skills) then re-render all widgets. */
    fun setWidgetLayout(context: Context, layout: String) {
        if (layout != "stats" && layout != "skills") return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WIDGET_LAYOUT, layout)
            .apply()
        updateAllWidgets(context)
    }

    /** Last successfully fetched osu!skills (osuskills.com) — null when absent. */
    fun getSkillsData(context: Context): SignatureRenderer.SkillsData? {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SKILLS_JSON, null) ?: return null
        val data = runCatching { lenientJson.decodeFromString<SignatureRenderer.SkillsData>(json) }.getOrNull()
            ?: return null
        // Stale payloads from an older schema decode as all-zero skills —
        // treat them as absent so the widget shows "No skills data".
        if (data.radarSkills.all { it.percent == 0f }) return null
        return data
    }

    fun setSkillsData(context: Context, data: SignatureRenderer.SkillsData) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SKILLS_JSON, Json.encodeToString(data))
            .apply()
        updateAllWidgets(context)
    }

    /**
     * Switch the widget mode then re-render all installed widgets.
     * Per-mode stats are fetched separately by the caller (AuthViewModel) and
     * stored via [updateWithUser].
     */
    fun setWidgetMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WIDGET_MODE, mode)
            .apply()
        updateAllWidgets(context)
    }

    /** Widget provider list — updateAllWidgets iterates this list. */
    private val widgetProviders = listOf(
        ProfileLargeWidgetProvider::class.java,
        StatsWidgetProvider::class.java,
        PpWidgetProvider::class.java,
    )

    /**
     * Save a user snapshot + re-render all widgets.
     * Called after a successful /me (login, app open, refresh).
     */
    fun updateWithUser(context: Context, user: UserDto) {
        val stats = user.statistics
        val grades = stats?.gradeCounts
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USERNAME, user.username.orEmpty())
            .putString(KEY_USER_ID, "#${user.id}")
            .putString(KEY_PP, formatNumberGrouped((stats?.pp ?: 0.0).toLong()))
            .putString(
                KEY_GLOBAL_RANK,
                stats?.globalRank?.let { "#${formatNumberGrouped(it.toLong())}" }.orEmpty(),
            )
            .putString(
                KEY_COUNTRY_RANK,
                stats?.countryRank?.let { "#${formatNumberGrouped(it.toLong())}" }.orEmpty(),
            )
            .putInt(KEY_LEVEL, stats?.levelCurrent ?: 0)
            .putFloat(KEY_LEVEL_PROGRESS, (stats?.levelProgress ?: 0.0).toFloat().coerceIn(0f, 1f))
            .putString(KEY_COVER_URL, user.coverUrl.orEmpty())
            .putString(KEY_AVATAR_URL, user.avatarUrl.orEmpty())

            // Full stats for the large widget.
            .putString(KEY_ACCURACY, String.format(Locale.US, "%.2f%%", accuracyPercent(stats?.accuracy ?: 0.0)))
            .putString(KEY_PLAY_COUNT, formatNumberGrouped((stats?.playCount ?: 0).toLong()))
            .putString(KEY_PLAY_TIME, formatDuration(stats?.playTime ?: 0))
            .putString(KEY_TOTAL_HITS, formatNumberGrouped(stats?.totalHits ?: 0L))
            .putString(KEY_MAX_COMBO, "${formatNumberGrouped(stats?.maximumCombo ?: 0L)}x")
            .putString(KEY_RANKED_SCORE, formatNumber(stats?.rankedScore ?: 0L))
            .putString(KEY_TOTAL_SCORE, formatNumber(stats?.totalScore ?: 0L))
            .putInt(KEY_GRADE_SS, (grades?.ss ?: 0) + (grades?.ssh ?: 0))
            .putInt(KEY_GRADE_S, (grades?.s ?: 0) + (grades?.sh ?: 0))
            .putInt(KEY_GRADE_A, grades?.a ?: 0)

            // Signature-widget specific data.
            .putString(KEY_COUNTRY_CODE, (user.country?.code ?: user.countryCode).orEmpty())
            .putString(KEY_COUNTRY_NAME, user.country?.name.orEmpty())
            .putString(KEY_MEDALS_COUNT, formatNumberGrouped(user.achievements.size.toLong()))
            .putString(KEY_PLAYTIME_SIG, formatPlaytimeSig(stats?.playTime ?: 0))
            .putString(KEY_REPLAYS, formatNumberGrouped((stats?.replaysWatchedByOthers ?: 0).toLong()))
            .putString(KEY_BP, "-")
            .putString(KEY_FIRST_PLACE, "-")
            .putString(KEY_PROFILE_COLOUR, user.profileColour.orEmpty())
            .putInt(KEY_GRADE_SSH, grades?.ssh ?: 0)
            .putInt(KEY_GRADE_SS_RAW, grades?.ss ?: 0)
            .putInt(KEY_GRADE_SH, grades?.sh ?: 0)
            .putInt(KEY_GRADE_S_RAW, grades?.s ?: 0)
            .putInt(KEY_GRADE_A_RAW, grades?.a ?: 0)
            .apply()

        updateAllWidgets(context)
    }

    /** Clear data (on logout) then render placeholders on all widgets. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        // Remove cached renders too — widgets must not show the old account's photo.
        WidgetBitmapCache.clearAll(context)
        updateAllWidgets(context)
    }

    /** Send an APPWIDGET_UPDATE broadcast to every installed provider.
     *  Posts with a 150ms delay so that SharedPreferences.apply() completes
     *  before the widget provider reads the data — prevents stale renders. */
    private fun updateAllWidgets(context: Context) {
        Handler(Looper.getMainLooper()).postDelayed({
            val manager = AppWidgetManager.getInstance(context)
            for (clazz in widgetProviders) {
                runCatching {
                    val ids = manager.getAppWidgetIds(ComponentName(context, clazz))
                    if (ids.isNotEmpty()) {
                        val intent = Intent(context, clazz).apply {
                            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        }
                        context.sendBroadcast(intent)
                    }
                }
            }
        }, 150L)
    }
}
