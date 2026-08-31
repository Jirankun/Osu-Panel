/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.rankings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import net.aokaze.osupanel.R
import net.aokaze.osupanel.feature.rankings.RankingsUiState
import net.aokaze.osupanel.feature.rankings.RankingsViewModel
import net.aokaze.osupanel.ui.components.CountryFlagImage
import net.aokaze.osupanel.ui.components.OsuSpinner
import net.aokaze.osupanel.ui.components.RetryButton
import net.aokaze.osupanel.ui.components.rememberAvatarPlaceholderPainter
import net.aokaze.osupanel.ui.components.trianglesLine

private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1.0f)

/**
 * Rankings — global performance ranking,
 * country filter (scrollable chips + flag PNG), global search
 * (150ms debounce, rotating icon animation), pagination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen(
    onOpenProfile: (Int) -> Unit,
    viewModel: RankingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    // Search bar bounds (window coords) — used to detect "tap outside the text box".
    var searchBarBounds by remember { mutableStateOf<Rect?>(null) }
    var columnOrigin by remember { mutableStateOf(Offset.Zero) }

    // Real-time scroll detection: load the next page WHEN the last item
    // becomes visible (rows.size - 1). Previously used rows.size - 3 (prefetch 3 rows)
    // → the load finished before the user reached the bottom → the bottom spinner
    // was never visible. Now loading starts when the user reaches the list end,
    // and the spinner (last item, always present while hasMore) shows below the last
    // item. Disabled while a search is active.
    LaunchedEffect(listState, state.rows.size, state.searchQuery) {
        if (state.searchQuery.isNotEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible >= state.rows.size - 1) viewModel.onScrollNearEnd()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.nav_rankings), fontWeight = FontWeight.Bold)
                },
                actions = {
                    SearchToggleButton(
                        isOpen = state.isSearchOpen,
                        onToggle = { viewModel.toggleSearch() },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onGloballyPositioned { columnOrigin = it.positionInRoot() }
                // Tap outside the text box → focus lost (keyboard closes).
                // Detected in PointerEventPass.Initial so it stays visible
                // even when the tap is consumed by a scrollable child (LazyRow/LazyColumn).
                .pointerInput(searchBarBounds) {
                    awaitPointerEventScope {
                        var down: Offset? = null
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val c = event.changes.firstOrNull() ?: continue
                            if (c.pressed) {
                                if (down == null) down = c.position
                            } else if (!c.pressed && down != null) {
                                val from = down!!
                                down = null
                                val moved = (c.position - from).getDistance() > 24f
                                // Skip if the tap is inside the search bar itself.
                                val onField = searchBarBounds?.contains(from + columnOrigin) == true
                                if (!moved && !onField) {
                                    focusManager.clearFocus()
                                }
                            }
                        }
                    }
                },
        ) {
            // Country filter — still shown even on error
            CountryFilter(
                selected = remember(state) { viewModel.selectedCountryCode },
                onSelect = { viewModel.selectCountry(it) },
                onClear = { viewModel.selectCountry(null) },
            )

            // Search bar animasi
            AnimatedVisibility(
                visible = state.isSearchOpen,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.onQueryChanged(it) },
                    onSearch = {
                        viewModel.onSearchSubmit()
                        focusManager.clearFocus()
                    },
                    onClear = { viewModel.clearSearchQuery() },
                    onBoundsChanged = { searchBarBounds = it },
                )
            }

            // Konten
            Box(Modifier.fillMaxSize()) {
                when {
                    state.isLoading && state.rows.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            OsuSpinner(size = 48.dp)
                        }
                    }
                    state.error != null && state.rows.isEmpty() -> {
                        ErrorState(
                            message = state.error!!,
                            isLoading = state.isRefreshing || state.isLoading,
                            onRetry = { viewModel.refresh() },
                        )
                    }
                    state.displayRows.isEmpty() && !state.isLoading -> {
                        EmptyState(
                            isSearch = state.searchQuery.isNotEmpty(),
                            query = state.searchQuery,
                        )
                    }
                    else -> {
                        PullToRefreshBox(
                            isRefreshing = state.isRefreshing,
                            onRefresh = { viewModel.refresh() },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 120.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                itemsIndexed(state.displayRows) { index, row ->
                                    UserTile(
                                        row = row,
                                        rank = index + 1,
                                        onOpen = { row.user.id.let(onOpenProfile) },
                                    )
                                }
                                // Next-page loading indicator — ALWAYS shown
                                // as the last item while more pages
                                // remain (hasMore). This way a user who
                                // scrolls to the bottom WILL see it, and while
                                // load-more runs the spinner spins. Previously it
                                // only showed while idle (`hasMore && !isLoading`)
                                // → it DISAPPEARED during load (isLoading=true), and
                                // with only isLoadingMore → the item sat below
                                // the screen fold so it was never seen.
                                val showBottomLoader = state.isSearchingApi ||
                                    (state.searchQuery.isEmpty() &&
                                        (state.isLoadingMore || (state.hasMore && !state.isLoading)))
                                if (showBottomLoader) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            OsuSpinner(size = 32.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Search icon — rotates 0→180° while fading out, replaced by an up arrow. */
