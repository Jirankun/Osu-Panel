/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.remote

import android.content.Context
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.config.Env
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/** Error category — only distinguishes WHICH SIDE failed. */
enum class AppErrorKind {
    /** The Cloudflare Worker (app server) failed. */
    WORKER,

    /** The osu! server returned an error response. */
    OSU_SERVER,

    /** Cannot connect (no internet / timeout). */
    NETWORK,

    /** Other / unknown error. */
    UNKNOWN,
}

/**
 * Exception with a friendly message that is safe to show to the user.
 * The message never contains raw API details (status code, body, etc.).
 */
class AppException(
    val kind: AppErrorKind,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)

/** Did the error come from a request to the Cloudflare Worker? */
private fun isWorkerRequest(e: HttpException): Boolean =
    e.response()?.raw()?.request?.url?.host?.let { host ->
        Env.WORKER_API_BASE_URL.contains(host)
    } ?: false

/**
 * Classifies any error into an [AppException]. Friendly messages are read
 * from res/values/strings.xml via [context].
 */
fun classifyError(context: Context, error: Throwable): AppException {
    if (error is AppException) return error

    if (error is HttpException) {
        // 429 = osu! rate limit → custom message
        if (error.code() == 429) {
            return AppException(
                AppErrorKind.OSU_SERVER,
                context.getString(R.string.error_osu_rate_limit),
                error,
            )
        }
        if (isWorkerRequest(error)) {
            return AppException(
                AppErrorKind.WORKER,
                context.getString(R.string.error_worker),
                error,
            )
        }
        return AppException(
            AppErrorKind.OSU_SERVER,
            context.getString(R.string.error_osu_server),
            error,
        )
    }

    if (error is SocketTimeoutException || error is InterruptedIOException) {
        // The request did NOT arrive (slow server) — NOT "no internet".
        // Show the dedicated timeout message.
        return AppException(
            AppErrorKind.NETWORK,
            context.getString(R.string.error_request_timeout),
            error,
        )
    }

    if (error is IOException) {
        return AppException(
            AppErrorKind.NETWORK,
            context.getString(R.string.error_no_internet),
            error,
        )
    }

    if (error is IllegalArgumentException || error is IllegalStateException) {
        return AppException(
            AppErrorKind.UNKNOWN,
            context.getString(R.string.error_app),
            error,
        )
    }

    return AppException(
        AppErrorKind.UNKNOWN,
        context.getString(R.string.error_generic),
        error,
    )
}
