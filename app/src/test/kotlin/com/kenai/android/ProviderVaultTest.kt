package com.kenai.android

import com.kenai.android.app.BlobCipher
import com.kenai.android.app.ProviderSecrets
import com.kenai.android.app.SecretBlobs
import com.kenai.android.core.ProviderKeyState
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The blobs in a map; the app keeps them in a private preferences file. */
private class FakeBlobs : SecretBlobs {
    val map = LinkedHashMap<String, String>()
    override fun read(name: String): String? = map[name]
    override fun write(name: String, blob: String) { map[name] = blob }
    override fun remove(name: String) { map.remove(name) }
}

/**
 * Stands in for the Android Keystore: the blob never carries the key back in clear, and it can stop being readable, as a
 * blob encrypted with a key the Keystore has forgotten.
 */
private class FakeCipher : BlobCipher {
    var readable = true
    override fun encrypt(plain: String): String =
        "x" + Base64.getEncoder().encodeToString(plain.toByteArray(Charsets.UTF_8).map { (it.toInt() xor 0x5A).toByte() }.toByteArray())
    override fun decrypt(blob: String): String? {
        if (!readable || !blob.startsWith("x")) return null
        val bytes = Base64.getDecoder().decode(blob.substring(1))
        return String(bytes.map { (it.toInt() xor 0x5A).toByte() }.toByteArray(), Charsets.UTF_8)
    }
}

/**
 * The provider keys: one encrypted blob each, and the three states the screen shows. A key that cannot be read any more
 * is reported as LOST and kept — never thrown away behind the user's back the way `SecureStore.loadPairing` does, because
 * a provider key is not a pairing that can simply be made again.
 */
class ProviderVaultTest {
    private val secret = "sk-test-1234567890"
    private val blobs = FakeBlobs()
    private val cipher = FakeCipher()
    private val vault = ProviderSecrets(blobs, cipher)

    @Test fun aKeyIsStoredAndComesBack() {
        assertTrue(vault.set("nan", secret))
        assertEquals(ProviderKeyState.PRESENT, vault.state("nan"))
        assertEquals(secret, vault.key("nan"))
    }

    @Test fun theKeyIsNotOnTheDiskInClearAndNotInThePreferenceName() {
        vault.set("nan", secret)
        assertTrue(blobs.map.isNotEmpty())
        for ((name, blob) in blobs.map) {
            assertFalse("the preference name carries the key: $name", name.contains(secret))
            assertFalse("the blob carries the key in clear", blob.contains(secret))
            assertFalse("the blob carries the key in clear", blob.contains("sk-test"))
        }
    }

    @Test fun noKeyYetIsAbsentAndReadsNothing() {
        assertEquals(ProviderKeyState.ABSENT, vault.state("nan"))
        assertNull(vault.key("nan"))
    }

    @Test fun aBlobThatCannotBeDecryptedIsLostAndIsNeverThrownAway() {
        vault.set("nan", secret)
        cipher.readable = false
        assertEquals("the phone cannot read it: it says so", ProviderKeyState.LOST, vault.state("nan"))
        assertNull(vault.key("nan"))
        assertTrue("a lost blob is kept, not cleared", blobs.map.containsKey("key:nan"))
        // And it comes back if the key that encrypted it does (a backup restored, a Keystore that was only locked).
        cipher.readable = true
        assertEquals(ProviderKeyState.PRESENT, vault.state("nan"))
        assertEquals(secret, vault.key("nan"))
    }

    @Test fun clearForgetsTheProvider() {
        vault.set("nan", secret)
        vault.clear("nan")
        assertEquals(ProviderKeyState.ABSENT, vault.state("nan"))
        assertNull(vault.key("nan"))
        assertTrue(blobs.map.isEmpty())
    }

    @Test fun aBlankKeyIsTheSameAsNoKey() {
        vault.set("nan", secret)
        assertTrue(vault.set("nan", "   "))
        assertEquals(ProviderKeyState.ABSENT, vault.state("nan"))
        assertTrue(blobs.map.isEmpty())
    }

    @Test fun twoProvidersKeepTheirOwnKeys() {
        vault.set("nan", "one")
        vault.set("openai", "two")
        assertEquals("one", vault.key("nan"))
        assertEquals("two", vault.key("openai"))
        vault.clear("nan")
        assertEquals(ProviderKeyState.ABSENT, vault.state("nan"))
        assertEquals("two", vault.key("openai"))
    }

    @Test fun theProviderIdIsCheckedBeforeItBecomesAPreferenceKey() {
        for (ok in listOf("nan", "open-ai_2", "A1", "a".repeat(40))) assertTrue(ok, ProviderSecrets.validId(ok))
        for (bad in listOf("", " ", "a b", "a/b", "a.b", "ñ", "a".repeat(41), "key:nan", "a\nb")) {
            assertFalse(bad, ProviderSecrets.validId(bad))
        }
        // An id that does not pass writes nothing and reads nothing: it never reaches a preference name.
        assertFalse(vault.set("key:nan", secret))
        assertTrue(blobs.map.isEmpty())
        assertEquals(ProviderKeyState.ABSENT, vault.state("key:nan"))
        assertNull(vault.key("a b"))
    }

    @Test fun theKeyIsInNoTextThePhoneCouldShow() {
        vault.set("nan", secret)
        assertFalse("toString", vault.toString().contains(secret))
        assertFalse("state", vault.state("nan").toString().contains(secret))
        assertFalse("preference names", blobs.map.keys.joinToString().contains(secret))
        assertFalse("blobs", blobs.map.values.joinToString().contains(secret))
    }
}

/**
 * The Android half of the vault cannot run on the JVM, so it is checked in the source: the Keystore with its own alias,
 * its own preferences file, and no logging anywhere near a key.
 */
class ProviderVaultSourceTest {
    private val source get() = File("src/main/kotlin/com/kenai/android/app/ProviderVault.kt").readText()

    @Test fun theKeysLiveInTheKeystoreUnderTheirOwnAliasAndFile() {
        assertTrue(source.contains("KeyStore.getInstance("))
        assertTrue(source.contains("\"AndroidKeyStore\""))
        assertTrue(source.contains("KeyProperties.KEY_ALGORITHM_AES"))
        assertTrue(source.contains("\"AES/GCM/NoPadding\""))
        assertTrue(source.contains("setKeySize(256)"))
        assertTrue("its own alias", source.contains("ALIAS = \"kenai_provider_keys\""))
        assertTrue("its own preferences file, not the pairing's", source.contains("getSharedPreferences(\"kenai_providers\""))
        assertFalse("the pairing's alias is the pairing's", source.contains("kenai_pairing_key"))
    }

    @Test fun nothingHereEverWritesAKeyToALog() {
        for (bad in listOf("Log.", "println", "printStackTrace", "System.err", "System.out")) {
            assertFalse(bad, source.contains(bad))
        }
    }

    @Test fun aLostBlobIsReportedInsteadOfCleared() {
        val decrypt = source.substringAfter("override fun decrypt(blob: String): String?")
        assertTrue("a failed decryption is a lost key, said in the code", decrypt.contains("lost, not cleared"))
        assertFalse("the decrypting side never removes a blob", decrypt.substringBefore("private fun key()").contains("remove"))
    }

    @Test fun itIsTheKeyProviderTheBackendAsks() {
        assertTrue(source.contains("class ProviderVault(context: Context) : KeyProvider"))
        assertTrue(source.contains("override fun key(providerId: String): String? = secrets.key(providerId)"))
    }
}
