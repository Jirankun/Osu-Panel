/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.widget

import net.aokaze.osupanel.core.util.accuracyPercent
import net.aokaze.osupanel.core.util.formatNumber
import net.aokaze.osupanel.data.model.UserDto
import java.text.NumberFormat
import java.util.Locale

/**
 * Builds [SignatureRenderer.Data] from a live [UserDto] — used by the card
 * generator (Profile → FAB → Card Generator).
 *
 * The number formats match [net.aokaze.osupanel.data.local.WidgetDataStore]
 * EXACTLY (one implementation — the widget store delegates here too), so a
 * generated card looks identical to the home screen widget: pp/ranks/counts
 * use osu! web grouping ("12,345"), scores use the K/M formatter, playtime
 * uses the stat-sign "134d 10h 38m" style.
 */
object SignatureDataMapper {

    /** osu! web style number format: "12,345" (comma grouping, no decimals). */
    fun formatNumber(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

    /** stat-sign playtime format: "134d 10h 38m" (or "10h 38m"). */
    fun formatPlaytimeSig(seconds: Int): String {
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        return if (days > 0) "${days}d ${hours}h ${minutes}m" else "${hours}h ${minutes}m"
    }

    /**
     * All dynamic data shown on the signature, straight from a [UserDto].
     *
     * @param mode  game mode ("std"/"catch"/"taiko"/"mania") — stats must be
     *              for that mode already ([UserDto.statistics] swapped).
     * @param layout "stats" (default), "skills" (osu!skills radar) or "mini".
     */
    fun buildData(
        user: UserDto,
        mode: String = "std",
        layout: String = "stats",
        skills: SignatureRenderer.SkillsData? = null,
    ): SignatureRenderer.Data {
        val stats = user.statistics
        val grades = stats?.gradeCounts
        return SignatureRenderer.Data(
            username = user.username.orEmpty(),
            playmode = mode,
            countryCode = (user.country?.code ?: user.countryCode).orEmpty(),
            countryName = user.country?.name.orEmpty(),
            level = stats?.levelCurrent ?: 0,
            levelProgressPercent = ((stats?.levelProgress ?: 0.0) * 100).toInt().coerceIn(0, 100),
            pp = formatNumber((stats?.pp ?: 0.0).toLong()),
            medals = formatNumber(user.achievements.size.toLong()),
            playtime = formatPlaytimeSig(stats?.playTime ?: 0),
            globalRank = stats?.globalRank?.let { "#${formatNumber(it.toLong())}" }.orEmpty(),
            countryRank = stats?.countryRank?.let { "#${formatNumber(it.toLong())}" }.orEmpty(),
            rankedScore = formatNumber(stats?.rankedScore ?: 0L),
            playCount = formatNumber((stats?.playCount ?: 0).toLong()),
            totalScore = formatNumber(stats?.totalScore ?: 0L),
            totalHits = formatNumber(stats?.totalHits ?: 0L),
            replays = formatNumber((stats?.replaysWatchedByOthers ?: 0).toLong()),
            acc = String.format(Locale.US, "%.2f%%", accuracyPercent(stats?.accuracy ?: 0.0)),
            maxCombo = "${formatNumber(stats?.maximumCombo ?: 0L)}x",
            bp = "-",
            firstPlace = "-",
            gradeSsh = grades?.ssh ?: 0,
            gradeSs = grades?.ss ?: 0,
            gradeSh = grades?.sh ?: 0,
            gradeS = grades?.s ?: 0,
            gradeA = grades?.a ?: 0,
            profileColour = user.profileColour,
            layout = layout,
            skills = skills,
        )
    }
}
