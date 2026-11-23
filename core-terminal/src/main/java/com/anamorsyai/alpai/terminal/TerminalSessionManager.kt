package com.anamorsyai.alpai.terminal

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Application-scoped owner of every terminal tab — the sessions outlive any single Activity
 * (a config change, the app going to the background), so they live here, not in a ViewModel.
 *
 * Extracted from AlpDroid's MainActivity session patterns (startSessionNow / startReaderThread /
 * onTabExited / switchToTab / closeTab / dropDeadTabs) — minus everything Activity-shaped:
 * no tab bar, no setup UI, no auto-start, no resource manager. The UI side
 * (TerminalScreen's ViewModel) observes [tabsFlow]/[activeIndex] and calls into this.
 *
 * The single [TerminalView] is reattached to whichever tab is active; each tab's [TerminalTab.onOutput]
 * callback only forwards to the view when that tab is the active one.
 */
class TerminalSessionManager(private val app: Application) : TerminalSessionRegistry {

    /** The owning Application — sessions, rootfs and services all hang off this. */
    val application: Application get() = app

    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "alpai-terminal-bg").apply { isDaemon = true }
    }

    private val tabsLock = Any()
    private val tabsInternal = mutableListOf<TerminalTab>()

    /** Live tabs, oldest first. Read-only snapshot — never mutate the returned list. */
    val tabs: List<TerminalTab>
        get() = synchronized(tabsLock) { tabsInternal.toList() }

    private val _tabsFlow = MutableStateFlow<List<TerminalTab>>(emptyList())
    val tabsFlow: StateFlow<List<TerminalTab>> = _tabsFlow.asStateFlow()

    private val _activeIndex = MutableStateFlow(0)
    val activeIndex: StateFlow<Int> = _activeIndex.asStateFlow()

    /** Grid size the next new tab starts at; updated from TerminalView's onGridSize. */
    @Volatile var lastRows: Int = 24
        private set
    @Volatile var lastCols: Int = 80
        private set

    private var nextTabId = 1

    /** Starts in flight (AlpineSession.start running on the background executor). While one is
     *  pending, the "last tab exited" path must not report the list as permanently empty. */
    private val pendingSessionStarts = AtomicInteger(0)

    /** The last tab closed/exited and nothing is starting — the UI shows its empty state. */
    var onAllTabsClosed: (() -> Unit)? = null

    /** A tab's bell rang (main thread, tab id). The UI decides what a bell means. */
    var onBell: ((Int) -> Unit)? = null

    /** A session ended by itself with a non-zero status (main thread). */
    var onSessionEnded: ((String) -> Unit)? = null

    // --- TerminalSessionRegistry (for the keep-alive service) ---

    override val tabCount: Int
        get() = synchronized(tabsLock) { tabsInternal.size }

    override fun destroyAllTabs() {
        synchronized(tabsLock) { tabsInternal.toList() }.forEach { runCatching { it.session.destroy() } }
    }

    override fun removeAllTabsNow() {
        synchronized(tabsLock) { tabsInternal.clear() }
        _activeIndex.value = 0
        emitTabs()
        persistTabLabels()
        updateKeepAliveService()
    }

    // --- Tabs ---

    /**
     * Starts a new tab on the background executor. [AlpineSession.start] itself runs
     * AlpineRootfs.ensureReady() when needed, so this is also the first-run path — the caller
     * shows the setup UI while this runs and only switches to the terminal on [onStarted].
     */
    fun addTab(
        label: String? = null,
        onStarted: (() -> Unit)? = null,
        onFailed: ((Throwable) -> Unit)? = null,
    ) {
        val id = synchronized(tabsLock) { nextTabId++ }
        pendingSessionStarts.incrementAndGet()
        backgroundExecutor.execute {
            val result = runCatching { AlpineSession.start(app, lastRows, lastCols, id) }
            mainHandler.post {
                pendingSessionStarts.decrementAndGet()
                result.onSuccess { started ->
                    val emulator = TerminalEmulator(
                        lastRows, lastCols,
                        maxScrollback = 2000,
                        respond = { text -> writeToSession(started.session, text) },
                    )
                    val tab = TerminalTab(id, started.session, emulator, started.backendLabel, label)
                    if (!tab.backendLabel.startsWith("Alpine")) {
                        val banner = "[AlpAI] backend: ${tab.backendLabel}\r\n".toByteArray(Charsets.UTF_8)
                        emulator.feed(banner, banner.size)
                    }
                    synchronized(tabsLock) { tabsInternal.add(tab) }
                    tab.onOutput = {
                        if (tabs.getOrNull(_activeIndex.value) === tab) onOutputForActiveTab?.invoke()
                    }
                    tab.onExit = { onTabExited(tab) }
                    emulator.onBell = { onBell?.invoke(tab.id) }
                    persistTabLabels()
                    startReaderThread(tab)
                    emitTabs()
                    _activeIndex.value = tabsInternal.size - 1
                    updateKeepAliveService()
                    onStarted?.invoke()
                }.onFailure { e ->
                    Log.e(TAG, "failed to start shell for tab $id", e)
                    onFailed?.invoke(e)
                }
            }
        }
    }

    /** Delivered on the main thread when the *active* tab produces output — the ViewModel
     *  points this at the attached TerminalView's onPtyOutput(). */
    var onOutputForActiveTab: (() -> Unit)? = null

    fun switchToTab(index: Int) {
        val list = tabs
        if (index !in list.indices) return
        _activeIndex.value = index
    }

    fun closeTab(index: Int) {
        val tab = synchronized(tabsLock) { tabsInternal.getOrNull(index) } ?: return
        closeTabInternal(tab)
    }

    fun closeTabById(id: Int): Boolean {
        val tab = tabs.firstOrNull { it.id == id } ?: return false
        closeTabInternal(tab)
        return true
    }

    private fun closeTabInternal(tab: TerminalTab) {
        val wasActive: Boolean
        val activeTab: TerminalTab?
        synchronized(tabsLock) {
            val idx = tabsInternal.indexOf(tab)
            if (idx < 0) return
            wasActive = idx == _activeIndex.value
            activeTab = tabs.getOrNull(_activeIndex.value)
            tabsInternal.removeAt(idx)
        }
        tab.session.destroy()
        persistTabLabels()
        updateKeepAliveService()
        val remaining = tabs
        if (remaining.isEmpty()) {
            if (pendingSessionStarts.get() == 0) onAllTabsClosed?.invoke()
            _activeIndex.value = 0
            emitTabs()
            return
        }
        _activeIndex.value = if (wasActive) {
            _activeIndex.value.coerceAtMost(remaining.size - 1)
        } else {
            remaining.indexOf(activeTab).takeIf { it >= 0 } ?: _activeIndex.value.coerceAtMost(remaining.size - 1)
        }
        emitTabs()
    }

    /** A tab's shell exited (typed "exit"/Ctrl-D, or crashed) — drop it and switch to a
     *  neighbor. The reader thread calls this via [TerminalTab.onExit]. */
    private fun onTabExited(tab: TerminalTab) {
        val idx: Int
        val wasActive: Boolean
        val activeTab: TerminalTab?
        synchronized(tabsLock) {
            idx = tabsInternal.indexOf(tab)
            if (idx < 0) return
            wasActive = idx == _activeIndex.value
            activeTab = tabs.getOrNull(_activeIndex.value)
            tabsInternal.removeAt(idx)
        }
        // The pty itself is already gone, but nothing else is: destroy() joins the resize
        // thread, closes the control-fifo fd and deletes the fifo file. Safe unconditionally —
        // destroy() is idempotent.
        tab.session.destroy()
        persistTabLabels()
        updateKeepAliveService()
        val remaining = tabs
        if (remaining.isEmpty()) {
            if (pendingSessionStarts.get() > 0) return // a replacement is already on its way
            SessionPersistence.clear(app)
            _activeIndex.value = 0
            emitTabs()
            onAllTabsClosed?.invoke()
            return
        }
        _activeIndex.value = if (wasActive) {
            idx.coerceAtMost(remaining.size - 1)
        } else {
            // The tab the user was actually looking at is still there — only its index shifted.
            remaining.indexOf(activeTab).takeIf { it >= 0 } ?: idx.coerceAtMost(remaining.size - 1)
        }
        emitTabs()
    }

    /** Sessions that ended while no UI was attached (callbacks never fired) — drop them quietly. */
    fun dropDeadTabs() {
        val dead = tabs.filter { !it.session.isAlive() }
        if (dead.isEmpty()) return
        val active = tabs.getOrNull(_activeIndex.value)
        dead.forEach { tab ->
            runCatching { tab.session.destroy() }
            synchronized(tabsLock) { tabsInternal.remove(tab) }
        }
        val remaining = tabs
        _activeIndex.value = remaining.indexOf(active).takeIf { it >= 0 } ?: 0
        persistTabLabels()
        updateKeepAliveService()
        emitTabs()
    }

    /**
     * Restores tabs from the last session's saved labels — one tab per saved label. Returns
     * true when anything was restored; the caller starts a single fresh tab otherwise.
     */
    fun restoreSavedTabs(): Boolean {
        val labels = SessionPersistence.load(app)
        if (labels.isEmpty()) return false
        labels.forEach { addTab(label = it) }
        return true
    }

    fun setGridSize(rows: Int, cols: Int) {
        if (rows < 1 || cols < 1) return
        lastRows = rows
        lastCols = cols
        tabs.getOrNull(_activeIndex.value)?.let { resizeTab(it, rows, cols) }
    }

    fun resizeTab(tab: TerminalTab, rows: Int, cols: Int) {
        tab.session.resize(rows, cols)
    }

    fun refreshNetwork() = AlpineSession.refreshNetwork(app)

    fun writeToSession(session: PtySession, text: String) {
        session.writeAsync(text.toByteArray(Charsets.UTF_8))
    }

    private fun startReaderThread(tab: TerminalTab) {
        Thread({
            val buf = ByteArray(32 * 1024)
            // Wrapping the whole loop, not just the read() — any other uncaught exception here
            // would otherwise kill this thread, skipping the exit handling below entirely: no
            // crash, no visible error, but the tab's shell could never report its own exit
            // again — every future EOF would go unnoticed, i.e. "the tab hangs and never
            // closes". Catching here guarantees onExit still fires no matter what went wrong.
            try {
                while (true) {
                    val n = try { tab.session.stdout.read(buf) } catch (e: IOException) { -1 }
                    if (n <= 0) break
                    // Throwable, not just Exception, and per chunk: if this thread died on one
                    // bad chunk nothing would read the pty any more, the bridge's stdout pipe
                    // would fill, the bridge would block, and the whole session would freeze.
                    try {
                        tab.emulator.feed(buf, n)
                        tab.onOutput?.invoke()
                    } catch (t: Throwable) {
                        Log.e(TAG, "pty-reader-${tab.id}: dropped a chunk after ${t.javaClass.simpleName}", t)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "pty-reader-${tab.id} crashed; closing tab instead of hanging it", e)
            }
            // Why did it end? A session ending by itself with a non-zero status was killed
            // from outside — say so, otherwise a shell that "just died in the background"
            // leaves no trace at all.
            val code = if (tab.session.endedByUs) null else tab.session.exitCodeWithin(800)
            mainHandler.post {
                if (code != null && code != 0 && tabs.contains(tab)) {
                    val why = when (code) {
                        137 -> "killed by the system (SIGKILL — likely Android's phantom-process killer or the low-memory killer)"
                        143 -> "terminated (SIGTERM)"
                        125 -> "closed by the terminal's own cleanup (the terminal link ended while the program was still running)"
                        else -> "exit code $code"
                    }
                    onSessionEnded?.invoke("Session ended: $why")
                }
                tab.onExit?.invoke()
            }
        }, "pty-reader-${tab.id}").apply { isDaemon = true }.start()
    }

    private fun persistTabLabels() {
        SessionPersistence.save(app, tabs.map { it.label })
    }

    private fun emitTabs() {
        _tabsFlow.value = tabs
    }

    private fun updateKeepAliveService() {
        runCatching {
            if (tabCount > 0) {
                app.startForegroundService(Intent(app, TerminalKeepAliveService::class.java))
            } else {
                app.stopService(Intent(app, TerminalKeepAliveService::class.java))
            }
        }.onFailure { Log.w(TAG, "could not update keep-alive service", it) }
    }

    companion object {
        private const val TAG = "AlpAI/TerminalManager"
    }
}
