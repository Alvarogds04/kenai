package com.kenai.android

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4: a light/dark switch (and a rotation, a language or font change, or the system ending the process in the
 * background) recreates MainActivity. The open screen and its sheets must come back, not Home. Compose cannot run
 * in a JVM test, so the wiring is checked in the sources; the restore rule itself is in NavigationTest.
 */
class ConfigChangeTest {
    private val activity = File("src/main/kotlin/com/kenai/android/MainActivity.kt").readText()
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test fun theOpenScreenAndSheetsAreSavedWhenTheActivityIsRecreated() {
        assertTrue(activity.contains("override fun onSaveInstanceState(outState: Bundle)"))
        assertTrue("the screen", activity.contains("outState.putString(KEY_SCREEN, screen.name)"))
        assertTrue("the session page", activity.contains("outState.putString(KEY_SESSION, detailPill)"))
        assertTrue("a closed approval stays closed", activity.contains("outState.putString(KEY_CLOSED_APPROVAL, closedApproval)"))
        assertTrue("an open question stays open", activity.contains("outState.putString(KEY_QUESTION, questionPill)"))
    }

    @Test fun theSavedScreenComesBackBeforeTheFirstFrame() {
        assertTrue(activity.contains("screen = Nav.restore(saved.getString(KEY_SCREEN), detailPill, questionPending = questionPill?.let(::questionWaits) == true)"))
        // Restored before setContent, so the first frame already shows it (no flash of Home).
        assertTrue(activity.indexOf("Nav.restore(") in 0 until activity.indexOf("setContent {"))
    }

    @Test fun aLightDarkSwitchStillRecreatesTheActivity() {
        // Recreation is what re-reads the DayNight window theme and the Spanish/English resources; the saved state is
        // what keeps the screen. Handling uiMode ourselves would skip the first and cover only one kind of change.
        assertFalse(manifest.contains("android:configChanges"))
        // The bar icons are worked out in composition from the system's mode, so the new activity re-themes them too.
        assertTrue(activity.contains("val dark = isSystemInDarkTheme()"))
        assertTrue(activity.contains("SystemBars.darkStatusIcons(dark, screen,"))
    }
}
