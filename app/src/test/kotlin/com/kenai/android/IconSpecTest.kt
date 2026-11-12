package com.kenai.android

import com.kenai.android.core.IconKind
import com.kenai.android.core.IconShape
import com.kenai.android.core.IconSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconSpecTest {
    @Test fun everyIconHasADrawing() {
        for (k in IconKind.entries) assertTrue("$k", IconSpec.shapes(k).isNotEmpty())
    }

    @Test fun everythingStaysOnTheGridWithRoomForTheStroke() {
        val margin = IconSpec.STROKE / 2
        for (k in IconKind.entries) for (v in IconSpec.extent(k)) {
            assertTrue("$k point $v", v >= margin && v <= IconSpec.GRID - margin)
        }
    }

    @Test fun polylinesHavePairsAndAtLeastTwoPoints() {
        for (k in IconKind.entries) for (s in IconSpec.shapes(k)) if (s is IconShape.Poly) {
            assertEquals("$k", 0, s.points.size % 2)
            assertTrue("$k", s.points.size >= 4)
        }
    }

    @Test fun noTwoIconsAreTheSameDrawing() {
        assertEquals(IconKind.entries.size, IconKind.entries.map { IconSpec.shapes(it) }.toSet().size)
    }

    @Test fun theSettingsIconIsNotASunOrAGear() {
        // The old gear was a ring with eight spokes. Settings is two rails with two knobs.
        val s = IconSpec.shapes(IconKind.SLIDERS)
        assertEquals(2, s.count { it is IconShape.Circle })
        assertTrue(s.none { it is IconShape.Arc })
        assertTrue(s.filterIsInstance<IconShape.Poly>().all { p -> p.points[1] == p.points[3] }) // every rail is horizontal
    }

    @Test fun strokeIsTheSameAsThePc() {
        assertEquals(1.9, IconSpec.STROKE, 0.0)
    }

    /** kenai-0.1 S9: the request hero's fingerprint on Allow, the risk line's warning and the expiry clock, drawn in code. */
    @Test fun theRequestHeroIconsExist() {
        for (k in listOf("FINGERPRINT", "WARNING", "CLOCK")) assertTrue(k, IconKind.entries.any { it.name == k })
        assertTrue("a warning is a triangle", IconSpec.shapes(IconKind.WARNING).any { it is IconShape.Poly && it.points.size >= 8 })
        assertTrue("a clock is a circle with hands", IconSpec.shapes(IconKind.CLOCK).any { it is IconShape.Circle })
        assertTrue("a fingerprint is made of arcs", IconSpec.shapes(IconKind.FINGERPRINT).count { it is IconShape.Arc } >= 2)
    }
}
