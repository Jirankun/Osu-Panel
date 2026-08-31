/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.settings.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.aokaze.osupanel.BuildConfig
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.theme.osuPink
import net.aokaze.osupanel.data.local.ChatSettingsStore
import net.aokaze.osupanel.data.local.WidgetDataStore
import net.aokaze.osupanel.feature.auth.AuthViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.style.TextDecoration
import net.aokaze.osupanel.ui.components.ConfirmDialog
import net.aokaze.osupanel.ui.components.SectionLabel
import net.aokaze.osupanel.ui.components.trianglesLine
import net.aokaze.osupanel.widget.WidgetMode

/**
 * Settings — redesigned Material:
 * - **Widget** section: game mode + large-widget layout dropdowns.
 * - **About** section: Source Code (GitHub), License, Contributors —
 *   each an item with laser triangles.
 * - **Logout** button at the bottom (with confirmation dialog).
 * Every tappable element uses laser triangles (no exceptions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AuthViewModel,
    onOpenLicenses: () -> Unit,
    onOpenContributors: () -> Unit,
    onOpenChatSettings: () -> Unit,
    onOpenLanguagePicker: () -> Unit = {},
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    // Current widget mode — read directly from the widget prefs.
    var widgetMode by remember { mutableStateOf(WidgetDataStore.getWidgetMode(context)) }
    // Large widget layout (stats / skills) — read directly from the widget prefs.
    var widgetLayout by remember { mutableStateOf(WidgetDataStore.getWidgetLayout(context)) }

    var showLogoutConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.nav_settings), fontWeight = FontWeight.Bold)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 120.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Section: System ──
            SectionLabel(stringResource(R.string.settings_system_section))
            Spacer(Modifier.height(8.dp))

            AboutItem(
                icon = Icons.Rounded.Language,
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_language_desc),
                onClick = onOpenLanguagePicker,
                showTriangles = false,
            )

            Spacer(Modifier.height(24.dp))

            // ── Section: Widget ──
            SectionLabel(stringResource(R.string.settings_widget_section))
            Spacer(Modifier.height(8.dp))

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Widgets,
                            contentDescription = null,
                            tint = osuPink(context),
                            modifier = Modifier.width(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.settings_widget_mode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.settings_widget_mode_desc),
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = WidgetMode.displayName(widgetMode),
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            WidgetMode.ALL.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(WidgetMode.displayName(mode)) },
                                    leadingIcon = {
                                        if (mode == widgetMode) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = osuPink(context),
                                            )
                                        } else {
                                            ModeDotIcon()
                                        }
                                    },
                                    onClick = {
                                        if (mode != widgetMode) {
                                            widgetMode = mode
                                            viewModel.setWidgetMode(mode)
                                        }
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }

                    // ── Large widget layout: with stats / with skills ──
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Widgets,
                            contentDescription = null,
                            tint = osuPink(context),
                            modifier = Modifier.width(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                stringResource(R.string.settings_widget_layout),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.settings_widget_layout_desc),
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    var layoutExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = layoutExpanded,
                        onExpandedChange = { layoutExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = if (widgetLayout == "skills") {
                                stringResource(R.string.settings_layout_skills)
                            } else {
                                stringResource(R.string.settings_layout_stats)
                            },
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = layoutExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = layoutExpanded,
                            onDismissRequest = { layoutExpanded = false },
                        ) {
                            listOf("stats" to R.string.settings_layout_stats, "skills" to R.string.settings_layout_skills)
                                .forEach { (value, labelRes) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(labelRes)) },
                                        leadingIcon = {
                                            if (value == widgetLayout) {
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = osuPink(context),
                                                )
                                            } else {
                                                ModeDotIcon()
                                            }
                                        },
                                        onClick = {
                                            if (value != widgetLayout) {
                                                widgetLayout = value
                                                viewModel.setWidgetLayout(value)
                                            }
                                            layoutExpanded = false
                                        },
                                    )
                                }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Section: Chat (Chatango) ──
            SectionLabel(stringResource(R.string.settings_chat_section))
            Spacer(Modifier.height(8.dp))

            val chatUser = ChatSettingsStore.getUsername(context)
            val chatActive = ChatSettingsStore.isPmEnabled(context) || ChatSettingsStore.isGroupEnabled(context)
            AboutItem(
                icon = Icons.Rounded.ChatBubble,
                title = stringResource(R.string.settings_chat_title),
                subtitle = if (chatUser != null && chatActive) {
                    stringResource(R.string.settings_chat_connected_as, chatUser)
                } else {
                    stringResource(R.string.settings_chat_not_activated)
                },
                onClick = onOpenChatSettings,
                showTriangles = false,
            )

            Spacer(Modifier.height(24.dp))

            // ── Section: About ──
            SectionLabel(stringResource(R.string.settings_about_section))
            Spacer(Modifier.height(8.dp))

            // Source Code → GitHub repo (external browser).
            AboutItem(
                icon = Icons.Rounded.Code,
                title = stringResource(R.string.settings_source_code),
                subtitle = stringResource(R.string.settings_source_code_desc),
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Jirankun/Osu-Panel")),
                        )
                    }
                },
                showTriangles = false,
            )
            Spacer(Modifier.height(10.dp))

            AboutItem(
                icon = Icons.Rounded.Verified,
                title = stringResource(R.string.settings_license),
                subtitle = stringResource(R.string.settings_license_desc),
                onClick = onOpenLicenses,
                showTriangles = false,
            )
            Spacer(Modifier.height(10.dp))

            AboutItem(
                icon = Icons.Rounded.People,
                title = stringResource(R.string.settings_contributor),
                subtitle = stringResource(R.string.settings_contributor_desc),
                onClick = onOpenContributors,
                showTriangles = false,
            )
            Spacer(Modifier.height(10.dp))

            var showVersionDialog by remember { mutableStateOf(false) }
            AboutItem(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.settings_version, "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"),
                subtitle = stringResource(R.string.app_name),
                onClick = { showVersionDialog = true },
                showTriangles = false,
            )

            Spacer(Modifier.height(28.dp))

            // ── Version info dialog ──
            if (showVersionDialog) {
                VersionInfoDialog(
                    version = "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}",
                    onDismiss = { showVersionDialog = false },
                )
            }

            // ── Logout (with confirmation) — lazer pill, red ──
            LogoutButton(onClick = { showLogoutConfirm = true })
            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Logout confirmation dialog ──
    if (showLogoutConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.settings_logout_title),
            text = stringResource(R.string.settings_logout_confirm),
            confirmLabel = stringResource(R.string.settings_confirm),
            dismissLabel = stringResource(R.string.settings_cancel),
            onConfirm = {
                showLogoutConfirm = false
                viewModel.logout()
            },
            onDismiss = { showLogoutConfirm = false },
        )
    }
}

/**
 * One About list item — Material card-like pill with laser triangles.
 * [onClick] = null → non-interactive (e.g. the version row).
 */
