package com.kenai.android.link

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversation part of the link, end to end over a socket: the frames the phone writes to an engine
 * (DESIGN.md §4.2, Appendix A) and the two it reads back. `answers = false` is a desktop of 0.1, which drops what it
 * does not know: that case must leave the chat exactly as it was.
 */
internal class FakeEngine(private val answers: Boolean) : AutoCloseable {
    val server = ServerSocket(0)
    /** Every line the phone wrote, in order, except its hello and its pings. */
    val received = LinkedBlockingQueue<String>()
    /** What the engine sends after a chatNew, chatOpen or chatDelete (and a chatDeleted when it deleted). */
    @Volatile var list = """{"type":"chatConversations","items":[{"id":"c0","title":"Chat","updatedAt":1,"open":true}]}"""
    /** False: a connection that never lists its conversations, so the phone may not name one on it. */
    @Volatile var answersList = true
    @Volatile var deleted: String? = null
    @Volatile private var out: java.io.OutputStream? = null
    /** The connections the phone opened, so a test can drop one. */
    val peers = java.util.concurrent.CopyOnWriteArrayList<Socket>()

    init {
        Thread {
            try {
                while (true) serve(server.accept())
            } catch (_: Exception) { }
        }.apply { isDaemon = true; start() }
    }

    private fun serve(s: Socket) = Thread {
        peers.add(s)
        try {
            val r = BufferedReader(InputStreamReader(s.getInputStream()))
            val o = s.getOutputStream()
            out = o
            fun send(line: String) { o.write((line + "\n").toByteArray()); o.flush() }
            while (true) {
                val line = r.readLine() ?: break
                if (line.contains("\"type\":\"ping\"")) { send("""{"type":"pong"}"""); continue }
                if (line.contains("\"type\":\"hello\"")) {
                    send("""{"type":"welcome","v":1,"desktop":"Test engine","os":"linux","caps":["chat"]}""")
                    continue
                }
                received.add(line)
                if (!answers) continue // an engine of 0.1: a frame it does not know is dropped, never answered
                when {
                    line.contains("\"type\":\"chatModels\"") -> send("""{"type":"chatModels","models":[{"id":"openai/gpt-4o"}]}""")
                    line.contains("\"type\":\"chatConversations\"") -> if (answersList) send(list)
                    line.contains("\"type\":\"chatNew\"") || line.contains("\"type\":\"chatOpen\"") || line.contains("\"type\":\"chatDelete\"") -> {
                        send(list)
                        deleted?.let { send("""{"type":"chatDeleted","id":"$it"}""") }
                    }
                }
            }
        } catch (_: Exception) { }
    }.apply { isDaemon = true; start() }

    val port get() = server.localPort
    /** The engine's host went away: the phone has to reconnect and ask again. */
    fun drop() { peers.forEach { runCatching { it.close() } }; peers.clear() }
    override fun close() { runCatching { out?.close() }; runCatching { server.close() } }
}

private class ConversationRecorder : LinkListener {
    val connected = CountDownLatch(1)
    val lists = LinkedBlockingQueue<List<ChatConversation>>()
    val deleted = LinkedBlockingQueue<String>()
    override fun onWelcome(desktopName: String, os: String) { connected.countDown() }
    override fun onChatConversations(items: List<ChatConversation>) { lists.add(items) }
    override fun onChatDeleted(id: String) { deleted.add(id) }
}

class LinkConversationsTest {
    private val closeables = mutableListOf<AutoCloseable>()

    @After fun cleanup() = closeables.forEach { runCatching { it.close() } }

    private fun <T : AutoCloseable> T.track(): T { closeables.add(this); return this }

    private fun client(engine: FakeEngine, rec: ConversationRecorder): LinkClient {
        val p = PairingPayload("127.0.0.1", engine.port, "f".repeat(64), "T".repeat(20), "Test engine")
        val connector = Connector { ms -> Socket().apply { connect(InetSocketAddress(p.host, p.port), ms) } }
        return LinkClient(p, "Pixel", rec, connector, readTimeoutMs = 2_000, pingEveryMs = 100, backoffMs = longArrayOf(50))
            .also { closeables.add(AutoCloseable { it.stop() }) }
    }

    /** The phone asks for the model list at the welcome; every conversation test gets past it first. */
    private fun FakeEngine.skipModels() { assertTrue(received.poll(5, TimeUnit.SECONDS)!!.contains("chatModels")) }

    private fun FakeEngine.next(): String = received.poll(5, TimeUnit.SECONDS) ?: throw AssertionError("nothing more was written")

