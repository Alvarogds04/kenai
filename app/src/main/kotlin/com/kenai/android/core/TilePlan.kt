package com.kenai.android.core

import com.kenai.android.character.BotState
import com.kenai.android.link.ApprovalRequest
import com.kenai.android.link.QuestionRequest

/** Where a tap on the quick-settings tile goes: what waits for the user, or Home. Never a decision. */
sealed interface TileTarget {
    /** The approval sheet, for this request. */
    data class Approval(val fingerprint: String) : TileTarget

    /** The question sheet of this session. */
    data class Question(val pillId: String) : TileTarget

    object Home : TileTarget
}

/**
 * What the quick-settings tile says under its name and where a tap on it goes (S2.7b). Pure, so each rule is a unit test.
 * Built only from the [Glance], so it says nothing the notification and the widgets do not: an agent name and a state word,
 * never a command, a path or a file name. On the lock screen it names no agent at all, like the notifications' public versions.
 */
object TilePlan {
    private val WAITS = setOf(BotState.APPROVAL, BotState.QUESTION)

    /**
     * The tile's second line. Not connected: the glance's own word. More than one waiting: "N waiting" ([waiting] formats it).
     * Otherwise the most urgent agent and its state, which is the waiting one when one waits. The lock screen shows the same
     * line (decided L17, option b): the agent's name and a state word, never a command or a file.
     */
    fun subtitle(g: Glance, waiting: (Int) -> String): String {
        if (!g.connected) return g.headline
        val top = g.lines.firstOrNull()
        return when {
            g.waiting > 1 -> waiting(g.waiting)
            // A request with no session in a waiting state yet: never pin the wait on an agent that is only working.
            g.waiting == 1 && (top == null || top.state !in WAITS) -> g.headline
            top == null -> g.headline
            else -> "${top.agent} · ${top.text}"
        }
    }

    /**
     * What a tap opens: the approval the sheet shows first, else the oldest question that reached the phone, else Home.
     * Not connected: Home, where the app says why.
     */
    fun target(g: Glance, approvals: List<ApprovalRequest>, questions: List<QuestionRequest>): TileTarget = when {
        !g.connected -> TileTarget.Home
        approvals.isNotEmpty() -> TileTarget.Approval(approvals.first().fingerprint)
        questions.isNotEmpty() -> TileTarget.Question(questions.first().pillId)
        else -> TileTarget.Home
    }
}
