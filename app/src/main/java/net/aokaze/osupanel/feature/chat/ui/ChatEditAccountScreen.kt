/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.chat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import net.aokaze.osupanel.R
import net.aokaze.osupanel.core.theme.osuPink
import net.aokaze.osupanel.data.chatango.ChatangoAuthRepository
import net.aokaze.osupanel.data.local.ChatSettingsStore
import net.aokaze.osupanel.ui.components.BusyPill
import java.io.File
import java.io.FileOutputStream

/** Avatar URL like the Chatango app: ust.chatango.com/profileimg/x/y/user/thumb_m.jpg */
private fun chatAvatarUrl(user: String): String {
    val u = user.lowercase()
    val x = u.take(1)
    val y = u.drop(1).take(1).ifEmpty { x }
    return "https://ust.chatango.com/profileimg/$x/$y/$u/thumb_m.jpg"
}

/**
 * Edit Chatango account — full screen (opened from the Edit button in
 * Chat Settings): edit profile fields (bio/age/gender/location) + upload
 * profile photo. Same API as the Chatango EditProfileActivity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatEditAccountScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val loggedIn = ChatSettingsStore.isLoggedIn(context)
    val username = ChatSettingsStore.getUsername(context).orEmpty()

    // Edit fields.
    var editBio by rememberSaveable { mutableStateOf("") }
    var editAge by rememberSaveable { mutableStateOf("") }
    var editGender by rememberSaveable { mutableStateOf("") }
    var editDir by rememberSaveable { mutableStateOf(true) }
    var editBusy by remember { mutableStateOf(false) }
    var editMessage by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }

    // Prefill About me / Age / Gender with the current profile on open — so it
    // is an actual *edit* screen, not an empty form (mod1.xml, no auth needed).
    LaunchedEffect(username) {
        if (!loggedIn || username.isBlank()) return@LaunchedEffect
        val profile = ChatangoAuthRepository.getUserProfile(username)
        if (profile != null) {
            if (editBio.isBlank()) editBio = profile.bio.orEmpty()
            if (editAge.isBlank()) editAge = profile.age?.toString().orEmpty()
            if (editGender.isBlank()) editGender = profile.gender.orEmpty()
        }
    }

    // Photo upload.
    var photoBusy by remember { mutableStateOf(false) }
    var photoMessage by remember { mutableStateOf<Pair<Boolean, Int>?>(null) }
    var photoVersion by remember { mutableStateOf(0L) } // busts the avatar cache after upload

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                photoBusy = true
                photoMessage = null
                val token = ChatSettingsStore.getToken(context)
                val ok = token != null && runCatching {
                    val file = copyToCache(context, uri)
                    ChatangoAuthRepository.uploadProfilePicture(context, token, file)
                }.getOrDefault(false)
                photoMessage = if (ok) {
                    photoVersion = System.currentTimeMillis()
                    true to R.string.chat_edit_photo_saved
                } else {
                    false to R.string.chat_edit_photo_failed
                }
                photoBusy = false
            }
        }
    }

    // Preview — the same profile popup as the Global list, for this user.
    var previewUser by remember { mutableStateOf<String?>(null) }
    var previewBio by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.chat_edit_section), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.beatmap_back))
                    }
                },
                actions = {
                    // "Preview" — pink, top-right of the header. Opens the same
                    // profile popup used in the Global list, with this account.
                    if (loggedIn) {
                        Text(
                            stringResource(R.string.chat_edit_preview),
                            color = osuPink(context),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { previewUser = username }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
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
            if (!loggedIn) {
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.chat_edit_not_logged),
                    color = colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                return@Column
            }

            Spacer(Modifier.height(12.dp))

            // ── Profile photo ──
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    // Avatar + username on their own row — the photo button lives
                    // BELOW so no wrap-content pill can ever distort the card.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = chatAvatarUrl(username) + (if (photoVersion > 0) "?v=$photoVersion" else ""),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(colorScheme.surfaceVariant),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(username, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Chatango",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Signature loading: spinner kiri + "Uploading…", lalu
                    // hasil sukses tampil di tombol dan hilang fade.
                    BusyPill(
                        idleLabel = stringResource(R.string.chat_edit_change_photo),
                        busyLabel = stringResource(R.string.chat_edit_uploading),
                        busy = photoBusy,
                        result = photoMessage?.first,
                        successLabel = stringResource(R.string.chat_edit_photo_saved),
                        failureLabel = stringResource(R.string.chat_edit_photo_failed),
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        fillWidth = false,
                        height = 40.dp,
                        fontSize = 13.sp,
                        leadingIcon = Icons.Rounded.PhotoCamera,
                    )
                }
            }

            // ── Profile fields ──
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    // About me — grows downward to fit the whole bio (like a
                    // text editor); no maxLines cap so nothing gets cut off.
                    OutlinedTextField(
                        value = editBio,
                        onValueChange = { editBio = it },
                        label = { Text(stringResource(R.string.chat_edit_bio)) },
                        minLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editAge,
                        onValueChange = { editAge = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text(stringResource(R.string.chat_edit_age)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    GenderDropdown(
                        selected = editGender,
                        onSelect = { editGender = it },
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.chat_edit_directory), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.chat_edit_directory_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.material3.Switch(
                            checked = editDir,
                            onCheckedChange = { editDir = it },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // Signature loading: spinner kiri + "Saving…", lalu hasil
                    // sukses tampil di tombol dan hilang fade.
                    BusyPill(
                        idleLabel = stringResource(R.string.chat_edit_save),
                        busyLabel = stringResource(R.string.chat_edit_saving),
                        busy = editBusy,
                        result = editMessage?.first,
                        successLabel = stringResource(R.string.chat_edit_saved),
                        failureLabel = stringResource(R.string.chat_edit_failed),
                        onClick = {
                            editBusy = true
                            editMessage = null
                            scope.launch {
                                val token = ChatSettingsStore.getToken(context)
                                val ok = token != null && ChatangoAuthRepository.updateProfile(
                                    context,
                                    token,
                                    buildMap {
                                        if (editBio.isNotBlank()) put("line", editBio)
                                        if (editAge.isNotBlank()) put("age", editAge)
                                        if (editGender.isNotBlank()) put("gender", editGender)
                                        if (editDir) put("dir", "checked")
                                    },
                                )
                                editMessage = if (ok) {
                                    true to R.string.chat_edit_saved
                                } else {
                                    false to R.string.chat_edit_failed
                                }
                                editBusy = false
                            }
                        },
                    )
                }
            }
        }
    }

    // Profile preview popup — same component as the Global list (avatar tap),
    // with the action buttons disabled (it's your own profile).
    previewUser?.let { user ->
        UserProfileDialog(
            user = user,
            isFriend = false,
            onToggleFriend = {},
            onDismiss = { previewUser = null },
            onOpenPmChat = { previewUser = null },
            onShowBio = { previewBio = true },
            // Own account → gray out chat/friend and hide the block icon
            // (self = true; onBlock stays null).
            self = true,
        )
    }
    if (previewBio && previewUser != null) {
        BioDialog(user = previewUser!!, onDismiss = { previewBio = false })
    }
}

/** Copy a picked content Uri into the cache dir as a jpg file. */
private fun copyToCache(context: android.content.Context, uri: Uri): File {
    val file = File(context.cacheDir, "chatango_profile_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(file).use { output -> input.copyTo(output) }
    }
    return file
}

/** Gender dropdown — values used by the Chatango updateprofile API (M/F). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GenderDropdown(
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "" to "",
        "M" to stringResource(R.string.chat_edit_gender_male),
        "F" to stringResource(R.string.chat_edit_gender_female),
    )
    val label = options.firstOrNull { it.first == selected }?.second
        ?: stringResource(R.string.chat_edit_gender_other)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.chat_edit_gender)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.drop(1).forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
