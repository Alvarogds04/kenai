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

    /** What a row's tap does. Never a decision: it only reaches the sheet or screen that already decides behind its own gates. */
    sealed interface Opens {
        data class Question(val pillId: String) : Opens
        /** The existing approval sheet, for an approval that is not Home's hero. */
        data class Sheet(val fingerprint: String) : Opens
        /** The first approval is Home's hero, already on screen with its buttons: bring the list to it. */
        data object Hero : Opens
        data object Nothing : Opens
    }

    fun opens(item: Item, approvals: List<ApprovalRequest>, canAnswer: Boolean): Opens = when (item.kind) {
        Kind.QUESTION -> if (canAnswer) Opens.Question(item.pillId) else Opens.Nothing
        Kind.APPROVAL -> when {
            approvals.none { it.fingerprint == item.fingerprint } -> Opens.Nothing
            ApprovalSheetPlan.hero(approvals)?.fingerprint == item.fingerprint -> Opens.Hero
            else -> Opens.Sheet(item.fingerprint)
        }
    }

    private fun left(createdAtMs: Long, nowMs: Long) = Protocol.APPROVAL_TTL_MS - (nowMs - createdAtMs)
}
