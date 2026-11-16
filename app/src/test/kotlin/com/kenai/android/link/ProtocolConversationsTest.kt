package com.kenai.android.link

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversation part of the wire (DESIGN.md §4.2 and Appendix A): the four frames the phone sends, the two the
 * engine answers with, and the `conversation` field on `chatSend`. Additive on v1 — a desktop that knows none of
 * them drops them and the chat is the single conversation of 0.1 — so nothing here may change an old frame.
 */
class ProtocolConversationsTest {
    @Test fun thePhoneAsksForTheListCreatesOpensAndDeletes() {
        assertEquals("""{"type":"chatConversations"}""", Wire.encode(ClientMsg.ChatConversations))
        assertEquals("""{"type":"chatNew"}""", Wire.encode(ClientMsg.ChatNew(null)))
        assertEquals("""{"type":"chatNew","title":"Presupuesto"}""", Wire.encode(ClientMsg.ChatNew("Presupuesto")))
        val open = JSONObject(Wire.encode(ClientMsg.ChatOpen("c1")))
        assertEquals("chatOpen", open.getString("type"))
        assertEquals("c1", open.getString("id"))
        val delete = JSONObject(Wire.encode(ClientMsg.ChatDelete("c1")))
        assertEquals("chatDelete", delete.getString("type"))
        assertEquals("c1", delete.getString("id"))
    }

    @Test fun aChatMessageNamesItsConversationOnlyWhenItHasOne() {
        // The 0.1 frame, key for key, for a desktop that has no conversations: this is what must not change.
        val old = JSONObject(Wire.encode(ClientMsg.ChatSend("c1", "openai/gpt", "hi")))
        assertEquals(setOf("type", "id", "model", "text"), old.keys().asSequence().toSet())
        assertEquals("chatSend", old.getString("type"))
        assertEquals("hi", old.getString("text"))
        val o = JSONObject(Wire.encode(ClientMsg.ChatSend("c7", "openai/gpt-4o", "hola", "c1")))
        assertEquals("chatSend", o.getString("type"))
        assertEquals("c1", o.getString("conversation"))
        assertEquals(setOf("type", "id", "model", "text", "conversation"), o.keys().asSequence().toSet())
    }

    @Test fun theListKeepsIdsTitlesTimesAndTheOpenOne() {
        val m = Wire.decodeServer(
            """{"type":"chatConversations","items":[
                {"id":"c0","title":"Chat","updatedAt":1700000000000,"open":true},
                {"id":"c1","title":"Presupuesto","updatedAt":1700000100000,"open":false},
                {"id":"c2"}]}""",
        ) as ServerMsg.ChatConversations
        assertEquals(
            listOf(
                ChatConversation("c0", "Chat", 1700000000000, open = true),
                ChatConversation("c1", "Presupuesto", 1700000100000, open = false),
                ChatConversation("c2", "", 0, open = false),
            ),
            m.items,
        )
        assertEquals(emptyList<ChatConversation>(), (Wire.decodeServer("""{"type":"chatConversations","items":[]}""") as ServerMsg.ChatConversations).items)
    }

    @Test fun theOpenConversationMayBeNamedOnTheListInsteadOfOnTheItem() {
        // The engine's own list frame carries `open` once for the whole list; the front may keep that shape, so both are read.
        val m = Wire.decodeServer("""{"type":"chatConversations","open":"c1","items":[{"id":"c0"},{"id":"c1"}]}""") as ServerMsg.ChatConversations
        assertEquals(listOf(false, true), m.items.map { it.open })
    }

    @Test fun entriesThatAreNotConversationsAreDropped() {
        val m = Wire.decodeServer(
            """{"type":"chatConversations","items":[
                5,{"title":"no id"},{"id":""},{"id":"a b"},{"id":"c0","title":"kept"},{"id":"c0","title":"repeated"},
                {"id":"${"a".repeat(65)}","title":"too long"},{"id":"c9","title":"  two\nlines  "}]}""",
        ) as ServerMsg.ChatConversations
        assertEquals(listOf("kept", "twolines"), m.items.map { it.title })
        assertEquals(listOf("c0", "c9"), m.items.map { it.id })
        // A title cannot carry control characters or grow for ever: the engine cuts at 60, the phone accepts a little more.
        assertTrue((m.items[1].title as String).isNotEmpty())
        val long = Wire.decodeServer("""{"type":"chatConversations","items":[{"id":"c0","title":"${"x".repeat(500)}"}]}""") as ServerMsg.ChatConversations
        assertEquals(Protocol.MAX_CONVERSATION_TITLE, long.items[0].title.length)
    }

    @Test fun aListLongerThanThePhoneKeepsIsCut() {
        val many = (0 until Protocol.MAX_CONVERSATIONS + 20).joinToString(",") { """{"id":"c$it"}""" }
        val m = Wire.decodeServer("""{"type":"chatConversations","items":[$many]}""") as ServerMsg.ChatConversations
        assertEquals(Protocol.MAX_CONVERSATIONS, m.items.size)
        assertEquals("c0", m.items.first().id)
    }

    @Test fun aDeletedConversationIsTheIdAlone() {
        assertEquals(ServerMsg.ChatDeleted("c1"), Wire.decodeServer("""{"type":"chatDeleted","id":"c1"}"""))
        for (bad in listOf("""{"type":"chatDeleted"}""", """{"type":"chatDeleted","id":""}""", """{"type":"chatDeleted","id":"a/b"}""", """{"type":"chatDeleted","id":7}""")) {
            assertNull(bad, Wire.decodeServer(bad))
        }
    }

    @Test fun aLineThisBuildDoesNotKnowIsStillDropped() {
        // The engine may add a frame before this phone knows it (history, for one): the phone keeps what it had.
        assertNull(Wire.decodeServer("""{"type":"chatHistory","items":[]}"""))
        assertNull(Wire.decodeServer("""{"type":"chatConversations"}"""))
        assertNull(Wire.decodeServer("""{"type":"chatConversations","items":5}"""))
    }

    @Test fun conversationIdsUseTheChatIdAlphabet() {
        for (ok in listOf("c0", "abc-DEF_09", "a".repeat(64))) assertTrue(ok, Wire.isConversationId(ok))
        for (bad in listOf("", "a b", "é", "c/1", "a".repeat(65), "a\n")) assertTrue(bad, !Wire.isConversationId(bad))
    }
}
