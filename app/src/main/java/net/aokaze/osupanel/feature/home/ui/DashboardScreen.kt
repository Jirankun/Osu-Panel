/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.home.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Score
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.aokaze.osupanel.ui.components.TopBanner
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.aokaze.osupanel.R
import net.aokaze.osupanel.ui.components.ConfirmDialog
import net.aokaze.osupanel.ui.components.OsuSpinner
import net.aokaze.osupanel.core.theme.OsuColors
import net.aokaze.osupanel.core.util.accuracyPercent
import net.aokaze.osupanel.core.util.formatDuration
import net.aokaze.osupanel.core.util.formatNumber
import net.aokaze.osupanel.data.medal.MedalDisplay
import net.aokaze.osupanel.data.medal.MedalService
import net.aokaze.osupanel.data.model.UserDto
import net.aokaze.osupanel.data.model.UserMedalDto
import net.aokaze.osupanel.feature.auth.AuthStatus
import net.aokaze.osupanel.feature.auth.AuthViewModel
import net.aokaze.osupanel.feature.home.DashboardUiState
import net.aokaze.osupanel.feature.home.DashboardViewModel
import net.aokaze.osupanel.ui.components.BadgeDetailDialog
import net.aokaze.osupanel.ui.components.BadgeImage
import net.aokaze.osupanel.ui.components.CountryFlagImage
import net.aokaze.osupanel.ui.components.MedalExpandButton
import net.aokaze.osupanel.ui.components.MedalDetailDialog
import net.aokaze.osupanel.ui.components.MedalImage
import net.aokaze.osupanel.ui.components.MonthlyPlaycountCard
import net.aokaze.osupanel.ui.components.RetryButton
import net.aokaze.osupanel.ui.components.MapCoverImage
import net.aokaze.osupanel.ui.components.SkillRadar
import net.aokaze.osupanel.ui.components.SupporterBadge
import net.aokaze.osupanel.ui.components.rememberMapPlaceholderPainter
import net.aokaze.osupanel.ui.components.trianglesLine


