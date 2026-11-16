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

    // ── T1a-3b leftovers (L17) ───────────────────────────────────────────────

    /** Each side keeps its own model: switching shows the model of the source in use, never the other one's. */
    @Test fun everySourceKeepsItsOwnChosenModel() {
        assertTrue(model.contains("private val chatModelKey: String get() = providerSettings.activeChat?.let { \"chat_model_\$it\" } ?: \"chat_model\""))
        assertFalse("the computer's model is no longer written for everyone", model.contains("putString(\"chat_model\", id)"))
        assertTrue("the choice is saved under the key of its source", model.contains("prefs.edit().putString(chatModelKey, id).apply()"))
        assertTrue("and re-read when the source changes", model.contains("chatModel = seededModel()"))
    }

    /** A provider's `defaultModel` is what the chat uses when its list is empty or does not contain the saved model. */
    @Test fun aProvidersDefaultModelSeedsTheChatModel() {
        assertTrue("the seed is the saved model, else the provider's own default", model.contains("prefs.getString(chatModelKey, null)?.takeIf { it.isNotBlank() } ?: activeProvider?.defaultModel?.takeIf { it.isNotBlank() }"))
        assertTrue("switching to it rebuilds and seeds the new source", body("fun selectChatSource(").contains("refreshChatSource()"))
        assertTrue("saving it does too", body("private fun afterChatSourceChanged()").contains("refreshChatSource()"))
        assertTrue("the seed is taken when the source changes", body("private fun refreshChatSource()").contains("seedModel()"))
        val pick = body("private fun pickModel(")
        assertTrue("the saved model goes first", pick.contains("models.firstOrNull { it.id == saved }?.id"))
        assertTrue("then the provider's own default", pick.contains("preferred?.takeIf { wanted -> wanted.isNotBlank() && models.any { it.id == wanted } }"))
        assertTrue("and an empty list keeps the seed instead of clearing it", pick.contains("if (models.isEmpty()) return seededModel()"))
        assertTrue("the list that arrives decides with it", body("override fun onModels(").contains("chatModel = pickModel(models)"))
    }

    /** The provider's empty state speaks of the provider, not of the computer. */
    @Test fun aProvidersNoModelsStateHasItsOwnWording() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val es = File("src/main/res/values-es/strings.xml").readText()
        val chat = File("src/main/kotlin/com/kenai/android/ui/ChatScreen.kt").readText()
        assertTrue(chat.contains("R.string.chat_nomodel_provider_body"))
        assertTrue(strings.contains("name=\"chat_nomodel_provider_body\""))
        assertTrue(es.contains("Tu proveedor todavía no ofreció modelos"))
        assertTrue("the computer keeps its own words", strings.contains("name=\"chat_nomodel_title\"") && es.contains("name=\"chat_nomodel_title\""))
    }
}
