package com.kenai.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KN-02 (kenai-0.2 S14 «3b»): the direct providers of T1a-2, T1a-3a and T1a-3b are archived on `archive/direct-providers`
 * and are not part of this app any more. The phone talks to no provider itself and holds no key: the chat is the chat of
 * 0.1 over the link. What stays is the one seam that will take the engine later, `ChatBackend` (T1a-1), with the link as
 * its only implementation.
 *
 * A source guard, like `KeepAliveSourceTest` or the note test in `ChatScreenTest`: the removal is a property of the tree
 * (no file, no screen, no key field), and only reading the tree proves it.
 */
class NoDirectProvidersTest {
    private val main = File("src/main")

    private fun files(): List<File> = main.walkTopDown().filter { it.isFile }.toList()

    /** The files of the app whose text contains [word], relative to `src/main`, sorted so a failure reads well. */
    private fun holders(word: String): List<String> =
        files().filter { it.readText().contains(word) }.map { it.path.removePrefix("src/main/") }.sorted()

    private fun source(path: String): File = File(main, path)

    @Test fun theAppNamesNoProviderPackageAndNoProviderKey() {
        // A provider the phone called itself needed three things: a package of its own, a vault for the key and the header
        // that carried it. None of the three survives the update (AC-01, AC-03).
        for (word in listOf("provider/", "ProviderVault", "apiKey", "Authorization")) {
            assertEquals("«$word» is gone from the app", emptyList<String>(), holders(word))
        }
    }

    @Test fun theProviderFilesAreGone() {
        for (path in listOf(
            "kotlin/com/kenai/android/provider",
            "kotlin/com/kenai/android/app/ProviderVault.kt",
            "kotlin/com/kenai/android/core/ProviderSettings.kt",
            "kotlin/com/kenai/android/ui/ProvidersScreen.kt",
        )) {
            assertFalse("$path is archived, not in the app", source(path).exists())
        }
    }

    @Test fun thereIsNoProvidersScreenAndNoRowPointingAtIt() {
        // AC-01: Ajustes has no «Chat con proveedores» and no field where a key could be typed.
        assertEquals("no code names the screen", emptyList<String>(), holders("Screen.PROVIDERS"))
        assertEquals("no code draws the providers' strings", emptyList<String>(), holders("R.string.providers_"))
        val settings = source("kotlin/com/kenai/android/ui/SettingsScreens.kt").readText()
        assertFalse("Settings › More has no row for it", settings.contains("onProviders"))
        assertFalse("the screen hides no keys because it has none", source("kotlin/com/kenai/android/core/SecureScreens.kt").readText().contains("PROVIDERS"))
    }

    @Test fun theKeyStatesOfAProviderAreGoneAndTheLinksOwnStatesAreAll() {
        val tab = source("kotlin/com/kenai/android/core/ChatTab.kt").readText()
        assertFalse("no key, key lost", tab.contains("NO_KEY") || tab.contains("KEY_LOST"))
        assertFalse("no provider key in the tab's rule", tab.contains("ProviderKeyState"))
        assertEquals("the states are the five of 0.1", 5, com.kenai.android.core.ChatTabState.entries.size)
    }

    @Test fun theSeamForTheEngineIsStillThereWithTheLinkAsItsImplementation() {
        // T1a-1 (e240166) is kept on purpose: the engine's client plugs in here instead of a provider.
        val model = source("kotlin/com/kenai/android/app/AppModel.kt").readText()
        assertTrue("one selection point", model.contains("private val chatBackend: ChatBackend get() = chatLink"))
        assertTrue("the seam is a file of its own", source("kotlin/com/kenai/android/core/ChatBackend.kt").exists())
        assertTrue("the link implements it", source("kotlin/com/kenai/android/link/ChatLinkBackend.kt").readText().contains(": ChatBackend"))
    }

    @Test fun theChatBehavesAsInZeroOneAndTheNoteIsTrueAgain() {
        val chat = source("kotlin/com/kenai/android/ui/ChatScreen.kt").readText()
        // The computer's sentence is the only one, and it is true: the key never reaches this phone.
        assertTrue("the 0.1 note", chat.contains("stringResource(R.string.chat_note)"))
        assertFalse("no second sentence for a source that is gone", chat.contains("chat_note_provider"))
        assertFalse("the composer keeps the computer's limit", chat.contains("model.chatMaxText"))
        assertTrue("the same limit as 0.1", chat.contains("Protocol.CHAT_MAX_TEXT"))
    }

    @Test fun theAppHoldsNoProviderSourceInTheSettingsItKeeps() {
        // The provider settings were one JSON document in the settings store; nothing reads or writes it now except the
        // one-time sweep, which only deletes it.
        assertEquals("«providers» is only named by the purge", listOf("kotlin/com/kenai/android/app/ProviderPurge.kt"), holders("\"providers\""))
    }
}
