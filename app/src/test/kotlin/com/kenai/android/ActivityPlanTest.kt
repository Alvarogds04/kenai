package com.kenai.android

import com.kenai.android.character.BotState
import com.kenai.android.core.ActivityPlan
import com.kenai.android.core.GlanceBuilder
import com.kenai.android.core.WidgetData
import com.kenai.android.core.WidgetTone
import com.kenai.android.core.Words
import com.kenai.android.link.LinkState
import com.kenai.android.link.SessionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Sesiones y actividad» as plain data (KN-13 T3, S1-S4): the header and its badge, the rows with the engine's own progress, the pending
 * section the phone holds, the approximate finished-today line and the one calm line when there is nothing to list. Real engine values
 * only; nothing is estimated (S4) and the only phone-local number says it is approximate (S2).
 */
class ActivityPlanTest {
    private val names = mapOf("integration_claude" to "Claude Code", "agent_codex" to "Codex", "agent_gemini" to "Gemini CLI")
    private fun name(id: String) = names[id] ?: "An agent"
    private fun s(id: String, state: BotState, index: Int = 0, count: Int = 0) = SessionInfo(id, "", state, "", index, count, 0L)

    private fun content(
        sessions: List<SessionInfo> = emptyList(),
        requests: List<String> = emptyList(),
        questions: List<String> = emptyList(),
        today: Int = 0,
        link: LinkState = LinkState.CONNECTED,
    ) = WidgetData.build(
        GlanceBuilder.build(true, link, "PC", sessions, requests.size) { name(it) }, sessions, { name(it) },
        today = today, requests = requests, questions = questions,
    )

    private val working = listOf(s("integration_claude", BotState.WORKING), s("agent_codex", BotState.FINISHED), s("agent_gemini", BotState.IDLE))

    @Test fun theHeaderIsTheHeadlineAndItsBadgeIsWhatThePhoneHolds() {
        val c = content(listOf(s("integration_claude", BotState.APPROVAL), s("agent_gemini", BotState.QUESTION)), requests = listOf("integration_claude"), questions = listOf("agent_gemini"))
        val plan = ActivityPlan.of(c, ActivityPlan.ROWS_MEDIUM, Words.EN)
        assertEquals(c.headline, plan.headline)
        assertEquals("the phone holds two things for you", 2, plan.badge)
        assertNull("nothing waits: no badge", ActivityPlan.of(content(working), ActivityPlan.ROWS_MEDIUM, Words.EN).badge)
    }

    @Test fun theRowsComeMostUrgentFirstAndStopAtTheSizeLimit() {
        val many = listOf(
            s("p1", BotState.IDLE), s("p2", BotState.WORKING), s("p3", BotState.FINISHED),
            s("p4", BotState.ERROR), s("p5", BotState.THINKING), s("p6", BotState.SEARCHING),
        )
        assertEquals(ActivityPlan.ROWS_SMALL, ActivityPlan.of(content(many), ActivityPlan.ROWS_SMALL, Words.EN).rows.size)
        assertEquals(ActivityPlan.ROWS_MEDIUM, ActivityPlan.of(content(many), ActivityPlan.ROWS_MEDIUM, Words.EN).rows.size)
        assertEquals(ActivityPlan.ROWS_LARGE, ActivityPlan.of(content(many), ActivityPlan.ROWS_LARGE, Words.EN).rows.size)
        assertEquals("the five first, the most urgent first", listOf("p4", "p2", "p6", "p5", "p3"), ActivityPlan.of(content(many), ActivityPlan.ROWS_LARGE, Words.EN).rows.map { it.pillId })
        // A size nobody offers still cannot ask for more than there is or for a negative number.
        assertEquals(ActivityPlan.ROWS_LARGE, ActivityPlan.of(content(many), 99, Words.EN).rows.size)
        assertTrue(ActivityPlan.of(content(many), -1, Words.EN).rows.isEmpty())
    }

    @Test fun aRowCarriesTheEnginesProgressOnlyWhenItSentATotal() {
        val c = content(listOf(s("agent_codex", BotState.WORKING, 3, 8), s("integration_claude", BotState.WORKING)))
        val rows = ActivityPlan.of(c, ActivityPlan.ROWS_MEDIUM, Words.EN).rows
        assertEquals(listOf("3/8", null), rows.map { it.progress })
        assertEquals("Codex", rows.first().name)
        assertEquals(WidgetTone.WORKING, rows.first().tone)
    }

