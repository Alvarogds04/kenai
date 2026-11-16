package com.kenai.android

import com.kenai.android.core.ChatConversationOption
import com.kenai.android.core.ChatConversations
import com.kenai.android.core.ChatMessage
import com.kenai.android.core.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The conversation list as the phone keeps it (KN-01, DESIGN Q11): which conversation is open, what the phone holds of
 * each one it showed, and what a list or a deletion does to the messages on screen. Pure Kotlin: the screen renders it
 * and `AppModel` is the only thing that asks the backend.
 */
class ChatConversationsTest {
    private fun msg(id: String, text: String) = ChatMessage(id, ChatRole.ASSISTANT, text)
    private fun conv(id: String, open: Boolean = false, title: String = id) = ChatConversationOption(id, title, 0, open)

    @Test fun anEngineThatNeverAnsweredHasNoListAndTheChatIsTheOneOfZeroOne() {
        // A desktop of 0.1 drops the request: nothing is known, so nothing is listed and no message names a conversation.
        val c = ChatConversations()
        assertFalse(c.known)
        assertNull(c.open)
        assertEquals(emptyList<ChatConversationOption>(), c.items)
    }

    @Test fun theListSaysWhichConversationIsOpen() {
        val c = ChatConversations()
        val shown = c.onList(listOf(conv("c0", open = true), conv("c1", title = "Presupuesto")), showing = emptyList())
        assertTrue(c.known)
        assertEquals("c0", c.open)
        assertEquals(listOf("c0", "c1"), c.items.map { it.id })
        assertEquals("Presupuesto", c.items[1].title)
        assertEquals("nothing was on screen before, so nothing to swap", emptyList<ChatMessage>(), shown)
    }

    @Test fun theListIsTheWholeTruthAndARepeatedOneChangesNothing() {
        val c = ChatConversations()
        c.onList(listOf(conv("c0", open = true), conv("c1")), emptyList())
        assertNull("same open conversation: the screen does not move", c.onList(listOf(conv("c0", open = true)), emptyList()))
        assertEquals(listOf("c0"), c.items.map { it.id })
    }

    @Test fun aListThatOpensAnotherConversationSwapsWhatIsOnScreen() {
        val c = ChatConversations()
        c.onList(listOf(conv("c0", open = true), conv("c1")), emptyList())
        c.keep(listOf(msg("u-1", "hola")))
        // Another device opened c1 on the engine: the messages of c0 are put away and c1's (none yet) are shown.
        val shown = c.onList(listOf(conv("c0"), conv("c1", open = true)), listOf(msg("u-1", "hola")))
        assertEquals(emptyList<ChatMessage>(), shown)
        assertEquals("c1", c.open)
        // Back to c0: what it had is still there.
        assertEquals(listOf(msg("u-1", "hola")), c.onList(listOf(conv("c0", open = true), conv("c1")), emptyList()))
    }

    @Test fun openingAConversationShowsWhatThePhoneHeldForIt() {
        val c = ChatConversations()
        c.onList(listOf(conv("c0", open = true), conv("c1")), emptyList())
        c.keep(listOf(msg("a-1", "de la c0")))
        // The user asked for c1: the screen switches at once, without waiting for the engine's next list.
        assertEquals(emptyList<ChatMessage>(), c.opened("c1", listOf(msg("a-1", "de la c0"))))
        assertEquals("c1", c.open)
        assertEquals("the list follows the phone's own move", listOf(false, true), c.items.map { it.open })
        c.keep(listOf(msg("b-1", "de la c1")))
        assertEquals(listOf(msg("a-1", "de la c0")), c.opened("c0", listOf(msg("b-1", "de la c1"))))
        assertEquals(listOf(msg("b-1", "de la c1")), c.opened("c1", listOf(msg("a-1", "de la c0"))))
    }

    @Test fun aDeletedConversationIsDroppedFromThePhone() {
        // Q11: the engine deleted it, so what the phone held for it goes too — otherwise "delete the whole
        // conversation" would only be true on the host.
        val c = ChatConversations()
        c.onList(listOf(conv("c0", open = true), conv("c1")), emptyList())
        c.keep(listOf(msg("a-1", "de la c0")))
        c.opened("c1", listOf(msg("a-1", "de la c0")))
        c.keep(listOf(msg("b-1", "de la c1")))
        assertNull("deleting another conversation does not move the screen", c.onDeleted("c0"))
        assertEquals("c1", c.open)
        assertEquals(emptyList<ChatMessage>(), c.onDeleted("c1"))
        assertNull(c.open)
        assertEquals(emptyList<String>(), c.items.map { it.id })
        // And what it held is gone: opening it again (it cannot be opened again) shows nothing.
        assertEquals(emptyList<ChatMessage>(), c.opened("c1", emptyList()))
    }

    @Test fun aDeletionThePhoneNeverHeldIsHarmless() {
        val c = ChatConversations()
        c.onList(listOf(conv("c0", open = true)), emptyList())
        assertNull(c.onDeleted("c9"))
        assertEquals("c0", c.open)
    }

    @Test fun clearingForgetsTheCopyOfTheOpenOne() {
        val c = ChatConversations()
        c.onList(listOf(conv("c0", open = true)), emptyList())
        c.keep(listOf(msg("a-1", "hola")))
        assertEquals(emptyList<ChatMessage>(), c.cleared())
        assertNull(c.open)
        assertEquals(emptyList<ChatMessage>(), c.opened("c0", emptyList()))
    }

    @Test fun aNewPairingStartsFromNothing() {
        val c = ChatConversations()
        c.onList(listOf(conv("c0", open = true)), emptyList())
        c.keep(listOf(msg("a-1", "hola")))
        c.reset()
        assertFalse(c.known)
        assertNull(c.open)
        assertEquals(emptyList<ChatConversationOption>(), c.items)
    }

    @Test fun whatIsHeldIsNeverMoreThanTheConversationThatIsOpen() {
        // Nothing is held for a conversation the phone never showed: opening one for the first time starts empty.
        val c = ChatConversations()
        c.onList(listOf(conv("c0", open = true), conv("c1")), emptyList())
        assertEquals(emptyList<ChatMessage>(), c.opened("c1", emptyList()))
        assertEquals(emptyList<ChatMessage>(), c.opened("c0", emptyList()))
    }
}
