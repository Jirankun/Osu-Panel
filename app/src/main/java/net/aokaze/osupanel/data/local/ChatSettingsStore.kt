/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Chat (Chatango) settings & credentials.
 *
 * Non-sensitive settings live in plain SharedPreferences ("osu_panel_chat"):
 * username, activation toggles, notification toggles (keys taken from the
 * Chatango app: grVisibleSound, pmVisibleSound, pmAlert*, atAlert*).
 *
 * Credentials (token + password) live in an encrypted prefs file
 * ("osu_panel_chat_secure") so a refresh can re-login automatically.
 */
object ChatSettingsStore {

    const val PREFS_NAME = "osu_panel_chat"
    private const val SECURE_PREFS_NAME = "osu_panel_chat_secure"

    // ── Plain keys ──
    const val KEY_USERNAME = "chat_username"
    const val KEY_PM_ENABLED = "chat_pm_enabled"
    const val KEY_GROUP_ENABLED = "chat_group_enabled"

    // Notification toggles (same keys as the Chatango app).
    const val KEY_GR_VISIBLE_SOUND = "grVisibleSound"
    const val KEY_PM_VISIBLE_SOUND = "pmVisibleSound"
    const val KEY_PM_ALERT_ENABLED = "pmAlertEnabled"
    const val KEY_PM_ALERT_SOUND = "pmAlertSound"
    const val KEY_PM_ALERT_VIBRATE = "pmAlertVibrate"
    const val KEY_AT_ALERT_ENABLED = "atAlertEnabled"
    const val KEY_AT_ALERT_SOUND = "atAlertSound"
    const val KEY_AT_ALERT_VIBRATE = "atAlertVibrate"

    // ── Secure keys ──
    const val KEY_TOKEN = "chat_token"
    const val KEY_PASSWORD = "chat_password"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun securePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ── Account ──

    fun getUsername(context: Context): String? =
        prefs(context).getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() }

    /** True when a Chatango account is logged in (username + token stored). */
    fun isLoggedIn(context: Context): Boolean =
        !getUsername(context).isNullOrBlank() && !getToken(context).isNullOrBlank()

    fun getToken(context: Context): String? =
        securePrefs(context).getString(KEY_TOKEN, null)?.takeIf { it.isNotEmpty() }

    fun getPassword(context: Context): String? =
        securePrefs(context).getString(KEY_PASSWORD, null)?.takeIf { it.isNotEmpty() }

    fun saveCredentials(context: Context, username: String, token: String, password: String) {
        prefs(context).edit().putString(KEY_USERNAME, username).apply()
        securePrefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    /** Clear the Chatango account (username, token, password + activation toggles). */
    fun logout(context: Context) {
        prefs(context).edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PM_ENABLED)
            .remove(KEY_GROUP_ENABLED)
            .apply()
        securePrefs(context).edit()
            .remove(KEY_TOKEN)
            .remove(KEY_PASSWORD)
            .apply()
    }

    // ── Activation ──

    fun isPmEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PM_ENABLED, false)

    fun setPmEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PM_ENABLED, enabled).apply()
    }

    fun isGroupEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_GROUP_ENABLED, false)

    fun setGroupEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_GROUP_ENABLED, enabled).apply()
    }

    // ── Notification toggles ──

    private fun getBool(context: Context, key: String, default: Boolean): Boolean =
        prefs(context).getBoolean(key, default)

    private fun setBool(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun isGrVisibleSound(context: Context): Boolean = getBool(context, KEY_GR_VISIBLE_SOUND, true)
    fun setGrVisibleSound(context: Context, value: Boolean) = setBool(context, KEY_GR_VISIBLE_SOUND, value)

    fun isPmVisibleSound(context: Context): Boolean = getBool(context, KEY_PM_VISIBLE_SOUND, true)
    fun setPmVisibleSound(context: Context, value: Boolean) = setBool(context, KEY_PM_VISIBLE_SOUND, value)

    fun isPmAlertEnabled(context: Context): Boolean = getBool(context, KEY_PM_ALERT_ENABLED, true)
    fun setPmAlertEnabled(context: Context, value: Boolean) = setBool(context, KEY_PM_ALERT_ENABLED, value)

    fun isPmAlertSound(context: Context): Boolean = getBool(context, KEY_PM_ALERT_SOUND, true)
    fun setPmAlertSound(context: Context, value: Boolean) = setBool(context, KEY_PM_ALERT_SOUND, value)

    fun isPmAlertVibrate(context: Context): Boolean = getBool(context, KEY_PM_ALERT_VIBRATE, true)
    fun setPmAlertVibrate(context: Context, value: Boolean) = setBool(context, KEY_PM_ALERT_VIBRATE, value)

    fun isAtAlertEnabled(context: Context): Boolean = getBool(context, KEY_AT_ALERT_ENABLED, true)
    fun setAtAlertEnabled(context: Context, value: Boolean) = setBool(context, KEY_AT_ALERT_ENABLED, value)

    fun isAtAlertSound(context: Context): Boolean = getBool(context, KEY_AT_ALERT_SOUND, true)
    fun setAtAlertSound(context: Context, value: Boolean) = setBool(context, KEY_AT_ALERT_SOUND, value)

    fun isAtAlertVibrate(context: Context): Boolean = getBool(context, KEY_AT_ALERT_VIBRATE, true)
    fun setAtAlertVibrate(context: Context, value: Boolean) = setBool(context, KEY_AT_ALERT_VIBRATE, value)
}
