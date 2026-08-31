/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.beatmap.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.theme.OsuColors
import net.aokaze.osupanel.core.util.formatDuration
import net.aokaze.osupanel.core.util.formatNumber
import net.aokaze.osupanel.data.local.BookmarkStore
import net.aokaze.osupanel.data.model.BeatmapsetDto
import net.aokaze.osupanel.feature.beatmap.BeatmapDetailViewModel
import net.aokaze.osupanel.ui.components.MapCoverImage
import net.aokaze.osupanel.ui.components.OsuSpinner
import net.aokaze.osupanel.ui.components.RetryButton
import net.aokaze.osupanel.ui.components.trianglesLine
import android.util.Base64
import java.io.File
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** QR viewer hosted at osu-panel.zhyllanfyllah.my.id */
private const val QR_VIEWER_URL = "https://osu-panel.zhyllanfyllah.my.id/qr"

/** Shared audio cache — one SimpleCache per app (required by Media3). */
private var sharedAudioCache: SimpleCache? = null
private fun getAudioCache(context: Context): SimpleCache {
    return sharedAudioCache ?: synchronized(Unit) {
        sharedAudioCache ?: run {
            val dir = File(context.cacheDir, "audio_preview")
            dir.mkdirs()
            val evictor = LeastRecentlyUsedCacheEvictor(10L * 1024 * 1024)
            SimpleCache(dir, evictor, StandaloneDatabaseProvider(context.applicationContext))
                .also { sharedAudioCache = it }
        }
    }
}

/** Build base64-encoded QR payload for a beatmap. */
internal fun buildQrBase64(
    bms: BeatmapsetDto,
    bm: net.aokaze.osupanel.data.model.BeatmapDto?,
    osuUrl: String,
): String {
    val json = buildJsonObject {
        put("title", JsonPrimitive(bms.title))
        put("artist", JsonPrimitive(bms.artist))
        put("mapper", JsonPrimitive(bms.creator ?: ""))
        put("bpm", JsonPrimitive(bms.bpm ?: 0.0))
        put("length", JsonPrimitive(formatDuration(bm?.totalLength ?: 0)))
                put("cover", JsonPrimitive(bms.covers?.cover2x ?: bms.covers?.cover ?: "https://assets.ppy.sh/beatmaps/${bms.id}/covers/list.jpg"))
        put("url", JsonPrimitive(osuUrl))
        put("tags", JsonPrimitive(bms.tags ?: ""))
        put("preview", JsonPrimitive(bms.previewUrl ?: ""))
    }.toString()
    return Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatmapDetailScreen(
    beatmapsetId: Int,
    currentUserId: Int?,
    onBack: () -> Unit,
    onOpenProfile: (Int) -> Unit,
    onOpenQr: (Int) -> Unit = {},
    viewModel: BeatmapDetailViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(beatmapsetId) {
        viewModel.load(beatmapsetId, currentUserId)
    }

    val colorScheme = MaterialTheme.colorScheme

    if (state.isLoading) {
        Scaffold { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OsuSpinner(size = 48.dp)
                }
                DetailTopBar(onBack = onBack, onRefresh = {}, onShare = {}, showActions = false)
            }
        }
        return
    }

    if (state.error != null || state.beatmapset == null) {
        Scaffold { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
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
                DetailTopBar(onBack = onBack, onRefresh = {}, onShare = {}, showActions = false)
            }
        }
        return
    }

    val bms = state.beatmapset!!
    val previewUrl = bms.previewUrl

    // ── Audio preview (Media3 ExoPlayer) — shared cache, single instance ──
    val scope = rememberCoroutineScope()
    val player = remember {
        val cache = getAudioCache(context)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cacheFactory)
            )
            .build()
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var isAudioLoading by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var audioPrepared by remember { mutableStateOf(false) }

    // ── Pause + fade-out when the app goes to background ──
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(player) {
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onPause(owner: androidx.lifecycle.LifecycleOwner) {
                if (player.isPlaying) {
                    scope.launch {
                        val steps = 10
                        for (i in steps downTo 1) {
                            player.volume = i.toFloat() / steps
                            delay(30L)
                        }
                        player.pause()
                        player.volume = 1f
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    // Load preview ONCE when URL is available
    LaunchedEffect(previewUrl) {
        if (previewUrl != null && !audioPrepared) {
            isAudioLoading = true
            try {
                player.setMediaItem(MediaItem.fromUri(previewUrl))
                player.prepare()
                audioPrepared = true
            } catch (e: Exception) {
                isAudioLoading = false
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.currentPosition
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
            } else if (audioPrepared) {
                player.play()
            }
        }
    }






    val vibe: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    val vibrateTick = { vibe.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)) }

    Box {
        Scaffold(
            floatingActionButton = {
                // Bottom pill: Bookmark + Play/Pause
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Bookmark button
                    val isBookmarked = remember(state.beatmapset) {
                        state.beatmapset?.let { BookmarkStore.isBookmarked(beatmapsetId) } ?: false
                    }
                    var bookmarked by remember { mutableStateOf(isBookmarked) }
                    LaunchedEffect(beatmapsetId) {
                        bookmarked = BookmarkStore.isBookmarked(beatmapsetId)
                    }
                    IconButton(
                        onClick = {
                            val bms = state.beatmapset ?: return@IconButton
                            scope.launch {
                                bookmarked = BookmarkStore.toggle(
                                    beatmapsetId = beatmapsetId,
                                    title = bms.title ?: "",
                                    artist = bms.artist ?: "",
                                    creator = bms.creator ?: "",
                                    coverUrl = bms.covers?.cover2x ?: bms.covers?.cover ?: "",
                                )
                            }
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = stringResource(R.string.bookmark_add),
                            tint = Color.White,
                        )
                    }
                    // Divider
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(Color.White.copy(alpha = 0.3f)),
                    )
                    // Play/Pause button
                    IconButton(
                        onClick = toggleAudio,
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (isAudioLoading) {
                            OsuSpinner(size = 20.dp)
                        } else {
                            Icon(
                                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.beatmap_play_preview),
                                tint = Color.White,
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            val listState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item { DetailCover(bms = bms, onOpenProfile = onOpenProfile) }
                    item { QuickStats(bms) }
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
                    item { InfoCard(bms) }
                    item {
                        CreatorCard(
                            bms = bms,
                            creatorAvatarUrl = state.creatorAvatarUrl,
                            onOpenProfile = onOpenProfile,
                        )
                    }
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

                DetailTopBar(
                    onBack = onBack,
                    onRefresh = { viewModel.refresh(beatmapsetId, currentUserId) },
                    onShare = {
                        val mode = bms.beatmaps.firstOrNull { it.id == state.selectedBeatmapId }?.mode ?: "osu"
                        val bm = bms.beatmaps.firstOrNull { it.id == state.selectedBeatmapId }
                            ?: bms.beatmaps.firstOrNull()
                        val shareUrl = "$QR_VIEWER_URL/$beatmapsetId"
                        val diffInfo = bm?.let {
                            "\u2605${String.format("%.2f", it.difficultyRating)} ${it.version}"
                        } ?: ""
                        val details = buildString {
                            appendLine("🎵 ${bms.title} \u2014 ${bms.artist}")
                            appendLine("👤 ${bms.creator ?: "Unknown"}${if (diffInfo.isNotEmpty()) " | $diffInfo" else ""}")
                            appendLine("🎵 BPM: ${bms.bpm?.toInt() ?: "-"} | ⏱ ${bm?.totalLength?.let { formatDuration(it) } ?: "-"}")
                            if (!bms.tags.isNullOrBlank()) {
                                appendLine("🏷\uFE0F ${bms.tags}")
                            }
                            appendLine()
                            append(shareUrl)
                        }
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, details)
                            putExtra(Intent.EXTRA_SUBJECT, "${bms.title} \u2014 ${bms.artist}")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.beatmap_share_qr)))
                    },
                    onOpenQr = {
                        vibrateTick()
                        onOpenQr(beatmapsetId)
                    },
                )
            }
        }
    }
}





