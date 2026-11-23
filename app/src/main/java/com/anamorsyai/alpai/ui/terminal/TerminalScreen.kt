package com.anamorsyai.alpai.ui.terminal

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LIGHT LOCAL TERMINAL — STUB (v0.1).
 *
 * What this screen is today: a terminal-looking UI with a tiny local
 * command interpreter (help / echo / clear / date). It runs NO shell and
 * NO PTY yet.
 *
 * NEXT STEP — wiring the real terminal from AlpDroid:
 *  1. Port AlpDroid's PTY session class (the one that spawns a process
 *     with a pseudo-terminal and pumps stdin/stdout) into a new
 *     `:core-terminal` module here — do NOT copy the whole app, only the
 *     session + I/O pump.
 *  2. Reuse AlpDroid's Alpine rootfs approach: extract the minimal
 *     rootfs to app storage on first run and run `/bin/sh` inside it
 *     (proot-style, no root required), exactly as AlpDroid does.
 *  3. Replace [TerminalViewModel.runCommand]'s interpreter with the PTY
 *     session: write input bytes to the PTY, append PTY output bytes to
 *     [TerminalViewModel.lines]. Keep this UI unchanged — it already
 *     speaks in lines + input, which is all a PTY needs.
 *  4. Add real tabs (one PTY session per tab) and the extra-keys row
 *     from AlpDroid.
 */
class TerminalViewModel : ViewModel() {

    private val _lines = MutableStateFlow(
        listOf(
            "AlpAI Terminal — light local terminal (stub)",
            "Type 'help' for the built-in commands.",
            "Real shell (AlpDroid PTY + Alpine) lands in the next step.",
            "",
        ),
    )
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun runCommand(raw: String) {
        val command = raw.trim()
        if (command.isEmpty()) return
        viewModelScope.launch {
            _lines.update { it + "$ $command" }
            when {
                command == "help" -> _lines.update {
                    it + listOf(
                        "Built-in commands (stub):",
                        "  help   — show this list",
                        "  echo <text> — print text",
                        "  date   — current date/time",
                        "  clear  — clear the screen",
                        "Anything else: the real shell is not wired yet.",
                    )
                }
                command.startsWith("echo ") -> _lines.update { it + command.removePrefix("echo ") }
                command == "date" -> _lines.update {
                    it + SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.getDefault()).format(Date())
                }
                command == "clear" -> _lines.value = emptyList()
                else -> _lines.update {
                    it + "alpai: command not found: $command (real shell coming soon)"
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(viewModel: TerminalViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val lines by viewModel.lines.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            runCatching { listState.scrollToItem(lines.lastIndex) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                actions = {
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Terminal tabs land with the real shell.", Toast.LENGTH_SHORT).show()
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "New terminal tab")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0D0C16))
                .padding(12.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                items(lines) { line ->
                    Text(
                        text = line.ifBlank { " " },
                        color = Color(0xFFE8E6F5),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$",
                    color = Color(0xFF2DD4BF),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(end = 8.dp),
                )
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("type a command…", color = Color.Gray) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF171524),
                        unfocusedContainerColor = Color(0xFF171524),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color(0xFF2DD4BF),
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        viewModel.runCommand(input)
                        input = ""
                    },
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Run", tint = Color(0xFF2DD4BF))
                }
            }
        }
    }
}
