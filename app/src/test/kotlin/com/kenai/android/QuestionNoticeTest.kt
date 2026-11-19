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
}
