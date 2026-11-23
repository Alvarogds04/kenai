package com.kenai.android

import com.kenai.android.character.BotState
import com.kenai.android.core.SessionsWithQuestions
import com.kenai.android.link.SessionInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/** KN-04 live probe: the engine sends a `question` frame with no `sessions` row for that agent; Kenai must still show it. */
class SessionsWithQuestionsTest {
    private fun s(id: String, st: BotState) = SessionInfo(id, id, st, "", 0, 0, 5L)

    @Test fun aQuestionWithoutASessionRowGetsOne() {
        val out = SessionsWithQuestions.merge(emptyList(), setOf("agent_pi"), nowMs = 99L)
        assertEquals(1, out.size)
        assertEquals("agent_pi", out[0].pillId)
        assertEquals(BotState.QUESTION, out[0].state)
        assertEquals(99L, out[0].updatedAtMs)
    }

    @Test fun anExistingRowOfThatAgentTurnsIntoAQuestion() {
        val out = SessionsWithQuestions.merge(listOf(s("a", BotState.WORKING), s("b", BotState.IDLE)), setOf("a"), nowMs = 1L)
        assertEquals(listOf("a", "b"), out.map { it.pillId })
        assertEquals(BotState.QUESTION, out[0].state)
        assertEquals(BotState.IDLE, out[1].state)
    }

    @Test fun anApprovalRowIsNotDowngradedAndNoQuestionsChangesNothing() {
        val list = listOf(s("a", BotState.APPROVAL), s("b", BotState.WORKING))
        assertEquals(BotState.APPROVAL, SessionsWithQuestions.merge(list, setOf("a"), 1L)[0].state)
        assertEquals(list, SessionsWithQuestions.merge(list, emptySet(), 1L))
    }
}
