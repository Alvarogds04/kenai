package com.kenai.android

import com.kenai.android.core.IslandSurface
import com.kenai.android.core.MotionSpec
import com.kenai.android.core.Palette
import com.kenai.android.core.StatusPalette
import com.kenai.android.core.Radii
import com.kenai.android.core.Spacing
import com.kenai.android.core.Tokens
import com.kenai.android.core.TypeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The redesign's tokens: readable text on every surface it sits on, in the dark and the light theme, and a scale that holds together. */
class TokensTest {
    private val themes = mapOf("dark" to Tokens.DARK, "light" to Tokens.LIGHT)
    private fun c(a: Long, b: Long) = Palette.contrast(a, b)

    @Test fun primaryTextIsReadableOnEverySurface() {
        for ((n, t) in themes) for (s in listOf(t.bg, t.panel, t.panel2, t.secondaryButton)) {
            assertTrue("$n text on ${s.toString(16)}", c(t.text, s) >= 7.0)
            assertTrue("$n dim text on ${s.toString(16)}", c(t.textDim, s) >= 4.5 || s == t.panel2 && c(t.textDim, s) >= 4.0)
        }
    }

    @Test fun dimTextIsReadableOnThePageAndOnPanels() {
        for ((n, t) in themes) for (s in listOf(t.bg, t.panel)) assertTrue("$n dim on ${s.toString(16)}", c(t.textDim, s) >= 4.5)
    }

    @Test fun faintTextIsOnlyForLargeTextAndIcons() {
        for ((n, t) in themes) for (s in listOf(t.bg, t.panel)) assertTrue("$n faint", c(t.textFaint, s) >= 3.0)
    }

    @Test fun theMainButtonAndItsLabelAreReadable() {
        for ((n, t) in themes) {
            assertTrue("$n label", c(t.onPrimaryButton, t.primaryButton) >= 7.0)
            assertTrue("$n button stands out from the panel", c(t.primaryButton, t.panel) >= 3.0)
        }
    }

    @Test fun dangerTextIsReadableAndGreenIsVisibleAsADot() {
        for ((n, t) in themes) for (s in listOf(t.bg, t.panel)) {
            assertTrue("$n danger", c(t.danger, s) >= 4.5)
            assertTrue("$n online dot", c(t.online, s) >= 3.0)
        }
    }

    @Test fun panelsAreToldApartFromThePage() {
        for ((n, t) in themes) {
            assertTrue("$n panel border", c(t.line, t.panel) >= 1.1)
            assertTrue("$n panel vs page", c(t.panel, t.bg) >= 1.05)
            assertTrue("$n row vs panel", c(t.panel2, t.panel) >= 1.05)
        }
    }

    @Test fun secondaryButtonsAreVisibleOnPanels() {
        for ((n, t) in themes) assertTrue("$n secondary", c(t.secondaryButton, t.panel) >= 1.05)
    }

    @Test fun theAlwaysDarkIslandKeepsItsTextReadable() {
        for (bg in listOf(IslandSurface.BLACK, IslandSurface.PANEL, IslandSurface.HERO)) {
            assertTrue(c(IslandSurface.TEXT, bg) >= 7.0)
            assertTrue(c(IslandSurface.TEXT_DIM, bg) >= 4.5)
        }
    }

    @Test fun theWashIsSoft() {
        for ((n, t) in themes) assertTrue(n, t.washAlpha in 0.3..0.6)
    }

    @Test fun theLightThemeIsDarkTextOnALightPage() {
        val t = Tokens.LIGHT
        for (s in listOf(t.bg, t.panel, t.panel2)) assertTrue("light surface ${s.toString(16)}", c(s, 0xFFFFFFFF) < c(s, 0xFF000000))
        assertTrue("light text is dark", c(t.text, 0xFF000000) < c(t.text, 0xFFFFFFFF))
        assertTrue("the main button is dark on a light page", c(t.primaryButton, 0xFF000000) < c(t.primaryButton, 0xFFFFFFFF))
    }

    @Test fun theDarkOnlySurfacesDoNotFollowTheTheme() {
        // The island, the sheets, the scan screen and the hero stay dark in both themes: their colours are not theme tokens.
        assertTrue(c(IslandSurface.HERO, 0xFF000000) < 1.2 && c(IslandSurface.PANEL, 0xFF000000) < 1.2)
        assertTrue(c(IslandSurface.ON_PRIMARY, IslandSurface.TEXT) >= 7.0)
        assertTrue("danger on the dark sheets", c(Tokens.DARK.danger, IslandSurface.PANEL) >= 4.5)
    }

