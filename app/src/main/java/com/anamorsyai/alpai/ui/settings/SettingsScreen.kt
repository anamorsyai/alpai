package com.anamorsyai.alpai.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anamorsyai.alpai.data.repository.ThemeMode
import com.anamorsyai.alpai.ui.components.SectionLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()
    val streamingEnabled by viewModel.streamingEnabled.collectAsState()
    val defaultModel by viewModel.defaultModel.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Appearance")
            ThemeMode.entries.forEach { mode ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                    )
                    Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }

            Text("Font size: ${"%.0f".format(fontScale * 100)}%", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = fontScale,
                onValueChange = { viewModel.setFontScale(it) },
                valueRange = 0.85f..1.3f,
                steps = 8,
            )

            SectionLabel("Chat")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Streaming replies", modifier = Modifier.weight(1f))
                Switch(
                    checked = streamingEnabled,
                    onCheckedChange = { viewModel.setStreamingEnabled(it) },
                )
            }
            OutlinedTextField(
                value = defaultModel,
                onValueChange = { viewModel.setDefaultModel(it) },
                label = { Text("Default model ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Data")
            OutlinedButton(
                onClick = {
                    // TODO: export the selected chat as Markdown/JSON.
                    Toast.makeText(context, "Chat export is on the roadmap.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export chat (coming soon)") }
            Button(
                onClick = { showClearConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Clear all chats") }

            Text(
                text = "AlpAI v0.1.0 — smooth Android AI agent client.\nChats are stored locally with Room; settings with DataStore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all chats?") },
            text = { Text("This deletes every chat and message stored on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllChats()
                        showClearConfirm = false
                        Toast.makeText(context, "All chats cleared.", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
