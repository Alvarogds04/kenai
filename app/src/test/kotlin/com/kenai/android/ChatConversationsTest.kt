package com.kenai.android

import com.kenai.android.core.ChatConversationOption
import com.kenai.android.core.ChatConversations
import com.kenai.android.core.ChatMessage
import com.kenai.android.core.ChatRole
import com.kenai.android.core.TextFile
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

    /** The private file, in memory: what a phone keeps between two runs of the app. */
    private class Mem(var text: String? = null) : TextFile {
        override fun read() = text
        override fun write(text: String) { this.text = text }
        override fun delete() { text = null }
    }

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
        assertNull("nothing was on screen before, so there is nothing to redraw", shown)
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

    @Test fun theFirstListKeepsWhatIsAlreadyOnScreen() {
        // The phone was chatting in the engine's open conversation before it knew its id: when the first list names it,
        // the messages on screen are that conversation's and must not be thrown away.
        val c = ChatConversations()
        val onScreen = listOf(msg("u-1", "pregunta"), msg("a-1", "respuesta"))
        assertNull("nothing to redraw: the screen already shows that conversation", c.onList(listOf(conv("c0", open = true), conv("c1")), onScreen))
        assertEquals("c0", c.open)
        // And they are held as c0's: switching away and back brings them again.
        assertEquals(emptyList<ChatMessage>(), c.opened("c1", onScreen))
        assertEquals(onScreen, c.opened("c0", emptyList()))
    }

    @Test fun aListTheEngineNoLongerHasDropsWhatThePhoneHeldForIt() {
        // The full list is the whole truth about what exists: a conversation that is gone cannot be opened again, and
        // what the phone held for it must not come back from the dead (a lost chatDeleted leaves the same state).
        val c = ChatConversations()
        c.onList(listOf(conv("c0", open = true), conv("c1")), emptyList())
        c.keep(listOf(msg("a-1", "de la c0")))
        assertEquals(emptyList<ChatMessage>(), c.opened("c1", listOf(msg("a-1", "de la c0"))))
        c.keep(listOf(msg("b-1", "de la c1")))
        // c1 is not in the list any more; c0 is the open one.
        assertEquals(listOf(msg("a-1", "de la c0")), c.onList(listOf(conv("c0", open = true)), listOf(msg("b-1", "de la c1"))))
        assertEquals("c0", c.open)
        assertEquals(emptyList<ChatMessage>(), c.opened("c1", emptyList()))
    }

    @Test fun nothingSwitchesUnderAnAnswerAndTheLatestListAppliesWhenItEnds() {
        // Another device can change the open conversation while this phone is still receiving an answer. Switching then
        // would orphan the stream, so the list waits for the turn to end, and the last one of those lists wins.
        val c = ChatConversations()
        c.onList(listOf(conv("c0", open = true), conv("c1")), emptyList())
        val streaming = listOf(msg("u-1", "pregunta"))
        assertNull("the list waits", c.onList(listOf(conv("c0"), conv("c1", open = true)), streaming, running = true))
        assertEquals("c0", c.open)
        assertNull("and a second list does not move it either", c.onList(listOf(conv("c0", open = true), conv("c1")), streaming, running = true))
        assertEquals("c0", c.open)
        // The turn ends and the latest list says c0 is still the open one: the screen does not move.
        assertNull(c.turnEnded(streaming))
        assertEquals("c0", c.open)
        // A turn that ends with another conversation open does move, once.
        c.onList(listOf(conv("c0"), conv("c1", open = true)), streaming, running = true)
        assertEquals(emptyList<ChatMessage>(), c.turnEnded(streaming))
        assertEquals("c1", c.open)
        assertNull("nothing is left waiting", c.turnEnded(emptyList()))
    }

    @Test fun theNextRunReadsWhatThisOneHeld() {
        // KN-01 AC-2: with the engine off the chat still has the list and the conversation that was open, so it can be read.
        val file = Mem()
        val first = ChatConversations.restored(file)
        first.onList(listOf(conv("c0", open = true), conv("c1")), emptyList())
        first.keep(listOf(msg("a-1", "hola"), msg("a-2", "qué tal")))
        first.persist() // what AppModel does when the answer is final
        val next = ChatConversations.restored(file)
        assertTrue(next.known)
        assertEquals("c0", next.open)
        assertEquals(listOf("c0", "c1"), next.items.map { it.id })
        assertEquals(listOf(msg("a-1", "hola"), msg("a-2", "qué tal")), next.openMessages())
    }

    @Test fun aDeletionIsPurgedFromTheFileToo() {
        // Q11: the engine deleted it, so it is not on the phone after a restart either.
        val file = Mem()
        val c = ChatConversations.restored(file)
        c.onList(listOf(conv("c0", open = true), conv("c1")), emptyList())
        c.keep(listOf(msg("a-1", "hola")))
        c.opened("c1", listOf(msg("a-1", "hola")))
        c.keep(listOf(msg("b-1", "de la c1")))
        c.onDeleted("c1")
        val next = ChatConversations.restored(file)
        assertEquals(listOf("c0"), next.items.map { it.id })
        assertEquals("what it held for c1 is gone", emptyList<ChatMessage>(), next.opened("c1", emptyList()))
    }

    @Test fun aFreshListThatNoLongerNamesAConversationPurgesItsCopyFromTheFile() {
        val file = Mem()
        val c = ChatConversations.restored(file)
        c.onList(listOf(conv("c0", open = true), conv("c1")), emptyList())
        c.opened("c1", emptyList())
        c.keep(listOf(msg("b-1", "de la c1")))
        c.onList(listOf(conv("c0", open = true)), listOf(msg("b-1", "de la c1")))
        val next = ChatConversations.restored(file)
        assertEquals(listOf("c0"), next.items.map { it.id })
        assertEquals(emptyList<ChatMessage>(), next.opened("c1", emptyList()))
    }

    @Test fun aDesktopOfZeroOneWritesNothingAndADesktopIsNeverOverwrittenByNothing() {
        // No list ever arrived (a desktop of 0.1): there is nothing to keep, and the chat is the single one of 0.1.
        val file = Mem()
        val c = ChatConversations.restored(file)
        c.keep(listOf(msg("a-1", "hola")))
        c.persist()
        assertNull("nothing is written for a desktop with one conversation", file.text)
        assertFalse(ChatConversations.restored(file).known)
    }

    @Test fun forgettingTheComputerDeletesTheFile() {
        val file = Mem()
        val c = ChatConversations.restored(file)
        c.onList(listOf(conv("c0", open = true)), emptyList())
        c.keep(listOf(msg("a-1", "hola")))
        c.reset()
        assertNull(file.text)
        val next = ChatConversations.restored(file)
        assertFalse(next.known)
        assertNull(next.open)
    }

    @Test fun theCopyIsWrittenWhenTheTurnEndsAndNotOnEveryPiece() {
        // One file write per turn, not one per delta: the text on screen is kept when it is final (and by persist()).
        val file = Mem()
        val c = ChatConversations.restored(file)
        c.onList(listOf(conv("c0", open = true)), emptyList())
        val before = file.text
        c.keep(listOf(msg("a-1", "H")))
        assertEquals("a piece of an answer does not rewrite the file", before, file.text)
        c.persist()
        assertTrue(file.text!!.contains("H"))
    }

    @Test fun theOpenConversationCannotBeDeletedWhileItsAnswerIsStreaming() {
        // The engine refuses to delete a conversation mid-turn; the phone must not orphan the answer it is showing either.
        val c = ChatConversations()
        c.onList(listOf(conv("c0", open = true), conv("c1")), emptyList())
        assertFalse(c.canDelete("c0", running = true))
        assertTrue("another conversation may go", c.canDelete("c1", running = true))
        assertTrue("an idle one may go", c.canDelete("c0", running = false))
    }
}
