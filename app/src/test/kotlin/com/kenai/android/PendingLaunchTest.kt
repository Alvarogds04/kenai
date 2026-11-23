package com.kenai.android

import com.kenai.android.core.LaunchScreen
import com.kenai.android.core.PendingLaunch
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingLaunchTest {
    private var now = 1_000L
    private val launch = PendingLaunch { now }

    @Test fun aRequestFromOurOwnTapIsTakenOnce() {
        launch.request("fp-1", allow = true)
        assertEquals(PendingLaunch.Request("fp-1", true), launch.take())
        assertNull("only once", launch.take())
    }

    @Test fun nothingAskedMeansNothingToTake() {
        // A foreign intent with the same extras never reaches here: MainActivity does not read them (see below),
        // so with no tap on our own notification there is simply no request.
        assertNull(launch.take())
    }

    @Test fun aStaleRequestIsDropped() {
        launch.request("fp-1", allow = true)
        now += PendingLaunch.TTL_MS + 1
        assertNull(launch.take())
        launch.request("fp-2", allow = true)
        now += PendingLaunch.TTL_MS
        assertEquals("fp-2", launch.take()!!.fingerprint)
    }

    @Test fun aBlankOrHugeFingerprintCanNeverAllow() {
        launch.request("", allow = true)
        assertNull(launch.take())
        launch.request(null, allow = true)
        assertNull(launch.take())
        launch.request("x".repeat(PendingLaunch.MAX_FP + 1), allow = true)
        assertNull(launch.take())
    }

    @Test fun aNewerRequestReplacesTheOlderOne() {
        launch.request("old", allow = true)
        launch.request("new", allow = false)
        assertEquals(PendingLaunch.Request("new", false), launch.take())
    }

    private val main = "src/main/kotlin/com/kenai/android/"
    private fun manifest() = File("src/main/AndroidManifest.xml").readText()

    @Test fun theExportedActivityNeverReadsTheRequestFromItsIntent() {
        val src = File(main + "MainActivity.kt").readText()
        for (bad in listOf("EXTRA_FP", "EXTRA_ALLOW", "getStringExtra", "getBooleanExtra", "\"fingerprint\"", "\"allow\"")) {
            assertFalse("MainActivity must not read $bad from an intent", src.contains(bad))
        }
        assertTrue(src.contains("model.launch.take()"))
    }

    @Test fun onlyTheNonExportedEntryActivityCanMakeARequest() {
        val callers = File(main).walkTopDown().filter { it.extension == "kt" }
            .filter { it.readText().contains("launch.request(") }.map { it.name }.toList()
        assertEquals(listOf("LaunchActivity.kt"), callers)
        val m = manifest()
        val entry = m.substringAfter("""android:name=".app.LaunchActivity"""").substringBefore("/>")
        assertTrue(entry.contains("""android:exported="false""""))
        assertTrue("it is the only way the request reaches the app", m.contains(".app.LaunchActivity"))
    }

    @Test fun ourNotificationsAndTheIslandGoThroughIt() {
        val n = File(main + "app/Notifications.kt").readText()
        assertTrue(n.contains("LaunchActivity::class.java"))
        // The only component that takes the fingerprint extra from the outside is the Deny receiver, which is not exported.
        assertTrue(manifest().substringAfter(""".app.ActionReceiver"""").substringBefore("/>").contains("""android:exported="false""""))
    }

    @Test fun everyOtherComponentWithAnIntentFilterIsTheLauncher() {
        val m = manifest()
        val exported = Regex("""android:name="([^"]+)"[^>]*android:exported="true"""").findAll(m).map { it.groupValues[1] }.toSet()
        assertEquals(setOf(
                ".MainActivity", ".app.GlanceWidget", ".app.GlanceTile", ".app.SoloWidget", ".app.ListWidget", ".app.CharacterBigWidget",
                ".app.StatusPillWidget", ".app.WaitingAlertWidget", ".app.UsageRingWidget", ".app.TodayWidget", ".app.MoodWidget",
            ), exported)
    }

    @Test fun aWidgetRowAsksForASessionOnce() {
        launch.requestSession("agent_codex")
        assertEquals("agent_codex", launch.takeSession())
        assertNull("only once", launch.takeSession())
    }

    @Test fun aStaleOrBadSessionRequestIsDropped() {
        launch.requestSession("agent_codex")
        now += PendingLaunch.TTL_MS + 1
        assertNull(launch.takeSession())
        launch.requestSession("")
        assertNull(launch.takeSession())
        launch.requestSession(null)
        assertNull(launch.takeSession())
        launch.requestSession("x".repeat(PendingLaunch.MAX_PILL + 1))
        assertNull(launch.takeSession())
    }

    @Test fun theSessionSlotAndTheAllowSlotNeverMeet() {
        launch.request("fp-1", allow = true)
        launch.requestSession("agent_codex")
        assertEquals("agent_codex", launch.takeSession())
        assertEquals("the Allow is still there", PendingLaunch.Request("fp-1", true), launch.take())
        launch.requestSession("agent_codex")
        assertNull("a session request is never an Allow", launch.take())
        assertEquals("agent_codex", launch.takeSession())
    }

    @Test fun theExportedActivityOnlyAsksTheModelForTheSessionToOpen() {
        val src = File(main + "MainActivity.kt").readText()
        assertTrue(src.contains("model.launch.takeSession()"))
        assertFalse(src.contains("EXTRA_SESSION"))
        val callers = File(main).walkTopDown().filter { it.extension == "kt" }
            .filter { it.readText().contains("launch.requestSession(") }.map { it.name }.toList()
        assertEquals(listOf("LaunchActivity.kt"), callers)
    }

    // ── The screen slot (a Widget button) ───────────────────────────────────────────────────────────

    @Test fun aWidgetButtonAsksForAScreenOnce() {
        launch.requestScreen(LaunchScreen.CHAT)
        assertEquals(LaunchScreen.CHAT, launch.takeScreen())
        assertNull("only once, and the slot is cleared", launch.takeScreen())
    }

    @Test fun nothingAskedMeansNoScreen() = assertNull(launch.takeScreen())

    @Test fun aStaleScreenRequestIsDropped() {
        launch.requestScreen(LaunchScreen.SETTINGS)
        now += PendingLaunch.TTL_MS + 1
        assertNull(launch.takeScreen())
        launch.requestScreen(LaunchScreen.RECENT)
        now += PendingLaunch.TTL_MS
        assertEquals(LaunchScreen.RECENT, launch.takeScreen())
    }

    @Test fun aNewerScreenRequestReplacesTheOlderOneAndNullClearsIt() {
        launch.requestScreen(LaunchScreen.HOME)
        launch.requestScreen(LaunchScreen.PAIR)
        assertEquals(LaunchScreen.PAIR, launch.takeScreen())
        launch.requestScreen(LaunchScreen.HOME)
        launch.requestScreen(null)
        assertNull(launch.takeScreen())
    }

    @Test fun theThreeSlotsNeverMeet() {
        launch.request("fp-1", allow = true)
        launch.requestSession("agent_codex")
        launch.requestScreen(LaunchScreen.CHAT)
        assertEquals(LaunchScreen.CHAT, launch.takeScreen())
        assertNull("a screen never turns into a session", launch.takeScreen())
        assertEquals("agent_codex", launch.takeSession())
        assertEquals("the Allow is still there, untouched", PendingLaunch.Request("fp-1", true), launch.take())
        launch.requestScreen(LaunchScreen.HOME)
        assertNull("a screen is never an Allow", launch.take())
        assertNull("nor a session", launch.takeSession())
        assertEquals(LaunchScreen.HOME, launch.takeScreen())
    }

    @Test fun theScreenSlotOnlyHoldsAValueOfTheClosedList() {
        val src = File("src/main/kotlin/com/kenai/android/core/PendingLaunch.kt").readText()
        assertTrue(src.contains("fun requestScreen(which: LaunchScreen?)"))
        assertTrue(src.contains("fun takeScreen(): LaunchScreen?"))
        assertTrue(src.contains("private var screen: LaunchScreen?"))
    }

    // ── The question slot (a tap on the quick-settings tile) ────────────────────────────────────────

    @Test fun theTileAsksForAQuestionOnce() {
        launch.requestQuestion("agent_codex")
        assertEquals("agent_codex", launch.takeQuestion())
        assertNull("only once", launch.takeQuestion())
        assertNull("nothing asked", launch.takeQuestion())
    }

    @Test fun aStaleOrBadQuestionRequestIsDropped() {
        launch.requestQuestion("agent_codex")
        now += PendingLaunch.TTL_MS + 1
        assertNull(launch.takeQuestion())
        for (bad in listOf("", " ", null, "x".repeat(PendingLaunch.MAX_PILL + 1))) {
            launch.requestQuestion(bad)
            assertNull("'$bad'", launch.takeQuestion())
        }
    }

    @Test fun theQuestionSlotMeetsNoOther() {
        launch.request("fp-1", allow = true)
        launch.requestSession("agent_claude")
        launch.requestScreen(LaunchScreen.CHAT)
        launch.requestQuestion("agent_codex")
        assertEquals("agent_codex", launch.takeQuestion())
        assertEquals(LaunchScreen.CHAT, launch.takeScreen())
        assertEquals("agent_claude", launch.takeSession())
        assertEquals("the Allow is still there, untouched", PendingLaunch.Request("fp-1", true), launch.take())
        launch.requestQuestion("agent_codex")
        assertNull("a question is never an Allow", launch.take())
        assertNull("nor a session", launch.takeSession())
        assertNull("nor a screen", launch.takeScreen())
    }

    @Test fun theExportedActivityOnlyAsksTheModelForTheQuestionToOpen() {
        val src = File(main + "MainActivity.kt").readText()
        assertTrue(src.contains("model.launch.takeQuestion()"))
        assertFalse(src.contains("EXTRA_QUESTION"))
        val callers = File(main).walkTopDown().filter { it.extension == "kt" }
            .filter { it.readText().contains("launch.requestQuestion(") }.map { it.name }.toList()
        assertEquals(listOf("LaunchActivity.kt"), callers)
    }

    @Test fun theExportedActivityOnlyAsksTheModelForTheScreenToOpen() {
        val src = File(main + "MainActivity.kt").readText()
        assertTrue(src.contains("model.launch.takeScreen()"))
        for (bad in listOf("EXTRA_SCREEN", "getStringExtra", "getExtras", "intent.extras", "hasExtra")) assertFalse("MainActivity reads $bad", src.contains(bad))
        val callers = File(main).walkTopDown().filter { it.extension == "kt" }
            .filter { it.readText().contains("launch.requestScreen(") }.map { it.name }.toList()
        assertEquals(listOf("LaunchActivity.kt"), callers)
    }

    // kenai-0.1 S9, T17 (L26): «Revisar el pedido» (notification, widget, tile) from any screen lands on Home with THAT request.
    @Test fun reviewingARequestFromOutsideAlwaysLandsOnHomeWithThatRequest() {
        val src = File(main + "MainActivity.kt").readText()
        val review = src.substringAfter("if (!launch.allow) {").substringBefore("\n            return\n")
        assertTrue("Home from every screen, not a sheet over Settings or Chat", review.contains("screen = Screen.HOME"))
        assertFalse("never only from the camera and a question", review.contains("if (screen == Screen.SCAN || screen == Screen.QUESTION) screen = Screen.HOME"))
        assertTrue("an open question is left", review.contains("questionPill = null"))
        assertTrue("its sheet rises again even if it was closed", review.contains("closedApproval = null"))
        assertTrue("the hero's own request: its result gives way to it", review.contains("ApprovalSheetPlan.hero(model.approvals)?.fingerprint == fp") && review.contains("model.dismissOutcome()"))
        assertTrue("another request: its sheet over Home", review.contains("reviewApproval = fp"))
    }

    // ── Allow tapped after its request is gone (KN-04 T4, S10) ──────────────────────────────────────

    @Test fun anAllowTappedAfterItsRequestIsGoneOpensHomeAndSendsNothing() {
        // The tap reaches the app with a request the phone no longer holds (decided here, decided on the computer, expired): the app
        // opens on Home and says so. Nothing is sent for it, and the allow is not kept for the next request either.
        val src = File(main + "MainActivity.kt").readText()
        val branch = src.substringAfter("val launch = model.launch.take() ?: return").substringBefore("private fun openQuestion(")
        assertTrue("only a request still here reaches the prompt", branch.contains("request != null -> approve(request)"))
        val gone = branch.substringAfter("else ->")
        assertTrue("Home, from whatever screen was open", gone.contains("screen = Screen.HOME"))
        assertFalse("nothing is decided for it", gone.contains("decide(") || gone.contains("approve("))
        assertTrue("in its own words", gone.contains("R.string.msg_not_pending"))
    }

    @Test fun theResponderOfAQuestionNotificationOnlyAsksForThatQuestion() {
        // «Responder» on a question's notice is the tile's own route: the pill id, once, through the non-exported entry point.
        val n = File(main + "app/Notifications.kt").readText()
        val pi = n.substringAfter("private fun openQuestion(").substringBefore("fun openAllow(")
        assertTrue("into the app through our own activity", pi.contains("LaunchActivity::class.java"))
        assertTrue("with the pill id and nothing else", pi.contains("putExtra(LaunchActivity.EXTRA_QUESTION, pillId)"))
        assertEquals("one extra, and it is the pill id", 1, Regex("""putExtra\(""").findAll(pi).count())
        val entry = File(main + "app/LaunchActivity.kt").readText()
        assertTrue(entry.contains("model.launch.requestQuestion(intent.getStringExtra(EXTRA_QUESTION))"))
        val activity = File(main + "MainActivity.kt").readText()
        assertTrue("and the app opens that question", activity.contains("model.launch.takeQuestion()"))
        assertEquals("the one way a question reaches the app", listOf("LaunchActivity.kt"), File(main).walkTopDown().filter { it.extension == "kt" }
            .filter { it.readText().contains("launch.requestQuestion(") }.map { it.name }.toList())
    }
}
