package com.kenai.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for the approval and question sheets (Compose cannot be rendered in a JVM test, so these read the
 * sources). The point of the redesign here: no raw command on screen unless asked for, and nothing about Allow gets weaker.
 */
class SheetsTest {
    private fun src(name: String) = File("src/main/kotlin/com/kenai/android/$name").readText()
    private val sheets get() = src("ui/Sheets.kt")
    private val main get() = src("MainActivity.kt")

    @Test fun theCommandIsOnlyDrawnAfterShowExactCommand() {
        assertEquals("the command is drawn in exactly one place", 1, Regex("""r\.command""").findAll(sheets).count())
        val before = sheets.substringBefore("r.command")
        assertTrue(before.substringAfterLast("if (showCommand)").length < 600)
        assertTrue(before.contains("if (showCommand)"))
    }

    @Test fun aCutCommandIsSaidClearlyAndAllowIsOnlyOfferedWhenTheWholeCommandIsHere() {
        assertTrue(sheets.contains("R.string.approval_too_long"))
        // Allow is drawn only under the check, and the notice is not behind a tap.
        assertTrue(sheets.substringAfter("val canAllow").substringBefore("PillKind.PRIMARY").contains("if (canAllow)"))
        assertFalse(sheets.substringAfter("approval_too_long").substringBefore("canAllow").contains("showCommand"))
        // The same rule at every door: the sheet's callback, the model, the island and the shade (KN-04 T4). The notification's
        // «Permitir» opens the app's own prompt and decides nothing in the shade, so a cut request is never allowed from there either.
        assertTrue(main.contains("ApprovalSheetPlan.canAllowHere(r)"))
        assertTrue(src("app/AppModel.kt").substringAfter("fun decide(fingerprint").substringBefore("link?.decide").contains("canAllowHere(request)"))
        val notifications = src("app/Notifications.kt")
        assertTrue("Allow is the app's prompt, never a decision in the shade", notifications.contains("open(r.fingerprint, true, id + 1)"))
        assertTrue("and the shade asks the same question as the sheet", notifications.contains("NotificationPlan.approvalActions(r)"))
        assertTrue("the only decision in the notifications is Deny", notifications.contains("model.decide(fp, allow = false)") && !notifications.contains("allow = true"))
        assertTrue(src("ui/IslandOverlay.kt").contains("if (s.canAllow)"))
    }

    @Test fun theCommandIsNeverCutByALineLimit() {
        assertFalse(sheets.contains("maxLines = ApprovalSheetPlan.MAX"))
        assertTrue(sheets.contains("verticalScroll(rememberScrollState())"))
    }

    /**
     * kenai-0.1 S9, L21 «a»: the exact command is on Home, inside the request hero only (never in the agent rows or the hero card),
     * Home hides from screenshots while it is there, and the lock prompt still repeats the exact command as its description.
     */
    @Test fun homeDrawsTheCommandOnlyInsideTheRequestHero() {
        val ui = File("src/main/kotlin/com/kenai/android/ui").listFiles().orEmpty().filter { it.extension == "kt" }
        val drawing = ui.filter { it.readText().contains(".command") }.map { it.name }.sorted()
        assertEquals(listOf("RequestHero.kt", "Sheets.kt"), drawing)
        val hero = src("ui/RequestHero.kt")
        assertEquals("the hero draws it in exactly one place", 1, Regex("""Text\(\s*r\.command""").findAll(hero).count())
        // and otherwise only hands it to the local risk detector
        assertEquals(2, Regex("""r\.command""").findAll(hero).count())
        assertTrue(hero.contains("HomeText.riskLine(r.command"))
        assertFalse(src("ui/HomePanel.kt").contains(".command"))
        // the activity names it once: the lock prompt's description
        assertEquals(1, Regex("""r\.command""").findAll(main).count())
        assertTrue(main.contains("description = r.command"))
        assertFalse("the old subtitle (tool: command) is gone", main.contains("\"${'$'}{r.tool}: ${'$'}{r.command}\""))
        assertTrue(main.contains("R.string.confirm_title"))
        assertTrue(main.contains("R.string.confirm_command") && main.contains("R.string.confirm_action"))
        assertFalse(main.contains("fun ApprovalCard"))
    }

