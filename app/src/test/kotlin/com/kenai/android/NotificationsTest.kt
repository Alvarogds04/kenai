package com.kenai.android

import com.kenai.android.core.SafeText
import com.kenai.android.core.Words
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The approval notification of «Decidí en un toque» (kenai-0.1 S9, screen 5): «<Agent> necesita tu aprobación», «Quiere ejecutar un
 * comando», a live countdown in the header, and the actions «Denegar» and «Revisar el pedido» (no Allow: deciding yes takes the app
 * and the fingerprint). Private on the lock screen, never a command or a file. Android classes only compile in CI: pure text plus
 * source guards.
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

    @Test fun theActionsAreDenyAndReviewTheRequestNeverAllow() {
        assertTrue(approval.contains("R.string.action_deny"))
        assertTrue(approval.contains("R.string.action_review"))
        assertFalse("no Allow from the notification", approval.contains("R.string.action_allow"))
        // Review opens the app on that request, the same way as a tap on the notification: never with allow = true.
        assertTrue(approval.contains("open(r.fingerprint, false, id + 1)"))
        assertFalse(approval.contains("open(r.fingerprint, true"))
        assertEquals("Review request", strings("src/main/res/values/strings.xml")["action_review"])
        assertEquals("Revisar el pedido", strings("src/main/res/values-es/strings.xml")["action_review"])
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