// ── Header ──
@Composable
private fun DetailTopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onShare: () -> Unit,
    onOpenQr: () -> Unit = {},
    showActions: Boolean = true,
) {
    Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Image(
            painter = painterResource(R.drawable.triangles_header),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.beatmap_back), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (showActions) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.beatmap_refresh), tint = Color.White)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.beatmap_share_qr), tint = Color.White)
                }
                IconButton(onClick = onOpenQr) {
                    Icon(Icons.Rounded.QrCode, contentDescription = stringResource(R.string.beatmap_share_qr), tint = Color.White)
                }
            }
        }
    }
}

// ── Cover, QuickStats, Difficulties, Info, Creator, Leaderboard ──
@Composable
private fun DetailCover(bms: BeatmapsetDto, onOpenProfile: (Int) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
        MapCoverImage(url = bms.covers?.cover, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.1f),
                    0.5f to Color.Transparent,
                    1.0f to colorScheme.surface.copy(alpha = 0.8f),
                ),
            ),
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp).fillMaxWidth()) {
            StatusBadge(bms.status)
            Spacer(Modifier.height(8.dp))
            Text(bms.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = colorScheme.onSurface))
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.beatmap_artist_by, bms.artist), maxLines = 1, overflow = TextOverflow.Ellipsis, color = colorScheme.onSurfaceVariant, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { (bms.creatorId ?: bms.userId)?.let(onOpenProfile) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.beatmap_mapped_by, bms.creator ?: "Unknown"), color = colorScheme.primary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String?) {
    val (bg, fg) = OsuColors.statusColor(status)
    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(status?.uppercase() ?: stringResource(R.string.beatmap_status_unknown), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun QuickStats(bms: BeatmapsetDto) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickStat(Icons.Rounded.Speed, stringResource(R.string.beatmap_bpm), bms.bpm?.toInt()?.toString() ?: "-", Modifier.weight(1f))
        QuickStat(Icons.Rounded.Timer, stringResource(R.string.beatmap_length), bms.beatmaps.firstOrNull()?.totalLength?.let { formatDuration(it) } ?: "-", Modifier.weight(1f))
        QuickStat(Icons.Rounded.PlayArrow, stringResource(R.string.beatmap_plays), formatNumber((bms.playCount ?: 0).toLong()), Modifier.weight(1f))
        QuickStat(Icons.Rounded.Favorite, stringResource(R.string.beatmap_fav), formatNumber((bms.favouriteCount ?: 0).toLong()), Modifier.weight(1f))
    }
}

