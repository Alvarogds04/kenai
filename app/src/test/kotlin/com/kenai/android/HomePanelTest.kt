package com.kenai.android

import com.kenai.android.core.HomePanel
import com.kenai.android.core.Outcome
import com.kenai.android.core.OutcomeKind
import com.kenai.android.core.Words
import com.kenai.android.link.ApprovalRequest
import com.kenai.android.link.SessionInfo
import com.kenai.android.character.BotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePanelTest {
    private fun s(id: String, st: BotState) = SessionInfo(id, id, st, "", 0, 0, 0L)

    @Test fun theAgentThatNeedsYouMostIsInTheBigCard() {
        val list = listOf(s("a", BotState.WORKING), s("b", BotState.APPROVAL), s("c", BotState.ERROR), s("d", BotState.IDLE))
        assertEquals("b", HomePanel.focus(list, null)!!.pillId)
        assertEquals("c", HomePanel.focus(list.filter { it.pillId != "b" }, null)!!.pillId)
        assertEquals("a", HomePanel.focus(listOf(s("d", BotState.IDLE), s("a", BotState.WORKING)), null)!!.pillId)
    }

    @Test fun onATieTheFirstOneWins() {
        assertEquals("a", HomePanel.focus(listOf(s("a", BotState.WORKING), s("b", BotState.WORKING)), null)!!.pillId)
    }

    @Test fun aTappedAgentStaysInTheCardWhileItExists() {
        val list = listOf(s("a", BotState.APPROVAL), s("b", BotState.IDLE))
        assertEquals("b", HomePanel.focus(list, "b")!!.pillId)
        assertEquals("a", HomePanel.focus(list, "gone")!!.pillId)
    }

    @Test fun noAgentMeansNoFocus() {
        assertNull(HomePanel.focus(emptyList(), null))
        assertNull(HomePanel.focus(emptyList(), "a"))
    }

    @Test fun theOthersExcludeTheFocusAndKeepTheirOrder() {
        val list = listOf(s("a", BotState.IDLE), s("b", BotState.IDLE), s("c", BotState.IDLE), s("d", BotState.IDLE), s("e", BotState.IDLE))
        val others = HomePanel.others(list, list[0])
        assertEquals(listOf("b", "c", "d", "e"), others.map { it.pillId })
        assertEquals(5, HomePanel.others(list, null).size)
    }

    @Test fun theLinkAnswersARequestFirstThenPairsThenIsGone() {
        assertEquals(HomePanel.Link.APPROVAL, HomePanel.link(paired = true, demo = false, hasApproval = true))
        assertEquals(HomePanel.Link.APPROVAL, HomePanel.link(paired = false, demo = true, hasApproval = true))
        assertEquals(HomePanel.Link.PAIR, HomePanel.link(paired = false, demo = false, hasApproval = false))
        // Settings is no longer a link on the card (it has its own button): nothing to show.
        assertEquals(HomePanel.Link.NONE, HomePanel.link(paired = true, demo = false, hasApproval = false))
        assertEquals(HomePanel.Link.NONE, HomePanel.link(paired = false, demo = true, hasApproval = false))
    }

    @Test fun pillColoursBecomeCharacterBodies() {
        val c = HomePanel.rgb("#8AB4F8")!!
        assertEquals(0x8A / 255.0, c.r, 1e-9)
        assertEquals(0xB4 / 255.0, c.g, 1e-9)
        assertEquals(0xF8 / 255.0, c.b, 1e-9)
        assertNull(HomePanel.rgb("blue"))
        assertNull(HomePanel.rgb("#12"))
        assertNull(HomePanel.rgb("#GGGGGG"))
    }

    @Test fun theCardOpensOnlyWhenThereIsSomethingToShow() {
        assertTrue(!HomePanel.hasDetails(s("a", BotState.IDLE)))
        assertTrue(HomePanel.hasDetails(s("a", BotState.IDLE).copy(steps = listOf("x"))))
        assertTrue(HomePanel.hasDetails(s("a", BotState.IDLE).copy(finalLine = "done")))
        assertTrue(HomePanel.hasDetails(s("a", BotState.IDLE).copy(project = "app")))
        assertTrue(!HomePanel.hasDetails(s("a", BotState.IDLE).copy(finalLine = "  ", color = "#112233")))
    }

    @Test fun theUsersColourBeatsTheCatalogsAndTheCatalogIsTheFallback() {
        assertEquals("#2DD4BF", HomePanel.colorHex(s("agent_gemini", BotState.IDLE).copy(color = "#2DD4BF")))
        assertEquals("#8AB4F8", HomePanel.colorHex(s("agent_gemini", BotState.IDLE)))
        assertNull(HomePanel.colorHex(s("not_a_pill", BotState.IDLE)))
    }

    @Test fun stepsAreListedNewestFirst() {
        assertEquals(listOf("c", "b", "a"), HomePanel.stepsNewestFirst(s("a", BotState.IDLE).copy(steps = listOf("a", "b", "c"))))
    }

    // ── Inicio with a request as the hero (kenai-0.1 S9, T13) ──────────────────────────────────────

    private fun r(pill: String, fp: String = pill) = ApprovalRequest(pill, fp, "Bash", "npm test", 0L)
    private val names = mapOf("a" to "Claude Code", "b" to "Gemini CLI", "c" to "Codex", "d" to "Aider")
    private fun name(id: String) = names[id] ?: "An agent"

    @Test fun homeHasFourScenesAndOfflineWinsBecauseNothingCanBeAnswered() {
        val q = listOf(s("b", BotState.QUESTION))
        assertEquals(HomePanel.Scene.OFFLINE, HomePanel.scene(paired = true, connected = false, approvals = 0, sessions = q))
        assertEquals(HomePanel.Scene.REQUEST, HomePanel.scene(paired = true, connected = true, approvals = 1, sessions = q))
        assertEquals("the demo is never offline", HomePanel.Scene.REQUEST, HomePanel.scene(paired = false, connected = false, approvals = 1, sessions = emptyList()))
        assertEquals(HomePanel.Scene.WAITING, HomePanel.scene(paired = true, connected = true, approvals = 0, sessions = q))
        assertEquals(HomePanel.Scene.CALM, HomePanel.scene(paired = true, connected = true, approvals = 0, sessions = listOf(s("c", BotState.WORKING))))
        assertEquals(HomePanel.Scene.CALM, HomePanel.scene(paired = false, connected = false, approvals = 0, sessions = emptyList()))
    }

    @Test fun theOthersAreSplitIntoWaitingRunningAndTheRest() {
        val list = listOf(
            s("a", BotState.APPROVAL), s("b", BotState.QUESTION), s("c", BotState.WORKING), s("d", BotState.FINISHED),
            s("e", BotState.APPROVAL), s("f", BotState.THINKING), s("g", BotState.ERROR), s("h", BotState.SEARCHING),
        )
        val g = HomePanel.groups(list, heroPillId = "a")
        assertEquals("the hero's agent is not listed again", listOf("b", "e"), g.waiting.map { it.pillId })
        assertEquals(listOf("c", "f", "h"), g.running.map { it.pillId })
        assertEquals(listOf("d", "g"), g.rest.map { it.pillId })
        assertEquals(listOf("a", "b", "e"), HomePanel.groups(list, heroPillId = null).waiting.map { it.pillId })
        // An agent still waiting on the computer for a request the phone does not hold (decided, or not arrived) is not «waiting for you».
        val held = HomePanel.groups(list, heroPillId = "a", requestPills = setOf("a"))
        assertEquals(listOf("b"), held.waiting.map { it.pillId })
        assertEquals(listOf("d", "e", "g"), held.rest.map { it.pillId })
        val empty = HomePanel.groups(emptyList(), null)
        assertTrue(empty.waiting.isEmpty() && empty.running.isEmpty() && empty.rest.isEmpty())
    }

    @Test fun theBadgeCountsRequestsAndQuestions() {
        val list = listOf(s("a", BotState.APPROVAL), s("b", BotState.QUESTION), s("c", BotState.QUESTION), s("d", BotState.WORKING))
        assertEquals(3, HomePanel.waitingCount(1, list))
        assertEquals(0, HomePanel.waitingCount(0, listOf(s("d", BotState.WORKING))))
        assertEquals(2, HomePanel.waitingCount(2, emptyList()))
    }

    @Test fun aRequestExpiresTwoMinutesAfterItArrivedOnThisPhone() {
        assertEquals(120_000L, HomePanel.remainingMs(arrivedMs = 1_000, nowMs = 1_000))
        assertEquals(60_000L, HomePanel.remainingMs(arrivedMs = 1_000, nowMs = 61_000))
        assertEquals("never below zero", 0L, HomePanel.remainingMs(arrivedMs = 0, nowMs = 500_000))
        assertEquals("a clock set back never adds time", 120_000L, HomePanel.remainingMs(arrivedMs = 10_000, nowMs = 0))
        assertEquals("2:00", HomePanel.clock(120_000))
        assertEquals("rounded up: it still says 2:00 in the first second", "2:00", HomePanel.clock(119_001))
        assertEquals("1:59", HomePanel.clock(119_000))
        assertEquals("0:05", HomePanel.clock(4_200))
        assertEquals("0:00", HomePanel.clock(0))
        assertEquals("Caduca en 1:30", HomePanel.expiry(90_000, Words.ES))
        assertEquals("Expires in 2:00", HomePanel.expiry(120_000, Words.EN))
        assertEquals(1f, HomePanel.expiryFraction(120_000), 1e-6f)
        assertEquals(0.25f, HomePanel.expiryFraction(30_000), 1e-6f)
        assertEquals(0f, HomePanel.expiryFraction(-5), 1e-6f)
    }

    @Test fun theStripSaysWhoWaitsOrThatAllIsCalmOrThatTheComputerIsAway() {
        val list = listOf(s("a", BotState.APPROVAL), s("b", BotState.QUESTION), s("c", BotState.WORKING))
        val req = HomePanel.strip(HomePanel.Scene.REQUEST, "Alvaro PC", list, listOf(r("a")), ::name, lostAtMs = null, connecting = false, nowMs = 0, w = Words.ES)
        assertEquals(HomePanel.Tone.WAITING, req.tone)
        assertEquals("Claude Code espera tu sí", req.title)
        assertEquals("Alvaro PC · 3 agentes", req.subtitle)
        assertEquals(2, req.count)
        val ask = HomePanel.strip(HomePanel.Scene.WAITING, "Alvaro PC", list, emptyList(), ::name, null, false, 0, Words.ES)
        assertEquals("Gemini CLI tiene una pregunta", ask.title)
        assertEquals(HomePanel.Tone.WAITING, ask.tone)
        val calm = HomePanel.strip(HomePanel.Scene.CALM, "Alvaro PC", listOf(s("c", BotState.WORKING)), emptyList(), ::name, null, false, 0, Words.ES)
        assertEquals(HomePanel.Tone.CALM, calm.tone)
        assertEquals("Alvaro PC · todo tranquilo", calm.title)
        assertEquals("Conectado", calm.subtitle)
        assertEquals("the chip counts what runs", 1, calm.count)
        val away = HomePanel.strip(HomePanel.Scene.OFFLINE, "Alvaro PC", list, emptyList(), ::name, lostAtMs = 0, connecting = false, nowMs = 3 * 60_000, w = Words.ES)
        assertEquals(HomePanel.Tone.OFFLINE, away.tone)
        assertEquals("Sin conexión con Alvaro PC", away.title)
        assertEquals("Último dato hace 3 min", away.subtitle)
        assertEquals(0, away.count)
        val never = HomePanel.strip(HomePanel.Scene.OFFLINE, "Alvaro PC", emptyList(), emptyList(), ::name, null, connecting = true, nowMs = 0, w = Words.ES)
        assertEquals("Conectando…", never.subtitle)
        assertEquals("1 agent", "Alvaro PC · 1 agent", HomePanel.strip(HomePanel.Scene.REQUEST, "Alvaro PC", list.take(1), listOf(r("a")), ::name, null, false, 0, Words.EN).subtitle)
        assertFalse(req.title.contains("npm"))
    }

    // ── The result of a decision (kenai-0.1 S9, screen 3; T14) ──

    private fun outcome(kind: OutcomeKind, allow: Boolean? = true, since: Long = 1_000) =
        Outcome("fpA", kind, allow, "a", "Bash", sinceMs = since, atWallMs = 0, expiresAtWallMs = null)

    @Test fun aResultBeatsCalmWaitingAndOfflineButANewRequestBeatsTheResult() {
        val q = listOf(s("b", BotState.QUESTION))
        val done = outcome(OutcomeKind.ALLOWED)
        assertEquals(HomePanel.Scene.RESULT, HomePanel.scene(true, true, 0, emptyList(), result = done, newestArrivalMs = null))
        assertEquals(HomePanel.Scene.RESULT, HomePanel.scene(true, true, 0, q, result = done, newestArrivalMs = null))
        // A request that waited before the decision does not hide its result («Lo siguiente» offers it).
        assertEquals(HomePanel.Scene.RESULT, HomePanel.scene(true, true, 1, q, result = done, newestArrivalMs = 500))
        // A request that arrived after it does.
        assertEquals(HomePanel.Scene.REQUEST, HomePanel.scene(true, true, 1, q, result = done, newestArrivalMs = 1_001))
        // «No se pudo enviar» is said even though the computer is away.
        assertEquals(HomePanel.Scene.RESULT, HomePanel.scene(true, false, 0, q, result = outcome(OutcomeKind.FAILED), newestArrivalMs = null))
        assertEquals(HomePanel.Scene.CALM, HomePanel.scene(true, true, 0, emptyList(), result = null, newestArrivalMs = null))
    }

    @Test fun theStripOfAResultSaysWhatHappenedAndAlwaysNamesTheComputer() {
        fun strip(kind: OutcomeKind, allow: Boolean? = true) =
            HomePanel.strip(HomePanel.Scene.RESULT, "Alvaro PC", emptyList(), emptyList(), ::name, null, false, 0, Words.ES, result = outcome(kind, allow))
        strip(OutcomeKind.SENDING).let {
            assertEquals("Enviando a Alvaro PC…", it.title)
            assertEquals(HomePanel.Tone.SENDING, it.tone)
            assertTrue(it.spinner)
        }
        assertEquals("Permitido · Alvaro PC lo recibió", strip(OutcomeKind.ALLOWED).title)
        assertEquals(HomePanel.Tone.DONE, strip(OutcomeKind.ALLOWED).tone)
        assertEquals("Denegado · Alvaro PC lo recibió", strip(OutcomeKind.DENIED, false).title)
        assertEquals(HomePanel.Tone.FAINT, strip(OutcomeKind.DENIED, false).tone)
        assertEquals("Ese pedido ya no está pendiente", strip(OutcomeKind.EXPIRED, null).title)
        assertEquals(HomePanel.Tone.FAINT, strip(OutcomeKind.EXPIRED, null).tone)
        assertEquals("No se pudo enviar", strip(OutcomeKind.FAILED).title)
        assertEquals(HomePanel.Tone.OFFLINE, strip(OutcomeKind.FAILED).tone)
        for (k in OutcomeKind.entries) {
            assertEquals(k.name, "Alvaro PC", strip(k).subtitle)
            assertEquals(k.name, k == OutcomeKind.SENDING, strip(k).spinner)
        }
    }

    @Test fun whatComesNextIsTheNextRequestElseTheFirstQuestionNeverTheOneJustDecided() {
        val q = listOf(s("c", BotState.WORKING), s("b", BotState.QUESTION))
        val next = HomePanel.next(listOf(r("a", "fpA"), r("d", "fpD")), q, exclude = setOf("fpA"))
        assertEquals(HomePanel.NextKind.APPROVAL, next!!.kind)
        assertEquals("d", next.pillId)
        assertEquals("fpD", next.fingerprint)
        val ask = HomePanel.next(listOf(r("a", "fpA")), q, exclude = setOf("fpA"))
        assertEquals(HomePanel.NextKind.QUESTION, ask!!.kind)
        assertEquals("b", ask.pillId)
        assertNull(ask.fingerprint)
        assertNull("nothing waits", HomePanel.next(emptyList(), listOf(s("c", BotState.WORKING)), emptySet()))
    }
}
