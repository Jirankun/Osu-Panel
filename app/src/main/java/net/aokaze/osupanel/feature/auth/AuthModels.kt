/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.auth

import net.aokaze.osupanel.data.model.UserDto

/** Auth session status — used by the NavHost to decide the screen. */
enum class AuthStatus {
    INITIAL,
    AUTHENTICATING,
    AUTHENTICATED,
    UNAUTHENTICATED,
    ERROR,
}

/** Auth state consumed by the UI (StateFlow in [AuthViewModel]). */
data class AuthState(
    val status: AuthStatus = AuthStatus.INITIAL,
    val user: UserDto? = null,
    val errorMessage: String? = null,

    /**
     * "Goodbye, <user>!" message after logout — rendered at the NAVHOST level
     * (above every screen, including login) and stays visible until tapped.
     */
    val goodbyeMessage: String? = null,
)

/**
 * OAuth redirect result from MainActivity.
 * [code] is set if the user completed authorize on the osu! page;
 * [error] is set if an AppAuth exception occurred (e.g. user cancelled).
 */
data class AuthResult(
    val code: String? = null,
    val error: Throwable? = null,
)
