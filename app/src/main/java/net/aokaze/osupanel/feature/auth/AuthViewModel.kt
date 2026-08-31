/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.auth

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.aokaze.osupanel.OsuPanelApp
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.config.Env
import net.aokaze.osupanel.data.local.WidgetDataStore
import net.aokaze.osupanel.data.model.UserDto
import net.aokaze.osupanel.data.remote.classifyError
import net.aokaze.osupanel.widget.WidgetMode
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import retrofit2.HttpException
import java.security.SecureRandom
import java.util.Base64

/**
 * Auth ViewModel.
 *
 * OAuth login flow (Authorization Code Grant):
 * 1. Open the osu! authorize page in the system browser (Custom Tab via AppAuth).
 * 2. User logs in & approves → redirect to `osupanel://callback` →
 *    RedirectUriReceiverActivity completes the PendingIntent →
 *    MainActivity.onNewIntent → [AuthResult] is delivered here.
 * 3. The code is exchanged via the Cloudflare Worker (which holds the Client
 *    Secret), tokens are stored, and the user is fetched via /me.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as OsuPanelApp).container
    private val repository = container.authRepository
    private val contentRepository = container.contentRepository

    /** App context — for reading messages from res/values/strings.xml. */
    private val appContext = getApplication<Application>()

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    // Separate flag: checkAuthStatus and login must not block each other.
    private var authCheckInProgress = false
    private var loginInProgress = false

    init {
        viewModelScope.launch {
            container.sessionExpired.collect {
                // Definite refresh failure → the interceptor already cleared the token.
                logout()
            }
        }
        viewModelScope.launch {
            container.authResults.collect { result ->
                handleAuthResult(result)
            }
        }
    }

    /**
     * Receives the OAuth result from MainActivity.
     *
     * IMPORTANT: there is no `loginInProgress` guard here — if the system
     * kills the app process while the authorize browser is open, the ViewModel
     * is recreated and its flags reset. Codes that arrive afterwards must still
     * be processed (otherwise login would hang forever). An OAuth code can only
     * appear if the authorize flow was actually started (the code is bound to
     * this app's client_id + redirect_uri).
     */
    private fun handleAuthResult(result: AuthResult) {
        val code = result.code
        if (code != null && code.isNotEmpty()) {
            loginInProgress = true
            viewModelScope.launch { continueLoginWithCode(code) }
            return
        }

        // No code → cancellation or error.
        loginInProgress = false

        val error = result.error
        val wasAuthenticating = _state.value.status == AuthStatus.AUTHENTICATING

        // User closed the login page (user-canceled error, or
        // RESULT_CANCELED with no data) — not an error.
        val isCancel = (error is AuthorizationException &&
            error.error == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.error) ||
            (error == null && result.code == null)

        if (isCancel) {
            // Only reset the status if a login is actually in progress (after
            // process death the status is already INITIAL — leave it, the UI is
            // already on the login page).
            if (wasAuthenticating) {
                _state.value = AuthState(status = AuthStatus.UNAUTHENTICATED)
            }
            return
        }

        // Other OAuth error.
        if (wasAuthenticating) {
            _state.value = AuthState(
                status = AuthStatus.ERROR,
                errorMessage = appContext.getString(R.string.error_login_generic),
            )
        }
    }

    /** Start login via osu! web (Authorization Code Grant). */
    fun loginWithOsu() {
        // Only 1 request: ignore taps while a login is still in progress.
        if (loginInProgress) return
        loginInProgress = true

        _state.value = _state.value.copy(status = AuthStatus.AUTHENTICATING, errorMessage = null)

        viewModelScope.launch {
            // Fetch client_id from worker (fallback to hardcoded if network fails)
            val clientId = repository.fetchConfig() ?: Env.CLIENT_ID
            startOAuthFlow(clientId)
        }
    }

    private fun startOAuthFlow(clientId: String) {
        val serviceConfig = AuthorizationServiceConfiguration(
            Uri.parse(Env.AUTHORIZE_ENDPOINT),
            Uri.parse(Env.TOKEN_ENDPOINT),
        )

        val codeVerifier = generateCodeVerifier()
        container.tokenStore.pendingCodeVerifier = codeVerifier

        val request = AuthorizationRequest.Builder(
            serviceConfig,
            clientId,
            ResponseTypeValues.CODE,
            Uri.parse(Env.REDIRECT_URI),
        )
            .setScopes(Env.SCOPES.toSet())
            .setCodeVerifier(codeVerifier)
            .build()

        container.launchOAuth(request)
    }

    /** Continue login after the OAuth code arrives from the redirect. */
    private suspend fun continueLoginWithCode(code: String) {
        try {
            // PKCE: the verifier stored when the browser opened must be sent
            // together with the code — osu! rejects the exchange without it.
            val codeVerifier = container.tokenStore.pendingCodeVerifier

            // Exchange code → tokens via the worker (which holds the Client Secret).
            repository.loginWithCode(
                code = code,
                redirectUri = Env.REDIRECT_URI,
                codeVerifier = codeVerifier,
            )

            // Fetch user data via /me — the account that authorized on the osu! page.
            onAuthenticated(repository.getCurrentUser())
        } catch (e: Throwable) {
            Log.d(TAG, "Login via osu! error: $e")
            handleLoginError(e)
        } finally {
            // One-time verifier — clear it after the exchange (success/failure).
            container.tokenStore.pendingCodeVerifier = null
            loginInProgress = false
        }
    }

    /**
     * Login error handling: if a token is already stored (e.g. /me failed
     * due to a transient network issue) → re-verify without logging in again.
     * Otherwise → show a friendly message.
     */
    private suspend fun handleLoginError(error: Throwable) {
        if (repository.isAuthenticated()) {
            checkAuthStatus()
        } else if (_state.value.status != AuthStatus.UNAUTHENTICATED) {
            // Session already cleared (definite refresh failure) → logout()
            // already handles navigation via sessionExpired; do not override
            // the state with ERROR.
            _state.value = AuthState(
                status = AuthStatus.ERROR,
                errorMessage = loginErrorMessage(error),
            )
        }
    }

    /**
     * Login failure message: 429 (rate limit — the server refuses) gets a
     * dedicated "too many attempts" message; anything else: generic classification.
     */
    private fun loginErrorMessage(e: Throwable): String {
        if (e is HttpException && e.code() == 429) {
            return appContext.getString(R.string.error_login_too_many)
        }
        return classifyError(appContext, e).message
    }

    /** Quick login via identifier (Client Credentials through the worker). */
    fun loginWithIdentifier(identifier: String) {
        // Only 1 request: ignore taps while a login is still in progress.
        if (loginInProgress) return
        loginInProgress = true
        viewModelScope.launch {
            _state.value = _state.value.copy(status = AuthStatus.AUTHENTICATING, errorMessage = null)
            try {
                repository.loginWithCredentials(identifier)
                repository.setUserIdentifier(identifier)
                onAuthenticated(repository.getCurrentUser())
            } catch (e: Throwable) {
                _state.value = AuthState(
                    status = AuthStatus.ERROR,
                    errorMessage = loginErrorMessage(e),
                )
            } finally {
                loginInProgress = false
            }
        }
    }

    /** Check auth status — called from SplashScreen. */
    fun checkAuthStatus() {
        if (authCheckInProgress) return
        authCheckInProgress = true
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(status = AuthStatus.AUTHENTICATING, errorMessage = null)

                // Pre-check: no token at all → go straight to login.
                if (!repository.isAuthenticated()) {
                    _state.value = AuthState(status = AuthStatus.UNAUTHENTICATED)
                    return@launch
                }

                // App just opened → update home screen widgets (+ skills radar).
                onAuthenticated(repository.getCurrentUser(), fetchSkills = true)
            } catch (e: Throwable) {
                handleCheckAuthError(e)
            } finally {
                authCheckInProgress = false
            }
        }
    }

    private suspend fun handleCheckAuthError(e: Throwable) {
        Log.d(TAG, "Check auth error: $e")

        // A token still exists in app data → the session is STILL valid: DO NOT logout
        // and DO NOT switch to login. Stay authenticated — show the profile
        // from the cache (if any) + an error banner (offline, timeout, etc.).
        if (repository.isAuthenticated()) {
            val cached = repository.cachedUser
            // Keep the widget fed with cached data — never leave it empty while offline.
            cached?.let { WidgetDataStore.updateWithUser(appContext, it) }
            _state.value = AuthState(
                status = AuthStatus.AUTHENTICATED,
                user = cached ?: _state.value.user,
                errorMessage = classifyError(appContext, e).message,
            )
            return
        }

        // The token is truly gone (logout / definite refresh failure)
        // → the user is simply not logged in.
        _state.value = AuthState(status = AuthStatus.UNAUTHENTICATED)
    }

    /** Set a timeout error — called by the splash after 8 seconds. */
    fun setTimeoutError() {
        val current = _state.value
        if (current.status != AuthStatus.INITIAL && current.status != AuthStatus.AUTHENTICATING) {
            return
        }
        viewModelScope.launch {
            if (repository.isAuthenticated()) {
                // Token exists → stay authenticated (home), do not go to login.
                _state.value = AuthState(
                    status = AuthStatus.AUTHENTICATED,
                    user = current.user ?: repository.cachedUser,
                    errorMessage = appContext.getString(R.string.error_no_internet),
                )
            } else {
                _state.value = AuthState(
                    status = AuthStatus.ERROR,
                    errorMessage = appContext.getString(R.string.error_no_internet),
                )
            }
        }
    }

    /** Silently refresh user data (no authenticating state → no UI flash). */
    suspend fun refreshUser(): String? {
        return try {
            onAuthenticated(repository.getCurrentUser(), fetchSkills = true, forceSkills = true)
            null
        } catch (e: Throwable) {
            classifyError(appContext, e).message
        }
    }

    /**
     * After a successful login / app open / refresh: publish the user and feed
     * the home screen widgets (data + selected game mode + optional skills).
     */
    private fun onAuthenticated(
        user: UserDto,
        fetchSkills: Boolean = false,
        forceSkills: Boolean = false,
    ) {
        _state.value = AuthState(status = AuthStatus.AUTHENTICATED, user = user)
        // Update home screen widgets with the latest data.
        WidgetDataStore.updateWithUser(appContext, user)
        syncWidgetsToSelectedMode()
        if (fetchSkills) {
            fetchSkillsForWidget(force = forceSkills)
        }
    }

    /**
     * Switch the large widget layout (Settings → Widget layout): "stats" / "skills".
     * The layout is saved + widgets re-render; when skills is selected, the
     * Skill Pulse is computed too so the radar appears right away.
     */
    fun setWidgetLayout(layout: String) {
        WidgetDataStore.setWidgetLayout(appContext, layout)
        if (layout == "skills") {
            fetchSkillsForWidget(force = true)
        }
    }

    /**
     * Fetches osu!skills (osuskills.com — the source used by osu-stats-
     * signature) for the "skills" widget layout. Once per app session (unless
     * force — called on refresh / when skills is picked). Fails silently;
     * the widget shows "No skills data". Same data as the dashboard & profile.
     */
    private var skillsFetchedForSession = false

    private fun fetchSkillsForWidget(force: Boolean = false) {
        if (!force && skillsFetchedForSession) return
        skillsFetchedForSession = true
        viewModelScope.launch {
            val username = _state.value.user?.username?.takeIf { it.isNotBlank() }
                ?: appContext.getSharedPreferences(
                    WidgetDataStore.PREFS_NAME,
                    android.content.Context.MODE_PRIVATE,
                ).getString(WidgetDataStore.KEY_USERNAME, null)?.takeIf { it.isNotBlank() }
                ?: return@launch
            val data = net.aokaze.osupanel.data.skills.SkillsFetcher.fetch(username)
            if (data != null) {
                WidgetDataStore.setSkillsData(appContext, data)
            }
        }
    }

    /**
     * Switches the home screen widget game mode (Settings → Widget mode).
     * The mode is saved and widgets re-render immediately (icon + mode name
     * change instantly), then stats for that mode are fetched from the osu! API
     * and saved to the widget — including when switching back to osu! standard
     * (stale stats from another mode must be replaced).
     */
    fun setWidgetMode(mode: String) {
        WidgetDataStore.setWidgetMode(appContext, mode)
        fetchWidgetModeStats(mode, "setWidgetMode")
    }

    /**
     * When the widget mode ≠ osu! standard, fetch stats for that mode
     * (`GET /users/{id}/{mode}`) and save them to the widget — so after
     * a refresh / app open, the widget still shows the selected mode.
     * On failure (offline/etc.) → stay silent; old data keeps showing.
     */
    private fun syncWidgetsToSelectedMode() {
        val mode = WidgetDataStore.getWidgetMode(appContext)
        if (mode == "std") return
        fetchWidgetModeStats(mode, "syncWidgetsToSelectedMode")
    }

    /**
     * Fetch stats for [mode] (`GET /users/{id}/{mode}`) and save them to the
     * widget — shared by [setWidgetMode] and [syncWidgetsToSelectedMode].
     * Base fields (avatar/cover/country/achievements) are kept — the mode
     * endpoint does not always return achievements. On failure (offline/etc.)
     * stay silent; old data keeps showing.
     */
    private fun fetchWidgetModeStats(mode: String, tag: String) {
        val base = _state.value.user ?: return
        viewModelScope.launch {
            try {
                val modeUser = repository.getUserByMode(base.id, WidgetMode.apiMode(mode))
                WidgetDataStore.updateWithUser(appContext, base.copy(statistics = modeUser.statistics))
            } catch (e: Throwable) {
                Log.d(TAG, "$tag($mode) gagal: $e")
            }
        }
    }

    /** Clear the error message (called by the banner after its exit animation). */
    fun clearError() {
        if (_state.value.errorMessage != null) {
            _state.value = _state.value.copy(errorMessage = null)
        }
    }

    fun logout() {
        viewModelScope.launch {
            val username = _state.value.user?.username?.takeIf { it.isNotBlank() } ?: "osu! player"
            repository.logout()
            // Logout → clear the widgets (placeholder, not stale data).
            WidgetDataStore.clear(appContext)
            _state.value = AuthState(
                status = AuthStatus.UNAUTHENTICATED,
                // "Goodbye" banner is rendered at the NavHost level — appears
                // at the VERY TOP (above the login screen) and stays until tapped.
                goodbyeMessage = appContext.getString(R.string.settings_goodbye, username),
            )
        }
    }

    /** Tutup banner "Goodbye" (dipanggil saat banner ditap). */
    fun clearGoodbye() {
        if (_state.value.goodbyeMessage != null) {
            _state.value = _state.value.copy(goodbyeMessage = null)
        }
    }

    companion object {
        private const val TAG = "AuthViewModel"

        /**
         * PKCE code_verifier (RFC 7636): 64 random bytes → base64url without
         * padding (86 chars) — within the valid 43–128 char range
         * and only using unreserved characters.
         */
        private fun generateCodeVerifier(): String {
            val bytes = ByteArray(64)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
