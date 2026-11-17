package com.kenai.android

import com.kenai.android.core.ChatCache
import com.kenai.android.core.ChatCacheSnapshot
import com.kenai.android.core.ChatConversationOption
import com.kenai.android.core.ChatMessage
import com.kenai.android.core.ChatRole
import com.kenai.android.core.ChatStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The copy the phone keeps between two runs (KN-01 AC-2): the engine's list, the conversation that was open and what was
 * read of the conversations it opened, so the chat can be read with the engine off. Bounded, in app-private storage and
 * with nothing of the wire in it: a desktop of 0.1 never writes one.
 */
class ChatCacheTest {
    private fun msg(id: String, text: String = "t") = ChatMessage(id, ChatRole.ASSISTANT, text)
    private fun conv(id: String, open: Boolean = false) = ChatConversationOption(id, "T$id", 1, open)

    private val sample = ChatCacheSnapshot(
        known = true,
        items = listOf(conv("c0", open = true), conv("c1")),
        open = "c0",
        held = mapOf("c0" to listOf(msg("a-1", "hola \"ahí\"\nsegunda línea"), msg("u-2")), "c1" to listOf(msg("b-1"))),
    )

    @Test fun whatThePhoneHeldComesBackWhole() {
        assertEquals(sample, ChatCache.decode(ChatCache.encode(sample)))
    }

    @Test fun nothingHeldIsNothingRead() {
        for (empty in listOf(null, "", "   ")) assertEquals(ChatCacheSnapshot(), ChatCache.decode(empty))
    }

    @Test fun aDamagedOrUnknownFileIsNothingRatherThanAnError() {
        for (bad in listOf("not json", "{}", """{"v":2,"known":true}""", "[]", """{"v":1,"items":5}""")) {
            assertEquals(bad, ChatCacheSnapshot(), ChatCache.decode(bad))
        }
    }

    @Test fun anEntryThatIsNotAConversationIsDroppedAndARepeatedOneWithIt() {
        val text = """{"v":1,"known":true,"items":[{"id":"c0"},{"id":""},{"id":"c0"},{"id":"a/b"},{"title":"no id"}],"open":"c0"}"""
        val snap = ChatCache.decode(text)
        assertEquals(listOf("c0"), snap.items.map { it.id })
        assertEquals("c0", snap.open)
    }

    @Test fun theListIsCutAtTheEnginesLimitAndTheTitleAtThePhoneS() {
        val many = (1..250).map { conv("c$it") }
        val long = ChatCacheSnapshot(known = true, items = listOf(ChatConversationOption("c0", "x".repeat(300), 1)), open = null)
        assertEquals(ChatCache.MAX_CONVERSATIONS, ChatCache.decode(ChatCache.encode(ChatCacheSnapshot(known = true, items = many))).items.size)
        assertEquals(120, ChatCache.decode(ChatCache.encode(long)).items.single().title.length)
    }

    @Test fun onlyTheConversationsOpenedLastKeepACopy() {
        // Bounded size: the copy is what the user may want to read again, not an archive of everything.
        val items = (1..ChatCache.MAX_HELD + 2).map { conv("c$it") }
        val held = items.associate { it.id to listOf(msg("m-${it.id}")) }
        val snap = ChatCache.decode(ChatCache.encode(ChatCacheSnapshot(known = true, items = items, held = held)))
        assertEquals(ChatCache.MAX_HELD, snap.held.size)
        assertFalse("the oldest opened goes first", snap.held.containsKey("c1"))
        assertTrue("the newest opened stays", snap.held.containsKey("c${items.size}"))
    }

    @Test fun aConversationKeepsItsNewestMessagesAndTheBeginningOfEach() {
        val messages = (1..ChatCache.MAX_MESSAGES + 10).map { msg("m$it", "line $it") } +
            msg("last", "y".repeat(ChatCache.MAX_TEXT + 500))
        val snap = ChatCache.decode(
            ChatCache.encode(ChatCacheSnapshot(known = true, items = listOf(conv("c0")), held = mapOf("c0" to messages))),
        )
        val back = snap.held.getValue("c0")
        assertEquals(ChatCache.MAX_MESSAGES, back.size)
        assertEquals("last", back.last().id)
        assertEquals(ChatCache.MAX_TEXT, back.last().text.length)
        assertEquals("m12", back.first().id)
    }

    @Test fun aConversationTheListNoLongerNamesKeepsNoCopy() {
        // The list is the whole truth about what exists: a gone conversation cannot be opened again, so its text is not kept.
        val snap = ChatCacheSnapshot(known = true, items = listOf(conv("c0")), open = "c0", held = mapOf("c9" to listOf(msg("x-1"))))
        val back = ChatCache.decode(ChatCache.encode(snap))
        assertEquals(emptyMap<String, List<ChatMessage>>(), back.held)
        val weird = ChatCache.decode("""{"v":1,"known":true,"items":[{"id":"c0"}],"held":[{"id":"c9","messages":[{"id":"x-1","role":"assistant","text":"t"}]}]}""")
        assertEquals(emptyMap<String, List<ChatMessage>>(), weird.held)
    }

    @Test fun anOpenConversationThatIsNotInTheListIsNotOpen() {
        val snap = ChatCacheSnapshot(known = true, items = listOf(conv("c0")), open = "c9")
        assertNull(ChatCache.decode(ChatCache.encode(snap)).open)
        assertNull(ChatCache.decode("""{"v":1,"known":true,"items":[],"open":"c9"}""").open)
    }

    @Test fun anAnswerThatWasStillArrivingReadsAsInterrupted() {
        val snap = ChatCacheSnapshot(
            known = true, items = listOf(conv("c0")), open = "c0",
            held = mapOf("c0" to listOf(ChatMessage("a-1", ChatRole.ASSISTANT, "half", ChatStatus.STREAMING))),
        )
        val back = ChatCache.decode(ChatCache.encode(snap)).held.getValue("c0").single()
        assertEquals(ChatStatus.FAILED, back.status)
        assertEquals("interrupted", back.reason)
        assertEquals("half", back.text)
    }

    @Test fun theCopyHoldsNoSecret() {
        // AC-4 by construction: a conversation carries ids, roles, text and a reason, and nothing else, so nothing else
        // can be written. The reason is a fixed code and the only one a phone-side failure leaves is "interrupted".
        val text = ChatCache.encode(
            ChatCacheSnapshot(
                known = true, items = listOf(conv("c0")), open = "c0",
                held = mapOf("c0" to listOf(ChatMessage("a-1", ChatRole.ASSISTANT, "hi", ChatStatus.FAILED, "interrupted"))),
            ),
        )
        for (word in listOf("key", "token", "authorization", "provider")) assertFalse(word, text.contains(word, ignoreCase = true))
    }
}
