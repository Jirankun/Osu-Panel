/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.repository

import android.util.Log
import kotlinx.serialization.json.Json
import net.aokaze.osupanel.data.model.UserDto
import net.aokaze.osupanel.data.remote.OsuApi
import net.aokaze.osupanel.data.remote.WorkerApi
import net.aokaze.osupanel.data.local.TokenStore
import retrofit2.HttpException
import java.io.IOException

/**
 * Auth repository.
 * Tokens are stored in [TokenStore] (encrypted); OAuth code exchanges
 * and refresh go through the Cloudflare Worker.
 */
class AuthRepository(
    private val tokenStore: TokenStore,
    private val workerApi: WorkerApi,
    private val osuApi: OsuApi,
) {
    /** JSON parser for the user cache (may differ in config from the main parser). */
    private val cacheJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Last successfully loaded user (from the app data cache) — for offline. */
    val cachedUser: UserDto?
        get() = tokenStore.cachedUserJson?.let { raw ->
            runCatching { cacheJson.decodeFromString<UserDto>(raw) }.getOrNull()
        }

    companion object {
        private const val TAG = "AuthRepository"

        /** User identifier key (used for score polling). */
        const val KEY_USER_IDENTIFIER = "osu_user_identifier"
    }

    /**
     * Login via Cloudflare Worker (Client Credentials).
     *
     * Client-credentials tokens are NOT user-specific and have NO refresh
     * token — so a still-valid token is REUSED (no new token request to osu!
     * on every identifier login). This prevents hitting the rate limit (429)
     * of the osu! token endpoint from repeated requests.
     *
     * Marker: client-credentials token = `refreshToken == null` (OAuth
     * tokens ALWAYS have a refresh token, so they cannot be confused).
     */
    suspend fun loginWithCredentials(identifier: String): String {
        val existing = tokenStore.accessToken
        val expiry = tokenStore.tokenExpiry?.toLongOrNull() ?: 0L
        // Reuse only while it stays valid for more than 60 seconds.
        if (existing != null && tokenStore.refreshToken == null && expiry - System.currentTimeMillis() > 60_000L) {
            return existing
        }
        val result = workerApi.getToken(mapOf("identifier" to identifier))
        tokenStore.saveTokens(result.accessToken, null, result.expiresIn)
        return result.accessToken
    }

    /**
     * Exchange the OAuth code (from the osu! login page) for tokens via the worker.
     * [codeVerifier] = the PKCE verifier created when authorize opened —
     * osu! requires it during the code exchange.
     */
    suspend fun loginWithCode(
        code: String,
        redirectUri: String,
        codeVerifier: String?,
    ) {
        val body = buildMap {
            put("code", code)
            put("redirect_uri", redirectUri)
            if (!codeVerifier.isNullOrEmpty()) {
                put("code_verifier", codeVerifier)
            }
        }
        val result = workerApi.exchangeCode(body)
        tokenStore.saveTokens(result.accessToken, result.refreshToken, result.expiresIn)
    }

    /**
     * Refresh access token.
     * - returns `true`  → new token stored.
     * - returns `false` → refresh token definitely invalid (logout allowed).
     * - throws         → failed due to NETWORK (do not logout).
     */
    suspend fun refreshAccessToken(): Boolean {
        val refreshToken = tokenStore.refreshToken
        if (refreshToken.isNullOrEmpty()) return false

        return try {
            val result = workerApi.refreshAccessToken(mapOf("refresh_token" to refreshToken))
            tokenStore.saveTokens(result.accessToken, result.refreshToken, result.expiresIn)
            true
        } catch (e: HttpException) {
            // 429 (rate limit osu!) / 5xx (server sementara) — transien, BUKAN
            // invalid_grant: throw so the caller handles it without logging out.
            if (e.code() == 429 || e.code() >= 500) throw e
            // Hanya 400/401 yang berarti refresh token benar-benar mati.
            Log.d(TAG, "refreshAccessToken definitif: ${e.code()}")
            false
        } catch (e: IOException) {
            // Transient — let the caller handle it without logout.
            Log.d(TAG, "refreshAccessToken transien: $e")
            throw e
        }
    }

    suspend fun setUserIdentifier(identifier: String) {
        tokenStore.userIdentifier = identifier
    }

    suspend fun isAuthenticated(): Boolean = tokenStore.accessToken != null

    /**
     * Fetches the current user.
     *
     * - OAuth token (has refresh token + identify scope): the user MUST be
     *   fetched from /me — the account that authorized on the osu! page. Must NOT
     *   fall back to the stored identifier: if /me fails (transient network),
     *   showing a stale user from a previous session would be wrong.
     * - Client Credentials token (quick login): no /me scope → lookup
     *   directly by the entered ID/username.
     */
    /** Save the last user snapshot to app data (read when offline). */
    private fun cacheUser(user: UserDto) {
        tokenStore.cachedUserJson = runCatching { cacheJson.encodeToString(UserDto.serializer(), user) }.getOrNull()
    }

    suspend fun getCurrentUser(): UserDto {
        // OAuth flow — /me is the single source of truth.
        if (tokenStore.refreshToken != null) {
            val user = osuApi.getMe()
            tokenStore.savedUserId = user.id.toString()
            cacheUser(user)
            return user
        }

        // Client Credentials flow — look up by the stored identifier.
        var numericId = tokenStore.savedUserId?.toIntOrNull()
        var username: String? = null

        if (numericId == null) {
            val identifier = tokenStore.userIdentifier
            val parsed = identifier?.toIntOrNull()
            if (parsed != null) {
                numericId = parsed
            } else {
                username = identifier
            }
        }

        val user = when {
            numericId != null -> osuApi.getUser(numericId)
            username != null -> osuApi.getUserByUsername(username)
            else -> throw IllegalStateException(
                "Token client credentials tanpa identifier tersimpan",
            )
        }

        tokenStore.savedUserId = user.id.toString()
        cacheUser(user)
        return user
    }

    /**
     * Fetch a user with stats for a specific game mode
     * (`GET /users/{id}/{mode}` — mode: osu | fruits | taiko | mania).
     * Used by Settings → "Widget mode" so widgets show that mode's stats.
     */
    suspend fun getUserByMode(userId: Int, mode: String): UserDto {
        return osuApi.getUserByMode(userId, mode)
    }

    /**
     * Fetch public config from the worker (client_id for OAuth).
     * Returns null on failure — caller should fallback to cached value.
     */
    suspend fun fetchConfig(): String? {
        return try {
            val config = workerApi.getConfig()
            config.client_id.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.d(TAG, "fetchConfig failed: $e")
            null
        }
    }

    suspend fun logout() {
        tokenStore.clear()
    }
}
