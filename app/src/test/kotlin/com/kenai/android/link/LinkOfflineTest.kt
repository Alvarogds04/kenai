package com.kenai.android.link

import com.kenai.android.core.ChatBackend
import com.kenai.android.core.ChatConversationOption
import com.kenai.android.core.ChatConversations
import com.kenai.android.core.ChatMessage
import com.kenai.android.core.ChatModelOption
import com.kenai.android.core.ChatRole
import com.kenai.android.core.ChatSession
import com.kenai.android.core.TextFile
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KN-01 AC-2 and AC-3 over a real socket: the engine stops answering, the phone keeps reading the copy of what the user
 * had and cannot write, and when the engine comes back the pairing the phone already stored brings the chat back with
 * nothing to pair again.
 */
class LinkOfflineTest {
    private val closeables = mutableListOf<AutoCloseable>()

    @After fun cleanup() = closeables.forEach { runCatching { it.close() } }

    private fun <T : AutoCloseable> T.track(): T { closeables.add(this); return this }

    /** The private file of a phone, in memory: what one run leaves for the next. */
    private class Mem(var text: String? = null) : TextFile {
        override fun read() = text
        override fun write(text: String) { this.text = text }
        override fun delete() { text = null }
    }

    /** What AppModel does with the link, the main thread and the screen aside: the copy, the session and the four events. */
    private inner class Phone(file: TextFile) : LinkListener {
        val conversations = ChatConversations.restored(file)
        val session = ChatSession(if (conversations.known) conversations.openMessages() else emptyList())
        private lateinit var link: LinkClient
        val backend = ChatLinkBackend({ link }, Events())
        var state = LinkState.DISCONNECTED
        var offered = false
        var models: List<ChatModelOption> = emptyList()

        /** How many lists this phone has been given: after a reconnect the engine sends its list again. */
        var lists = 0
            private set

        /** AppModel.chatAvailable: offered, a model allowed and a connection that works. */
        val available get() = offered && models.isNotEmpty() && state == LinkState.CONNECTED
        val messages get() = session.messages

        private inner class Events : ChatBackend.Listener {
            override fun onModels(models: List<ChatModelOption>) { this@Phone.models = models }
            override fun onDelta(id: String, text: String) { session.onDelta(id, text) }
            override fun onDone(id: String, text: String?) {
                session.onDone(id, text)
                conversations.keep(session.messages)
                conversations.persist()
            }
            override fun onError(id: String, reason: String, message: String) { session.onError(id, reason) }
            override fun onConversations(items: List<ChatConversationOption>) {
                lists++
                val showing = conversations.onList(items, session.messages, running = session.running != null)
                if (showing != null) session.load(showing)
            }
            override fun onConversationDeleted(id: String) {
                conversations.onDeleted(id)?.let { if (session.running == null) session.load(it) }
            }
        }

        // AppModel.onState: a link that is not connected offers nothing to chat with, and the copy stays.
        override fun onState(state: LinkState) {
            this.state = state
            if (state != LinkState.CONNECTED) { offered = false; models = emptyList() }
        }
        override fun onCaps(caps: Set<String>) { offered = Protocol.CAP_CHAT in caps }
        override fun onChatModels(m: List<ChatModel>) { backend.onChatModels(m) }
        override fun onChatDelta(id: String, text: String) { backend.onChatDelta(id, text) }
        override fun onChatDone(id: String, text: String?) { backend.onChatDone(id, text) }
        override fun onChatError(id: String, reason: String, message: String) { backend.onChatError(id, reason, message) }
        override fun onChatConversations(items: List<ChatConversation>) { backend.onChatConversations(items) }
        override fun onChatDeleted(id: String) { backend.onChatDeleted(id) }

        /** The pairing the phone keeps: the same payload, the same token, every time the link comes back. */
        fun start(payload: PairingPayload) {
            link = LinkClient(
                payload, "Pixel", this,
                Connector { ms -> Socket().apply { connect(InetSocketAddress(payload.host, payload.port), ms) } },
                readTimeoutMs = 2_000, pingEveryMs = 100, backoffMs = longArrayOf(50),
            )
            closeables.add(AutoCloseable { link.stop() })
            link.start()
        }

        /**
         * A question, the way the screen sends one: refused here when nothing answers. The model is the one the phone
         * remembered (`AppModel.chatModel`), which outlives the link: with the engine off the model list is empty, and
         * the refusal has to be the link's, not a missing model.
         */
        fun ask(text: String): ChatSession.Sent =
            session.send(text, models.firstOrNull()?.id ?: "openai/gpt-4o", connected = available, maxText = backend.maxText)
    }

