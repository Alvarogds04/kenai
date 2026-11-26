package com.kenai.android

import com.kenai.android.core.ChatActions
import com.kenai.android.core.ChatMessage
import com.kenai.android.core.ChatRole
import com.kenai.android.core.ChatStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatActionsTest {
    private fun msg(text: String, status: ChatStatus = ChatStatus.DONE, role: ChatRole = ChatRole.ASSISTANT) =
        ChatMessage("1", role, text, status)

    @Test fun copyTextIsTheMessageAsItWasReceived() {
        assertEquals("**a** `b`", ChatActions.copyText(msg("**a** `b`")))
    }

    @Test fun codeBlocksAreCopiedWithoutTheFencesOrTheLanguage() {
        assertEquals(listOf("val a = 1\n  b", "x"), ChatActions.codeBlocks("t\n```kotlin\nval a = 1\n  b\n```\nmid\n```\nx\n```"))
        assertEquals(emptyList<String>(), ChatActions.codeBlocks("no code `inline`"))
    }

    @Test fun anEmptyOrStillStreamingMessageOffersNoActions() {
        assertFalse(ChatActions.available(msg("")))
        assertFalse(ChatActions.available(msg("   ")))
        assertFalse(ChatActions.available(msg("partial", ChatStatus.STREAMING)))
        assertTrue(ChatActions.available(msg("done")))
        assertTrue(ChatActions.available(msg("cut", ChatStatus.FAILED)))
        assertTrue(ChatActions.available(msg("mine", role = ChatRole.USER)))
    }

    @Test fun theScreenWiresCopyAndShareThroughTheSystemOnly() {
        val ui = File("src/main/kotlin/com/kenai/android/ui/ChatScreen.kt").readText()
        assertTrue(ui.contains("ChatActions.copyText"))
        assertTrue(ui.contains("ChatActions.codeBlocks") && ui.contains("ChatActions.available"))
        assertTrue(ui.contains("Intent.ACTION_SEND"))
        for (k in listOf("chat_copy", "chat_share", "chat_copy_code")) assertTrue(k, File("src/main/res/values/strings.xml").readText().contains("name=\"$k\""))
    }
}
