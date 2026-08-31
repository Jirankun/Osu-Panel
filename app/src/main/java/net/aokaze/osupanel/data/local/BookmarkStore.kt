/* MIT License — Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio */

package net.aokaze.osupanel.data.local

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local bookmark storage — saves beatmap bookmarks to a JSON file in
 * the app's private storage (Android/data/…/files/bookmarks.json).
 *
 * This is a LOCAL-ONLY feature — no osu! API calls involved.
 */
object BookmarkStore {

    private const val FILE_NAME = "bookmarks.json"
    private const val TAG = "BookmarkStore"

    data class Bookmark(
        val beatmapsetId: Int,
        val title: String,
        val artist: String,
        val creator: String,
        val coverUrl: String,
        val savedAt: Long = System.currentTimeMillis(),
    )

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private var file: File? = null

    /** Initialise with app context — call once from Application.onCreate(). */
    fun init(context: Context) {
        file = File(context.filesDir, FILE_NAME)
        load()
    }

    /** Check if a beatmapset is bookmarked. */
    fun isBookmarked(beatmapsetId: Int): Boolean =
        _bookmarks.value.any { it.beatmapsetId == beatmapsetId }

    /** Toggle bookmark — returns true if now bookmarked, false if removed. */
    suspend fun toggle(beatmapsetId: Int, title: String, artist: String, creator: String, coverUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            val current = _bookmarks.value.toMutableList()
            val existing = current.indexOfFirst { it.beatmapsetId == beatmapsetId }
            if (existing >= 0) {
                current.removeAt(existing)
                _bookmarks.update { current }
                save(current)
                false
            } else {
                current.add(0, Bookmark(beatmapsetId, title, artist, creator, coverUrl))
                _bookmarks.update { current }
                save(current)
                true
            }
        }
    }

    /** Remove a specific bookmark. */
    suspend fun remove(beatmapsetId: Int) {
        withContext(Dispatchers.IO) {
            val current = _bookmarks.value.toMutableList()
            current.removeAll { it.beatmapsetId == beatmapsetId }
            _bookmarks.update { current }
            save(current)
        }
    }

    // ── File I/O ──

    private fun load() {
        try {
            val f = file ?: return
            if (!f.exists()) {
                _bookmarks.value = emptyList()
                return
            }
            val text = f.readText()
            if (text.isBlank()) {
                _bookmarks.value = emptyList()
                return
            }
            val arr = JSONArray(text)
            val list = mutableListOf<Bookmark>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Bookmark(
                        beatmapsetId = obj.getInt("id"),
                        title = obj.optString("title", ""),
                        artist = obj.optString("artist", ""),
                        creator = obj.optString("creator", ""),
                        coverUrl = obj.optString("cover", ""),
                        savedAt = obj.optLong("savedAt", 0L),
                    ),
                )
            }
            _bookmarks.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bookmarks", e)
            _bookmarks.value = emptyList()
        }
    }

    private fun save(list: List<Bookmark>) {
        try {
            val arr = JSONArray()
            for (b in list) {
                arr.put(
                    JSONObject().apply {
                        put("id", b.beatmapsetId)
                        put("title", b.title)
                        put("artist", b.artist)
                        put("creator", b.creator)
                        put("cover", b.coverUrl)
                        put("savedAt", b.savedAt)
                    },
                )
            }
            file?.writeText(arr.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bookmarks", e)
        }
    }
}
