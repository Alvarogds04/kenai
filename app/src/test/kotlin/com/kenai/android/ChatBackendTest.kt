package com.kenai.android

import com.kenai.android.core.ChatBackend
import com.kenai.android.core.ChatConversationOption
import com.kenai.android.core.ChatModelOption
import com.kenai.android.core.ChatSession
import com.kenai.android.core.ChatSession.Sent
import com.kenai.android.core.ChatStatus
import com.kenai.android.link.ApprovalBook
import com.kenai.android.link.ChatConversation
import com.kenai.android.link.ChatLinkBackend
import com.kenai.android.link.ChatModel
import com.kenai.android.link.DesktopLink
import com.kenai.android.link.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire is out of the chat. A backend only has to answer four calls and say four things, and the session
 * (what the user reads) behaves the same whether those four things come from the link or from a provider the
 * phone calls itself (T1a-2).
 */
class ChatBackendTest {
    private var models: List<ChatModelOption>? = null

    /** A backend that records what it was asked and pushes the four events through its listener, as a provider would. */
    private class FakeChatBackend(
        override val maxText: Int = Protocol.CHAT_MAX_TEXT,
        private val listener: ChatBackend.Listener? = null,
    ) : ChatBackend {
        val calls = mutableListOf<String>()
        var canAsk = true
        var canSend = true

        override fun models(): Boolean { calls += "models"; return canAsk }

        override fun send(id: String, model: String, text: String, conversation: String?): Boolean {
            calls += "send:$id:$model:$text:${conversation ?: "-"}"
            return canSend
        }

        override fun cancel(id: String) { calls += "cancel:$id" }

        override fun reset() { calls += "reset" }

        // The four things a backend says, pushed by the test.
        fun pushModels(offered: List<ChatModelOption>) { listener?.onModels(offered) }
        fun pushDelta(id: String, text: String) { listener?.onDelta(id, text) }
        fun pushDone(id: String, text: String?) { listener?.onDone(id, text) }
        fun pushError(id: String, reason: String, message: String = "") { listener?.onError(id, reason, message) }
        fun pushConversations(items: List<ChatConversationOption>) { listener?.onConversations(items) }
        fun pushDeleted(id: String) { listener?.onConversationDeleted(id) }
    }

    /** The wiring AppModel does, the main thread aside: these four events are all the session needs. */
    private fun wired(): Pair<ChatSession, FakeChatBackend> {
        val session = ChatSession(newId = { "c1" })
        val backend = FakeChatBackend(listener = object : ChatBackend.Listener {
            override fun onModels(offered: List<ChatModelOption>) { models = offered }
            override fun onDelta(id: String, text: String) { session.onDelta(id, text) }
            override fun onDone(id: String, text: String?) { session.onDone(id, text) }
            override fun onError(id: String, reason: String, message: String) { session.onError(id, reason) }
        })
        return session to backend
    }

    private fun match(backend: FakeChatBackend, session: ChatSession): Sent.Ok {
        val sent = session.send("hi", "openai/gpt", connected = true, maxText = backend.maxText) as Sent.Ok
        assertTrue(backend.send(sent.id, sent.model, sent.text))
        return sent
    }

    @Test fun theFourEventsDriveTheSessionWithoutTheWire() {
        val (s, b) = wired()
        val sent = match(b, s)
        b.pushDelta(sent.id, "Hel")
        b.pushDelta(sent.id, "lo")
        assertEquals("Hello", s.messages[1].text)
        b.pushDone(sent.id, null)
        assertEquals(ChatStatus.DONE, s.messages[1].status)
        assertNull(s.running)
    }

    @Test fun aCorrectedAnswerAndAnErrorReachTheSession() {
        val (s, b) = wired()
        val sent = match(b, s)
        b.pushDelta(sent.id, "<think>hmm")
        b.pushDone(sent.id, "Answer")
        assertEquals("Answer", s.messages[1].text)
        val again = match(b, s)
        b.pushError(again.id, "provider", "the provider refused")
        assertEquals("provider", s.messages[3].reason)
        assertEquals(ChatStatus.FAILED, s.messages[3].status)
    }

