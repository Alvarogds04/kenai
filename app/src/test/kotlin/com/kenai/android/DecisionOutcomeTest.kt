package com.kenai.android

import com.kenai.android.character.BotState
import com.kenai.android.core.DecisionOutcomes
import com.kenai.android.core.OutcomeKind
import com.kenai.android.link.ApprovalRequest
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What became of the decision the phone sent (kenai-0.1 S9, screen 3 «Resultado»; T14). The phone only knows a decision arrived
 * when the computer says the request is resolved, so: sending until then; allowed or denied once it is resolved for a fingerprint
 * the phone decided; expired when it was answered on the computer, refused as no longer pending, or ran out of its 120 s; failed
 * when the decision could not be written or the link went down while sending. A new request replaces the result.
 */
class DecisionOutcomeTest {
    private val a = ApprovalRequest("integration_claude", "fpA", "Bash", "npm test", 0L)
    private val b = ApprovalRequest("agent_codex", "fpB", "Edit", "a.txt", 0L)

    private fun machine() = DecisionOutcomes()

    @Test fun decidingIsSendingUntilTheComputerSaysItGotIt() {
        val m = machine()
        val o = m.decided(a, allow = true, nowMs = 1_000, wallMs = 50_000, expiresAtWallMs = 170_000)
        assertEquals(OutcomeKind.SENDING, o.kind)
        assertEquals(true, o.allow)
        assertEquals("integration_claude", o.pillId)
        assertEquals("Bash", o.tool)
        assertEquals(o, m.current)
    }

    @Test fun resolvedForTheFingerprintThePhoneDecidedIsAllowedOrDenied() {
        val allow = machine()
        allow.decided(a, allow = true, nowMs = 1_000, wallMs = 50_000, expiresAtWallMs = null)
        val ok = allow.resolved("fpA", shown = a, nowMs = 1_400, wallMs = 50_400, expiresAtWallMs = null)
        assertEquals(OutcomeKind.ALLOWED, ok!!.kind)
        assertEquals(OutcomeKind.ALLOWED, allow.current!!.kind)
        assertEquals("shown for two minutes from the confirmation", 1_400L, allow.current!!.sinceMs)

        val deny = machine()
        deny.decided(a, allow = false, nowMs = 1_000, wallMs = 50_000, expiresAtWallMs = null)
        assertEquals(OutcomeKind.DENIED, deny.resolved("fpA", shown = a, nowMs = 1_200, wallMs = 50_200, expiresAtWallMs = null)!!.kind)
        assertEquals(OutcomeKind.DENIED, deny.current!!.kind)
    }

    @Test fun resolvedForARequestThePhoneDidNotDecideWasAnsweredOnTheComputer() {
        val m = machine()
        val o = m.resolved("fpA", shown = a, nowMs = 5_000, wallMs = 60_000, expiresAtWallMs = 170_000)
        assertEquals(OutcomeKind.EXPIRED, o!!.kind)
        assertNull("nothing was sent from the phone", o.allow)
        assertEquals(170_000L, o.expiresAtWallMs)
        // A request that was not on Home (the second one waiting) going away is no news for the hero.
        val n = machine()
        assertNull(n.resolved("fpB", shown = null, nowMs = 5_000, wallMs = 60_000, expiresAtWallMs = null))
        assertNull(n.current)
    }

    @Test fun aForeignResolutionNeverReplacesTheResultOfTheDecisionOnScreen() {
        val m = machine()
        m.decided(a, allow = true, nowMs = 0, wallMs = 0, expiresAtWallMs = null)
        assertNull(m.resolved("fpB", shown = b, nowMs = 10, wallMs = 10, expiresAtWallMs = null))
        assertEquals(OutcomeKind.SENDING, m.current!!.kind)
    }

    @Test fun refusedAsNoLongerPendingIsExpiredAndNothingWasSent() {
        val m = machine()
        val o = m.refused(a, nowMs = 0, wallMs = 0, expiresAtWallMs = 120_000)
        assertEquals(OutcomeKind.EXPIRED, o.kind)
        assertNull(o.allow)
        assertEquals(o, m.current)
    }

