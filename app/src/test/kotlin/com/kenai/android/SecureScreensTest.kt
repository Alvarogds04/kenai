package com.kenai.android

import com.kenai.android.core.Screen
import com.kenai.android.core.SecureScreens
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureScreensTest {
    private fun needed(
        screen: Screen, pairing: Boolean = false, approval: Boolean = false, question: Boolean = false, confirm: Boolean = false,
        pending: Boolean = false,
    ) = SecureScreens.needed(screen, pairing, approval, question, confirm, approvalPending = pending)

    @Test fun pairingChatApprovalAndQuestionAreHidden() {
        assertTrue("pairing screen", needed(Screen.HOME, pairing = true))
        assertTrue("scanning the code", needed(Screen.SCAN))
        assertTrue("chat", needed(Screen.CHAT))
        assertTrue("approval over Home", needed(Screen.HOME, approval = true))
        assertTrue("approval over Settings", needed(Screen.SETTINGS, approval = true))
        assertTrue("question", needed(Screen.HOME, question = true))
        assertTrue("pair confirmation", needed(Screen.HOME, confirm = true))
    }

    /** kenai-0.1 S9, L21 «a»: the exact command is on Home while a request waits, so Home hides like the sheet. */
    @Test fun homeIsHiddenWhileARequestIsPending() {
        assertTrue(needed(Screen.HOME, pending = true))
        for (s in listOf(Screen.SETTINGS, Screen.HISTORY, Screen.GALLERY, Screen.SESSION, Screen.DIAGNOSTICS, Screen.LICENCES)) {
            assertFalse("$s draws no command", needed(s, pending = true))
            assertTrue("$s with the sheet over it", needed(s, approval = true, pending = true))
        }
    }

    @Test fun otherScreensAreNotHidden() {
        for (s in listOf(Screen.HOME, Screen.SETTINGS, Screen.HISTORY, Screen.GALLERY, Screen.SESSION)) {
            assertFalse("$s", needed(s))
        }
    }

    @Test fun theActivityAppliesAndClearsTheFlagFromThatDecision() {
        val src = File("src/main/kotlin/com/kenai/android/MainActivity.kt").readText()
        assertTrue(src.contains("SecureScreens.needed("))
        assertTrue(src.contains("window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)"))
        assertTrue("cleared when the screen is no longer sensitive", src.contains("window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)"))
        // The decision sees every sheet that exists: a new sheet must be added to it.
        for (sheet in listOf("ApprovalSheet(", "QuestionSheet(", "PairConfirm(")) assertTrue(sheet, src.contains(sheet))
        assertTrue("Home's hero is part of the decision", src.contains("approvalPending = model.approvals.isNotEmpty()"))
    }
}
