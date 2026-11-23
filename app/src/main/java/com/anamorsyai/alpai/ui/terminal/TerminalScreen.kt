package com.anamorsyai.alpai.ui.terminal

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateBottomPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anamorsyai.alpai.R
import com.anamorsyai.alpai.terminal.AlpineRootfs
import com.anamorsyai.alpai.terminal.TerminalColors
import com.anamorsyai.alpai.terminal.TerminalSessionManager
import com.anamorsyai.alpai.terminal.TerminalTab
import com.anamorsyai.alpai.terminal.TerminalView
import com.anamorsyai.alpai.terminal.Themes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** First-run / setup state of the terminal screen. */
sealed interface TerminalSetupState {
    /** Checking whether the Alpine rootfs is already installed. */
    data object Checking : TerminalSetupState

    /** Rootfs ready (or a plain system shell will do) — tabs can start. */
    data object Ready : TerminalSetupState

    /** Downloading + extracting the Alpine minirootfs. [progress] is 0..1 (-1 = unknown). */
    data class Downloading(val progress: Float, val status: String) : TerminalSetupState

    /** Setup failed (download error, checksum, …) — offers a retry. */
    data class Failed(val message: String) : TerminalSetupState
}

class TerminalViewModel(private val manager: TerminalSessionManager) : ViewModel() {

    val tabs: StateFlow<List<TerminalTab>> = manager.tabsFlow
    val activeIndex: StateFlow<Int> = manager.activeIndex

    private val _setupState = MutableStateFlow<TerminalSetupState>(TerminalSetupState.Checking)
    val setupState: StateFlow<TerminalSetupState> = _setupState.asStateFlow()

    /** One-shot message for the UI to toast (bell, session killed, save result). */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** The attached TerminalView, as Compose state so the extra-keys row recomposes with it. */
    var terminalView by mutableStateOf<TerminalView?>(null)
        private set

    /** The tab the view is currently bound to — rebinding only happens when this changes. */
    private var boundTab: TerminalTab? = null

    init {
        TerminalColors.applyTheme(Themes.AURORA)
        manager.onAllTabsClosed = { _setupState.value = TerminalSetupState.Ready }
        manager.onBell = { _toast.value = "Bell" }
        manager.onSessionEnded = { message -> _toast.value = message }
        ensureReadyAndStart()
    }

    fun consumeToast() {
        _toast.value = null
    }

    /** First run: download + extract the Alpine rootfs with progress; afterwards start tabs. */
    fun ensureReadyAndStart() {
        _setupState.value = TerminalSetupState.Checking
        viewModelScope.launch {
            val app = manager.application
            val ready = withContext(Dispatchers.IO) { AlpineRootfs.isReady(app) }
            if (ready) {
                startTabs()
                return@launch
            }
            _setupState.value = TerminalSetupState.Downloading(-1f, "Looking up current Alpine release…")
            val ok = withContext(Dispatchers.IO) {
                // ensureReady() runs on its own thread and reports progress through
                // AlpineRootfs.downloadedBytes/totalBytes — poll it while it works.
                val job = async { AlpineRootfs.ensureReady(app) }
                while (!job.isCompleted) {
                    val total = AlpineRootfs.totalBytes
                    val done = AlpineRootfs.downloadedBytes
                    val progress = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else -1f
                    _setupState.value = TerminalSetupState.Downloading(
                        progress,
                        AlpineRootfs.lastStatus.ifEmpty { "Downloading Alpine Linux…" },
                    )
                    delay(200)
                }
                job.await()
            }
            if (ok) {
                startTabs()
            } else {
                _setupState.value = TerminalSetupState.Failed(
                    AlpineRootfs.lastFailure.ifEmpty { "Alpine setup failed for an unknown reason." },
                )
            }
        }
    }

    private fun startTabs() {
        _setupState.value = TerminalSetupState.Ready
        if (!manager.restoreSavedTabs()) {
            manager.addTab(
                onFailed = { e -> _toast.value = "Failed to start a shell: ${e.message}" },
            )
        }
    }

    fun newTab() = manager.addTab(
        onFailed = { e -> _toast.value = "Failed to start a shell: ${e.message}" },
    )

    fun switchTab(index: Int) = manager.switchToTab(index)

    fun closeTab(index: Int) = manager.closeTab(index)

    /**
     * Binds [view] to [tab] — called from the AndroidView update block on every recomposition,
     * but only rebinds when the tab actually changed, so a recomposition never resets the
     * view's scroll position or selection. Tab-independent wiring (grid size, swipe, save)
     * is set once, the first time the view is seen.
     */
    fun attachView(view: TerminalView, tab: TerminalTab?) {
        if (terminalView !== view) {
            terminalView = view
            view.onGridSize = { rows, cols -> manager.setGridSize(rows, cols) }
            view.onSwipeTab = { next ->
                val list = manager.tabs
                if (list.size > 1) {
                    val cur = manager.activeIndex.value
                    val target = if (next) (cur + 1) % list.size else (cur - 1 + list.size) % list.size
                    manager.switchToTab(target)
                }
            }
            view.onSaveSelection = { text ->
                val file = File(view.context.cacheDir, "terminal-selection.txt")
                runCatching { file.writeText(text) }
                    .onSuccess { _toast.value = "Saved to ${file.name} (${text.length} chars)" }
                    .onFailure { _toast.value = "Could not save selection: ${it.message}" }
            }
            manager.onOutputForActiveTab = { view.onPtyOutput() }
        }
        if (boundTab !== tab) {
            boundTab = tab
            view.emulator = tab?.emulator
            view.resetViewState()
            tab?.emulator?.applyPalette()
            view.onInput = { bytes -> tab?.session?.writeAsync(bytes) }
            view.onPaste = { bytes, bracketed -> tab?.session?.pasteAsync(bytes, bracketed) }
            view.invalidate()
        }
    }
}

