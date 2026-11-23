package com.anamorsyai.alpai.terminal

/**
 * Minimal /proc helpers for [PtySession]'s teardown, ported from AlpDroid's
 * ResourcePolicy (which also holds power-mode / CPU / kill-explanation logic
 * this module does not need). Android-free on purpose, so it stays unit-testable.
 */
internal object ProcTree {

    class ProcStat(val ppid: Int, val cpuJiffies: Long)

    /** Parses /proc/<pid>/stat. The command name (field 2) sits in parentheses and may itself
     *  contain spaces and parentheses, so everything is read relative to the LAST ')'. */
    fun parseStat(text: String): ProcStat? {
        val close = text.lastIndexOf(')')
        if (close < 0) return null
        val f = text.substring(close + 1).trim().split(' ')
        // f[0] = state (field 3), f[1] = ppid (field 4), f[11] = utime (field 14), f[12] = stime (field 15)
        if (f.size < 13) return null
        val ppid = f[1].toIntOrNull() ?: return null
        val utime = f[11].toLongOrNull() ?: return null
        val stime = f[12].toLongOrNull() ?: return null
        return ProcStat(ppid, utime + stime)
    }

    /** All descendants of [root] (excluding it) given a pid -> ppid map. */
    fun descendants(root: Int, parentOf: Map<Int, Int>): Set<Int> {
        val children = HashMap<Int, MutableList<Int>>()
        for ((pid, ppid) in parentOf) children.getOrPut(ppid) { ArrayList() }.add(pid)
        val out = LinkedHashSet<Int>()
        val stack = ArrayDeque<Int>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            for (c in children[stack.removeLast()].orEmpty()) if (out.add(c)) stack.add(c)
        }
        return out
    }
}