    @Test fun theModelsEventIsWhatTheModelChipPicksFrom() {
        val (_, b) = wired()
        val offered = listOf(ChatModelOption("openai/gpt", "openai", "GPT"), ChatModelOption("google/g", "google", "G"))
        b.pushModels(offered)
        assertEquals(offered, models)
    }

    @Test fun aSendTheBackendCannotTakeNowEndsTheWaitWithoutLosingTheText() {
        val (s, b) = wired()
        val sent = match(b, s)
        b.pushDelta(sent.id, "half")
        b.canSend = false
        assertFalse("the backend says it cannot send now", b.send(sent.id, sent.model, sent.text))
        // What AppModel does with that false: nothing is coming, so the wait is over and the text stays.
        s.onDisconnected()
        assertEquals("half", s.messages[1].text)
        assertEquals("connection", s.messages[1].reason)
        assertEquals(ChatStatus.FAILED, s.messages[1].status)
        assertNull(s.running)
    }

    @Test fun cancelAndResetAreDelegated() {
        val (s, b) = wired()
        val sent = match(b, s)
        val canceled = s.cancel()
        b.cancel(canceled!!)
        b.reset()
        assertEquals(listOf("send:${sent.id}:openai/gpt:hi:-", "cancel:${sent.id}", "reset"), b.calls)
        assertNull(s.running)
    }

    @Test fun aBackendWithSeveralConversationsNamesTheOneAndSaysTheList() {
        // The engine's backend takes the conversation; the neutral event carries the whole list and a deletion.
        val seen = mutableListOf<String>()
        val b = FakeChatBackend(listener = object : ChatBackend.Listener {
            override fun onModels(models: List<ChatModelOption>) {}
            override fun onDelta(id: String, text: String) {}
            override fun onDone(id: String, text: String?) {}
            override fun onError(id: String, reason: String, message: String) {}
            override fun onConversations(items: List<ChatConversationOption>) { seen += "list:" + items.joinToString(",") { "${it.id}${if (it.open) "*" else ""}" } }
            override fun onConversationDeleted(id: String) { seen += "deleted:$id" }
        })
        assertTrue(b.send("c1", "openai/gpt", "hi", "c7"))
        assertEquals(listOf("send:c1:openai/gpt:hi:c7"), b.calls)
        b.pushConversations(listOf(ChatConversationOption("c0", "Chat"), ChatConversationOption("c7", "Presupuesto", open = true)))
        b.pushDeleted("c7")
        assertEquals(listOf("list:c0,c7*", "deleted:c7"), seen)
    }

    @Test fun aBackendWithOneConversationHasNoListAndNoNewOnes() {
        // The four conversation calls are no-ops on a backend that is one conversation: this is the 0.1 chat, unchanged.
        val b = FakeChatBackend()
        assertFalse(b.conversations())
        assertFalse(b.newConversation(null))
        assertFalse(b.openConversation("c0"))
        assertFalse(b.deleteConversation("c0"))
        assertEquals(emptyList<String>(), b.calls)
    }

    @Test fun theModelsRequestSaysWhetherTheBackendCouldAsk() {
        val b = FakeChatBackend()
        assertTrue(b.models())
        b.canAsk = false
        assertFalse(b.models())
        assertEquals(listOf("models", "models"), b.calls)
    }
}

/**
 * The link is the first backend: it asks the [DesktopLink] and turns the wire's four chat callbacks into the
 * neutral ones. Nothing else outside `link/` is allowed to know those types.
 */
class ChatLinkBackendTest {
    private class FakeLink : DesktopLink {
        override val approvals = ApprovalBook { 0L }
        val calls = mutableListOf<String>()
        var modelsAsked = 0
        var canSend = true

        override fun start() {}
        override fun stop() {}
        override fun decide(fingerprint: String, allow: Boolean) = false
        override fun chatModels() { modelsAsked++ }
        override fun chatSend(id: String, model: String, text: String, conversation: String?): Boolean {
            calls += "send:$id:$model:$text:${conversation ?: "-"}"
            return canSend
        }
        override fun chatCancel(id: String) { calls += "cancel:$id" }
        override fun chatReset() { calls += "reset" }
        val conversationCalls = mutableListOf<String>()
        var hasConversations = true
        override fun chatConversations(): Boolean { conversationCalls += "list"; return hasConversations }
        override fun chatNew(title: String?): Boolean { conversationCalls += "new:${title ?: "-"}"; return hasConversations }
        override fun chatOpen(id: String): Boolean { conversationCalls += "open:$id"; return hasConversations }
        override fun chatDelete(id: String): Boolean { conversationCalls += "delete:$id"; return hasConversations }
    }

