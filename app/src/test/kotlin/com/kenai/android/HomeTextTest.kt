package com.kenai.android

import com.kenai.android.core.HomeText
import com.kenai.android.core.Outcome
import com.kenai.android.core.OutcomeKind
import com.kenai.android.core.ToolLabels
import com.kenai.android.core.Words
import com.kenai.android.link.SessionInfo
import com.kenai.android.character.BotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

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

    // ── The result of a decision (kenai-0.1 S9, screen 3; T14) ──────────────────────────────────────

    private val at1441 = 14 * 3_600_000L + 41 * 60_000L
    private val at1445 = 14 * 3_600_000L + 45 * 60_000L

    private fun o(kind: OutcomeKind, allow: Boolean?, tool: String = "Bash", buzzes: Int = 0) =
        Outcome("fp", kind, allow, "p", tool, sinceMs = 0, atWallMs = at1441, expiresAtWallMs = at1445, buzzes = buzzes)

    @Test fun theResultSaysWhatYouDecidedWithTheActionsOwnWords() {
        val es = Words.ES
        assertEquals("Enviando tu decisión", HomeText.resultTitle(o(OutcomeKind.SENDING, true), es))
        assertEquals("Permitiste ejecutar un comando", HomeText.resultTitle(o(OutcomeKind.ALLOWED, true), es))
        assertEquals("Denegaste ejecutar un comando", HomeText.resultTitle(o(OutcomeKind.DENIED, false), es))
        assertEquals("Permitiste editar archivos", HomeText.resultTitle(o(OutcomeKind.ALLOWED, true, tool = "Edit"), es))
        assertEquals("Ese pedido ya no está pendiente", HomeText.resultTitle(o(OutcomeKind.EXPIRED, null), es))
        assertEquals("Tu decisión no llegó", HomeText.resultTitle(o(OutcomeKind.FAILED, true), es))
        assertEquals("You let it edit files", HomeText.resultTitle(o(OutcomeKind.ALLOWED, true, tool = "Edit"), Words.EN))
    }

    @Test fun theResultsBodyIsHonestAboutWhatReachedTheComputer() {
        val es = Words.ES
        val utc = ZoneOffset.UTC
        assertEquals(
            "Kenai espera que tu computadora confirme que la recibió. No cierres nada.",
            HomeText.resultBody(o(OutcomeKind.SENDING, true), "Claude Code", "kenai", utc, es),
        )
        assertEquals("Claude Code sigue trabajando en kenai.", HomeText.resultBody(o(OutcomeKind.ALLOWED, true), "Claude Code", "kenai", utc, es))
        assertEquals("Claude Code sigue trabajando.", HomeText.resultBody(o(OutcomeKind.ALLOWED, true), "Claude Code", null, utc, es))
        assertEquals(
            "Claude Code no lo va a ejecutar. Si querés, explicale por qué en tu computadora.",
            HomeText.resultBody(o(OutcomeKind.DENIED, false), "Claude Code", "kenai", utc, es),
        )
        assertEquals(
            "Claude Code no lo va a hacer. Si querés, explicale por qué en tu computadora.",
            HomeText.resultBody(o(OutcomeKind.DENIED, false, tool = "Edit"), "Claude Code", "kenai", utc, es),
        )
        assertEquals(
            "Caducó a las 14:45 o se respondió en tu computadora. Desde el teléfono no se envió nada.",
            HomeText.resultBody(o(OutcomeKind.EXPIRED, null), "Claude Code", "kenai", utc, es),
        )
        // Sent, never confirmed, then expired: it does not claim nothing was sent.
        assertEquals(
            "Caducó a las 14:45 sin que tu computadora confirmara tu decisión.",
            HomeText.resultBody(o(OutcomeKind.EXPIRED, true), "Claude Code", "kenai", utc, es),
        )
        assertEquals(
            "Se perdió la conexión con tu computadora. El pedido sigue abierto: volver a intentar es seguro.",
            HomeText.resultBody(o(OutcomeKind.FAILED, true), "Claude Code", "kenai", utc, es),
        )
    }

    @Test fun theMetaLineSaysTheBuzzOnlyWhenItBuzzed() {
        val utc = ZoneOffset.UTC
        assertEquals("14:41 · vibró una vez · quedó en Actividad", HomeText.resultMeta(o(OutcomeKind.ALLOWED, true, buzzes = 1), utc, Words.ES))
        assertEquals("14:41 · vibró dos veces · quedó en Actividad", HomeText.resultMeta(o(OutcomeKind.DENIED, false, buzzes = 2), utc, Words.ES))
        assertEquals("14:41 · quedó en Actividad", HomeText.resultMeta(o(OutcomeKind.ALLOWED, true, buzzes = 0), utc, Words.ES))
        assertEquals("14:41 · buzzed once · saved in Activity", HomeText.resultMeta(o(OutcomeKind.ALLOWED, true, buzzes = 1), utc, Words.EN))
        for (k in listOf(OutcomeKind.SENDING, OutcomeKind.EXPIRED, OutcomeKind.FAILED)) assertNull(HomeText.resultMeta(o(k, true, buzzes = 1), utc, Words.ES))
    }

    @Test fun whatComesNextSaysTheQuestionAndWhere() {
        val q = SessionInfo("g", "Gemini CLI", BotState.QUESTION, "¿En qué rama?", 0, 0, 0L, project = "notes")
        // The question itself when the phone has it (the `answers` capability), else the state in words; then the project.
        assertEquals("¿En qué rama? · notes", HomeText.nextLine("¿En qué rama?", q, Words.ES))
        assertEquals("¿En qué rama?", HomeText.nextLine("¿En qué rama?", q.copy(project = null), Words.ES))
        assertEquals(Words.ES.hasAQuestion + " · notes", HomeText.nextLine(null, q, Words.ES))
        assertEquals("a path in the question is not shown", false, HomeText.nextLine("¿Borro /home/alvaro/secret?", q, Words.ES).contains("/home"))
    }
}
