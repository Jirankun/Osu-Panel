/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.aokaze.osupanel.data.medal.MedalAssets
import net.aokaze.osupanel.data.medal.MedalService

/**
 * osu! medal image — shown IMMEDIATELY from the global [MedalAssets] cache
 * (all PNGs preloaded at app start), so no loading flash,
 * no scroll-time decode, and NO fallback icon.
 *
 * Medals NOT yet achieved → grey (grayscale) + dimmed (alpha 0.5).
 * Tap → the shared [ItemDetailDialog] (name, description,
 * achieved date / not-achieved label).
 */
@Composable
fun MedalImage(
    name: String,
    grouping: String,
    slug: String?,
    description: String,
    achievementId: Int?,
    achievedAt: String?,
    achieved: Boolean,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    // Look up local data — slug first, then achievement_id.
    val local = remember(slug, achievementId) {
        val service = MedalService
        if (!service.isReady) return@remember null
        if (!slug.isNullOrEmpty()) {
            service.bySlug(slug) ?: service.byAchievementId(achievementId ?: -1)
        } else {
            service.byAchievementId(achievementId ?: -1)
        }
    }
    val assetPath = local?.localAssetPath
        ?: if (!slug.isNullOrEmpty()) "medals/$grouping/$slug.png" else null
    val displayName = local?.displayName?.takeIf { it.isNotEmpty() } ?: name
    val displayDesc = local?.description?.takeIf { it.isNotEmpty() } ?: description

    var showDetail by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // First frame reads the cache ONLY (peek — sync, no IO, no decode)
    // → the tile appears instantly with the real image, NO "loading" effect.
    // If not preloaded yet (rare), decode the fallback in the background.
    val bitmap by produceState<ImageBitmap?>(
        initialValue = assetPath?.let { MedalAssets.peek(it) },
        context,
        assetPath,
    ) {
        if (assetPath == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) { MedalAssets.get(context, assetPath) }
    }

    // Copy to a local so smart-cast works (bitmap is a delegated property).
    val bmp = bitmap

    Surface(
        onClick = { showDetail = true },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(12.dp)),
                    colorFilter = if (!achieved) {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    } else {
                        null
                    },
                    alpha = if (achieved) 1f else 0.5f,
                )
            }
            // No fallback icon — every medal has its own real image.
        }
    }

    if (showDetail) {
        MedalDetailDialog(
            name = displayName,
            description = displayDesc,
            achievedAt = achievedAt,
            achieved = achieved,
            bitmap = bmp,
            onDismiss = { showDetail = false },
        )
    }
}

/** Medal detail dialog — delegates to the shared [ItemDetailDialog]. */
@Composable
private fun MedalDetailDialog(
    name: String,
    description: String,
    achievedAt: String?,
    achieved: Boolean,
    bitmap: ImageBitmap?,
    onDismiss: () -> Unit,
) {
    ItemDetailDialog(
        title = name,
        description = description,
        dateText = achievedAt?.let { formatAchievedDate(it) },
        showLock = !achieved,
        onDismiss = onDismiss,
        imageContent = {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(100.dp),
                    colorFilter = if (!achieved) {
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                    } else null,
                    alpha = if (achieved) 1f else 0.5f,
                )
            }
        },
    )
}