    private var models: List<ChatModelOption>? = null
    private val seen = mutableListOf<String>()

    private fun backend(link: FakeLink, hasLink: Boolean = true): ChatLinkBackend = ChatLinkBackend({ if (hasLink) link else null }, events())

    private fun events() = object : ChatBackend.Listener {
        override fun onModels(models: List<ChatModelOption>) { this@ChatLinkBackendTest.models = models }
        override fun onDelta(id: String, text: String) { seen += "delta:$id:$text" }
        override fun onDone(id: String, text: String?) { seen += "done:$id:$text" }
        override fun onError(id: String, reason: String, message: String) { seen += "error:$id:$reason:$message" }
        override fun onConversations(items: List<ChatConversationOption>) { seen += "list:" + items.joinToString(",") { "${it.id}:${it.title}:${if (it.open) "open" else "-"}" } }
        override fun onConversationDeleted(id: String) { seen += "deleted:$id" }
    }

    @Test fun everyCallGoesToTheLink() {
        val link = FakeLink()
        val b = backend(link)
        assertTrue(b.models())
        assertEquals(1, link.modelsAsked)
        assertTrue(b.send("c1", "openai/gpt", "hi", "c7"))
        b.cancel("c1")
        b.reset()
        assertEquals(listOf("send:c1:openai/gpt:hi:c7", "cancel:c1", "reset"), link.calls)
    }

    @Test fun theConversationsAreTheLinksAndItsListBecomesTheNeutralOne() {
        val link = FakeLink()
        val b = backend(link)
        assertTrue(b.conversations())
        assertTrue(b.newConversation("Presupuesto"))
        assertTrue(b.openConversation("c1"))
        assertTrue(b.deleteConversation("c2"))
        assertEquals(listOf("list", "new:Presupuesto", "open:c1", "delete:c2"), link.conversationCalls)
        b.onChatConversations(listOf(ChatConversation("c0", "Chat", 5, open = true), ChatConversation("c1", "Presupuesto")))
        b.onChatDeleted("c1")
        assertEquals(listOf("list:c0:Chat:open,c1:Presupuesto:-", "deleted:c1"), seen)
    }

    @Test fun aLinkWithoutConversationsAnswersNoToAllFour() {
        // A desktop of 0.1: the chat stays the single conversation, and nothing is created, opened or deleted.
        val link = FakeLink().apply { hasConversations = false }
        val b = backend(link)
        assertFalse(b.conversations())
        assertFalse(b.newConversation(null))
        assertFalse(b.openConversation("c1"))
        assertFalse(b.deleteConversation("c1"))
    }

    @Test fun withNoLinkItSaysItCannotSendAndAsksNothing() {
        val b = backend(FakeLink(), hasLink = false)
        assertFalse("nothing is paired or the link is stopped", b.models())
        assertFalse(b.send("c1", "openai/gpt", "hi"))
        assertFalse(b.conversations())
        assertFalse(b.newConversation(null))
        b.cancel("c1") // nothing to tell
        b.reset()
    }

    @Test fun aLinkThatCannotSendIsReportedAsSuch() {
        val link = FakeLink().apply { canSend = false }
        assertFalse(backend(link).send("c1", "openai/gpt", "hi"))
    }

    @Test fun theWireEventsBecomeTheNeutralOnes() {
        val b = backend(FakeLink())
        b.onChatModels(listOf(ChatModel("openai/gpt", "openai", "GPT")))
        assertEquals(listOf(ChatModelOption("openai/gpt", "openai", "GPT")), models)
        b.onChatDelta("c1", "Hel")
        b.onChatDone("c1", "Hello")
        b.onChatError("c1", "rate", "slow down")
        assertEquals(listOf("delta:c1:Hel", "done:c1:Hello", "error:c1:rate:slow down"), seen)
    }

    @Test fun theTextLimitIsTheComputersOwnRule() {
        assertEquals(Protocol.CHAT_MAX_TEXT, backend(FakeLink()).maxText)
    }
}
