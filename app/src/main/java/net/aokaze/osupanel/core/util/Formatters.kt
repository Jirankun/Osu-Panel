/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.core.util

import java.text.NumberFormat
import java.time.OffsetDateTime
import java.util.Locale

/** "Xh Ym" format. */
fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return "${hours}h ${minutes}m"
}

/**
 * Accuracy to percent — the osu! API sends a 0–1 fraction (e.g. 0.791).
 * Normalization so the UI always shows "79.10%" instead of "0.79%".
 */
fun accuracyPercent(accuracy: Double): Double =
    if (accuracy <= 1.0) accuracy * 100 else accuracy

/** "1.2M" / "1.2K" format. */
fun formatNumber(number: Long): String = when {
    number >= 1_000_000 -> "${(number / 1_000_000.0).format1() }M"
    number >= 1_000 -> "${(number / 1_000.0).format1()}K"
    else -> number.toString()
}

/** "1.23M" — two-decimal score format (Maps `_formatScore` counterpart). */
fun formatScore(score: Long): String = when {
    score >= 1_000_000 -> "${(score / 1_000_000.0).format2()}M"
    score >= 1_000 -> "${(score / 1_000.0).format1()}K"
    else -> score.toString()
}

/** "Xm Ys" — audio duration (beatmap detail `_formatAudioDuration` counterpart). */
fun formatAudioDuration(positionSeconds: Int, totalSeconds: Int?): String {
    val pos = formatMSS(positionSeconds)
    val total = totalSeconds?.let { formatMSS(it) }
    return if (total != null) "$pos / $total" else pos
}

private fun formatMSS(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

private fun Double.format1(): String =
    if (this == toLong().toDouble()) toLong().toString() else String.format("%.1f", this)

private fun Double.format2(): String = String.format("%.2f", this)

private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/** Display date "15 January 2024" (profile screen). */
fun formatLongDate(iso: String): String = runCatching {
    val d = OffsetDateTime.parse(iso)
    "${d.dayOfMonth} ${monthNames[d.monthValue - 1]} ${d.year}"
}.getOrElse { iso.take(10) }

/** Display date "2024, January, 15" (medal/badge achieved date). */
fun formatAchievedDate(iso: String): String = runCatching {
    val d = OffsetDateTime.parse(iso)
    "${d.year}, ${monthNames[d.monthValue - 1]}, ${d.dayOfMonth}"
}.getOrElse { iso.take(10) }

/** Comma-grouped number: "12,345" (osu! web style — for pp, ranks, counts). */
fun formatNumberGrouped(value: Long): String =
    NumberFormat.getIntegerInstance(Locale.US).format(value)

/** stat-sign playtime: "134d 10h 38m" (or "10h 38m" when < 1 day). */
fun formatPlaytimeSig(seconds: Int): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return if (days > 0) "${days}d ${hours}h ${minutes}m" else "${hours}h ${minutes}m"
}