@Composable
private fun SearchToggleButton(isOpen: Boolean, onToggle: () -> Unit) {
    val anim = remember { Animatable(if (isOpen) 1f else 0f) }
    LaunchedEffect(isOpen) {
        anim.animateTo(
            if (isOpen) 1f else 0f,
            tween(250, easing = EaseOutCubic),
        )
    }
    val t = anim.value

    IconButton(onClick = onToggle) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Search,
                contentDescription = stringResource(R.string.rankings_search_hint),
                modifier = Modifier
                    .size(22.dp)
                    .alpha(1f - t)
                    .rotate(t * 180f),
            )
            Icon(
                Icons.Rounded.ArrowUpward,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .alpha(t),
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onBoundsChanged: (Rect) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) },
        placeholder = { Text(stringResource(R.string.rankings_search_hint), fontSize = 14.sp) },
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearch()
                focusManager.clearFocus()
            },
        ),
    )
}

@Composable
private fun CountryFilter(
    selected: String?,
    onSelect: (String?) -> Unit,
    onClear: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 4.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (selected != null) {
                    stringResource(R.string.rankings_country) + ": ${selected}"
                } else {
                    stringResource(R.string.rankings_country_all)
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurfaceVariant,
            )
            if (selected != null) {
                Spacer(Modifier.width(6.dp))
                CountryFlagImage(countryCode = selected, size = 14.dp)
                Spacer(Modifier.weight(1f))
                // Clear button — subtle triangles inside the pill.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.errorContainer.copy(alpha = 0.6f))
                        .clickable(onClick = onClear),
                ) {
                    // matchParentSize (NOT fillMaxSize): follows the pill size
                    // without defining it — fillMaxSize on an unconstrained
                    // Box would expand it to the full screen.
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .trianglesLine(
                                scaleAdjust = 0.3f,
                                velocity = 0.6f,
                                spawnRatio = 2f,
                                alpha = 0.5f,
                            ),
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            tint = colorScheme.onErrorContainer,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            stringResource(R.string.rankings_clear),
                            fontSize = 11.sp,
                            color = colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.height(40.dp),
        ) {
            item {
                CountryChip(
                    label = stringResource(R.string.rankings_all),
                    isActive = selected == null,
                    onClick = { if (selected != null) onSelect(null) },
                )
            }
            itemsIndexed(RankingsViewModel.countryCodes) { _, code ->
                CountryChip(
                    label = code,
                    flagCode = code,
                    isActive = code == selected,
                    onClick = { if (code != selected) onSelect(code) },
                )
            }
        }
    }
}

@Composable
private fun CountryChip(
    label: String,
    flagCode: String? = null,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    // Triangles appear only on the ACTIVE chip (like the nav) — fade in/out.
    // ALWAYS composed (only alpha changes) so the triangle animation does not
    // restart from 0 on every state change.
    val triAnim = remember { Animatable(if (isActive) 1f else 0f) }
    LaunchedEffect(isActive) {
        triAnim.animateTo(if (isActive) 1f else 0f, tween(300, easing = EaseOutCubic))
    }

    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isActive) {
                    colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                },
            )
            .border(
                width = 1.2.dp,
                color = if (isActive) {
                    colorScheme.primary.copy(alpha = 0.5f)
                } else {
                    colorScheme.outlineVariant.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Triangles layer — matchParentSize (not fillMaxSize!) so it follows
        // the chip size without stretching it to the full screen.
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .alpha(triAnim.value)
                .trianglesLine(
                    scaleAdjust = 0.35f,
                    velocity = 0.6f,
                    spawnRatio = 3.5f,
                    alpha = 0.6f,
                ),
        )
        // Content on top of the triangles.
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (flagCode != null) {
                CountryFlagImage(countryCode = flagCode, size = 14.dp)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) colorScheme.primary else colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UserTile(
    row: net.aokaze.osupanel.feature.rankings.RankingRow,
    rank: Int,
    onOpen: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val user = row.user

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "#${row.globalRank ?: rank}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = colorScheme.primary,
                modifier = Modifier.width(44.dp),
            )
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = user.username,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                error = rememberAvatarPlaceholderPainter(),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.username ?: "Unknown",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${user.countryCode ?: "??"}  |  #${row.globalRank ?: "N/A"}",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${row.pp.toInt()} PP",
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EmptyState(isSearch: Boolean, query: String) {
    val colorScheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isSearch) Icons.Rounded.SearchOff else Icons.Rounded.Leaderboard,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (isSearch) {
                    stringResource(R.string.rankings_no_matching) + " \"$query\""
                } else {
                    stringResource(R.string.rankings_no_data)
                },
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    isLoading: Boolean,
    onRetry: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.error,
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
