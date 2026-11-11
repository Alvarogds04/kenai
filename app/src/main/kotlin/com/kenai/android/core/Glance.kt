package com.kenai.android.core

import com.kenai.android.link.LinkState
import com.kenai.android.link.SessionInfo
import com.kenai.android.character.BotState

/** One agent in a glance: its name and one plain line. No command, no path, no answer text. */
data class GlanceLine(val agent: String, val text: String, val state: BotState)

/** How loud a glance is: picks the dot colour of the widget, the tile state and the notification accent. */
enum class GlanceTone(val argb: Int) {
    CALM(0xFF8E8E93.toInt()),
    OK(0xFF34C759.toInt()),
    BUSY(0xFF0A84FF.toInt()),
    ATTENTION(0xFFFF9F0A.toInt()),
    TROUBLE(0xFFFF453A.toInt()),
}

/**
 * What the small surfaces show without opening the app: the ongoing notification, the home-screen widget and the
 * quick-settings tile. One pure summary so the three always say the same thing. Only agent names and the plain-language
 * lines of [HomeText] go in: never a command, a path, a file name or a final answer, because a widget or a tile can be
 * seen without unlocking the phone.
 */
data class Glance(
    val headline: String,
    val detail: String,
    val tone: GlanceTone,
    val connected: Boolean,
    val lines: List<GlanceLine>,
    /** Requests and questions waiting for the user. */
    val waiting: Int,
)

object GlanceBuilder {
    const val MAX_LINES = 4
    private val BUSY = setOf(BotState.WORKING, BotState.THINKING, BotState.SEARCHING)
    private val TROUBLE = setOf(BotState.ERROR, BotState.RATELIMIT, BotState.DIZZY)

    /** Most urgent first; the same order as the Home list. */
    private val ORDER = listOf(
        BotState.APPROVAL, BotState.QUESTION, BotState.ERROR, BotState.RATELIMIT, BotState.DIZZY,
        BotState.WORKING, BotState.SEARCHING, BotState.THINKING, BotState.FINISHED, BotState.SLEEPING, BotState.IDLE,
    )

    /** Where a state sits in the list, most urgent first; unknown states go last. */
    fun rank(state: BotState): Int = ORDER.indexOf(state).let { i -> if (i < 0) ORDER.size else i }

    fun build(
        paired: Boolean,
        link: LinkState,
        desktop: String?,
        sessions: List<SessionInfo>,
        approvals: Int,
        nameOf: (String) -> String,
    ): Glance {
        val w = Words.current()
        if (!paired) return Glance(w.notPaired, w.openToConnect, GlanceTone.CALM, false, emptyList(), 0)
        if (link != LinkState.CONNECTED) {
            val connecting = link == LinkState.CONNECTING
            return Glance(
                if (connecting) w.connecting else w.notConnected,
                if (connecting) w.reachingComputer else w.cannotReachComputer,
                GlanceTone.CALM, false, emptyList(), 0,
            )
        }
        val ranked = sessions.sortedBy { ORDER.indexOf(it.state).let { i -> if (i < 0) ORDER.size else i } }
        val lines = ranked.take(MAX_LINES).map { GlanceLine(nameOf(it.pillId), lineOf(it, w), it.state) }
        val questions = sessions.count { it.state == BotState.QUESTION }
        val waiting = approvals + questions
        val busy = sessions.count { it.state in BUSY }
        val trouble = sessions.count { it.state in TROUBLE }
        val where = desktop?.takeIf { it.isNotBlank() }
        return when {
            approvals > 0 -> Glance(w.waitingForYou, w.requestsToApprove(approvals), GlanceTone.ATTENTION, true, lines, waiting)
            questions > 0 -> Glance(w.waitingForAnswer, w.questionsFromAgents(questions), GlanceTone.ATTENTION, true, lines, waiting)
            trouble > 0 -> Glance(w.agentNeedsALook, w.agentsStopped(trouble), GlanceTone.TROUBLE, true, lines, 0)
            busy > 0 -> Glance(
                if (busy == 1) w.isWorking(nameOf(ranked.first { it.state in BUSY }.pillId)) else w.agentsWorking(busy),
                where?.let { w.onComputer(it) } ?: w.working,
                GlanceTone.BUSY, true, lines, 0,
            )
            sessions.any { it.state == BotState.FINISHED } -> Glance(
                w.allDone, where?.let { w.onComputer(it) } ?: w.finished, GlanceTone.OK, true, lines, 0,
            )
            else -> Glance(w.connected, where?.let { w.toComputer(it) } ?: w.nothingRunning, GlanceTone.OK, true, lines, 0)
        }
    }

    /** The plain line of a session: its state in a few words, never its step text or answer (those stay in the app). */
    fun lineOf(s: SessionInfo, w: Words = Words.current()): String = when (s.state) {
        BotState.APPROVAL -> w.waitingForApproval
        BotState.QUESTION -> w.hasAQuestion
        BotState.ERROR -> w.stoppedOnAnError
        BotState.RATELIMIT -> w.hitALimit
        BotState.DIZZY -> w.needsALook
        BotState.FINISHED -> w.finished
        BotState.SLEEPING, BotState.IDLE -> w.idle
        BotState.THINKING -> w.thinking
        BotState.SEARCHING -> w.searching
        else -> w.working // never the step text: it can hold a file name or a command
    }
}
