/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aokaze.osupanel.R
import net.aokaze.osupanel.feature.auth.AuthStatus
import net.aokaze.osupanel.feature.auth.AuthViewModel
import net.aokaze.osupanel.ui.components.TopBanner
import net.aokaze.osupanel.ui.components.OsuSpinner
import net.aokaze.osupanel.ui.components.trianglesLine

/**
 * Login — minimal: heart icon, title, subtitle, and a Login button.
 * Errors show as a banner dropping from the top center for
 * 3 seconds then sliding back up (not a static card under the button).
 */
@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme
    val authenticating = state.status == AuthStatus.AUTHENTICATING
    val hasError = state.status == AuthStatus.ERROR && state.errorMessage != null

    // 10-second cooldown after login error
    var cooldown by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        snapshotFlow { hasError }.collect { error ->
            if (error && cooldown == 0) {
                cooldown = 10
                while (cooldown > 0) {
                    delay(1000)
                    cooldown--
                }
            }
        }
    }

    val blocked = authenticating || cooldown > 0

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = colorResource(R.color.osu_pink),
                modifier = Modifier.size(64.dp),
            )

            Spacer(Modifier.height(16.dp))

            // EXPLICIT onSurface color — text without an explicit color turned out
            // to render black (LocalContentColor is not applied on this screen),
            // so do not rely on implicit colors.
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))

            // lazer-style login button — TrianglesV2 background with the same
            // EXACTLY the same as the loading spinner (drift up, continuous cycle).
            Surface(
                onClick = { viewModel.loginWithOsu() },
                enabled = !blocked,
                shape = RoundedCornerShape(50),
                color = if (blocked) {
                    colorScheme.onSurface.copy(alpha = 0.12f)
                } else {
                    colorScheme.primary
                },
                contentColor = if (blocked) {
                    colorScheme.onSurface.copy(alpha = 0.38f)
                } else {
                    colorScheme.onPrimary
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                // Laser triangles via modifier — other elements/buttons can use
                // the exact same `Modifier.trianglesLine()`.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .trianglesLine(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            authenticating -> {
                                OsuSpinner(size = 20.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.login_loading))
                            }
                            cooldown > 0 -> {
                                Text(stringResource(R.string.login_cooldown, cooldown))
                            }
                            else -> {
                                Text(stringResource(R.string.login_button))
                            }
                        }
                    }
                }
            }
        }

        // Banner — drops from the top center, 3 seconds, slides back up.
        state.errorMessage?.let { message ->
            TopBanner(
                message = message,
                onDismiss = { viewModel.clearError() },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}
