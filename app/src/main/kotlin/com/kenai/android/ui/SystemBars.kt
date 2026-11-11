package com.kenai.android.ui

import com.kenai.android.core.Screen

/**
 * Which icons the system bars wear. Light icons over anything dark: the whole dark theme, the camera screen and,
 * in the light theme, the Home hero while it is the thing under the status bar. Dark icons over the light page.
 */
object SystemBars {
    /** For `isAppearanceLightStatusBars`: true means dark icons. [heroOnTop]: Home's list is scrolled to its hero. */
    fun darkStatusIcons(darkTheme: Boolean, screen: Screen, paired: Boolean, heroOnTop: Boolean): Boolean {
        if (darkTheme || screen == Screen.SCAN) return false
        // Home without a computer is the pairing screen: a light page, no hero.
        return !(screen == Screen.HOME && paired && heroOnTop)
    }

    /** For `isAppearanceLightNavigationBars`: true means dark icons. The camera screen is black to the bottom edge. */
    fun darkNavigationIcons(darkTheme: Boolean, screen: Screen): Boolean = !darkTheme && screen != Screen.SCAN
}
