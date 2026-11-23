package com.anamorsyai.alpai.ui.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anamorsyai.alpai.data.model.Attachment
import com.anamorsyai.alpai.ui.components.MessageBubble
import com.anamorsyai.alpai.ui.components.SectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String?,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    fontScale: Float = 1f,
) {
    val context = LocalContext.current
    // An empty chatId observes nothing (Room returns no rows for "").
    val messages by viewModel.observeMessages(chatId ?: "").collectAsState(initial = emptyList())
    val streaming by viewModel.streamingMessage.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val selectedModelId by viewModel.selectedModelId.collectAsState()
    val createdChatId by viewModel.createdChatId.collectAsState()

    var input by remember { mutableStateOf("") }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    val attachments = remember { mutableStateListOf<Attachment>() }
    val listState = rememberLazyListState()

    // After the first message creates a chat, open it.
    LaunchedEffect(createdChatId) {
        val id = createdChatId
        if (id != null && chatId == null) {
            viewModel.consumeCreatedChatId()
            // The parent NavHost navigates via onOpenChat; here we just go
            // back to the list, where the new chat appears at the top.
            // (Deep navigation to the new chat id is handled by the caller
            // observing the same state in a future step.)
            onBack()
            Toast.makeText(context, "Chat saved — open it from the list.", Toast.LENGTH_SHORT).show()
        }
    }

    val visibleMessages = remember(messages, streaming) {
        val streamingForThisChat = streaming?.takeIf { it.chatId == chatId }
        if (streamingForThisChat != null) messages + streamingForThisChat else messages
    }
    LaunchedEffect(visibleMessages.size, streaming?.content?.length) {
        if (visibleMessages.isNotEmpty()) {
            runCatching { listState.scrollToItem(visibleMessages.lastIndex) }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            attachments += Attachment(
                name = uri.lastPathSegment ?: "file",
                uri = uri.toString(),
            )
        }
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            attachments += Attachment(
                name = uri.lastPathSegment ?: "photo.jpg",
                uri = uri.toString(),
                mimeType = "image/*",
            )
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) {
            // TODO: save the bitmap to app storage and attach its real URI.
            // v0.1 records the capture; the image is not persisted yet.
            attachments += Attachment(name = "camera-photo.jpg", uri = "", mimeType = "image/jpeg")
            Toast.makeText(context, "Photo captured (saving to storage is a TODO).", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (chatId == null) "New chat" else "Chat",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton(onClick = { showModelPicker = true }) {
                            Text(
                                text = "${selectedProvider?.name ?: "No provider"} · $selectedModelId",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                actions = {
                    if (chatId != null && !isGenerating && messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.regenerate(chatId) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Regenerate")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visibleMessages, key = { it.id }) { message ->
                    MessageBubble(message = message, fontScale = fontScale)
                }
                if (visibleMessages.isEmpty()) {
                    item {
                        Text(
                            text = "Ask anything. Pick a model from the top bar, or add your own provider in Providers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    attachments.forEach { attachment ->
                        AssistChip(
                            onClick = { attachments.remove(attachment) },
                            label = { Text(attachment.name) },
                            trailingIcon = {
                                Icon(Icons.Filled.Close, contentDescription = "Remove")
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showAttachSheet = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Attach")
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Message AlpAI…") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    maxLines = 5,
                )
                IconButton(
                    onClick = {
                        if (isGenerating) {
                            viewModel.stopGeneration()
                        } else {
                            viewModel.sendMessage(chatId, input, attachments.toList())
                            input = ""
                            attachments.clear()
                        }
                    },
                    enabled = isGenerating || input.isNotBlank(),
                ) {
                    Icon(
                        if (isGenerating) Icons.Filled.Stop else Icons.Filled.Send,
                        contentDescription = if (isGenerating) "Stop" else "Send",
                    )
                }
            }
        }
    }

    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionLabel("Add to this message")
                AttachOption(
                    icon = { Icon(Icons.Filled.AttachFile, contentDescription = null) },
                    label = "Files",
                    onClick = { showAttachSheet = false; filePicker.launch("*/*") },
                )
                AttachOption(
                    icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                    label = "Photo from gallery",
                    onClick = { showAttachSheet = false; photoPicker.launch("image/*") },
                )
                AttachOption(
                    icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                    label = "Camera",
                    onClick = { showAttachSheet = false; cameraLauncher.launch(null) },
                )
                AttachOption(
                    icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                    label = "Voice (coming soon)",
                    onClick = {
                        showAttachSheet = false
                        Toast.makeText(context, "Voice input is on the roadmap.", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }

    if (showModelPicker) {
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("Choose provider & model") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    providers.forEach { provider ->
                        SectionLabel(provider.name)
                        provider.models.forEach { model ->
                            TextButton(
                                onClick = {
                                    viewModel.selectProvider(provider.id, model.id)
                                    showModelPicker = false
                                },
                            ) {
                                Text(
                                    text = if (model.id == selectedModelId && provider.id == selectedProvider?.id) {
                                        "● ${model.displayName}"
                                    } else {
                                        model.displayName
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelPicker = false }) { Text("Done") }
            },
        )
    }
}

@Composable
private fun AttachOption(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
