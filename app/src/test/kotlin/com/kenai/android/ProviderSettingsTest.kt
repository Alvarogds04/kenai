package com.kenai.android

import com.kenai.android.core.ChatModelOption
import com.kenai.android.core.KeyValueStore
import com.kenai.android.core.ProviderPresets
import com.kenai.android.core.ProviderSettings
import com.kenai.android.provider.ProviderConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the user configured for the phone's own chat: the providers themselves and which one the chat talks to. One JSON
 * document, bounded and tolerant: a corrupt one means no providers, never a crash, and the default is the computer, as in
 * 0.1.
 */
class ProviderSettingsTest {
    private class Memory : KeyValueStore {
        val map = HashMap<String, Any>()
        override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
        override fun getInt(key: String, default: Int) = map[key] as? Int ?: default
        override fun getFloat(key: String, default: Float) = map[key] as? Float ?: default
        override fun getString(key: String, default: String) = map[key] as? String ?: default
        override fun put(key: String, value: Any) { map[key] = value }
    }

    private fun config(id: String = "nan", baseUrl: String = "https://api.nan.builders/v1", models: List<ChatModelOption> = emptyList()) =
        ProviderConfig(id, "NaN", baseUrl, "nan-default", models)

    private fun model(i: Int) = ChatModelOption("nan/m$i", "nan", "M$i")

    @Test fun anEmptyStoreMeansNoProviderAndTheComputerAsIn01() {
        val s = ProviderSettings.load(Memory())
        assertTrue(s.providers.isEmpty())
        assertNull("the chat goes through the computer, exactly as in 0.1", s.activeChat)
        assertNull(s.activeProvider())
    }

    @Test fun aRoundTripKeepsTheProvidersAndTheChoice() {
        val store = Memory()
        val saved = ProviderSettings(
            providers = listOf(
                config(models = listOf(model(1), model(2))),
                ProviderConfig("openai", "OpenAI", "https://api.openai.com/v1", "gpt-x"),
            ),
            activeChat = "openai",
        )
        ProviderSettings.save(store, saved)
        val loaded = ProviderSettings.load(store)
        assertEquals(saved, loaded)
        assertEquals("openai", loaded.activeProvider()?.id)
        assertEquals("https://api.openai.com/v1", loaded.activeProvider()?.baseUrl)
        assertEquals(listOf(model(1), model(2)), loaded.providers.first().models)
    }

    @Test fun atMostEightProvidersAreKept() {
        val full = (1..ProviderSettings.MAX_PROVIDERS).fold(ProviderSettings()) { s, i -> s.with(config("p$i")) }
        assertEquals(8, full.providers.size)
        assertTrue("a ninth is refused, not a silent removal of another one", full.with(config("p9")) === full)

        val store = Memory()
        ProviderSettings.save(store, full)
        assertEquals(8, ProviderSettings.load(store).providers.size)
    }

    @Test fun aDocumentWithTooManyProvidersOrModelsIsCutToTheCaps() {
        val providers = (1..12).joinToString(",") { i ->
            val models = (1..200).joinToString(",") { m -> """{"id":"p$i/m$m","provider":"p$i","label":"M$m"}""" }
            """{"id":"p$i","name":"P$i","baseUrl":"https://p$i.example/v1","defaultModel":"m","models":[$models]}"""
        }
        val raw = """{"version":1,"providers":[$providers],"activeChat":"p1"}"""
        val s = ProviderSettings.decode(raw)
        assertEquals(ProviderSettings.MAX_PROVIDERS, s.providers.size)
        assertEquals(ProviderSettings.MAX_MODELS, s.providers.first().models.size)
    }

    @Test fun aCorruptDocumentMeansNoProvidersAndNeverACrash() {
        for (raw in listOf("{", "not a document", "[]", "{}", """{"version":99,"providers":[]}""", """{"version":1,"providers":"no"}""", "\u0000")) {
            val s = ProviderSettings.decode(raw)
            assertTrue("$raw", s.providers.isEmpty())
            assertNull(s.activeChat)
        }
        // A half-broken document keeps what can be read.
        val raw = """{"version":1,"providers":[{"id":"nan","name":"NaN","baseUrl":"https://api.nan.builders/v1","defaultModel":"nan-default"},"junk",{"id":"bad","baseUrl":"http://192.168.1.9:4000/v1"}],"activeChat":"nan"}"""
        val s = ProviderSettings.decode(raw)
        assertEquals(listOf("nan"), s.providers.map { it.id })
        assertEquals("nan", s.activeChat)
    }

    @Test fun aChoicePointingAtAProviderThatIsNotThereMeansTheComputer() {
        val store = Memory()
        val s = ProviderSettings(providers = listOf(config("nan")), activeChat = "gone")
        ProviderSettings.save(store, s)
        assertNull(ProviderSettings.load(store).activeChat)
        assertNull(ProviderSettings(providers = listOf(config("nan")), activeChat = "gone").activeProvider())
    }

