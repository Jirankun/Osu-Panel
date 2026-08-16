/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3-style bottom navigation (counterpart of the Flutter `AnimatedBottomNav`):
 *
 * - Fixed 78dp height, thin border on top (outlineVariant 30%).
 * - A **pill** indicator (rounded rect behind the icon) appears with
 *   scale ease-out-back.
 * - **Laser triangles** appear behind the ACTIVE item with a **fade-in**
 *   when selected (fade-out when inactive).
 * - The icon **rises 3px** when active (translate, not padding — no
 *   menggeser tataletak).
 * - The label only shows for the active item — rises from the bottom
 *   (slide 12px → 0, translate) sambil fade in.
 */
@Composable
fun AnimatedBottomNav(
    currentIndex: Int,
    onTap: (Int) -> Unit,
    items: List<BottomNavItem>,
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(color = colorScheme.surface) {
        Box(
            Modifier
                .fillMaxWidth()
                // Roomy height (78dp) so the label under the pill does not
                // get squeezed against the bottom screen frame.
                .height(78.dp)
                .border(
                    BorderStroke(
                        width = 1.dp,
                        color = colorScheme.outlineVariant.copy(alpha = 0.3f),
                    ),
                )
                .background(colorScheme.surface),
        ) {
            Row(Modifier.fillMaxSize()) {
                items.forEachIndexed { idx, item ->
                    NavItem(
                        item = item,
                        isSelected = idx == currentIndex,
                        onClick = { onTap(idx) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Data for one navigation item. */
data class BottomNavItem(
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String,
)

@Composable
private fun NavItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val iconColor = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant

    // Animation progress — 1 = active, 0 = not (AnimationController 300ms counterpart).
    val anim = remember { Animatable(if (isSelected) 1f else 0f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            anim.animateTo(1f, tween(300, easing = EaseOutCubic))
        } else {
            anim.animateTo(0f, tween(300, easing = EaseOutCubic))
        }
    }
    val t = anim.value

    // Pill scale — ease-out-back (slight overshoot), Flutter counterpart.
    val pillScale = remember { Animatable(if (isSelected) 1f else 0f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            pillScale.animateTo(1f, tween(300, easing = EaseOutBack))
        } else {
            pillScale.animateTo(0f, tween(300, easing = EaseOutCubic))
        }
    }

    // Label opacity — 0.15–1.0 interval (appears after the pill).
    val labelAlpha = if (t <= 0.15f) 0f else (t - 0.15f) / 0.85f

    Column(
        // Inactive items are shifted down slightly (offset 0), then RISE
        // when active (offset −4dp + icon rises 3dp) — the "slide up"
        // effect seen when navigating.
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxHeight()
            .offset(y = ((1f - t) * 4f - 4f).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        // ── Ikon area (42dp) — pill + triangles DI DALAM pill + ikon ──
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Pill + triangles (72dp × 40dp). IMPORTANT: triangles do NOT follow
            // `scale(pillScale)` — if applied, triangles shrink while the pill
            // is still small/overshooting (narrow frame) → looks broken. The pill
            // grows on its own; triangles fade in at full size.
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(40.dp),
            ) {
                // Bottom layer: pill background (scaled, grows as it appears).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(pillScale.value)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) {
                                colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        ),
                )
                // Top layer: triangles — FIXED size (does not shrink),
                // fade-in/out follows activation, drifts up slowly.
                // IMPORTANT: ALWAYS composed (only alpha changes) — if wrapped
                // in `if (isSelected)` this composable leaves/re-enters the
                // composition on every tab switch → rememberInfiniteTransition
                // restarts from 0 → the triangle animation replays from the
                // start on each nav change. Kept always composed, the
                // animation time keeps running and switching tabs only fades.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .alpha(t)
                        .trianglesBackground(
                            scaleAdjust = 0.35f,
                            velocity = 0.6f,
                            spawnRatio = 3.5f,
                            alpha = 0.6f,
                        ),
                )
            }
            // Icon rises 3px when active — translate (offset), no layout shift.
            Icon(
                imageVector = if (isSelected) item.selectedIcon else item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .offset(y = (-t * 3f).dp),
            )
        }
        // ── Label under the pill (18dp) — fully CENTERED under the pill ──
        // (fillMaxWidth + Center textAlign so it does not shift left.
        // The 18dp box is taller than the 11sp text line (~15dp) so glyphs
        // never spill to the nav's bottom edge.)
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                item.label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = iconColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = ((1f - t) * 12f).dp)
                    .alpha(labelAlpha),
            )
        }
    }
}

private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1.0f)
private val EaseOutBack = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)