    @Test fun aSendErrorOrTheLinkGoingDownWhileSendingIsFailed() {
        val m = machine()
        m.decided(a, allow = true, nowMs = 0, wallMs = 0, expiresAtWallMs = null)
        assertNull("another fingerprint's error is not ours", m.sendFailed("fpB"))
        assertEquals(OutcomeKind.FAILED, m.sendFailed("fpA")!!.kind)
        assertEquals(true, m.current!!.allow)

        val n = machine()
        n.decided(a, allow = false, nowMs = 0, wallMs = 0, expiresAtWallMs = null)
        assertEquals(OutcomeKind.FAILED, n.linkLost()!!.kind)
        // A confirmed result stays true when the link drops afterwards.
        val c = machine()
        c.decided(a, allow = true, nowMs = 0, wallMs = 0, expiresAtWallMs = null)
        c.resolved("fpA", a, 10, 10, null)
        assertNull(c.linkLost())
        assertEquals(OutcomeKind.ALLOWED, c.current!!.kind)
    }

    @Test fun aConfirmationAfterAFailureStillCountsAsReceived() {
        val m = machine()
        m.decided(a, allow = true, nowMs = 0, wallMs = 0, expiresAtWallMs = null)
        m.linkLost()
        assertEquals(OutcomeKind.ALLOWED, m.resolved("fpA", a, 10, 10, null)!!.kind)
    }

    @Test fun theExpiryTimerTurnsTheHeroIntoExpired() {
        // Nothing decided: the request on Home ran out.
        val idle = machine()
        assertEquals(OutcomeKind.EXPIRED, idle.expired("fpA", shown = a, nowMs = 120_000, wallMs = 0, expiresAtWallMs = 120_000)!!.kind)
        // Sent but never confirmed: expired, and it says the phone did send (allow is kept).
        val sent = machine()
        sent.decided(a, allow = true, nowMs = 0, wallMs = 0, expiresAtWallMs = null)
        val o = sent.expired("fpA", shown = a, nowMs = 120_000, wallMs = 0, expiresAtWallMs = 120_000)!!
        assertEquals(OutcomeKind.EXPIRED, o.kind)
        assertEquals(true, o.allow)
        // Failed and then expired: nothing reached the computer.
        val failed = machine()
        failed.decided(a, allow = true, nowMs = 0, wallMs = 0, expiresAtWallMs = null)
        failed.sendFailed("fpA")
        assertNull(failed.expired("fpA", shown = a, nowMs = 120_000, wallMs = 0, expiresAtWallMs = 120_000)!!.allow)
        // Another request expiring does not touch the result on screen.
        val other = machine()
        other.decided(a, allow = true, nowMs = 0, wallMs = 0, expiresAtWallMs = null)
        other.resolved("fpA", a, 10, 10, null)
        assertNull(other.expired("fpB", shown = null, nowMs = 20, wallMs = 0, expiresAtWallMs = null))
        assertEquals(OutcomeKind.ALLOWED, other.current!!.kind)
    }

    @Test fun aNewRequestReplacesTheResultAndALateConfirmationStaysOffScreen() {
        val m = machine()
        m.decided(a, allow = true, nowMs = 0, wallMs = 0, expiresAtWallMs = null)
        m.arrived("fpB")
        assertNull(m.current)
        // The confirmation still says what happened (the buzz), but the new request keeps Home.
        assertEquals(OutcomeKind.ALLOWED, m.resolved("fpA", shown = b, nowMs = 10, wallMs = 10, expiresAtWallMs = null)!!.kind)
        assertNull(m.current)
    }

    @Test fun theSameRequestOfferedAgainAfterAFailureIsANewChanceToDecide() {
        val m = machine()
        m.decided(a, allow = true, nowMs = 0, wallMs = 0, expiresAtWallMs = null)
        m.linkLost()
        m.arrived("fpA")
        assertNull(m.current)
        // The phone's earlier decision did not land: a resolution now is the computer's own answer.
        assertEquals(OutcomeKind.EXPIRED, m.resolved("fpA", shown = a, nowMs = 10, wallMs = 10, expiresAtWallMs = null)!!.kind)
    }

    @Test fun dismissingClearsAndTheResultNeverShowsAfterTwoMinutes() {
        val m = machine()
        m.decided(a, allow = true, nowMs = 1_000, wallMs = 0, expiresAtWallMs = null)
        assertNotNull(m.visible(1_000 + DecisionOutcomes.SHOW_MS))
        assertNull(m.visible(1_000 + DecisionOutcomes.SHOW_MS + 1))
        assertEquals(120_000L, DecisionOutcomes.SHOW_MS)
        m.dismiss()
        assertNull(m.current)
        assertNull(m.visible(1_000))
    }