    @Test fun thePendingSectionIsThePhonesRowsWithItsButtonAndOnlyWhileSomethingWaits() {
        val waiting = content(
            listOf(s("integration_claude", BotState.APPROVAL), s("agent_gemini", BotState.QUESTION)),
            requests = listOf("integration_claude"), questions = listOf("agent_gemini"),
        )
        val plan = ActivityPlan.of(waiting, ActivityPlan.ROWS_MEDIUM, Words.EN)
        assertEquals(listOf("Claude Code" to "Waiting for your OK", "Gemini CLI" to "Has a question"), plan.pending.map { it.name to it.word })
        assertEquals("Review request", plan.button)
        val calm = ActivityPlan.of(content(working), ActivityPlan.ROWS_MEDIUM, Words.EN)
        assertTrue("nothing waits: no section", calm.pending.isEmpty())
        assertNull("and no button", calm.button)
    }

    @Test fun thePendingButtonSpeaksForTheQuestionWhenOnlyQuestionsWait() {
        val question = content(listOf(s("agent_gemini", BotState.QUESTION)), questions = listOf("agent_gemini"))
        assertEquals("Answer the question", ActivityPlan.of(question, ActivityPlan.ROWS_MEDIUM, Words.EN).button)
        assertEquals("Responder la pregunta", ActivityPlan.of(question, ActivityPlan.ROWS_MEDIUM, Words.ES).button)
    }

    @Test fun thePendingSectionKeepsRoomForTheRows() {
        val waiting = content(
            listOf(s("a", BotState.APPROVAL), s("b", BotState.APPROVAL), s("c", BotState.QUESTION)),
            requests = listOf("a", "b"), questions = listOf("c"),
        )
        assertEquals(1, ActivityPlan.of(waiting, ActivityPlan.ROWS_SMALL, Words.EN).pending.size)
        assertEquals(2, ActivityPlan.of(waiting, ActivityPlan.ROWS_MEDIUM, Words.EN).pending.size)
        assertEquals(WidgetData.ALERT_ROWS, ActivityPlan.of(waiting, ActivityPlan.ROWS_LARGE, Words.EN).pending.size)
    }

    @Test fun theFinishedTodayLineIsApproximateAndOnlyOnceSomethingFinished() {
        assertNull(ActivityPlan.of(content(working), ActivityPlan.ROWS_MEDIUM, Words.EN).today)
        assertEquals("3 finished today · approx., this phone", ActivityPlan.of(content(working, today = 3), ActivityPlan.ROWS_MEDIUM, Words.EN).today)
        assertEquals("3 terminadas hoy · aprox., este teléfono", ActivityPlan.of(content(working, today = 3), ActivityPlan.ROWS_MEDIUM, Words.ES).today)
    }

    @Test fun noRowIsEverAPercentageOrAnEstimate() {
        val c = content(listOf(s("agent_codex", BotState.WORKING, 3, 8)), today = 2)
        val plan = ActivityPlan.of(c, ActivityPlan.ROWS_MEDIUM, Words.EN)
        assertFalse("the rows are counts, never a rate", plan.rows.toString().contains("%"))
        // The one number this phone counted says so; nothing else does.
        assertTrue(plan.today!!.contains("approx."))
        assertTrue(plan.today!!.contains("this phone"))
    }

    @Test fun theCalmLineComesOnlyWithNoRowsAndSaysWhy() {
        assertEquals("No agents running", ActivityPlan.of(content(), ActivityPlan.ROWS_MEDIUM, Words.EN).emptyLine)
        assertEquals("Waiting for your computer", ActivityPlan.of(content(link = LinkState.DISCONNECTED), ActivityPlan.ROWS_MEDIUM, Words.EN).emptyLine)
        assertNull("with rows there is nothing to explain", ActivityPlan.of(content(working), ActivityPlan.ROWS_MEDIUM, Words.EN).emptyLine)
    }

    @Test fun theCardTakesTheContentsLook() {
        assertEquals(WidgetTone.ATTENTION, ActivityPlan.of(content(listOf(s("agent_gemini", BotState.QUESTION)), questions = listOf("agent_gemini")), ActivityPlan.ROWS_MEDIUM, Words.EN).tone)
        assertEquals(WidgetTone.WORKING, ActivityPlan.of(content(working), ActivityPlan.ROWS_MEDIUM, Words.EN).tone)
        assertEquals(WidgetTone.MUTED, ActivityPlan.of(content(link = LinkState.DISCONNECTED), ActivityPlan.ROWS_MEDIUM, Words.EN).tone)
        assertNotNull(ActivityPlan.of(content(working), ActivityPlan.ROWS_MEDIUM, Words.EN).rows)
    }
}