/**
 * Dashboard — profile + stats + medal + badge (first shell tab).
 * Profile + stats + medal + badge.
 *
 * PERFORMANCE: the whole page uses [LazyColumn] — header/stats are regular
 * items, while medal/badge tiles (352 medals!) are virtualized per row
 * (4 columns) so only visible rows are rendered. Previously a non-lazy
 * SimpleGrid rendered every tile at once → lag while scrolling.
 *
 * Refresh via the top-right button (not pull-to-refresh).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AuthViewModel,
    onOpenProfile: () -> Unit,
    onOpenBeatmapDetail: (Int) -> Unit = {},
    dashboardViewModel: DashboardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dashState by dashboardViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var showEditProfileConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme

    if (state.status == AuthStatus.ERROR && state.user == null) {
        ErrorRetry(
            message = state.errorMessage,
            onRetry = { viewModel.checkAuthStatus() },
        )
        return
    }

    val user = state.user
    if (user == null) {
        // Guest/Test mode (not logged in) — same as Maps: show a short
        // message instead of a spinner that spins forever.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.maps_login_needed),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    // Extra data (best / most played / skills radar) — loaded once per session
    // with per-session cache; refreshed only via the refresh button (rate-safe).
    LaunchedEffect(user.id) {
        dashboardViewModel.load(user.id, user.username.orEmpty())
    }

    val onRefresh: () -> Unit = {
        if (!refreshing) {
            scope.launch {
                refreshing = true
                viewModel.refreshUser()
                dashboardViewModel.refresh(user.id, user.username.orEmpty())
                refreshing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.nav_dashboard),
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    // Edit profile — pencil icon LEFT of refresh. Opens the
                    // osu! account edit page in the browser after a
                    // confirmation dialog (same style as the logout dialog).
                    IconButton(onClick = { showEditProfileConfirm = true }) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(R.string.dashboard_edit_profile),
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.dashboard_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        // No pull-to-refresh — refresh only via the top-right button.
        if (refreshing) {
            // Full-screen loading — EXACTLY the same as the Profile detail
            // and Map detail screens: OsuSpinner 48dp centered on screen.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                OsuSpinner(size = 48.dp)
            }
        } else {
            DashboardContent(
                user = user,
                onOpenProfile = onOpenProfile,
                dashboardState = dashState,
                onOpenBeatmapDetail = onOpenBeatmapDetail,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    // ── Edit profile confirmation dialog (same dialog as logout, primary color) ──
    if (showEditProfileConfirm) {
        val colorScheme = MaterialTheme.colorScheme
        ConfirmDialog(
            title = stringResource(R.string.dashboard_edit_profile_title),
            text = stringResource(R.string.dashboard_edit_profile_confirm),
            confirmLabel = stringResource(R.string.dashboard_edit_profile_yes),
            dismissLabel = stringResource(R.string.dashboard_edit_profile_no),
            confirmColor = colorScheme.primary,
            confirmContentColor = colorScheme.onPrimary,
            onConfirm = {
                showEditProfileConfirm = false
                // Open the osu! account edit page in a CUSTOM TAB
                // (in-app overlay, same as the login flow) — not the
                // full standalone browser.
                runCatching {
                    openInCustomTab(
                        context = context,
                        colorScheme = colorScheme,
                        url = "https://osu.ppy.sh/home/account/edit",
                    )
                }
            },
            onDismiss = { showEditProfileConfirm = false },
        )
    }

    // Rate limit / error banner — drops from the top center.
    state.errorMessage?.let { message ->
        TopBanner(
            message = message,
            onDismiss = { viewModel.clearError() },
        )
    }

}

@Composable
private fun DashboardContent(
    user: UserDto,
    onOpenProfile: () -> Unit,
    dashboardState: DashboardUiState,
    onOpenBeatmapDetail: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = user.statistics

    // Medal & badge grids are chunked into rows (4 columns) → each row is
    // ONE lazy item → only visible rows are composed (anti-lag).
    val medalDisplay = remember(user.achievements) {
        val achievedIds = user.achievements.mapNotNull { it.medal?.achievementId ?: it.achievementId }.toSet()
        val achievedSlugs = user.achievements.mapNotNull { it.medal?.slug }.toSet()
        val achievedAtById = user.achievements.mapNotNull { um ->
            val id = um.medal?.achievementId ?: um.achievementId
            id?.let { idv -> um.achievedAt?.let { idv to it } }
        }.toMap()
        MedalService.buildAllMedalDisplay(
            achievedIds = achievedIds,
            achievedSlugs = achievedSlugs,
            achievedAtById = achievedAtById,
        )
    }
    // Medals start minimized (like the Profile detail screen): only the first
    // few rows, then "Show all (n)" expands the full grid.
    var medalsExpanded by remember { mutableStateOf(false) }
    val medalHasMore = medalDisplay.size > MEDALS_INITIAL_SHOW
    val medalRows = remember(medalDisplay, medalsExpanded) {
        val all = medalDisplay.chunked(4)
        if (medalsExpanded) all else all.take((MEDALS_INITIAL_SHOW + 3) / 4)
    }
    val badgeRows = remember(user.badges) { user.badges.chunked(4) }
    val achievedCount = medalDisplay.count { it.achieved }
    val totalCount = medalDisplay.size

    // Banner dismiss — session-only (resets on app restart).
    var bannerDismissed by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Profile header card
        item {
            ProfileHeaderCard(user, onOpenProfile)
        }

        // Daily Challenge Banner — hidden after X tap (session only)
        if (!bannerDismissed) {
            dashboardState.dailyChallenge?.let { dc ->
                item {
                    DailyChallengeBanner(
                        dc = dc,
                        onDismiss = { bannerDismissed = true },
                        onClick = { beatmapsetId ->
                            onOpenBeatmapDetail(beatmapsetId)
                        },
                    )
                }
            }
        }

        if (stats != null) {
            item {
                StatsGrid(stats)
            }
            // Extra stats — #1 scores / favourites / followers / weighted PP.
            // Weighted PP always shows (only the number changes while loading);
            // the API-optional stats appear only when actually returned.
            if (user.scoresFirstCount != null || user.favouriteCount != null ||
                user.followerCount != null
            ) {
                item {
                    ExtraStatsRow(user, dashboardState.weightedPp)
                }
            } else {
                item {
                    // Only weighted PP available → still show it.
                    WeightedPpStat(dashboardState.weightedPp)
                }
            }
            item {
                ProgressSection(stats)
            }
            // Rank history — mini chart (last 90 days).
            (stats.rankHistory ?: user.rankHistory)?.data
                ?.takeIf { it.isNotEmpty() }
                ?.let { history ->
                    item {
                        DashboardRankHistoryCard(history)
                    }
                }
            // Grade counters — its own card.
            stats.gradeCounts?.let { grades ->
                item {
                    GradeCard(grades)
                }
            }
            item {
                DetailedStatsCard(user, stats)
            }
            // Plays per month — last 12 months (osu! web signature chart),
            // grouped by year with brackets (shared with the Profile screen).
            if (user.monthlyPlaycounts.isNotEmpty()) {
                item {
                    MonthlyPlaycountCard(user.monthlyPlaycounts)
                }
            }
        }

        // Skills radar — osu!skills (osuskills.com, the osu-stats-signature
        // source): the SAME 6-axis radar as the profile & widget.
        dashboardState.skills?.let { skills ->
            item {
                SkillsRadarCard(skills)
            }
        }

        // Badges — header + grid tiles (lazy per row)
        item {
            BadgeHeaderCard(count = user.badges.size)
        }
        if (user.badges.isEmpty()) {
            item {
                EmptySection(
                    icon = Icons.Rounded.EmojiEvents,
                    text = stringResource(R.string.dashboard_no_badges),
                )
            }
        } else {
            items(badgeRows) { rowItems ->
                GridRow(rowItems) { badge ->
                    var showDetail by remember(badge) { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(14.dp),
                            )
                            // Whole card = tap target (not just the photo).
                            .clickable { showDetail = true }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BadgeImage(
                            badge = badge,
                            size = 60.dp,
                        )
                    }
                    if (showDetail) {
                        BadgeDetailDialog(badge = badge, onDismiss = { showDetail = false })
                    }
                }
            }
        }

        // Medals — header + grid tiles (lazy per row, minimized until "Show all")
        item {
            MedalHeaderCard(achievedCount = achievedCount, totalCount = totalCount)
        }
        if (medalDisplay.isEmpty()) {
            item {
                EmptySection(
                    icon = Icons.Rounded.MilitaryTech,
                    text = stringResource(R.string.dashboard_no_medals),
                )
            }
        } else {
            items(medalRows) { rowItems ->
                GridRow(rowItems) { display ->
                    val m = display.medal
                    val name = if (m.medalName.isNotEmpty()) m.medalName else m.name
                    var showDetail by remember(name, m.achievementIdInt) { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(14.dp),
                            )
                            // Whole card = tap target (not just the photo).
                            .clickable { showDetail = true }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MedalImage(
                            name = name,
                            grouping = m.grouping,
                            slug = m.slug,
                            description = m.description,
                            achievementId = m.achievementIdInt,
                            achievedAt = display.achievedAt,
                            achieved = display.achieved,
                            size = 56.dp,
                        )
                    }
                    if (showDetail) {
                        MedalDetailDialog(
                            name = name,
                            grouping = m.grouping,
                            slug = m.slug,
                            description = m.description,
                            achievementId = m.achievementIdInt,
                            achievedAt = display.achievedAt,
                            achieved = display.achieved,
                            onDismiss = { showDetail = false },
                        )
                    }
                }
            }
            if (medalHasMore) {
                item {
                    MedalExpandButton(
                        expanded = medalsExpanded,
                        totalCount = totalCount,
                        onToggle = { medalsExpanded = !medalsExpanded },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** One grid row (4 columns) — used by medals & badges inside the LazyColumn. */
