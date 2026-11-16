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
    private val es get() = File("src/main/res/values-es/strings.xml").readText()

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

    // ── A provider the phone calls itself (T1a-3a) ─────────────────────────────────────────────

    @Test fun theComposerLimitIsTheLimitOfTheBackendInUse() {
        val ui = src("ui/ChatScreen.kt")
        assertTrue("the limit is read from the source in use", ui.contains("val limit = model.chatMaxText"))
        assertFalse("a provider accepts more than the computer's 4000", ui.contains("Protocol.CHAT_MAX_TEXT"))
        assertTrue(src("app/AppModel.kt").contains("val chatMaxText: Int get() = chatBackend.maxText"))
    }

    @Test fun aProviderWithoutItsKeySaysSoInShortTexts() {
        val ui = src("ui/ChatScreen.kt")
        for (s in listOf("chat_nokey_title", "chat_nokey_body", "chat_keylost_title", "chat_keylost_body")) {
            assertTrue("the screen maps $s", ui.contains("R.string.$s"))
            assertTrue("$s is in English", strings.contains("name=\"$s\""))
            assertTrue("$s is in Spanish", es.contains("name=\"$s\""))
        }
        assertTrue(es.contains("Cargá la clave de tu proveedor"))
        assertTrue(es.contains("No se puede leer la clave guardada: cargala de nuevo."))
    }

    @Test fun aFailedModelListIsSaidOnTheScreenInsteadOfVanishing() {
        val ui = src("ui/ChatScreen.kt")
        assertTrue(ui.contains("model.chatSourceError"))
        for (s in listOf("chat_provider_no_key", "chat_provider_auth", "chat_provider_unreachable", "chat_provider_rate", "chat_provider_failed")) {
            assertTrue(s, ui.contains("R.string.$s"))
            assertTrue("$s is in both languages", strings.contains("name=\"$s\"") && es.contains("name=\"$s\""))
        }
    }

    @Test fun theScreenStillHandlesNoKeyAndNoAddressItself() {
        // The providers screen (3b) does the keys and the addresses; this one only shows states.
        val ui = src("ui/ChatScreen.kt")
        for (word in listOf("apiKey", "Authorization", "https://", "Bearer", "ProviderVault")) assertFalse(word, ui.contains(word))
    }
}