class TerminalViewModelFactory(
    private val manager: TerminalSessionManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TerminalViewModel::class.java)) {
            return TerminalViewModel(manager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    manager: TerminalSessionManager,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: TerminalViewModel = viewModel(factory = TerminalViewModelFactory(manager))
    val tabs by viewModel.tabs.collectAsState()
    val activeIndex by viewModel.activeIndex.collectAsState()
    val setupState by viewModel.setupState.collectAsState()
    val toast by viewModel.toast.collectAsState()
    val activeTab = tabs.getOrNull(activeIndex)
    val terminalView = viewModel.terminalView

    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchInfo by remember { mutableStateOf("") }
    var ctrlArmed by remember { mutableStateOf(false) }
    var altArmed by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val imeBottomDp = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val imeBottomPx = with(density) { imeBottomDp.roundToPx() }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal") },
                actions = {
                    IconButton(onClick = { viewModel.newTab() }) {
                        Icon(Icons.Filled.Add, contentDescription = "New terminal tab")
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0D0C16)),
        ) {
            when (val state = setupState) {
                is TerminalSetupState.Checking,
                is TerminalSetupState.Downloading -> {
                    val progress = (state as? TerminalSetupState.Downloading)?.progress ?: -1f
                    val status = (state as? TerminalSetupState.Downloading)?.status
                        ?: "Checking Alpine Linux…"
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text("Setting up Alpine Linux", color = Color(0xFFE8E6F5), style = MaterialTheme.typography.titleMedium)
                        Text(
                            status,
                            color = Color(0xFF9B98B0),
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                        )
                        if (progress >= 0f) {
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "${(progress * 100).toInt()}%",
                                color = Color(0xFF9B98B0),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            CircularProgressIndicator()
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Downloads the ~3 MB Alpine minirootfs once, then extracts it (~50 MB).",
                            color = Color(0xFF6E6B80),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                is TerminalSetupState.Failed -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.weight(1f))
                        Text("Terminal setup failed", color = Color(0xFFE8E6F5), style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.message,
                            color = Color(0xFF9B98B0),
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        Button(onClick = { viewModel.ensureReadyAndStart() }) { Text("Retry") }
                        TextButton(onClick = { viewModel.newTab() }) { Text("Use system shell instead") }
                        Spacer(Modifier.weight(1f))
                    }
                }

                is TerminalSetupState.Ready -> {
                    if (tabs.isEmpty()) {
                        // Last tab closed (or the first start is still in flight).
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(Modifier.weight(1f))
                            Text("No terminal tabs", color = Color(0xFFE8E6F5), style = MaterialTheme.typography.titleMedium)
                            Button(
                                onClick = { viewModel.newTab() },
                                modifier = Modifier.padding(top = 16.dp),
                            ) { Text("New tab") }
                            Spacer(Modifier.weight(1f))
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Tabs strip.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    FilterChip(
                                        selected = index == activeIndex,
                                        onClick = { viewModel.switchTab(index) },
                                        label = { Text(tab.label ?: "${index + 1}") },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { viewModel.closeTab(index) },
                                                modifier = Modifier.size(20.dp),
                                            ) {
                                                Icon(Icons.Filled.Close, contentDescription = "Close tab", modifier = Modifier.size(14.dp))
                                            }
                                        },
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                }
                            }

                            // Search row (toggled from the extra-keys row).
                            if (showSearch) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Search terminal") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                terminalView?.searchAsync(searchQuery, true) { found, row, total ->
                                                    searchInfo = if (found) "Match ${row + 1} of $total" else "Not found"
                                                }
                                            },
                                        ),
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = {
                                        terminalView?.searchAsync(searchQuery, false) { found, row, total ->
                                            searchInfo = if (found) "Match ${row + 1} of $total" else "Not found"
                                        }
                                    }) {
                                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Previous match")
                                    }
                                    IconButton(onClick = {
                                        terminalView?.searchAsync(searchQuery, true) { found, row, total ->
                                            searchInfo = if (found) "Match ${row + 1} of $total" else "Not found"
                                        }
                                    }) {
                                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Next match")
                                    }
                                    IconButton(onClick = {
                                        showSearch = false
                                        terminalView?.clearSearch()
                                        searchInfo = ""
                                    }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                                    }
                                }
                                if (searchInfo.isNotEmpty()) {
                                    Text(searchInfo, color = Color(0xFF9B98B0), modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
                                }
                            }

                            // The terminal itself — a plain Android View hosted in Compose.
                            // The view never resizes for the keyboard (the app is edge-to-edge);
                            // instead imeCoverPx shifts rendering so the cursor stays visible,
                            // and the extra-keys row below floats above the keyboard via ime
                            // padding — the same pairing AlpDroid used.
                            AndroidView(
                                factory = { ctx -> TerminalView(ctx, null) },
                                update = { view ->
                                    if (view.imeCoverPx != imeBottomPx) view.imeCoverPx = imeBottomPx
                                    viewModel.attachView(view, activeTab)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            )

                            // Extra-keys row (AlpDroid MAIN row only — no SHIFT/DEL).
                            ExtraKeysRow(
                                view = terminalView,
                                ctrlArmed = ctrlArmed,
                                altArmed = altArmed,
                                onToggleSearch = { showSearch = !showSearch },
                                onModifierChanged = { c, a -> ctrlArmed = c; altArmed = a },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = imeBottomDp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * AlpDroid's MAIN extra-keys row, as Compose buttons: keyboard, search, ESC, TAB, CTRL, ALT,
 * arrows, HOME, END, /, -, |. CTRL/ALT are momentary modifiers — tapping one arms it for the
 * next key, and the view reports back through onModifierStateChanged so the highlight stays
 * in sync when a keystroke consumes the armed state.
 *
 * Compose buttons never take focus the way focusable Views could, so tapping one can't
 * hide/show the keyboard by accident.
 */
@Composable
private fun ExtraKeysRow(
    view: TerminalView?,
    ctrlArmed: Boolean,
    altArmed: Boolean,
    onToggleSearch: () -> Unit,
    onModifierChanged: (Boolean, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExtraKeyIcon(icon = Icons.Filled.Keyboard, label = "Keyboard") { view?.toggleKeyboard() }
        ExtraKeyIcon(icon = Icons.Filled.Search, label = "Search", tint = Color(0xFF3ED0B8)) { onToggleSearch() }
        ExtraKey("ESC") { view?.sendControlAware(byteArrayOf(0x1B)) }
        ExtraKey("TAB") { view?.sendControlAware(byteArrayOf(0x09)) }
        ExtraKey(
            "CTRL",
            highlighted = ctrlArmed,
            onClick = {
                view?.let {
                    it.ctrlArmed = !it.ctrlArmed
                    onModifierChanged(it.ctrlArmed, it.altArmed)
                }
            },
        )
        ExtraKey(
            "ALT",
            highlighted = altArmed,
            onClick = {
                view?.let {
                    it.altArmed = !it.altArmed
                    onModifierChanged(it.ctrlArmed, it.altArmed)
                }
            },
        )
        ExtraKeyIcon(iconRes = R.drawable.ic_arrow_up, label = "Up") { view?.sendControlAware(arrow(view, "\u001BOA", "\u001B[A")) }
        ExtraKeyIcon(iconRes = R.drawable.ic_arrow_down, label = "Down") { view?.sendControlAware(arrow(view, "\u001BOB", "\u001B[B")) }
        ExtraKeyIcon(iconRes = R.drawable.ic_arrow_left, label = "Left") { view?.sendControlAware(arrow(view, "\u001BOD", "\u001B[D")) }
        ExtraKeyIcon(iconRes = R.drawable.ic_arrow_right, label = "Right") { view?.sendControlAware(arrow(view, "\u001BOC", "\u001B[C")) }
        ExtraKey("HOME") { view?.sendControlAware("\u001B[H") }
        ExtraKey("END") { view?.sendControlAware("\u001B[F") }
        ExtraKey("/") { view?.sendControlAware("/") }
        ExtraKey("-") { view?.sendControlAware("-") }
        ExtraKey("|") { view?.sendControlAware("|") }
    }
    // Keep the Compose highlight in sync when the view itself clears the armed state
    // (a keystroke consuming CTRL/ALT) — mirrors AlpDroid's modifierRefreshers.
    LaunchedEffect(view) {
        view?.onModifierStateChanged = {
            onModifierChanged(view.ctrlArmed, view.altArmed)
        }
    }
}

/** Respect DECCKM like TerminalView.onKeyDown does: programs in application-cursor-keys mode
 *  (vim, fzf-style pickers) expect SS3 sequences, not CSI. */
private fun arrow(view: TerminalView?, app: String, normal: String): String =
    if (view?.emulator?.applicationCursorKeys == true) app else normal

@Composable
private fun ExtraKey(label: String, highlighted: Boolean = false, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.padding(end = 6.dp),
    ) {
        Text(
            label,
            color = if (highlighted) Color(0xFF8B7CF6) else Color(0xFFE7ECEF),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ExtraKeyIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconRes: Int? = null,
    label: String,
    tint: Color = Color(0xFFE7ECEF),
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp)) {
        when {
            icon != null -> Icon(icon, contentDescription = label, tint = tint)
            iconRes != null -> Icon(painterResource(iconRes), contentDescription = label, tint = tint)
        }
    }
}