    @Test fun aToggleToTheComputerIsTheDefaultAndStaysThat() {
        val s = ProviderSettings(providers = listOf(config("nan")), activeChat = "nan").with(config("openai"))
        assertEquals("nan", s.activeChat)
        assertEquals(listOf("nan", "openai"), s.providers.map { it.id })
        assertEquals("the computer", null, s.copy(activeChat = null).activeChat)
    }

    @Test fun replacingAProviderKeepsItsPlaceInTheList() {
        val s = ProviderSettings(providers = listOf(config("nan"), config("openai"))).with(config("nan", "https://api.nan.builders/v2"))
        assertEquals(listOf("nan", "openai"), s.providers.map { it.id })
        assertEquals("https://api.nan.builders/v2", s.providers.first().baseUrl)
    }

    @Test fun removingTheActiveProviderGoesBackToTheComputer() {
        val s = ProviderSettings(providers = listOf(config("nan"), config("openai")), activeChat = "nan").without("nan")
        assertEquals(listOf("openai"), s.providers.map { it.id })
        assertNull(s.activeChat)
        assertEquals("openai", ProviderSettings(providers = listOf(config("nan"), config("openai")), activeChat = "openai").without("nan").activeChat)
    }

    @Test fun anAddressThatCannotBeUsedIsNeverStored() {
        val store = Memory()
        // Plain http on a machine at home cannot work (the app refuses cleartext) and nobody's LAN address belongs in a document.
        ProviderSettings.save(store, ProviderSettings(providers = listOf(config("lan", "http://192.168.1.9:4000/v1"))))
        assertTrue(ProviderSettings.load(store).providers.isEmpty())
    }

    @Test fun theFreeTextIsBounded() {
        val long = "x".repeat(5000)
        val store = Memory()
        ProviderSettings.save(
            store,
            ProviderSettings(listOf(ProviderConfig("nan", long, "https://api.nan.builders/v1", long, listOf(ChatModelOption(long, long, long))))),
        )
        val raw = store.map["providers"] as String
        assertTrue("the document stays small: ${raw.length}", raw.length < 2000)
        val loaded = ProviderSettings.load(store).providers.first()
        assertTrue(loaded.name.length <= ProviderSettings.NAME_MAX)
        assertTrue(loaded.defaultModel.length <= ProviderSettings.MODEL_MAX)
        assertTrue(loaded.models.first().label.length <= ProviderSettings.MODEL_MAX)
    }

    @Test fun thePresetsAreOnlySuggestionsAndEveryOneOfThemIsValid() {
        for (preset in ProviderPresets.ALL) {
            val config = preset.asConfig()
            assertEquals("a preset must produce a provider the app can use", preset.asConfig(), config.validated())
        }
        assertEquals("https://api.nan.builders/v1", ProviderPresets.NAN.baseUrl)
        assertEquals("nan-default", ProviderPresets.NAN.defaultModel)
        assertEquals("https://api.openai.com/v1", ProviderPresets.OPENAI.baseUrl)
        assertEquals(listOf("nan", "openai"), ProviderPresets.ALL.map { it.id })
    }

    @Test fun noPresetPointsAtAnybodysOwnMachine() {
        // S10: whatever is shipped works on another person's phone, so no home address, no tunnel name, no key.
        for (preset in ProviderPresets.ALL) {
            val url = preset.baseUrl
            assertFalse(url, url.contains("192.168") || url.contains("127.0.0.1") || url.contains("localhost"))
            assertFalse(url, url.contains("ts.net") || url.contains("tailscale") || url.contains(":4000"))
            assertTrue(url, url.startsWith("https://"))
            assertEquals(preset.id, preset.asConfig().id)
        }
        assertTrue("no LiteLLM preset: that address belongs to each user", ProviderPresets.ALL.none { it.id == "litellm" })
    }

    @Test fun aPresetAddsNothingUntilTheUserSavesIt() {
        val store = Memory()
        assertTrue(ProviderSettings.load(store).providers.isEmpty())
        // Copying a preset is a copy: the presets themselves are constants, not user data.
        val saved = ProviderSettings.load(store).with(ProviderPresets.NAN.asConfig())
        assertEquals(1, saved.providers.size)
        assertTrue("nothing was written just by reading the constants", ProviderSettings.load(store).providers.isEmpty())
    }

    @Test fun theSettingsFileIsWhereTheChatLooksForIt() {
        val source = File("src/main/kotlin/com/kenai/android/core/ProviderSettings.kt").readText()
        assertTrue(source.contains("const val KEY = \"providers\""))
        assertTrue(source.contains("const val VERSION = 1"))
        // Pure Kotlin: no Android, no Keystore, no key anywhere near the document.
        for (bad in listOf("android.content", "android.security", "android.util", "androidx", "KeyStore", "apiKey", "Authorization", "Log.")) {
            assertFalse(bad, source.contains(bad))
        }
    }
}
