/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.theme.OsuColors
import net.aokaze.osupanel.core.util.formatNumber
import net.aokaze.osupanel.data.model.MonthlyPlaycountDto

/** Minimum width of one month column — below this the chart scrolls sideways. */
private val MIN_BAR_WIDTH = 40.dp

/** Maximum width of one month column (prevents overly fat bars on wide screens). */
private val MAX_BAR_WIDTH = 52.dp

/** Height of the bar lane (the colored bars sit in this fixed-height area). */
private val BAR_LANE_HEIGHT = 64.dp

/**
 * "Plays per Month" bar chart — the last 12 months (osu! web signature chart).
 *
 * When there are MANY months (e.g. several years of history) the chart uses a
 * fixed bar width and scrolls LEFT/RIGHT instead of crushing the bars. Year
 * brackets live in the same scrollable row so they always line up with their
 * bars. Overflow-safe: every label is single-line with ellipsis and the bars
 * sit in a fixed-height lane.
 */
@Composable
fun MonthlyPlaycountCard(
    monthly: List<MonthlyPlaycountDto>,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val max = monthly.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val total = monthly.sumOf { it.count }.toLong()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Header: title + total ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.dashboard_plays_per_month),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.dashboard_plays_total, formatNumber(total)),
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))

            if (monthly.isEmpty()) return@Column

            // ── Chart body — scrollable when months don't fit ──
            val barW = when {
                monthly.size <= 6 -> MAX_BAR_WIDTH               // few months → generous width
                else -> MIN_BAR_WIDTH                            // many months → scrollable
            }
            val totalContentWidth = barW * monthly.size
            val ranges = yearRanges(monthly)
            val scroll = rememberScrollState()

            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                // The whole bar+labels area scrolls horizontally when needed.
                // A Row of MonthColumn items, each at a fixed width.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scroll),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.width(totalContentWidth),
                    ) {
                        monthly.forEachIndexed { i, m ->
                            val range = ranges.firstOrNull { i in it }
                            val isFirst = range?.first == i
                            val isLast = range?.last == i
                            val isCenter = range != null && i == (range.first + range.last) / 2
                            val year = m.startDate?.take(4)?.toIntOrNull()
                            MonthColumn(
                                width = barW,
                                count = m.count,
                                fraction = m.count.toFloat() / max,
                                label = monthShortLabel(m.startDate),
                                isFirst = isFirst,
                                isLast = isLast,
                                isCenter = isCenter,
                                yearLabel = year?.toString().orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One month column: count label + bar + month label + year-bracket segment. */
@Composable
private fun MonthColumn(
    width: Dp,
    count: Int,
    fraction: Float,
    label: String,
    isFirst: Boolean,
    isLast: Boolean,
    isCenter: Boolean,
    yearLabel: String,
) {
    val colorScheme = MaterialTheme.colorScheme
    val barColor = OsuColors.pink300
    val barFaded = OsuColors.pink300.copy(alpha = 0.3f)

    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Count label (above bar) ──
        Text(
            formatNumber(count.toLong()),
            fontSize = 9.sp,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(4.dp))

        // ── Bar ──
        Box(
            modifier = Modifier
                .height(BAR_LANE_HEIGHT)
                .fillMaxWidth(0.65f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val barHeightPx = with(LocalDensity.current) {
                (BAR_LANE_HEIGHT * fraction).toPx().coerceAtLeast(3.dp.toPx())
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { barHeightPx.toDp() })
                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(barColor, barFaded),
                        ),
                    ),
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── Month label ──
        Text(
            label,
            fontSize = 9.sp,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(6.dp))

        // ── Year bracket (line connecting months within same year) ──
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        ) {
            val lineY = size.height * 0.3f
            val stubH = size.height * 0.55f
            val stroke = Stroke(
                width = 1.2.dp.toPx(),
                pathEffect = null,
            )
            val c = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

            // Horizontal line across the column
            drawLine(
                color = c,
                start = Offset(0f, lineY),
                end = Offset(size.width, lineY),
                strokeWidth = stroke.width,
            )
            // Left stub (start of year group)
            if (isFirst) {
                drawLine(
                    color = c,
                    start = Offset(0f, lineY),
                    end = Offset(0f, lineY + stubH),
                    strokeWidth = stroke.width,
                )
            }
            // Right stub (end of year group)
            if (isLast) {
                drawLine(
                    color = c,
                    start = Offset(size.width, lineY),
                    end = Offset(size.width, lineY + stubH),
                    strokeWidth = stroke.width,
                )
            }
        }

        // ── Year label (only in the center column of each year group) ──
        Text(
            if (isCenter) yearLabel else " ",
            fontSize = 9.sp,
            color = colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Consecutive months grouped by year → the index range of each group. */
private fun yearRanges(monthly: List<MonthlyPlaycountDto>): List<IntRange> {
    val yearOf = monthly.map { it.startDate?.take(4)?.toIntOrNull() ?: 0 }
    val ranges = ArrayList<IntRange>()
    var start = 0
    while (start < monthly.size) {
        var end = start
        while (end + 1 < monthly.size && yearOf[end + 1] == yearOf[start]) end++
        ranges.add(start..end)
        start = end + 1
    }
    return ranges
}

/** "2025-01-01" → "Jan" (or "" when unparsable). */
private fun monthShortLabel(startDate: String?): String {
    val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    val month = startDate?.take(10)?.substring(5)?.substring(0, 2)?.toIntOrNull()
    return if (month != null && month in 1..12) months[month - 1] else ""
}