    @Test fun theBuzzIsNotedOnTheResult() {
        val m = machine()
        m.decided(a, allow = false, nowMs = 0, wallMs = 0, expiresAtWallMs = null)
        m.resolved("fpA", a, 10, 10, null)
        m.buzzed("fpA", 2)
        assertEquals(2, m.current!!.buzzes)
        m.buzzed("fpB", 1)
        assertEquals(2, m.current!!.buzzes)
    }

    @Test fun theMemoryOfDecidedFingerprintsStaysSmall() {
        val m = machine()
        for (i in 0 until 1_000) m.decided(ApprovalRequest("p", "f$i", "Bash", "ls", 0L), true, i.toLong(), 0, null)
        assertTrue(m.remembered() <= DecisionOutcomes.MAX)
    }

    @Test fun theExpiryClockIsTheArrivalPlusTwoMinutesOnTheWallClock() {
        // Arrived 30 s ago by the elapsed clock; now is 14:41:00 UTC on the wall: it expires at 14:42:30.
        val now = 14 * 3_600_000L + 41 * 60_000L
        val at = DecisionOutcomes.expiresAtWall(arrivedElapsedMs = 70_000, nowElapsedMs = 100_000, nowWallMs = now)
        assertEquals(now - 30_000 + 120_000, at)
        assertEquals("14:42", DecisionOutcomes.clock(at!!, ZoneOffset.UTC))
        assertNull(DecisionOutcomes.expiresAtWall(null, 0, 0))
    }

    // ── One «what just happened» card on Home (kenai-0.1 S9, T19; L29 a) ──────────────────────────

    @Test fun theAnswerToAQuestionTakesHomesOneCardAndAResultTakesItBack() {
        val m = machine()
        m.decided(a, allow = true, nowMs = 0, wallMs = 0, expiresAtWallMs = null)
        val note = m.answered("Respuesta enviada", nowMs = 10)
        assertEquals("Respuesta enviada", note.text)
        assertEquals(note, m.answer)
        assertNull("Home shows one card, never two", m.current)
        // The decision of a later request replaces the answer, whichever card came first.
        m.decided(b, allow = false, nowMs = 20, wallMs = 20, expiresAtWallMs = null)
        assertEquals(OutcomeKind.SENDING, m.current!!.kind)
        assertNull(m.answer)
        // And an answer replaces a confirmed result too.
        m.answered("Respuesta enviada", nowMs = 30)
        assertNull(m.current)
        assertEquals("Respuesta enviada", m.answer!!.text)
    }

    @Test fun aNewRequestClearsTheAnswerAsItClearsAResult() {
        val m = machine()
        m.answered("Esa pregunta ya no está esperando.", nowMs = 0)
        m.arrived("fpB")
        assertNull(m.current)
        assertNull("the new request takes Home, alone", m.answer)
    }

    @Test fun theAnswerNeverShowsAfterTwoMinutesAndCerrarClearsIt() {
        val m = machine()
        m.answered("Respuesta enviada", nowMs = 1_000)
        assertNotNull(m.answerVisible(1_000))
        assertNotNull(m.answerVisible(1_000 + DecisionOutcomes.SHOW_MS))
        assertNull(m.answerVisible(1_000 + DecisionOutcomes.SHOW_MS + 1))
        m.dismissAnswer()
        assertNull(m.answer)
        assertNull(m.answerVisible(1_000))
    }

    @Test fun eachResultHasItsKodaPoseAndRoleColour() {
        assertEquals(BotState.APPROVAL, DecisionOutcomes.pose(OutcomeKind.SENDING))
        assertEquals(BotState.FINISHED, DecisionOutcomes.pose(OutcomeKind.ALLOWED))
        assertEquals(BotState.IDLE, DecisionOutcomes.pose(OutcomeKind.DENIED))
        assertEquals(BotState.IDLE, DecisionOutcomes.pose(OutcomeKind.EXPIRED))
        assertEquals(BotState.ERROR, DecisionOutcomes.pose(OutcomeKind.FAILED))
        assertEquals(com.kenai.android.character.KodaRole.LIVE, DecisionOutcomes.role(OutcomeKind.SENDING))
        assertEquals(com.kenai.android.character.KodaRole.GO, DecisionOutcomes.role(OutcomeKind.ALLOWED))
        assertEquals(com.kenai.android.character.KodaRole.FAINT, DecisionOutcomes.role(OutcomeKind.DENIED))
        assertEquals(com.kenai.android.character.KodaRole.FAINT, DecisionOutcomes.role(OutcomeKind.EXPIRED))
        assertEquals(com.kenai.android.character.KodaRole.RED, DecisionOutcomes.role(OutcomeKind.FAILED))
    }
}
