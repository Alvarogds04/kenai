package com.kenai.android

import com.kenai.android.app.LegacyProviderStorage
import com.kenai.android.app.ProviderPurge
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KN-02 AC-02: «Dado un teléfono que tenía claves guardadas, cuando actualizo Kenai, entonces las claves se borran del
 * Keystore.» The rules live in [ProviderPurge], over the port [LegacyProviderStorage], so they are plain JVM code; the
 * Android side (the Keystore alias, the preferences file, the settings key) is the port's one implementation, and a source
 * guard below checks that it names what the archived version left behind.
 */
class ProviderPurgeTest {
    /**
     * A phone as the sweep sees it: it had keys, or it never did. [failOn] makes one port throw, the way a Keystore that
     * refuses the alias would; every port records the fact that it was asked, so "nothing happened" can be checked.
     */
    private class Phone(
        var blobs: Boolean = true,
        var keystoreKey: Boolean = true,
        var settings: Boolean = true,
        var done: Boolean = false,
        private val failOn: MutableSet<String> = mutableSetOf(),
    ) : LegacyProviderStorage {
        val touched = ArrayList<String>()

        /** Lets a port work again: a phone that was restarted after the Keystore came back. */
        fun allow(port: String) { failOn.remove(port) }

        private fun ask(port: String) {
            touched += port
            if (port in failOn) throw IllegalStateException("$port refused")
        }

        override fun dropKeyBlobs(): Boolean {
            ask("blobs")
            val had = blobs
            blobs = false
            return had
        }

        override fun dropKeystoreKey(): Boolean {
            ask("keystore")
            val had = keystoreKey
            keystoreKey = false
            return had
        }

        override fun dropProviderSettings(): Boolean {
            ask("settings")
            val had = settings
            settings = false
            return had
        }

        override fun alreadyDone(): Boolean {
            if ("done" in failOn) throw IllegalStateException("done refused")
            return done
        }

        override fun markDone() {
            ask("mark")
            done = true
        }
    }

    @Test fun anUpdatedPhoneLosesTheKeysItHad() {
        val phone = Phone()
        assertTrue("something was removed", ProviderPurge(phone).run())
        assertFalse("the encrypted blobs are gone", phone.blobs)
        assertFalse("the Keystore key is gone", phone.keystoreKey)
        assertFalse("the provider settings are gone", phone.settings)
        assertTrue("and the sweep is recorded", phone.done)
    }

    @Test fun theKeysAreSweptOnlyOnce() {
        val phone = Phone(done = true)
        assertFalse("nothing left to remove", ProviderPurge(phone).run())
        assertEquals("a normal start touches nothing at all", emptyList<String>(), phone.touched)
    }

    @Test fun aPhoneThatNeverHadKeysCostsOneStartAndThenNothing() {
        val phone = Phone(blobs = false, keystoreKey = false, settings = false)
        assertFalse("nothing was removed", ProviderPurge(phone).run())
        assertTrue("but the sweep is recorded", phone.done)
        phone.touched.clear()
        ProviderPurge(phone).run()
        assertEquals("the next start touches nothing", emptyList<String>(), phone.touched)
    }

    @Test fun oneSideThatRefusesStillLetsTheOthersGoAndLeavesTheSweepPending() {
        // A Keystore that will not give up the alias must not keep the blobs or the settings alive with it, and it must
        // not let the sweep be recorded as finished: the key it still holds is the whole point of the sweep, so the next
        // start has to try again.
        val phone = Phone(failOn = mutableSetOf("keystore"))
        assertTrue("the rest was removed", ProviderPurge(phone).run())
        assertFalse("the blobs", phone.blobs)
        assertFalse("the settings", phone.settings)
        assertTrue("the Keystore key is still there", phone.keystoreKey)
        assertFalse("so the sweep is not recorded as done", phone.done)
        assertFalse("and no mark was written", phone.touched.contains("mark"))
    }

    @Test fun aRefusedStepIsRetriedOnTheNextStartUntilItSucceeds() {
        // The finding of the review of 65c880f: recording the sweep as done after a refused step would keep the Keystore
        // key for ever. It stays pending instead, and the next start deletes it.
        val phone = Phone(failOn = mutableSetOf("keystore"))
        ProviderPurge(phone).run()
        assertTrue("the first start could not delete the Keystore key", phone.keystoreKey)
        assertFalse("and did not record the sweep", phone.done)
        phone.allow("keystore")
        ProviderPurge(phone).run()
        assertFalse("the second start deleted it", phone.keystoreKey)
        assertTrue("and then recorded the sweep", phone.done)
    }

    @Test fun aStepWithNothingToRemoveStillCountsAsDone() {
        // False means "there was nothing", not "it failed": a phone that never had the settings did not fail.
        val phone = Phone(blobs = false, keystoreKey = false, settings = false)
        ProviderPurge(phone).run()
        assertTrue("all three answered, so the sweep is recorded", phone.done)
    }

    @Test fun aSweepThatCannotBeRecordedStillStartsTheApp() {
        val phone = Phone(failOn = mutableSetOf("mark"))
        assertTrue("the removal happened", ProviderPurge(phone).run())
        assertFalse("the record did not, and nothing was thrown", phone.done)
    }

    @Test fun aPhoneWithNoAnswerAtAllIsNotAFailure() {
        val phone = Phone(failOn = mutableSetOf("done", "mark", "keystore"))
        ProviderPurge(phone).run() // no exception: a start cannot fail because of a version that is gone
        assertFalse("the blobs still went", phone.blobs)
        assertFalse("and so did the settings", phone.settings)
    }

    @Test fun theAndroidSideReportsAFailedCommitInsteadOfSwallowingIt() {
        // SharedPreferences' commit() answers whether the write happened; ignoring it would report "nothing to remove"
        // for a deletion that did not happen, and the sweep would be recorded as done with the keys still on the phone.
        val src = File("src/main/kotlin/com/kenai/android/app/ProviderPurge.kt").readText()
        assertTrue("the blobs' deletion is checked", src.contains("if (!file.edit().clear().commit()) throw"))
        assertTrue("the settings' deletion is checked", src.contains("if (!file.edit().remove(SETTINGS_PROVIDERS).commit()) throw"))
        assertTrue("so is the mark", src.contains("if (!context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE).edit()\n            .putBoolean(SWEPT, true).commit())"))
        assertFalse("apply() cannot report a failure", src.contains(".apply()"))
    }

    @Test fun thePurgeNamesTheLeftoversOfTheArchivedVersionAndPrintsNoKey() {
        // The three names are the ones the archived version used: the Keystore alias, its preferences file and the key of
        // the provider settings in the settings store.
        val src = File("src/main/kotlin/com/kenai/android/app/ProviderPurge.kt").readText()
        for (name in listOf("\"kenai_provider_keys\"", "\"kenai_providers\"", "\"providers\"", "\"kenai_ui\"")) {
            assertTrue("the sweep names $name", src.contains(name))
        }
        for (leak in listOf("Log.", "println", "printStackTrace", "System.out", "System.err")) {
            assertFalse("«$leak» could put a key in a log", src.contains(leak))
        }
    }
}