@Composable
private fun <T> GridRow(
    rowItems: List<T>,
    content: @Composable (T) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rowItems.forEach { item ->
            Box(modifier = Modifier.weight(1f)) {
                content(item)
            }
        }
    }
}

// ── Profile Header Card ──

@Composable
private fun ProfileHeaderCard(user: UserDto, onOpenProfile: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val stats = user.statistics

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenProfile),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) {
            // Cover banner
            AsyncImage(
                model = user.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = rememberMapPlaceholderPainter(),
            )

            // Dark gradient so the text stays readable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.35f),
                            ),
                        ),
                    ),
            )

            // Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Avatar with a gradient ring
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(colorScheme.primary, colorScheme.tertiary),
                            ),
                        )
                        .padding(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(colorScheme.surface),
                    ) {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = user.username,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Spacer(Modifier.width(20.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Username.
                    Text(
                        user.username ?: stringResource(R.string.home_unknown_username),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    // flag | country code | user id (+ supporter)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        user.countryCode?.let { code ->
                            CountryFlagImage(countryCode = code, size = 16.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                code,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            "#${user.id}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        if (user.isSupporter) {
                            Spacer(Modifier.width(10.dp))
                            SupporterBadge(supportLevel = user.supportLevel, height = 18.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.dashboard_supporter),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = OsuColors.pink300,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        MiniChip(
                            icon = Icons.Rounded.TrendingUp,
                            label = "${stats?.pp?.toInt() ?: 0} PP",
                            color = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                        MiniChip(
                            icon = Icons.Rounded.Public,
                            label = "#${stats?.globalRank ?: "N/A"}",
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    // Country rank on its own row below PP + global
                    // rank — not mixed/wrapped with other chips.
                    Row {
                        MiniChip(
                            icon = Icons.Rounded.Flag,
                            label = "#${stats?.countryRank ?: "N/A"}",
                            color = Color.White,
                        )
                    }
                }

                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun MiniChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
) {
    // FIXED chip height (24dp) + content centered via Box — icon and text
    // always sit in the middle of the chip, never drop downward. Why glyphs
    // dropped: the Torus font + Android's default includeFontPadding (padding
    // above/below glyphs) made the text box taller and sank the glyph down.
    // includeFontPadding=false + explicit lineHeight fixes it.
    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                color = color,
                style = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                        includeFontPadding = false,
                    ),
                ),
            )
        }
    }
}

