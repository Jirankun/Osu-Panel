/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.profile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Score
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.theme.OsuColors
import net.aokaze.osupanel.core.util.accuracyPercent
import net.aokaze.osupanel.core.util.formatDuration
import net.aokaze.osupanel.core.util.formatLongDate
import net.aokaze.osupanel.core.util.formatNumber
import net.aokaze.osupanel.data.medal.MedalDisplay
import net.aokaze.osupanel.data.medal.MedalService
import net.aokaze.osupanel.data.model.GradeCountsDto
import net.aokaze.osupanel.data.model.UserDto
import net.aokaze.osupanel.feature.cardgen.ui.CardGenScreen
import net.aokaze.osupanel.feature.profile.ProfileUiState
import net.aokaze.osupanel.feature.profile.ProfileViewModel
import net.aokaze.osupanel.widget.SignatureRenderer
import net.aokaze.osupanel.ui.components.BadgeDetailDialog
import net.aokaze.osupanel.ui.components.BadgeImage
import net.aokaze.osupanel.ui.components.CountryFlagImage
import net.aokaze.osupanel.ui.components.MapCoverImage
import net.aokaze.osupanel.ui.components.MedalExpandButton
import net.aokaze.osupanel.ui.components.MonthlyPlaycountCard
import net.aokaze.osupanel.ui.components.MedalDetailDialog
import net.aokaze.osupanel.ui.components.MedalImage
import net.aokaze.osupanel.ui.components.OsuSpinner
import net.aokaze.osupanel.ui.components.RetryButton
import net.aokaze.osupanel.ui.components.SimpleGrid
import net.aokaze.osupanel.ui.components.SkillRadar
import net.aokaze.osupanel.ui.components.SupporterBadge
import net.aokaze.osupanel.ui.components.rememberMapPlaceholderPainter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin


/** Number of medals shown before pressing "Show all". */
private const val MEDALS_INITIAL_SHOW = 9

