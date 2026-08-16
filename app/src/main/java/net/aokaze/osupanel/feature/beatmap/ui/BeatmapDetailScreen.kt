/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.beatmap.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.theme.OsuColors
import net.aokaze.osupanel.core.util.formatAudioDuration
import net.aokaze.osupanel.core.util.formatDuration
import net.aokaze.osupanel.core.util.formatNumber
import net.aokaze.osupanel.data.model.BeatmapsetDto
import net.aokaze.osupanel.feature.beatmap.BeatmapDetailViewModel
import net.aokaze.osupanel.ui.components.BannerMessage
import net.aokaze.osupanel.ui.components.BannerType
import net.aokaze.osupanel.ui.components.MapCoverImage
import net.aokaze.osupanel.ui.components.OsuSpinner
import net.aokaze.osupanel.ui.components.RetryButton
import net.aokaze.osupanel.ui.components.TopBanner
import net.aokaze.osupanel.ui.components.trianglesBackground


/**
 * Beatmap Detail — counterpart of the Flutter `BeatmapDetailPage`: collapsible
 * cover header, quick stats, difficulty list (played indicator),
 * info, creator, leaderboard + user score, and audio preview (Media3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatmapDetailScreen(
    beatmapsetId: Int,
    currentUserId: Int?,
    onBack: () -> Unit,
    onOpenProfile: (Int) -> Unit,
    viewModel: BeatmapDetailViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Top banner (snackbar replacement) — one state for error & info.
    var banner by remember { mutableStateOf<BannerMessage?>(null) }

    LaunchedEffect(beatmapsetId) {
        viewModel.load(beatmapsetId, currentUserId)
    }

    val colorScheme = MaterialTheme.colorScheme

    if (state.isLoading) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.beatmap_back))
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                OsuSpinner(size = 48.dp)
            }
        }
        return
    }

    if (state.error != null || state.beatmapset == null) {
        // No top-right refresh here — the "Try Again" button below the error
        // text already retries the load.
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.beatmap_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.beatmap_back))
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.error ?: stringResource(R.string.beatmap_not_available),
                        color = colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    RetryButton(
                        isLoading = state.isLoading,
                        onClick = { viewModel.load(beatmapsetId, currentUserId) },
                    )
                }
            }
        }
        return
    }

    val bms = state.beatmapset!!
    val previewUrl = bms.previewUrl

    // ── Audio preview (Media3 ExoPlayer) ──
    val player = remember {
        ExoPlayer.Builder(context).build()
    }
    // BUGFIX: release the player when the screen closes — otherwise audio
    // keeps playing after beatmap detail is popped (resource leak).
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var isAudioLoading by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    LaunchedEffect(player) {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                isAudioLoading = false
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) isAudioLoading = true
                if (playbackState == Player.STATE_READY) {
                    isAudioLoading = false
                    durationMs = player.duration.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    isPlaying = false
                    positionMs = 0L
                    player.seekTo(0)
                }
            }
        })
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.currentPosition
            // Duration is only known LATER (after STATE_READY) —
            // poll every tick so the "X:XX" total always shows in the chip.
            val d = player.duration
            if (d > 0) durationMs = d
            delay(500)
        }
    }

    val toggleAudio: () -> Unit = {
        if (!isAudioLoading) {
        if (player.isPlaying) {
            player.pause()
        } else if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
            player.play()
        } else {
            isAudioLoading = true
            try {
                previewUrl?.let {
                    player.setMediaItem(MediaItem.fromUri(it))
                    player.prepare()
                    player.play()
                }
            } catch (e: Exception) {
                isAudioLoading = false
                banner = BannerMessage(context.getString(R.string.beatmap_failed_preview))
            }
        }
        }
    }

    val showAudioChip = isPlaying || positionMs != 0L || durationMs != 0L

    Box {
    Scaffold(
        floatingActionButton = {
            if (previewUrl != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Duration slot is ALWAYS present (fixed width & height,
                    // as wide as the button) so the play/pause button does NOT
                    // shift when the chip appears/disappears — the wrap-content
                    // chip is centered inside the slot so the FAB Column width
                    // never changes (Scaffold pins the FAB block's right edge).
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showAudioChip) {
                            // Time pill — same primary + laser triangles as the
                            // play/pause button below, so it reads as one
                            // connected audio control (extension of play/pause).
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(colorScheme.primary),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .trianglesBackground(
                                            scaleAdjust = 0.3f,
                                            velocity = 0.6f,
                                            spawnRatio = 2.5f,
                                            alpha = 0.4f,
                                        ),
                                )
                                Text(
                                    formatAudioDuration(
                                        (positionMs / 1000).toInt(),
                                        if (durationMs > 0) (durationMs / 1000).toInt() else null,
                                    ),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // Play button — SOLID primary (same as the Login button),
                    // white laser triangles on top.
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary)
                            .clickable(onClick = toggleAudio),
                    ) {
                        Box(Modifier.fillMaxSize().trianglesBackground())
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isAudioLoading) {
                                OsuSpinner(size = 22.dp, color = colorScheme.onPrimary)
                            } else {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.beatmap_play_preview),
                                    tint = colorScheme.onPrimary,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            // Full padding (top + bottom) — SAME as ProfileScreen:
            // content does NOT extend fullscreen to the top (it does not cover
            // the status bar / system buttons), the header cover starts below it.
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ── Header cover ──
            item {
                DetailHeader(
                    bms = bms,
                    onBack = onBack,
                    onRefresh = { viewModel.refresh(beatmapsetId, currentUserId) },
                    onOpenProfile = onOpenProfile,
                    onCopyLink = {
                        val mode = bms.beatmaps.firstOrNull { it.id == state.selectedBeatmapId }?.mode ?: "osu"
                        val url = "https://osu.ppy.sh/beatmapsets/$beatmapsetId#$mode/${state.selectedBeatmapId}"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("beatmap link", url))
                        banner = BannerMessage(
                            context.getString(R.string.beatmap_link_copied, url),
                            BannerType.Info,
                        )
                    },
                )
            }

            // ── Quick stats ──
            item {
                QuickStats(bms)
            }

            // ── Difficulties ──
            if (bms.beatmaps.isNotEmpty()) {
                item {
                    DifficultiesCard(
                        bms = bms,
                        selectedId = state.selectedBeatmapId,
                        playedIds = state.playedBeatmapIds,
                        onSelect = { viewModel.switchDifficulty(it, currentUserId) },
                    )
                }
            }

            // ── Info ──
            item {
                InfoCard(bms)
            }

            // ── Creator ──
            item {
                CreatorCard(
                    bms = bms,
                    creatorAvatarUrl = state.creatorAvatarUrl,
                    onOpenProfile = onOpenProfile,
                )
            }

            // ── Leaderboard ──
            item {
                LeaderboardCard(
                    scores = state.scores,
                    myScore = state.myScore,
                    currentUserId = currentUserId,
                    loading = state.scoresLoading,
                    onOpenProfile = onOpenProfile,
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    banner?.let { b ->
        TopBanner(
            message = b.text,
            type = b.type,
            onDismiss = { banner = null },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
    }
}

// ── Header ──

@Composable
private fun DetailHeader(
    bms: BeatmapsetDto,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenProfile: (Int) -> Unit,
    onCopyLink: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        MapCoverImage(
            url = bms.covers?.cover,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.1f),
                        0.5f to Color.Transparent,
                        1.0f to colorScheme.surface.copy(alpha = 0.8f),
                    ),
                ),
        )

        // Top band behind the buttons: dark scrim + white laser triangles
        // (triangles_header.png from res/drawable) — keeps the white top
        // buttons visible on ANY cover (especially white ones).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent),
                    ),
                ),
        ) {
            Image(
                painter = painterResource(R.drawable.triangles_header),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(Color.White),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.beatmap_back),
                    tint = Color.White,
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.beatmap_refresh),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onCopyLink) {
                Icon(
                    Icons.Rounded.Link,
                    contentDescription = stringResource(R.string.beatmap_copy_link),
                    tint = Color.White,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .fillMaxWidth(),
        ) {
            StatusBadge(bms.status)
            Spacer(Modifier.height(8.dp))
            Text(
                bms.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "by ${bms.artist}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { (bms.creatorId ?: bms.userId)?.let(onOpenProfile) }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.beatmap_mapped_by, bms.creator ?: "Unknown"),
                    color = colorScheme.primary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String?) {
    val (bg, fg) = OsuColors.statusColor(status)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            status?.uppercase() ?: stringResource(R.string.beatmap_status_unknown),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

// ── Quick stats ──

@Composable
private fun QuickStats(bms: BeatmapsetDto) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickStat(
            icon = Icons.Rounded.Speed,
            label = stringResource(R.string.beatmap_bpm),
            value = bms.bpm?.toInt()?.toString() ?: "-",
            modifier = Modifier.weight(1f),
        )
        QuickStat(
            icon = Icons.Rounded.Timer,
            label = stringResource(R.string.beatmap_length),
            value = bms.beatmaps.firstOrNull()?.totalLength?.let { formatDuration(it) } ?: "-",
            modifier = Modifier.weight(1f),
        )
        QuickStat(
            icon = Icons.Rounded.PlayArrow,
            label = stringResource(R.string.beatmap_plays),
            value = formatNumber((bms.playCount ?: 0).toLong()),
            modifier = Modifier.weight(1f),
        )
        QuickStat(
            icon = Icons.Rounded.Favorite,
            label = stringResource(R.string.beatmap_fav),
            value = formatNumber((bms.favouriteCount ?: 0).toLong()),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
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
        Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
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

// ── Difficulties ──

@Composable
private fun DifficultiesCard(
    bms: BeatmapsetDto,
    selectedId: Int?,
    playedIds: Set<Int>,
    onSelect: (Int) -> Unit,
) {
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
                    Icons.Rounded.Speed,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.beatmap_difficulties, bms.beatmaps.size),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(Modifier.height(12.dp))
            bms.beatmaps.forEachIndexed { index, bm ->
                val isSelected = bm.id == selectedId
                val isPlayed = playedIds.contains(bm.id)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) {
                                colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                Color.Transparent
                            },
                        ),
                ) {
                        // Laser triangles behind the ACTIVE difficulty — ALWAYS
                        // composed (only alpha changes); same issue as the nav:
                        // wrapping in `if (isSelected)` would make the composable
                        // leave/re-enter the composition on every difficulty
                        // change → the triangle animation restarts from scratch.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .alpha(if (isSelected) 1f else 0f)
                                .trianglesBackground(
                                    scaleAdjust = 0.35f,
                                    velocity = 0.6f,
                                    spawnRatio = 3.5f,
                                    alpha = 0.6f,
                                ),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(bm.id) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                        if (isPlayed) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(OsuColors.green),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                bm.version,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "★${String.format("%.2f", bm.difficultyRating)}  CS ${bm.cs ?: 0}  AR ${bm.ar ?: 0}  " +
                                    formatDuration(bm.totalLength ?: 0),
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            bm.mode?.uppercase() ?: "OSU",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                        )
                        }
                    }
            }
        }
    }
}

// ── Info ──

@Composable
private fun InfoCard(bms: BeatmapsetDto) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.beatmap_info),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))
            if (!bms.source.isNullOrEmpty()) {
                Text(
                    "Source: ${bms.source}",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            if (!bms.tags.isNullOrEmpty()) {
                Text(
                    "Tags: ${bms.tags}",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Creator ──

@Composable
private fun CreatorCard(
    bms: BeatmapsetDto,
    creatorAvatarUrl: String?,
    onOpenProfile: (Int) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { (bms.creatorId ?: bms.userId)?.let(onOpenProfile) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                // Icon behind — shown while the avatar is not loaded yet / failed.
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                AsyncImage(
                    model = creatorAvatarUrl,
                    contentDescription = bms.creator,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.beatmap_creator),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                )
                Text(
                    bms.creator ?: "Unknown",
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ── Leaderboard ──

@Composable
private fun LeaderboardCard(
    scores: List<net.aokaze.osupanel.data.model.ScoreDto>,
    myScore: net.aokaze.osupanel.data.model.ScoreDto?,
    currentUserId: Int?,
    loading: Boolean,
    onOpenProfile: (Int) -> Unit,
) {
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
                    Icons.Rounded.Leaderboard,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.beatmap_leaderboard),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            Spacer(Modifier.height(12.dp))

            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    OsuSpinner(size = 28.dp)
                }
            } else if (scores.isEmpty() && myScore == null) {
                Text(
                    stringResource(R.string.beatmap_no_scores),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            } else {
                // ── Two separate users AT THE TOP ──
                // 1) The logged-in user (if they have a score on this map) — so the
                //    user knows they have played this map, even outside the top 50.
                // 2) The #1 ranked score — if it is not the logged-in user.
                //    (If the logged-in user IS #1, only ONE row is shown: the YOU row
                //    one labeled with its real rank.)
                val rank1 = scores.firstOrNull()
                val pinned = buildList {
                    if (myScore != null) add(myScore)
                    if (rank1 != null && myScore?.userId != rank1.userId) add(rank1)
                }
                val pinnedIds = pinned.mapNotNull { it.userId }.toSet()

                pinned.forEach { score ->
                    val isYou = score.userId == currentUserId
                    val youListIndex = scores.indexOfFirst { it.userId != null && it.userId == currentUserId }
                    ScoreRow(
                        score = score,
                        rankLabel = when {
                            !isYou -> "#1"
                            youListIndex >= 0 -> "#${youListIndex + 1}"
                            else -> "YOU"
                        },
                        highlight = isYou,
                        onOpenProfile = { score.userId?.let(onOpenProfile) },
                    )
                    Spacer(Modifier.height(6.dp))
                }

                if (pinned.isNotEmpty() && scores.any { it.userId !in pinnedIds }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(1.dp)
                            .background(colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    )
                }

                scores.take(50).forEachIndexed { index, score ->
                    if (score.userId in pinnedIds) return@forEachIndexed
                    ScoreRow(
                        score = score,
                        rankLabel = "#${index + 1}",
                        highlight = false,
                        onOpenProfile = { score.userId?.let(onOpenProfile) },
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

/** One leaderboard score row — clicking opens the score owner's profile. */
@Composable
private fun ScoreRow(
    score: net.aokaze.osupanel.data.model.ScoreDto,
    rankLabel: String,
    highlight: Boolean,
    onOpenProfile: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (highlight) colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .clickable(onClick = onOpenProfile)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rankLabel,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = colorScheme.primary,
            modifier = Modifier.width(36.dp),
        )
        AsyncImage(
            model = score.user?.avatarUrl,
            contentDescription = score.user?.username,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                score.user?.username ?: "Unknown",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            )
            Text(
                "${formatNumber(if (score.totalScore > 0) score.totalScore else score.score)} pts  " +
                    "${String.format("%.2f%%", score.accuracy * 100)}  ${score.maxCombo}x",
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant,
            )
        }
        Text(
            score.rank ?: "?",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = colorScheme.primary,
        )
    }
}
