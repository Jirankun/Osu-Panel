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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import net.aokaze.osupanel.core.util.formatAchievedDate
import net.aokaze.osupanel.data.medal.MedalAssets
import net.aokaze.osupanel.data.medal.MedalService

/**
 * osu! medal image — shown IMMEDIATELY from the global [MedalAssets] cache
 * (all PNGs preloaded at app start), so no loading flash, no scroll-time
 * decode, and NO fallback icon.
 *
 * Medals NOT yet achieved → grey (grayscale) + dimmed (alpha 0.5).
 *
 * PURE display, no click handling: the parent tile decides the tap target so
 * the WHOLE card opens the detail popup (not just the photo). Callers render
 * [MedalDetailDialog] themselves when the card is tapped. The [modifier]
 * controls the container (grid cards pass `fillMaxSize`); the medal itself is
 * always [size] and centered.
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
        medalLocalInfo(slug, achievementId, name, grouping, description)
    }
    val assetPath = local.assetPath
    val displayName = local.displayName

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

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
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

/** Medal detail dialog — delegates to the shared [ItemDetailDialog]. */
@Composable
fun MedalDetailDialog(
    name: String,
    grouping: String,
    slug: String?,
    description: String,
    achievementId: Int?,
    achievedAt: String?,
    achieved: Boolean,
    onDismiss: () -> Unit,
) {
    val local = remember(slug, achievementId) {
        medalLocalInfo(slug, achievementId, name, grouping, description)
    }
    val context = LocalContext.current

    // The medal PNG is already in the global cache (preloaded at app start) —
    // read it with the same peek-first strategy as the tile, so the popup
    // image appears instantly with zero flicker.
    val bitmap by produceState<ImageBitmap?>(
        initialValue = local.assetPath?.let { MedalAssets.peek(it) },
        context,
        local.assetPath,
    ) {
        if (local.assetPath == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) { MedalAssets.get(context, local.assetPath) }
    }
    // Local copy — the delegated property cannot be smart-cast.
    val bmp = bitmap

    ItemDetailDialog(
        title = local.displayName,
        description = local.displayDesc,
        dateText = achievedAt?.let { formatAchievedDate(it) },
        achieved = achieved,
        onDismiss = onDismiss,
        imageContent = {
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = local.displayName,
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

/** Resolved local medal data used by both the tile and the detail popup. */
private data class MedalLocalInfo(
    val assetPath: String?,
    val displayName: String,
    val displayDesc: String,
)

/** Single source of truth for the local medal lookup (slug → achievement_id). */
private fun medalLocalInfo(
    slug: String?,
    achievementId: Int?,
    name: String,
    grouping: String,
    description: String,
): MedalLocalInfo {
    val local = run {
        val service = MedalService
        if (!service.isReady) return@run null
        if (!slug.isNullOrEmpty()) {
            service.bySlug(slug) ?: service.byAchievementId(achievementId ?: -1)
        } else {
            service.byAchievementId(achievementId ?: -1)
        }
    }
    return MedalLocalInfo(
        assetPath = local?.localAssetPath
            ?: if (!slug.isNullOrEmpty()) "medals/$grouping/$slug.png" else null,
        displayName = local?.displayName?.takeIf { it.isNotEmpty() } ?: name,
        displayDesc = local?.description?.takeIf { it.isNotEmpty() } ?: description,
    )
}
