package com.kenai.android

import com.kenai.android.core.Nav
import com.kenai.android.core.Screen
import com.kenai.android.core.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
    @Test fun theBarAlwaysHasHomeChatAndSettingsInThatOrder() {
        assertEquals(listOf(Tab.HOME, Tab.CHAT, Tab.SETTINGS), Nav.tabs())
    }

    @Test fun theBarShowsOnTheThreeRootsOnly() {
        for (s in listOf(Screen.HOME, Screen.CHAT, Screen.SETTINGS)) assertTrue("$s", Nav.barVisible(s, keyboardOpen = false))
        for (s in listOf(Screen.HISTORY, Screen.GALLERY, Screen.DIAGNOSTICS, Screen.LICENCES, Screen.SESSION, Screen.SCAN)) assertFalse("$s", Nav.barVisible(s, false))
    }

    @Test fun theBarHidesWhileTheKeyboardIsOpen() {
        for (s in Screen.entries) assertFalse("$s", Nav.barVisible(s, keyboardOpen = true))
    }

    @Test fun backGoesUpOneLevelAndLeavesFromHome() {
        assertNull(Nav.back(Screen.HOME))
        assertEquals(Screen.HOME, Nav.back(Screen.CHAT))
        assertEquals(Screen.HOME, Nav.back(Screen.SETTINGS))
        assertEquals(Screen.HOME, Nav.back(Screen.SESSION))
        assertEquals(Screen.HOME, Nav.back(Screen.SCAN))
        assertEquals(Screen.SETTINGS, Nav.back(Screen.HISTORY))
        assertEquals(Screen.SETTINGS, Nav.back(Screen.GALLERY))
        assertEquals(Screen.SETTINGS, Nav.back(Screen.DIAGNOSTICS))
    }

    @Test fun backAlwaysReachesHomeAndNeverLoops() {
        for (start in Screen.entries) {
            var s = start
            var steps = 0
            while (true) {
                s = Nav.back(s) ?: break
                assertTrue("loop from $start", ++steps < 10)
            }
            assertEquals("from $start", Screen.HOME, s)
        }
    }

    @Test fun pagesBelowSettingsKeepSettingsHighlighted() {
        for (s in listOf(Screen.SETTINGS, Screen.HISTORY, Screen.GALLERY, Screen.DIAGNOSTICS)) assertEquals(Tab.SETTINGS, Nav.tabOf(s))
        assertNull(Nav.tabOf(Screen.SCAN))
        assertNull(Nav.tabOf(Screen.SESSION))
    }

    @Test fun everyTabLeadsToItsOwnScreen() {
        for (t in Tab.entries) assertEquals(t, Nav.tabOf(Nav.screenOf(t)))
    }

    @Test fun aRecreatedActivityReopensTheScreenThatWasOpen() {
        // S4: a light/dark switch, a rotation or a language change must not send the user back to Home.
        assertEquals(Screen.SETTINGS, Nav.restore(Screen.SETTINGS.name, sessionPill = null))
        for (s in Screen.entries) assertEquals("$s", s, Nav.restore(s.name, sessionPill = "pill-1"))
    }

    @Test fun nothingOrSomethingUnknownSavedOpensHome() {
        assertEquals(Screen.HOME, Nav.restore(null, null))
        assertEquals(Screen.HOME, Nav.restore("", null))
        assertEquals(Screen.HOME, Nav.restore("PLANETS", null)) // a screen of an older or newer version
        assertEquals(Screen.HOME, Nav.restore("settings", null))
    }

    @Test fun aSessionPageWithoutItsSessionOpensHome() {
        assertEquals(Screen.HOME, Nav.restore(Screen.SESSION.name, sessionPill = null))
    }
}
