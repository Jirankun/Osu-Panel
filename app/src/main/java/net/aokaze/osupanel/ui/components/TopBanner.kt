/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Top banner type: error (red) vs info (normal). */
enum class BannerType { Error, Info }

/** Banner message — keep it in the caller's state, then render [TopBanner]. */
data class BannerMessage(
    val text: String,
    val type: BannerType = BannerType.Error,
)

/**
 * Centered top banner — drops from the top (slide + fade), shows for 3 seconds,
 * then rises again. ONE component for all notifications (replacing
 * the snackbar): error & info differ only in color + icon + text.
 */
@Composable
fun TopBanner(
    message: String,
    onDismiss: () -> Unit,
    type: BannerType = BannerType.Error,
    modifier: Modifier = Modifier,
    /**
     * false = banner STAYS visible until tapped (for important messages like
     * "Goodbye" that must be seen above every layer). true = auto-dismisses
     * after 3 seconds (legacy behavior).
     */
    autoDismiss: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val container = when (type) {
        BannerType.Error -> colorScheme.errorContainer
        BannerType.Info -> colorScheme.secondaryContainer
    }
    val content = when (type) {
        BannerType.Error -> colorScheme.onErrorContainer
        BannerType.Info -> colorScheme.onSecondaryContainer
    }
    val icon: ImageVector = when (type) {
        BannerType.Error -> Icons.Filled.Warning
        BannerType.Info -> Icons.Filled.Info
    }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(message, autoDismiss) {
        visible = true
        if (autoDismiss) {
            delay(3000)
            visible = false
            delay(400) // wait for the exit animation to finish
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300),
        ) + fadeIn(tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300),
        ) + fadeOut(tween(300)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 16.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(50))
                .background(container)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                // Tap anywhere on the banner → close immediately (for
                // non-auto-dismiss banners this is the only way to hide it).
                .clickable {
                    visible = false
                    onDismiss()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                message,
                color = content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