// ── Stats Grid — ONE horizontal row of 4 compact boxes (same style as the
//    Profile screen's MiniStatsGrid), not a 2×2 grid. ──

@Composable
private fun StatsGrid(stats: net.aokaze.osupanel.data.model.UserStatisticsDto) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DashStat(
            label = stringResource(R.string.dashboard_performance),
            value = "${stats.pp.toInt()}",
            icon = Icons.Rounded.TrendingUp,
            color = OsuColors.blue,
            modifier = Modifier.weight(1f),
        )
        DashStat(
            label = stringResource(R.string.dashboard_global_rank),
            value = stats.globalRank?.let { "#$it" } ?: "#N/A",
            icon = Icons.Rounded.Public,
            color = colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        DashStat(
            label = stringResource(R.string.dashboard_country_rank),
            value = stats.countryRank?.let { "#$it" } ?: "#N/A",
            icon = Icons.Rounded.Flag,
            color = colorScheme.tertiary,
            modifier = Modifier.weight(1f),
        )
        DashStat(
            label = stringResource(R.string.dashboard_accuracy),
            value = String.format("%.2f%%", accuracyPercent(stats.accuracy)),
            icon = Icons.Rounded.TouchApp,
            color = OsuColors.green,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Compact stat box — icon → value → label (identical to Profile's MiniStat). */
@Composable
private fun DashStat(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            fontSize = 11.sp,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

// ── Progress Section ──

@Composable
private fun ProgressSection(stats: net.aokaze.osupanel.data.model.UserStatisticsDto) {
    val colorScheme = MaterialTheme.colorScheme
    val level = stats.levelCurrent
    val progress = (stats.levelProgress * 100).toInt()

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Stars,
                    contentDescription = null,
                    tint = OsuColors.amber600,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.dashboard_progress),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(Modifier.height(16.dp))

            Row {
                Text(
                    stringResource(R.string.dashboard_level) + " $level",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "$progress%",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { stats.levelProgress.toFloat() },
                // M3 linear progress: 4dp, pill-shaped rounded ends.
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = OsuColors.amber,
                // surfaceVariant is clearly visible against the card interior
                // (#36343B); surfaceContainerHighest would blend in invisibly.
                trackColor = colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                // No M3 1.3.0 stop-dot / gap — keeps the clean M3 bar.
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(16.dp))

            Row {
                ProgressStat(
                    icon = Icons.Rounded.Timer,
                    label = stringResource(R.string.dashboard_play_time),
                    value = formatDuration(stats.playTime),
                    color = OsuColors.blue,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                ProgressStat(
                    icon = Icons.Rounded.PlayArrow,
                    label = stringResource(R.string.dashboard_play_count),
                    value = formatNumber(stats.playCount.toLong()),
                    color = OsuColors.purple,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProgressStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = color,
            )
            Text(
                label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Detailed Stats ──

@Composable
private fun DetailedStatsCard(
    user: UserDto,
    stats: net.aokaze.osupanel.data.model.UserStatisticsDto,
) {
    val colorScheme = MaterialTheme.colorScheme
    val rows = listOf(
        stringResource(R.string.dashboard_level) to "${stats.levelCurrent}",
        stringResource(R.string.dashboard_play_count) to "${stats.playCount}",
        stringResource(R.string.dashboard_play_time) to formatDuration(stats.playTime),
        stringResource(R.string.dashboard_total_hits) to "${stats.totalHits}",
        stringResource(R.string.dashboard_max_combo) to "${stats.maximumCombo}x",
        stringResource(R.string.dashboard_ranked_score) to formatNumber(stats.rankedScore),
        stringResource(R.string.dashboard_total_score) to formatNumber(stats.totalScore),
        stringResource(R.string.dashboard_replays_watched) to formatNumber(stats.replaysWatchedByOthers.toLong()),
        stringResource(R.string.dashboard_kudosu) to "${user.kudosu?.total ?: 0}",
    )

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Analytics,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.dashboard_detailed_stats),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(Modifier.height(16.dp))
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, color = colorScheme.onSurfaceVariant)
                    Text(value, fontWeight = FontWeight.SemiBold)
                }
            }
            user.joinDate?.takeIf { it.isNotBlank() }?.let { joined ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.dashboard_joined), color = colorScheme.onSurfaceVariant)
                    Text(joined.take(10), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Grade counters card — its own card (SS / SSH / S / SH / A). */
@Composable
private fun GradeCard(grades: net.aokaze.osupanel.data.model.GradeCountsDto) {
    val colorScheme = MaterialTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Score,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.dashboard_grade_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.dashboard_grade_counters),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradeChip("SS", grades.ss + grades.ssh, OsuColors.gradeSS, Modifier.weight(1f))
                GradeChip("SSH", grades.ssh, OsuColors.gradeSSH, Modifier.weight(1f))
                GradeChip("S", grades.s + grades.sh, OsuColors.gradeS, Modifier.weight(1f))
                GradeChip("SH", grades.sh, OsuColors.gradeSH, Modifier.weight(1f))
                GradeChip("A", grades.a, OsuColors.gradeA, Modifier.weight(1f))
            }
        }
    }
}

/** Small grade-count chip (SS/SSH/S/SH/A) inside the grade card. */
@Composable
private fun GradeChip(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = color)
        Text("$count", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
    }
}

