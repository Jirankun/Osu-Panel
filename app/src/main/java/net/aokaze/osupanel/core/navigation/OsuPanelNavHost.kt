/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.core.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.aokaze.osupanel.R
import net.aokaze.osupanel.rememberClickGuard
import net.aokaze.osupanel.feature.auth.AuthStatus
import net.aokaze.osupanel.feature.auth.AuthViewModel
import net.aokaze.osupanel.feature.auth.ui.LoginScreen
import net.aokaze.osupanel.feature.auth.ui.SplashScreen
import net.aokaze.osupanel.feature.beatmap.ui.BeatmapDetailScreen
import net.aokaze.osupanel.feature.beatmap.ui.QrCodeScreen
import net.aokaze.osupanel.feature.chat.ui.ChatEditAccountScreen
import net.aokaze.osupanel.feature.chat.ui.ChatSettingsScreen
import net.aokaze.osupanel.feature.chat.ui.GroupChatScreen
import net.aokaze.osupanel.feature.chat.ui.PmChatScreen
import net.aokaze.osupanel.feature.home.ui.MainShell
import net.aokaze.osupanel.feature.profile.ui.ProfileScreen
import net.aokaze.osupanel.feature.settings.ui.ContributorsScreen
import net.aokaze.osupanel.feature.settings.ui.LicensesScreen
import net.aokaze.osupanel.feature.update.UpdateCheckViewModel
import net.aokaze.osupanel.ui.components.BannerType
import net.aokaze.osupanel.ui.components.TopBanner

/**
 * Auth-status based navigation:
 *   authenticated   → home (MainShell: Dashboard/Maps/Rankings/Settings)
 *   unauthenticated → login
 *   error           → home (the home screen shows the error + retry)
 *
 * Push routes: profile/{userId} & beatmap/{beatmapsetId} (from lists).
 */
