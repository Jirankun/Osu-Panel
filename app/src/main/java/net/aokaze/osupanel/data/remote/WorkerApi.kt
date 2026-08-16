/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.remote

import net.aokaze.osupanel.data.model.TokenResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Cloudflare Worker endpoints — the worker holds the osu! Client ID +
 * Secret, so the app never touches the Client Secret.
 * Counterpart of the Flutter `AuthRemoteDataSource` (worker part).
 */
interface WorkerApi {

    /** Client Credentials: exchange an identifier (ID/username) → access token. */
    @POST("auth/token")
    suspend fun getToken(
        @Body body: Map<String, String>,
    ): TokenResponse

    /** Authorization Code: exchange an OAuth code → access + refresh token. */
    @POST("auth/code")
    suspend fun exchangeCode(
        @Body body: Map<String, String>,
    ): TokenResponse

    /** Refresh an expired access token using the refresh token. */
    @POST("auth/refresh")
    suspend fun refreshAccessToken(
        @Body body: Map<String, String>,
    ): TokenResponse
}
