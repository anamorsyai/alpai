package com.anamorsyai.alpai.terminal

/**
 * The handful of session-start settings [AlpineSession] honours. AlpAI v1 wires the
 * hardcoded defaults only — there is no Settings screen for these yet. When one lands,
 * pass a real implementation into [AlpineSession.start] instead of changing the session code.
 */
interface TerminalSettings {
    /**
     * Let proot's seccomp filter trap only the syscalls it must, instead of single-stepping
     * every syscall (PROOT_NO_SECCOMP=1 forces the slow way). Much less CPU for syscall-heavy
     * programs; off by default because it can make some apk operations fail with EPERM.
     */
    val fastProotTracing: Boolean

    /** Run the session on the low-power cores (pty_bridge applies the mask before exec). */
    val efficiencyCores: Boolean
}

/** AlpAI v1 defaults: conservative — PROOT_NO_SECCOMP=1 always, no CPU mask. */
object DefaultTerminalSettings : TerminalSettings {
    override val fastProotTracing: Boolean = false
    override val efficiencyCores: Boolean = false
}
