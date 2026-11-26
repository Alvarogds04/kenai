package com.kenai.android

import com.kenai.android.core.ChatDrafts
import com.kenai.android.core.ChatMessage
import com.kenai.android.core.ChatRetry
import com.kenai.android.core.ChatRole
import com.kenai.android.core.ChatStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatComposerStateTest {
    private fun u(t: String) = ChatMessage("u", ChatRole.USER, t)
    private fun a(t: String, s: ChatStatus) = ChatMessage("a", ChatRole.ASSISTANT, t, s, if (s == ChatStatus.FAILED) "rate" else null)

    @Test fun eachConversationKeepsItsOwnDraft() {
        val d = ChatDrafts()
        d.set("c1", "one"); d.set("c2", "two"); d.set(null, "single")
        assertEquals("one", d.get("c1")); assertEquals("two", d.get("c2")); assertEquals("single", d.get(null))
        assertEquals("", d.get("c3"))
    }

    @Test fun anEmptyDraftIsForgottenAndTheOldestGoPastTheBound() {
        val d = ChatDrafts(max = 2)
        d.set("a", "1"); d.set("b", "2"); d.set("a", "")
        assertEquals("", d.get("a")); assertEquals("2", d.get("b"))
        d.set("c", "3"); d.set("d", "4")
        assertEquals("", d.get("b")); assertEquals("3", d.get("c")); assertEquals("4", d.get("d"))
    }

    @Test fun retryResendsTheTextOfTheTurnThatFailedOrWasStopped() {
        assertEquals("hello", ChatRetry.textFor(listOf(u("hello"), a("par", ChatStatus.FAILED))))
        assertEquals("second", ChatRetry.textFor(listOf(u("first"), a("ok", ChatStatus.DONE), u("second"), a("", ChatStatus.FAILED))))
    }

    @Test fun noRetryUnlessTheLastMessageIsAFailedAnswer() {
        assertNull(ChatRetry.textFor(emptyList()))
        assertNull(ChatRetry.textFor(listOf(u("x"), a("ok", ChatStatus.DONE))))
        assertNull(ChatRetry.textFor(listOf(u("x"), a("", ChatStatus.STREAMING))))
        assertNull(ChatRetry.textFor(listOf(a("alone", ChatStatus.FAILED))))
        assertNull(ChatRetry.textFor(listOf(u("x"), a("bad", ChatStatus.FAILED), a("later", ChatStatus.DONE))))
        assertNull(ChatRetry.textFor(listOf(u("  "), a("bad", ChatStatus.FAILED))))
    }


    @Test fun askAgainOnlyForTheLastFinishedAnswerAndEditForUserMessages() {
        val done = a("ok", ChatStatus.DONE)
        assertEquals("q", com.kenai.android.core.ChatAgain.textFor(listOf(u("q"), done)))
        assertNull(com.kenai.android.core.ChatAgain.textFor(listOf(u("q"), a("bad", ChatStatus.FAILED))))
        assertNull(com.kenai.android.core.ChatAgain.textFor(listOf(u("q"), a("", ChatStatus.STREAMING))))
        assertNull(com.kenai.android.core.ChatAgain.textFor(listOf(done)))
        assertTrue(com.kenai.android.core.ChatAgain.editable(u("q")))
        assertTrue(!com.kenai.android.core.ChatAgain.editable(done) && !com.kenai.android.core.ChatAgain.editable(u(" ")))
    }


    @Test fun allDraftsSurviveASaveAndRestoreRoundTrip() {
        val d = ChatDrafts()
        d.set(null, "single"); d.set("c1", "one"); d.set("c2", "two\nlines | with = odd, chars \"q\" ✓"); d.set("c3", "")
        val back = ChatDrafts.restore(d.save())
        assertEquals("single", back.get(null)); assertEquals("one", back.get("c1"))
        assertEquals("two\nlines | with = odd, chars \"q\" ✓", back.get("c2")); assertEquals("", back.get("c3"))
        // The saved form is what Compose's saveable state can hold: plain strings in a list.
        assertTrue(d.save().all { it is String })
    }

    @Test fun restoringNothingOrGarbageGivesNoDrafts() {
        assertEquals("", ChatDrafts.restore(emptyList()).get("c1"))
        assertEquals("", ChatDrafts.restore(listOf("odd-length-list")).get(null))
        assertEquals("", ChatDrafts.restore(listOf("a", "b", "dangling")).get("dangling"))
        assertEquals("b", ChatDrafts.restore(listOf("a", "b", "dangling")).get("a"))
    }

    @Test fun theBoundSurvivesARestoreToo() {
        val d = ChatDrafts(max = 2)
        d.set("a", "1"); d.set("b", "2")
        val back = ChatDrafts.restore(d.save(), max = 2)
        back.set("c", "3")
        assertEquals("", back.get("a")); assertEquals("2", back.get("b")); assertEquals("3", back.get("c"))
    }

    @Test fun editingNeverOverwritesAnUnsentDraft() {
        val m = u("old question")
        assertEquals("old question", com.kenai.android.core.ChatAgain.editDraft("", m))
        assertEquals("old question", com.kenai.android.core.ChatAgain.editDraft("   ", m))
        assertEquals("typing now\nold question", com.kenai.android.core.ChatAgain.editDraft("typing now", m))
        // Already holding that very text: nothing to add twice.
        assertEquals("old question", com.kenai.android.core.ChatAgain.editDraft("old question", m))
    }

    @Test fun theProtocolStillHasNoFrameToRewriteATurn() {
        val names = com.kenai.android.link.ClientMsg::class.java.declaredClasses.map { it.simpleName.lowercase() }
        assertTrue(names.isNotEmpty())
        assertTrue(names.none { "regenerate" in it || "edit" in it || "rewrite" in it || "retry" in it })
    }

    private val d = ChatMessage("a", ChatRole.ASSISTANT, "ok", ChatStatus.DONE)

    @Test fun rowActionsAreDecidedOnlyForTheLastTurnAndOnlyWhenSendingIsPossible() {
        val done = listOf(u("q"), d)
        val failed = listOf(u("q"), a("bad", ChatStatus.FAILED))
        com.kenai.android.core.ChatRowPlan.of(d, done, canSend = true).let { assertEquals("q", it.askAgain); assertNull(it.retry); assertTrue(!it.edit) }
        com.kenai.android.core.ChatRowPlan.of(failed[1], failed, canSend = true).let { assertEquals("q", it.retry); assertNull(it.askAgain) }
        com.kenai.android.core.ChatRowPlan.of(done[0], done, canSend = true).let { assertTrue(it.edit); assertNull(it.askAgain) }
        // An older answer offers nothing; nothing at all while sending is not possible.
        val longer = listOf(u("q1"), d, u("q2"), ChatMessage("b", ChatRole.ASSISTANT, "ok2"))
        com.kenai.android.core.ChatRowPlan.of(longer[1], longer, canSend = true).let { assertNull(it.askAgain); assertNull(it.retry) }
        for (m in done) com.kenai.android.core.ChatRowPlan.of(m, done, canSend = false).let { assertTrue(!it.edit && it.askAgain == null && it.retry == null) }
    }

    @Test fun theNamedLimitsAreTheOnesTheScreenUses() {
        assertEquals(50, ChatDrafts.DEFAULT_MAX)
        assertEquals(200, ChatDrafts.OVERFLOW)
        assertEquals("", ChatDrafts().get("x"))
    }
}
