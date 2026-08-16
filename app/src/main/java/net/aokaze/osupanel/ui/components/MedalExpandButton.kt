/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.aokaze.osupanel.R

/**
 * Expand/collapse button for a medal grid — shared by the Dashboard and the
 * Profile detail screen so the "Show all (n) / Show less" control is a single
 * implementation. [modifier] carries each screen's own padding.
 */
@Composable
fun MedalExpandButton(
    expanded: Boolean,
    totalCount: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(
            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            if (expanded) {
                stringResource(R.string.profile_show_less)
            } else {
                stringResource(R.string.profile_show_all, totalCount)
            },
        )
    }
}