    @Test fun spacingIsOneScaleOnAFourPointGrid() {
        assertTrue(Spacing.SCALE.all { it % 4 == 0 })
        assertEquals(Spacing.SCALE.sorted(), Spacing.SCALE)
        assertTrue(Spacing.GUTTER in Spacing.SCALE && Spacing.GAP in Spacing.SCALE && Spacing.INSIDE in Spacing.SCALE)
        assertEquals(48, Spacing.MIN_TOUCH)
    }

    @Test fun typeScaleGrowsAndHasRoomForItsLines() {
        val sizes = TypeScale.ALL.map { it.sizeSp }
        assertEquals(sizes.sortedDescending(), sizes)
        for (t in TypeScale.ALL) {
            assertTrue("${t.name} line height", t.lineSp >= t.sizeSp + 4)
            assertTrue("${t.name} weight", t.weight in 400..700)
        }
        assertTrue("body text is at least 15 sp", TypeScale.BODY.sizeSp >= 15)
        assertTrue("nothing under 12 sp", TypeScale.ALL.all { it.sizeSp >= 12 })
    }

    @Test fun radiiMatchThePc() {
        assertEquals(20, Radii.PANEL)
        assertEquals(22, Radii.ISLAND_EXPANDED)
        assertEquals(14, Radii.ISLAND_COMPACT)
    }

    @Test fun statusDotsAreVisibleOnBlackDarkAndLightPanels() {
        val surfaces = listOf(Tokens.DARK.bg, Tokens.DARK.panel, IslandSurface.HERO, IslandSurface.BLACK, Tokens.LIGHT.bg, Tokens.LIGHT.panel)
        for (c in listOf(StatusPalette.ONLINE, StatusPalette.BUSY, StatusPalette.OFFLINE)) for (s in surfaces) {
            assertTrue("${c.toString(16)} on ${s.toString(16)}", Palette.contrast(c, s) >= 3.0)
        }
    }

    @Test fun theBubbleMeetsTheArtboardAndKeepsItsTextReadable() {
        // KN-03: the user's bubble is Koda's `--bubble` — translucent white in dark, translucent black in light — and the
        // words on it stay readable over what it paints (the chat reads on the page). Values are the artboard's.
        assertEquals(0x1AFFFFFF, Tokens.DARK.bubble)
        assertEquals(0x12000000, Tokens.LIGHT.bubble)
        for ((n, t) in themes) {
            val painted = blendedOver(t.bubble, t.bg)
            assertTrue("$n text on bubble over page (${painted.toString(16)})", c(t.text, painted) >= 7.0)
            assertTrue("$n text on bubble over panel (${painted.toString(16)})", c(t.text, blendedOver(t.bubble, t.panel)) >= 4.5)
        }
    }

    @Test fun theSendIsTheArtboardSLimeAndContrastsWithItsGlyph() {
        // KN-03: the send button is the lime circle `#b8ff2e` with the black glyph, the same in both themes.
        for ((_, t) in themes) {
            assertEquals(0xFFB8FF2E, t.send)
            assertEquals(0xFF000000, t.onSend)
            assertTrue("glyph on the lime", c(t.onSend, t.send) >= 7.0)
        }
    }

    /** A translucent token paints over what is already there: the colour the eye sees is the blend, not the number. */
    private fun blendedOver(top: Long, under: Long): Long {
        val a = ((top shr 24) and 0xFF).toInt() / 255.0
        fun mix(shift: Int): Int {
            val paint = ((top shr shift) and 0xFF).toInt()
            val base = ((under shr shift) and 0xFF).toInt()
            return Math.round(paint * a + base * (1 - a)).toInt()
        }
        return (0xFF shl 24).toLong() or (mix(16).toLong() shl 16) or (mix(8).toLong() shl 8) or mix(0).toLong()
    }

    @Test fun reducedMotionFollowsTheAnimatorScale() {
        assertTrue(MotionSpec.isReduced(0f))
        assertTrue(!MotionSpec.isReduced(1f) && !MotionSpec.isReduced(0.5f))
    }
}