@Composable
fun OsuPanelNavHost(
    viewModel: AuthViewModel = viewModel(),
    updateViewModel: UpdateCheckViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateInfo by updateViewModel.updateInfo.collectAsStateWithLifecycle()

    // One central double-tap guard for every push-navigation entry point
    // (profile, beatmap, licenses, contributors, chat screens…): a double
    // tap must never push the same screen twice.
    val guard = rememberClickGuard()

    LaunchedEffect(state.status) {
        val current = navController.currentDestination?.route
        val navigateTo: String? = when (state.status) {
            AuthStatus.AUTHENTICATED -> Routes.HOME
            AuthStatus.UNAUTHENTICATED -> Routes.LOGIN
            // Login / splash error → show the login page (if a user exists,
            // the error came from the home screen → stay on home).
            AuthStatus.ERROR -> if (state.user != null) Routes.HOME else Routes.LOGIN
            else -> null
        }
        if (navigateTo != null && current != navigateTo) {
            navController.navigate(navigateTo) {
                // Clear the whole back stack (splash/home) so the BACK button
                // does not return to an already-invalid screen.
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
        // When the app opens (authenticated) → check for updates once.
        if (state.status == AuthStatus.AUTHENTICATED) {
            updateViewModel.checkForUpdate()
        }
    }

    Box {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        // Standard forward/back screen motion (300ms, app-wide): pushing a
        // screen slides it in from the right; popping slides it back out to
        // the right, with a light cross-fade on both. Tuned so the pinned
        // detail headers stay visually calm while switching screens.
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(tween(300))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeOut(tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(300)) + fadeIn(tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(tween(300))
        },
    ) {
        composable(Routes.SPLASH) { SplashScreen(viewModel) }
        composable(Routes.LOGIN) { LoginScreen(viewModel) }
        composable(Routes.HOME) {
            MainShell(
                viewModel = viewModel,
                onOpenProfile = { userId ->
                    if (guard()) navController.navigate(Routes.profile(userId))
                },
                onOpenBeatmapDetail = { beatmapsetId ->
                    if (guard()) navController.navigate(Routes.beatmapDetail(beatmapsetId))
                },
                onOpenLicenses = {
                    if (guard()) navController.navigate(Routes.LICENSES)
                },
                onOpenContributors = {
                    if (guard()) navController.navigate(Routes.CONTRIBUTORS)
                },
                onOpenChatSettings = {
                    if (guard()) navController.navigate(Routes.CHAT_SETTINGS)
                },
                onOpenPmChat = { user ->
                    if (guard()) navController.navigate(Routes.pmChat(user))
                },
                onOpenGroupChat = { group ->
                    if (guard()) navController.navigate(Routes.groupChat(group))
                },
            )
        }
        composable(Routes.CHAT_SETTINGS) {
            ChatSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenEditAccount = {
                    if (guard()) navController.navigate(Routes.CHAT_EDIT)
                },
            )
        }
        composable(Routes.CHAT_EDIT) {
            ChatEditAccountScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = "${Routes.PM_CHAT}/{user}",
            arguments = listOf(navArgument("user") { type = NavType.StringType }),
        ) { entry ->
            val user = entry.arguments?.getString("user") ?: return@composable
            PmChatScreen(user = user, onBack = { navController.popBackStack() })
        }
        composable(
            route = "${Routes.GROUP_CHAT}/{group}",
            arguments = listOf(navArgument("group") { type = NavType.StringType }),
        ) { entry ->
            val group = entry.arguments?.getString("group") ?: return@composable
            GroupChatScreen(group = group, onBack = { navController.popBackStack() })
        }
        composable(Routes.LICENSES) {
            LicensesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CONTRIBUTORS) {
            ContributorsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = "${Routes.PROFILE}/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.IntType }),
        ) { entry ->
            val userId = entry.arguments?.getInt("userId") ?: return@composable
            ProfileScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                onOpenBeatmapDetail = { beatmapsetId ->
                    if (guard()) navController.navigate(Routes.beatmapDetail(beatmapsetId))
                },
            )
        }
        composable(
            route = "${Routes.BEATMAP_DETAIL}/{beatmapsetId}",
            arguments = listOf(navArgument("beatmapsetId") { type = NavType.IntType }),
        ) { entry ->
            val beatmapsetId = entry.arguments?.getInt("beatmapsetId") ?: return@composable
            BeatmapDetailScreen(
                beatmapsetId = beatmapsetId,
                currentUserId = state.user?.id,
                onBack = { navController.popBackStack() },
                onOpenProfile = { userId ->
                    if (guard()) navController.navigate(Routes.profile(userId))
                },
                onOpenQr = { id ->
                    if (guard()) navController.navigate(Routes.qrScreen(id))
                },
            )
        }
        composable(
            route = "${Routes.QR_SCREEN}/{beatmapsetId}",
            arguments = listOf(navArgument("beatmapsetId") { type = NavType.IntType }),
        ) { entry ->
            val beatmapsetId = entry.arguments?.getInt("beatmapsetId") ?: return@composable
            QrCodeScreen(
                beatmapsetId = beatmapsetId,
                onBack = { navController.popBackStack() },
            )
        }
    }

    // ── Update available! popup (shows when the app opens; text only) ──
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateViewModel.dismiss() },
            title = {
                Text(
                    stringResource(R.string.update_available),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(stringResource(R.string.update_prompt, info.remoteVerName))
            },
            confirmButton = {
                TextButton(onClick = { updateViewModel.openUpdatePage() }) {
                    Text(
                        stringResource(R.string.update_lets_update),
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { updateViewModel.dismiss() }) {
                    Text(stringResource(R.string.update_not_now))
                }
            },
        )
    }

    // ── Global banner — rendered ABOVE every screen (including login) ──
    // "Goodbye, <user>!" after logout: stays visible until tapped.
    state.goodbyeMessage?.let { message ->
        TopBanner(
            message = message,
            type = BannerType.Info,
            onDismiss = { viewModel.clearGoodbye() },
            autoDismiss = false,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
    }
}
