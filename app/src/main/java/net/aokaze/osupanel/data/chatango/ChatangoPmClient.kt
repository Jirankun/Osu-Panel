/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.chatango

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/**
 * Chatango PM socket client — reverse-engineered from the Chatango 2.1.5 APK
 * (see ANALISIS_CHATANGO.md, section 3):
 *
 *   Transport : TLS socket to cs.chatango.com:443
 *   Login     : `tlogin:<token>:3:<deviceId>\u0000`
 *               (token from settokenapp; deviceId = ANDROID_ID hex→decimal,
 *                see util/a.java in the APK — NOT the username!)
 *   Frames    : commands separated by NUL (\u0000), fields by ':',
 *               lines end with \r\n
 *   Keepalive : send `\r\n` every 30 seconds
 *
 * Incoming frame layout (msg): msg:<from>:<id1>:<id2>:<time>:<flags>:<body>
 *   (time = unix seconds; body may contain ':' → join the rest).
 */
object ChatangoPmClient {

    sealed interface Event {
        /** Socket connected, tlogin sent. */
        data object Connected : Event

        /** Server answered `OK` — logged in. */
        data object LoginOk : Event

        /** Server confirmed the logged-in user (seller_name:<user>:<id>). */
        data class SellerName(val user: String) : Event

        /** Server answered `DENIED` — bad token/username. */
        data object LoginDenied : Event

        data object Disconnected : Event

        /** Incoming message ([offline] = msgoff). */
        data class Message(
            val from: String,
            val body: String,
            val time: Long, // epoch ms
            val offline: Boolean,
        ) : Event

        /** Presence update — status: "online" | "app" | "offline". */
        data class Status(val user: String, val status: String, val time: Long) : Event

        /** User started a chat with us (`connect` event). */
        data class ChatStarted(val user: String) : Event

        /** Whitelist (friends) — response to `wl`; 4 fields per user. */
        data class Whitelist(val users: List<String>) : Event

        /** A user was added to the whitelist (wladd:<user>:<time>:<status>:<flag>). */
        data class FriendAdded(val user: String) : Event

        /** A user was removed from the whitelist (wldelete:<user>:<time>). */
        data class FriendRemoved(val user: String) : Event

        /** Friend presence change — wlonline / wloffline / wlapp. */
        data class FriendStatus(val user: String, val status: String, val time: Long) : Event

        /** Full blocked list — response to `getblock` (block_list:<u>:<u>:…). */
        data class BlockList(val users: List<String>) : Event

        /** A user was unblocked (`unblocked:<user>`) — remove from the list. */
        data class UserUnblocked(val user: String) : Event
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 128)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var writerLock = Any()

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var running = false

    val isConnected: Boolean
        get() = running && socket?.isConnected == true

    /** Connect (disconnects any existing connection first).
     *  [token] from settokenapp, [deviceId] = ANDROID_ID hex→decimal. */
    fun connect(token: String, deviceId: String) {
        disconnect()
        running = true
        scope.launch { runConnection(token, deviceId) }
    }

    fun disconnect() {
        running = false
        runCatching { socket?.close() }
        socket = null
        writer = null
        scope.launch { _events.emit(Event.Disconnected) }
    }

    /** Open a chat with [user] (starts receiving their status/presence). */
    fun openChat(user: String) = write("connect:$user\r\n")

    fun closeChat(user: String) = write("disconnect:$user\r\n")

    /** Send a text message. Message id = hex of current time (like the APK). */
    fun sendMessage(to: String, body: String) {
        val id = java.lang.Long.toHexString(System.currentTimeMillis())
        write("msgt:$id:$to:$body\r\n")
    }

    fun block(user: String) = write("block:$user:$user:S\r\n")

    fun unblock(user: String) = write("unblock:$user\r\n")

    fun getBlockList() = write("getblock\r\n")

    /** Request the whitelist (friends) — answer arrives as a `wl` event. */
    fun getWhitelist() = write("wl\r\n")

