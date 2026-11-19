package com.kenai.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards for how the island over other apps is drawn (the window cannot be rendered in a JVM test, so these read the source). */
class IslandOverlayTest {
    private val src = File("src/main/kotlin/com/kenai/android/ui/IslandOverlay.kt").readText()

    @Test fun theRequestCardNamesTheActionAndNeverShowsTheCommand() {
        val card = src.substringAfter("private fun RequestCard").substringBefore("A window outside any activity")
        assertTrue(card.contains("R.string.approval_wants"))
        assertFalse(card.contains("Monospace") || card.contains(".command") || card.contains("s.text,"))
        assertTrue("Allow keeps its lock note", card.contains("R.string.approval_hint"))
    }

    @Test fun everyTapStillGoesThroughTheEarlyTapGuard() {
        for (what in listOf("tap(\"Deny\")", "tap(\"Allow\")", "tap(\"the request header\")", "tap(\"the result card\")", "tap(\"the working strip\")", "tap(\"the question card\")")) {
            assertTrue(what, src.contains(what))
        }
    }

    @Test fun aQuestionThePhoneHoldsOpensThatQuestionAndTheIslandNeverAnswersIt() {
        // Live finding: the question card's whole row opened a bare MainActivity, so the question never showed. A question whose frame
        // is on the phone now opens THAT question — the same door as «Responder» on its notification — and nothing else changes.
        assertTrue("its own callback, next to Allow, Deny and open", src.contains("private val onAnswer: (pillId: String) -> Unit"))
        val card = src.substringAfter("private fun ResultCard").substringBefore("private fun RequestCard")
        assertTrue(card.contains("s.kind == IslandSpec.Kind.QUESTION && s.canAnswerHere"))
        assertTrue(card.contains("tap(\"the question card\") { onAnswer(s.pillId) }"))
        assertTrue("a question with no frame here keeps opening the app", card.contains("tap(\"the result card\") { onOpen() }"))
        // S10/S9: the island only opens the question. The answer stays behind the app's screen lock, and it launches nothing.
        assertFalse("nothing is decided from the island", src.contains("decide("))
        assertFalse("no answer is sent from here", src.contains("answerQuestion(") || src.contains("?.answer("))
    }

    @Test fun theQuestionCardSaysWhereTheAnswerGoes() {
        val card = src.substringAfter("private fun ResultCard").substringBefore("private fun RequestCard")
        assertTrue("answerable here: it says so", card.contains("R.string.island_answer_here"))
        assertTrue("no frame for it: the old line stays", card.contains("R.string.island_answer_on_pc"))
        assertTrue("a notice of another kind still shows the agent's own text", card.contains("s.text"))
    }

    @Test fun theRequestCardStillRoutesAllowAndDenyAndNeverDecides() {
        val card = src.substringAfter("private fun RequestCard").substringBefore("A window outside any activity")
        assertTrue(card.contains("tap(\"Allow\") { onAllow(fingerprint) }"))
        assertTrue(card.contains("tap(\"Deny\") { onDeny(fingerprint) }"))
        assertFalse("the island never decides by itself", card.contains("decide("))
    }

    @Test fun theQuestionCardCopySaysWhereTheAnswerGoes() {
        fun strings(path: String) =
            Regex("""<string name="(\w+)">(.*?)</string>""").findAll(File(path).readText()).associate { it.groupValues[1] to it.groupValues[2] }
        val en = strings("src/main/res/values/strings.xml")
        val es = strings("src/main/res/values-es/strings.xml")
        // A question the phone holds: tapped here, answered behind the app's lock (never from the island).
        assertEquals("Tap to answer", en["island_answer_here"])
        assertEquals("Tocá para responder", es["island_answer_here"])
        // No frame for it on this phone: the old line stays, so nobody is sent to a question that is not here.
        assertEquals("Tap to open Kenai, or answer on your computer", en["island_answer_on_pc"])
        assertEquals("Tocá para abrir Kenai, o respondé en tu computadora", es["island_answer_on_pc"])
    }

    @Test fun theModelWiresTheQuestionTapToTheNotificationsOwnQuestionRoute() {
        val model = File("src/main/kotlin/com/kenai/android/app/AppModel.kt").readText()
        val wiring = model.substringAfter("private val overlay = IslandOverlay(").substringBefore("fun overlayPermitted")
        assertTrue(wiring.contains("onAllow = { notifier.openAllow(it) }"))
        assertTrue(wiring.contains("onDeny = { decide(it, allow = false) }"))
        assertTrue(wiring.contains("onOpen = { notifier.openApp() }"))
        assertTrue("the question opens that question, through the notification's own door", wiring.contains("onAnswer = { notifier.openQuestion(it) }"))
        assertFalse("no allow is ever wired from the island", wiring.contains("allow = true"))
        assertTrue(
            "what the island draws knows which questions the phone holds",
            model.substringAfter("private fun refreshIsland(").substringBefore("var wantAllow")
                .contains("questions = questions.map { it.pillId }.toSet()"),
        )
    }
}
