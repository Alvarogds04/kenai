package com.kenai.android.core

import com.kenai.android.link.SessionInfo
import com.kenai.android.character.BotState

/** The words on Home: one plain line per agent, what you decided lately, how many are busy. Pure, so it is unit-tested. */
object HomeText {
    private val BUSY = setOf(BotState.WORKING, BotState.THINKING, BotState.SEARCHING)

    /**
     * The sentence under an agent's name: its last message once it has finished, otherwise what it is
     * doing in plain words ([ToolLabels]). Null when there is nothing to say; the screen then shows the state's name.
     */
    fun line(s: SessionInfo): String? {
        if (s.state == BotState.FINISHED) s.finalLine?.let { SafeText.prose(it) }?.takeIf { it.isNotEmpty() && it != "…" }?.let { return it }
        return ToolLabels.label(s.statusText).takeIf { it.isNotEmpty() }
    }

    /** "Allowed: edit files" or "Denied: run a command", for the Recent list. */
    fun recent(allowed: Boolean, tool: String, w: Words = Words.current()): String = w.recent(allowed, ToolLabels.action(tool, w))

    /** "You allowed Claude Code to run a command": a decision in a sentence, with no command and no path. */
    fun decision(allowed: Boolean, agent: String, tool: String, w: Words = Words.current()): String =
        w.decision(allowed, agent, ToolLabels.action(tool, w))

    /** How many agents are busy (working, thinking or searching), for "2 running". */
    fun running(sessions: List<SessionInfo>): Int = sessions.count { it.state in BUSY }

    // ── Inicio with a request as the hero (kenai-0.1 S9, T13) ──

    /** "Quiere ejecutar un comando en kenai": the kind of action ([ToolLabels.action]) and, when the details are offered, the project. */
    fun wants(tool: String, project: String?, w: Words = Words.current()): String {
        val action = ToolLabels.action(tool, w)
        val where = project?.trim().orEmpty()
        return if (where.isEmpty()) w.wants(action) else w.wantsIn(action, where)
    }

    /** True for a shell tool (Bash, run_shell_command…): the lock prompt then says "run a command". */
    fun isCommand(tool: String): Boolean = ToolLabels.action(tool, Words.EN) == Words.EN.toolAction(ToolKind.RUN)

    /**
     * The risk line's parts: the bold words from [CommandRisk] and what follows them (" inside kenai." or "."); the screen adds the
     * caution after. Null when no destructive shape is recognised (then only the caution shows; nothing claims the command is safe).
     */
    fun riskLine(command: String, project: String?, w: Words = Words.current()): Pair<String, String>? {
        val risk = CommandRisk.phrase(command, w) ?: return null
        val where = project?.trim().orEmpty()
        return risk to if (where.isEmpty()) "." else " " + w.riskWhere(where)
    }

    /** The calm card's sentence about the first busy agent, or null when nothing runs. */
    fun calm(sessions: List<SessionInfo>, nameOf: (String) -> String, w: Words = Words.current()): String? {
        val s = sessions.firstOrNull { it.state in BUSY } ?: return null
        return w.calmWorking(nameOf(s.pillId), s.project.orEmpty())
    }

    /** The computer's name for Inicio's strip: the demo's own name in the demo, else the paired name, else "Your computer". */
    fun computer(desktopName: String?, demo: Boolean, w: Words = Words.current()): String =
        if (demo) w.demoComputer else desktopName?.trim()?.takeIf { it.isNotEmpty() } ?: w.yourPc
}
