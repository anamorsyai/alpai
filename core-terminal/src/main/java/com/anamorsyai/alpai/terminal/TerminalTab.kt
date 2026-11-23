package com.anamorsyai.alpai.terminal

import com.anamorsyai.alpai.terminal.TerminalEmulator

/** One tab's independent shell + screen buffer — [TerminalSessionManager] holds a list of these and
 *  reattaches the single [com.anamorsyai.alpai.terminal.TerminalView] to whichever is active. */
class TerminalTab(
    val id: Int,
    val session: PtySession,
    val emulator: TerminalEmulator,
    val backendLabel: String,
    /** User-assigned name (long-press a tab → Rename) — null falls back to the plain "N" the tab
     *  bar has always shown. Distinct sessions become hard to tell apart once several are
     *  running something different in the background. */
    var label: String? = null,
) {
    /** Set by whichever TerminalSessionManager owner currently owns the UI, and re-set every time one
     *  does (including a freshly recreated Activity re-attaching to this same tab) — the reader
     *  thread that calls this outlives any single Activity instance, so it can't just close over
     *  a TerminalView directly, or output arriving while the Activity was destroyed and being
     *  recreated would poke a defunct, no-longer-displayed view instead of the new one. */
    @Volatile var onOutput: (() -> Unit)? = null

    /** Same reasoning and same re-binding as [onOutput] — the reader thread that calls this on
     *  EOF outlives any one UI instance, so it must never close over a UI object
     *  directly (see TerminalSessionManager.startReaderThread()), or a shell exiting after the system
     *  recreated the UI would update the dead instance's tab list and views instead of the
     *  live one's. */
    @Volatile var onExit: (() -> Unit)? = null
}
