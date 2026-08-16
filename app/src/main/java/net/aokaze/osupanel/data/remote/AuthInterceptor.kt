/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.aokaze.osupanel.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.HttpException
import java.io.IOException

/**
 * Auth interceptor — counterpart of `_AuthInterceptor` in `dio_client.dart`.
 *
 * 1. Injects `Authorization: Bearer <token>` into every request.
 * 2. On 401: refresh the token via the worker ONCE (shared across all
 *    concurrent 401 requests), then retry the request once.
 * 3. Refresh result semantics (same as the Flutter version):
 *    - success  → new token stored, retry the request.
 *    - definite (worker returned an error) → clear the token + call
 *      [onSessionExpired] (logout).
 *    - transient (network failure) → old token KEPT, the request
 *      fails as a friendly network error (no logout).
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val workerApi: WorkerApi,
    private val onSessionExpired: () -> Unit,
) : Interceptor {

    private val refreshLock = Mutex()

    /** In-flight refresh — shared across all 401 requests. */
    private var inFlight: Deferred<Boolean?>? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Do not attach the header to a request that was already retried.
        if (request.header(HEADER_RETRIED) != null) {
            return chain.proceed(request)
        }

        val token = tokenStore.accessToken
        val authRequest = if (token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }

        val response = chain.proceed(authRequest)

        if (response.code == 401) {
            // No token at all (guest mode / not logged in) → the anonymous
            // request is rejected by osu! — NOT a dead session. Pass the 401
            // through (the UI shows an error), DO NOT log out.
            if (token == null) return response

            val outcome = runBlocking { refreshOnce() }
            return when (outcome) {
                // Success: retry once with the new token.
                true -> {
                    response.close()
                    val retryRequest = request.newBuilder()
                        .header("Authorization", "Bearer ${tokenStore.accessToken}")
                        .header(HEADER_RETRIED, "true")
                        .build()
                    chain.proceed(retryRequest)
                }

                // Transient: failed due to the network, the token is NOT cleared.
                // Throw an IOException → the UI shows a friendly network message.
                null -> {
                    response.close()
                    throw IOException("Gagal refresh token karena masalah jaringan (sementara).")
                }

                // Definite: refresh token invalid → clear the session + logout,
                // then pass the original 401 to the call site.
                false -> {
                    tokenStore.clear()
                    onSessionExpired()
                    response
                }
            }
        }

        return response
    }

    /**
     * Refresh with coalescing: if another refresh is already in flight,
     * wait for its result (avoids fake logout on concurrent 401s).
     * Result: true = success, false = definite failure, null = transient.
     */
    private suspend fun refreshOnce(): Boolean? = refreshLock.withLock {
        val current = inFlight
        if (current != null) return@withLock current.await()

        val deferred = CoroutineScope(Dispatchers.IO).async { doRefresh() }
        inFlight = deferred
        try {
            deferred.await()
        } finally {
            inFlight = null
        }
    }

    private suspend fun doRefresh(): Boolean? {
        val refreshToken = tokenStore.refreshToken
        if (refreshToken.isNullOrEmpty()) return false

        return try {
            val res = workerApi.refreshAccessToken(
                mapOf("refresh_token" to refreshToken),
            )
            tokenStore.saveTokens(res.accessToken, res.refreshToken, res.expiresIn)
            true
        } catch (e: HttpException) {
            // 429 (osu! rate limit) / 5xx (transient server issue) — NOT invalid_grant:
            // transient — keep the old token, DO NOT log out. Only 400/401
            // yang berarti refresh token benar-benar mati.
            if (e.code() == 429 || e.code() >= 500) null else false
        } catch (e: IOException) {
            // No response (timeout/connection) → transient.
            null
        }
    }

    companion object {
        const val HEADER_RETRIED = "X-Retried"
    }
}
