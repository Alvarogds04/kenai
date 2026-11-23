package com.kenai.android

import com.kenai.android.character.BotState
import com.kenai.android.core.StatusPolicy
import com.kenai.android.core.UserSettings
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** KN-04 live finding: a question from an agent Kenai had not seen yet raised no notification (previous state null = «already there»). */
class QuestionNoticeTest {
    @Test fun aQuestionOfAnAgentSeenForTheFirstTimeIsStillAnnouncedOnceItHasAPriorState() {
        val s = UserSettings()
        assertFalse("null previous means the state was already there when the link came: quiet", StatusPolicy.announce(null, BotState.QUESTION, s, 600, false).notify)
        assertTrue(StatusPolicy.announce(BotState.IDLE, BotState.QUESTION, s, 600, false).notify)
    }

    @Test fun aNewQuestionFrameSeedsThePriorStateOfItsAgent() {
        val model = File("src/main/kotlin/com/kenai/android/app/AppModel.kt").readText()
        val onQuestion = model.substringAfter("override fun onQuestion(").substringBefore("/** The phone's 120 s")
        assertTrue(
            "a question frame is news even for an agent the phone has no state for",
            onQuestion.contains("lastState.putIfAbsent(request.pillId, BotState.IDLE)") && onQuestion.indexOf("putIfAbsent") < onQuestion.indexOf("announceSessions()"),
        )
    }

    // ── Live finding: a question's status notice outlived its question ────────────────────────────

    @Test fun theStatusNoticeOfAQuestionGoesWhenTheQuestionDoes() {
        // The notice says «Tiene una pregunta» and offers «Responder»: once the question is gone (answered, expired, refused, link
        // dropped) its «Responder» would land on Home for nothing, so the notice goes with it.
        val model = File("src/main/kotlin/com/kenai/android/app/AppModel.kt").readText()
        val remove = model.substringAfter("private fun removeQuestion(").substringBefore("/** One short buzz")
        assertTrue("the agent of the question is known before it is forgotten", remove.contains("questions.firstOrNull { it.fingerprint == fingerprint }?.pillId"))
        assertTrue("and its notice is cancelled", remove.contains("notifier.cancelStatus(pillId)"))
        assertTrue(
            "unless the same agent is still asking something else",
            remove.contains("questions.none { it.pillId == pillId }"),
        )
        // The wipe (link lost, unlinked) takes the notices of every question it clears, not just the cards.
        val drop = model.substringAfter("private fun dropApprovals()").substringBefore("private fun removeApproval")
        assertTrue("one notice per question pill goes", drop.contains("notifier.cancelStatus("))
    }

    @Test fun aQuestionResolvedElsewhereAlsoTakesItsNotice() {
        // `approvalResolved` and a refusal that says the request is gone both end in removeQuestion, so one rule covers them.
        val model = File("src/main/kotlin/com/kenai/android/app/AppModel.kt").readText()
        assertTrue(model.substringAfter("override fun onApprovalResolved(").substringBefore("override fun onDecisionNotSent(").contains("removeQuestion(fingerprint)"))
        assertTrue(model.substringAfter("private fun answerRefused(").substringBefore("private fun refusalSentence").contains("removeQuestion(it)"))
    }

    // Native review R3-001/R4 (19 Nov): the second question of an agent with no `sessions` row went unannounced, because the first one left
    // QUESTION in lastState and the synthetic row vanished without the announcer ever seeing it go.
    @Test fun anAgentWithNoSessionsRowForgetsItsQuestionStateWhenTheQuestionGoes() {
        val model = File("src/main/kotlin/com/kenai/android/app/AppModel.kt").readText()
        val remove = model.substringAfter("private fun removeQuestion(").substringBefore("/** One short buzz")
        assertTrue(
            "the prior state of an agent the computer never listed is dropped, so its next question is news again",
            remove.contains("sentSessions.none { it.pillId == pillId }") && remove.contains("lastState.remove(pillId)"),
        )
        assertTrue("and the island follows the list", remove.contains("refreshIsland()"))
        val drop = model.substringAfter("private fun dropApprovals()").substringBefore("private fun removeApproval")
        assertTrue("a wiped question forgets its state too", drop.contains("lastState.remove("))
    }
}
