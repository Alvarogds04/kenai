package com.kenai.android

import com.kenai.android.core.ChatReasons
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level guards for the Chat screen (Compose cannot be rendered in a JVM test). */
class ChatScreenTest {
    private fun src(name: String) = File("src/main/kotlin/com/kenai/android/$name").readText()
    private val strings get() = File("src/main/res/values/strings.xml").readText()

    @Test fun everyReasonCodeHasAnEnglishSentence() {
        for (code in ChatReasons.KNOWN) assertTrue("chat_err_$code", strings.contains("name=\"chat_err_$code\""))
        assertEquals("internal", ChatReasons.key("something-new"))
        assertEquals("internal", ChatReasons.key(null))
        assertEquals("rate", ChatReasons.key("rate"))
    }

    @Test fun theScreenMapsEveryKnownReasonToItsSentence() {
        val ui = src("ui/ChatScreen.kt")
        for (code in ChatReasons.KNOWN.filter { it != "internal" }) assertTrue("reason $code", ui.contains("\"$code\" -> R.string.chat_err_$code"))
    }

    @Test fun theKeyNoteIsOnTheScreenAndNoKeyIsEverHandledHere() {
        val ui = src("ui/ChatScreen.kt")
        assertTrue(ui.contains("R.string.chat_note"))
        assertTrue(strings.contains("Uses your computer's API key") || strings.contains("Uses your computer\\'s API key"))
        // the phone has no key, address or provider call of its own
        for (word in listOf("api-key", "apiKey", "Authorization", "https://")) assertFalse(word, ui.contains(word))
    }

    @Test fun clearAsksFirstAndTellsTheComputerToForget() {
        val ui = src("ui/ChatScreen.kt")
        assertTrue(ui.contains("AlertDialog"))
        assertTrue(ui.contains("model.chatClear()"))
        assertTrue(src("app/AppModel.kt").contains("chatBackend.reset()"))
    }

    @Test fun theBoxCannotSendWhenUnavailableEmptyOrTooLong() {
        val ui = src("ui/ChatScreen.kt")
        assertTrue(ui.contains("enabled = model.chatAvailable && length in 1..limit"))
        assertTrue(ui.contains("enabled = model.chatAvailable"))
    }

    @Test fun theScreenListsTheEngineSConversationsAndOpensThem() {
        // KN-01: opening the Chat shows the engine's list, a row opens that conversation, and one button creates another.
        val ui = src("ui/ChatScreen.kt")
        assertTrue("the list is drawn", ui.contains("model.chatConversationList"))
        assertTrue("a row opens it", ui.contains("model.chatOpenConversation("))
        assertTrue("there is a way to create one", ui.contains("model.chatNewConversation()"))
        assertTrue("the list only exists when the engine has conversations", ui.contains("model.chatHasConversations"))
        assertTrue("a title the engine left blank still reads", ui.contains("R.string.chat_conversations_new"))
    }

    @Test fun deletingAConversationAsksFirstAndSaysItIsIrreversible() {
        // DESIGN Q3/Q11: the one destructive frame on the front. The user is asked, the question says it is
        // irreversible in both languages, and only then is the engine told.
        val ui = src("ui/ChatScreen.kt")
        assertTrue("the confirmation exists", ui.contains("AlertDialog"))
        assertTrue("it uses the irreversible sentence", ui.contains("R.string.chat_conversations_delete_body"))
        assertTrue("and only then deletes", ui.contains("model.chatDeleteConversation("))
        for (file in listOf("res/values/strings.xml", "res/values-es/strings.xml")) {
            assertTrue("$file must say irreversible", File("src/main/$file").readText().contains("irreversible"))
        }
        for (file in listOf("../i18n/app-strings.json", "../i18n/app-strings.es.json")) {
            assertTrue("$file must say irreversible", File(file).readText().contains("irreversible"))
        }
    }

    @Test fun aMessageNamesItsConversationAndADeletionDropsThePhoneSCopy() {
        // Q11: what the phone held for a deleted conversation goes with it, and a message says which one it belongs to.
        val model = src("app/AppModel.kt")
        assertTrue("the send names the open conversation", model.contains("chatBackend.send(sent.id, sent.model, sent.text, chatOpenId)"))
        assertTrue("a deletion is applied to the phone's copy", model.contains("conversations.onDeleted("))
        assertTrue("a list is applied to the phone's copy", model.contains("conversations.onList("))
        assertTrue("a new computer starts from nothing", model.contains("conversations.reset()"))
        // The header's rule itself is behaviour-tested in ChatTabTest (`ChatHeader.action`), not by this text.
        assertTrue("the screen asks the rule which action the header has", src("ui/ChatScreen.kt").contains("ChatHeader.action("))
    }
}
