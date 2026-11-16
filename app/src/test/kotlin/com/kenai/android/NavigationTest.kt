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
        for (s in listOf(Screen.HISTORY, Screen.GALLERY, Screen.DIAGNOSTICS, Screen.LICENCES, Screen.PROVIDERS, Screen.SESSION, Screen.SCAN, Screen.QUESTION)) assertFalse("$s", Nav.barVisible(s, false))
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
        assertEquals(Screen.HOME, Nav.back(Screen.QUESTION))
        assertEquals(Screen.SETTINGS, Nav.back(Screen.HISTORY))
        assertEquals(Screen.SETTINGS, Nav.back(Screen.GALLERY))
        assertEquals(Screen.SETTINGS, Nav.back(Screen.DIAGNOSTICS))
        assertEquals(Screen.SETTINGS, Nav.back(Screen.PROVIDERS))
    }

    /** T1a-3b: the providers screen is a Settings page; T6: it comes back after a recreation like the others. */
    @Test fun theProvidersScreenIsASettingsPageBelowTheBar() {
        assertEquals(Tab.SETTINGS, Nav.tabOf(Screen.PROVIDERS))
        assertEquals(Screen.SETTINGS, Nav.back(Screen.PROVIDERS))
        assertFalse("the bar hides like every deep screen", Nav.barVisible(Screen.PROVIDERS, keyboardOpen = false))
        assertEquals(Screen.PROVIDERS, Nav.restore(Screen.PROVIDERS.name, sessionPill = null))
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
        for (s in listOf(Screen.SETTINGS, Screen.HISTORY, Screen.GALLERY, Screen.DIAGNOSTICS, Screen.PROVIDERS)) assertEquals(Tab.SETTINGS, Nav.tabOf(s))
        assertNull(Nav.tabOf(Screen.SCAN))
        assertNull(Nav.tabOf(Screen.SESSION))
        assertNull(Nav.tabOf(Screen.QUESTION))
    }

    @Test fun everyTabLeadsToItsOwnScreen() {
        for (t in Tab.entries) assertEquals(t, Nav.tabOf(Nav.screenOf(t)))
    }

    @Test fun aRecreatedActivityReopensTheScreenThatWasOpen() {
        // S4: a light/dark switch, a rotation or a language change must not send the user back to Home.
        assertEquals(Screen.SETTINGS, Nav.restore(Screen.SETTINGS.name, sessionPill = null))
        for (s in Screen.entries) assertEquals("$s", s, Nav.restore(s.name, sessionPill = "pill-1", questionPending = true))
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

    /** kenai-0.1 S9, T15: the question is a full screen; recreated, it comes back only while its question still waits. */
    @Test fun aQuestionScreenComesBackOnlyWhileItsQuestionWaits() {
        assertEquals(Screen.QUESTION, Nav.restore(Screen.QUESTION.name, sessionPill = null, questionPending = true))
        assertEquals("answered or gone meanwhile", Screen.HOME, Nav.restore(Screen.QUESTION.name, sessionPill = null, questionPending = false))
        assertEquals(Screen.HOME, Nav.restore(Screen.QUESTION.name, sessionPill = null))
        assertEquals("other screens do not care", Screen.SETTINGS, Nav.restore(Screen.SETTINGS.name, sessionPill = null, questionPending = false))
    }
}
