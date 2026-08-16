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
 * Token + session data storage, encrypted with the Android Keystore
 * (counterpart of the Flutter `flutter_secure_storage`).
 *
 * Keys match the Flutter version (ApiConstants):
 *   osu_access_token, osu_refresh_token, osu_token_expiry,
 *   osu_user_identifier, osu_user_id
 */
class TokenStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    companion object {
        private const val PREFS_NAME = "osu_panel_secure"

        // Keys — match the Flutter ApiConstants
        const val KEY_ACCESS_TOKEN = "osu_access_token"
        const val KEY_REFRESH_TOKEN = "osu_refresh_token"
        const val KEY_TOKEN_EXPIRY = "osu_token_expiry"
        const val KEY_USER_IDENTIFIER = "osu_user_identifier"
        const val KEY_SAVED_USER_ID = "osu_user_id"

        /** PKCE code_verifier for the ongoing OAuth flow. */
        const val KEY_PENDING_CODE_VERIFIER = "osu_pending_code_verifier"

        /** Cache of the last user data (JSON) — used offline so the
         *  profile page still renders without logging out. */
        const val KEY_CACHED_USER = "osu_cached_user"
    }

    /** Last user data (JSON) — read when offline. */
    var cachedUserJson: String?
        get() = prefs.getString(KEY_CACHED_USER, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_CACHED_USER).apply()
            else prefs.edit().putString(KEY_CACHED_USER, value).apply()
        }

    /**
     * PKCE code_verifier from the ongoing OAuth flow.
     * Stored BEFORE the authorize browser opens so it survives process
     * death — the verifier must be sent when exchanging the code.
     */
    var pendingCodeVerifier: String?
        get() = prefs.getString(KEY_PENDING_CODE_VERIFIER, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_PENDING_CODE_VERIFIER).apply()
            else prefs.edit().putString(KEY_PENDING_CODE_VERIFIER, value).apply()
        }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
            else prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()
        }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_REFRESH_TOKEN).apply()
            else prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()
        }

    var tokenExpiry: String?
        get() = prefs.getString(KEY_TOKEN_EXPIRY, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_TOKEN_EXPIRY).apply()
            else prefs.edit().putString(KEY_TOKEN_EXPIRY, value).apply()
        }

    var userIdentifier: String?
        get() = prefs.getString(KEY_USER_IDENTIFIER, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_USER_IDENTIFIER).apply()
            else prefs.edit().putString(KEY_USER_IDENTIFIER, value).apply()
        }

    var savedUserId: String?
        get() = prefs.getString(KEY_SAVED_USER_ID, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_SAVED_USER_ID).apply()
            else prefs.edit().putString(KEY_SAVED_USER_ID, value).apply()
        }

    /** Save access/refresh tokens + expiry (epoch ms) from the worker response. */
    fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresIn: Long,
    ) {
        val edit = prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_TOKEN_EXPIRY, (System.currentTimeMillis() + expiresIn * 1000L).toString())
        if (refreshToken.isNullOrEmpty()) {
            // A client-credentials token has NO refresh token — remove the
            // old one so no stale refresh token is left over from an OAuth session.
            edit.remove(KEY_REFRESH_TOKEN)
        } else {
            edit.putString(KEY_REFRESH_TOKEN, refreshToken)
        }
        edit.apply()
    }

    /** Clear all session data (logout). */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
