package com.anamorsyai.alpai.ui.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.anamorsyai.alpai.data.model.Provider
import com.anamorsyai.alpai.data.model.ProviderKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(viewModel: ProvidersViewModel) {
    val providers by viewModel.providers.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<Provider?>(null) }
    var customModelFor by remember { mutableStateOf<Provider?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Providers & Models") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add provider")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = "Add any OpenAI-compatible provider (OpenAI, OpenRouter, Groq, DeepSeek…). Keys are kept in memory in v0.1 — encrypted storage lands with the next step.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(providers, key = { it.id }) { provider ->
                ProviderCard(
                    provider = provider,
                    onToggle = { viewModel.updateProvider(provider.copy(enabled = !provider.enabled)) },
                    onEdit = { editingProvider = provider },
                    onDelete = { viewModel.deleteProvider(provider.id) },
                    onAddModel = { customModelFor = provider },
                )
            }
        }
    }

    if (showAddDialog) {
        ProviderEditorDialog(
            title = "Add provider",
            onDismiss = { showAddDialog = false },
            onSave = { name, baseUrl, apiKey ->
                viewModel.addProvider(name, baseUrl, apiKey, ProviderKind.OPENAI_COMPATIBLE)
                showAddDialog = false
            },
        )
    }
    editingProvider?.let { provider ->
        ProviderEditorDialog(
            title = "Edit provider",
            initial = provider,
            onDismiss = { editingProvider = null },
            onSave = { name, baseUrl, apiKey ->
                viewModel.updateProvider(
                    provider.copy(name = name, baseUrl = baseUrl, apiKey = apiKey),
                )
                editingProvider = null
            },
        )
    }
    customModelFor?.let { provider ->
        var modelId by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { customModelFor = null },
            title = { Text("Add model to ${provider.name}") },
            text = {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Model ID (e.g. gpt-4o)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addModel(provider.id, modelId)
                        customModelFor = null
                    },
                    enabled = modelId.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { customModelFor = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProviderCard(
    provider: Provider,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddModel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = provider.baseUrl.ifBlank { "Demo — no network" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (provider.apiKey.isBlank()) "No API key set" else "API key set",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = provider.enabled, onCheckedChange = { onToggle() })
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit provider")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete provider")
                }
            }
            Text(
                text = "Models: " + provider.models.joinToString(", ") { it.displayName },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onAddModel) { Text("+ Add custom model") }
        }
    }
}

@Composable
private fun ProviderEditorDialog(
    title: String,
    initial: Provider? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, baseUrl: String, apiKey: String) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "https://api.openai.com/v1") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), baseUrl.trim(), apiKey.trim()) },
                enabled = name.isNotBlank() && baseUrl.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
