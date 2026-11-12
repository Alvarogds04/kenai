package com.kenai.android.core

import com.kenai.android.link.ApprovalRequest

/**
 * What the approval sheet shows and when. Pure, so each rule is a unit test.
 * The sheet rises over any screen while a request waits, until the user closes it; it comes back for
 * the next request, or from the "Review request" button on Home.
 */
object ApprovalSheetPlan {
    /** Taps in the first moments are ignored: the sheet must not catch a tap meant for what was under it. Same as the island's. */
    const val GUARD_MS = 600L

    /** The command box scrolls past this height instead of cutting the text: the whole command that was sent is always readable. */
    const val COMMAND_BOX_MAX_DP = 220

    /**
     * Allow from the phone is offered only when the whole command is on the phone. When the computer cut it (a flag it sends with the
     * request), the phone could show only the start of what would run, so the request is answered on the computer. Deny is always offered.
     */
    fun canAllowHere(r: ApprovalRequest): Boolean = !r.truncated

    /**
     * The request the sheet shows, or null. On every screen but Home: the first one the user has not closed. While Home shows
     * the first request as its hero ([heroShown], kenai-0.1 S9), the sheet never rises over it for that same request; it opens
     * only for another one the user asked to [review] ("Revisar" in "También te espera", or a tap on its notification).
     */
    fun next(approvals: List<ApprovalRequest>, closed: String?, heroShown: Boolean = false, review: String? = null): ApprovalRequest? =
        if (heroShown) approvals.drop(1).firstOrNull { it.fingerprint == review }
        else approvals.firstOrNull { it.fingerprint != closed }

    /** The request Home's hero shows: the oldest that waits. */
    fun hero(approvals: List<ApprovalRequest>): ApprovalRequest? = approvals.firstOrNull()

    /** "Wants to run a command": what kind of action it is, never the command itself. */
    fun action(tool: String): String = ToolLabels.action(tool)
}
