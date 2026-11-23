package com.anamorsyai.alpai.terminal

/**
 * The narrow surface [TerminalKeepAliveService] needs from the app's session owner.
 * The service must never depend on the app's Application class (AlpDroid's service reached
 * straight into AlpineTermApp for tabs/pluginJobs/services); it only ever asks the registry
 * how many tabs are alive and to destroy them all.
 */
interface TerminalSessionRegistry {
    /** Number of live tabs right now. */
    val tabCount: Int

    /** Destroy every session's underlying process (tabs are removed by their reader threads). */
    fun destroyAllTabs()

    /** Synchronously drop every tab from the list (fallback when a session is wedged and its
     *  reader thread never reports EOF). */
    fun removeAllTabsNow()
}

/** Implemented by the Application so the keep-alive service can find the registry. */
interface TerminalSessionRegistryProvider {
    val terminalRegistry: TerminalSessionRegistry
}
