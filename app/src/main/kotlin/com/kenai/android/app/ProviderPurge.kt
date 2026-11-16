package com.kenai.android.app

import android.content.Context
import java.security.KeyStore

/**
 * What a phone that ran kenai-0.2 with the direct providers still holds, seen as the three things the one-time sweep
 * deletes: the preferences file of the encrypted keys, the Keystore key that encrypted them, and the provider settings in
 * the settings store. A port, so the sweep's rules are plain JVM code (see `ProviderPurgeTest`); the Android side is
 * [AndroidLegacyProviderStorage].
 *
 * A step answers true when it deleted something and false when there was nothing to delete. It *throws* when it could not
 * do it — a Keystore that refuses the alias, a write that did not happen — which is not the same as "nothing there".
 */
interface LegacyProviderStorage {
    /** Deletes the preferences file that held one encrypted blob per provider. True when there was something to delete. */
    fun dropKeyBlobs(): Boolean

    /** Deletes the Keystore key the blobs were encrypted with. True when it was there. */
    fun dropKeystoreKey(): Boolean

    /** Deletes the provider settings: the list of providers and the one the chat talked to. True when there was one. */
    fun dropProviderSettings(): Boolean

    /** Whether this phone already went through the sweep. */
    fun alreadyDone(): Boolean

    /** Records that it did, so an ordinary start reads one boolean and touches nothing else. */
    fun markDone()
}

/**
 * KN-02 (kenai-0.2 S14 «3b»), AC-02: a phone that had keys loses them when it updates. The phone no longer talks to a
 * provider of its own and the code that read those keys is gone, so nothing could ever read them again: keeping them in
 * the Keystore would keep a secret the app cannot use and the user cannot see.
 *
 * Once per install — but only once every step has actually answered. The first start after the update deletes the three
 * things and records it; every later start reads one boolean. A step that fails leaves the sweep *pending*, so the next
 * start tries again: recording it as done after a refused step would keep the Keystore key for ever, which is the one
 * thing this sweep exists to delete. A failed step never stops the other two, and nothing here logs, prints or throws —
 * a deleted key is not worth a line anywhere, and a start cannot fail because of a version that is gone.
 */
class ProviderPurge(private val storage: LegacyProviderStorage) {
    /** True when something was actually removed. */
    fun run(): Boolean {
        // A phone already swept reads one boolean and touches nothing else; a check that fails means "not swept".
        if (try {
                storage.alreadyDone()
            } catch (_: Exception) {
                false
            }
        ) {
            return false
        }
        val answers = listOf(
            attempt { storage.dropKeyBlobs() },
            attempt { storage.dropKeystoreKey() },
            attempt { storage.dropProviderSettings() },
        )
        // FAILED is a step that did not answer: the sweep stays pending until a start in which all three answer.
        if (answers.none { it == Answer.FAILED }) {
            try {
                storage.markDone()
            } catch (_: Exception) {
                // Not recorded, so the next start sweeps again. Nothing is logged: what is deleted is not described.
            }
        }
        return answers.any { it == Answer.REMOVED }
    }

    /** What one step answered: it removed something, it found nothing to remove, or it failed and must be retried. */
    private enum class Answer { REMOVED, NOTHING, FAILED }

    private fun attempt(block: () -> Boolean): Answer = try {
        if (block()) Answer.REMOVED else Answer.NOTHING
    } catch (_: Exception) {
        Answer.FAILED
    }
}

/**
 * The three leftovers on an Android phone, by the names the archived version used: the Keystore alias, its preferences
 * file, and the provider settings under one key of the app's settings. The names are written out here on purpose — this
 * is the only code left that has to know them, and it only ever deletes.
 */
class AndroidLegacyProviderStorage(private val context: Context) : LegacyProviderStorage {
    override fun dropKeyBlobs(): Boolean {
        val file = context.getSharedPreferences(KEYS_FILE, Context.MODE_PRIVATE)
        if (file.all.isEmpty()) return false
        // Committed, not applied: the deletion is the point of the sweep, it is done before any screen is drawn, and its
        // answer is the difference between "deleted" and "did not".
        if (!file.edit().clear().commit()) throw IllegalStateException("the key blobs could not be deleted")
        return true
    }

    override fun dropKeystoreKey(): Boolean {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!store.containsAlias(KEYS_ALIAS)) return false
        store.deleteEntry(KEYS_ALIAS)
        return true
    }

    override fun dropProviderSettings(): Boolean {
        val file = context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)
        if (!file.contains(SETTINGS_PROVIDERS)) return false
        if (!file.edit().remove(SETTINGS_PROVIDERS).commit()) throw IllegalStateException("the provider settings could not be deleted")
        return true
    }

    override fun alreadyDone(): Boolean =
        context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE).getBoolean(SWEPT, false)

    override fun markDone() {
        if (!context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(SWEPT, true).commit()) {
            throw IllegalStateException("the sweep could not be recorded")
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        /** The alias of the Keystore key the provider keys were encrypted with. */
        const val KEYS_ALIAS = "kenai_provider_keys"
        /** The preferences file that held the encrypted blobs. */
        const val KEYS_FILE = "kenai_providers"
        /** The app's settings file (`AppModel`'s store) and the key the provider settings were saved under. */
        const val SETTINGS_FILE = "kenai_ui"
        const val SETTINGS_PROVIDERS = "providers"
        /** The mark itself: one boolean, so a normal start never touches the Keystore. */
        const val SWEPT = "providers_purged"
    }
}
