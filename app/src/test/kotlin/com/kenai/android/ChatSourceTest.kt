package com.kenai.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who the chat talks to. `AppModel` needs a Context, so the rules that decide it are checked in its source, next to the
 * pure rules they call (`ChatTab`, `ProviderSettings`, `ProviderVault`).
 */
class ChatSourceTest {
    private val model get() = File("src/main/kotlin/com/kenai/android/app/AppModel.kt").readText()

    /** The body of one function, so a guard cannot be satisfied by a line somewhere else in the file. */
    private fun body(signature: String): String = model.substringAfter(signature).substringBefore("\n    }\n")

    @Test fun withNoProviderChosenTheChatGoesThroughTheComputerExactlyAsIn01() {
        // The default is no choice at all: nothing is written, nothing is selected, the link is what answers.
        assertTrue(model.contains("var providerSettings by mutableStateOf(ProviderSettings.load(kv))"))
        assertTrue(model.contains("private val chatBackend: ChatBackend get() = chatDirect ?: chatLink"))
        assertTrue("no provider chosen means no provider backend at all", model.contains("val config = providerSettings.activeProvider() ?: return null"))
    }

    @Test fun aProviderIsOnlyBuiltWhenItsKeyIsHereAndItIsSeededFromTheScreen() {
        val build = body("private fun buildDirect()")
        assertTrue(build.contains("ProviderKeyState.PRESENT"))
        assertTrue(build.contains("OpenAiCompatibleBackend(config, providerVault, this)"))
        assertTrue("the conversation the phone already shows is the provider's context", build.contains("seed(chatSession.messages)"))
    }

    @Test fun aProviderWithNoKeyOrALostOneIsNeverSelected() {
        assertTrue(model.contains("if (providerVault.state(config.id) != ProviderKeyState.PRESENT) return null"))
        // …and the screen is told why instead of a chat that silently does nothing.
        assertTrue(model.contains("providerKey = activeProviderKey"))
        assertTrue(File("src/main/kotlin/com/kenai/android/core/ChatTab.kt").readText().contains("ProviderKeyState.ABSENT -> ChatTabState.NO_KEY"))
    }

    @Test fun switchingTheSourceResetsBothBackendsAndStartsANewConversation() {
        val select = body("fun selectChatSource(")
        assertTrue("the computer forgets too: each side keeps its own conversation", select.contains("chatLink.reset()"))
        assertTrue(select.contains("chatDirect?.reset()"))
        assertTrue(select.contains("chatSession.clear()"))
        assertTrue(select.contains("chatHistory.clear()"))
        assertTrue(select.contains("activeChat = id"))
        assertTrue("the new side is asked for its models", select.contains("chatRefreshModels()"))
    }

    @Test fun choosingSomethingThatIsNotAProviderMeansTheComputer() {
        val select = body("fun selectChatSource(")
        assertTrue(select.contains("providerSettings.providers.any { it.id == wanted }"))
        assertTrue(model.contains("fun selectChatSource(providerId: String?)"))
    }

    @Test fun clearResetsTheBackendThatIsInUse() {
        val clear = body("fun chatClear()")
        assertTrue(clear.contains("chatBackend.reset()"))
    }

    @Test fun aLinkThatGoesAwayIsNoLongerTheProvidersBusiness() {
        val lost = body("private fun chatLinkLost()")
        assertTrue(lost.contains("if (directActive) return"))
        assertTrue("the provider's models stay", lost.substringAfter("if (directActive) return").contains("chatModels = emptyList()"))
    }

    @Test fun aFailedModelListReachesTheScreenAndIsNotLost() {
        val error = body("override fun onError(id: String, reason: String, message: String)")
        assertTrue("an error with no answer id is about the source", error.contains("id.isEmpty()"))
        assertTrue(error.contains("chatSourceError = reason"))
    }

    @Test fun theComposerLimitComesFromTheBackendInUse() {
        assertTrue(model.contains("val chatMaxText: Int get() = chatBackend.maxText"))
    }

    @Test fun noAddressOrKeyIsHardCodedHere() {
        // S10: the phone calls the provider the user configured; the app itself names none.
        for (bad in listOf("https://", "http://", "apiKey", "api.nan.builders", "litellm", "Bearer", "192.168", "ts.net")) {
            assertFalse("AppModel.kt mentions $bad", model.contains(bad))
        }
        assertEquals("the addresses live in the presets and in the user's settings, not here", false, model.contains("openai.com"))
    }
}
