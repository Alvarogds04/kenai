package com.kenai.android.core

import com.kenai.android.link.ApprovalRequest

/**
 * Why the computer refused an answer this phone sent (KN-04 T3, S4/S12). `decision` and `answer` are refused on the same path as
 * every other frame: a v1 `error` with one of these fixed codes, a fixed sentence, and — since abito-core AC-07c — an OPTIONAL,
 * additive `fingerprint` naming the request it is about (abito-core `engine/docs/PROTOCOL.md`, AC-07 U1). When the frame names it,
 * that name is authoritative (it is the phone's own request name echoed back); when it does not, the link pairs it with the OLDEST
 * answer it still holds in flight. A code says only what becomes of the card:
 *
 * - [OFF], [TRUNCATED], [INVALID_ANSWER], [BAD_REQUEST], [FAILED]: the request is still open on the computer, so the card stays and
 *   the answer can be tried again (deny is still taken on a card that was cut, a correct answer is still taken on one that was
 *   rejected, and a `failed` answer settled nothing at all);
 * - [RESOLVED] and the rest: the request is gone, so the card goes and this phone never answers it again.
 */
enum class Refusal {
    /** The answers switch is off for this phone on the computer (AC-07 U4): the request stays answerable THERE. */
    OFF,

    /** Already settled — on the computer, on another phone, or by its own deadline: one request, one answer (S4). */
    RESOLVED,

    /** A card written cut asked to allow: the registry's own backstop. The request stays open and deny is still taken. */
    TRUNCATED,

    /** Not an answer the request offers: nothing was changed, so a correct answer is still possible. */
    INVALID_ANSWER,

    /** Not an answer this computer understands: a malformed fingerprint, a decision that is neither allow nor deny, picks that
     * are not lists of strings, a decision for a question or picks for an approval. The request is untouched. */
    BAD_REQUEST,

    /** The computer does not hold that request any more (it was never one of its cards, or the link was remade). */
    UNKNOWN_REQUEST,

    /** This computer never offered this phone `answers` on this link. */
    NOT_OFFERED,

    /** Past the request's own deadline on the computer, so a tap on the card cannot resolve anything. */
    EXPIRED,

    /** The computer could not take the answer and settled NOTHING (an unmapped registry refusal): the request stays pending until
     * its own `approvalResolved`, so the card stays answerable. */
    FAILED,
}

/**
 * What a refusal leaves on Home (KN-04 T3b): the decisions `AppModel.answerRefused` takes, pure so they are unit-tested with
 * behavioural assertions instead of source greps.
 */
data class RefusalEffect(
    /** The request is still open on the computer: the card stays and can be answered again. */
    val requestStays: Boolean,
    /** From now on the card only offers Deny: the command arrived cut, so an allow is never offered again. */
    val cutCard: Boolean,
    /** The request is gone: the card goes, and this phone never answers it again (S4). */
    val cardGoes: Boolean,
    /** Home's «Enviando…» of this very request becomes «expiró» instead of just going: this phone decided and lost. */
    val showExpired: Boolean,
    /** A pending «Permitir» prompt for this request is dropped: it could only resolve a request that is gone. */
    val dropWantAllow: Boolean,
)

/** What `AppModel.answerRefused` does for one refusal, as data (KN-04 T1). Every field concerns only the request it names. */
data class RefusalPlan(
    /** Home's «Enviando…» of this request becomes «expiró». */
    val expire: ApprovalRequest?,
    /** «Enviando…» and the answer note of this request just go (the request stays open). */
    val clearPending: String?,
    /** This card arrived cut and only offers Deny from now on. */
    val cutFp: String?,
    /** This request's card goes. */
    val removeFp: String?,
    val dropWantAllow: Boolean,
)

/** What each of the engine's refusal codes means for the card (KN-04 S4/S12). Pure, so it is unit-tested. */
object Refusals {
    /**
     * The refusal behind a v1 `error` code, or null when the code is something else: `auth`, `version` and the locally told `send`
     * are the link's own, and `busy`, `unavailable`, `rate` and `limit` are the chat frames' codes, which this front also sends as an
     * `error` and the app still shows as a link error.
     */
    fun of(code: String): Refusal? = when (code) {
        "off" -> Refusal.OFF
        "resolved" -> Refusal.RESOLVED
        "truncated" -> Refusal.TRUNCATED
        "invalid_answer" -> Refusal.INVALID_ANSWER
        "bad_request" -> Refusal.BAD_REQUEST
        "unknown_request" -> Refusal.UNKNOWN_REQUEST
        "not_offered" -> Refusal.NOT_OFFERED
        "expired" -> Refusal.EXPIRED
        "failed" -> Refusal.FAILED
        else -> null
    }

    /** The request is still open on the computer: the card stays, and the decision (or another answer) can be sent again. */
    fun keeps(r: Refusal): Boolean =
        r == Refusal.OFF || r == Refusal.TRUNCATED || r == Refusal.INVALID_ANSWER || r == Refusal.BAD_REQUEST || r == Refusal.FAILED

    /** The request is gone: nobody can answer it from this phone any more, so the card goes. */
    fun gone(r: Refusal): Boolean = !keeps(r)

    /**
     * True when no frame but an answer produces this code, so it speaks of this phone's answer even with nothing in flight.
     * [Refusal.BAD_REQUEST] and [Refusal.FAILED] are the exceptions: the engine refuses a `chatNew`, `chatOpen` or `chatDelete` with
     * them too (with no fingerprint), which stays a link error for the app.
     */
    fun onlyAnswers(r: Refusal): Boolean = r != Refusal.BAD_REQUEST && r != Refusal.FAILED

    /**
     * What [refusal] leaves on Home. [onHome] is whether the request it is about is still one of the phone's cards, [sending] whether
     * Home holds this phone's «Enviando…» for it, and [wantsAllow] whether the screen-lock prompt for that allow is still pending.
     */
    fun effect(refusal: Refusal, onHome: Boolean, sending: Boolean, wantsAllow: Boolean): RefusalEffect {
        val keeps = keeps(refusal)
        return RefusalEffect(
            requestStays = keeps,
            cutCard = keeps && refusal == Refusal.TRUNCATED,
            cardGoes = !keeps,
            showExpired = !keeps && onHome && sending,
            dropWantAllow = !keeps && wantsAllow,
        )
    }

    /** [RefusalEffect] applied to the phone's cards: which request each step touches. A refusal with no fingerprint touches no card. */
    fun plan(refusal: Refusal, fingerprint: String?, approvals: List<ApprovalRequest>, sendingFp: String?, wantAllowFp: String?): RefusalPlan {
        if (fingerprint == null) return RefusalPlan(null, null, null, null, false)
        val request = approvals.firstOrNull { it.fingerprint == fingerprint }
        val e = effect(refusal, onHome = request != null, sending = sendingFp == fingerprint, wantsAllow = wantAllowFp == fingerprint)
        val expire = if (e.showExpired) request else null
        return RefusalPlan(
            expire = expire,
            clearPending = if (expire == null) fingerprint else null,
            cutFp = if (e.cutCard && request != null) fingerprint else null,
            removeFp = if (e.cardGoes) fingerprint else null,
            dropWantAllow = e.dropWantAllow,
        )
    }

    /** [approvals] with the card [fingerprint] marked as cut (null: unchanged). */
    fun markCut(approvals: List<ApprovalRequest>, fingerprint: String?): List<ApprovalRequest> =
        if (fingerprint == null) approvals else approvals.map { if (it.fingerprint == fingerprint) it.copy(truncated = true) else it }
}