@Composable
private fun AboutItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    showTriangles: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val tint = osuPink(context)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            ),
    ) {
        if (showTriangles) {
            // Laser triangles — subtle, fill the item.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .trianglesLine(
                        scaleAdjust = 0.35f,
                        velocity = 0.6f,
                        spawnRatio = 2.5f,
                        alpha = 0.5f,
                    ),
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant,
                )
            }
            if (onClick != null) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

/** Logout — full-width lazer pill in the error/red color, with triangles. */
@Composable
private fun LogoutButton(onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = colorScheme.errorContainer.copy(alpha = 0.7f),
        contentColor = colorScheme.onErrorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .trianglesLine(
                    scaleAdjust = 0.35f,
                    velocity = 0.6f,
                    spawnRatio = 3f,
                    alpha = 0.6f,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_logout_button),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Version info dialog — version string, Privacy Policy, Terms of Service. */
@Composable
private fun VersionInfoDialog(
    version: String,
    onDismiss: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = {
            Text(
                "Osu! Panel",
                fontWeight = FontWeight.Bold,
                color = osuPink(context),
            )
        },
        text = {
            Column {
                Text(
                    "Osu! Panel - $version",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Aokaze Studio ~ Jirankun",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_privacy_policy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = osuPink(context),
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://aokazestudio.zhyllanfyllah.my.id/secure/privacy_policy/"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .width(1.dp)
                            .height(16.dp)
                            .background(colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    )
                    Text(
                        stringResource(R.string.settings_terms_of_service),
                        style = MaterialTheme.typography.bodyMedium,
                        color = osuPink(context),
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://aokazestudio.zhyllanfyllah.my.id/secure/terms_%20service/"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

/** Small placeholder dot on unselected dropdown items (for alignment). */
@Composable
private fun ModeDotIcon() {
    Box(
        Modifier
            .width(20.dp)
            .height(20.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                shape = CircleShape,
            ),
    )
}
