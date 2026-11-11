package com.kenai.android

import com.kenai.android.character.BotState
import com.kenai.android.core.Glance
import com.kenai.android.core.GlanceBuilder
import com.kenai.android.core.TilePlan
import com.kenai.android.core.TileTarget
import com.kenai.android.link.ApprovalRequest
import com.kenai.android.link.LinkState
import com.kenai.android.link.QuestionRequest
import com.kenai.android.link.SessionInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The quick-settings tile: its second line says what waits, and a tap opens it (S2.7b). Never a decision from the tile. */
class TilePlanTest {
    private fun s(id: String, state: BotState, status: String = "rm -rf build/ secret.txt") = SessionInfo(id, "", state, status, 0, 0, 0L)
    private val names = mapOf("a" to "Claude Code", "b" to "Codex", "c" to "Gemini")
    private fun glance(
        sessions: List<SessionInfo> = emptyList(), approvals: Int = 0, link: LinkState = LinkState.CONNECTED, paired: Boolean = true,
    ): Glance = GlanceBuilder.build(paired, link, "Léa's PC", sessions, approvals) { names[it] ?: "An agent" }
    private val waiting: (Int) -> String = { "$it waiting" }
    private fun sub(g: Glance) = TilePlan.subtitle(g, waiting)
    private fun approval(pill: String, fp: String) = ApprovalRequest(pill, fp, "Bash", "rm -rf build/", 0L)
    private fun question(pill: String, fp: String) = QuestionRequest(pill, fp, emptyList(), 0L)

    // ── The subtitle ───────────────────────────────────────────────────────────────────────────────

    @Test fun moreThanOneWaitingIsACount() {
        val g = glance(listOf(s("a", BotState.APPROVAL), s("b", BotState.QUESTION), s("c", BotState.WORKING)), approvals = 1)
        assertEquals("2 waiting", sub(g))
        assertEquals("3 waiting", sub(glance(listOf(s("a", BotState.APPROVAL), s("b", BotState.APPROVAL), s("c", BotState.QUESTION)), approvals = 2)))
    }

    @Test fun oneWaitingNamesTheAgentAndWhatItWaitsFor() {
        assertEquals("Codex · Waiting for your approval", sub(glance(listOf(s("a", BotState.WORKING), s("b", BotState.APPROVAL)), approvals = 1)))
        assertEquals("Gemini · Has a question", sub(glance(listOf(s("a", BotState.FINISHED), s("c", BotState.QUESTION)))))
    }

    @Test fun anApprovalWithNoWaitingSessionFallsBackToTheHeadline() {
        // The request arrived before the session said so: never pin the wait on an agent that is only working.
        assertEquals("Waiting for you", sub(glance(listOf(s("a", BotState.WORKING)), approvals = 1)))
    }

    @Test fun nothingWaitingShowsTheMostUrgentAgent() {
        assertEquals("Codex · Stopped on an error", sub(glance(listOf(s("a", BotState.WORKING), s("b", BotState.ERROR), s("c", BotState.IDLE)))))
        assertEquals("Claude Code · Working", sub(glance(listOf(s("a", BotState.WORKING), s("c", BotState.IDLE)))))
        assertEquals("no agent at all: the glance's own word", "Connected", sub(glance()))
    }

    @Test fun notConnectedSaysSo() {
        assertEquals("Not connected", sub(glance(listOf(s("a", BotState.APPROVAL)), approvals = 1, link = LinkState.DISCONNECTED)))
        assertEquals("Connecting…", sub(glance(link = LinkState.CONNECTING)))
        assertEquals("Not paired", sub(glance(paired = false)))
    }

