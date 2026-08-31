/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import net.aokaze.osupanel.core.navigation.OsuPanelNavHost
import net.aokaze.osupanel.core.theme.OsuPanelTheme
import net.aokaze.osupanel.di.AppContainer
import net.aokaze.osupanel.feature.auth.AuthResult
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as OsuPanelApp).container

    /**
     * Opens the osu! authorize page (Custom Tab) and receives the result.
     * The result (OAuth code or exception, including user cancel) is
     * delivered to AuthViewModel.
     */
    private val authLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val response = result.data?.let { AuthorizationResponse.fromIntent(it) }
        val error = result.data?.let { AuthorizationException.fromIntent(it) }
        container.deliverAuthResult(
            AuthResult(
                code = response?.authorizationCode,
                error = error,
            ),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Receive the OAuth request from the ViewModel and launch the browser.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.oauthRequests.collect { request ->
                    val service = AuthorizationService(this@MainActivity)
                    authLauncher.launch(service.getAuthorizationRequestIntent(request))
                }
            }
        }

        setContent {
            OsuPanelTheme {
                // Global "tap outside to dismiss keyboard": any tap on empty
                // space clears the focused TextField's focus (hiding the IME).
                // Taps handled by children (buttons, fields, lists) are consumed
                // first, so they never trigger this.
                val focusManager = LocalFocusManager.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        },
                ) {
                    OsuPanelNavHost()
                }
            }
        }
    }
}

/**
 * ── Central double-action guard (single location) ──────────────────────────
 *
 * Any "action" that must fire exactly once per user intent — screen
 * navigation (double-tap would push the same screen twice), launchers
 * (double-tap would open two pickers / two OAuth browsers) — goes through
 * this. The FIRST tap wins; taps within [windowMs] after it are dropped.
 *
 * One shared instance for the whole app (same as the keyboard-focus guard
 * above): cheap, stateless, and keeps the debounce in exactly one place.
 */
@Composable
fun rememberClickGuard(windowMs: Long = 400L): () -> Boolean {
    var lastTap by remember { mutableLongStateOf(0L) }
    return {
        val now = SystemClock.uptimeMillis()
        if (now - lastTap < windowMs) {
            false // too soon after the last accepted tap — drop it
        } else {
            lastTap = now
            true
        }
    }
}