    private fun JSONObject.field(name: String) = getString(name)

    @Test fun thePhoneAsksForTheListAsSoonAsChatIsOffered() {
        // The Chat screen must already have the list when it opens (KN-01), like the model list of 0.1.
        val engine = FakeEngine(answers = true).track()
        val rec = ConversationRecorder()
        client(engine, rec).start()
        assertTrue(rec.connected.await(5, TimeUnit.SECONDS))
        engine.skipModels()
        assertEquals("""{"type":"chatConversations"}""", engine.next())
    }

    @Test fun theEngineSWholeListArrivesWithItsOpenConversation() {
        val engine = FakeEngine(answers = true).track()
        val rec = ConversationRecorder()
        client(engine, rec).start()
        assertEquals(listOf(ChatConversation("c0", "Chat", 1, open = true)), rec.lists.poll(5, TimeUnit.SECONDS))
    }

    @Test fun anEngineThatKnowsNoConversationsKeepsTheSingleChatOfZeroOne() {
        // A desktop of 0.1 drops all of it: nothing is answered, so nothing is created, opened or deleted, and the
        // message goes without a conversation — exactly the 0.1 frame.
        val engine = FakeEngine(answers = false).track()
        val rec = ConversationRecorder()
        val link = client(engine, rec)
        link.start()
        assertTrue(rec.connected.await(5, TimeUnit.SECONDS))
        engine.skipModels()
        assertEquals("the list is still asked for", """{"type":"chatConversations"}""", engine.next())
        assertFalse("no list came back, so creating is refused here", link.chatNew(null))
        assertFalse(link.chatOpen("c0"))
        assertFalse(link.chatDelete("c0"))
        assertTrue(link.chatSend("c1", "openai/gpt-4o", "hola"))
        val send = JSONObject(engine.next())
        assertEquals("chatSend", send.field("type"))
        assertEquals("hola", send.field("text"))
        assertFalse("a desktop with no conversations is never told one", send.has("conversation"))
        assertNull(rec.lists.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test fun anEngineWithConversationsTakesANewOneAnOpenAndADelete() {
        val engine = FakeEngine(answers = true).track()
        val rec = ConversationRecorder()
        val link = client(engine, rec)
        link.start()
        assertTrue(rec.connected.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(ChatConversation("c0", "Chat", 1, open = true)), rec.lists.poll(5, TimeUnit.SECONDS))
        engine.skipModels()
        engine.next() // the list request of the welcome

        assertTrue(link.chatNew("Presupuesto"))
        val created = JSONObject(engine.next())
        assertEquals("chatNew", created.field("type"))
        assertEquals("Presupuesto", created.field("title"))
        rec.lists.poll(5, TimeUnit.SECONDS)

        assertTrue(link.chatOpen("c0"))
        val opened = JSONObject(engine.next())
        assertEquals("chatOpen", opened.field("type"))
        assertEquals("c0", opened.field("id"))
        rec.lists.poll(5, TimeUnit.SECONDS)

        engine.deleted = "c1"
        assertTrue(link.chatDelete("c1"))
        val delete = JSONObject(engine.next())
        assertEquals("chatDelete", delete.field("type"))
        assertEquals("c1", delete.field("id"))
        assertEquals("c1", rec.deleted.poll(5, TimeUnit.SECONDS))
    }

    @Test fun aMessageNamesTheConversationOnceTheEngineHasThem() {
        val engine = FakeEngine(answers = true).track()
        val rec = ConversationRecorder()
        val link = client(engine, rec)
        link.start()
        assertTrue(rec.connected.await(5, TimeUnit.SECONDS))
        rec.lists.poll(5, TimeUnit.SECONDS)
        engine.skipModels()
        engine.next()
        assertTrue(link.chatSend("c7", "openai/gpt-4o", "hola", "c0"))
        val send = JSONObject(engine.next())
        assertEquals("c7", send.field("id"))
        assertEquals("c0", send.field("conversation"))
    }

    @Test fun theListIsAskedAgainAfterAReconnect() {
        // Nothing that matters lives only in a connection: the list is asked for on every welcome.
        val engine = FakeEngine(answers = true).track()
        val rec = ConversationRecorder()
        val link = client(engine, rec)
        link.start()
        assertNotNull(rec.lists.poll(5, TimeUnit.SECONDS))
        engine.skipModels()
        engine.next() // the list request of the first connection
        engine.drop()
        assertNotNull("the list is asked for again after the reconnect", rec.lists.poll(5, TimeUnit.SECONDS))
    }
}
