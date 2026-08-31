/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.util.formatAchievedDate

/**
 * Shared item detail dialog — used TOGETHER by medals (MedalImage) and
 * badges (BadgeImage) so the popup structure is never duplicated.
 *
 * Isi disesuaikan lewat parameter:
 *  - title: the item name (null for badges, which have no name)
 *  - description: the item description
 *  - dateText: achieved / awarded date (already formatted)
 *  - achieved: true when the medal is earned / badge awarded
 *  - imageContent: the item image slot (medal: cached bitmap; badge: URL)
 */
@Composable
fun ItemDetailDialog(
    title: String?,
    description: String,
    dateText: String?,
    achieved: Boolean,
    imageContent: @Composable () -> Unit,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        // Card entrance — the popup scales up from 90% with a fade (matches
        // the app's fast motion), so the medal/badge card "lands" on screen.
        var entered by remember { mutableStateOf(false) }
        val cardScale by animateFloatAsState(
            targetValue = if (entered) 1f else 0.9f,
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            label = "dialogScale",
        )
        val cardAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(260),
            label = "dialogAlpha",
        )
        LaunchedEffect(Unit) { entered = true }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    alpha = cardAlpha
                },
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Item icon — image from the slot (medal cache / badge URL).
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    imageContent()
                }
                Spacer(Modifier.height(20.dp))

                if (title != null) {
                    Text(
                        title,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (description.isNotEmpty()) {
                    Text(
                        description,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colorScheme.onSurfaceVariant,
                        ),
                    )
                } else {
                    Text(
                        stringResource(R.string.medal_no_description),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontStyle = FontStyle.Italic,
                        ),
                    )
                }

                // Status chip — ALWAYS present for medals: shows the obtained
                // date when achieved (falling back to "Achieved" if the API
                // omitted the date), otherwise the "Not achieved yet" lock
                // chip (same pill style as the badge popup, + laser triangles).
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (achieved) {
                                colorScheme.primary.copy(alpha = 0.08f)
                            } else {
                                colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                            }
                        )
                        .trianglesLine(alpha = 0.18f, scaleAdjust = 0.3f, spawnRatio = 2f)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    when {
                        !achieved -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                stringResource(R.string.medal_not_achieved),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }

                        dateText != null -> Text(
                            dateText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            ),
                        )

                        else -> Text(
                            stringResource(R.string.medal_achieved),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                // OK — laser triangles, same style as every other app button.
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .trianglesLine(alpha = 0.35f, scaleAdjust = 0.35f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.medal_okey),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
