/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.maps.ui

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.theme.OsuColors
import net.aokaze.osupanel.core.util.formatScore
import net.aokaze.osupanel.data.model.BeatmapsetDto
import net.aokaze.osupanel.data.model.MostPlayedBeatmapDto
import net.aokaze.osupanel.data.model.ScoreDto
import net.aokaze.osupanel.feature.maps.MapsTabState
import net.aokaze.osupanel.feature.maps.MapsViewModel
import net.aokaze.osupanel.ui.components.MapCoverImage
import net.aokaze.osupanel.ui.components.OsuSpinner
import net.aokaze.osupanel.ui.components.RetryButton

/**
 * Maps — counterpart of the Flutter `MapPage`: 4 tabs (Last Play / Best Scores /
 * Most Played / Loved), each with 100-per-page pagination,
 * pull-to-refresh, and tapping an item → Beatmap Detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapsScreen(
    userId: Int?,
    onOpenBeatmapDetail: (Int) -> Unit,
    viewModel: MapsViewModel = viewModel(),
) {
    // rememberSaveable: the selected tab survives navigating to beatmap
    // detail and back (plain `remember` died with the composition → the
    // tab always reset to "Last Play").
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Hoisted per-tab list states so each tab keeps its scroll position
    // when switching tabs, or when leaving for beatmap detail and coming
    // back (rememberLazyListState is rememberSaveable-backed).
    val recentListState = rememberLazyListState()
    val bestListState = rememberLazyListState()
    val mostPlayedListState = rememberLazyListState()
    val lovedListState = rememberLazyListState()

    LaunchedEffect(userId) {
        if (userId != null) viewModel.load(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.nav_maps), fontWeight = FontWeight.Bold)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                listOf(
                    R.string.maps_last_play,
                    R.string.maps_best_scores,
                    R.string.maps_most_played,
                    R.string.maps_loved,
                ).forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(label)) },
                    )
                }
            }

            if (userId == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.maps_login_needed),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                return@Column
            }

            when (selectedTab) {
                0 -> {
                    val state by viewModel.recent.collectAsStateWithLifecycle()
                    ScoreTabContent(
                        state = state,
                        listState = recentListState,
                        onRefresh = { viewModel.refresh(userId) },
                        onLoadMore = { viewModel.loadMoreRecent(userId) },
                        onOpenBeatmapDetail = onOpenBeatmapDetail,
                    )
                }
                1 -> {
                    val state by viewModel.best.collectAsStateWithLifecycle()
                    ScoreTabContent(
                        state = state,
                        listState = bestListState,
                        onRefresh = { viewModel.refresh(userId) },
                        onLoadMore = { viewModel.loadMoreBest(userId) },
                        onOpenBeatmapDetail = onOpenBeatmapDetail,
                        showPp = true,
                    )
                }
                2 -> {
                    val state by viewModel.mostPlayed.collectAsStateWithLifecycle()
                    MostPlayedTabContent(
                        state = state,
                        listState = mostPlayedListState,
                        onRefresh = { viewModel.refresh(userId) },
                        onLoadMore = { viewModel.loadMoreMostPlayed(userId) },
                        onOpenBeatmapDetail = onOpenBeatmapDetail,
                    )
                }
                else -> {
                    val state by viewModel.loved.collectAsStateWithLifecycle()
                    LovedTabContent(
                        state = state,
                        listState = lovedListState,
                        onRefresh = { viewModel.refresh(userId) },
                        onLoadMore = { viewModel.loadMoreLoved(userId) },
                        onOpenBeatmapDetail = onOpenBeatmapDetail,
                    )
                }
            }
        }
    }
}

// ── Last Play / Best Scores ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScoreTabContent(
    state: MapsTabState<ScoreDto>,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenBeatmapDetail: (Int) -> Unit,
    showPp: Boolean = false,
) {
    // Real-time scroll detection: near the end of the list → load
    // the next page (pagination never gets stuck).
    LaunchedEffect(listState, state.items.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= state.items.size - 3) onLoadMore()
            }
    }
    val colorScheme = MaterialTheme.colorScheme

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> {
                LoadingList()
            }
            state.error != null && state.items.isEmpty() -> {
                ErrorList(
                    message = state.error!!,
                    isLoading = state.isLoading || state.isRefreshing,
                    onRetry = onRefresh,
                )
            }
            state.items.isEmpty() -> {
                EmptyList(
                    icon = if (showPp) Icons.Rounded.MusicNote else Icons.Rounded.MusicNote,
                    text = stringResource(
                        if (showPp) R.string.maps_no_best else R.string.maps_no_recent,
                    ),
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(state.items) { index, score ->
                        ScoreCard(
                            score = score,
                            showPp = showPp,
                            onOpen = { onOpenBeatmapDetail(it) },
                        )
                    }
                    if (state.hasMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                OsuSpinner(size = 28.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreCard(
    score: ScoreDto,
    showPp: Boolean,
    onOpen: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val bms = score.beatmapset
    val bmsId = bms?.id
    val scoreVal = if (score.totalScore > 0) score.totalScore else score.score
    val rank = score.rank ?: "?"
    val rankColor = OsuColors.rankColor(rank, colorScheme)
    val title = bms?.title ?: "Unknown"
    val artist = bms?.artist ?: "Unknown"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = bmsId != null) { bmsId?.let(onOpen) },
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            MapCoverImage(
                url = bms?.covers?.list,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${stringResource(R.string.maps_by)} $artist",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatScore(scoreVal)} ${stringResource(R.string.maps_pts)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = colorScheme.primary,
                    )
                    if (score.accuracy > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            String.format("%.2f%%", score.accuracy * 100),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (score.maxCombo > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${score.maxCombo}x",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                    score.mods.take(4).forEach { m ->
                        Spacer(Modifier.width(4.dp))
                        Text(
                            m,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                if (showPp && score.pp != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${score.pp.toInt()} pp",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(rankColor.copy(alpha = 0.15f))
                    .border(1.dp, rankColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    rank,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = rankColor,
                )
            }
        }
    }
}

// ── Most Played ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MostPlayedTabContent(
    state: MapsTabState<MostPlayedBeatmapDto>,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenBeatmapDetail: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    LaunchedEffect(listState, state.items.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= state.items.size - 3) onLoadMore()
            }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> LoadingList()
            state.error != null && state.items.isEmpty() ->
                ErrorList(
                    state.error!!,
                    isLoading = state.isLoading || state.isRefreshing,
                    onRetry = onRefresh,
                )
            state.items.isEmpty() -> EmptyList(
                icon = Icons.Rounded.Repeat,
                text = stringResource(R.string.maps_no_most_played),
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.items) { index, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { item.beatmapset?.id?.let(onOpenBeatmapDetail) },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MapCoverImage(
                                url = item.beatmapset?.covers?.list,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.beatmapset?.title ?: "Unknown",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "${stringResource(R.string.maps_by)} ${item.beatmapset?.artist ?: "Unknown"}",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colorScheme.primaryContainer.copy(alpha = 0.5f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "${item.count}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.hasMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            OsuSpinner(size = 28.dp)
                        }
                    }
                }
            }
        }
    }
}

// ── Loved (favourites) ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LovedTabContent(
    state: MapsTabState<BeatmapsetDto>,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenBeatmapDetail: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    LaunchedEffect(listState, state.items.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= state.items.size - 3) onLoadMore()
            }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading && state.items.isEmpty() -> LoadingList()
            state.error != null && state.items.isEmpty() ->
                ErrorList(
                    state.error!!,
                    isLoading = state.isLoading || state.isRefreshing,
                    onRetry = onRefresh,
                )
            state.items.isEmpty() -> EmptyList(
                icon = Icons.Rounded.FavoriteBorder,
                text = stringResource(R.string.maps_no_favourites),
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.items) { index, bms ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { bms.id.let(onOpenBeatmapDetail) },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MapCoverImage(
                                url = bms.covers?.list,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    bms.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    "${stringResource(R.string.maps_by)} ${bms.artist}",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val star = bms.beatmaps.firstOrNull()?.difficultyRating
                            Text(
                                "★${String.format("%.1f", star ?: 0.0)}",
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                if (state.hasMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            OsuSpinner(size = 28.dp)
                        }
                    }
                }
            }
        }
    }
}

// ── Shared helpers ──

@Composable
private fun LoadingList() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        OsuSpinner(size = 36.dp)
    }
}

@Composable
private fun ErrorList(message: String, isLoading: Boolean, onRetry: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                message,
                color = colorScheme.error,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // Try button — SAME as other screens (triangles + right-side loading + retrying).
            RetryButton(
                isLoading = isLoading,
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun EmptyList(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(text, color = colorScheme.onSurfaceVariant)
        }
    }
}



