package com.kenai.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * kenai-0.1 S9, screen 4 «Pregunta» (T15): a question is a full screen (Screen.QUESTION), not a sheet: the status strip on top, a back
 * header, «Pregunta 1 de 2» with one progress segment per question, the options with their descriptions, the hint by capability, and
 * «Enviar respuesta» through the same lock as Allow. Compose cannot be rendered in a JVM test, so the wiring is read from the sources;
 * the rules themselves are in QuestionFlowTest and NavigationTest.
 */
class QuestionScreenTest {
    private fun src(name: String) = File("src/main/kotlin/com/kenai/android/$name").readText()
    private val screen get() = src("ui/QuestionScreen.kt")
    private val main get() = src("MainActivity.kt")
    private fun strings(dir: String) = File("src/main/res/$dir/strings.xml").readText()

    @Test fun theQuestionIsAFullScreenUnderScreenQuestion() {
        assertTrue(screen.contains("fun QuestionScreen("))
        assertTrue(main.contains("Screen.QUESTION -> "))
        assertTrue(main.substringAfter("Screen.QUESTION -> ").substringBefore("Screen.").contains("QuestionScreen("))
        assertFalse("no sheet host: it is a screen", screen.contains("BottomSheetHost("))
        assertTrue("system Back leaves it like the header's back", screen.contains("BackHandler(onBack = onBack)"))
        assertTrue(screen.contains("R.string.question_title"))
    }

    @Test fun everyDoorOpensTheScreen() {
        val open = main.substringAfter("private fun openQuestion(").substringBefore("\n    }")
        assertTrue(open.contains("questionPill = pillId") && open.contains("screen = Screen.QUESTION"))
        // Home's «Responder»/«Ver la pregunta», «También te espera», «Lo siguiente»: one callback for all of them.
        assertTrue(main.contains("onQuestion = ::openQuestion"))
        assertFalse(main.contains("onQuestion = { questionPill = it }"))
        // The tile (and its notification) through PendingLaunch.takeQuestion.
        assertTrue(main.substringAfter("model.launch.takeQuestion()").substringBefore("model.launch.take()").contains("openQuestion(pill)"))
    }

    @Test fun theStripOnTopOffersTheOtherPendingRequest() {
        assertTrue(screen.contains("StatusStrip("))
        assertTrue(src("ui/StatusStrip.kt").contains("onClick: (() -> Unit)? = null"))
        assertTrue(src("ui/StatusStrip.kt").contains("IconKind.CHEVRON"))
        assertTrue(main.contains("QuestionFlow.strip(model.approvals, model::agentName)"))
        // The tap goes back to Home with that request (its sheet, or Home's hero for the first one).
        val review = main.substringAfter("onReview = { fp ->", "").substringBefore("}")
        assertTrue(main.contains("private fun reviewFromQuestion("))
        val fn = main.substringAfter("private fun reviewFromQuestion(").substringBefore("\n    }")
        assertTrue(fn.contains("reviewApproval = fingerprint") && fn.contains("closedApproval = null") && fn.contains("screen = Screen.HOME"))
        assertTrue(review.isNotEmpty())
    }

    @Test fun theProgressHasOneLiveSegmentPerQuestion() {
        assertTrue(screen.contains("QuestionFlow.segments(request.questions, st)"))
        assertTrue(screen.contains("R.string.question_position"))
        assertTrue(screen.contains("KodaRole.LIVE"))
    }

    @Test fun theOptionsComeFromQuestionFlowWithTheirDescriptions() {
        assertTrue(screen.contains("QuestionFlow.toggle(request.questions, st, o.label)"))
        assertTrue(screen.contains("option.description"))
        assertTrue(screen.contains("Role.RadioButton") && screen.contains("Role.Checkbox"))
        assertTrue(screen.contains("R.string.question_pick_many"))
        assertTrue("48 dp targets", screen.contains("Spacing.MIN_TOUCH.dp"))
        assertTrue("selected: a live ring", screen.substringAfter("private fun OptionRow").contains("KodaRole.LIVE"))
    }

    @Test fun theHintFollowsTheCapability() {
        assertTrue(screen.contains("QuestionFlow.presence("))
        assertTrue(screen.contains("R.string.question_hint_answers"))
        assertTrue("without `answers`: where to answer, as before", screen.contains("R.string.question_body"))
        assertTrue(screen.contains("QuestionPresence.GONE"))
    }

    @Test fun sendingGoesThroughTheLockWithItsOwnTitle() {
        assertFalse("the screen never talks to the link itself", screen.contains("model.answerQuestion"))
        assertTrue(screen.contains("IconKind.LOCK"))
        assertTrue(screen.contains("ApprovalSheetPlan.GUARD_MS"))
        assertTrue(screen.contains("enabled = armed && QuestionFlow.complete"))
        val send = main.substringAfter("private fun sendAnswer").substringBefore("/** Allow:")
        assertTrue(send.indexOf("BiometricGate.confirm") in 0 until send.indexOf("model.answerQuestion"))
        assertTrue(send.contains("if (confirming) return"))
        assertTrue("«Confirmá que sos vos»", send.contains("getString(R.string.confirm_title)"))
        assertTrue("«Responder a <agent> en <computer>»", send.contains("R.string.confirm_answer"))
        assertFalse(send.contains("question_confirm_title"))
        // After the lock: back to Home, which says «Respuesta enviada» (or that it could not be sent).
        assertTrue(send.contains("screen = Screen.HOME"))
        assertTrue(send.contains("R.string.question_sent") && send.contains("R.string.question_not_sent"))
    }

    @Test fun thePicksAreNeverLoggedOrStored() {
        val send = main.substringAfter("private fun sendAnswer").substringBefore("/** Allow:")
        assertFalse(send.contains("Log."))
        assertFalse(screen.contains("Log."))
        assertFalse(screen.contains("rememberSaveable"))
        assertFalse(src("core/QuestionFlow.kt").contains("Log."))
    }

    @Test fun theNewWordsAreInEnglishAndSpanish() {
        val es = strings("values-es")
        assertTrue(es.contains("Responder acá está activado en Koda, en tu computadora. Si ya respondiste allá, esta pregunta se cierra sola."))
        assertTrue(es.contains("<string name=\"confirm_answer\">Responder a %1\$s en %2\$s</string>"))
        val en = strings("values")
        for (key in listOf("question_hint_answers", "confirm_answer", "question_gone")) {
            assertTrue(key, en.contains("<string name=\"$key\">"))
            assertTrue(key, es.contains("<string name=\"$key\">"))
        }
        assertFalse("replaced by confirm_title", en.contains("question_confirm_title"))
        assertEquals(1, Regex("name=\"confirm_title\"").findAll(es).count())
    }
}