    @Test fun theLockPromptCarriesTheCommandAsItsDescription() {
        val gate = src("app/BiometricGate.kt")
        assertTrue(gate.contains("description: String? = null"))
        assertTrue(gate.contains("setDescription("))
    }

    @Test fun theSheetSaysTheKindOfActionAndNotTheTool() {
        assertTrue(sheets.contains("ApprovalSheetPlan.action(r.tool)"))
        assertTrue(sheets.contains("R.string.approval_wants"))
    }

    @Test fun allowStillGoesThroughTheLockAndDenyNeedsNone() {
        assertFalse("the sheet never allows by itself", Regex("""allow\s*=\s*true""").containsMatchIn(sheets))
        assertTrue(sheets.contains("onAllow(r)"))
        assertTrue(main.contains("onAllow = ::approve"))
        assertTrue(main.contains("BiometricGate.confirm("))
        assertTrue(sheets.contains("model.decide(r.fingerprint, allow = false)"))
    }

    @Test fun bothButtonsIgnoreTapsDuringTheFirstMoments() {
        assertTrue(sheets.contains("delay(ApprovalSheetPlan.GUARD_MS)"))
        assertEquals(2, Regex("""enabled = armed""").findAll(sheets).count())
    }

    @Test fun theSheetCanBeClosedAndNeverCatchesTapsMeantForTheScrim() {
        assertTrue(sheets.contains("BackHandler(onBack = onDismiss)"))
        assertTrue(sheets.contains("detectTapGestures { }"))
        assertTrue(main.contains("closedApproval = pending.fingerprint"))
        // Review on a second request on Home opens the sheet for that one; the hero's own request never gets the sheet over it.
        assertTrue("Review opens the sheet", main.contains("onReview = { fp -> reviewApproval = fp; closedApproval = null }"))
        assertTrue(main.contains("ApprovalSheetPlan.next(model.approvals, closedApproval, heroShown = homeHero, review = reviewApproval)"))
    }

    @Test fun aQuestionIsAnsweredOnlyAfterTheScreenLock() {
        assertTrue(main.contains("focus.state == BotState.QUESTION"))
        // The sheet never talks to the link itself: it hands the picks to the activity, which asks for the lock first.
        assertFalse(sheets.contains("model.answerQuestion"))
        val send = main.substringAfter("private fun sendAnswer").substringBefore("/** Allow:")
        assertTrue(send.indexOf("BiometricGate.confirm") in 0 until send.indexOf("model.answerQuestion"))
        assertTrue(send.contains("if (confirming) return"))
    }

    /** kenai-0.1 S9, T15: the question is a full screen (QuestionScreenTest); no question sheet is left here. */
    @Test fun noQuestionSheetIsLeft() {
        assertFalse(sheets.contains("fun QuestionSheet"))
        assertFalse(sheets.contains("QuestionFlow"))
    }
    // ── the file-change sheet ──────────────────────────────────────────────────────

    private val diffSheet get() = src("ui/DiffSheet.kt")

    @Test fun theLinesOfAFileAreNeverLoggedStoredOrKeptAcrossRecreation() {
        assertFalse(diffSheet.contains("Log."))
        assertFalse(diffSheet.contains("rememberSaveable"))
        val model = src("app/AppModel.kt")
        val diffPart = model.substringAfter("A file's change (cap `diffs`)").substringBefore("override fun onApprovalResolved")
        assertFalse(diffPart.contains("Log."))
        assertFalse(diffPart.contains("kv.put") || diffPart.contains("store."))
        assertTrue("closing the sheet forgets the lines", diffPart.contains("fun closeDiff()") && diffPart.contains("DiffState.Idle"))
    }

    @Test fun anAddedOrRemovedLineIsMarkedAndNotOnlyColoured() {
        assertTrue(diffSheet.contains("'+' -> \"+\"") && diffSheet.contains("'-' -> \"−\""))
    }

    @Test fun unlinkingTheComputerClosesTheSheet() {
        val model = src("app/AppModel.kt")
        assertTrue(model.substringAfter("private fun stopLink()").take(120).contains("closeDiff()"))
    }

    @Test fun theSheetSaysWhenItCouldNotLoadOrWasCutOrIsTooLarge() {
        for (s in listOf("file_failed", "file_gone", "file_too_large", "file_truncated", "file_loading")) assertTrue(s, diffSheet.contains("R.string.$s"))
    }
}
