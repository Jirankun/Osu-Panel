/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.chat.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.activity.ComponentActivity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.theme.osuPink
import net.aokaze.osupanel.core.util.openInCustomTab
import net.aokaze.osupanel.data.chatango.ChatangoAuthRepository
import net.aokaze.osupanel.data.local.ChatSettingsStore
import net.aokaze.osupanel.feature.chat.ChatMessage
import net.aokaze.osupanel.feature.chat.ChatUiState
import net.aokaze.osupanel.feature.chat.ChatViewModel
import net.aokaze.osupanel.feature.chat.Conversation
import net.aokaze.osupanel.feature.chat.RichTextParser
import net.aokaze.osupanel.ui.components.OsuSpinner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Avatar URL like the Chatango app: ust.chatango.com/profileimg/x/y/user/thumb_m.jpg */
private fun avatarUrl(user: String): String {
    val u = user.lowercase()
    val x = u.take(1)
    val y = u.drop(1).take(1).ifEmpty { x }
    return "https://ust.chatango.com/profileimg/$x/$y/$u/thumb_m.jpg"
}

/** "HH:mm" for today, "dd MMM" otherwise. */
private fun chatTime(time: Long): String {
    val fmt = if (System.currentTimeMillis() - time < 24 * 3600 * 1000L) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    } else {
        SimpleDateFormat("dd MMM", Locale.getDefault())
    }
    return fmt.format(Date(time))
}