@Composable
private fun QuickStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).background(colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DifficultiesCard(bms: BeatmapsetDto, selectedId: Int?, playedIds: Set<Int>, onSelect: (Int) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.beatmap_difficulties, bms.beatmaps.size), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.height(12.dp))
            bms.beatmaps.forEachIndexed { _, bm ->
                val isSelected = bm.id == selectedId
                val isPlayed = playedIds.contains(bm.id)
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)) {
                    Box(modifier = Modifier.matchParentSize().clip(RoundedCornerShape(10.dp)).alpha(if (isSelected) 1f else 0f).trianglesLine(scaleAdjust = 0.35f, velocity = 0.6f, spawnRatio = 3.5f, alpha = 0.6f))
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(bm.id) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isPlayed) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(OsuColors.green))
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bm.version, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("\u2605${String.format("%.2f", bm.difficultyRating)}  CS ${bm.cs ?: 0}  AR ${bm.ar ?: 0}  ${formatDuration(bm.totalLength ?: 0)}", fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                        }
                        Text(bm.mode?.uppercase() ?: "OSU", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(bms: BeatmapsetDto) {
    val colorScheme = MaterialTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.beatmap_info), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(8.dp))
            if (!bms.source.isNullOrEmpty()) {
                Text(stringResource(R.string.beatmap_source, bms.source.orEmpty()), fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
            }
            if (!bms.tags.isNullOrEmpty()) {
                Text(stringResource(R.string.beatmap_tags, bms.tags.orEmpty()), fontSize = 13.sp, color = colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CreatorCard(bms: BeatmapsetDto, creatorAvatarUrl: String?, onOpenProfile: (Int) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { (bms.creatorId ?: bms.userId)?.let(onOpenProfile) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                AsyncImage(model = creatorAvatarUrl, contentDescription = bms.creator, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(CircleShape))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.beatmap_creator), fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                Text(bms.creator ?: "Unknown", fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun LeaderboardCard(scores: List<net.aokaze.osupanel.data.model.ScoreDto>, myScore: net.aokaze.osupanel.data.model.ScoreDto?, currentUserId: Int?, loading: Boolean, onOpenProfile: (Int) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Leaderboard, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.beatmap_leaderboard), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(Modifier.height(12.dp))
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) { OsuSpinner(size = 28.dp) }
            } else if (scores.isEmpty() && myScore == null) {
                Text(stringResource(R.string.beatmap_no_scores), color = colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                val rank1 = scores.firstOrNull()
                val pinned = buildList {
                    if (myScore != null) add(myScore)
                    if (rank1 != null && myScore?.userId != rank1.userId) add(rank1)
                }
                val pinnedIds = pinned.mapNotNull { it.userId }.toSet()
                pinned.forEach { score ->
                    val isYou = score.userId == currentUserId
                    val youListIndex = scores.indexOfFirst { it.userId != null && it.userId == currentUserId }
                    ScoreRow(score = score, rankLabel = when { !isYou -> "#1"; youListIndex >= 0 -> "#${youListIndex + 1}"; else -> "YOU" }, highlight = isYou, onOpenProfile = { score.userId?.let(onOpenProfile) })
                    Spacer(Modifier.height(6.dp))
                }
                if (pinned.isNotEmpty() && scores.any { it.userId !in pinnedIds }) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                }
                scores.take(50).forEachIndexed { index, score ->
                    if (score.userId in pinnedIds) return@forEachIndexed
                    ScoreRow(score = score, rankLabel = "#${index + 1}", highlight = false, onOpenProfile = { score.userId?.let(onOpenProfile) })
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(score: net.aokaze.osupanel.data.model.ScoreDto, rankLabel: String, highlight: Boolean, onOpenProfile: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (highlight) colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent).clickable(onClick = onOpenProfile).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rankLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colorScheme.primary, modifier = Modifier.width(36.dp))
        AsyncImage(model = score.user?.avatarUrl, contentDescription = score.user?.username, contentScale = ContentScale.Crop, modifier = Modifier.size(28.dp).clip(CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(score.user?.username ?: "Unknown", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            Text("${formatNumber(if (score.totalScore > 0) score.totalScore else score.score)} pts  ${String.format("%.2f%%", score.accuracy * 100)}  ${score.maxCombo}x", fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
        }
        Text(score.rank ?: "?", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colorScheme.primary)
    }
}
