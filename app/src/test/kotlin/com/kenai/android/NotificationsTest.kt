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
