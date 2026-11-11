package com.kenai.android

import com.kenai.android.character.BotState
import com.kenai.android.core.Arrival
import com.kenai.android.core.BuzzMemory
import com.kenai.android.core.OverlayPolicy.ApprovalAlert
import com.kenai.android.core.QuietHours
import com.kenai.android.core.UserSettings
import com.kenai.android.core.VibratePolicy
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S2.7a: a buzz when an approval or a question arrives (kenai-0.1, L5 option 3a). */
class VibrationTest {
    private val on = UserSettings()
    private val off = UserSettings(vibrate = false)

    // ── the decision ────────────────────────────────────────────────────────

    @Test fun anApprovalWithTheHeadsUpDoesNotBuzzTwice() {
        // The heads-up channel already vibrates: no second buzz on top of it.
        assertFalse(VibratePolicy.onArrival(Arrival.APPROVAL, on, ApprovalAlert.HEADS_UP, alreadyBuzzed = false))
    }

    @Test fun anApprovalShownByTheIslandBuzzes() {
        // The quiet channel does not vibrate, so the phone does it.
        assertTrue(VibratePolicy.onArrival(Arrival.APPROVAL, on, ApprovalAlert.QUIET, alreadyBuzzed = false))
    }

    @Test fun aQuestionBuzzes() {
        assertTrue(VibratePolicy.onArrival(Arrival.QUESTION, on, null, alreadyBuzzed = false))
    }

    @Test fun theSwitchOffMeansNeverBuzz() {
        assertFalse(VibratePolicy.onArrival(Arrival.APPROVAL, off, ApprovalAlert.QUIET, alreadyBuzzed = false))
        assertFalse(VibratePolicy.onArrival(Arrival.QUESTION, off, null, alreadyBuzzed = false))
    }

    @Test fun quietHoursStillBuzzBecauseRequestsAndQuestionsAlwaysNotify() {
        val allDay = on.copy(quiet = QuietHours(true, 0, 23 * 60 + 59))
        assertTrue(VibratePolicy.onArrival(Arrival.QUESTION, allDay, null, alreadyBuzzed = false))
        assertTrue(VibratePolicy.onArrival(Arrival.APPROVAL, allDay, ApprovalAlert.QUIET, alreadyBuzzed = false))
    }

    @Test fun somethingAlreadyBuzzedForStaysSilent() {
        assertFalse(VibratePolicy.onArrival(Arrival.QUESTION, on, null, alreadyBuzzed = true))
        assertFalse(VibratePolicy.onArrival(Arrival.APPROVAL, on, ApprovalAlert.QUIET, alreadyBuzzed = true))
    }

    @Test fun onlyAChangeIntoAQuestionCountsNeverTheFirstPicture() {
        assertTrue(VibratePolicy.questionArrived(BotState.WORKING, BotState.QUESTION))
        assertFalse(VibratePolicy.questionArrived(null, BotState.QUESTION))
        assertFalse(VibratePolicy.questionArrived(BotState.QUESTION, BotState.QUESTION))
        assertFalse(VibratePolicy.questionArrived(BotState.WORKING, BotState.APPROVAL))
        assertFalse(VibratePolicy.questionArrived(BotState.WORKING, BotState.FINISHED))
    }

    @Test fun theBuzzIsShort() {
        assertTrue(VibratePolicy.PATTERN.sum() <= 400)
        assertTrue(VibratePolicy.PATTERN.first() == 0L)
    }

    // ── one buzz per question or request ────────────────────────────────────

    private fun arrive(m: BuzzMemory, kind: Arrival, pill: String, fp: String?, at: Long): Boolean {
        val buzz = VibratePolicy.onArrival(kind, on, if (kind == Arrival.APPROVAL) ApprovalAlert.QUIET else null, m.alreadyBuzzed(kind, pill, fp, at))
        m.remember(kind, pill, fp, at)
        return buzz
    }

    @Test fun theSameQuestionTwiceBuzzesOnce() {
        val m = BuzzMemory()
        assertTrue(arrive(m, Arrival.QUESTION, "codex", "fp1", 0))
        assertFalse(arrive(m, Arrival.QUESTION, "codex", "fp1", 1_000))
        // Offered again after a reconnection, long after: still the same question.
        assertFalse(arrive(m, Arrival.QUESTION, "codex", "fp1", 10 * 60_000))
    }

    @Test fun theQuestionAndTheSessionChangeBuzzOnceWhicheverComesFirst() {
        val questionFirst = BuzzMemory()
        assertTrue(arrive(questionFirst, Arrival.QUESTION, "codex", "fp1", 0))
        assertFalse(arrive(questionFirst, Arrival.QUESTION, "codex", null, 300))

        val sessionFirst = BuzzMemory()
        assertTrue(arrive(sessionFirst, Arrival.QUESTION, "codex", null, 0))
        assertFalse(arrive(sessionFirst, Arrival.QUESTION, "codex", "fp1", 300))
    }

