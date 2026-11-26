package com.kenai.android

import com.kenai.android.core.CirclePlan
import com.kenai.android.core.PendingPlan
import com.kenai.android.link.ApprovalRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CirclePlanTest {
    private fun item(kind: PendingPlan.Kind, fp: String, pill: String = "p1", left: Long = 60_000) = PendingPlan.Item(kind, fp, pill, "agent-$pill", 0L, left)
    private fun a(fp: String, truncated: Boolean = false) = ApprovalRequest("p1", fp, "Bash", "ls", 0L, truncated)

    // S2: pending requests, circle visible → discreet pulse and the number of requests.
    @Test fun withPendingItemsTheCircleShowsTheCountAndPulses() {
        val c = CirclePlan.circle(listOf(item(PendingPlan.Kind.APPROVAL, "a"), item(PendingPlan.Kind.QUESTION, "q")), reducedMotion = false)
        assertEquals(2, c.count)
        assertTrue(c.pulses)
    }

    @Test fun nothingPendingIsQuiet() {
        val c = CirclePlan.circle(emptyList(), reducedMotion = false)
        assertEquals(0, c.count)
        assertFalse(c.pulses)
    }

    @Test fun reducedMotionKeepsTheCountButNeverPulses() {
        val c = CirclePlan.circle(listOf(item(PendingPlan.Kind.APPROVAL, "a")), reducedMotion = true)
        assertEquals(1, c.count)
        assertFalse(c.pulses)
    }

    @Test fun thePulseIsDiscreet() {
        val samples = (0..24).map { CirclePlan.pulseScale(it * 100L) }
        assertTrue(samples.all { it in 1.0..1.08 })
        assertEquals(1.0, CirclePlan.pulseScale(0), 1e-9)
        assertTrue(samples.max() > 1.02)
        assertEquals(CirclePlan.pulseScale(300), CirclePlan.pulseScale(300 + CirclePlan.PULSE_PERIOD_MS), 1e-9)
    }

    @Test fun theBadgeNeverGrowsPastNinetyNinePlus() {
        assertEquals("3", CirclePlan.badge(3))
        assertEquals("99+", CirclePlan.badge(120))
    }

    // S1: the panel lists the pending requests of several sessions.
    @Test fun thePanelListsEveryPendingItemInOrderWithItsSession() {
        val rows = CirclePlan.panel(listOf(item(PendingPlan.Kind.APPROVAL, "a", "p1"), item(PendingPlan.Kind.QUESTION, "q", "p2")), listOf(a("a")))
        assertEquals(listOf("a", "q"), rows.map { it.fingerprint })
        assertEquals(listOf("p1", "p2"), rows.map { it.pillId })
        assertEquals(listOf("agent-p1", "agent-p2"), rows.map { it.agent })
    }

    // S3: Deny resolves from the panel; Allow and answers never complete in the overlay: they hand off.
    @Test fun anApprovalOffersDenyHereAndAllowOnlyAsAHandoff() {
        val r = CirclePlan.panel(listOf(item(PendingPlan.Kind.APPROVAL, "a")), listOf(a("a"))).single()
        assertTrue(r.canDeny)
        assertEquals(CirclePlan.Handoff.Allow("a"), r.handoff)
    }

    @Test fun anApprovalCutByTheComputerHasNoAllowOnThePhoneButStillDeny() {
        val r = CirclePlan.panel(listOf(item(PendingPlan.Kind.APPROVAL, "a")), listOf(a("a", truncated = true))).single()
        assertTrue(r.canDeny)
        assertEquals(CirclePlan.Handoff.App, r.handoff)
    }

    @Test fun aQuestionCannotBeDeniedAndHandsOffToItsScreen() {
        val r = CirclePlan.panel(listOf(item(PendingPlan.Kind.QUESTION, "q", "p2")), emptyList()).single()
        assertFalse(r.canDeny)
        assertEquals(CirclePlan.Handoff.Question("p2"), r.handoff)
    }

    @Test fun anApprovalThePhoneNoLongerHoldsHasNothingToDeny() {
        val r = CirclePlan.panel(listOf(item(PendingPlan.Kind.APPROVAL, "gone")), emptyList()).single()
        assertFalse(r.canDeny)
        assertEquals(CirclePlan.Handoff.App, r.handoff)
    }

    @Test fun anEmptyPanelHasNoRows() {
        assertTrue(CirclePlan.panel(emptyList(), emptyList()).isEmpty())
    }
}
