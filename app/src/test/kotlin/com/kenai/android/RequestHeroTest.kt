package com.kenai.android

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for Inicio's request hero (kenai-0.1 S9, T13; L21 «a»). Compose cannot be rendered in a JVM test, so these read the sources:
 * the exact command is shown in full and scrolls, a cut command is said and never allowed here, Allow keeps the lock and the 600 ms
 * guard, Deny needs nothing, and the expiry counts down from the phone's own arrival time.
 */
class RequestHeroTest {
    private fun src(name: String) = File("src/main/kotlin/com/kenai/android/$name").readText()
    private val hero get() = src("ui/RequestHero.kt")
    private val main get() = src("MainActivity.kt")

    @Test fun theCommandIsShownWholeInAScrollingMonoBlockNamedForTalkBack() {
        val box = hero.substringAfter("R.string.request_command").substringBefore("Text(r.command")
        assertTrue(box.contains("heightIn(max = ApprovalSheetPlan.COMMAND_BOX_MAX_DP.dp)"))
        assertTrue(box.contains("verticalScroll(rememberScrollState())"))
        assertTrue(hero.contains("FontFamily.Monospace"))
        assertFalse("never cut by a line limit", hero.substringAfter("Text(r.command").substringBefore("\n").contains("maxLines"))
    }

    @Test fun aCutCommandIsSaidAndAllowIsOnlyOfferedWhenTheWholeCommandIsHere() {
        assertTrue(hero.contains("val canAllow = ApprovalSheetPlan.canAllowHere(r)"))
        assertTrue(hero.contains("R.string.approval_too_long") && hero.contains("R.string.approval_on_computer"))
        assertTrue(hero.substringAfter("val canAllow").substringBefore("PillKind.GO").contains("if (canAllow)"))
    }

    @Test fun allowGoesThroughTheLockAndDenyNeedsNone() {
        assertFalse("the hero never allows by itself", Regex("""allow\s*=\s*true""").containsMatchIn(hero))
        assertTrue(hero.contains("onAllow(r)"))
        assertTrue(hero.contains("model.decide(r.fingerprint, allow = false)"))
        assertTrue(main.contains("onApprove = ::approve"))
        assertTrue("the activity hands Allow to the lock", main.substringAfter("private fun Home(").contains("onAllow = onApprove"))
        assertTrue("Allow carries the fingerprint", hero.contains("IconKind.FINGERPRINT"))
        assertTrue(hero.contains("R.string.request_hint"))
    }

    @Test fun bothButtonsIgnoreTapsDuringTheFirstMoments() {
        assertTrue(hero.contains("delay(ApprovalSheetPlan.GUARD_MS)"))
        assertEquals(2, Regex("""enabled = armed""").findAll(hero).count())
        assertTrue("re-armed for each request", hero.contains("remember(r.fingerprint) { mutableStateOf(false) }"))
    }

    @Test fun theExpiryCountsDownFromTheArrivalOnThisPhone() {
        assertTrue(hero.contains("HomePanel.remainingMs(") && hero.contains("HomePanel.expiry(") && hero.contains("HomePanel.expiryFraction("))
        assertTrue("ticks every second", hero.contains("delay(1_000)"))
        assertTrue(hero.contains("SystemClock.elapsedRealtime()"))
        val model = src("app/AppModel.kt")
        assertTrue(model.contains("var arrivals by mutableStateOf"))
        val onApproval = model.substringAfter("override fun onApproval(").substringBefore("override fun onQuestion(")
        assertTrue(onApproval.contains("arrivals = arrivals + (request.fingerprint to android.os.SystemClock.elapsedRealtime())"))
        assertTrue("forgotten with the request", model.substringAfter("private fun removeApproval(").substringBefore("// ──").contains("arrivals = arrivals - fingerprint"))
    }

    @Test fun theRiskLineComesFromTheLocalDetectorAndTheCautionAlwaysShows() {
        assertTrue(hero.contains("HomeText.riskLine("))
        assertTrue(hero.contains("R.string.approval_caution"))
        assertTrue(hero.contains("IconKind.WARNING"))
    }

