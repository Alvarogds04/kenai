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
        )
        for ((code, refusal) in codes) assertEquals(code, refusal, Refusals.of(code))
        // Nothing the engine can refuse a code with is left unmapped.
        assertEquals(Refusal.values().toSet(), codes.values.toSet())
    }

    @Test fun everyOtherErrorCodeIsALinkErrorAsBefore() {
        // `auth` and `send` are the link's own; the rest are the chat frames' codes, which the protocol also sends as an `error`.
        for (code in listOf("auth", "send", "version", "failed", "busy", "unavailable", "rate", "limit", "")) assertNull(code, Refusals.of(code))
    }

    @Test fun aCodeThatLeavesTheRequestOpenKeepsTheCard() {
        for (r in listOf(Refusal.OFF, Refusal.TRUNCATED, Refusal.INVALID_ANSWER, Refusal.BAD_REQUEST)) {
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

    @Test fun onlyTheAmbiguousCodeNeedsAnAnswerInFlight() {
        // `bad_request` is also how the engine refuses a chat frame, so it only speaks of an answer while one is on its way.
        assertEquals(false, Refusals.onlyAnswers(Refusal.BAD_REQUEST))
        for (r in Refusal.values().filter { it != Refusal.BAD_REQUEST }) assertEquals("$r is about an answer only", true, Refusals.onlyAnswers(r))
    }
}
