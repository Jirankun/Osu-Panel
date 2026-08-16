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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import net.aokaze.osupanel.data.model.BadgeDto

/**
 * osu! badge image (from a network URL via Coil). Tap → the shared
 * ItemDetailDialog — the same popup as medals, just different content:
 * badge image + description + awarded date.
 */
@Composable
fun BadgeImage(
    badge: BadgeDto,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    var showDetail by remember { mutableStateOf(false) }

    Surface(
        onClick = { showDetail = true },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
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

    if (showDetail) {
        BadgeDetailDialog(badge = badge, onDismiss = { showDetail = false })
    }
}

/** Badge detail dialog — image + description + awarded date. */
@Composable
private fun BadgeDetailDialog(
    badge: BadgeDto,
    onDismiss: () -> Unit,
) {
    ItemDetailDialog(
        title = null,
        description = badge.description,
        dateText = badge.awardedAt
            ?.takeIf { it.isNotEmpty() }
            ?.let { formatAchievedDate(it) },
        showLock = false,
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
