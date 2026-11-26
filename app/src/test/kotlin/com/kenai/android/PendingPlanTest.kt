package com.kenai.android

import com.kenai.android.core.PendingPlan
import com.kenai.android.link.ApprovalRequest
import com.kenai.android.link.AskedQuestion
import com.kenai.android.link.Protocol
import com.kenai.android.link.QuestionRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPlanTest {
    private val ttl = Protocol.APPROVAL_TTL_MS
    private fun a(fp: String, at: Long, pill: String = "p1") = ApprovalRequest(pill, fp, "Bash", "ls", at)
    private fun q(fp: String, at: Long, pill: String = "p2") = QuestionRequest(pill, fp, listOf(AskedQuestion("?", emptyList(), false)), at)
    private val name = { id: String -> "agent-$id" }

    @Test fun oneListWithQuestionsAndApprovalsOldestFirst() {
        val items = PendingPlan.build(listOf(a("a2", 2_000), a("a1", 500)), listOf(q("q1", 1_000)), 3_000, name)
        assertEquals(listOf("a1", "q1", "a2"), items.map { it.fingerprint })
        assertEquals(listOf(PendingPlan.Kind.APPROVAL, PendingPlan.Kind.QUESTION, PendingPlan.Kind.APPROVAL), items.map { it.kind })
    }

    @Test fun eachRowCarriesAgentSessionAndTimeLeft() {
        val i = PendingPlan.build(listOf(a("a1", 1_000, "px")), emptyList(), 31_000, name).single()
        assertEquals("agent-px", i.agent)
        assertEquals("px", i.pillId)
        assertEquals(ttl - 30_000, i.leftMs)
    }

    @Test fun anExpiredRequestIsNotListed() {
        val items = PendingPlan.build(listOf(a("old", 0), a("live", 1_000)), listOf(q("oldq", 0)), ttl, name)
        assertEquals(listOf("live"), items.map { it.fingerprint })
        assertTrue(PendingPlan.build(listOf(a("x", 0)), emptyList(), ttl + 1, name).isEmpty())
    }

    @Test fun resolvedRemovesOnlyThatFingerprintAndSendsNothing() {
        val items = PendingPlan.build(listOf(a("a1", 0), a("a2", 1)), listOf(q("q1", 2)), 10, name)
        assertEquals(listOf("a1", "q1"), PendingPlan.resolved(items, "a2").map { it.fingerprint })
        assertEquals(items, PendingPlan.resolved(items, "unknown"))
    }

    @Test fun emptyStateIsAnEmptyList() {
        assertTrue(PendingPlan.build(emptyList(), emptyList(), 0, name).isEmpty())
    }
}
