package com.kenai.android

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for the providers screen (Settings > More > chat providers). Compose cannot be rendered in a JVM test, so these
 * read the sources: what the screen says, what it asks before doing, where the address is checked, and what it never
 * does with a key. The rules behind it are unit-tested in ProviderSettingsTest, ProviderVaultTest and ChatSourceTest.
 */
class ProvidersScreenTest {
    private fun src(name: String) = File("src/main/kotlin/com/kenai/android/$name").readText()
    private val ui get() = src("ui/ProvidersScreen.kt")
    private val chat get() = src("ui/ChatScreen.kt")
    private val settings get() = src("ui/SettingsScreens.kt")
    private val main get() = src("MainActivity.kt")
    private val strings get() = File("src/main/res/values/strings.xml").readText()
    private val es get() = File("src/main/res/values-es/strings.xml").readText()

    /** The body of `ProvidersScreen` only, so a helper defined below it cannot satisfy a guard about the screen. */
    private val screen get() = ui.substringAfter("fun ProvidersScreen(").substringBefore("\n}\n")

    private fun inBothLanguages(key: String, note: String = key) {
        assertTrue("$note is in English", strings.contains("name=\"$key\""))
        assertTrue("$note is in Spanish", es.contains("name=\"$key\""))
    }

    // ── What it says ─────────────────────────────────────────────────────────────────────────────

    @Test fun theExplanationAndItsDisclosureAreAlwaysOnTheScreen() {
        for (s in listOf("providers_explain", "providers_disclosure")) {
            assertTrue("the screen says $s", ui.contains("R.string.$s"))
            inBothLanguages(s)
        }
        // The disclosure is a requirement: the messages leave the phone for the provider.
        assertTrue(es.contains("Tus mensajes salen del teléfono"))
        assertTrue(strings.contains("Your messages leave the phone"))
    }

    @Test fun theProvidersListShowsTheNameTheHostAndTheKeyStateAndNeverTheWholeAddress() {
        assertTrue("the row carries the name", ui.contains("config.name"))
        assertTrue("only the host, never the path of the address", ui.contains(".host"))
        for (s in listOf("providers_key_present", "providers_key_absent", "providers_key_lost")) {
            assertTrue("the list shows $s", ui.contains("R.string.$s"))
            inBothLanguages(s)
        }
        assertTrue("the three states of a key are all covered", ui.contains("ProviderKeyState.PRESENT") && ui.contains("ProviderKeyState.LOST"))
    }

    @Test fun aLostKeySaysItCannotBeReadAndAsksForItAgain() {
        inBothLanguages("providers_key_lost_body")
        assertTrue(es.contains("No se puede leer la clave guardada: cargala de nuevo."))
        assertTrue(ui.contains("R.string.providers_key_lost_body"))
    }

    // ── What it asks first ───────────────────────────────────────────────────────────────────────

    @Test fun switchingTheSourceAsksBeforeItStartsANewConversation() {
        for (s in listOf("providers_switch_title", "providers_switch_body", "providers_switch_confirm")) inBothLanguages(s)
        assertTrue("the new conversation is said", es.contains("Vas a empezar una conversación nueva"))
        assertTrue("it is what calls the model", ui.contains("model.selectChatSource("))
        assertTrue("the question comes first", ui.contains("AlertDialog("))
    }

    @Test fun deletingAProviderAsksFirstToo() {
        for (s in listOf("providers_remove", "providers_remove_title", "providers_remove_body", "providers_remove_confirm")) inBothLanguages(s)
        assertTrue(ui.contains("model.removeProvider("))
        assertTrue("the confirmation closes only on its own button", ui.contains("R.string.providers_remove_title"))
        assertTrue("two confirmations on this screen", Regex("AlertDialog\\(").findAll(ui).count() >= 2)
    }

    // ── The address ──────────────────────────────────────────────────────────────────────────────

    @Test fun theAddressIsValidatedBeforeAnythingIsSaved() {
        val validated = ui.indexOf(".validated()")
        assertTrue("the screen asks ProviderConfig.validated()", validated >= 0)
        assertTrue("validated() comes before saveProvider()", validated < ui.indexOf("model.saveProvider("))
        inBothLanguages("providers_url_error")
        assertTrue("the error names https", es.contains("empezar con https://") && strings.contains("start with https://"))
        inBothLanguages("providers_save_error")
        assertTrue("an inline error is drawn", ui.contains("R.string.providers_url_error"))
        assertTrue("a refused save is said", ui.contains("R.string.providers_save_error"))
    }

