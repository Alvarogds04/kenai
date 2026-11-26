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

    @Test fun theScreenKeepsDraftsPerConversationAndRetriesThroughTheNormalSend() {
        val ui = File("src/main/kotlin/com/kenai/android/ui/ChatScreen.kt").readText()
        assertTrue(ui.contains("ChatDrafts"))
        assertTrue(ui.contains("ChatRetry.textFor") && ui.contains("model.chatSend(retry)"))
        assertTrue(File("src/main/res/values/strings.xml").readText().contains("name=\"chat_retry\""))
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

    @Test fun theScreenOffersAskAgainAndEditAsNewTurnsAndNeverANewFrame() {
        val ui = File("src/main/kotlin/com/kenai/android/ui/ChatScreen.kt").readText()
        assertTrue(ui.contains("ChatAgain.textFor") && ui.contains("ChatAgain.editable"))
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("name=\"chat_ask_again\"") && strings.contains("name=\"chat_edit\""))
        assertTrue(!File("src/main/kotlin/com/kenai/android/link/Protocol.kt").readText().contains("chatRegenerate"))
    }
}