/**
 * Chat — 2 tabs (Private | Groups), same pattern as MapsScreen:
 * only lists here; tapping opens a full-screen chat (PM native / group WebView).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenPmChat: (String) -> Unit,
    onOpenGroupChat: (String) -> Unit,
    onOpenChatSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel(LocalContext.current as ComponentActivity),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loggedIn = ChatSettingsStore.isLoggedIn(context)
    // Read the activation toggles fresh on every composition — when the user
    // enables PM/group chat in Settings and comes back, these flip immediately
    // (no app restart) and the effect below syncs the session to match.
    val pmEnabled = ChatSettingsStore.isPmEnabled(context)
    val groupEnabled = ChatSettingsStore.isGroupEnabled(context)

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // Hoisted to ChatScreen so switching tabs keeps the Global search state.
    var globalQuery by rememberSaveable { mutableStateOf("") }
    var globalFilter by rememberSaveable { mutableStateOf("all") }
    // User whose profile popup is open (opened by tapping an avatar).
    var profileUser by remember { mutableStateOf<String?>(null) }
    // Full bio popup — shown ABOVE the profile popup (own Dialog, composed later).
    var showBio by remember { mutableStateOf(false) }
    // Blocked-users screen (⋮ menu, top-right of the chat header).
    var showBlocked by remember { mutableStateOf(false) }
    // Overflow menu (⋮) open state.
    var overflowOpen by remember { mutableStateOf(false) }
    // Confirm-block dialog for the profile popup.
    var confirmBlockUser by remember { mutableStateOf<String?>(null) }

    // Blocked-users screen takes over the whole chat area (in-place screen).
    if (showBlocked) {
        // Refresh from the server every time the screen is opened — the
        // blocked list must reflect the SERVER state, not a stale local copy.
        LaunchedEffect(Unit) { viewModel.loadBlocked() }
        BlockedUsersScreen(
            blocked = state.blocked,
            loading = state.blockedLoading,
            onBack = { showBlocked = false },
            onUnblock = { viewModel.unblock(it) },
            onRefresh = { viewModel.loadBlocked() },
            onProfile = { profileUser = it },
        )
        return
    }

    // Initial load + sync with the CURRENT activation toggles every time this
    // screen (re)appears. Keyed on the toggles so activating PM/group chat in
    // Settings (while this screen was off-screen) takes effect right away.
    LaunchedEffect(pmEnabled, groupEnabled) {
        viewModel.ensureActive(pmEnabled, groupEnabled)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.nav_chat), fontWeight = FontWeight.Bold)
                },
                actions = {
                    // ⋮ overflow — blocked users list.
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.chat_more),
                            )
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.chat_blocked_list)) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Block, contentDescription = null)
                                },
                                onClick = {
                                    overflowOpen = false
                                    showBlocked = true
                                    viewModel.loadBlocked()
                                },
                            )
                        }
                    }
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
                    R.string.chat_pm_tab,
                    R.string.chat_group_tab,
                    R.string.chat_global_tab,
                ).forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(label)) },
                    )
                }
            }

            when (selectedTab) {
                0 -> PmTab(
                    loggedIn = loggedIn,
                    pmEnabled = pmEnabled,
                    conversations = state.conversations,
                    onOpenPmChat = onOpenPmChat,
                    onOpenChatSettings = onOpenChatSettings,
                    onProfile = { profileUser = it },
                    // Pull-refresh → reconnect the PM socket (re-sync presence).
                    onRefresh = { viewModel.load() },
                )
                2 -> GlobalTab(
                    loggedIn = loggedIn,
                    state = state,
                    query = globalQuery,
                    onQueryChange = { globalQuery = it },
                    filter = globalFilter,
                    onFilterChange = { globalFilter = it },
                    onSearch = { q, f -> viewModel.searchUsers(q, f) },
                    onLoadMore = { viewModel.loadMoreSearch() },
                    onOpenPmChat = onOpenPmChat,
                    onProfile = { profileUser = it },
                    onRefresh = {
                        when (globalFilter) {
                            "all" -> viewModel.searchUsers(globalQuery, "all")
                            "friend" -> viewModel.refreshFriends()
                            else -> viewModel.load()
                        }
                    },
                )
                else -> {
                    // Auto-fetched groups (groupslistupdate) — no manual names.
                    GroupTab(
                        loggedIn = loggedIn,
                        groupEnabled = groupEnabled,
                        groups = state.groups,
                        loading = state.groupsLoading,
                        error = state.groupsError,
                        onRetry = { viewModel.loadGroups() },
                        onOpenGroupChat = onOpenGroupChat,
                        onOpenChatSettings = onOpenChatSettings,
                        // Pull-refresh → re-fetch joined groups.
                        onRefresh = { viewModel.loadGroups() },
                    )
                }
            }
        }
    }

    profileUser?.let { user ->
        val loggedInAs = ChatSettingsStore.getUsername(context).orEmpty()
        UserProfileDialog(
            user = user,
            isFriend = viewModel.isFriend(user),
            isBlocked = viewModel.isBlocked(user),
            onToggleFriend = {
                if (viewModel.isFriend(user)) viewModel.removeFriend(user)
                else viewModel.addFriend(user)
            },
            onDismiss = { profileUser = null },
            onOpenPmChat = {
                profileUser = null
                onOpenPmChat(user)
            },
            onShowBio = { showBio = true },
            // Your own account (e.g. it shows up in the Global search): gray out
            // chat/friend and hide the block icon — no self-chat/friend/block loop.
            self = user.equals(loggedInAs, ignoreCase = true),
            onBlock = { confirmBlockUser = user },
            onUnblock = { viewModel.unblock(user) },
        )
    }

    // Confirm-block dialog — same flow as the APK ("Block %s?" → Block/Cancel).
    confirmBlockUser?.let { user ->
        AlertDialog(
            onDismissRequest = { confirmBlockUser = null },
            title = { Text(stringResource(R.string.chat_confirm_block_title)) },
            text = { Text(stringResource(R.string.chat_confirm_block_body, user)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.block(user)
                        confirmBlockUser = null
                    },
                ) {
                    Text(stringResource(R.string.chat_block), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlockUser = null }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            },
        )
    }
    // Full bio — its own Dialog window on TOP of the profile popup.
    if (showBio && profileUser != null) {
        BioDialog(
            user = profileUser!!,
            onDismiss = { showBio = false },
        )
    }
}

@Composable
private fun PmTab(
    loggedIn: Boolean,
    pmEnabled: Boolean,
    conversations: List<Conversation>,
    onOpenPmChat: (String) -> Unit,
    onOpenChatSettings: () -> Unit,
    onProfile: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    when {
        !loggedIn -> Placeholder(
            title = stringResource(R.string.chat_login_needed),
            action = stringResource(R.string.chat_open_settings),
            onAction = onOpenChatSettings,
        )
        !pmEnabled -> Placeholder(
            title = stringResource(R.string.chat_not_activated),
            action = stringResource(R.string.chat_open_settings),
            onAction = onOpenChatSettings,
        )
        conversations.isEmpty() -> Placeholder(
            title = stringResource(R.string.chat_empty_conversations),
            action = null,
            onAction = null,
        )
        else -> PullRefreshWrapper(onRefresh = onRefresh) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                items(conversations, key = { it.user }) { conv ->
                    ConversationRow(
                        conv = conv,
                        onClick = { onOpenPmChat(conv.user) },
                        onProfile = { onProfile(conv.user) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 76.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conv: Conversation, onClick: () -> Unit, onProfile: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar box WITHOUT clip — only the photo circle is clipped, so the
        // online dot sits on the rim, half outside the circle.
        Box(
            modifier = Modifier
                .size(52.dp)
                .clickable(onClick = onProfile),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .align(Alignment.TopStart),
            ) {
                AsyncImage(
                    model = avatarUrl(conv.user),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            StatusDot(
                modifier = Modifier.align(Alignment.BottomEnd),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conv.user,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    chatTime(conv.lastTime),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                conv.preview,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GroupTab(
    loggedIn: Boolean,
    groupEnabled: Boolean,
    groups: List<ChatangoAuthRepository.ChatangoGroup>,
    loading: Boolean,
    error: Boolean,
    onRetry: () -> Unit,
    onOpenGroupChat: (String) -> Unit,
    onOpenChatSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    when {
        !loggedIn -> Placeholder(
            title = stringResource(R.string.chat_login_needed),
            action = stringResource(R.string.chat_open_settings),
            onAction = onOpenChatSettings,
        )
        !groupEnabled -> Placeholder(
            title = stringResource(R.string.chat_not_activated),
            action = stringResource(R.string.chat_open_settings),
            onAction = onOpenChatSettings,
        )
        error && groups.isEmpty() -> Placeholder(
            title = stringResource(R.string.chat_groups_error),
            action = stringResource(R.string.chat_retry),
            onAction = onRetry,
        )
        groups.isEmpty() -> Placeholder(
            title = if (loading) stringResource(R.string.chat_connecting)
            else stringResource(R.string.chat_empty_groups),
            action = stringResource(R.string.chat_open_settings),
            onAction = onOpenChatSettings,
        )
        else -> PullRefreshWrapper(onRefresh = onRefresh) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                items(groups, key = { it.name }) { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenGroupChat(group.name) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(osuPink(LocalContext.current).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Rounded.Forum,
                                contentDescription = null,
                                tint = osuPink(LocalContext.current),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                group.title.ifBlank { group.name },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (group.title.isNotBlank() && group.title != group.name) {
                                Text(
                                    group.name,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalTab(
    loggedIn: Boolean,
    state: ChatUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: String,
    onFilterChange: (String) -> Unit,
    onSearch: (String, String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenPmChat: (String) -> Unit,
    onProfile: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    if (!loggedIn) {
        Placeholder(
            title = stringResource(R.string.chat_search_login),
            action = stringResource(R.string.chat_open_settings),
            onAction = null,
        )
        return
    }

    // Debounced search: fires 400 ms after typing stops, and automatically on
    // first open (query="" + filter=all → browse all users right away).
    // Re-entering this tab with the SAME (query, filter) that was already
    // searched skips the request — Chatango rate-limits hard, so switching
    // tabs must not fire a fresh search every time.
    LaunchedEffect(query, filter, state.searchedQuery, state.searchedFilter) {
        if (filter != "all") return@LaunchedEffect
        if (query.trim() == state.searchedQuery && filter == state.searchedFilter) {
            return@LaunchedEffect
        }
        delay(400)
        onSearch(query.trim(), filter)
    }

    val filterLabels = mapOf(
        "all" to stringResource(R.string.chat_filter_all),
        "recent" to stringResource(R.string.chat_filter_recent),
        "friend" to stringResource(R.string.chat_filter_friend),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Search bar — compact rounded pill (smaller + curved corners),
            // the filter dropdown takes the right.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    // Explicit text color — BasicTextField defaults to BLACK
                    // (invisible on the dark theme). Centered via CenterStart.
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (query.isEmpty()) {
                                    Text(
                                        stringResource(R.string.chat_search_hint),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    )
                                }
                                innerTextField()
                            }
                            if (query.isNotBlank()) {
                                Spacer(Modifier.width(6.dp))
                                IconButton(
                                    onClick = { onQueryChange("") },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.width(10.dp))
            FilterDropdown(
                selectedLabel = filterLabels[filter] ?: stringResource(R.string.chat_filter_all),
                onSelect = onFilterChange,
            )
        }

        // Pull-to-refresh wraps ONLY the list — so the standard indicator
        // appears below the search box, not over it.
        PullRefreshWrapper(onRefresh = onRefresh) {
        when (filter) {
            "recent" -> {
                // People we recently chatted with (most recent first).
                if (state.conversations.isEmpty()) {
                    CenteredText(stringResource(R.string.chat_empty_conversations))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                    ) {
                        items(state.conversations, key = { it.user }) { conv ->
                            ConversationRow(
                                conv = conv,
                                onClick = { onOpenPmChat(conv.user) },
                                onProfile = { onProfile(conv.user) },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 76.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
            "friend" -> {
                if (state.friends.isEmpty()) {
                    CenteredText(stringResource(R.string.chat_empty_conversations))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                    ) {
                        items(state.friends, key = { it }) { user ->
                            UserRow(
                                username = user,
                                online = false,
                                onClick = { onOpenPmChat(user) },
                                onProfile = { onProfile(user) },
                            )
                        }
                    }
                }
            }
            else -> {
                // All — browse/search all users, with pagination.
                when {
                    state.searching && state.searchResults.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        OsuSpinner()
                    }
                    state.searchError == "overload" -> SearchErrorBox(
                        message = stringResource(R.string.chat_search_overloaded),
                        onRetry = { onSearch(query.trim(), filter) },
                    )
                    state.searchError == "failure" -> SearchErrorBox(
                        message = stringResource(R.string.chat_search_error),
                        onRetry = { onSearch(query.trim(), filter) },
                    )
                    state.searchResults.isEmpty() -> CenteredText(stringResource(R.string.chat_search_empty))
                    else -> {
                        // Auto-load the next page when the user reaches the list
                        // end (same as Rankings): snapshotFlow on the last visible
                        // item index + a bottom spinner while more pages remain.
                        val listState = rememberLazyListState()
                        LaunchedEffect(listState, state.searchResults.size) {
                            snapshotFlow {
                                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                            }
                                .distinctUntilChanged()
                                .collect { lastVisible ->
                                    if (lastVisible >= state.searchResults.size - 1) onLoadMore()
                                }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            // Bottom padding so the last row & the load-chapter
                            // spinner sit ABOVE the floating nav (same as Rankings).
                            contentPadding = PaddingValues(bottom = 120.dp),
                        ) {
                            items(state.searchResults, key = { it.username }) { user ->
                                UserRow(
                                    username = user.username,
                                    online = user.online,
                                    onClick = { onOpenPmChat(user.username) },
                                    onProfile = { onProfile(user.username) },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 72.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                            }
                            // Bottom loader — same as the Rankings tab: always
                            // shown while more pages remain (spins while a
                            // load-more runs), size 32 like Rankings.
                            if (state.searching || state.searchHasMore) {
                                item(key = "bottom_loader") {
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

/** Compact pill dropdown on the right of the search bar (All / Recent / Friend). */
@Composable
private fun FilterDropdown(selectedLabel: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "all" to stringResource(R.string.chat_filter_all),
        "recent" to stringResource(R.string.chat_filter_recent),
        "friend" to stringResource(R.string.chat_filter_friend),
    )
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                selectedLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Rounded.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Profile popup — opened by tapping a user's avatar. Same structure as the
 * app's ItemDetailDialog (medal/badge popups): centered Dialog, rounded
 * Surface with a scale+fade entrance, photo + username + mod1.xml info,
 * and a full-width action button.
 */
