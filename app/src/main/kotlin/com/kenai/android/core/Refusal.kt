package com.kenai.android.core

/**
 * Why the computer refused an answer this phone sent (KN-04 T3, S4/S12). `decision` and `answer` are refused on the same path as
 * every other frame: a v1 `error` with one of these fixed codes, a fixed sentence, and no fingerprint, no label and no command
 * (abito-core `engine/docs/PROTOCOL.md`, AC-07 U1). The code therefore never says which request it was about — the link knows what
 * it had in flight — and it says only what becomes of the card:
 *
 * - [OFF], [TRUNCATED], [INVALID_ANSWER], [BAD_REQUEST]: the request is still open on the computer, so the card stays and the answer
 *   can be tried again (deny is still taken on a card that was cut, a correct answer is still taken on one that was rejected);
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
}

/** What each of the engine's refusal codes means for the card (KN-04 S4/S12). Pure, so it is unit-tested. */
object Refusals {
    /**
     * The refusal behind a v1 `error` code, or null when the code is something else: `auth`, `version` and the locally told `send`
     * are the link's own, and `failed`, `busy`, `unavailable`, `rate` and `limit` are the chat frames' codes, which this front also
     * sends as an `error` and the app still shows as a link error.
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
        else -> null
    }

    /** The request is still open on the computer: the card stays, and the decision (or another answer) can be sent again. */
    fun keeps(r: Refusal): Boolean = r == Refusal.OFF || r == Refusal.TRUNCATED || r == Refusal.INVALID_ANSWER || r == Refusal.BAD_REQUEST

    /** The request is gone: nobody can answer it from this phone any more, so the card goes. */
    fun gone(r: Refusal): Boolean = !keeps(r)

    /**
     * True when no frame but an answer produces this code, so it speaks of this phone's answer even with nothing in flight.
     * `bad_request` is the exception: the engine refuses a `chatNew`, `chatOpen` or `chatDelete` with it too, which stays a link
     * error for the app.
     */
    fun onlyAnswers(r: Refusal): Boolean = r != Refusal.BAD_REQUEST
}
