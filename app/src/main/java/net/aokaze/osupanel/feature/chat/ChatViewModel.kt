/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.aokaze.osupanel.data.chatango.ChatangoAuthRepository
import net.aokaze.osupanel.data.chatango.ChatangoPmClient
import net.aokaze.osupanel.data.local.ChatSettingsStore
import java.io.File

@Serializable
data class ChatMessage(
    val from: String,
    val body: String,
    val time: Long,
    val isMine: Boolean,
)

@Serializable
data class Conversation(
    val user: String,
    val messages: List<ChatMessage>,
) {
    val lastTime: Long get() = messages.lastOrNull()?.time ?: 0L
    val preview: String get() = messages.lastOrNull()?.body.orEmpty()
}

data class ChatUiState(
    val connected: Boolean = false,
    val loginOk: Boolean = false,
    val loginDenied: Boolean = false,
    val error: String? = null,
    /** Username confirmed by the server (seller_name). */
    val loggedInAs: String? = null,
    val conversations: List<Conversation> = emptyList(),
    val presence: Map<String, String> = emptyMap(),
    /** Groups the account joined (from groupslistupdate) — auto-fetched. */
    val groups: List<ChatangoAuthRepository.ChatangoGroup> = emptyList(),
    val recentGroups: List<ChatangoAuthRepository.ChatangoGroup> = emptyList(),
    val groupsLoading: Boolean = false,
    val groupsError: Boolean = false,

    /** Global user search results (from /search). */
    val searchResults: List<ChatangoAuthRepository.SearchUser> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
    val searchOffset: Int = 0,
    val searchHasMore: Boolean = false,
    /** Last query used by the search (for auto-load-more). */
    val searchQuery: String = "",
    /** Last (query, filter) pair actually searched — lets the Global tab skip
     *  repeat requests when it re-enters composition (tab switch). */
    val searchedQuery: String = "",
    val searchedFilter: String? = null,

    /** Whitelist (friends) from the socket `wl` command. */
    val friends: List<String> = emptyList(),

    /** Blocked users (from the socket `block_list`). */
    val blocked: List<String> = emptyList(),
    /** True while a fresh `getblock` request is in flight. */
    val blockedLoading: Boolean = false,
)