// ── Rank history (mini chart) ──

@Composable
private fun DashboardRankHistoryCard(history: List<Int>) {
    val colorScheme = MaterialTheme.colorScheme
    val ranks = history.map { it.toFloat() }
    val maxY = (ranks.maxOrNull() ?: 0f) + 50f
    val minY = ((ranks.minOrNull() ?: 0f) - 50f).coerceAtLeast(0f)

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.TrendingUp,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.dashboard_rank_history),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "#${history.lastOrNull() ?: 0}",
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                )
            }
            Spacer(Modifier.height(16.dp))
            DashboardLineChart(
                values = ranks,
                minY = minY,
                maxY = maxY,
                color = colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
            )
        }
    }
}

/** Compact line chart — same drawing as the profile detail chart. */
@Composable
private fun DashboardLineChart(
    values: List<Float>,
    minY: Float,
    maxY: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val range = (maxY - minY).coerceAtLeast(1f)

        // Horizontal grid
        for (i in 0..3) {
            val y = h - (h * i / 3f)
            drawLine(
                color = colorScheme.outlineVariant.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f,
            )
        }

        if (values.isEmpty()) return@Canvas

        val step = w / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { index, v ->
            val x = index * step
            val y = ((v - minY) / range) * h
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // Area under the line
        val area = Path().apply {
            addPath(path)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(area, color.copy(alpha = 0.1f))
        drawPath(path, color = color, style = Stroke(width = 2.5f))
    }
}


// ── Medals ──

/** Number of medals shown on the Dashboard before pressing "Show all" (same as Profile). */
private const val MEDALS_INITIAL_SHOW = 9

/** Medal card header — its grid tiles are rendered lazily (see [GridRow]). */
@Composable
private fun MedalHeaderCard(achievedCount: Int, totalCount: Int) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.MilitaryTech,
                    contentDescription = null,
                    tint = OsuColors.amber600,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.dashboard_medals) + " ($achievedCount/$totalCount)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/** Badge card header — its grid tiles are rendered lazily (see [GridRow]). */
@Composable
private fun BadgeHeaderCard(count: Int) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = OsuColors.amber600,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.dashboard_badges) + " ($count)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun EmptySection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(text, color = colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorRetry(message: String?, onRetry: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message ?: stringResource(R.string.error_retry_title),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
            // Try button — SAME as other screens (triangles + right-side loading + retrying).
            RetryButton(
                isLoading = false,
                onClick = onRetry,
            )
        }
    }
}