/**
 * Profile — a full page pushed
 * from Dashboard / Rankings. Collapsible header (cover), statistics,
 * rank history chart, progress, detail, grade counts, badges, groups,
 * medals (expandable), kudosu, yearly playcount, best scores, most played.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userId: Int,
    onBack: () -> Unit,
    onOpenBeatmapDetail: (Int) -> Unit = {},
    viewModel: ProfileViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(userId) {
        viewModel.load(userId)
    }

    val colorScheme = MaterialTheme.colorScheme

    if (state.isLoading) {
        // Same pinned top bar as the loaded screen (NOT a Material TopAppBar,
        // whose taller 64dp height + default placement would shift the back
        // button when the content finishes loading). Loading shows spinner +
        // back only — the back button sits in the EXACT same spot as loaded.
        Scaffold { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    OsuSpinner(size = 48.dp)
                }
                ProfileTopBar(
                    onBack = onBack,
                    onRefresh = {},
                    showRefresh = false,
                )
            }
        }
        return
    }

    if (state.error != null || state.user == null) {
        // Error layout identical to Beatmap detail: centered error text
        // (same color) + a "Try Again" button below it to retry the load.
        // No top-right refresh (as established) — but the back button stays
        // in the exact same pinned spot as loading/loaded (no shift).
        Scaffold { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.error ?: stringResource(R.string.error_generic),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(16.dp))
                        RetryButton(
                            isLoading = state.isLoading,
                            onClick = { viewModel.load(userId) },
                        )
                    }
                }
                ProfileTopBar(
                    onBack = onBack,
                    onRefresh = {},
                    showRefresh = false,
                )
            }
        }
        return
    }

    val user = state.user!!
    val stats = user.statistics
    val coverUrl = user.cover?.url

    // ── Medals — virtualized grid (same as Dashboard): when expanded,
    // 352 tiles are chunked into rows (3 columns) as lazy items, so only
    // visible rows are composed (anti-lag scrolling).
    var medalsExpanded by remember { mutableStateOf(false) }
    val medalItems = remember(user.achievements) {
        val achievedIds = user.achievements.mapNotNull { it.medal?.achievementId ?: it.achievementId }.toSet()
        val achievedSlugs = user.achievements.mapNotNull { it.medal?.slug }.toSet()
        val achievedAtById = user.achievements.mapNotNull { um ->
            val id = um.medal?.achievementId ?: um.achievementId
            id?.let { idv -> um.achievedAt?.let { idv to it } }
        }.toMap()
        MedalService.buildAllMedalDisplay(achievedIds, achievedSlugs, achievedAtById)
    }
    val medalAchievedCount = medalItems.count { it.achieved }
    val medalHasMore = medalItems.size > MEDALS_INITIAL_SHOW
    val medalRows = remember(medalItems, medalsExpanded) {
        val all = medalItems.chunked(3)
        if (medalsExpanded) all else all.take((MEDALS_INITIAL_SHOW + 2) / 3)
    }

    // Card generator layer (bottom-right FAB → expands into a screen layer).
    var generatorOpen by remember { mutableStateOf(false) }
    // FAB morph (container-transform style): the layer scales up from the
    // FAB's exact position. We capture the FAB center and the layer size to
    // compute the transform pivot, so the growth always starts AT the FAB.
    var fabCenter by remember { mutableStateOf(Offset.Zero) }
    var layerSizePx by remember { mutableStateOf(IntSize.Zero) }
    val morphOrigin = remember(fabCenter, layerSizePx) {
        if (layerSizePx.width > 0 && layerSizePx.height > 0) {
            TransformOrigin(
                pivotFractionX = (fabCenter.x / layerSizePx.width).coerceIn(0f, 1f),
                pivotFractionY = (fabCenter.y / layerSizePx.height).coerceIn(0f, 1f),
            )
        } else {
            TransformOrigin.Center
        }
    }
    val layerCorner by animateDpAsState(
        targetValue = if (generatorOpen) 0.dp else 28.dp,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "cardgenCorner",
    )

    Box {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { generatorOpen = true },
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                modifier = Modifier.onGloballyPositioned {
                    fabCenter = it.boundsInRoot().center
                },
            ) {
                Icon(
                    Icons.Rounded.Badge,
                    contentDescription = stringResource(R.string.cardgen_fab),
                )
            }
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                // ── Cover — part of the scrollable content (slides under the
                //    pinned top bar while scrolling) ──
                item {
                    ProfileCover(
                        user = user,
                        coverUrl = coverUrl,
                    )
                }

                // ── Stats grid (4 columns) ──
            if (stats != null) {
                item {
                    MiniStatsGrid(stats)
                }

                // ── Rank history chart ──
                // rank_history lives at the TOP level of the user object in
                // the API (not inside statistics) — fall back to it.
                (stats.rankHistory ?: user.rankHistory)?.data
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { history ->
                        item {
                            RankHistoryCard(history)
                        }
                    }

                // ── Skills radar + placeholder box (side by side) ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SkillsRadarCard(
                            skills = state.skills,
                            modifier = Modifier.weight(1f),
                        )
                        EmptyRadarSlot(
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // ── Progress ──
                item {
                    ProfileProgressCard(stats)
                }

                // ── Detailed stats ──
                item {
                    DetailedStatsCard(user, stats)
                }

                // ── Grade counts ──
                stats.gradeCounts?.let { grades ->
                    if (grades.ss + grades.ssh + grades.s + grades.sh + grades.a > 0) {
                        item {
                            GradeCountsCard(grades)
                        }
                    }
                }

                // ── Plays per month — last 12 months (osu! web signature
                //    chart), grouped by year with brackets (shared card).
                if (user.monthlyPlaycounts.isNotEmpty()) {
                    item {
                        MonthlyPlaycountCard(
                            monthly = user.monthlyPlaycounts,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // ── Badges ──
            item {
                BadgesCard(user.badges)
            }

            // ── Groups ──
            if (user.groups.isNotEmpty()) {
                item {
                    GroupsCard(user.groups)
                }
            }

            // ── Playstyle (mouse / tablet / keyboard / touch) ──
            if (user.playstyle.isNotEmpty()) {
                item {
                    PlaystyleCard(user.playstyle)
                }
            }

            // ── Medals ──
            item {
                MedalHeaderCard(
                    achievedCount = medalAchievedCount,
                    totalCount = medalItems.size,
                )
            }
            if (medalItems.isEmpty()) {
                item {
                    EmptySection(
                        icon = Icons.Rounded.MilitaryTech,
                        text = stringResource(R.string.profile_no_medals),
                    )
                }
            } else {
                items(medalRows) { rowItems ->
                    MedalGridRow(rowItems)
                }
                if (medalHasMore) {
                    item {
                        MedalExpandButton(
                            expanded = medalsExpanded,
                            totalCount = medalItems.size,
                            onToggle = { medalsExpanded = !medalsExpanded },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // ── Kudosu ──
            item {
                KudosuCard(user)
            }

            // ── Best scores ──
            if (state.bestScores.isNotEmpty()) {
                item {
                    BestScoresCard(state.bestScores, onOpenBeatmapDetail)
                }
            }

            // ── Most played ──
            if (state.mostPlayed.isNotEmpty()) {
                item {
                    MostPlayedCard(state.mostPlayed, onOpenBeatmapDetail)
                }
            }

                item { Spacer(Modifier.height(32.dp)) }
            }

            // ── Pinned top bar — ALWAYS at the top (never scrolls); only the
            //    slim strip with back/refresh. The cover slides beneath it, so
            //    the close button is always reachable without scrolling up. ──
            ProfileTopBar(
                onBack = onBack,
                onRefresh = { viewModel.refresh(userId) },
            )
        }
    }

    // Full-screen card generator layer — morphs out of the FAB itself: it
    // scales up from the FAB's exact position (content already composed, no
    // separate "open" step) while the rounded corners flatten to a full
    // screen; closing shrinks it back into the FAB.
    AnimatedVisibility(
        visible = generatorOpen,
        enter = scaleIn(
            initialScale = 0.1f,
            animationSpec = tween(380, easing = FastOutSlowInEasing),
            transformOrigin = morphOrigin,
        ) + fadeIn(tween(220)),
        exit = scaleOut(
            targetScale = 0.1f,
            animationSpec = tween(320),
            transformOrigin = morphOrigin,
        ) + fadeOut(tween(180)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { layerSizePx = it }
                .clip(RoundedCornerShape(layerCorner)),
        ) {
            CardGenScreen(
                userId = userId,
                user = user,
                onClose = { generatorOpen = false },
            )
        }
    }

    // System back closes the layer first; once it is closed, back behaves
    // normally (pops the profile detail from the nav stack).
    BackHandler(enabled = generatorOpen) { generatorOpen = false }
    }
}

// ── Header ──

/** Pinned top bar — triangles strip + back/refresh buttons, ALWAYS at the
 *  top (never scrolls). The cover scrolls beneath it, so the close button is
 *  always reachable without scrolling back up. */