    /**
     * Wait until the socket is ready (connected + tlogin sent), then send
     * [command]. Used for sync commands that must land after login (getblock,
     * wl). Runs on the client's IO scope so callers don't need to wait.
     */
    fun sendAfterReady(command: String, timeoutMs: Long = 3000L) {
        scope.launch {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (writer != null) {
                    write(command)
                    return@launch
                }
                delay(100)
            }
        }
    }

    /** Add [user] to the whitelist (friends) — like the APK's "Add friend". */
    fun addFriend(user: String) = write("wladd:$user\r\n")

    /** Remove [user] from the whitelist (friends). */
    fun removeFriend(user: String) = write("wldelete:$user\r\n")

    private fun write(raw: String) {
        val w = writer ?: return
        synchronized(writerLock) {
            runCatching { w.write(raw); w.flush() }
        }
    }

    private suspend fun runConnection(token: String, deviceId: String) {
        try {
            val sock = SSLSocketFactory.getDefault().createSocket(HOST, PORT)
            sock.keepAlive = true
            socket = sock
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val out = BufferedWriter(OutputStreamWriter(sock.getOutputStream()))
            writer = out

            // Send tlogin (frame ends with NUL).
            synchronized(writerLock) {
                out.write("tlogin:$token:3:$deviceId\u0000")
                out.flush()
            }
            _events.emit(Event.Connected)

            // Keepalive: `\r\n` every 30 s.
            val keepAlive = scope.launch {
                while (running && isActive) {
                    delay(KEEPALIVE_MS)
                    write("\r\n")
                }
            }

            // Reader loop: accumulate chars, split frames on NUL.
            val sb = StringBuilder()
            while (running) {
                val c = reader.read()
                if (c == -1) break
                if (c == 0) {
                    val frame = sb.toString()
                    sb.setLength(0)
                    handleFrame(frame)
                } else {
                    sb.append(c.toChar())
                }
            }
            keepAlive.cancel()
        } catch (e: Exception) {
            // Network failure → disconnected.
        } finally {
            running = false
            writer = null
            runCatching { socket?.close() }
            socket = null
            scope.launch { _events.emit(Event.Disconnected) }
        }
    }

    private suspend fun handleFrame(frame: String) {
        val clean = frame.removeSuffix("\r\n")
        if (clean.isBlank()) return
        val parts = clean.split(":")
        if (parts.isEmpty()) return
        when (parts[0]) {
            "OK" -> {
            _events.emit(Event.LoginOk)
            // Same as the APK (pm/a.java case 1): right after login, fetch
            // the blocked list so the server state is always mirrored locally.
            write("getblock\r\n")
        }
            "DENIED" -> _events.emit(Event.LoginDenied)
            "seller_name" -> {
                // seller_name:<user>:<id>
                if (parts.size >= 2) _events.emit(Event.SellerName(parts[1]))
            }
            "msg", "msgoff" -> {
                // msg:<from>:<id1>:<id2>:<time>:<flags>:<body…>
                if (parts.size >= 6) {
                    val from = parts[1]
                    val timeSec = parts[4].toLongOrNull() ?: (System.currentTimeMillis() / 1000)
                    val body = parts.subList(6, parts.size).joinToString(":")
                    _events.emit(
                        Event.Message(
                            from = from,
                            body = body,
                            time = timeSec * 1000,
                            offline = parts[0] == "msgoff",
                        ),
                    )
                }
            }
            "status" -> {
                // status:<user>:<time>:<status>
                if (parts.size >= 4) {
                    _events.emit(
                        Event.Status(
                            user = parts[1],
                            time = (parts[2].toLongOrNull() ?: 0) * 1000,
                            status = parts[3],
                        ),
                    )
                }
            }
            "connect" -> {
                // connect:<user>:<time>:<status>
                if (parts.size >= 2) _events.emit(Event.ChatStarted(parts[1]))
            }
            "wl" -> {
                // wl:<user>:<time>:<status>:<flag>:<user>:…  (4 fields per user)
                val users = parts.drop(1)
                    .filterIndexed { index, _ -> index % 4 == 0 }
                    .filter { it.isNotBlank() }
                _events.emit(Event.Whitelist(users))
            }
            "wladd" -> {
                // wladd:<user>:<time>:<status>:<flag>
                if (parts.size >= 2) _events.emit(Event.FriendAdded(parts[1]))
            }
            "wldelete" -> {
                // wldelete:<user>:<time>
                if (parts.size >= 2) _events.emit(Event.FriendRemoved(parts[1]))
            }
            "wlonline", "wloffline", "wlapp" -> {
                // wlonline:<user>:<time> (offline/app variants)
                if (parts.size >= 2) {
                    _events.emit(
                        Event.FriendStatus(
                            user = parts[1],
                            status = parts[0].removePrefix("wl"),
                            time = (parts.getOrNull(2)?.toLongOrNull() ?: 0) * 1000,
                        ),
                    )
                }
            }
            "block_list" -> {
                // block_list:<user>:<user>:… (users as consecutive fields)
                val users = parts.drop(1).filter { it.isNotBlank() }
                _events.emit(Event.BlockList(users))
            }
            "unblocked" -> {
                // unblocked:<user>
                if (parts.size >= 2) _events.emit(Event.UserUnblocked(parts[1]))
            }
            // Ignored for now: idleupdate, track, msg_rcv_serv, presence,
            // time, kickingoff, reload_profile, …
            else -> Unit
        }
    }

    private const val HOST = "cs.chatango.com"
    private const val PORT = 443
    private const val KEEPALIVE_MS = 30_000L
}
