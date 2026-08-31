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
 * App update check via the Appteka proxy worker.
 *
 * Flow: when the app opens, fetch `GET {worker}/app-info?package=...` — the
 * worker fetches Appteka server-side (the app never sends an arbitrary
 * endpoint URL, so the worker is NOT an open proxy). Lookup is by package
 * name only (no app id), then guard `package` against this app
 * (`net.aokaze.osupanel`), then compare `ver_code` with the installed
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
    const val KEY_LAST_CHECKED_MS = "last_checked_ms"

    /** Re-check the proxy at most once per day when nothing is cached. */
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

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
     * Read cached update info synchronously (SharedPreferences read is fast).
     * Returns non-null only when the cached version is newer than installed.
     */
    fun loadCachedInfo(context: Context): UpdateInfo? {
        val installed = installedVerCode(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getInt(KEY_CACHED_CODE, 0)
        val name = prefs.getString(KEY_CACHED_NAME, null)
        return if (code > installed && !name.isNullOrEmpty()) {
            UpdateInfo(code, name)
        } else null
    }

    /**
     * Checks for updates. `null` = no update (or a network failure / package
     * mismatch — do not bother the user).
     */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val installed = installedVerCode(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1) Valid cache (remote version still newer than the installed one)
        //    → show without the network. Popup appears EVERY time the app
        //    opens so the user is nudged to update, but can still dismiss.
        val cachedCode = prefs.getInt(KEY_CACHED_CODE, 0)
        val cachedName = prefs.getString(KEY_CACHED_NAME, null)
        if (cachedCode > installed && !cachedName.isNullOrEmpty()) {
            return@withContext UpdateInfo(cachedCode, cachedName)
        }

        // 2) Stale cache (the user updated) → clear it.
        prefs.edit().clear().apply()

        // 2b) Throttle: don't hit the proxy worker on EVERY app open — at
        // most once per day when no update is cached. (A cached update still
        // shows from step 1 without the network.)
        if (System.currentTimeMillis() - prefs.getLong(KEY_LAST_CHECKED_MS, 0L) < CHECK_INTERVAL_MS) {
            return@withContext null
        }

        // 3) Re-check Appteka via the worker — the worker fetches Appteka
        // server-side; the app only sends the package name (no endpoint
        // passthrough → the worker is NOT an open proxy).
        val checkUrl = Env.UPDATE_CHECK_BASE_URL + "/app-info?package=" +
            URLEncoder.encode(Env.UPDATE_PACKAGE_NAME, "UTF-8")

        runCatching {
            val req = Request.Builder().url(checkUrl).get().build()
            val resp = client.newCall(req).execute()
            try {
                if (!resp.isSuccessful) return@runCatching null
                // Successful check (update found or not) → remember when so the
                // proxy worker is not hit again for another day.
                prefs.edit().putLong(KEY_LAST_CHECKED_MS, System.currentTimeMillis()).apply()
                val body = resp.body?.string() ?: return@runCatching null
                val info = JSONObject(body)
                    .optJSONObject("result")
                    ?.optJSONObject("info") ?: return@runCatching null

                // Guard: only cares about entries whose package is this app.
                if (info.optString("package", "") != Env.UPDATE_PACKAGE_NAME) {
                    return@runCatching null
                }
                val verCode = info.optInt("ver_code", 0)
                val verName = info.optString("ver_name", "").ifEmpty { return@runCatching null }
                // Compare version code first; if equal, compare version name
                // (handles cases where the store reuses version codes across
                // minor releases — e.g. Appteka ver_code stays at 2 for both
                // 1.0.0 and 1.0.1).
                val installedName = context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName.orEmpty()
                // Compare version code first; if equal, compare version name
                // (handles cases where the store reuses version codes across
                // minor releases — e.g. Appteka ver_code stays at 2 for both
                // 1.0.0 and 1.0.1).
                val hasUpdate = verCode > installed || (verCode == installed && verName > installedName)
                if (!hasUpdate) return@runCatching null

                // Save the cache → shows again on the next open.
                prefs.edit()
                    .putInt(KEY_CACHED_CODE, verCode)
                    .putString(KEY_CACHED_NAME, verName)
                    .apply()

                UpdateInfo(verCode, verName)
            } finally {
                resp.close()
            }
        }.getOrNull()
    }
}
