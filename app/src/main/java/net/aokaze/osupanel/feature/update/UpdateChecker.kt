/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aokaze.osupanel.core.config.Env
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Update check result from Appteka. */
data class UpdateInfo(
    val remoteVerCode: Int,
    val remoteVerName: String,
)

/**
 * App update check via the proxy worker → Appteka.
 *
 * Flow (as requested): when the app opens, fetch
 * `GET {proxy}?endpoint={appteka.info(app_id)}`, match `package` against
 * this app (`net.aokaze.osupanel`), then compare `ver_code` with the installed
 * version. If newer → save to the prefs cache → the popup shows.
 *
 * If the user picks "Not Now", the popup just closes — the cache stays,
 * so on the next app open the popup shows AGAIN without needing the network
 * (makes pushing updates to users easy). The cache is cleared automatically once
 * the installed version is >= the remote version (the user updated).
 */
object UpdateChecker {

    const val PREFS_NAME = "osu_panel_update"
    const val KEY_CACHED_CODE = "cached_ver_code"
    const val KEY_CACHED_NAME = "cached_ver_name"
    const val KEY_LAST_SHOWN_DATE = "last_shown_date"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Installed version (versionCode) of this APK. */
    fun installedVerCode(context: Context): Int =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }.getOrDefault(0)

    /**
     * Checks for updates. `null` = no update (or a network failure / package
     * mismatch — do not bother the user).
     */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val installed = installedVerCode(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1) Valid cache (remote version still newer than the installed one)
        //    → show without the network. This is the "Not Now → reopen" path.
        //    Show limit: ONCE PER DAY — users who keep pressing "Not
        //    Now" are not bothered repeatedly in a day; it shows again
        //    tomorrow (the cache stays).
        val cachedCode = prefs.getInt(KEY_CACHED_CODE, 0)
        val cachedName = prefs.getString(KEY_CACHED_NAME, null)
        if (cachedCode > installed && !cachedName.isNullOrEmpty()) {
            return@withContext if (isShownToday(context)) null else UpdateInfo(cachedCode, cachedName)
        }

        // 2) Stale cache (the user updated) → clear it.
        prefs.edit().clear().apply()

        // 3) Re-check Appteka via the proxy worker.
        val appInfoUrl = "https://appteka.store/api/1/app/info?app_id=" +
            URLEncoder.encode(Env.UPDATE_APP_ID, "UTF-8") + "&locale=id"
        val proxyUrl = Env.UPDATE_PROXY_BASE_URL + "?endpoint=" +
            URLEncoder.encode(appInfoUrl, "UTF-8")

        runCatching {
            val req = Request.Builder().url(proxyUrl).get().build()
            val resp = client.newCall(req).execute()
            try {
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                val info = JSONObject(body)
                    .optJSONObject("result")
                    ?.optJSONObject("info") ?: return@runCatching null

                // Guard: only cares about entries whose package is this app.
                if (info.optString("package", "") != Env.UPDATE_PACKAGE_NAME) {
                    return@runCatching null
                }
                val verCode = info.optInt("ver_code", 0)
                if (verCode <= installed) return@runCatching null
                val verName = info.optString("ver_name", "").ifEmpty { return@runCatching null }

                // Save the cache → shows again on the next open.
                prefs.edit()
                    .putInt(KEY_CACHED_CODE, verCode)
                    .putString(KEY_CACHED_NAME, verName)
                    .apply()

                // Show limit of once/day (see the comment above).
                if (isShownToday(context)) return@runCatching null
                UpdateInfo(verCode, verName)
            } finally {
                resp.close()
            }
        }.getOrNull()
    }

    private fun today(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    /** True if the popup has already been shown today (once/day limit). */
    private fun isShownToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_SHOWN_DATE, null) == today()
    }

    /** Records that the popup was shown today — called on dismiss / opening the page. */
    fun recordShownToday(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SHOWN_DATE, today())
            .apply()
    }
}