@Composable
private fun ProfileTopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    showRefresh: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.triangles_header),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.profile_back),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
            if (showRefresh) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.dashboard_refresh),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** Scrollable cover — photo + gradient + identity block (avatar, username,
 *  flags, rank). First LazyColumn item; NOT pinned. */
@Composable
private fun ProfileCover(
    user: UserDto,
    coverUrl: String?,
) {
    val colorScheme = MaterialTheme.colorScheme
    val profileColor = remember(user.profileColour) {
        parseHexColor(user.profileColour)
    }
    val stats = user.statistics

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
    ) {
        // Cover
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            error = rememberMapPlaceholderPainter(),
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.15f),
                            colorScheme.surface.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )

        // Bottom content
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = user.username,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom,
            ) {
                // Username.
                Text(
                    user.username ?: "Unknown",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = profileColor ?: colorScheme.onSurface,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                // flag | country code | user id (+ online status + supporter)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    user.countryCode?.let { code ->
                        CountryFlagImage(countryCode = code, size = 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            code,
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        "#${user.id}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (user.isOnline) OsuColors.green else OsuColors.gray),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(if (user.isOnline) R.string.profile_online else R.string.profile_offline),
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                    if (user.isSupporter) {
                        Spacer(Modifier.width(8.dp))
                        SupporterBadge(supportLevel = user.supportLevel, height = 14.dp)
                    }
                }
                user.joinDate?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.profile_joined, it.take(10)),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            stats?.globalRank?.let { rank ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "#$rank",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                        ),
                    )
                    Text(
                        stringResource(R.string.profile_global),
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Parse "#RRGGBB" → Color (null when invalid). */
private fun parseHexColor(hex: String?): Color? {
    if (hex == null || !hex.matches(Regex("^#[0-9a-fA-F]{6}$"))) return null
    return Color(("FF" + hex.substring(1)).toLong(16))
}

// ── Mini stats grid ──

@Composable
private fun MiniStatsGrid(stats: net.aokaze.osupanel.data.model.UserStatisticsDto) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MiniStat(
            label = stringResource(R.string.profile_pp),
            value = "${stats.pp.toInt()}",
            icon = Icons.Rounded.TrendingUp,
            color = OsuColors.blue,
            modifier = Modifier.weight(1f),
        )
        MiniStat(
            label = stringResource(R.string.profile_acc),
            value = String.format("%.2f", accuracyPercent(stats.accuracy)),
            icon = Icons.Rounded.TouchApp,
            color = OsuColors.orange,
            modifier = Modifier.weight(1f),
        )
        MiniStat(
            label = stringResource(R.string.profile_ranked),
            value = formatNumber(stats.rankedScore),
            icon = Icons.Rounded.Score,
            color = OsuColors.purple,
            modifier = Modifier.weight(1f),
        )
        MiniStat(
            label = stringResource(R.string.profile_lvl),
            value = "${stats.levelCurrent}",
            icon = Icons.Rounded.Stars,
            color = OsuColors.teal,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MiniStat(
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

// ── Rank history chart (Canvas) ──

@Composable
private fun RankHistoryCard(history: List<Int>) {
    val colorScheme = MaterialTheme.colorScheme
    val ranks = history.map { it.toFloat() }
    val maxY = (ranks.maxOrNull() ?: 0f) + 50f
    val minY = ((ranks.minOrNull() ?: 0f) - 50f).coerceAtLeast(0f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.TrendingUp,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.profile_rank_history),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "#${history.lastOrNull() ?: 0}",
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            LineChart(
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

@Composable
private fun LineChart(
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
        for (i in 0..4) {
            val y = h - (h * i / 4f)
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

        drawPath(
            path,
            color = color,
            style = Stroke(width = 2.5f),
        )
    }
}

// ── Progress ──

@Composable
private fun ProfileProgressCard(stats: net.aokaze.osupanel.data.model.UserStatisticsDto) {
    val colorScheme = MaterialTheme.colorScheme
    val level = stats.levelCurrent
    val progress = (stats.levelProgress * 100).toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
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
                    stringResource(R.string.profile_progress),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row {
                Text(
                    stringResource(R.string.profile_lvl) + " $level",
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
                ProfileProgressStat(
                    icon = Icons.Rounded.Timer,
                    label = stringResource(R.string.profile_play_time),
                    value = formatDuration(stats.playTime),
                    color = OsuColors.blue,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                ProfileProgressStat(
                    icon = Icons.Rounded.PlayArrow,
                    label = stringResource(R.string.profile_play_count),
                    value = formatNumber(stats.playCount.toLong()),
                    color = OsuColors.purple,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ProfileProgressStat(
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
            Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = color)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Detailed stats ──

@Composable
private fun DetailedStatsCard(
    user: UserDto,
    stats: net.aokaze.osupanel.data.model.UserStatisticsDto,
) {
    val colorScheme = MaterialTheme.colorScheme
    val items = mutableListOf<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>>(
        Triple(stringResource(R.string.profile_play_count), "${stats.playCount}", Icons.Rounded.PlayArrow),
        Triple(stringResource(R.string.profile_play_time), formatDuration(stats.playTime), Icons.Rounded.Timer),
        Triple(stringResource(R.string.profile_total_hits), "${stats.totalHits}", Icons.Rounded.TouchApp),
        Triple(stringResource(R.string.profile_max_combo), "${stats.maximumCombo}x", Icons.Rounded.Link),
        Triple(stringResource(R.string.profile_total_score), formatNumber(stats.totalScore), Icons.Rounded.Score),
        Triple(stringResource(R.string.profile_ranked_score), formatNumber(stats.rankedScore), Icons.Rounded.WorkspacePremium),
        Triple(stringResource(R.string.profile_country_rank), "#${stats.countryRank ?: "N/A"}", Icons.Rounded.Flag),
        Triple(stringResource(R.string.profile_global_rank), "#${stats.globalRank ?: "N/A"}", Icons.Rounded.Public),
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_detailed_stats),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            items.forEach { (label, value, icon) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(value, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ── Grade counts (compact chips, same style as dashboard) ──

@Composable
private fun GradeCountsCard(grades: GradeCountsDto) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dashboard_grade_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.dashboard_grade_counters),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
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

/** Small grade-count chip (SS/SSH/S/SH/A), same style as dashboard. */
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

// ── Badges ──

@Composable
private fun BadgesCard(badges: List<net.aokaze.osupanel.data.model.BadgeDto>) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = OsuColors.amber600,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.profile_badges) + " (${badges.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (badges.isEmpty()) {
                EmptySection(icon = Icons.Rounded.EmojiEvents, text = stringResource(R.string.profile_no_badges))
            } else {
                SimpleGrid(
                    items = badges,
                    columns = 5,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp,
                ) { badge ->
                    var showDetail by remember(badge) { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colorScheme.surfaceContainerHighest)
                            // Whole card = tap target (not just the photo).
                            .clickable { showDetail = true }
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BadgeImage(
                            badge = badge,
                            size = 44.dp,
                        )
                    }
                    if (showDetail) {
                        BadgeDetailDialog(badge = badge, onDismiss = { showDetail = false })
                    }
                }
            }
        }
    }
}

// ── Groups ──

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GroupsCard(groups: List<net.aokaze.osupanel.data.model.UserGroupDto>) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_groups),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.forEach { group ->
                    val color = parseHexColor(group.colour) ?: colorScheme.primary
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(color.copy(alpha = 0.15f))
                            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            group.shortName?.takeIf { it.isNotEmpty() } ?: group.name ?: "",
                            color = color,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

// ── Playstyle ──

/** Input style chips — mouse / tablet / keyboard / touch (from the API). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlaystyleCard(playstyles: List<String>) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_playstyle),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                playstyles.forEach { style ->
                    val (label, icon) = playstyleInfo(style)
                    val color = net.aokaze.osupanel.core.theme.osuPink(LocalContext.current)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(color.copy(alpha = 0.15f))
                            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                label,
                                color = color,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** osu! playstyle → friendly label + icon. */
private fun playstyleInfo(style: String): Pair<String, androidx.compose.ui.graphics.vector.ImageVector> =
    when (style.lowercase()) {
        "mouse" -> "Mouse" to Icons.Rounded.Mouse
        "tablet" -> "Tablet" to Icons.Rounded.Tablet
        "keyboard" -> "Keyboard" to Icons.Rounded.Keyboard
        "touch" -> "Touch" to Icons.Rounded.TouchApp
        else -> style.replaceFirstChar { it.uppercase() } to Icons.Rounded.SportsEsports
    }

// ── Medals ──

/** Medal card header — its grid tiles are rendered lazily per row in the LazyColumn. */
@Composable
private fun MedalHeaderCard(achievedCount: Int, totalCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.MilitaryTech,
                contentDescription = null,
                tint = OsuColors.amber600,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.profile_medals) + " ($achievedCount/$totalCount)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** One medal grid row (3 columns) — a lazy item in the profile LazyColumn. */
@Composable
private fun MedalGridRow(rowItems: List<MedalDisplay>) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rowItems.forEach { display ->
            Box(modifier = Modifier.weight(1f)) {
                val m = display.medal
                val name = if (m.medalName.isNotEmpty()) m.medalName else m.name
                var showDetail by remember(name, m.achievementIdInt) { mutableStateOf(false) }
                Column(
                    // FIXED tile height — all tiles stay uniform even when the
                    // name wraps to 2 lines or there is no date (previously uneven).
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colorScheme.surfaceContainerHighest)
                        .border(
                            1.dp,
                            colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(14.dp),
                        )
                        // Whole card = tap target (not just the photo).
                        .clickable { showDetail = true }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    MedalImage(
                        name = name,
                        grouping = m.grouping,
                        slug = m.slug,
                        description = m.description,
                        achievementId = m.achievementIdInt,
                        achievedAt = display.achievedAt,
                        achieved = display.achieved,
                        size = 48.dp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        name,
                        // minLines 2: name space is always reserved → content
                        // height stays consistent, text still fits (ellipsis).
                        minLines = 2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                    )
                    display.achievedAt?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            formatLongDate(it),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
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
    }
}

// ── Kudosu ──

@Composable
private fun KudosuCard(user: UserDto) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = OsuColors.amber,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.profile_kudosu),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${user.kudosu?.total ?: 0}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

// ── Best scores ──

@Composable
private fun BestScoresCard(
    scores: List<net.aokaze.osupanel.data.model.ScoreDto>,
    onOpenBeatmapDetail: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_best_scores),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            scores.take(10).forEach { score ->
                val bms = score.beatmapset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { bms?.id?.let(onOpenBeatmapDetail) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MapCoverImage(
                        url = bms?.covers?.list,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            bms?.title ?: "Unknown",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        )
                        Text(
                            "${String.format("%.2f%%", score.accuracy * 100)}  ${score.pp?.toInt() ?: 0}pp",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        score.rank ?: "?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colorScheme.primary,
                    )
                }
            }
        }
    }
}

// ── Most played ──

@Composable
private fun MostPlayedCard(
    items: List<net.aokaze.osupanel.data.model.MostPlayedBeatmapDto>,
    onOpenBeatmapDetail: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_most_played),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            items.take(10).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { item.beatmapset?.id?.let(onOpenBeatmapDetail) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MapCoverImage(
                        url = item.beatmapset?.covers?.list,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.beatmapset?.title ?: "Unknown",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                        )
                        Text(
                            "${item.count} plays",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ── Skills radar (osu!skills — same data as the widget's "with skills") ──

@Composable
private fun SkillsRadarCard(
    skills: SignatureRenderer.SkillsData?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier.height(204.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Text(
                stringResource(R.string.profile_skills_radar),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            if (skills == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.profile_no_skills),
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SkillsRadar(
                    skills = skills,
                    tint = colorScheme.primary,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * 6-axis skills radar — the SAME shape/labels as the dashboard & widget
 * (shared [SkillRadar], osu!skills from osuskills.com), so every radar
 * always shows the same osu!skills values: STA/ACC/PRE/REA/AGI/TEN.
 */
@Composable
private fun SkillsRadar(
    skills: SignatureRenderer.SkillsData,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    SkillRadar(
        values = skills.radarSkills.map { it.percent },
        labels = listOf("STA", "ACC", "PRE", "REA", "AGI", "TEN"),
        tint = tint,
        modifier = modifier,
    )
}

/** Empty placeholder box next to the skills radar — content TBD later. */
@Composable
private fun EmptyRadarSlot(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(204.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        // Intentionally empty for now.
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
