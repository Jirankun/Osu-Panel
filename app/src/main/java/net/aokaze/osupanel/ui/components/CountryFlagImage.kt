/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Country flag from native drawable resources (`flag_{code}.png` in
 * res/drawable), 1.5:1 ratio — counterpart of the Flutter `CountryFlagWidget`.
 * Fallback: a flag emoji (when the resource is missing).
 */
@Composable
fun CountryFlagImage(
    countryCode: String,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val code = countryCode.uppercase()
    val resId = context.resources.getIdentifier(
        "flag_${code.lowercase()}", "drawable", context.packageName
    )

    val bitmap by produceState<ImageBitmap?>(null, code) {
        value = if (resId == 0) null else withContext(Dispatchers.IO) {
            runCatching {
                context.resources.openRawResource(resId)
                    .use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }

    val flag = bitmap
    if (flag != null) {
        Image(
            bitmap = flag,
            contentDescription = code,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(width = size * 1.5f, height = size)
                .clip(RoundedCornerShape(2.dp)),
        )
    } else {
        // Emoji fallback — clean, no broken images.
        Box(
            modifier = modifier
                .size(width = size * 1.5f, height = size)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Flag,
                contentDescription = code,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}