    @Test fun aNewQuestionLaterOrFromAnotherAgentBuzzesAgain() {
        val m = BuzzMemory()
        assertTrue(arrive(m, Arrival.QUESTION, "codex", "fp1", 0))
        assertTrue(arrive(m, Arrival.QUESTION, "claude", "fp2", 100))
        assertTrue(arrive(m, Arrival.QUESTION, "codex", "fp3", BuzzMemory.PAIR_WINDOW_MS + 1))
        assertTrue(arrive(m, Arrival.QUESTION, "codex", null, 3 * BuzzMemory.PAIR_WINDOW_MS))
    }

    @Test fun theSameRequestTwiceBuzzesOnceButAnotherRequestOfTheSameAgentBuzzes() {
        val m = BuzzMemory()
        assertTrue(arrive(m, Arrival.APPROVAL, "claude", "a1", 0))
        assertFalse(arrive(m, Arrival.APPROVAL, "claude", "a1", 500))
        assertTrue(arrive(m, Arrival.APPROVAL, "claude", "a2", 600))
        // A question right after a request of the same agent is news of its own.
        assertTrue(arrive(m, Arrival.QUESTION, "claude", null, 700))
    }

    @Test fun theMemoryStaysSmall() {
        val m = BuzzMemory()
        for (i in 0 until 1_000) m.remember(Arrival.APPROVAL, "p$i", "f$i", i.toLong())
        assertTrue(m.size() <= BuzzMemory.MAX)
    }

    // ── Android wiring (source guards: the Android classes only run on a phone) ──

    private fun read(path: String) = File(path).readText()

    @Test fun theManifestAsksForVibrate() {
        assertTrue(read("src/main/AndroidManifest.xml").contains("""<uses-permission android:name="android.permission.VIBRATE" />"""))
    }

    @Test fun theModelBuzzesOnApprovalsQuestionsAndQuestionChanges() {
        val model = read("src/main/kotlin/com/kenai/android/app/AppModel.kt")
        fun body(name: String): String {
            val start = model.indexOf("override fun $name(")
            assertTrue("$name not found", start >= 0)
            val end = model.indexOf("\n    override fun ", start + 1).let { if (it < 0) model.length else it }
            return model.substring(start, end)
        }
        val approval = body("onApproval")
        assertTrue(approval.contains("buzzOnArrival(Arrival.APPROVAL"))
        // The same alert decides the notification and the buzz, so they never disagree.
        assertTrue(approval.contains("notifier.showApproval(request, agentName(request.pillId), alert)"))
        assertTrue(approval.contains("buzzOnArrival(Arrival.APPROVAL, request.pillId, request.fingerprint, alert)"))
        assertTrue(body("onQuestion").contains("buzzOnArrival(Arrival.QUESTION, request.pillId, request.fingerprint)"))
        assertTrue(body("onSessions").contains("VibratePolicy.questionArrived(prev, s.state)"))
        assertTrue(model.contains("VibratePolicy.onArrival("))
        assertTrue(model.contains("buzzMemory.alreadyBuzzed("))
        assertTrue(model.contains("buzzMemory.remember("))
    }

    @Test fun hapticsUsesTheRightServiceForEachAndroidVersion() {
        val h = read("src/main/kotlin/com/kenai/android/app/Haptics.kt")
        assertTrue(h.contains("VibratorManager"))
        assertTrue(h.contains("Build.VERSION_CODES.S"))
        assertTrue(h.contains("VibratePolicy.PATTERN"))
        assertTrue(h.contains("hasVibrator()"))
        assertTrue(h.contains("runCatching"))
    }

    @Test fun settingsShowTheSwitchInTheSoundSection() {
        val ui = read("src/main/kotlin/com/kenai/android/ui/SettingsScreens.kt")
        val sound = ui.indexOf("R.string.settings_section_sound")
        val notices = ui.indexOf("R.string.settings_section_notices")
        val sw = ui.indexOf("SettingSwitch(stringResource(R.string.settings_vibrate), stringResource(R.string.settings_vibrate_hint), s.vibrate)")
        assertTrue(sound in 0 until sw)
        assertTrue(sw < notices)
        assertTrue(ui.contains("model.updateSettings(s.copy(vibrate = it))"))
        val strings = read("../i18n/app-strings.json")
        assertTrue(strings.contains("\"settings_vibrate\": \"Vibrate\""))
        assertTrue(strings.contains("\"settings_vibrate_hint\""))
    }
}