    @Test fun onTheLockScreenTheLineIsTheSameAsUnlocked() {
        // Decided by the human (L17, option b): the locked tile names the agent too; still never a command or a file.
        val cases = listOf(
            glance(listOf(s("b", BotState.APPROVAL)), approvals = 1),
            glance(listOf(s("a", BotState.QUESTION), s("b", BotState.QUESTION))),
            glance(listOf(s("a", BotState.WORKING), s("b", BotState.ERROR))),
            glance(listOf(s("b", BotState.WORKING))),
            glance(),
            glance(link = LinkState.DISCONNECTED),
        )
        for (g in cases) assertFalse(sub(g), sub(g).isBlank())
        val one = glance(listOf(s("b", BotState.APPROVAL)), approvals = 1)
        assertTrue(sub(one), names.values.any { sub(one).contains(it) })
    }

    @Test fun neverACommandOrAFile() {
        val sessions = listOf(s("a", BotState.APPROVAL), s("b", BotState.WORKING))
        for (text in listOf(sub(glance(sessions, approvals = 1)), sub(glance(sessions.drop(1))))) {
            assertFalse(text, text.contains("rm -rf"))
            assertFalse(text, text.contains("secret.txt"))
        }
    }

    // ── Where a tap goes ───────────────────────────────────────────────────────────────────────────

    @Test fun aWaitingApprovalIsOpenedFirst() {
        val g = glance(listOf(s("a", BotState.QUESTION), s("b", BotState.APPROVAL)), approvals = 2)
        val target = TilePlan.target(g, listOf(approval("b", "fp-1"), approval("c", "fp-2")), listOf(question("a", "q-1")))
        assertEquals("the one the approval sheet shows first", TileTarget.Approval("fp-1"), target)
    }

    @Test fun aQuestionAloneOpensItsSession() {
        val g = glance(listOf(s("a", BotState.QUESTION), s("c", BotState.QUESTION)))
        assertEquals(TileTarget.Question("a"), TilePlan.target(g, emptyList(), listOf(question("a", "q-1"), question("c", "q-2"))))
    }

    @Test fun nothingWaitingOpensHome() {
        assertEquals(TileTarget.Home, TilePlan.target(glance(listOf(s("a", BotState.WORKING))), emptyList(), emptyList()))
        // A session that says "question" but whose question has not reached the phone: nothing to open, so Home.
        assertEquals(TileTarget.Home, TilePlan.target(glance(listOf(s("a", BotState.QUESTION))), emptyList(), emptyList()))
    }

    @Test fun notConnectedOpensHome() {
        val g = glance(link = LinkState.DISCONNECTED)
        assertEquals(TileTarget.Home, TilePlan.target(g, listOf(approval("b", "fp-1")), listOf(question("a", "q-1"))))
    }

    // ── The tile's source ──────────────────────────────────────────────────────────────────────────

    private val tile get() = File("src/main/kotlin/com/kenai/android/app/GlanceTile.kt").readText()

    @Test fun theTileAsksThePlanForItsLineAndItsTarget() {
        assertTrue(tile.contains("TilePlan.subtitle("))
        assertTrue(tile.contains("TilePlan.target("))
        assertTrue(tile.contains("R.string.tile_waiting"))
        assertFalse("the locked tile shows the same line (L17 option b)", tile.contains("isLocked"))
        assertFalse("its second line is no longer the bare headline", tile.contains("subtitle = g.headline"))
    }

    @Test fun theTileGoesThroughTheNonExportedActivity() {
        assertTrue(tile.contains("LaunchActivity::class.java"))
        assertFalse("never straight to the exported activity", tile.contains("MainActivity::class.java"))
        assertTrue(tile.contains("LaunchActivity.EXTRA_QUESTION"))
        assertTrue(tile.contains("LaunchActivity.EXTRA_SCREEN"))
        assertTrue(tile.contains("LaunchScreen.HOME.key"))
        assertTrue(tile.contains("Notifications.EXTRA_FP"))
    }

    @Test fun theTileNeverDecidesAnything() {
        for (word in listOf("decide", "allow", "deny", "answer", "approve(", "unpair", "send(")) {
            assertFalse("tile uses $word", tile.contains(word, ignoreCase = true))
        }
    }
}