    /** What a previous run left: the engine's list, c0 open, and what the user read of it. */
    private fun cached(): Mem = Mem().also { file ->
        val conversations = ChatConversations.restored(file)
        conversations.onList(listOf(ChatConversationOption("c0", "Chat", 1, open = true), ChatConversationOption("c1", "Presupuesto")), emptyList())
        conversations.keep(listOf(ChatMessage("a-1", ChatRole.ASSISTANT, "lo que leí")))
        conversations.persist()
    }

    private fun payload(engine: FakeEngine) =
        PairingPayload("127.0.0.1", engine.port, "f".repeat(64), "T".repeat(20), "Test engine")

    private fun await(what: String, ms: Long = 5_000, condition: () -> Boolean): Boolean {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until) {
            if (condition()) return true
            Thread.sleep(10)
        }
        throw AssertionError("waited ${ms}ms for $what")
    }

    /** The next line of this type the phone wrote, skipping the ones it writes by itself (models, the list, pings). */
    private fun FakeEngine.until(type: String): JSONObject {
        val until = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < until) {
            val line = received.poll(200, TimeUnit.MILLISECONDS) ?: continue
            if (line.contains("\"type\":\"$type\"")) return JSONObject(line)
        }
        throw AssertionError("the phone never wrote a `$type`")
    }

    @Test fun withTheEngineOffThePhoneReadsTheCopyAndWritesNothing() {
        // AC-2: nothing answers, and the chat still lists and reads what the user had; a question is refused before a
        // frame is ever built.
        val phone = Phone(cached())
        assertFalse("nothing answers", phone.available)
        assertTrue(phone.conversations.known)
        assertEquals(listOf("c0", "c1"), phone.conversations.items.map { it.id })
        assertEquals("the conversation that was open is on screen", "lo que leí", phone.messages.single().text)
        assertEquals(ChatSession.Sent.No(ChatSession.Refusal.OFFLINE), phone.ask("hola"))
        assertEquals("the refused question left nothing behind", 1, phone.messages.size)
        assertEquals("c0", phone.conversations.open)
    }

    @Test fun theEngineComesBackAndTheStoredTokenBringsTheChatBackWithoutRepairing() {
        // AC-3: the engine goes away and returns; the phone reconnects with what it already had and the chat works again.
        val file = cached()
        val engine = FakeEngine(answers = true).track()
        val phone = Phone(file)
        val pairing = payload(engine)
        phone.start(pairing)
        await("the engine's chat") { phone.available }
        assertEquals("what was read is still on screen", "lo que leí", phone.messages.single().text)
        assertEquals("c0", phone.conversations.open)

        engine.drop()
        await("the link to go down") { !phone.available }
        assertEquals("the copy stays readable", "lo que leí", phone.messages.single().text)
        assertEquals(ChatSession.Sent.No(ChatSession.Refusal.OFFLINE), phone.ask("hola"))

        await("the link to come back", 10_000) { phone.available }
        await("the engine's list again on the new connection") { phone.lists >= 2 }
        assertEquals("the engine's list is the same one", 1, phone.conversations.items.size)
        val sent = phone.ask("hola")
        assertTrue("a question is taken again: $sent", sent is ChatSession.Sent.Ok)
        sent as ChatSession.Sent.Ok
        assertTrue(phone.backend.send(sent.id, sent.model, sent.text, phone.conversations.open))
        val line = engine.until("chatSend")
        assertEquals("the open conversation is named as it was", "c0", line.getString("conversation"))
    }

    @Test fun aSendOnAConnectionThatHasNotListedItsConversationsNeverNamesOne() {
        // Nothing that matters lives only in a connection, and no connection may assert one the engine has not named on
        // it: a cached conversation of an older run is not named until this connection lists it.
        val engine = FakeEngine(answers = true).apply { answersList = false }.track()
        val phone = Phone(cached())
        phone.start(payload(engine))
        await("the engine's chat") { phone.available }
        assertEquals("the copy says c0 is open", "c0", phone.conversations.open)
        val first = phone.ask("hola") as ChatSession.Sent.Ok
        assertTrue(phone.backend.send(first.id, first.model, first.text, phone.conversations.open))
        assertFalse("the engine has not listed its conversations on this connection", engine.until("chatSend").has("conversation"))
        assertEquals("nothing answered, so the user stops it", first.id, phone.session.cancel())

        engine.answersList = true
        assertTrue(phone.backend.conversations())
        await("the list on this connection") { phone.lists >= 1 }
        val second = phone.ask("otra") as ChatSession.Sent.Ok
        assertTrue(phone.backend.send(second.id, second.model, second.text, phone.conversations.open))
        assertEquals("now it is named", "c0", engine.until("chatSend").getString("conversation"))
    }
}
