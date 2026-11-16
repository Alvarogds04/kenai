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
        private val failOn: Set<String> = emptySet(),
    ) : LegacyProviderStorage {
        val touched = ArrayList<String>()

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

    @Test fun oneSideThatRefusesStillLetsTheOthersGo() {
        // A Keystore that will not give up the alias must not keep the blobs or the settings alive with it.
        val phone = Phone(failOn = setOf("keystore"))
        assertTrue("the rest was removed", ProviderPurge(phone).run())
        assertFalse("the blobs", phone.blobs)
        assertFalse("the settings", phone.settings)
        assertTrue("and it is recorded", phone.done)
    }

    @Test fun aSweepThatCannotBeRecordedStillStartsTheApp() {
        val phone = Phone(failOn = setOf("mark"))
        assertTrue("the removal happened", ProviderPurge(phone).run())
        assertFalse("the record did not, and nothing was thrown", phone.done)
    }

    @Test fun aPhoneWithNoAnswerAtAllIsNotAFailure() {
        val phone = Phone(failOn = setOf("done", "mark", "keystore"))
        ProviderPurge(phone).run() // no exception: a start cannot fail because of a version that is gone
        assertFalse("the blobs still went", phone.blobs)
        assertFalse("and so did the settings", phone.settings)
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
