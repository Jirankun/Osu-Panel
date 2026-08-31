/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import net.aokaze.osupanel.R
import net.aokaze.osupanel.data.chatango.ChatangoAuthRepository
import net.aokaze.osupanel.data.local.ChatSettingsStore
import net.aokaze.osupanel.ui.components.BusyPill
import net.aokaze.osupanel.ui.components.ConfirmDialog
import net.aokaze.osupanel.ui.components.SectionLabel
import net.aokaze.osupanel.ui.components.SettingsCard
import net.aokaze.osupanel.ui.components.SubHeader
import net.aokaze.osupanel.ui.components.ToggleRow

/**
 * Chat Settings — dedicated full screen opened from the Chat row in Settings.
 *
 * Sections (features taken from the Chatango APK — see ANALISIS_CHATANGO.md):
 *   Account   → Chatango login (settokenapp) + logout
 *   Edit      → update Chatango profile fields (updateprofile)
 *   Activation→ enable PM / group chat + group names
 *   Notifications → the 8 Chatango notification toggles
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen(
    onBack: () -> Unit,
    onOpenEditAccount: () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // ── State ──
    var loggedIn by remember { mutableStateOf(ChatSettingsStore.isLoggedIn(context)) }
    var username by remember { mutableStateOf(ChatSettingsStore.getUsername(context) ?: "") }
    var loginUser by rememberSaveable { mutableStateOf("") }
    var loginPass by rememberSaveable { mutableStateOf("") }
    var loginBusy by remember { mutableStateOf(false) }
    var loginErrorRes by remember { mutableStateOf<Int?>(null) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    var pmEnabled by remember { mutableStateOf(ChatSettingsStore.isPmEnabled(context)) }
    var groupEnabled by remember { mutableStateOf(ChatSettingsStore.isGroupEnabled(context)) }

    // Notification toggles.
    var grVisibleSound by remember { mutableStateOf(ChatSettingsStore.isGrVisibleSound(context)) }
    var pmVisibleSound by remember { mutableStateOf(ChatSettingsStore.isPmVisibleSound(context)) }
    var pmAlertEnabled by remember { mutableStateOf(ChatSettingsStore.isPmAlertEnabled(context)) }
    var pmAlertSound by remember { mutableStateOf(ChatSettingsStore.isPmAlertSound(context)) }
    var pmAlertVibrate by remember { mutableStateOf(ChatSettingsStore.isPmAlertVibrate(context)) }
    var atAlertEnabled by remember { mutableStateOf(ChatSettingsStore.isAtAlertEnabled(context)) }
    var atAlertSound by remember { mutableStateOf(ChatSettingsStore.isAtAlertSound(context)) }
    var atAlertVibrate by remember { mutableStateOf(ChatSettingsStore.isAtAlertVibrate(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.chat_settings_title), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.beatmap_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Account ──
            SectionLabel(stringResource(R.string.chat_account_section))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                if (!loggedIn) {
                    OutlinedTextField(
                        value = loginUser,
                        onValueChange = { loginUser = it },
                        label = { Text(stringResource(R.string.chat_login_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = loginPass,
                        onValueChange = { loginPass = it },
                        label = { Text(stringResource(R.string.chat_login_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(14.dp))
                    // Signature loading: spinner kiri + "Logging in…".
                    BusyPill(
                        idleLabel = stringResource(R.string.chat_login_button),
                        busyLabel = stringResource(R.string.chat_login_loading),
                        busy = loginBusy,
                        result = null,
                        successLabel = "",
                        failureLabel = "",
                        enabled = loginUser.isNotBlank() && loginPass.isNotBlank(),
                        onClick = {
                            loginBusy = true
                            loginErrorRes = null
                            scope.launch {
                                val user = loginUser.trim()
                                val result = ChatangoAuthRepository.login(context, user, loginPass)
                                when (result) {
                                    is ChatangoAuthRepository.LoginResult.Success -> {
                                        ChatSettingsStore.saveCredentials(context, user, result.token, loginPass)
                                        username = user
                                        loginUser = ""
                                        loginPass = ""
                                        loggedIn = true
                                    }
                                    is ChatangoAuthRepository.LoginResult.Failure -> {
                                        loginErrorRes = when (result.error) {
                                            ChatangoAuthRepository.LoginError.PASSWORD -> R.string.chat_login_error_pwd
                                            ChatangoAuthRepository.LoginError.USERNAME -> R.string.chat_login_error_sid
                                            ChatangoAuthRepository.LoginError.VERSION -> R.string.chat_login_error_version
                                            ChatangoAuthRepository.LoginError.GENERIC -> R.string.chat_login_error
                                        }
                                    }
                                }
                                loginBusy = false
                            }
                        },
                    )
                    loginErrorRes?.let { res ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(res),
                            color = colorScheme.error,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The user's real profile photo (thumb_m.jpg), same as
                        // the Chatango app — replaces the generic chat icon.
                        AsyncImage(
                            model = ChatangoAuthRepository.thumbUrl(username),
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorScheme.surfaceVariant),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_chat_connected_as, username),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Chatango",
                                color = colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        // Edit account (left) — same BusyPill as Change photo
                        // (identical triangles + pill), just full width.
                        BusyPill(
                            idleLabel = stringResource(R.string.chat_edit_section),
                            busyLabel = "",
                            busy = false,
                            result = null,
                            successLabel = "",
                            failureLabel = "",
                            onClick = onOpenEditAccount,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        // Logout (right) — red lazer pill, same component.
                        BusyPill(
                            idleLabel = stringResource(R.string.chat_logout_button),
                            busyLabel = "",
                            busy = false,
                            result = null,
                            successLabel = "",
                            failureLabel = "",
                            onClick = { showLogoutConfirm = true },
                            modifier = Modifier.weight(1f),
                            color = colorScheme.error,
                            contentColor = colorScheme.onError,
                        )
                    }
                }
            }

            // ── Activation ──
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.chat_activate_section))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                ToggleRow(
                    title = stringResource(R.string.chat_activate_pm),
                    subtitle = stringResource(R.string.chat_activate_pm_desc),
                    checked = pmEnabled,
                    onCheckedChange = { enabled ->
                        pmEnabled = enabled
                        ChatSettingsStore.setPmEnabled(context, enabled)
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                ToggleRow(
                    title = stringResource(R.string.chat_activate_group),
                    subtitle = stringResource(R.string.chat_activate_group_desc),
                    checked = groupEnabled,
                    onCheckedChange = { enabled ->
                        groupEnabled = enabled
                        ChatSettingsStore.setGroupEnabled(context, enabled)
                    },
                )
            }

            // ── Notifications ──
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.chat_notif_section))
            Spacer(Modifier.height(8.dp))
            SettingsCard {
                SubHeader(stringResource(R.string.chat_notif_pm_section))
                ToggleRow(
                    title = stringResource(R.string.chat_notif_sound_visible),
                    subtitle = stringResource(R.string.chat_notif_sound_visible_desc),
                    checked = pmVisibleSound,
                    onCheckedChange = {
                        pmVisibleSound = it
                        ChatSettingsStore.setPmVisibleSound(context, it)
                    },
                )
                ToggleRow(
                    title = stringResource(R.string.chat_notif_alert_closed),
                    subtitle = stringResource(R.string.chat_notif_alert_closed_desc),
                    checked = pmAlertEnabled,
                    onCheckedChange = {
                        pmAlertEnabled = it
                        ChatSettingsStore.setPmAlertEnabled(context, it)
                    },
                )
                ToggleRow(
                    title = stringResource(R.string.chat_notif_sound),
                    checked = pmAlertSound,
                    onCheckedChange = {
                        pmAlertSound = it
                        ChatSettingsStore.setPmAlertSound(context, it)
                    },
                )
                ToggleRow(
                    title = stringResource(R.string.chat_notif_vibrate),
                    checked = pmAlertVibrate,
                    onCheckedChange = {
                        pmAlertVibrate = it
                        ChatSettingsStore.setPmAlertVibrate(context, it)
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SubHeader(stringResource(R.string.chat_notif_group_section))
                ToggleRow(
                    title = stringResource(R.string.chat_notif_sound_visible),
                    subtitle = stringResource(R.string.chat_notif_sound_visible_desc),
                    checked = grVisibleSound,
                    onCheckedChange = {
                        grVisibleSound = it
                        ChatSettingsStore.setGrVisibleSound(context, it)
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SubHeader(stringResource(R.string.chat_notif_at_section))
                ToggleRow(
                    title = stringResource(R.string.chat_notif_alert_closed),
                    subtitle = stringResource(R.string.chat_notif_alert_closed_desc),
                    checked = atAlertEnabled,
                    onCheckedChange = {
                        atAlertEnabled = it
                        ChatSettingsStore.setAtAlertEnabled(context, it)
                    },
                )
                ToggleRow(
                    title = stringResource(R.string.chat_notif_sound),
                    checked = atAlertSound,
                    onCheckedChange = {
                        atAlertSound = it
                        ChatSettingsStore.setAtAlertSound(context, it)
                    },
                )
                ToggleRow(
                    title = stringResource(R.string.chat_notif_vibrate),
                    checked = atAlertVibrate,
                    onCheckedChange = {
                        atAlertVibrate = it
                        ChatSettingsStore.setAtAlertVibrate(context, it)
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Logout confirmation (same dialog as the osu account logout) ──
    if (showLogoutConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.chat_logout_title),
            text = stringResource(R.string.chat_logout_confirm),
            confirmLabel = stringResource(R.string.chat_logout_button),
            dismissLabel = stringResource(R.string.chat_cancel),
            onConfirm = {
                showLogoutConfirm = false
                ChatSettingsStore.logout(context)
                loggedIn = false
                username = ""
                pmEnabled = false
                groupEnabled = false
            },
            onDismiss = { showLogoutConfirm = false },
        )
    }
}

