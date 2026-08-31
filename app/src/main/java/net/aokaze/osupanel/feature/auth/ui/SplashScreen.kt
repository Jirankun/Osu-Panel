/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.auth.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import net.aokaze.osupanel.R
import net.aokaze.osupanel.feature.auth.AuthViewModel

/**
 * Splash — only the splash image, no spinner/loading of any kind.
 * Navigation is handled by [OsuPanelNavHost] based on the auth status.
 */
@Composable
fun SplashScreen(viewModel: AuthViewModel) {
    // Check auth once; a 20-second failsafe so it never loads forever
    // if the server is slow (worker retry + a slow network take time).
    // No visuals of any kind on screen.
    LaunchedEffect(Unit) {
        viewModel.checkAuthStatus()
        delay(10000)
        viewModel.setTimeoutError()
    }

    Image(
        painter = painterResource(R.drawable.splash),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}