@Composable
internal fun UserProfileDialog(
    user: String,
    isFriend: Boolean,
    onToggleFriend: () -> Unit,
    onDismiss: () -> Unit,
    onOpenPmChat: () -> Unit,
    onShowBio: () -> Unit,
    // When false (e.g. previewing your own profile) both action buttons are
    // disabled/grayed out — they make no sense for yourself.
    enabled: Boolean = true,
    // True when this popup shows the logged-in account itself — same effect
    // as [enabled]=false (no friend/chat/block for yourself, no loop).
    self: Boolean = false,
    // Block action — null hides the block icon (e.g. your own profile).
    isBlocked: Boolean = false,
    onBlock: (() -> Unit)? = null,
    onUnblock: (() -> Unit)? = null,
) {
    var profile by remember { mutableStateOf<ChatangoAuthRepository.ChatangoProfile?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(user) {
        profile = null
        failed = false
        val result = ChatangoAuthRepository.getUserProfile(user)
        profile = result
        failed = result == null
    }

    Dialog(onDismissRequest = onDismiss) {
        // Card entrance — scales up from 90% with a fade (same as ItemDetailDialog).
        var entered by remember { mutableStateOf(false) }
        val cardScale by animateFloatAsState(
            targetValue = if (entered) 1f else 0.9f,
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            label = "profileDialogScale",
        )
        val cardAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(260),
            label = "profileDialogAlpha",
        )
        LaunchedEffect(Unit) { entered = true }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = cardAlpha
                },
        ) {
            Box {
                // Block/Unblock icon — top-right corner (own profile hides it).
                if (!self && onBlock != null) {
                    IconButton(
                        onClick = if (isBlocked) onUnblock ?: {} else onBlock,
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            Icons.Rounded.Block,
                            contentDescription = stringResource(
                                if (isBlocked) R.string.chat_unblock else R.string.chat_block,
                            ),
                            tint = if (isBlocked) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Full profile photo — big circle on a surface chip.
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ChatangoAuthRepository.profilePictureUrl(user),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        )
                    }
                Spacer(Modifier.height(16.dp))
                Text(
                    user,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )

                when {
                    profile != null -> {
                        val p = profile!!
                        val info = listOfNotNull(p.gender, p.age?.toString(), p.city)
                            .joinToString("  •  ")
                        if (info.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                info,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                        if (!p.bio.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            // Bio — one line + pink "see more >" when it overflows
                            // (full version lives in the bio popup above).
                            BioLine(bio = p.bio, onShowBio = onShowBio)
                        }
                    }
                    failed -> {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.chat_profile_load_failed),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                    else -> {
                        Spacer(Modifier.height(10.dp))
                        OsuSpinner(size = 28.dp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                // Friend toggle — same as the APK's "Add friend" (wladd/wldelete).
                // Disabled for your own profile (or a blocked user).
                OutlinedButton(
                    onClick = onToggleFriend,
                    enabled = enabled && !self && !isBlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        stringResource(
                            if (isFriend) R.string.chat_remove_friend else R.string.chat_add_friend,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Primary action — same full-width button style as every popup.
                // Disabled for your own profile (or a blocked user).
                Button(
                    onClick = onOpenPmChat,
                    enabled = enabled && !self && !isBlocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        stringResource(R.string.chat_profile_chat),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                }
            }
        }
    }
}

/** Online/offline dot on the avatar rim (ring = surface color). */
@Composable
private fun StatusDot(modifier: Modifier, color: Color) {
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp)
            .clip(CircleShape)
            .background(color),
    )
}

/** Bio preview — one overflowing line + pink "see more >" (full bio in popup). */
@Composable
private fun BioLine(bio: String, onShowBio: () -> Unit) {
    val annotated = remember(bio) { RichTextParser.parse(bio) }
    var overflow by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = annotated,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.weight(1f, fill = false),
            onTextLayout = { overflow = it.hasVisualOverflow },
        )
        if (overflow) {
            Text(
                stringResource(R.string.chat_bio_see_more),
                color = net.aokaze.osupanel.core.theme.osuPink(LocalContext.current),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clickable(onClick = onShowBio),
            )
        }
    }
}