/**
 * Chat (Chatango PM) state holder — mirrors the socket into Compose state.
 * Conversations are kept in memory for the session (persistence = later step).
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** True once the socket connect was attempted this session. */
    private var loadAttempted = false

    init {
        // Restore conversations persisted from the last session — messages
        // survive leaving a chat (and even an app restart).
        _state.value = _state.value.copy(
            conversations = loadConversations(),
            searchResults = loadSearchResults(),
            searchedQuery = prefs.getString("searched_query", "") ?: "",
            searchedFilter = prefs.getString("searched_filter", null),
        )
        viewModelScope.launch {
            ChatangoPmClient.events.collect { event ->
                when (event) {
                    is ChatangoPmClient.Event.Connected -> {
                        _state.value = _state.value.copy(connected = true, loginDenied = false, error = null)
                    }
                    is ChatangoPmClient.Event.LoginOk -> {
                        _state.value = _state.value.copy(loginOk = true, loginDenied = false)
                        ChatangoPmClient.getWhitelist()
                    }
                    is ChatangoPmClient.Event.SellerName -> {
                        _state.value = _state.value.copy(loggedInAs = event.user)
                    }
                    is ChatangoPmClient.Event.LoginDenied -> {
                        _state.value = _state.value.copy(
                            connected = false,
                            loginOk = false,
                            loginDenied = true,
                            error = "login denied",
                        )
                    }
                    is ChatangoPmClient.Event.Disconnected -> {
                        _state.value = _state.value.copy(connected = false, blockedLoading = false)
                    }
                    is ChatangoPmClient.Event.Message -> {
                        val msg = ChatMessage(
                            from = event.from,
                            body = event.body,
                            time = event.time,
                            isMine = event.from.equals(ownUsername(), ignoreCase = true),
                        )
                        appendMessage(event.from, msg)
                    }
                    is ChatangoPmClient.Event.Status -> {
                        _state.value = _state.value.copy(
                            presence = _state.value.presence + (event.user to event.status),
                        )
                    }
                    is ChatangoPmClient.Event.ChatStarted -> Unit
                    is ChatangoPmClient.Event.Whitelist -> {
                        _state.value = _state.value.copy(friends = event.users)
                    }
                    is ChatangoPmClient.Event.FriendAdded -> {
                        _state.value = _state.value.copy(
                            friends = (_state.value.friends + event.user).distinct(),
                        )
                    }
                    is ChatangoPmClient.Event.FriendRemoved -> {
                        _state.value = _state.value.copy(
                            friends = _state.value.friends.filterNot {
                                it.equals(event.user, ignoreCase = true)
                            },
                        )
                    }
                    is ChatangoPmClient.Event.BlockList -> {
                        _state.value = _state.value.copy(blocked = event.users, blockedLoading = false)
                    }
                    is ChatangoPmClient.Event.UserUnblocked -> {
                        _state.value = _state.value.copy(
                            blocked = _state.value.blocked.filterNot {
                                it.equals(event.user, ignoreCase = true)
                            },
                        )
                    }
                    is ChatangoPmClient.Event.FriendStatus -> {
                        // Presence for friends — reused in the Friends list later.
                        _state.value = _state.value.copy(
                            presence = _state.value.presence + (event.user to event.status),
                        )
                    }
                }
            }
        }
    }

    /**
     * Initial load for the chat session — connects the PM socket and/or fetches
     * the joined groups depending on what is enabled NOW. Runs ONCE per ViewModel
     * session: re-entering the chat tab (or coming back from a chat) must not
     * reconnect the socket / re-fetch everything.
     */
    fun load() {
        if (loadAttempted) return
        loadAttempted = true
        val username = ChatSettingsStore.getUsername(context)
        val token = ChatSettingsStore.getToken(context)
        if (username.isNullOrBlank() || token.isNullOrBlank()) {
            _state.value = ChatUiState()
            return
        }
        ensureActive(
            pmEnabled = ChatSettingsStore.isPmEnabled(context),
            groupEnabled = ChatSettingsStore.isGroupEnabled(context),
        )
    }

    /**
     * Make the session match the CURRENT activation toggles. Called whenever the
     * Chat screen (re)appears, so toggling PM/group chat in Settings takes effect
     * immediately — no app restart needed. Idempotent: no-ops when the socket is
     * already connected / groups are already loaded.
     */
    fun ensureActive(pmEnabled: Boolean, groupEnabled: Boolean) {
        val token = ChatSettingsStore.getToken(context) ?: return
        // PM enabled → connect the socket. The tlogin 3rd arg is the device id,
        // NOT the username and NOT the token (verified against the live server).
        if (pmEnabled && !ChatangoPmClient.isConnected) {
            ChatangoPmClient.connect(token, ChatangoAuthRepository.deviceId(context))
        }
        // Group enabled → fetch the joined groups (works without PM enabled).
        if (groupEnabled && _state.value.groups.isEmpty() &&
            !_state.value.groupsLoading && !_state.value.groupsError
        ) {
            loadGroups()
        }
    }

    /** Fetch the account's joined groups (groupslistupdate). */
    fun loadGroups() {
        val token = ChatSettingsStore.getToken(context) ?: return
        if (_state.value.groupsLoading) return
        _state.value = _state.value.copy(groupsLoading = true, groupsError = false)
        viewModelScope.launch {
            when (val result = ChatangoAuthRepository.getGroups(context, token)) {
                is ChatangoAuthRepository.GroupsResult.Success -> {
                    _state.value = _state.value.copy(
                        groups = result.groups,
                        recentGroups = result.recent,
                        groupsLoading = false,
                        groupsError = false,
                    )
                }
                is ChatangoAuthRepository.GroupsResult.Failure -> {
                    _state.value = _state.value.copy(groupsLoading = false, groupsError = true)
                }
            }
        }
    }

    fun isFriend(user: String): Boolean =
        _state.value.friends.any { it.equals(user, ignoreCase = true) }

    /** Add [user] to the friends list (whitelist) — like the APK's "Add friend". */
    fun addFriend(user: String) {
        if (isFriend(user)) return
        ChatangoPmClient.addFriend(user)
        _state.value = _state.value.copy(friends = _state.value.friends + user)
    }

    /** Remove [user] from the friends list (whitelist). */
    fun removeFriend(user: String) {
        if (!isFriend(user)) return
        ChatangoPmClient.removeFriend(user)
        _state.value = _state.value.copy(
            friends = _state.value.friends.filterNot { it.equals(user, ignoreCase = true) },
        )
    }

    /** Pull-refresh for the Friends list — re-request the whitelist from the socket. */
    fun refreshFriends() {
        if (ChatangoPmClient.isConnected) ChatangoPmClient.getWhitelist()
    }

    fun isBlocked(user: String): Boolean =
        _state.value.blocked.any { it.equals(user, ignoreCase = true) }

    /** Block [user] — socket `block:<u>:<u>:S` (same as the APK). */
    fun block(user: String) {
        if (isBlocked(user)) return
        ChatangoPmClient.block(user)
        _state.value = _state.value.copy(blocked = _state.value.blocked + user)
    }

    /** Unblock [user] — socket `unblock:<u>` (same as the APK). */
    fun unblock(user: String) {
        if (!isBlocked(user)) return
        ChatangoPmClient.unblock(user)
        _state.value = _state.value.copy(
            blocked = _state.value.blocked.filterNot { it.equals(user, ignoreCase = true) },
        )
    }

    /**
     * Re-request the blocked list from the SERVER (`getblock`). This always
     * connects first when needed — a request sent while the socket is down
     * silently goes nowhere, which is why the list used to come back empty.
     */
    fun loadBlocked() {
        val username = ChatSettingsStore.getUsername(context)
        val token = ChatSettingsStore.getToken(context)
        if (username.isNullOrBlank() || token.isNullOrBlank()) return
        _state.value = _state.value.copy(blockedLoading = true)
        if (!ChatangoPmClient.isConnected) {
            ChatangoPmClient.connect(token, ChatangoAuthRepository.deviceId(context))
        }
        // Send after the socket is up (or re-up). The server answers with
        // block_list, which clears blockedLoading when it arrives.
        ChatangoPmClient.sendAfterReady("getblock\r\n")
    }

    /** Send a text message to [user] and echo it into the local conversation. */
    fun send(user: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        ChatangoPmClient.openChat(user)
        ChatangoPmClient.sendMessage(user, trimmed)
        appendMessage(
            user,
            ChatMessage(
                from = ownUsername(),
                body = trimmed,
                time = System.currentTimeMillis(),
                isMine = true,
            ),
        )
    }

    /**
     * Send text and a photo TOGETHER as one message, echoing a single local
     * message whose body carries both — the bubble renders photo + caption.
     * Returns true when sent.
     */
    suspend fun sendTextAndPhoto(user: String, text: String, file: File): Boolean {
        val username = ChatSettingsStore.getUsername(context)
        val password = ChatSettingsStore.getPassword(context)
        val token = ChatSettingsStore.getToken(context)
        if (username.isNullOrBlank() || password.isNullOrBlank() || token.isNullOrBlank()) return false

        if (!ChatangoPmClient.isConnected) {
            ChatangoPmClient.connect(token, ChatangoAuthRepository.deviceId(context))
            repeat(20) {
                if (ChatangoPmClient.isConnected) return@repeat
                delay(100)
            }
        }
        if (!ChatangoPmClient.isConnected) return false

        val imgId = ChatangoAuthRepository.uploadPmImage(context, username, password, file)
        if (imgId == null) return false

        val caption = text.trim()
        val body = if (caption.isEmpty()) {
            "<m v=\"1\"><up s=\"$imgId\" o=\"lib\" /></m>"
        } else {
            "<m v=\"1\"><up s=\"$imgId\" o=\"lib\" />$caption</m>"
        }
        ChatangoPmClient.openChat(user)
        var sent = false
        // Try once, then once more after a short wait if the socket dropped
        // mid-upload — but NEVER send twice (break on first success).
        for (attempt in 1..2) {
            if (ChatangoPmClient.isConnected) {
                ChatangoPmClient.sendMessage(user, body)
                sent = true
                break
            }
            delay(300)
        }
        if (!sent) return false

        appendMessage(
            user,
            ChatMessage(
                from = ownUsername(),
                body = body,
                time = System.currentTimeMillis(),
                isMine = true,
            ),
        )
        return true
    }

    /**
     * Upload [file] and send it as an image message to [user] — same as the
     * APK: uploadimg → "success:<id>" → msgt with `<m v="1"><up s="id" o="lib" /></m>`.
     *
     * Returns true when the image message was sent (upload + socket write ok),
     * false when anything failed — so the UI can keep the draft for a retry.
     */
    suspend fun sendPhoto(user: String, file: File): Boolean {
        val username = ChatSettingsStore.getUsername(context)
        val password = ChatSettingsStore.getPassword(context)
        val token = ChatSettingsStore.getToken(context)
        if (username.isNullOrBlank() || password.isNullOrBlank() || token.isNullOrBlank()) return false

        // Make sure the PM socket is connected first (a fresh app open may
        // still be mid-connect; upload succeeds but msgt would be dropped).
        if (!ChatangoPmClient.isConnected) {
            ChatangoPmClient.connect(token, ChatangoAuthRepository.deviceId(context))
            // Give the socket up to ~2s to come up before sending.
            repeat(20) {
                if (ChatangoPmClient.isConnected) return@repeat
                delay(100)
            }
        }
        if (!ChatangoPmClient.isConnected) return false

        val imgId = ChatangoAuthRepository.uploadPmImage(context, username, password, file)
        if (imgId == null) return false

        val body = "<m v=\"1\"><up s=\"$imgId\" o=\"lib\" /></m>"
        ChatangoPmClient.openChat(user)
        // Retry the socket write once if the connection dropped mid-upload.
        var sent = false
        // Try once, then once more after a short wait if the socket dropped
        // mid-upload — but NEVER send twice (break on first success).
        for (attempt in 1..2) {
            if (ChatangoPmClient.isConnected) {
                ChatangoPmClient.sendMessage(user, body)
                sent = true
                break
            }
            delay(300)
        }
        if (!sent) return false

        appendMessage(
            user,
            ChatMessage(
                from = ownUsername(),
                body = body,
                time = System.currentTimeMillis(),
                isMine = true,
            ),
        )
        return true
    }

    /** The logged-in username (server-confirmed when available). */
    private fun ownUsername(): String =
        _state.value.loggedInAs ?: ChatSettingsStore.getUsername(context).orEmpty()

    private fun appendMessage(user: String, msg: ChatMessage) {
        val conversations = _state.value.conversations.toMutableList()
        val index = conversations.indexOfFirst { it.user.equals(user, ignoreCase = true) }
        if (index >= 0) {
            val conv = conversations[index]
            conversations[index] = conv.copy(messages = conv.messages + msg)
        } else {
            conversations.add(Conversation(user = user, messages = listOf(msg)))
        }
        conversations.sortByDescending { it.lastTime }
        _state.value = _state.value.copy(conversations = conversations)
        saveConversations(conversations)
    }

    // ── Conversation persistence (survives screen closes & app restarts) ──

    private val prefs
        get() = context.getSharedPreferences("osu_panel_chat_conversations", android.content.Context.MODE_PRIVATE)

    private fun loadConversations(): List<Conversation> {
        val raw = prefs.getString("conversations", null) ?: return emptyList()
        return runCatching { Json.decodeFromString<List<Conversation>>(raw) }.getOrDefault(emptyList())
    }

    private fun saveConversations(conversations: List<Conversation>) = runCatching {
        prefs.edit().putString("conversations", Json.encodeToString(conversations)).apply()
    }

    private fun loadSearchResults(): List<ChatangoAuthRepository.SearchUser> {
        val raw = prefs.getString("search_results", null) ?: return emptyList()
        return runCatching {
            Json.decodeFromString<List<ChatangoAuthRepository.SearchUser>>(raw)
        }.getOrDefault(emptyList())
    }

    private fun saveSearchResults(results: List<ChatangoAuthRepository.SearchUser>) = runCatching {
        prefs.edit().putString("search_results", Json.encodeToString(results)).apply()
    }

    private var searchJob: Job? = null

    /** Global user search — [query] empty = browse all (default mode). */
    fun searchUsers(query: String, filter: String = "all") {
        val token = ChatSettingsStore.getToken(context)
        if (token.isNullOrBlank()) {
            _state.value = _state.value.copy(searching = false)
            return
        }
        // Cancel any in-flight request so rapid typing only fires the latest.
        searchJob?.cancel()
        _state.value = _state.value.copy(
            searching = true,
            searchError = null,
            searchOffset = 0,
            searchHasMore = false,
            searchQuery = query.trim(),
            searchedQuery = query.trim(),
            searchedFilter = filter,
        )
        // Remember what was searched so returning to the tab never re-fetches.
        prefs.edit()
            .putString("searched_query", query.trim())
            .putString("searched_filter", filter)
            .apply()
        searchJob = viewModelScope.launch {
            runSearch(token, query.trim(), from = 0, to = SEARCH_PAGE, append = false)
        }
    }

    /** Load the next page of the current search (appends results) — triggered
     *  automatically when the user scrolls to the end (same as Rankings). */
    fun loadMoreSearch() {
        val token = ChatSettingsStore.getToken(context) ?: return
        val s = _state.value
        if (s.searching || !s.searchHasMore) return
        val from = s.searchOffset
        _state.value = _state.value.copy(searching = true, searchError = null)
        viewModelScope.launch {
            runSearch(token, s.searchQuery, from = from, to = from + SEARCH_PAGE, append = true)
        }
    }

    private suspend fun runSearch(token: String, query: String, from: Int, to: Int, append: Boolean) {
        val result = ChatangoAuthRepository.searchUsers(context, token, query, from, to)
        // If this job was cancelled (a newer search replaced it), stop here.
        coroutineContext.ensureActive()
        when (result) {
            is ChatangoAuthRepository.SearchResult.Success -> {
                // Chatango's snapshot shifts between pages, so the same username
                // can appear twice (crashes LazyColumn keyed lists). Dedupe
                // against existing results AND within this page.
                val seen = if (append) {
                    _state.value.searchResults.map { it.username.lowercase() }.toHashSet()
                } else {
                    HashSet()
                }
                val uniqueNew = buildList {
                    for (u in result.users) {
                        if (seen.add(u.username.lowercase())) add(u)
                    }
                }
                val hasMore = result.users.size == to - from
                if (append && uniqueNew.isEmpty() && hasMore) {
                    // This page was all duplicates — skip ahead instead of
                    // leaving a spinning loader with nothing new (max 10 hops).
                    if (from < MAX_SKIP_PAGES * SEARCH_PAGE) {
                        val nextFrom = to
                        runSearch(token, query, from = nextFrom, to = nextFrom + SEARCH_PAGE, append = true)
                    }
                    return
                }
                val results = if (append) _state.value.searchResults + uniqueNew else uniqueNew
                _state.value = _state.value.copy(
                    searchResults = results,
                    searching = false,
                    searchError = null,
                    searchOffset = to,
                    searchHasMore = hasMore,
                )
                saveSearchResults(results)
            }
            is ChatangoAuthRepository.SearchResult.Overloaded -> {
                _state.value = _state.value.copy(searching = false, searchError = "overload")
            }
            is ChatangoAuthRepository.SearchResult.Failure -> {
                _state.value = _state.value.copy(searching = false, searchError = "failure")
            }
        }
    }

    companion object {
        private const val SEARCH_PAGE = 20
        private const val MAX_SKIP_PAGES = 10
    }

    fun messagesFor(user: String): List<ChatMessage> =
        _state.value.conversations
            .firstOrNull { it.user.equals(user, ignoreCase = true) }
            ?.messages.orEmpty()
}
