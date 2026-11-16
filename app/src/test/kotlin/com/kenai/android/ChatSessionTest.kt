package com.kenai.android

import com.kenai.android.core.ChatIds
import com.kenai.android.core.ChatModelOption
import com.kenai.android.core.ChatModels
import com.kenai.android.core.ChatRole
import com.kenai.android.core.ChatSession
import com.kenai.android.core.ChatSession.Refusal
import com.kenai.android.core.ChatSession.Sent
import com.kenai.android.core.ChatStatus
import com.kenai.android.link.Protocol
import com.kenai.android.link.Wire
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSessionTest {
    private var n = 0
    private fun session() = ChatSession(newId = { "c${++n}" })

    /** [maxText] is the backend's own limit, so every call says which one it is talking to. */
    private fun ChatSession.ask(
        text: String = "hi", model: String? = "openai/gpt", connected: Boolean = true, maxText: Int = Protocol.CHAT_MAX_TEXT,
    ) = send(text, model, connected, maxText)

    @Test fun aQuestionIsSentOnceAndShowsUpTwiceOnScreen() {
        val s = session()
        val sent = s.ask("  Why is the sky blue?  ") as Sent.Ok
        assertEquals("c1", sent.id)
        assertEquals("openai/gpt", sent.model)
        assertEquals("Why is the sky blue?", sent.text)
        assertEquals("c1", s.running)
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT), s.messages.map { it.role })
        assertEquals(ChatStatus.STREAMING, s.messages[1].status)
        assertEquals("", s.messages[1].text)
    }

    @Test fun refusalsChangeNothing() {
        val s = session()
        assertEquals(Refusal.EMPTY, (s.ask("   ") as Sent.No).why)
        assertEquals(Refusal.TOO_LONG, (s.ask("x".repeat(Protocol.CHAT_MAX_TEXT + 1)) as Sent.No).why)
        assertEquals(Refusal.NO_MODEL, (s.ask(model = null) as Sent.No).why)
        assertEquals(Refusal.NO_MODEL, (s.ask(model = " ") as Sent.No).why)
        assertEquals(Refusal.OFFLINE, (s.ask(connected = false) as Sent.No).why)
        assertTrue(s.messages.isEmpty())
        assertNull(s.running)
        assertTrue(s.ask("x".repeat(Protocol.CHAT_MAX_TEXT)) is Sent.Ok)
    }

    @Test fun onlyOneAnswerAtATime() {
        val s = session()
        s.ask("one")
        assertEquals(Refusal.BUSY, (s.ask("two") as Sent.No).why)
        assertEquals(2, s.messages.size)
    }

    @Test fun deltasBuildTheAnswerAndDoneEndsIt() {
        val s = session()
        s.ask()
        s.onDelta("c1", "Hel"); s.onDelta("c1", "lo")
        assertEquals("Hello", s.messages[1].text)
        assertEquals(ChatStatus.STREAMING, s.messages[1].status)
        s.onDone("c1", null)
        assertEquals("Hello", s.messages[1].text)
        assertEquals(ChatStatus.DONE, s.messages[1].status)
        assertNull(s.running)
        assertTrue(s.ask("next") is Sent.Ok)
    }

    @Test fun aCorrectedAnswerReplacesTheStreamedOne() {
        val s = session()
        s.ask()
        s.onDelta("c1", "<think>hmm")
        s.onDone("c1", "Answer")
        assertEquals("Answer", s.messages[1].text)
    }

    @Test fun anErrorKeepsWhatArrivedAndSaysWhy() {
        val s = session()
        s.ask()
        s.onDelta("c1", "partial")
        s.onError("c1", "provider")
        with(s.messages[1]) {
            assertEquals("partial", text); assertEquals(ChatStatus.FAILED, status); assertEquals("provider", reason)
        }
        assertNull(s.running)
    }

    @Test fun cancelStopsWaitingAtOnceAndLateWordsAreIgnored() {
        val s = session()
        s.ask()
        s.onDelta("c1", "a")
        assertEquals("c1", s.cancel())
        assertEquals("canceled", s.messages[1].reason)
        s.onDelta("c1", "late"); s.onDone("c1", "late"); s.onError("c1", "rate")
        assertEquals("a", s.messages[1].text)
        assertEquals("canceled", s.messages[1].reason)
        assertNull(s.cancel())
    }

    @Test fun wordsForAnotherRequestChangeNothing() {
        val s = session()
        s.ask()
        s.onDelta("other", "x"); s.onDone("other", "y"); s.onError("other", "auth")
        assertEquals("", s.messages[1].text)
        assertEquals("c1", s.running)
    }

    @Test fun aDroppedLinkEndsTheWaitWithoutLosingTheText() {
        val s = session()
        s.ask(); s.onDelta("c1", "half")
        s.onDisconnected()
        assertEquals("half", s.messages[1].text)
        assertEquals("connection", s.messages[1].reason)
        assertNull(s.running)
        s.onDisconnected() // nothing running: nothing happens
    }

    @Test fun clearForgetsEverythingAndStopsWaiting() {
        val s = session()
        s.ask(); s.onDone("c1", null); s.ask("again")
        s.clear()
        assertTrue(s.messages.isEmpty())
        assertNull(s.running)
        s.onDelta("c2", "late")
        assertTrue(s.messages.isEmpty())
    }

    @Test fun onlyTheLastTwoHundredMessagesAreKept() {
        val s = session()
        repeat(150) { s.ask("q$it"); s.onDone("c${it + 1}", null) }
        assertEquals(ChatSession.MAX_MESSAGES, s.messages.size)
        assertEquals("q149", s.messages[s.messages.size - 2].text)
    }

    @Test fun anAnswerCannotGrowWithoutLimit() {
        val s = session()
        s.ask()
        repeat(10) { s.onDelta("c1", "x".repeat(10_000)) }
        assertEquals(ChatSession.MAX_TEXT, s.messages[1].text.length)
    }

    @Test fun generatedIdsAreValidAndDifferent() {
        val ids = (1..200).map { ChatIds.random() }
        assertTrue(ids.all { Wire.isChatId(it) })
        assertEquals(200, ids.toSet().size)
    }

    @Test fun theTextLimitIsTheOneTheBackendGaveNotAFixedOne() {
        val s = session()
        assertEquals(Refusal.TOO_LONG, (s.ask("abcdef", maxText = 5) as Sent.No).why)
        assertNull(s.running)
        assertTrue(s.messages.isEmpty())
        assertTrue(s.ask("abcde", maxText = 5) is Sent.Ok)
    }

    @Test fun theSessionReturnsNothingFromTheWire() {
        // The result is plain data: an id, a model and the text, all of them Strings.
        val sent = session().ask() as Sent.Ok
        assertEquals(listOf("c1", "openai/gpt", "hi"), listOf(sent.id, sent.model, sent.text))
    }

    @Test fun theChatInCoreKnowsNothingAboutTheLink() {
        // A direct provider (T1a-2) must be addable without the link types leaking into the chat's own state.
        val core = File("src/main/kotlin/com/kenai/android/core")
        val chatFiles = core.listFiles { f -> f.isFile && f.name.startsWith("Chat") && f.extension == "kt" }.orEmpty()
        assertTrue("the chat files of core are still there", chatFiles.any { it.name == "ChatBackend.kt" } && chatFiles.any { it.name == "ChatSession.kt" })
        val offenders = chatFiles.filter { f ->
            f.readLines().any { it.trimStart().startsWith("import com.kenai.android.link") }
        }.map { it.name }
        assertTrue("core chat files import the link: $offenders", offenders.isEmpty())
    }

    @Test fun theModelStaysIfStillAllowedElseTheFirst() {
        val a = ChatModelOption("openai/a", "openai", "A")
        val b = ChatModelOption("google/b", "google", "B")
        assertEquals("google/b", ChatModels.pick(listOf(a, b), "google/b"))
        assertEquals("openai/a", ChatModels.pick(listOf(a, b), "gone/x"))
        assertEquals("openai/a", ChatModels.pick(listOf(a, b), null))
        assertNull(ChatModels.pick(emptyList(), "openai/a"))
    }
}
