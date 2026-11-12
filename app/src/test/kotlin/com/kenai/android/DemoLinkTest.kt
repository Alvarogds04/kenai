package com.kenai.android

import com.kenai.android.link.ApprovalRequest
import com.kenai.android.link.DemoLink
import com.kenai.android.link.LinkListener
import com.kenai.android.link.LinkState
import com.kenai.android.link.SessionInfo
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The demo's texts follow the phone's language (kenai-0.1 S6, «todo en español»): its status lines and its computer's name are
 * Spanish on a Spanish phone and exactly as before in English. The language is read at each step, so a change applies at the next
 * one. Agent names and the command stay as they are. The demo's own behaviour is pinned in link/DemoLinkTest.
 */
class DemoLinkTest {
    private lateinit var saved: Locale

    @Before fun remember() { saved = Locale.getDefault() }

    @After fun restore() = Locale.setDefault(saved)

    private class Rec : LinkListener {
        val sessions = mutableListOf<List<SessionInfo>>()
        val approvals = mutableListOf<ApprovalRequest>()
        val welcome = mutableListOf<String>()
        val resolved = mutableListOf<String>()
        override fun onApprovalResolved(fingerprint: String) { resolved.add(fingerprint) }
        override fun onState(state: LinkState) {}
        override fun onSessions(sessions: List<SessionInfo>) { this.sessions.add(sessions) }
        override fun onApproval(request: ApprovalRequest) { approvals.add(request) }
        override fun onWelcome(desktopName: String, os: String) { welcome.add(desktopName) }
    }

    private fun loop(rec: Rec): DemoLink = DemoLink(rec, autoRun = false).also { it.start(); repeat(DemoLink.STEPS) { _ -> it.step() } }

    private fun statuses(rec: Rec) = rec.sessions.flatten().map { "${it.agent}: ${it.statusText}" }

    @Test fun theDemoSpeaksSpanishOnASpanishPhone() {
        Locale.setDefault(Locale.forLanguageTag("es-AR"))
        val rec = Rec()
        loop(rec)
        assertEquals(
            listOf(
                "Claude Code: Leyendo el proyecto",
                "Claude Code: Editando archivos",
                "Codex: Buscando en el código",
                "Claude Code: Esperando tu aprobación",
                "Codex: Ejecutando los tests",
                "Claude Code: Compilando",
                "Codex: ¿Qué rama uso?",
                "Claude Code: Listo",
                "Codex: Fallaron los tests",
                "Codex: Llegó al límite",
                "Gemini CLI: En reposo",
            ),
            statuses(rec),
        )
        assertEquals(listOf("Computadora demo"), rec.welcome)
        // The command is the desktop's own text: it is never translated.
        assertEquals("npm run build", rec.approvals.single().command)
    }

    @Test fun theDemoStaysExactlyAsBeforeInEnglish() {
        Locale.setDefault(Locale.ENGLISH)
        val rec = Rec()
        loop(rec)
        assertEquals(
            listOf(
                "Claude Code: Reading the project",
                "Claude Code: Editing files",
                "Codex: Searching the code",
                "Claude Code: Waiting for your approval",
                "Codex: Running tests",
                "Claude Code: Building",
                "Codex: Which branch?",
                "Claude Code: Done",
                "Codex: Tests failed",
                "Codex: Rate limited",
                "Gemini CLI: Idle",
            ),
            statuses(rec),
        )
        assertEquals(listOf("Demo"), rec.welcome)
        assertEquals("npm run build", rec.approvals.single().command)
    }

    @Test fun theLanguageIsReadAtEachStepNotOnceAtStart() {
        Locale.setDefault(Locale.ENGLISH)
        val rec = Rec()
        val d = DemoLink(rec, autoRun = false).also { it.start() }
        d.step()
        Locale.setDefault(Locale.forLanguageTag("es-ES"))
        d.step()
        Locale.setDefault(Locale.US)
        d.step()
        assertEquals("Reading the project", rec.sessions[0].single().statusText)
        assertEquals(listOf("Editando archivos", "Buscando en el código"), rec.sessions[1].map { it.statusText })
        assertEquals(listOf("Waiting for your approval", "Running tests"), rec.sessions[2].map { it.statusText })
    }

    /**
     * The result screen (kenai-0.1 S9, T14) learns that a decision arrived only from the request being resolved: the demo resolves
     * it at once when decided, once, and an unanswered one at the end of the loop, exactly as before.
     */
    @Test fun aDecisionInTheDemoIsResolvedAtOnceAndOnlyOnce() {
        val rec = Rec()
        val d = DemoLink(rec, autoRun = false).also { it.start() }
        repeat(3) { d.step() }
        assertEquals(true, d.decide(DemoLink.FINGERPRINT, allow = false))
        assertEquals(listOf(DemoLink.FINGERPRINT), rec.resolved)
        assertEquals(false, d.decide(DemoLink.FINGERPRINT, allow = true))
        repeat(DemoLink.STEPS - 3) { d.step() }
        assertEquals("not resolved twice at the end of the loop", listOf(DemoLink.FINGERPRINT), rec.resolved)

        val idle = Rec()
        DemoLink(idle, autoRun = false).also { it.start(); repeat(DemoLink.STEPS) { _ -> it.step() } }
        assertEquals(listOf(DemoLink.FINGERPRINT), idle.resolved)
    }
}
