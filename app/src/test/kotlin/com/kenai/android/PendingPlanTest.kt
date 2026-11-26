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

    @Test fun emptyStateIsAnEmptyList() {
        assertTrue(PendingPlan.build(emptyList(), emptyList(), 0, name).isEmpty())
    }
}

class PendingOpensTest {
    private fun a(fp: String) = ApprovalRequest("p1", fp, "Bash", "ls", 0L)
    private fun item(kind: PendingPlan.Kind, fp: String, pill: String = "p1") = PendingPlan.Item(kind, fp, pill, "n", 0L, 1_000L)

    @Test fun aQuestionRowOpensItsQuestionScreen() {
        assertEquals(PendingPlan.Opens.Question("p2"), PendingPlan.opens(item(PendingPlan.Kind.QUESTION, "q", "p2"), listOf(a("a")), canAnswer = true))
    }

    @Test fun aQuestionThePhoneCannotAnswerOpensNothing() {
        assertEquals(PendingPlan.Opens.Nothing, PendingPlan.opens(item(PendingPlan.Kind.QUESTION, "q"), emptyList(), canAnswer = false))
    }

    @Test fun theFirstApprovalIsHomesHeroSoItOnlyScrollsToIt() {
        assertEquals(PendingPlan.Opens.Hero, PendingPlan.opens(item(PendingPlan.Kind.APPROVAL, "a"), listOf(a("a"), a("b")), canAnswer = true))
    }

    @Test fun anyOtherApprovalOpensTheExistingSheet() {
        assertEquals(PendingPlan.Opens.Sheet("b"), PendingPlan.opens(item(PendingPlan.Kind.APPROVAL, "b"), listOf(a("a"), a("b")), canAnswer = true))
    }

    @Test fun anApprovalThatIsGoneOpensNothing() {
        assertEquals(PendingPlan.Opens.Nothing, PendingPlan.opens(item(PendingPlan.Kind.APPROVAL, "z"), listOf(a("a")), canAnswer = true))
    }

    // KN-12 T0: Home's hero is the first approval the phone holds, even one past its 120 s that the list no longer shows;
    // a live approval behind it is then not the hero, so its row opens the sheet instead of scrolling to a hero that is another request.
    @Test fun aLiveApprovalBehindAnExpiredFirstOneOpensTheSheet() {
        val expiredFirst = ApprovalRequest("p1", "old", "Bash", "ls", 0L)
        val live = a("live")
        assertEquals(PendingPlan.Opens.Sheet("live"), PendingPlan.opens(item(PendingPlan.Kind.APPROVAL, "live"), listOf(expiredFirst, live), canAnswer = false))
    }

    // KN-12 T0: an approval row never depends on its session being found; only a question row needs it.
    @Test fun anApprovalOpensEvenWhenItsSessionIsNotKnown() {
        assertEquals(PendingPlan.Opens.Sheet("b"), PendingPlan.opens(item(PendingPlan.Kind.APPROVAL, "b", "ghost"), listOf(a("a"), a("b")), canAnswer = false))
    }
}

class PendingRowTapSourceTest {
    private fun src(): String = java.io.File("src/main/kotlin/com/kenai/android/MainActivity.kt").readText()

    @Test fun theRowTapDoesNotReturnEarlyWhenTheSessionIsMissing() {
        val tap = src().substringAfter("PendingList(pendingItems)").substringBefore("Opens.Nothing -> {}")
        assertTrue("only a question row needs its session", !tap.contains("?: return@PendingList"))
    }
}
