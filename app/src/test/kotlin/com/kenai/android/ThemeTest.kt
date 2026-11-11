package com.kenai.android

import com.kenai.android.core.Screen
import com.kenai.android.ui.SystemBars
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The light theme (S2.5): the app follows the system's dark/light mode with no setting of its own, while the
 * island, the sheets, the scan screen and the Home hero stay dark in both. Compose cannot be rendered in a JVM
 * test, so the wiring is checked in the sources; the status bar rule is plain code.
 */
class ThemeTest {
    private fun src(name: String) = File("src/main/kotlin/com/kenai/android/$name").readText()

    @Test fun theThemeFollowsTheSystem() {
        val theme = src("ui/Theme.kt")
        assertTrue(theme.contains("isSystemInDarkTheme()"))
        assertTrue(theme.contains("lightColorScheme()") && theme.contains("darkColorScheme()"))
        assertTrue(theme.contains("Tokens.LIGHT") && theme.contains("Palette.LIGHT"))
        assertTrue("the tokens follow the scheme", theme.contains("LocalTokens provides (if (dark) Tokens.DARK else Tokens.LIGHT)"))
        assertFalse("no setting of its own", src("core/UserSettings.kt").contains("theme", ignoreCase = true))
    }

    @Test fun theWindowThemeIsDayNight() {
        val xml = File("src/main/res/values/themes.xml").readText()
        assertTrue(xml.contains("parent=\"android:Theme.DeviceDefault.DayNight\""))
        assertFalse(xml.contains("Dark only"))
    }

    @Test fun nothingSaysTheAppIsDarkOnly() {
        for (f in listOf("core/Tokens.kt", "core/Palette.kt", "ui/DesignSystem.kt", "MainActivity.kt")) {
            assertFalse(f, src(f).contains("dark only", ignoreCase = true))
        }
    }

    @Test fun theDarkSurfacesStayDarkInTheLightTheme() {
        assertTrue("sheets", src("ui/Sheets.kt").contains("AlwaysDark {"))
        assertTrue("hero", src("ui/DesignSystem.kt").contains("AlwaysDark {"))
        assertTrue("scan", src("ui/ScanScreen.kt").contains("AlwaysDark {"))
        assertTrue("island", src("ui/IslandOverlay.kt").contains("LocalTokens provides Tokens.DARK"))
        assertTrue("a red label on a dark pill uses the dark red", src("ui/DesignSystem.kt").contains("onDark -> Tokens.DARK.danger"))
    }

    @Test fun statusBarIconsAreLightOverAnythingDark() {
        // Dark theme: always light icons.
        for (s in Screen.entries) assertFalse(SystemBars.darkStatusIcons(darkTheme = true, screen = s, paired = true, heroOnTop = true))
        // Light theme: the camera and the Home hero are black under the bar.
        assertFalse(SystemBars.darkStatusIcons(darkTheme = false, screen = Screen.SCAN, paired = false, heroOnTop = false))
        assertFalse(SystemBars.darkStatusIcons(darkTheme = false, screen = Screen.HOME, paired = true, heroOnTop = true))
        // Light theme, light page under the bar: dark icons.
        assertTrue("hero scrolled away", SystemBars.darkStatusIcons(darkTheme = false, screen = Screen.HOME, paired = true, heroOnTop = false))
        assertTrue("pairing has no hero", SystemBars.darkStatusIcons(darkTheme = false, screen = Screen.HOME, paired = false, heroOnTop = true))
        for (s in listOf(Screen.SETTINGS, Screen.CHAT, Screen.HISTORY, Screen.DIAGNOSTICS, Screen.LICENCES, Screen.SESSION, Screen.GALLERY)) {
            assertTrue("$s", SystemBars.darkStatusIcons(darkTheme = false, screen = s, paired = true, heroOnTop = true))
        }
    }

    @Test fun navigationBarIconsAreLightOnTheCamera() {
        assertFalse(SystemBars.darkNavigationIcons(darkTheme = false, screen = Screen.SCAN))
        assertTrue(SystemBars.darkNavigationIcons(darkTheme = false, screen = Screen.HOME))
        for (s in Screen.entries) assertFalse(SystemBars.darkNavigationIcons(darkTheme = true, screen = s))
    }

    @Test fun mainActivityUsesTheRule() {
        val main = src("MainActivity.kt")
        assertFalse(main.contains("isAppearanceLightStatusBars = false"))
        // Computed in composition, so a change of screen, mode or scroll re-runs the SideEffect.
        assertTrue(main.contains("val darkStatus = SystemBars.darkStatusIcons(") && main.contains("isAppearanceLightStatusBars = darkStatus"))
        assertTrue(main.contains("val darkNav = SystemBars.darkNavigationIcons(") && main.contains("isAppearanceLightNavigationBars = darkNav"))
        assertTrue("Home reports whether the hero is under the bar", main.contains("listState.firstVisibleItemIndex == 0"))
    }
}
