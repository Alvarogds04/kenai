package com.kenai.android.core

import com.kenai.android.character.BotState
import com.kenai.android.link.SessionInfo

/**
 * KN-04: a `question` frame is a real request, whether or not the computer also sent a `sessions` row for that agent (the engine's
 * notices and its hook requests are separate sources). Home, the notification and the badge all read the session list, so a question
 * with no QUESTION row would be invisible: this gives it one. An approval row stays an approval; no pending question changes nothing.
 */
object SessionsWithQuestions {
    fun merge(sessions: List<SessionInfo>, questionPills: Set<String>, nowMs: Long): List<SessionInfo> {
        if (questionPills.isEmpty()) return sessions
        val known = sessions.map { it.pillId }.toSet()
        val patched = sessions.map { if (it.pillId in questionPills && it.state != BotState.APPROVAL) it.copy(state = BotState.QUESTION) else it }
        val added = questionPills.filter { it !in known }.map { SessionInfo(it, "", BotState.QUESTION, "", 0, 0, nowMs) }
        return patched + added
    }
}
