package com.kenai.android

import com.kenai.android.character.BotState
import com.kenai.android.core.NotificationPlan
import com.kenai.android.core.SafeText
import com.kenai.android.core.Words
import com.kenai.android.link.ApprovalRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The approval notification of «Decidí en un toque» (kenai-0.1 S9, screen 5): «<Agent> necesita tu aprobación», «Quiere ejecutar un
 * comando», a live countdown in the header, and the actions «Denegar», «Revisar el pedido» and «Permitir» — the last one only opens
 * the app on that request's own fingerprint / screen-lock prompt, never an allow in the shade (KN-04 T4, S10). A question's notice
 * gets «Responder», which only opens that question. Private on the lock screen, never a command or a file. Android classes only
 * compile in CI: pure decisions plus source guards.
 */
class NotificationsTest {
    private val n get() = File("src/main/kotlin/com/kenai/android/app/Notifications.kt").readText()
    private val approval get() = n.substringAfter("fun showApproval(").substringBefore("private fun wakeScreen")
    private fun strings(path: String): Map<String, String> =
        Regex("""<string name="(\w+)">(.*?)</string>""").findAll(File(path).readText()).associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }

    // ── What it says ────────────────────────────────────────────────────────────────────────────────

    @Test fun theTitleNamesTheAgentAndTheLineSaysWhatItWants() {
        val es = SafeText.approvalNotice("Claude Code", "Bash", Words.ES)
        assertEquals("Claude Code necesita tu aprobación", es.title)
        assertEquals("Quiere ejecutar un comando", es.line)
        val en = SafeText.approvalNotice("Claude Code", "Bash", Words.EN)
        assertEquals("Claude Code needs your approval", en.title)
        assertEquals("Wants to run a command", en.line)
    }

    @Test fun theLineHasNoClockOfItsOwnAndAnAgentWithNoNameIsStillSomeone() {
        // The live countdown is the header's (the chronometer): a static «Caduca en 2:00» on the line would soon disagree with it.
        assertEquals("Quiere editar archivos", SafeText.approvalNotice("Codex", "Edit", Words.ES).line)
        for (w in listOf(Words.ES, Words.EN)) {
            val line = SafeText.approvalNotice("Codex", "Bash", w).line
            assertFalse(line, line.contains("Caduca") || line.contains("Expires") || line.contains(":"))
        }
        assertEquals("Un agente necesita tu aprobación", SafeText.approvalNotice("  ", "Bash", Words.ES).title)
        assertFalse("no expiry text is built for the notification", approval.contains("expiresIn") || approval.contains("HomePanel.expiry"))
    }

    @Test fun neverACommandOrAFileInTheText() {
        // The notice takes the tool's name only: a command, a path or a secret has no way in.
        for (tool in listOf("Bash", "Edit", "Write", "mcp__github__create_issue", "")) {
            val t = SafeText.approvalNotice("Claude Code", tool, Words.ES)
            for (bad in listOf("/", "=", "rm ", "npm", "secret")) assertFalse("$tool: ${t.title} / ${t.line}", (t.title + t.line).contains(bad))
        }
        assertTrue(approval.contains("SafeText.approvalNotice("))
        assertFalse("the request's command never reaches the notification", approval.contains("r.command"))
    }

    // ── How it is built ─────────────────────────────────────────────────────────────────────────────

    @Test fun theActionsAreDenyReviewAndAnAllowThatOnlyOpensTheApp() {
        // Pure: what the shade offers for a whole request, in the order it is drawn. Deny first, Allow last and furthest from it.
        val whole = ApprovalRequest("agent_claude", "fp-1", "Bash", "ls", 0L)
        assertEquals(
            listOf(NotificationPlan.Action.DENY, NotificationPlan.Action.REVIEW, NotificationPlan.Action.ALLOW),
            NotificationPlan.approvalActions(whole),
        )
        assertTrue(approval.contains("R.string.action_deny"))
        assertTrue(approval.contains("R.string.action_review"))
        // «Permitir» in the shade is the pill's and the tile's door: the app on this request's own prompt, never a decision.
        assertTrue(approval.contains("R.string.action_allow"))
        assertTrue(approval.contains("open(r.fingerprint, true, id + 1)"))
        // Review opens the app on that request, the same way as a tap on the notification: never with allow = true.
        assertTrue(approval.contains("open(r.fingerprint, false, id + 2)"))
        // Every button comes from the plan, so no other action can reach the shade.
        assertTrue(approval.contains("NotificationPlan.approvalActions(r)"))
        // Deny is still there and still instant: a broadcast to the receiver, with no prompt in between.
        assertTrue(approval.contains("PendingIntent.getBroadcast(") && approval.contains("ActionReceiver::class.java"))
        assertFalse("Deny never waits for the screen lock", n.substringAfter("class ActionReceiver").contains("BiometricGate"))
        assertEquals("Allow", strings("src/main/res/values/strings.xml")["action_allow"])
        assertEquals("Permitir", strings("src/main/res/values-es/strings.xml")["action_allow"])
        assertEquals("Review request", strings("src/main/res/values/strings.xml")["action_review"])
        assertEquals("Revisar el pedido", strings("src/main/res/values-es/strings.xml")["action_review"])
    }

    @Test fun aRequestThatArrivedCutNeverOffersAllow() {
        // A command the computer cut cannot be shown whole, so it is never allowed from the phone. The shade asks the same question
        // as the sheet, the model and the island: one rule (ApprovalSheetPlan.canAllowHere), read by the notification's plan too.
        val cut = ApprovalRequest("agent_claude", "fp-1", "Bash", "x".repeat(500), 0L, truncated = true)
        assertEquals(
            "Deny and its review stay: the request is still the user's to refuse or to read",
            listOf(NotificationPlan.Action.DENY, NotificationPlan.Action.REVIEW),
            NotificationPlan.approvalActions(cut),
        )
        assertTrue(
            "the notification's plan leaves Allow out of a cut request",
            File("src/main/kotlin/com/kenai/android/core/NotificationPlan.kt").readText()
                .substringAfter("fun approvalActions(").substringBefore("fun ").contains("ApprovalSheetPlan.canAllowHere(r)"),
        )
        assertTrue(
            "and that is the same «the whole command is here» as the sheet's",
            File("src/main/kotlin/com/kenai/android/core/ApprovalSheetPlan.kt").readText().contains("fun canAllowHere(r: ApprovalRequest): Boolean = !r.truncated"),
        )
    }

    @Test fun noButtonInTheShadeEverSendsAnAllow() {
        // The shade can never send a decision. Deny is the only button that acts, it comes from the non-exported receiver and it can
        // only refuse; Allow and «Responder» open the app, where the prompt is what decides (KN-04 T4, S10: an intent never carries
        // an allow).
        assertFalse("no allow decision anywhere in the notifications", n.contains("model.decide(") && !n.contains("model.decide(fp, allow = false)"))
        assertEquals("one decision, and it is a refusal", 1, Regex("""\.decide\(""").findAll(n).count())
        assertTrue("the receiver handles Deny and nothing else", n.contains("if (intent.action != Notifications.ACTION_DENY) return"))
        assertFalse("no allow of its own on the wire", n.contains("ACTION_ALLOW"))
        assertTrue(n.contains("model.decide(fp, allow = false)") && !n.contains("allow = true"))
        // The gate really runs on that path, and only its success sends the allow.
        val main = File("src/main/kotlin/com/kenai/android/MainActivity.kt").readText()
        assertEquals("one place sends an allow", 1, Regex("""decide\(r\.fingerprint, allow = true\)""").findAll(main).count())
        val approve = main.substringAfter("private fun approve(").substringBefore("private companion object")
        assertTrue(
            "after the prompt, and only from its success",
            approve.substringAfter("onSuccess =").contains("model.decide(r.fingerprint, allow = true)"),
        )
        assertTrue(approve.indexOf("BiometricGate.confirm(") < approve.indexOf("model.decide(r.fingerprint, allow = true)"))
        // And the non-exported entry point only hands the tap over: it decides nothing itself.
        val entry = File("src/main/kotlin/com/kenai/android/app/LaunchActivity.kt").readText()
        assertTrue(entry.contains("model.launch.request("))
        assertFalse(entry.contains("decide("))
    }

    @Test fun aQuestionOffersResponderAndItOnlyOpensThatQuestion() {
        // Pure: «Responder» for a question, and no button at all for anything else.
        assertEquals(listOf(NotificationPlan.Action.RESPOND), NotificationPlan.statusActions(BotState.QUESTION))
        for (s in BotState.values()) assertTrue("$s", s == BotState.QUESTION || NotificationPlan.statusActions(s).isEmpty())
        val status = n.substringAfter("fun showStatus(").substringBefore("fun cancelStatus(")
        // The notice only asks for the plan and draws nothing that is not in it.
        assertTrue(status.contains("NotificationPlan.statusActions(state)"))
        assertTrue("through the app, never the shade", status.contains("openQuestion(pillId") && n.contains("LaunchActivity::class.java"))
        assertTrue(status.contains("R.string.home_answer"))
        // Only the pill id travels: it opens that question, where the answer still goes behind the screen lock. No pick, no answer.
        assertTrue(n.contains("putExtra(LaunchActivity.EXTRA_QUESTION, pillId)"))
        for (bad in listOf("picks", "EXTRA_ANSWER", "optionLabel", "questions[")) assertFalse("$bad never travels to a notification", n.contains(bad))
        // Nothing else of the notice changes: the same silent channel and the same id.
        assertTrue(status.contains("nm.notify(STATUS_TAG, statusId(pillId)"))
        assertEquals("Responder", strings("src/main/res/values-es/strings.xml")["home_answer"])
    }

    @Test fun unlinkedMeansNoApprovalAndNoQuestionNotification() {
        // KN-04 criterion 4 (S5): with the phone unlinked nothing reaches it. A request's or a notice's notification is only posted
        // where the link delivers frames, and unlinking takes back whatever was already in the shade.
        val model = File("src/main/kotlin/com/kenai/android/app/AppModel.kt").readText()
        assertEquals("one place posts a request's notification", 1, Regex("""notifier\.showApproval\(""").findAll(model).count())
        assertEquals("one place posts a status notice", 1, Regex("""notifier\.showStatus\(""").findAll(model).count())
        val approvalCall = model.substringAfter("override fun onApproval(").substringBefore("override fun onQuestion(")
        val sessions = model.substringAfter("override fun onSessions(").substringBefore("override fun onApproval(")
        assertTrue("from the link's own frames", approvalCall.contains("notifier.showApproval(") && sessions.contains("notifier.showStatus("))
        // A link exists only for a stored pairing: with no pairing there is no socket and so no frame to notify.
        assertTrue("resume needs a stored pairing", model.substringAfter("fun resume()").substringBefore("var pairRequest").contains("val p = store.loadPairing() ?: return"))
        assertTrue("and a link is only built for one", model.substringAfter("private fun connect(").substringBefore("private fun useAddress(").contains("link = newLink(p)"))
        // Unlinking, or losing the link, cancels what was posted rather than leaving a stale request in the shade.
        val stop = model.substringAfter("private fun stopLink()").substringBefore("fun decide(")
        assertTrue(stop.contains("dropApprovals()"))
        assertTrue(stop.contains("notifier.cancelAllStatus()"))
        assertTrue(model.substringAfter("fun unpair()").substringBefore("fun stopDemo(").contains("stopLink()"))
        assertTrue(
            "every request's notification goes",
            model.substringAfter("private fun dropApprovals()").substringBefore("private fun removeApproval").contains("notifier.cancelApproval(it.fingerprint)"),
        )
        // The ongoing one is only posted while paired (and the link service is stopped with the link).
        assertTrue(n.contains("if (model.mode != Mode.PAIRED) return"))
        assertTrue(File("src/main/kotlin/com/kenai/android/app/LinkService.kt").readText().contains("Notifications.ONGOING_ID"))
    }

    @Test fun theCountdownIsLiveAndTheNotificationGoesWhenTheRequestDoes() {
        assertTrue(approval.contains("setUsesChronometer(true)"))
        assertTrue(approval.contains("setChronometerCountDown(true)"))
        assertTrue(approval.contains("setWhen(System.currentTimeMillis() + com.kenai.android.link.Protocol.APPROVAL_TTL_MS)"))
        assertTrue(approval.contains("setTimeoutAfter(com.kenai.android.link.Protocol.APPROVAL_TTL_MS)"))
    }

    // ── One request, one notification ───────────────────────────────────────────────────────────────

    @Test fun aWaitingRequestNeverCancelsTheForegroundNotification() {
        // Stepping aside did not work on API 35: the link service holds ONGOING_ID with startForeground, so the cancel is
        // ignored and the record stays (kenai-0.1 L31). The quiet one stays posted with the link's own state, and the
        // request keeps its own loud notification; nothing is taken out of the shade.
        assertFalse("nothing cancels the id the link service holds", n.contains("nm.cancel(ONGOING_ID)"))
        val body = n.substringAfter("fun updateOngoing(").substringBefore("fun showApproval(")
        assertTrue("it is always posted", body.contains("nm.notify(ONGOING_ID"))
        assertTrue("with the link's own state, never the request's", body.contains("quietGlance(model)"))
        assertFalse("no branch leaves the request out of the shade", body.contains("approvals.isNotEmpty()"))
        // The foreground service notification must stay a foreground service notification: ongoing and not dismissible.
        val ongoing = n.substringAfter("private fun ongoing(g: Glance?)").substringBefore("fun updateOngoing(")
        assertTrue(ongoing.contains(".setOngoing(true)"))
        assertTrue(File("src/main/kotlin/com/kenai/android/app/LinkService.kt").readText().contains("startForeground(Notifications.ONGOING_ID"))
    }

    @Test fun eachKindOfNotificationHasItsOwnExplicitGroup() {
        // Android autogroups an app's notifications that have no group of their own (GroupHelper: only non-app-grouped
        // ones are bundled), so one approval next to the ongoing one read as one group of «2» (kenai-0.1 L29/L31). An
        // explicit group takes a notification out of autogrouping, and a group with no summary is drawn as a plain
        // entry, not as a count (ShadeListBuilder.pruneIncompleteGroups).
        val ongoing = n.substringAfter("private fun ongoing(g: Glance?)").substringBefore("fun updateOngoing(")
        val status = n.substringAfter("fun showStatus(").substringBefore("fun cancelStatus(")
        assertTrue("the request", approval.contains("setGroup(GROUP_APPROVALS)"))
        assertTrue("the link", ongoing.contains("setGroup(GROUP_LINK)"))
        assertTrue("a status notice", status.contains("setGroup(GROUP_STATUS)"))
        val groups = Regex("""const val (GROUP_\w+) = "([^"]+)"""").findAll(n).associate { it.groupValues[1] to it.groupValues[2] }
        assertEquals("one group per kind", 3, groups.size)
        assertEquals("never the same group for two kinds", 3, groups.values.toSet().size)
        assertFalse("no summary is posted: it would bring the group count back", n.contains("setGroupSummary(true)"))
        // A request is the loud one: its own alert is never handed to a summary that does not exist.
        assertTrue(approval.contains("setGroupAlertBehavior(Notification.GROUP_ALERT_ALL)"))
        assertTrue(ongoing.contains("setGroupAlertBehavior(Notification.GROUP_ALERT_ALL)"))
    }

    @Test fun theQuietNotificationNeverAnnouncesARequestThatHasItsOwn() {
        // The same rule when the link service starts, which has to post one: the summary of a waiting request is the request's
        // own notification, never a second «Te está esperando» line.
        val call = n.substringAfter("fun ongoing(model: AppModel?)").substringBefore("private fun ongoing(g: Glance?)")
        assertTrue("the quiet one asks for its own content", call.contains("quietGlance"))
        val body = n.substringAfter("private fun quietGlance(").substringBefore("fun updateOngoing(")
        assertTrue(body.contains("model.approvals.isEmpty()"))
        assertTrue("the link's own state, from the same builder", body.contains("GlanceBuilder.build("))
        assertTrue("no request is counted in it", body.contains("approvals = 0"))
    }

    @Test fun itStaysPrivateOnTheLockScreenAndLoud() {
        assertTrue(approval.contains("VISIBILITY_PRIVATE"))
        assertTrue(approval.contains("setPublicVersion(publicVersion)"))
        val pub = approval.substringAfter("val publicVersion").substringBefore(".build()")
        assertTrue(pub.contains("R.string.approval_title") && pub.contains("R.string.notif_unlock"))
        assertTrue(approval.contains("if (alert == OverlayPolicy.ApprovalAlert.QUIET) CH_APPROVAL_QUIET else CH_APPROVAL"))
        assertEquals("Necesita tu aprobación", strings("src/main/res/values-es/strings.xml")["approval_title"])
        assertEquals("Desbloqueá para ver el pedido", strings("src/main/res/values-es/strings.xml")["notif_unlock"])
    }
}
