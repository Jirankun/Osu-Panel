/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import net.aokaze.osupanel.R

/**
 * "no background map" placeholder painter from res/drawable
 * (`no_background_maps.png`) — used as the `error` slot in AsyncImage     * (Flutter `AssetPaths.noBackgroundMap` counterpart). The PNG moved from
     * assets to drawable to keep the assets folder slim.
 */
@Composable
fun rememberMapPlaceholderPainter(size: Dp = 56.dp): Painter =
    painterResource(id = R.drawable.no_background_maps)

/**
 * Map cover with a "no image" fallback from drawable.
 *
 * When [url] is null/empty (map without a cover), render the image
 * `no_background_maps.png` directly — plain AsyncImage does NOT fire the `error`
 * slot for a null model, so without this a coverless map would look blank.
 * When [url] exists, use AsyncImage with the same placeholder as the
 * fallback if the load fails.
 */
@Composable
fun MapCoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val placeholder = rememberMapPlaceholderPainter()
    if (url.isNullOrBlank()) {
        Image(
            painter = placeholder,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
            error = placeholder,
        )
    }
}

/** "no avatars" placeholder painter from drawable (`no_avatars.png`). */
@Composable
fun rememberAvatarPlaceholderPainter(): Painter =
    painterResource(id = R.drawable.no_avatars)
