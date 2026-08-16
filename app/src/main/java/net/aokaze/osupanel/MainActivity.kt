/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
     * Opens the osu! authorize page (Custom Tab) and receives the result —
     * exactly like the flutter_appauth flow. The result (OAuth code or
     * exception, including user cancel) is delivered to AuthViewModel.
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
                OsuPanelNavHost()
            }
        }
    }
}
