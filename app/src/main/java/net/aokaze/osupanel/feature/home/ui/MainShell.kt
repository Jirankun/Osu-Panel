/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.home.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.aokaze.osupanel.R
import net.aokaze.osupanel.feature.auth.AuthStatus
import net.aokaze.osupanel.feature.auth.AuthViewModel
import net.aokaze.osupanel.feature.maps.ui.MapsScreen
import net.aokaze.osupanel.feature.rankings.ui.RankingsScreen
import net.aokaze.osupanel.feature.settings.ui.SettingsScreen
import net.aokaze.osupanel.ui.components.AnimatedBottomNav
import net.aokaze.osupanel.ui.components.BottomNavItem

/**
 * Main shell after login — counterpart of the Flutter `MainShell`:
 * PageView (swipe between pages) + AnimatedBottomNav
 * (Dashboard / Maps / Rankings / Settings).
 */
@Composable
fun MainShell(
    viewModel: AuthViewModel,
    onOpenProfile: (Int) -> Unit,
    onOpenBeatmapDetail: (Int) -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenContributors: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    val items = listOf(
        BottomNavItem(
            icon = Icons.Rounded.Dashboard,
            selectedIcon = Icons.Rounded.Dashboard,
            label = stringResource(R.string.nav_dashboard),
        ),
        BottomNavItem(
            icon = Icons.Rounded.Map,
            selectedIcon = Icons.Rounded.Map,
            label = stringResource(R.string.nav_maps),
        ),
        BottomNavItem(
            icon = Icons.Rounded.Leaderboard,
            selectedIcon = Icons.Rounded.Leaderboard,
            label = stringResource(R.string.nav_rankings),
        ),
        BottomNavItem(
            icon = Icons.Rounded.Settings,
            selectedIcon = Icons.Rounded.Settings,
            label = stringResource(R.string.nav_settings),
        ),
    )

    val user = state.user
    androidx.compose.material3.Scaffold(
        bottomBar = {
            AnimatedBottomNav(
                currentIndex = pagerState.currentPage,
                onTap = { index ->
                    scope.launch {
                        // INSTANTLY (not animateScrollToPage): animating across
                        // intermediate pages → every page in between gets composed
                        // at once → each screen loads data (network burst)
                        // → heavy lag when switching nav. An instant jump only
                        // composes the target page (+ neighbours), consistent
                        // with swipe and without double-loading.
                        pagerState.scrollToPage(index)
                    }
                },
                items = items,
            )
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) { page ->
            when (page) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    onOpenProfile = { onOpenProfile(user?.id ?: 0) },
                )
                1 -> MapsScreen(
                    userId = user?.id,
                    onOpenBeatmapDetail = onOpenBeatmapDetail,
                )
                2 -> RankingsScreen(onOpenProfile = onOpenProfile)
                3 -> SettingsScreen(
                    viewModel = viewModel,
                    onOpenLicenses = onOpenLicenses,
                    onOpenContributors = onOpenContributors,
                )
            }
        }
    }
}
