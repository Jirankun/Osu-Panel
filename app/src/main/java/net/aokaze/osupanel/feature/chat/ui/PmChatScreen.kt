/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.chat.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import net.aokaze.osupanel.R
import net.aokaze.osupanel.data.chatango.ChatangoAuthRepository
import net.aokaze.osupanel.feature.chat.ChatMessage
import net.aokaze.osupanel.feature.chat.ChatViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PM chat — one conversation, full screen. Bubble UI + composer.
 * Messages come from the socket via ChatViewModel (in-memory for this session).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmChatScreen(
    user: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(LocalContext.current as ComponentActivity),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = state.conversations
        .firstOrNull { it.user.equals(user, ignoreCase = true) }
        ?.messages.orEmpty()
    val presence = state.presence[user.lowercase()]

    val listState = rememberLazyListState()
    var text by rememberSaveable { mutableStateOf("") }

    // ── Photo draft ──
    // Picking a photo does NOT send it: it becomes a draft shown in a small
    // preview above the composer. It only goes out when the user taps Send
    // (or the old draft is replaced / discarded).
    var photoDraft by remember { mutableStateOf<File?>(null) }
    var sendingPhoto by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null && !sendingPhoto) {
            // Replace any previous draft, then stage the new one as a file
            // (content URIs may expire — a cache copy survives until sent).
            photoDraft?.delete()
            photoDraft = copyToCache(context, uri)
        }
    }

    // Send the current text + photo draft together (upload → one image
    // message with an optional caption). On failure the draft survives so
    // the user can tap Send again to retry.
    fun sendDraftPhoto() {
        val file = photoDraft ?: return
        if (sendingPhoto) return
        sendingPhoto = true
        scope.launch {
            val ok = if (text.isBlank()) {
                viewModel.sendPhoto(user, file)
            } else {
                viewModel.sendTextAndPhoto(user, text, file)
            }
            sendingPhoto = false
            if (ok) {
                file.delete()
                photoDraft = null
                text = ""
            }
        }
    }

    // Leaving the chat discards an unsent draft (the cache file is deleted;
    // drafts only live while this screen is open).
    DisposableEffect(Unit) {
        onDispose {
            photoDraft?.delete()
        }
    }

    // Auto-scroll to the newest message when the conversation grows.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(user, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            statusLabel(presence),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                itemsIndexed(messages, key = { index, _ -> index }) { _, msg ->
                    MessageBubble(msg, mine = msg.isMine)
                }
            }

            // Photo draft preview — a small strip ABOVE the composer while a
            // photo is staged (not yet sent). Shows the image, a "draft"
            // affordance and an ✕ to discard it.
            photoDraft?.let { draft ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                1.5.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                RoundedCornerShape(14.dp),
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = draft,
                            contentDescription = stringResource(R.string.chat_photo_draft),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp)),
                        )
                        if (sendingPhoto) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.chat_photo_draft_hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!sendingPhoto) {
                        IconButton(
                            onClick = {
                                draft.delete()
                                photoDraft = null
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.chat_discard_draft),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // Composer — photo button on the LEFT of the (narrower, rounded)
            // text field, send on the right. Send fires text AND the staged
            // photo draft together.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !sendingPhoto,
                ) {
                    Icon(
                        Icons.Rounded.Image,
                        contentDescription = stringResource(R.string.chat_send_photo),
                        tint = if (sendingPhoto) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    // Narrower than before + fully rounded corners (same as the
                    // Global search box).
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (text.isNotBlank() && photoDraft == null) {
                            // Plain text only — send as a normal message.
                            viewModel.send(user, text)
                            text = ""
                        } else {
                            // Photo (with or without caption) — one combined message.
                            sendDraftPhoto()
                        }
                    },
                    enabled = (text.isNotBlank() || photoDraft != null) && !sendingPhoto,
                ) {
                    Icon(
                        Icons.Rounded.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        tint = if (sendingPhoto) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, mine: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (mine) 16.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 16.dp,
    )
    val bubbleColor =
        if (mine) colorScheme.primary.copy(alpha = 0.9f)
        else colorScheme.surfaceVariant.copy(alpha = 0.6f)

    // Image message? `<m v="1"><up s="<id>" o="lib" /></m>` — render the
    // uploaded photo instead of the raw markup (own + incoming). The id may
    // carry a `.jpg` suffix or extra chars, so grab the loose token.
    val imgMatch = Regex("<up s=\"([^\"]+)\"").find(msg.body)
    val imgId = imgMatch?.groupValues?.get(1)?.substringBefore(".jpg")?.trim()
    // Text that came along with the photo — everything in the message apart
    // from the `<m …>` wrapper and the `<up … />` image tag (may be empty).
    // Handles both "…</m>caption" and "…/>caption</m>" layouts.
    val textPart = imgMatch?.let {
        msg.body
            .replace(Regex("<m\\s[^>]*>"), "")
            .replace(Regex("<up\\s[^>]*?/>"), "")
            .replace(Regex("</m>"), "")
            .trim()
            .ifEmpty { null }
    }
    // Plain text message (no image markup).
    val plainText = if (imgMatch == null) msg.body else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleColor),
            ) {
                Column(modifier = Modifier.padding(if (imgId != null) 4.dp else 0.dp)) {
                    if (imgId != null) {
                        AsyncImage(
                            model = ChatangoAuthRepository.chatangoUploadedImageUrl(msg.from, imgId),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .widthIn(max = 252.dp)
                                .heightIn(max = 320.dp)
                                .clip(bubbleShape),
                        )
                    }
                    // Text that was sent together with the photo, or the
                    // plain text of a text-only message.
                    val textToShow = if (imgId != null) textPart else plainText
                    if (!textToShow.isNullOrBlank()) {
                        Text(
                            textToShow,
                            color = if (mine) colorScheme.onPrimary else colorScheme.onSurface,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(
                                horizontal = if (imgId != null) 8.dp else 12.dp,
                                vertical = if (imgId != null) 6.dp else 8.dp,
                            ),
                        )
                    }
                }
            }
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.time)),
                fontSize = 10.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** Copy a picked content Uri into the cache dir as a jpg file. */
private fun copyToCache(context: android.content.Context, uri: Uri): File {
    val file = File(context.cacheDir, "chatango_pm_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(file).use { output -> input.copyTo(output) }
    }
    return file
}

private fun statusLabel(status: String?): String = when (status) {
    "online" -> "● Online"
    "app" -> "● Mobile"
    else -> "○ Offline"
}
