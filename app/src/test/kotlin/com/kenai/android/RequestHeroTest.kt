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
}
