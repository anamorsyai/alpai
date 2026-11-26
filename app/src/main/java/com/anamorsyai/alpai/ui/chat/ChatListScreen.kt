package com.anamorsyai.alpai.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.anamorsyai.alpai.data.model.Chat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    viewModel: ChatViewModel,
    onOpenChat: (String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val chats by viewModel.chats.collectAsState()
    val deletedChat by viewModel.deletedChat.collectAsState()
    var query by remember { mutableStateOf("") }
    var chatToDelete by remember { mutableStateOf<Chat?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val filtered = remember(chats, query) {
        if (query.isBlank()) chats
        else chats.filter { it.title.contains(query, ignoreCase = true) }
    }

    // Undo bar: after a delete, offer Undo while the snackbar is showing.
    // Tapping Undo restores the chat; dismissing / timing out drops it for good.
    LaunchedEffect(deletedChat) {
        val deleted = deletedChat ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Deleted \"${deleted.chat.title}\"",
            actionLabel = "Undo",
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.restoreDeletedChat()
        } else {
            viewModel.consumeDeletedChat()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AlpAI") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenChat(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "New chat")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search chats…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (filtered.isEmpty()) {
                Text(
                    text = if (chats.isEmpty()) {
                        "No chats yet — tap + to start your first one."
                    } else {
                        "No chats match \"$query\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                    items(filtered, key = { it.id }) { chat ->
                        ChatRow(
                            chat = chat,
                            onClick = { onOpenChat(chat.id) },
                            onDelete = { chatToDelete = chat },
                        )
                    }
                }
            }
        }
    }

    // Confirmation before anything is deleted.
    chatToDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            title = { Text("Delete chat?") },
            text = { Text("Delete \"${chat.title}\" and all its messages? You can undo right after, from the bar at the bottom.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatToDelete = null
                        viewModel.deleteChat(chat.id)
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { chatToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ChatRow(chat: Chat, onClick: () -> Unit, onDelete: () -> Unit) {
    val time = remember(chat.updatedAt) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(chat.updatedAt))
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    text = listOfNotNull(chat.modelId, time).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete chat")
            }
        }
    }
}
