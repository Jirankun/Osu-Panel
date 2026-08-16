/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Non-lazy grid — counterpart of the Flutter `GridView.builder(shrinkWrap: true)`.
 *
 * Safe to use INSIDE a scroll container (LazyColumn / verticalScroll):
 * LazyVerticalGrid needs a bounded height and CRASHES when measured
 * with an unbounded max height (a common nesting issue). SimpleGrid
 * splits items into rows of [columns] without laziness — small item counts
 * (medals/badges) need no virtualization.
 */
@Composable
fun <T> SimpleGrid(
    items: List<T>,
    columns: Int,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 12.dp,
    content: @Composable (T) -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            ) {
                rowItems.forEach { item ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.weight(1f),
                    ) {
                        content(item)
                    }
                }
            }
        }
    }
}
