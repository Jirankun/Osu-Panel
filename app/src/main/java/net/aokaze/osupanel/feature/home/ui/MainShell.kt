/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.aokaze.osupanel.R
import net.aokaze.osupanel.feature.auth.AuthStatus
import net.aokaze.osupanel.rememberClickGuard
import net.aokaze.osupanel.feature.auth.AuthViewModel
import net.aokaze.osupanel.feature.chat.ui.ChatScreen
import net.aokaze.osupanel.feature.maps.ui.MapsScreen
import net.aokaze.osupanel.feature.rankings.ui.RankingsScreen
import net.aokaze.osupanel.feature.settings.ui.SettingsScreen
import net.aokaze.osupanel.ui.components.AnimatedBottomNav
import net.aokaze.osupanel.ui.components.BottomNavItem

/**
 * Main shell after login:
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
    onOpenChatSettings: () -> Unit,
    onOpenPmChat: (String) -> Unit,
    onOpenGroupChat: (String) -> Unit,
    onOpenSavedMaps: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()

    // Guard the bottom-nav taps: double-tapping the same tab must not launch
    // two competing scroll animations (same guard used across the app).
    val guard = rememberClickGuard()

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
            icon = Icons.Rounded.ChatBubble,
            selectedIcon = Icons.Rounded.ChatBubble,
            label = stringResource(R.string.nav_chat),
        ),
        BottomNavItem(
            icon = Icons.Rounded.Settings,
            selectedIcon = Icons.Rounded.Settings,
            label = stringResource(R.string.nav_settings),
        ),
    )

    val user = state.user

    // System navigation bar height (3-button-nav devices) — must match the
    // value used by the pill nav so the content stops exactly at the pill's
    // bottom edge (see AnimatedBottomNav). On gesture-nav devices this is 0.
    val navContext = LocalContext.current
    val navDensity = LocalDensity.current
    val navBarBottomExtra = remember(navContext, navDensity) {
        val res = navContext.resources
        val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
        val px = if (id > 0) res.getDimensionPixelSize(id) else 0
        with(navDensity) { px.toDp() }
    }

    // Floating overlay: the root paints the app background across the WHOLE
    // window (this is what stops the dark system window background showing
    // behind the nav). The pager content fills the FULL screen so the bottom
    // is never a big empty gap — the scrim below fades the content around the
    // pill into darkness, and the pill floats over it.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp),
            beyondViewportPageCount = 1,
        ) { page ->
            when (page) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    onOpenProfile = { onOpenProfile(user?.id ?: 0) },
                    onOpenBeatmapDetail = onOpenBeatmapDetail,
                )
                1 -> MapsScreen(
                    userId = user?.id,
                    onOpenBeatmapDetail = onOpenBeatmapDetail,
                    onOpenSavedMaps = onOpenSavedMaps,
                )
                2 -> RankingsScreen(onOpenProfile = onOpenProfile)
                3 -> ChatScreen(
                    onOpenPmChat = onOpenPmChat,
                    onOpenGroupChat = onOpenGroupChat,
                    onOpenChatSettings = onOpenChatSettings,
                )
                4 -> SettingsScreen(
                    viewModel = viewModel,
                    onOpenLicenses = onOpenLicenses,
                    onOpenContributors = onOpenContributors,
                    onOpenChatSettings = onOpenChatSettings,
                )
            }
        }

        // Dimming scrim behind the floating pill (iOS-like). SHORT: it only
        // starts right at the pill's top edge, so content stays fully bright
        // down to just above the nav (never "cut off" or prematurely dimmed)
        // and the dark zone is small, not a wide gap. Below the pill it fades
        // to ~92% black so no thin content strip ever hangs under the pill.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(88.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.2f to Color.Black.copy(alpha = 0.35f),
                            0.7f to Color.Black.copy(alpha = 0.75f),
                            1f to Color.Black.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )

        AnimatedBottomNav(
            currentIndex = pagerState.currentPage,
            onTap = { index ->
                if (!guard()) return@AnimatedBottomNav
                scope.launch {
                    // Smooth LEFT/RIGHT slide between pages (same direction as
                    // a finger swipe). A short tween keeps it snappy: adjacent
                    // tabs (the common case) compose only the target page,
                    // exactly like swiping.
                    pagerState.animateScrollToPage(
                        page = index,
                        animationSpec = tween(
                            durationMillis = 320,
                            easing = FastOutSlowInEasing,
                        ),
                    )
                }
            },
            items = items,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
