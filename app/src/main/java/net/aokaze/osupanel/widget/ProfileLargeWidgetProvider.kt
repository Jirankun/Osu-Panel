/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.widget

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import net.aokaze.osupanel.R
import net.aokaze.osupanel.data.local.WidgetDataStore

/**
 * Osu! Panel — Profile Large Home Screen Widget (osu-stats-signature style).
 *
 * Rendered as a SINGLE 550x320 signature bitmap (stat-sign "full" template):
 * cover + avatar + username + flag + level bar + grade counts +
 * all stats, drawn by [SignatureRenderer]. The bitmap is scaled to
 * the widget size by the ImageView (fitCenter) — tap → opens the app.
 *
 * Rendering & image refresh logic lives in [SignatureWidgetProvider].
 */
class ProfileLargeWidgetProvider : SignatureWidgetProvider() {

    override val layoutId = R.layout.profile_large_widget_layout
    override val imageViewId = R.id.large_signature_image
    override val clickRootId = R.id.profile_large_widget_root
    override val requestCode = 2

    override val placeholderW = SignatureRenderer.OUT_W
    override val placeholderH = SignatureRenderer.OUT_H
    override val placeholderTextSize = 48f

    override val coverTargetW = (550 * 1.5).toInt()
    override val coverTargetH = (120 * 1.5).toInt()
    override val avatarTarget = 170

    /** Cache key per widget type + mode + layout + user — a change renders fresh. */
    override fun cacheKey(prefs: SharedPreferences): String {
        val mode = prefs.getString(WidgetDataStore.KEY_WIDGET_MODE, null)
            ?.takeIf { it in WidgetMode.ALL }
            ?: "std"
        val layout = prefs.getString(WidgetDataStore.KEY_WIDGET_LAYOUT, null)
            ?.takeIf { it == "stats" || it == "skills" }
            ?: "stats"
        val userId = prefs.getString(WidgetDataStore.KEY_USER_ID, null)
            ?.ifBlank { "x" }
            ?: "x"
        return "large_${mode}_${layout}_$userId"
    }

    override fun buildData(context: Context, prefs: SharedPreferences, username: String): SignatureRenderer.Data {
        val zero = context.getString(R.string.widget_zero)
        val na = context.getString(R.string.widget_rank_na)
        return SignatureRenderer.Data(
            username = username,
            playmode = prefs.getString(WidgetDataStore.KEY_WIDGET_MODE, null)
                ?.takeIf { it in WidgetMode.ALL }
                ?: "std",
            countryCode = prefs.getString(WidgetDataStore.KEY_COUNTRY_CODE, null).orEmpty(),
            countryName = prefs.getString(WidgetDataStore.KEY_COUNTRY_NAME, null).orEmpty(),
            level = prefs.getInt(WidgetDataStore.KEY_LEVEL, 0),
            levelProgressPercent = (prefs.getFloat(WidgetDataStore.KEY_LEVEL_PROGRESS, 0f).coerceIn(0f, 1f) * 100).toInt(),
            pp = prefs.getString(WidgetDataStore.KEY_PP, null) ?: zero,
            medals = prefs.getString(WidgetDataStore.KEY_MEDALS_COUNT, null) ?: zero,
            playtime = prefs.getString(WidgetDataStore.KEY_PLAYTIME_SIG, null)?.takeIf { it.isNotBlank() } ?: "0h 0m",
            globalRank = prefs.getString(WidgetDataStore.KEY_GLOBAL_RANK, null)?.takeIf { it.isNotBlank() } ?: na,
            countryRank = prefs.getString(WidgetDataStore.KEY_COUNTRY_RANK, null)?.takeIf { it.isNotBlank() } ?: na,
            rankedScore = prefs.getString(WidgetDataStore.KEY_RANKED_SCORE, null) ?: zero,
            playCount = prefs.getString(WidgetDataStore.KEY_PLAY_COUNT, null) ?: zero,
            totalScore = prefs.getString(WidgetDataStore.KEY_TOTAL_SCORE, null) ?: zero,
            totalHits = prefs.getString(WidgetDataStore.KEY_TOTAL_HITS, null) ?: zero,
            replays = prefs.getString(WidgetDataStore.KEY_REPLAYS, null) ?: zero,
            acc = prefs.getString(WidgetDataStore.KEY_ACCURACY, null) ?: zero,
            maxCombo = prefs.getString(WidgetDataStore.KEY_MAX_COMBO, null) ?: zero,
            bp = prefs.getString(WidgetDataStore.KEY_BP, null) ?: "-",
            firstPlace = prefs.getString(WidgetDataStore.KEY_FIRST_PLACE, null) ?: "-",
            gradeSsh = prefs.getInt(WidgetDataStore.KEY_GRADE_SSH, 0),
            gradeSs = prefs.getInt(WidgetDataStore.KEY_GRADE_SS_RAW, 0),
            gradeSh = prefs.getInt(WidgetDataStore.KEY_GRADE_SH, 0),
            gradeS = prefs.getInt(WidgetDataStore.KEY_GRADE_S_RAW, 0),
            gradeA = prefs.getInt(WidgetDataStore.KEY_GRADE_A_RAW, 0),
            profileColour = prefs.getString(WidgetDataStore.KEY_PROFILE_COLOUR, null),
            layout = prefs.getString(WidgetDataStore.KEY_WIDGET_LAYOUT, null)
                ?.takeIf { it == "stats" || it == "skills" }
                ?: "stats",
            skills = WidgetDataStore.getSkillsData(context),
        )
    }

    override fun render(context: Context, data: SignatureRenderer.Data): Bitmap =
        SignatureRenderer.render(context, data)
}
