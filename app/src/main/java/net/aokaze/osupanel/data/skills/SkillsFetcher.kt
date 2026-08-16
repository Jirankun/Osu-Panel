/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.data.skills

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aokaze.osupanel.widget.SignatureRenderer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Fetches osu!skills data from osuskills.com (a community service — the same
 * source used by osu-stats-signature). The page is scraped with a browser
 * User-Agent (osuskills blocks non-browser UAs), then the 7 skill values +
 * rank tags are parsed from the HTML.
 *
 * Persen mengikuti stat-sign: `min(value / 1000 * 100, 100)`.
 * The 8th skill ("Reading") is ignored — the stat-sign radar only uses 7.
 */
object SkillsFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"

    /** Urutan skill di DOM osuskills (stamina → memory). */
    private val ORDER = listOf("stamina", "tenacity", "agility", "accuracy", "precision", "reaction", "memory")

    /**
     * Fetches skills for [username]. `null` on failure (network / user not
     * found / unexpected page) — the widget then shows "No skills data".
     */
    suspend fun fetch(username: String): SignatureRenderer.SkillsData? = withContext(Dispatchers.IO) {
        val safeName = username.trim().replace(" ", "%20")
        if (safeName.isEmpty()) return@withContext null
        val url = "https://osuskills.com/user/$safeName"
        runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.string() ?: return@runCatching null
            }

            val values = Regex("""<output class="skillValue">\s*([0-9,]+)\s*</output>""")
                .findAll(body)
                .map { it.groupValues[1].replace(",", "").toInt() }
                .toList()
            if (values.size < ORDER.size) return@runCatching null

            val tags = Regex("""userRankTitle[^>]*>\s*([^<]+?)\s*<""")
                .findAll(body)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() }
                .toList()

            fun skill(i: Int): SignatureRenderer.SkillValue {
                val value = values[i]
                return SignatureRenderer.SkillValue(
                    value = value,
                    percent = min(value / 1000f * 100f, 100f),
                )
            }

            SignatureRenderer.SkillsData(
                stamina = skill(0),
                tenacity = skill(1),
                agility = skill(2),
                accuracy = skill(3),
                precision = skill(4),
                reaction = skill(5),
                memory = skill(6),
                tags = tags,
            )
        }.getOrNull()
    }
}
