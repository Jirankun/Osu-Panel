/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.aokaze.osupanel.R

/**
 * Shared item detail dialog — used TOGETHER by medals (MedalImage) and
 * badges (BadgeImage) so the popup structure is never duplicated.
 *
 * Isi disesuaikan lewat parameter:
 *  - title: the item name (null for badges, which have no name)
 *  - description: the item description
 *  - dateText: achieved / awarded date (already formatted)
 *  - showLock: show the "Not achieved yet" label (medals only)
 *  - imageContent: the item image slot (medal: cached bitmap; badge: URL)
 */
@Composable
fun ItemDetailDialog(
    title: String?,
    description: String,
    dateText: String?,
    showLock: Boolean,
    imageContent: @Composable () -> Unit,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
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

                // One status chip that is ALWAYS present for medals — its
                // content differs: the obtained date when achieved, "Not
                // achieved yet" otherwise (same pill style as the badge popup).
                if (dateText != null || showLock) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (dateText != null) {
                                    colorScheme.primary.copy(alpha = 0.08f)
                                } else {
                                    colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        if (dateText != null) {
                            Text(
                                dateText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
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

/**
 * Date format "2024, January, 15" — exactly the Flutter `_formatDate`.
 * (achievedAt/awardedAt from the API: ISO-8601 "2024-01-15T12:00:00Z")
 */
fun formatAchievedDate(iso: String): String {
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    return runCatching {
        val date = java.time.OffsetDateTime.parse(iso)
        "${date.year}, ${months[date.monthValue - 1]}, ${date.dayOfMonth}"
    }.getOrElse { iso.take(10) }
}
