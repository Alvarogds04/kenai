package com.kenai.android

import com.kenai.android.core.Refusal
import com.kenai.android.core.Refusals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Why the engine refused an answer this phone sent (KN-04 T3, S4/S12). The engine's `error` frame carries one of these codes and
 * nothing else, so the code alone says what Kenai must do with the card: keep it answerable, or take it away for good.
 */
class RefusalsTest {
    @Test fun everyRefusalCodeOfTheEngineIsUnderstood() {
        val codes = mapOf(
            "off" to Refusal.OFF,
            "resolved" to Refusal.RESOLVED,
            "truncated" to Refusal.TRUNCATED,
            "invalid_answer" to Refusal.INVALID_ANSWER,
            "bad_request" to Refusal.BAD_REQUEST,
            "unknown_request" to Refusal.UNKNOWN_REQUEST,
            "not_offered" to Refusal.NOT_OFFERED,
            "expired" to Refusal.EXPIRED,
            // AC-07: an unmapped registry refusal. NOTHING was settled, so the request stays pending (T3b review finding 4).
            "failed" to Refusal.FAILED,
        )
        for ((code, refusal) in codes) assertEquals(code, refusal, Refusals.of(code))
        // Nothing the engine can refuse a code with is left unmapped.
        assertEquals(Refusal.values().toSet(), codes.values.toSet())
    }

    @Test fun everyOtherErrorCodeIsALinkErrorAsBefore() {
        // `auth` and `send` are the link's own; the rest are the chat frames' codes, which the protocol also sends as an `error`.
        for (code in listOf("auth", "send", "version", "busy", "unavailable", "rate", "limit", "")) assertNull(code, Refusals.of(code))
    }

    @Test fun aCodeThatLeavesTheRequestOpenKeepsTheCard() {
        for (r in listOf(Refusal.OFF, Refusal.TRUNCATED, Refusal.INVALID_ANSWER, Refusal.BAD_REQUEST, Refusal.FAILED)) {
            assertEquals("$r keeps the request answerable", true, Refusals.keeps(r))
            assertEquals("$r leaves nothing to close", false, Refusals.gone(r))
        }
    }

    @Test fun aCodeThatClosesTheRequestTakesTheCardAway() {
        // `resolved` is the one S4 names: someone else answered, so this phone never answers it again.
        for (r in listOf(Refusal.RESOLVED, Refusal.UNKNOWN_REQUEST, Refusal.NOT_OFFERED, Refusal.EXPIRED)) {
            assertEquals("$r takes the card away", true, Refusals.gone(r))
            assertEquals("$r leaves nothing answerable", false, Refusals.keeps(r))
        }
    }

    @Test fun onlyTheCodesAChatFrameAlsoSendsNeedAnAnswerInFlight() {
        // `bad_request` refuses a `chatNew`/`chatOpen`/`chatDelete`, and `failed` a `chatNew` that could not be served: with
        // nothing in flight and no fingerprint they are still link errors, exactly as before.
        assertEquals(false, Refusals.onlyAnswers(Refusal.BAD_REQUEST))
        assertEquals(false, Refusals.onlyAnswers(Refusal.FAILED))
        for (r in Refusal.values().filter { it != Refusal.BAD_REQUEST && it != Refusal.FAILED }) {
            assertEquals("$r is about an answer only", true, Refusals.onlyAnswers(r))
        }
    }

    // ── What a refusal leaves on Home (T3b review finding 3: the decision is pure and behaviourally tested) ─────────

    @Test fun aRefusalThatLeavesTheRequestOpenKeepsTheCardAndNothingElseChanges() {
        for (r in listOf(Refusal.OFF, Refusal.INVALID_ANSWER, Refusal.FAILED)) {
            val e = Refusals.effect(r, onHome = true, sending = true, wantsAllow = true)
            assertEquals("$r keeps the request answerable", true, e.requestStays)
            assertEquals("$r takes the card away", false, e.cardGoes)
            assertEquals("$r does not cut the card", false, e.cutCard)
            assertEquals("$r leaves «Enviando…» as a plain dismissal", false, e.showExpired)
            assertEquals("$r keeps the pending allow: the card is still there", false, e.dropWantAllow)
        }
    }

    @Test fun aCutCardKeepsTheCardAndOnlyOffersDenyFromNowOn() {
        val e = Refusals.effect(Refusal.TRUNCATED, onHome = true, sending = true, wantsAllow = true)
        assertEquals(true, e.requestStays)
        assertEquals("the card stays cut", true, e.cutCard)
        assertEquals(false, e.cardGoes)
    }

    @Test fun aRefusalThatSaysTheRequestIsGoneTakesTheCardAndItsSendingAway() {
        val e = Refusals.effect(Refusal.RESOLVED, onHome = true, sending = true, wantsAllow = true)
        assertEquals(false, e.requestStays)
        assertEquals(true, e.cardGoes)
        assertEquals("a request this phone decided and lost says it expired", true, e.showExpired)
        assertEquals("a prompt for a request that is gone can resolve nothing", true, e.dropWantAllow)
    }

    @Test fun aGoneRefusalOfSomethingThisPhoneIsNotSendingJustClearsTheCard() {
        // Someone answered it on the computer while the phone held the card: nothing of this phone's was in flight.
        val e = Refusals.effect(Refusal.RESOLVED, onHome = true, sending = false, wantsAllow = false)
        assertEquals(true, e.cardGoes)
        assertEquals("there is no «Enviando…» of this phone's to expire", false, e.showExpired)
        assertEquals(false, e.dropWantAllow)
        // And a request the phone does not even hold: nothing on Home to change but the sentence.
        val unseen = Refusals.effect(Refusal.EXPIRED, onHome = false, sending = false, wantsAllow = false)
        assertEquals(true, unseen.cardGoes)
        assertEquals(false, unseen.showExpired)
    }
}
