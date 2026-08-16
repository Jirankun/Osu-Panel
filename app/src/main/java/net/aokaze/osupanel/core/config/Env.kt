/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.core.config

/**
 * Environment configuration — counterpart of the Flutter `lib/env.dart`.
 *
 * Client credentials (Client ID + Secret) live in the Cloudflare Worker;
 * the app never sees the Client Secret.
 */
object Env {
    /** Base URL of the Cloudflare Worker that proxies osu! auth. */
    const val WORKER_API_BASE_URL = "https://api-osupanel.zhyllanfyllah.my.id"

    /** Base URL osu! API v2. */
    const val API_BASE_URL = "https://osu.ppy.sh/api/v2"

    /** Endpoint OAuth osu!. */
    const val AUTHORIZE_ENDPOINT = "https://osu.ppy.sh/oauth/authorize"
    const val TOKEN_ENDPOINT = "https://osu.ppy.sh/oauth/token"

    /** osu! OAuth Application Client ID (public — aman di-embed). */
    const val CLIENT_ID = "65842"

    /** Redirect URI — must match the osu! callback + intent-filter scheme. */
    const val REDIRECT_URI = "osupanel://callback"

    /** OAuth scope. `identify` is always granted implicitly by osu!. */
    val SCOPES: List<String> = listOf("identify", "public", "friends.read")

    /** Network timeout — 20s connect/read (login & fetch), 30s for the worker
     *  (the worker retries 3× + backoff can exceed 10 seconds). */
    const val CONNECT_TIMEOUT_SECONDS = 20L
    const val READ_TIMEOUT_SECONDS = 20L
    const val WORKER_TIMEOUT_SECONDS = 30L

    // ── Pengecekan update (Appteka via proxy worker) ──
    // Flow: app opens → GET proxy?endpoint=appteka.info(app_id) → match
    // `package` against this app → compare `ver_code`. Change UPDATE_APP_ID
    // when the app id on Appteka changes (the detail page link follows it).
    const val UPDATE_PROXY_BASE_URL = "https://proxy-app.jirankun.workers.dev"
    const val UPDATE_APP_ID = "646r307816"
    const val UPDATE_PACKAGE_NAME = "net.aokaze.osupanel"
    const val UPDATE_STORE_PAGE_URL =
        "https://aokazestudio.zhyllanfyllah.my.id/pages/app-detail?app_id=646r307816"
}