/** Full bio popup — its own Dialog window, composed ABOVE the profile popup. */
@Composable
internal fun BioDialog(user: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<ChatangoAuthRepository.ChatangoProfile?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(user) {
        profile = null
        failed = false
        val result = ChatangoAuthRepository.getUserProfile(user)
        profile = result
        failed = result == null
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.chat_bio_title, user),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(14.dp))
                when {
                    profile != null && !profile!!.bio.isNullOrBlank() -> {
                        val parsed = remember(profile) { RichTextParser.parseRich(profile!!.bio.orEmpty()) }
                        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                        Text(
                            parsed.annotated,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            onTextLayout = { layoutResult = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                                // Links in the bio open in a Chrome Custom Tab.
                                .pointerInput(parsed) {
                                    detectTapGestures { pos ->
                                        val layout = layoutResult ?: return@detectTapGestures
                                        val offset = layout.getOffsetForPosition(pos)
                                        parsed.links.firstOrNull { offset in it.first }
                                            ?.second
                                            ?.let { openInCustomTab(context, it) }
                                    }
                                },
                        )
                    }
                    failed -> Text(
                        stringResource(R.string.chat_profile_load_failed),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> OsuSpinner(size = 28.dp)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        stringResource(R.string.chat_close),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Error state for the All search with a retry button. */
@Composable
private fun SearchErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text(
            message,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onRetry)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                stringResource(R.string.chat_retry),
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** One user row: avatar + username + online dot. */
@Composable
private fun UserRow(username: String, online: Boolean, onClick: () -> Unit, onProfile: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar box WITHOUT clip — only the photo circle is clipped, so the
        // online dot sits on the rim, half outside the circle.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onProfile),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .align(Alignment.TopStart),
            ) {
                AsyncImage(
                    model = avatarUrl(username),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            StatusDot(
                modifier = Modifier.align(Alignment.BottomEnd),
                color = if (online) {
                    net.aokaze.osupanel.core.theme.osuPink(LocalContext.current)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(username, fontWeight = FontWeight.SemiBold)
            Text(
                if (online) stringResource(R.string.chat_online) else stringResource(R.string.chat_offline),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/** Centered placeholder with optional action button. */
@Composable
private fun Placeholder(title: String, action: String?, onAction: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(
            Icons.Rounded.ChatBubble,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    action,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Pull-to-refresh wrapper — osu! signature spinner on top while refreshing.
 * The refresh action runs for a minimum visible time so the gesture always
 * gives feedback, then the spinner disappears.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullRefreshWrapper(
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (!refreshing) {
                refreshing = true
                scope.launch {
                    onRefresh()
                    delay(700)
                    refreshing = false
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        // Standard Material3 indicator (PullToRefreshDefaults) — no custom
        // spinner: the animated custom one made the gesture feel broken.
        content = content,
    )
}

/**
 * Blocked users — full-area screen opened from the ⋮ menu (same as the APK's
 * "Block list" dialog but as a screen): list of blocked users, each with an
 * Unblock action (socket `unblock:<user>`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockedUsersScreen(
    blocked: List<String>,
    loading: Boolean,
    onBack: () -> Unit,
    onUnblock: (String) -> Unit,
    onRefresh: () -> Unit,
    onProfile: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.chat_blocked_list), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.beatmap_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Fetching from the server — show the spinner instead of a
            // (wrong) empty state while the socket is connecting.
            if (loading && blocked.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OsuSpinner()
                }
            } else if (blocked.isEmpty()) {
                CenteredText(stringResource(R.string.chat_blocked_empty))
            } else {
                PullRefreshWrapper(onRefresh = onRefresh) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp),
                    ) {
                        items(blocked, key = { it }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onProfile(user) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = avatarUrl(user),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        stringResource(R.string.chat_blocked_label),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                                // Unblock — same as the APK (tap → unblock:<user>).
                                OutlinedButton(
                                    onClick = { onUnblock(user) },
                                    modifier = Modifier.height(36.dp),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.chat_unblock),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
        }
    }
}
