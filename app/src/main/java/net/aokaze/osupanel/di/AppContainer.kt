/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.di

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import net.aokaze.osupanel.BuildConfig
import net.aokaze.osupanel.core.config.Env
import net.aokaze.osupanel.data.local.TokenStore
import net.aokaze.osupanel.data.remote.AuthInterceptor
import net.aokaze.osupanel.data.remote.OsuApi
import net.aokaze.osupanel.data.remote.WorkerApi
import net.aokaze.osupanel.data.repository.AuthRepository
import net.aokaze.osupanel.data.repository.ContentRepository
import net.aokaze.osupanel.feature.auth.AuthResult
import net.openid.appauth.AuthorizationRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * App dependency container (manual DI — no Hilt to keep things simple
 * and builds fast, following the reference project's style).
 */
class AppContainer(context: Context) {

    /** JSON parser — ignores unknown fields to survive API changes. */
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val jsonConverter = json.asConverterFactory("application/json".toMediaType())

    val tokenStore = TokenStore(context)

    /** Session expired (definite refresh failure) → ViewModel logs out. */
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    /** OAuth request from the ViewModel → run by MainActivity. */
    private val _oauthRequests = MutableSharedFlow<AuthorizationRequest>(extraBufferCapacity = 1)
    val oauthRequests: SharedFlow<AuthorizationRequest> = _oauthRequests.asSharedFlow()

    fun launchOAuth(request: AuthorizationRequest) {
        _oauthRequests.tryEmit(request)
    }

    /** OAuth result (code/exception) from MainActivity → consumed by the ViewModel. */
    private val _authResults = MutableSharedFlow<AuthResult>(extraBufferCapacity = 1)
    val authResults: SharedFlow<AuthResult> = _authResults.asSharedFlow()

    fun deliverAuthResult(result: AuthResult) {
        _authResults.tryEmit(result)
    }

    /** Full HTTP logging (URL + status + body) only in debug builds. */
    private val httpLogging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    // ── Cloudflare Worker (auth proxy) ──
    private val workerClient = OkHttpClient.Builder()
        .connectTimeout(Env.WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(Env.WORKER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(httpLogging)
        .build()

    val workerApi: WorkerApi = Retrofit.Builder()
        .baseUrl(Env.WORKER_API_BASE_URL.ensureTrailingSlash())
        .client(workerClient)
        .addConverterFactory(jsonConverter)
        .build()
        .create(WorkerApi::class.java)

    // ── osu! API v2 (with auth + refresh interceptor) ──
    private val authInterceptor = AuthInterceptor(tokenStore, workerApi) {
        _sessionExpired.tryEmit(Unit)
    }

    private val osuClient = OkHttpClient.Builder()
        .connectTimeout(Env.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(Env.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(httpLogging)
        .build()

    val osuApi: OsuApi = Retrofit.Builder()
        .baseUrl(Env.API_BASE_URL.ensureTrailingSlash())
        .client(osuClient)
        .addConverterFactory(jsonConverter)
        .build()
        .create(OsuApi::class.java)

    val authRepository = AuthRepository(tokenStore, workerApi, osuApi)
    val contentRepository = ContentRepository(osuApi)
}

private fun String.ensureTrailingSlash(): String =
    if (endsWith("/")) this else "$this/"