/** Open a URL in a Custom Tab (in-app overlay, same as the login flow). */
private fun openInCustomTab(
    context: android.content.Context,
    colorScheme: androidx.compose.material3.ColorScheme,
    url: String,
) {
    val builder = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .setToolbarColor(colorScheme.surfaceContainerHighest.toArgb())
        .setDefaultColorSchemeParams(
            CustomTabColorSchemeParams.Builder()
                .setToolbarColor(colorScheme.surfaceContainerHighest.toArgb())
                .setSecondaryToolbarColor(colorScheme.surface.toArgb())
                .build(),
        )
    builder.build().launchUrl(context, Uri.parse(url))
}

// ── Skills radar — osu!skills (osuskills.com, the osu-stats-signature source) ──

/**
 * Dashboard skills card — the SAME 6-axis radar as the profile & widget
 * (STA/ACC/PRE/REA/AGI/TEN), driven by the same osuskills.com data.
 */
@Composable
private fun SkillsRadarCard(skills: net.aokaze.osupanel.widget.SignatureRenderer.SkillsData) {
    val colorScheme = MaterialTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.dashboard_skills_radar),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.dashboard_skills_radar_sub),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(196.dp),
                contentAlignment = Alignment.Center,
            ) {
                SkillRadar(
                    values = skills.radarSkills.map { it.percent },
                    labels = listOf("STA", "ACC", "PRE", "REA", "AGI", "TEN"),
                    tint = colorScheme.primary,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                )
            }
            if (skills.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    skills.tags.joinToString("  ·  "),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OsuColors.pink300,
                )
            }
        }
    }
}

// ── Extra stats: #1 scores / favourites / followers / weighted PP ──

@Composable
private fun ExtraStatsRow(user: UserDto, weightedPp: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (user.scoresFirstCount != null) {
            DashStat(
                label = stringResource(R.string.dashboard_first_place),
                value = formatNumber(user.scoresFirstCount.toLong()),
                icon = Icons.Rounded.EmojiEvents,
                color = OsuColors.amber,
                modifier = Modifier.weight(1f),
            )
        }
        if (user.favouriteCount != null) {
            DashStat(
                label = stringResource(R.string.dashboard_favourites),
                value = formatNumber(user.favouriteCount.toLong()),
                icon = Icons.Rounded.Stars,
                color = OsuColors.pink300,
                modifier = Modifier.weight(1f),
            )
        }
        if (user.followerCount != null) {
            DashStat(
                label = stringResource(R.string.dashboard_followers),
                value = formatNumber(user.followerCount.toLong()),
                icon = Icons.Rounded.People,
                color = OsuColors.cyan,
                modifier = Modifier.weight(1f),
            )
        }
        // Weighted PP — always shown; only the number changes while loading.
        DashStat(
            label = stringResource(R.string.dashboard_weighted_pp),
            value = formatNumber(weightedPp.toLong()),
            icon = Icons.Rounded.TrendingUp,
            color = OsuColors.blue,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Weighted PP alone (when none of the API-optional stats were returned). */
@Composable
private fun WeightedPpStat(weightedPp: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DashStat(
            label = stringResource(R.string.dashboard_weighted_pp),
            value = formatNumber(weightedPp.toLong()),
            icon = Icons.Rounded.TrendingUp,
            color = OsuColors.blue,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Daily Challenge Banner ──
@Composable
private fun DailyChallengeBanner(
    dc: net.aokaze.osupanel.data.model.DailyChallengeResponse,
    onDismiss: () -> Unit,
    onClick: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val beatmapsetId = dc.beatmapsetId ?: dc.beatmapset?.id ?: return
    val bms = dc.beatmapset ?: return
    val bm = dc.beatmap

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(beatmapsetId) },
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(OsuColors.dailyChallengeBg)
        ) {
            // Triangles background
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .trianglesLine(
                        color = Color.White,
                        alpha = 0.15f,
                        scaleAdjust = 0.3f,
                        velocity = 0.5f,
                        spawnRatio = 2f,
                    ),
            )

            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cover circle
                AsyncImage(
                    model = bms.covers?.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.daily_challenge_title),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Text(
                        bms.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                    )
                    Text(
                        bms.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        stringResource(R.string.beatmap_mapped_by, bms.creator ?: "Unknown"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (bm != null) {
                            Text(
                                bm.version,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                            Text(
                                bm.totalLength?.let { formatDuration(it) } ?: "-",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                            Text(
                                "${bms.bpm?.toInt() ?: "-"} BPM",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                // Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

