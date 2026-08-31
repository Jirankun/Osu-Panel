/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.core.config

/**
 * Environment configuration.
 *
 * Client credentials (Client ID + Secret) live in the Cloudflare Worker;
 * the app never sees the Client Secret.
 */
object Env {
    /** Base URL of the Cloudflare Worker that proxies osu! auth. */
    const val WORKER_API_BASE_URL = "https://osu-panel.zhyllanfyllah.my.id"

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

    // ── Update check (Appteka via proxy worker) ──
    // Flow: app opens → GET {worker}/app-info?package=... → the worker fetches
    // Appteka server-side (the app/site never send an arbitrary endpoint URL,
    // so the worker is NOT an open proxy). Lookup is 100% by package name —
    // guard `package` matches this app → compare `ver_code`. The store detail
    // page is opened by package too (`pckg=...`). Nothing Appteka-generated
    // is ever hardcoded, so an unstable Appteka id can never break the flow.
    const val UPDATE_CHECK_BASE_URL = "https://proxy-app.jirankun.workers.dev"
    const val UPDATE_PACKAGE_NAME = "net.aokaze.osupanel"
    const val UPDATE_STORE_PAGE_URL =
        "https://aokazestudio.zhyllanfyllah.my.id/pages/app-detail?pckg=net.aokaze.osupanel"
}
