package com.kenai.android

import com.kenai.android.core.HomeText
import com.kenai.android.core.ToolLabels
import com.kenai.android.core.Words
import com.kenai.android.link.SessionInfo
import com.kenai.android.character.BotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTextTest {
    private fun s(state: BotState, status: String = "", final: String? = null) =
        SessionInfo("p", "A", state, status, 0, 0, 0L, finalLine = final)

    @Test fun aRawToolNameBecomesASentence() {
        assertEquals("Running a command", HomeText.line(s(BotState.WORKING, "Bash")))
        assertEquals("Editing files", HomeText.line(s(BotState.WORKING, "Edit · a.txt")))
    }

    @Test fun aFinishedAgentShowsItsLastMessage() {
        assertEquals("Migration applied.", HomeText.line(s(BotState.FINISHED, "Bash", "Migration applied.")))
        assertEquals("Running a command", HomeText.line(s(BotState.FINISHED, "Bash", "  ")))
    }

    @Test fun onlyAFinishedAgentShowsALastMessage() {
        assertEquals("Reading files", HomeText.line(s(BotState.WORKING, "Read", "old message")))
    }

    @Test fun nothingToSayIsNull() {
        assertNull(HomeText.line(s(BotState.IDLE)))
    }

    @Test fun requestsReadAsActions() {
        assertEquals("run a command", ToolLabels.action("Bash"))
        assertEquals("edit files", ToolLabels.action("multi_edit"))
        assertEquals("search the web", ToolLabels.action("WebSearch"))
        assertEquals("list issues", ToolLabels.action("list_issues"))
        assertEquals("do something", ToolLabels.action(" "))
    }

    @Test fun theRecentListSaysWhatYouDecided() {
        assertEquals("Allowed: edit files", HomeText.recent(true, "Edit"))
        assertEquals("Denied: run a command", HomeText.recent(false, "Bash"))
    }

    @Test fun runningCountsBusyAgentsOnly() {
        val all = listOf(s(BotState.WORKING), s(BotState.THINKING), s(BotState.SEARCHING), s(BotState.IDLE), s(BotState.APPROVAL), s(BotState.FINISHED))
        assertEquals(3, HomeText.running(all))
        assertEquals(0, HomeText.running(emptyList()))
    }

    @Test fun aDecisionIsASentenceWithNoCommand() {
        assertEquals("You allowed Claude Code to run a command", HomeText.decision(true, "Claude Code", "Bash"))
        assertEquals("You denied Codex to edit files", HomeText.decision(false, "Codex", "Edit"))
        assertEquals("You allowed an agent to read files", HomeText.decision(true, " ", "Read"))
    }

    // ── Inicio with a request as the hero (kenai-0.1 S9, T13) ──────────────────────────────────────

    @Test fun theRequestSaysWhatItWantsAndWhereWhenTheProjectIsKnown() {
        assertEquals("Quiere ejecutar un comando en kenai", HomeText.wants("Bash", "kenai", Words.ES))
        assertEquals("Quiere ejecutar un comando", HomeText.wants("Bash", null, Words.ES))
        assertEquals("Quiere editar archivos", HomeText.wants("Edit", "  ", Words.ES))
        assertEquals("Wants to edit files in app", HomeText.wants("Edit", "app", Words.EN))
        assertEquals("Wants to run a command", HomeText.wants("Bash", null, Words.EN))
    }

    @Test fun onlyShellToolsAreCommands() {
        assertTrue(HomeText.isCommand("Bash"))
        assertTrue(HomeText.isCommand("run_shell_command"))
        assertFalse(HomeText.isCommand("Edit"))
        assertFalse(HomeText.isCommand(""))
    }

    @Test fun theRiskLineIsBoldWordsAndWhereOrNothing() {
        assertEquals("Borra archivos" to " dentro de kenai.", HomeText.riskLine("rm -rf app/build && ./gradlew assembleRelease", "kenai", Words.ES))
        assertEquals("Deletes files" to ".", HomeText.riskLine("rm -rf build", null, Words.EN))
        assertNull(HomeText.riskLine("npm test", "kenai", Words.ES))
    }

    @Test fun theCalmCardNamesWhoIsWorkingAndWhere() {
        val name = { id: String -> if (id == "c") "Codex" else "Claude Code" }
        val working = SessionInfo("c", "Codex", BotState.WORKING, "", 0, 0, 0L, project = "kenai-desktop")
        assertEquals("Codex está trabajando en kenai-desktop. Te aviso apenas alguien necesite tu sí.", HomeText.calm(listOf(s(BotState.IDLE), working), name, Words.ES))
        assertEquals("Codex is working. I'll tell you as soon as someone needs your yes.", HomeText.calm(listOf(working.copy(project = null)), name, Words.EN))
        assertNull("nothing runs: the screen says how to start one", HomeText.calm(listOf(s(BotState.IDLE), s(BotState.FINISHED)), name, Words.ES))
    }

    @Test fun theComputerIsNamedOrTheDemo() {
        assertEquals("Alvaro PC", HomeText.computer("Alvaro PC", demo = false, w = Words.ES))
        assertEquals("Computadora demo", HomeText.computer(null, demo = true, w = Words.ES))
        assertEquals("Tu computadora", HomeText.computer(" ", demo = false, w = Words.ES))
    }
}
