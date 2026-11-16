package com.kenai.android.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The SSE reader on its own: one line in, one event out. The OpenAI-compatible stream is `data:` lines whose payload is
 * a JSON chunk with `choices[0].delta.content`; `[DONE]` ends it. Comments, other fields and junk are not errors.
 */
class SseReaderTest {
    private fun feed(vararg lines: String): List<SseReader.Event> =
        lines.mapNotNull { SseReader().feed(it) }

    @Test fun aDeltaIsItsContent() {
        val e = SseReader().feed("""data: {"choices":[{"delta":{"content":"Hi"}}]}""")
        assertEquals(SseReader.Event.Delta("Hi"), e)
    }

    @Test fun aRoleOnlyChunkSaysNothing() {
        assertNull(SseReader().feed("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""))
        assertNull(SseReader().feed("""data: {"choices":[{"delta":{"content":""}}]}"""))
        assertNull(SseReader().feed("""data: {"choices":[]}"""))
        assertNull(SseReader().feed("""data: {"id":"only-meta"}"""))
    }

    @Test fun theDoneMarkerEndsIt() {
        assertEquals(SseReader.Event.End, SseReader().feed("data: [DONE]"))
        assertEquals(SseReader.Event.End, SseReader().feed("data:[DONE]"))
    }

    @Test fun commentsBlankLinesAndOtherFieldsAreIgnored() {
        assertNull(SseReader().feed(""))
        assertNull(SseReader().feed("   "))
        assertNull(SseReader().feed(": keep-alive"))
        assertNull(SseReader().feed("event: message"))
        assertNull(SseReader().feed("id: 42"))
        assertNull(SseReader().feed("retry: 1000"))
    }

    @Test fun junkIsToleratedTheWayTheWireToleratesIt() {
        assertNull(SseReader().feed("data: not json"))
        assertNull(SseReader().feed("""data: {"choices":"nope"}"""))
        assertNull(SseReader().feed("""data: {"choices":[{"delta":"nope"}]}"""))
        assertNull(SseReader().feed("""data: {"choices":[{"delta":{"content":42}}]}"""))
        assertNull(SseReader().feed("""data: [1,2,3]"""))
        assertNull(SseReader().feed("""data: {"unclosed": """))
        assertNull(SseReader().feed("random noise"))
    }

    @Test fun aWholeMessageOnOneDataLineIsAFullAnswer() {
        assertEquals(
            SseReader.Event.Full("all at once"),
            SseReader().feed("""data: {"choices":[{"message":{"role":"assistant","content":"all at once"}}]}"""),
        )
    }

    @Test fun aLineWithNoColonIsNotAField() {
        assertNull(SseReader().feed("data"))
    }

    @Test fun theWholeAnswerOfANonStreamedBodyIsItsFirstChoice() {
        assertEquals(
            "Hello there",
            SseReader.fullAnswer("""{"choices":[{"message":{"role":"assistant","content":"Hello there"}}]}"""),
        )
        assertNull(SseReader.fullAnswer("{}"))
        assertNull(SseReader.fullAnswer("not json"))
        assertNull(SseReader.fullAnswer("""{"choices":[{"message":{}}]}"""))
    }
}