    @Test fun theEditorStartsFromAPresetOrFromNothing() {
        assertTrue("the app's own presets", ui.contains("ProviderPresets.ALL"))
        inBothLanguages("providers_preset_other")
        assertTrue(ui.contains("R.string.providers_preset_other"))
        inBothLanguages("providers_preset_use")
        for (s in listOf("providers_name", "providers_address", "providers_model")) {
            assertTrue("the field $s", ui.contains("R.string.$s"))
            inBothLanguages(s)
        }
    }

    // ── The key ──────────────────────────────────────────────────────────────────────────────────

    @Test fun theKeyIsTypedBlindAndIsNeverKept() {
        assertTrue("a password field", ui.contains("PasswordVisualTransformation()"))
        assertTrue(ui.contains("KeyboardType.Password"))
        assertTrue("no autocorrect on a key", ui.contains("autoCorrectEnabled = false"))
        assertTrue("it opens empty: the saved key is never read back", ui.contains("mutableStateOf(\"\")"))
        assertFalse("nothing typed here survives a recreation", Regex("rememberSaveable\\s*\\{").containsMatchIn(ui))
        for (bad in listOf("Log.", "println(", "printStackTrace", "System.out", "Timber")) {
            assertFalse("the screen must never write a key out: $bad", ui.contains(bad))
        }
        assertTrue("the key goes to the Keystore through the model", ui.contains("model.setProviderKey("))
        assertTrue("and comes back out of the field at once", ui.contains("key = \"\""))
        assertTrue("it can also be forgotten", ui.contains("model.clearProviderKey("))
        inBothLanguages("providers_key_replace")
        inBothLanguages("providers_key_clear")
        inBothLanguages("providers_key_hint")
    }

    // ── TalkBack and fingers ─────────────────────────────────────────────────────────────────────

    @Test fun everyControlSaysWhatItDoesAndIsBigEnoughToHit() {
        // One helper per kind of control, each one carrying the description; the screen builds none by hand, so no
        // control can reach the user without one.
        for (helper in listOf("private fun DescribedPill(", "private fun DescribedRow(", "private fun DescribedField(", "private fun DescribedTextAction(")) {
            assertTrue(helper, ui.contains(helper))
        }
        assertTrue(Regex("""contentDescription = description""").containsMatchIn(ui))
        for (raw in listOf("PillButton(", "ListRow(", "OutlinedTextField(", "TextButton(", "clickable(")) {
            assertFalse("the screen builds $raw without a description", screen.contains(raw))
        }
        assertTrue("48 dp is the smallest thing a finger hits", ui.contains("Spacing.MIN_TOUCH.dp"))
    }

    // ── Who opens it ─────────────────────────────────────────────────────────────────────────────

    @Test fun settingsOpensItAndTheActivityDrawsIt() {
        assertTrue("a row in Settings > More", settings.contains("R.string.providers_title), onProviders)"))
        inBothLanguages("providers_title")
        assertTrue(main.contains("Screen.PROVIDERS -> ProvidersScreen(model, onBack = { screen = Screen.SETTINGS })"))
        assertTrue("Settings hands the row its own action", main.contains("onProviders = { screen = Screen.PROVIDERS }"))
    }

    @Test fun theChatsDeadEndsLeadHere() {
        assertTrue(chat.contains("Screen.PROVIDERS ->"))
        assertTrue(chat.contains("onProviders"))
        assertTrue(main.contains("onProviders = { screen = Screen.PROVIDERS }"))
    }

    // ── S10: nothing belongs to one machine ──────────────────────────────────────────────────────

    @Test fun nothingIsHardCodedToOneMachine() {
        for (bad in listOf("http://", "https://", "api.openai.com", "api.nan.builders", "litellm", "LiteLLM", "ts.net", "192.168", "tailscale", "Tailscale")) {
            assertFalse("ProvidersScreen.kt mentions $bad", ui.contains(bad))
        }
        assertTrue("the address is a field, and the presets carry their own", ui.contains("R.string.providers_address"))
    }
}
