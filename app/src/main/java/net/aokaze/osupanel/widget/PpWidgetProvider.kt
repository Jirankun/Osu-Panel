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
 * Osu! Panel — PP Home Screen Widget (osu-stats-signature MINI 400x120 style).
 *
 * Rendered as a SINGLE mini signature bitmap by [SignatureRenderer]:
 * avatar + username + flag + country rank + level, then a bottom row with
 * global rank, PP, acc, and play count — exactly the stat-sign `mini` template.
 * Tap → opens the app.
 *
 * Rendering & image refresh logic lives in [SignatureWidgetProvider].
 */
class PpWidgetProvider : SignatureWidgetProvider() {

    override val layoutId = R.layout.pp_widget_layout
    override val imageViewId = R.id.pp_signature_image
    override val clickRootId = R.id.pp_widget_root
    override val requestCode = 0

    override val placeholderW = SignatureRenderer.MINI_OUT_W
    override val placeholderH = SignatureRenderer.MINI_OUT_H
    override val placeholderTextSize = 34f

    override val coverTargetW = (400 * 1.5).toInt()
    override val coverTargetH = (120 * 1.5).toInt()
    override val avatarTarget = 180

    /** Cache key per widget type + mode + user — a different mode/account renders fresh. */
    override fun cacheKey(prefs: SharedPreferences): String {
        val mode = prefs.getString(WidgetDataStore.KEY_WIDGET_MODE, null)
            ?.takeIf { it in WidgetMode.ALL }
            ?: "std"
        val userId = prefs.getString(WidgetDataStore.KEY_USER_ID, null)
            ?.ifBlank { "x" }
            ?: "x"
        return "pp_${mode}_$userId"
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
            countryName = "",
            level = prefs.getInt(WidgetDataStore.KEY_LEVEL, 0),
            levelProgressPercent = 0,
            pp = prefs.getString(WidgetDataStore.KEY_PP, null) ?: zero,
            medals = "",
            playtime = "",
            globalRank = prefs.getString(WidgetDataStore.KEY_GLOBAL_RANK, null)?.takeIf { it.isNotBlank() } ?: na,
            countryRank = prefs.getString(WidgetDataStore.KEY_COUNTRY_RANK, null)?.takeIf { it.isNotBlank() } ?: na,
            rankedScore = "",
            playCount = prefs.getString(WidgetDataStore.KEY_PLAY_COUNT, null) ?: zero,
            totalScore = "",
            totalHits = "",
            replays = "",
            acc = prefs.getString(WidgetDataStore.KEY_ACCURACY, null) ?: zero,
            maxCombo = "",
            bp = "",
            firstPlace = "",
            gradeSsh = 0,
            gradeSs = 0,
            gradeSh = 0,
            gradeS = 0,
            gradeA = 0,
            profileColour = prefs.getString(WidgetDataStore.KEY_PROFILE_COLOUR, null),
        )
    }

    override fun render(context: Context, data: SignatureRenderer.Data): Bitmap =
        SignatureRenderer.renderMini(context, data)
}
