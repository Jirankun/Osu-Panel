/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import net.aokaze.osupanel.core.util.formatAchievedDate
import net.aokaze.osupanel.data.model.BadgeDto

/**
 * osu! badge image (from a network URL via Coil) — PURE display, no click
 * handling: the parent tile decides the tap target, so the WHOLE card opens
 * the detail popup (not just the photo). Callers render [BadgeDetailDialog]
 * themselves when the card is tapped.
 *
 * The [modifier] controls the container (grid cards pass `fillMaxSize` so the
 * box covers the full tile); the badge itself is always [size] and centered.
 */
@Composable
fun BadgeImage(
    badge: BadgeDto,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = badge.imageUrl,
            contentDescription = badge.description,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp)),
            error = {
                Icon(
                    Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(size * 0.6f),
                )
            },
        )
    }
}

/** Badge detail dialog — image + description + awarded date. */
@Composable
fun BadgeDetailDialog(
    badge: BadgeDto,
    onDismiss: () -> Unit,
) {
    ItemDetailDialog(
        title = null,
        description = badge.description,
        dateText = badge.awardedAt
            ?.takeIf { it.isNotEmpty() }
            ?.let { formatAchievedDate(it) },
        achieved = true,
        onDismiss = onDismiss,
        imageContent = {
            SubcomposeAsyncImage(
                model = badge.imageUrl,
                contentDescription = badge.description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(100.dp),
                error = {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(60.dp),
                    )
                },
            )
        },
    )
}
