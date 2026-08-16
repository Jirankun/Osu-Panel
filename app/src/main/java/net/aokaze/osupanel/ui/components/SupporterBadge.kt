/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.aokaze.osupanel.R

/**
 * osu! supporter badge — the real supporter pill (pink capsule with 1–3 white
 * hearts, from the stat-sign assets, converted to VectorDrawables in
 * res/drawable). [supportLevel] picks the icon (clamped to 1..3). Shown on
 * the Dashboard & Profile instead of the plain heart icon.
 */
@Composable
fun SupporterBadge(
    supportLevel: Int,
    height: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    val drawableId = when (supportLevel.coerceIn(1, 3)) {
        1 -> R.drawable.ic_supporter_1
        2 -> R.drawable.ic_supporter_2
        else -> R.drawable.ic_supporter_3
    }
    Image(
        painter = painterResource(drawableId),
        contentDescription = stringResource(R.string.dashboard_supporter),
        modifier = modifier.height(height),
        contentScale = ContentScale.Fit,
    )
}