    @Test fun theProjectComesFromTheSessionDetailsOnly() {
        assertTrue(hero.contains("model.sessions.firstOrNull { it.pillId == r.pillId }?.project"))
    }

    @Test fun theHeroIsDarkWithTheSunWash() {
        assertTrue(hero.contains("HeroPanel(wash = Color(KodaRole.SUN))"))
        assertTrue(hero.contains("KodaView(BotState.APPROVAL"))
    }

    @Test fun theStripIsAlwaysDarkWithARingInTheRoleColour() {
        val strip = src("ui/StatusStrip.kt")
        assertTrue(strip.contains("IslandSurface.PANEL"))
        assertTrue(strip.contains("border(1.dp, ring"))
        for (role in listOf("KodaRole.SUN", "KodaRole.GO", "KodaRole.RED")) assertTrue(role, strip.contains(role))
        assertTrue(strip.contains("liveRegion"))
    }

    // ── The result of a decision (kenai-0.1 S9, screen 3; T14) ──

    private val result get() = src("ui/ResultHero.kt")

    @Test fun theResultHeroDrawsEveryStateFromTheCoreWords() {
        for (k in listOf("SENDING", "ALLOWED", "DENIED", "EXPIRED", "FAILED")) assertTrue(k, result.contains("OutcomeKind.$k"))
        assertTrue(result.contains("HomeText.resultTitle(") && result.contains("HomeText.resultBody(") && result.contains("HomeText.resultMeta("))
        assertTrue("Koda's pose and the wash follow the result", result.contains("KodaView(DecisionOutcomes.pose(") && result.contains("HeroPanel(wash = Color(DecisionOutcomes.role("))
        assertTrue("the meta line has the clock icon", result.contains("IconKind.CLOCK"))
        assertTrue("TalkBack hears the change once", result.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(result.contains("semantics { heading() }"))
    }

    @Test fun whileSendingBothButtonsAreThereAndDisabled() {
        val sending = result.substringAfter("OutcomeKind.SENDING ->").substringBefore("OutcomeKind.FAILED ->")
        assertTrue(sending.contains("R.string.action_deny") && sending.contains("R.string.result_sending"))
        assertEquals(2, Regex("""enabled = false""").findAll(sending).count())
    }

    @Test fun aFailedDecisionOffersTryAgainAndTheChecksAndTheOthersClose() {
        val failed = result.substringAfter("OutcomeKind.FAILED ->").substringBefore("else ->")
        assertTrue(failed.contains("R.string.result_retry") && failed.contains("onRetry"))
        assertTrue(failed.contains("R.string.diag_title") && failed.contains("onHelp"))
        assertTrue(result.substringAfter("else ->").contains("R.string.action_close") && result.contains("onClose"))
        assertFalse("the result never decides by itself", result.contains("model.decide("))
    }

    @Test fun whatComesNextAnswersAQuestionOrReviewsTheNextRequest() {
        assertTrue(result.contains("R.string.result_next"))
        assertTrue(result.contains("R.string.home_answer") && result.contains("onQuestion("))
        assertTrue(result.contains("R.string.home_review") && result.contains("onReview("))
        assertTrue(result.contains("HomeText.nextLine("))
        assertTrue(main.contains("HomePanel.next("))
    }

    @Test fun homeShowsTheResultAsItsHeroAndTheModelKeepsIt() {
        val home = main.substringAfter("private fun Home(")
        assertTrue(home.contains("HomePanel.Scene.RESULT ->"))
        assertTrue(home.contains("ResultHero("))
        assertTrue(home.contains("model.dismissOutcome()"))
        assertTrue(home.contains("model.retryDecision()"))
        assertTrue("a new request beats the result", home.contains("newestArrivalMs = model.arrivals.values.maxOrNull()"))
        val model = src("app/AppModel.kt")
        assertTrue(model.contains("var outcome by mutableStateOf<Outcome?>"))
        assertTrue(model.substringAfter("fun decide(fingerprint").substringBefore("\n    }\n").contains("outcomes.decided("))
        assertTrue(model.substringAfter("override fun onApproval(").substringBefore("override fun onQuestion(").contains("outcomes.arrived("))
        assertTrue(model.substringAfter("override fun onState(").substringBefore("\n    }\n").contains("outcomes.linkLost()"))
        assertTrue(model.contains("override fun onDecisionNotSent(") && model.contains("outcomes.sendFailed("))
        assertTrue(model.substringAfter("override fun onApprovalResolved(").substringBefore("\n    }\n").contains("outcomes.resolved("))
        val strings = File("../i18n/app-strings.es.json").readText()
        for (k in listOf("\"result_next\": \"Lo siguiente\"", "\"result_sending\": \"Enviando…\"", "\"result_retry\": \"Volver a intentar\"")) assertTrue(k, strings.contains(k))
    }

    @Test fun theStripSpinsWhileSending() {
        val strip = src("ui/StatusStrip.kt")
        assertTrue(strip.contains("if (strip.spinner)") && strip.contains("CircularProgressIndicator("))
        for (role in listOf("KodaRole.LIVE", "KodaRole.FAINT")) assertTrue(role, strip.contains(role))
    }

    // ── One «what just happened» card on Home (kenai-0.1 S9, T19; L29 a) ──

    @Test fun homeDrawsOnlyOneOfTheResultHeroAndTheAnswerCard() {
        val home = main.substringAfter("private fun Home(")
        assertTrue("the one slot comes from core", home.contains("val card = HomePanel.card(result, model.answerNote)"))
        val resultBranch = home.substringAfter("HomePanel.Scene.RESULT ->").substringBefore("HomePanel.Scene.OFFLINE ->")
        assertTrue("the result hero is drawn only while the slot holds the result", resultBranch.contains("HomePanel.HomeCard.Result") && resultBranch.contains("ResultHero("))
        val answer = home.substringAfter("HomePanel.HomeCard.Note")
        assertTrue("the answer card is drawn from the same slot", answer.contains("MessagePanel("))
        assertTrue("its own «Cerrar» clears it", answer.contains("model.dismissAnswer()"))
        val model = src("app/AppModel.kt")
        assertTrue("the answer has the same life as a result", model.contains("var answerNote by mutableStateOf") && model.contains("outcomes.answered("))
        assertTrue("it never rides the plain message slot", model.contains("fun answered(text: String)"))
        assertTrue("and it expires like a result", model.substringAfter("private fun showOutcome()").substringBefore("} ").contains("answerNote"))
    }

    // ── The request's agent also asks (kenai-0.1 S9, T17; L24) ──

    @Test fun theQuestionOfTheRequestsAgentIsOfferedInAlsoWaiting() {
        val home = main.substringAfter("private fun Home(")
        assertTrue("the rule comes from core", home.contains("HomePanel.heroAsks("))
        assertTrue("questions held on the phone count", home.contains("questionPills = model.questions.map { it.pillId }.toSet()"))
        assertTrue("for the request hero, and for the request offered next under a result", home.contains("HomePanel.Scene.REQUEST -> hero?.pillId") && home.contains("HomePanel.NextKind.APPROVAL"))
        assertTrue("the section shows even when only that question waits", home.contains("if (groups.waiting.isNotEmpty() || asks != null)"))
        assertTrue(home.contains("asks = asks"))
        val ui = src("ui/HomePanel.kt")
        val section = ui.substringAfter("fun AlsoWaiting(").substringBefore("\n}\n")
        assertTrue(section.contains("asks: SessionInfo?"))
        assertTrue("«Responder» opens that agent's question", section.contains("onQuestion(a.pillId)") && section.contains("R.string.home_answer"))
        assertTrue("drawn as a question, whatever the computer shows", section.contains("a.copy(state = BotState.QUESTION)"))
    }
}
