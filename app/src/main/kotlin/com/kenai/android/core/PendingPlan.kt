package com.kenai.android.core

import com.kenai.android.link.ApprovalRequest
import com.kenai.android.link.Protocol
import com.kenai.android.link.QuestionRequest

/**
 * Inicio's «Pendientes» list (KN-11): every approval and question that waits on the phone, in one list, oldest first.
 * Pure: it only reads what the phone already holds and decides nothing; a row opens the existing sheet or screen.
 */
object PendingPlan {
    enum class Kind { APPROVAL, QUESTION }

    /** [leftMs]: what remains of the phone's 120 s for it. */
    data class Item(val kind: Kind, val fingerprint: String, val pillId: String, val agent: String, val createdAtMs: Long, val leftMs: Long)

    fun build(approvals: List<ApprovalRequest>, questions: List<QuestionRequest>, nowMs: Long, agentName: (String) -> String): List<Item> =
        (approvals.map { Item(Kind.APPROVAL, it.fingerprint, it.pillId, agentName(it.pillId), it.createdAtMs, left(it.createdAtMs, nowMs)) } +
            questions.map { Item(Kind.QUESTION, it.fingerprint, it.pillId, agentName(it.pillId), it.createdAtMs, left(it.createdAtMs, nowMs)) })
            .filter { it.leftMs > 0 }
            .sortedBy { it.createdAtMs }

    /** `approvalResolved{fingerprint}`: the item goes, nothing is sent. */
    fun resolved(items: List<Item>, fingerprint: String): List<Item> = items.filter { it.fingerprint != fingerprint }

    private fun left(createdAtMs: Long, nowMs: Long) = Protocol.APPROVAL_TTL_MS - (nowMs - createdAtMs)
}
