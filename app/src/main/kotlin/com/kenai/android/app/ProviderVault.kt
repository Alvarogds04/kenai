package com.kenai.android.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kenai.android.core.ProviderKeyState
import com.kenai.android.provider.KeyProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where the encrypted blobs live: a private preferences file in the app, a map in tests. */
interface SecretBlobs {
    fun read(name: String): String?
    fun write(name: String, blob: String)
    fun remove(name: String)
}

/**
 * Turns one key into an opaque blob and back. The real one is the Android Keystore ([KeystoreCipher]); a test uses a
 * fake, so the rules around the keys are plain JVM code. [decrypt] answers null when the blob cannot be read any more (a
 * Keystore that was reset, a corrupted file), which is a [ProviderKeyState.LOST] key and not an exception.
 */
interface BlobCipher {
    fun encrypt(plain: String): String
    fun decrypt(blob: String): String?
}

/**
 * The provider keys: one encrypted blob per provider, and nothing else. The key never appears in a log, in a message, in
 * an exception or in a preference name — the name only carries the provider's id, which is checked first.
 *
 * The rules live here, over [SecretBlobs] and [BlobCipher], so they are unit-tested without Android; the Keystore itself
 * is [KeystoreCipher], reached through [ProviderVault]. Unlike `SecureStore.loadPairing`, a blob that cannot be decrypted
 * is *reported* ([ProviderKeyState.LOST]) and kept: clearing it silently would hide from the user that a key he once
 * loaded is gone, and would throw away the one thing that could come back.
 */
class ProviderSecrets(private val blobs: SecretBlobs, private val cipher: BlobCipher) {
    /** Stores [key] for [providerId]. False when the id is not a valid one; then nothing is read or written. */
    fun set(providerId: String, key: String): Boolean {
        if (!validId(providerId)) return false
        // A blank key is no key: the same as Clear, so a screen can save an emptied field.
        if (key.isBlank()) {
            blobs.remove(blobName(providerId))
            return true
        }
        blobs.write(blobName(providerId), cipher.encrypt(key))
        return true
    }

    fun clear(providerId: String) {
        if (validId(providerId)) blobs.remove(blobName(providerId))
    }

    /** Whether the phone holds a key for [providerId], and whether it can still read it. */
    fun state(providerId: String): ProviderKeyState {
        if (!validId(providerId)) return ProviderKeyState.ABSENT
        val blob = blobs.read(blobName(providerId)) ?: return ProviderKeyState.ABSENT
        return when (val plain = cipher.decrypt(blob)) {
            null -> ProviderKeyState.LOST
            else -> if (plain.isBlank()) ProviderKeyState.ABSENT else ProviderKeyState.PRESENT
        }
    }

    /**
     * The key, for the one request that needs it ([ProviderVault] is the [KeyProvider] the backend asks): null when there
     * is none, or when it cannot be read any more.
     */
    fun key(providerId: String): String? {
        if (!validId(providerId)) return null
        val blob = blobs.read(blobName(providerId)) ?: return null
        return cipher.decrypt(blob)?.takeIf { it.isNotBlank() }
    }

    /** Never the keys, and nothing that could put one in a log line by accident. */
    override fun toString(): String = "ProviderSecrets"

    companion object {
        /** Letters, digits, `-` and `_`, up to 40: an id becomes a preference name, so nothing else may get through. */
        const val ID_MAX = 40
        private val ID = Regex("[A-Za-z0-9_-]{1,$ID_MAX}")

        fun validId(providerId: String): Boolean = ID.matches(providerId)

        private fun blobName(providerId: String) = "key:$providerId"
    }
}

/**
 * The Keystore key the blobs are encrypted with: AES-256-GCM, one alias, an IV with every blob, and the key itself never
 * leaves the phone. The same pattern as `SecureStore` (the pairing), with its own alias and its own preferences file, so a
 * lost provider key does not take the pairing with it. Backups are off (`allowBackup=false`), so it stays on this device.
 */
private class KeystoreCipher : BlobCipher {
    override fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    /**
     * Null when the blob cannot be decrypted any more (a Keystore that was reset, a corrupted blob). The exception is
     * dropped, never rethrown and never printed: its text could carry a fragment of the key.
     */
    override fun decrypt(blob: String): String? = try {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_BYTES))
        }
        String(c.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    } catch (_: Exception) {
        null // lost, not cleared: the screen asks the user for the key again
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        /** Its own alias, not the pairing's: the provider keys and the pairing are lost independently. */
        const val ALIAS = "kenai_provider_keys"
        const val IV_BYTES = 12
    }
}

/**
 * The provider keys on this phone: what the provider backend asks for a key ([KeyProvider]) and what the providers screen
 * (T1a-3b) reads and changes. No key is ever returned to a screen, logged or sent anywhere but the `Authorization`
 * header of one request.
 */
class ProviderVault(context: Context) : KeyProvider {
    /** Its own file, not the settings' and not the pairing's. */
    private val prefs = context.getSharedPreferences("kenai_providers", Context.MODE_PRIVATE)
    private val secrets = ProviderSecrets(
        object : SecretBlobs {
            override fun read(name: String): String? = prefs.getString(name, null)
            override fun write(name: String, blob: String) { prefs.edit().putString(name, blob).apply() }
            override fun remove(name: String) { prefs.edit().remove(name).apply() }
        },
        KeystoreCipher(),
    )

    override fun key(providerId: String): String? = secrets.key(providerId)

    /** Loads (or replaces) the key of a provider; false when the id is not a valid one. */
    fun set(providerId: String, key: String): Boolean = secrets.set(providerId, key)

    fun clear(providerId: String) = secrets.clear(providerId)

    fun state(providerId: String): ProviderKeyState = secrets.state(providerId)

    override fun toString(): String = "ProviderVault"
}
