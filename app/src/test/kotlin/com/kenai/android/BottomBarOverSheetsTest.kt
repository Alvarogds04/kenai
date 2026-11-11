package com.kenai.android

import com.kenai.android.core.Nav
import com.kenai.android.core.Screen
import com.kenai.android.ui.ScreenSheet
import com.kenai.android.ui.SheetLayer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S7: the bottom bar never covers a sheet's buttons. The sheets a screen opens itself (Pairing's "Paste the link",
 * Home's address) are drawn by the activity in the same layer as the approval sheet, after the bar, and the bar hides
 * while any sheet is up. Compose cannot be rendered in a JVM test, so the wiring is read from the sources.
 */
class BottomBarOverSheetsTest {
    private fun src(name: String) = File("src/main/kotlin/com/kenai/android/$name").readText()
    private val main get() = src("MainActivity.kt")

    @Test fun theBarHidesWhileAnySheetIsOpen() {
        for (s in Screen.entries) for (keyboard in listOf(false, true)) {
            assertFalse("$s", SheetLayer.barVisible(s, keyboard, sheetOpen = true))
            assertEquals("$s without a sheet", Nav.barVisible(s, keyboard), SheetLayer.barVisible(s, keyboard, sheetOpen = false))
        }
    }

    @Test fun aScreenSheetShowsOnlyOnTheScreenThatOpenedIt() {
        assertEquals(ScreenSheet.PASTE, SheetLayer.shown(ScreenSheet.PASTE, Screen.HOME, pairing = true))
        assertNull("paired meanwhile: no paste sheet", SheetLayer.shown(ScreenSheet.PASTE, Screen.HOME, pairing = false))
        assertEquals(ScreenSheet.ADDRESS, SheetLayer.shown(ScreenSheet.ADDRESS, Screen.HOME, pairing = false))
        assertNull("unpaired meanwhile: no address sheet", SheetLayer.shown(ScreenSheet.ADDRESS, Screen.HOME, pairing = true))
        for (s in Screen.entries - Screen.HOME) for (p in listOf(false, true)) {
            assertNull("$s", SheetLayer.shown(ScreenSheet.PASTE, s, p))
            assertNull("$s", SheetLayer.shown(ScreenSheet.ADDRESS, s, p))
        }
        assertNull(SheetLayer.shown(null, Screen.HOME, pairing = true))
    }

    @Test fun theScreensNoLongerDrawTheirOwnSheets() {
        val pairing = src("ui/PairingScreen.kt").substringAfter("fun PairingScreen(").substringBefore("fun PasteSheet(")
        assertFalse("the pairing screen only asks for the paste sheet", pairing.contains("PasteSheet("))
        val home = main.substringAfter("private fun Home(")
        assertFalse("Home only asks for the address sheet", home.contains("AddressSheet("))
    }

    @Test fun theActivityDrawsEverySheetAfterTheBar() {
        val bar = main.indexOf("BottomBar(")
        assertTrue(bar > 0)
        for (sheet in listOf("ApprovalSheet(", "QuestionSheet(", "DiffSheet(", "PasteSheet(", "AddressSheet(")) {
            assertTrue("$sheet is drawn after the bar", main.indexOf(sheet, bar) > bar)
        }
    }

    @Test fun theBarFollowsTheSheetRule() {
        assertTrue(main.contains("SheetLayer.barVisible("))
        assertFalse("the bar no longer ignores the sheets", main.contains("Nav.barVisible("))
    }

    @Test fun theSheetReachesTheBottomEdgeAndKeepsItsButtonsAboveTheNavigationBar() {
        val host = src("ui/Sheets.kt").substringAfter("fun BottomSheetHost(").substringBefore("/** The colour of an agent")
        assertTrue(host.contains("windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))"))
        // The sheet layer sits outside the inset-padded box of the screens, so nothing light shows under a dark sheet.
        val sheetLayer = main.substringAfter("// The sheet layer")
        assertTrue(sheetLayer.contains("ApprovalSheet(") && sheetLayer.contains("PasteSheet(") && sheetLayer.contains("AddressSheet("))
        assertFalse(sheetLayer.substringBefore("PairConfirm(").contains("windowInsetsPadding("))
    }
}
