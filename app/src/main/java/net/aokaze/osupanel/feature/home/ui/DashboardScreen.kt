/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.home.ui

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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import net.aokaze.osupanel.R
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
import net.aokaze.osupanel.ui.components.BadgeImage
import net.aokaze.osupanel.ui.components.CountryFlagImage
import net.aokaze.osupanel.ui.components.MedalExpandButton
import net.aokaze.osupanel.ui.components.MedalImage
import net.aokaze.osupanel.ui.components.RetryButton
import net.aokaze.osupanel.ui.components.OsuSpinner
import net.aokaze.osupanel.ui.components.SimpleGrid
import net.aokaze.osupanel.ui.components.SupporterBadge
import net.aokaze.osupanel.ui.components.rememberMapPlaceholderPainter


/**
 * Dashboard — counterpart of the Flutter `DashboardPage` (first shell tab).
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

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

    val onRefresh: () -> Unit = {
        if (!refreshing) {
            scope.launch {
                refreshing = true
                viewModel.refreshUser()
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }
}

@Composable
private fun DashboardContent(
    user: UserDto,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = user.statistics

    // Medal & badge grids are chunked into rows (4 columns) → each row is
    // ONE lazy item → only visible rows are composed (anti-lag).
    val medalDisplay = remember(user.achievements) {
        val achievedIds = user.achievements.mapNotNull { it.medal?.achievementId ?: it.achievementId }.toSet()
        val achievedSlugs = user.achievements.mapNotNull { it.medal?.slug }.toSet()
        val achievedAtById = user.achievements.mapNotNull { um ->
            um.medal?.achievementId?.let { id ->
                um.achievedAt?.let { id to it }
            }
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

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Profile header card
        item {
            ProfileHeaderCard(user, onOpenProfile)
        }

        if (stats != null) {
            item {
                StatsGrid(stats)
            }
            item {
                ProgressSection(stats)
            }
            item {
                DetailedStatsCard(stats)
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
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BadgeImage(
                            badge = badge,
                            size = 60.dp,
                        )
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
                    icon = Icons.Rounded.EmojiEvents,
                    text = stringResource(R.string.dashboard_no_medals),
                )
            }
        } else {
            items(medalRows) { rowItems ->
                GridRow(rowItems) { display ->
                    val m = display.medal
                    val name = if (m.medalName.isNotEmpty()) m.medalName else m.name
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

// ── Stats Grid ──

@Composable
private fun StatsGrid(stats: net.aokaze.osupanel.data.model.UserStatisticsDto) {
    val colorScheme = MaterialTheme.colorScheme
    val cards = listOf(
        GridStat(
            icon = Icons.Rounded.TrendingUp,
            label = stringResource(R.string.dashboard_performance),
            value = "${stats.pp.toInt()} PP",
            color = OsuColors.blue,
            gradient = listOf(OsuColors.blue400, OsuColors.blue800),
        ),
        GridStat(
            icon = Icons.Rounded.Public,
            label = stringResource(R.string.dashboard_global_rank),
            value = "#${stats.globalRank ?: "N/A"}",
            color = colorScheme.secondary,
            gradient = listOf(colorScheme.secondary, colorScheme.secondary.copy(alpha = 0.7f)),
        ),
        GridStat(
            icon = Icons.Rounded.Flag,
            label = stringResource(R.string.dashboard_country_rank),
            value = "#${stats.countryRank ?: "N/A"}",
            color = colorScheme.tertiary,
            gradient = listOf(colorScheme.tertiary, colorScheme.tertiary.copy(alpha = 0.7f)),
        ),
        GridStat(
            icon = Icons.Rounded.TouchApp,
            label = stringResource(R.string.dashboard_accuracy),
            value = String.format("%.2f%%", accuracyPercent(stats.accuracy)),
            color = OsuColors.green,
            gradient = listOf(OsuColors.green400, OsuColors.green800),
        ),
    )

    SimpleGrid(
        items = cards,
        columns = 2,
    ) {
        GridStatCard(it)
    }
}

private data class GridStat(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val value: String,
    val color: Color,
    val gradient: List<Color>,
)

@Composable
private fun GridStatCard(stat: GridStat) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            colorScheme.surface,
                            colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        ),
                    ),
                )
                .padding(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(stat.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(stat.icon, contentDescription = null, tint = stat.color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stat.value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stat.label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = OsuColors.amber,
                trackColor = colorScheme.surfaceContainerHighest,
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
private fun DetailedStatsCard(stats: net.aokaze.osupanel.data.model.UserStatisticsDto) {
    val colorScheme = MaterialTheme.colorScheme
    val rows = listOf(
        stringResource(R.string.dashboard_level) to "${stats.levelCurrent}",
        stringResource(R.string.dashboard_play_count) to "${stats.playCount}",
        stringResource(R.string.dashboard_play_time) to formatDuration(stats.playTime),
        stringResource(R.string.dashboard_total_hits) to "${stats.totalHits}",
        stringResource(R.string.dashboard_max_combo) to "${stats.maximumCombo}x",
        stringResource(R.string.dashboard_ranked_score) to formatNumber(stats.rankedScore),
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
        }
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
                    Icons.Rounded.EmojiEvents,
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
