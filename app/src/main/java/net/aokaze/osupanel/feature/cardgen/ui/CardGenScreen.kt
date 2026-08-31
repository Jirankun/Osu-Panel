/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.cardgen.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.aokaze.osupanel.R
import net.aokaze.osupanel.data.model.UserDto
import net.aokaze.osupanel.feature.cardgen.CardGenViewModel
import net.aokaze.osupanel.feature.cardgen.CardShare
import net.aokaze.osupanel.feature.cardgen.TEMPLATE_MINI
import net.aokaze.osupanel.feature.cardgen.TEMPLATE_SKILLS
import net.aokaze.osupanel.feature.cardgen.TEMPLATE_STATS
import net.aokaze.osupanel.ui.components.OsuSpinner
import net.aokaze.osupanel.ui.components.trianglesLine
import net.aokaze.osupanel.widget.WidgetMode

/**
 * Card Generator — full-screen layer opened from the Profile FAB.
 *
 * Live preview of the currently open user (rendered by the widget's
 * [net.aokaze.osupanel.widget.SignatureRenderer]), game mode + template
 * dropdowns (same stat-sign generator types: Full stats / Skills / Mini),
 * and a "Complete" button that opens the SYSTEM share sheet — no storage
 * permission needed (PNG → cacheDir → FileProvider).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardGenScreen(
    userId: Int,
    user: UserDto,
    onClose: () -> Unit,
    viewModel: CardGenViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(userId, user.id) { viewModel.init(userId, user) }

    val isMini = state.template == TEMPLATE_MINI
    val ratio = if (isMini) 800f / 240f else 1100f / 640f

    Box(
        Modifier
            .fillMaxSize()
            .background(colorResource(R.color.cardgen_dark))
            // Consume ALL pointer events: this layer is a sibling of the
            // profile Scaffold, so without a pointer input node, drags pass
            // through and scroll the LazyColumn behind it ("bleed-through").
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        // Laser triangles across the whole layer — ABSOLUTE size mode
        // (fixedSizePx) so full-screen layers get the same small, dense
        // triangles as the contributor/license WebView pages (100 px wide,
        // count = W × 0.02 × spawnRatio) instead of one giant triangle.
        Box(
            Modifier
                .fillMaxSize()
                .trianglesLine(
                    alpha = 0.45f,
                    velocity = 1.5f,
                    spawnRatio = 2.5f,
                    strokeWidth = 0.4.dp,
                    fixedSizePx = 100f,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                // This layer is drawn edge-to-edge (outside the Scaffold's
                // insets), so pad the content below the status bar and above
                // the nav bar — otherwise the close (X) button hides under
                // the system header and the top bar "floats" too high.
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // ── Top bar ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cardgen_close),
                        tint = colorScheme.onSurface,
                    )
                }
                Text(
                    stringResource(R.string.cardgen_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Preview — fades when the dropdown changes (cool 😎) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colorResource(R.color.cardgen_dark_alt)),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = state.bitmap,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "cardPreview",
                ) { bmp ->
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = stringResource(R.string.cardgen_preview),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                        )
                    } else {
                        OsuSpinner(size = 36.dp)
                    }
                }
                if (state.isLoading && state.bitmap != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        OsuSpinner(size = 26.dp, withBox = false)
                    }
                }
            }

            state.error?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    message,
                    color = colorScheme.error,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Game mode ──
            CardGenLabel(stringResource(R.string.cardgen_mode))
            Spacer(Modifier.height(8.dp))
            CardGenDropdown(
                value = WidgetMode.displayName(state.mode),
                options = WidgetMode.ALL.map { WidgetMode.displayName(it) to it },
                selected = state.mode,
                onSelect = viewModel::setMode,
            )

            Spacer(Modifier.height(16.dp))

            // ── Template ──
            CardGenLabel(stringResource(R.string.cardgen_template))
            Spacer(Modifier.height(8.dp))
            CardGenDropdown(
                value = when (state.template) {
                    TEMPLATE_SKILLS -> stringResource(R.string.cardgen_template_skills)
                    TEMPLATE_MINI -> stringResource(R.string.cardgen_template_mini)
                    else -> stringResource(R.string.cardgen_template_stats)
                },
                options = listOf(
                    stringResource(R.string.cardgen_template_stats) to TEMPLATE_STATS,
                    stringResource(R.string.cardgen_template_skills) to TEMPLATE_SKILLS,
                    stringResource(R.string.cardgen_template_mini) to TEMPLATE_MINI,
                ),
                selected = state.template,
                onSelect = viewModel::setTemplate,
            )

            Spacer(Modifier.weight(1f))

            // ── Complete → system share sheet ──
            Surface(
                onClick = {
                    scope.launch {
                        val uri = viewModel.prepareShare()
                        uri?.let { CardShare.share(context, it) }
                    }
                },
                enabled = state.bitmap != null,
                shape = RoundedCornerShape(50),
                color = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .trianglesLine(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.cardgen_complete),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CardGenLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Mode/template dropdown — same ExposedDropdownMenu style as Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardGenDropdown(
    value: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (label, optionValue) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        if (optionValue == selected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        if (optionValue != selected) onSelect(optionValue)
                        expanded = false
                    },
                )
            }
        }
    }
}
