package com.kenai.android.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.util.Log
import com.kenai.android.MainActivity
import com.kenai.android.R
import com.kenai.android.core.Glance
import com.kenai.android.core.GlanceBuilder
import com.kenai.android.core.GlanceTone
import com.kenai.android.core.NotificationPlan
import com.kenai.android.core.OverlayPolicy
import com.kenai.android.core.SafeText
import com.kenai.android.core.ToolLabels
import com.kenai.android.core.Words
import com.kenai.android.link.ApprovalRequest
import com.kenai.android.character.BotState

/**
 * Notifications: one quiet ongoing notification for the link, a loud one per approval and a quiet one per notice.
 *
 * What a notification may do is decided by [NotificationPlan] and nowhere else. A request offers Deny — the one button that decides
 * anything, and it only refuses — «Review request», which opens the app on it, and Allow, which does not allow: it opens the app on
 * that request's biometric / screen-lock prompt, the same door as the pill's (kenai-0.2 S4: Allow always asks, and an intent never
 * carries an allow). A question offers «Responder», which opens the app on that question, where the answer goes behind the same
 * prompt. The command is never in a notification, no option label is ever a button, and Kenai launches nothing (KN-04 T4, S9/S10).
 *
 * Each kind carries its own group key ([GROUP_APPROVALS], [GROUP_LINK], [GROUP_STATUS]) and none of them posts a summary:
 * Android only autogroups the notifications of an app that have no group of their own (kenai-0.1 T20), so an approval next
 * to the ongoing one no longer reads as one group of «2», and a group with no summary is drawn as a plain entry.
 */
class Notifications(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    init {
        // Channel names in the phone's language ([Words]); creating a channel again only renames it, so a new language shows on the
        // next start.
        val w = Words.current()
        nm.deleteNotificationChannel("approvals")
        nm.deleteNotificationChannel("approvals_v2")
        nm.createNotificationChannel(
            NotificationChannel(CH_LINK, w.channelLink, NotificationManager.IMPORTANCE_LOW).apply {
                description = w.channelLinkAbout
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_UPDATES, w.channelUpdates, NotificationManager.IMPORTANCE_LOW).apply {
                description = w.channelUpdatesAbout
                setShowBadge(false)
            },
        )
        // The same request while the pill is on screen: in the shade only, no heads-up and no sound,
        // so the pill and the notification never overlap at the top of the screen.
        nm.createNotificationChannel(
            NotificationChannel(CH_APPROVAL_QUIET, w.channelApprovalsQuiet, NotificationManager.IMPORTANCE_LOW).apply {
                description = w.channelApprovalsQuietAbout
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_APPROVAL, w.channelApprovals, NotificationManager.IMPORTANCE_HIGH).apply {
                description = w.channelApprovalsAbout
                // A short buzz, so a request is felt even when the notification volume is 0 (a channel cannot change
                // later, which is why this one has a new id).
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 150, 500)
                // No bundled sound yet (HU-049 T3): the channel keeps the system's default notification sound.
            },
        )
    }

    private fun open(fp: String?, allow: Boolean, code: Int): PendingIntent {
        // A request goes through LaunchActivity (not exported); a plain open needs no extras and goes straight to the app.
        val i = Intent(context, if (fp != null) LaunchActivity::class.java else MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { if (fp != null) { putExtra(EXTRA_FP, fp); putExtra(EXTRA_ALLOW, allow) } }
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * The quiet notification that keeps the link alive: the headline of the link's [Glance] as its title, the detail under it and,
     * when expanded, one line per agent ("Claude Code: Working"). Only states, never a command or a path. On a locked
     * screen it shows the plain "Connected" line, like the other notifications of the app.
     */
    fun ongoing(model: AppModel?): Notification = ongoing(model?.let { quietGlance(it) })

    private fun ongoing(g: Glance?): Notification {
        val title = g?.headline ?: context.getString(R.string.status_connecting)
        val detail = g?.detail.orEmpty()
        val b = Notification.Builder(context, CH_LINK)
            .setSmallIcon(R.drawable.ic_stat_kenai)
            .setContentTitle(title)
            .setContentText(detail)
            .setColor(g?.tone?.argb ?: GlanceTone.CALM.argb)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Its own group: autogrouping leaves it alone whatever else the app posts (T20). No summary, so it keeps its own
            // alert instead of handing it to one that never exists.
            .setGroup(GROUP_LINK)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_ALL)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, CH_LINK).setSmallIcon(R.drawable.ic_stat_kenai)
                    .setContentTitle(context.getString(R.string.app_name))
                    .setContentText(if (g?.connected == true) context.getString(R.string.status_connected) else context.getString(R.string.status_not_connected))
                    .build(),
            )
            .setContentIntent(open(null, false, 0))
        if (g != null && g.lines.isNotEmpty()) {
            b.setStyle(Notification.BigTextStyle().bigText(g.lines.joinToString("\n") { "${it.agent}: ${it.text}" }).setSummaryText(detail))
        }
        return b.build()
    }

    fun updateOngoing(model: AppModel, g: Glance = model.glance()) {
        if (model.mode != Mode.PAIRED) return
        // One request, one notification (kenai-0.1 T20). The quiet one never steps aside: the link service holds this id with
        // startForeground, so on API 35 a cancel here is ignored and the record stays next to the approval (L31). It is always
        // posted, and while something waits it carries the link's own state (quietGlance) instead of repeating the request,
        // which has its own loud notification in its own group: nothing to bundle, no «2».
        runCatching { nm.notify(ONGOING_ID, ongoing(if (model.approvals.isEmpty()) g else quietGlance(model))) }
    }

    /**
     * What the quiet ongoing notification says: the link at a glance. A request waiting for the user already has its own loud
     * notification with its own actions, so this one never repeats it — a second «Te está esperando» line — and is built with
     * no requests at all: the link's own state.
     */
    private fun quietGlance(model: AppModel): Glance =
        if (model.approvals.isEmpty()) {
            model.glance()
        } else {
            GlanceBuilder.build(
                paired = true,
                link = model.linkState,
                desktop = model.desktopName,
                sessions = model.sessions,
                approvals = 0,
                nameOf = model::agentName,
            )
        }

    fun showApproval(r: ApprovalRequest, agentName: String, alert: OverlayPolicy.ApprovalAlert = OverlayPolicy.ApprovalAlert.HEADS_UP) {
        val channel = if (alert == OverlayPolicy.ApprovalAlert.QUIET) CH_APPROVAL_QUIET else CH_APPROVAL
        Log.d("KenaiOverlay", "approval notification: $alert")
        val id = idFor(r.fingerprint)
        val deny = PendingIntent.getBroadcast(
            context, id, Intent(context, ActionReceiver::class.java).setAction(ACTION_DENY).putExtra(EXTRA_FP, r.fingerprint),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val publicVersion = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_kenai).setContentTitle(context.getString(R.string.approval_title)).setContentText(context.getString(R.string.notif_unlock)).build()
        // «Claude Code necesita tu aprobación» / «Quiere ejecutar un comando» (kenai-0.1 S9, screen 5): the kind of action, never the
        // command. The time left is only in the header, counted down live, so no fixed text can disagree with it.
        val notice = SafeText.approvalNotice(agentName, r.tool)
        val b = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_kenai)
            .setContentTitle(notice.title)
            .setContentText(notice.line)
            // The chronometer sits in the header («· 1:59»): a live countdown to the moment the request stops being offered (posted on arrival).
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(System.currentTimeMillis() + com.kenai.android.link.Protocol.APPROVAL_TTL_MS)
            .setCategory(Notification.CATEGORY_ALARM)
            // Its own group, so Android's autogrouping never bundles it with the ongoing notification (T20). No summary is
            // posted, so this one keeps its own heads-up: its alert is never handed to a child or a summary.
            .setGroup(GROUP_APPROVALS)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_ALL)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setTimeoutAfter(com.kenai.android.link.Protocol.APPROVAL_TTL_MS)
            .setContentIntent(open(r.fingerprint, false, id))
        // One button per action the plan allows, in its order. Deny sends its refusal straight from the shade; the other two only
        // open the app, where the prompt — not the shade — is what allows (KN-04 T4, S10). Each has its own request code: Android
        // keeps two PendingIntents apart by code plus intent, and these must not become the same one.
        for (a in NotificationPlan.approvalActions(r)) {
            val button = when (a) {
                NotificationPlan.Action.DENY ->
                    Notification.Action.Builder(null, context.getString(R.string.action_deny), deny).build()
                // «Revisar el pedido»: the app on that request (Home's hero, or its sheet), like a tap on the notification.
                NotificationPlan.Action.REVIEW ->
                    Notification.Action.Builder(null, context.getString(R.string.action_review), open(r.fingerprint, false, id + 2)).build()
                // Allow: never decided here. It opens the app on this request's own fingerprint / screen-lock prompt.
                NotificationPlan.Action.ALLOW ->
                    Notification.Action.Builder(null, context.getString(R.string.action_allow), open(r.fingerprint, true, id + 1)).build()
                // A question's button is never on a request's notification: the plan gives it to showStatus.
                NotificationPlan.Action.RESPOND -> continue
            }
            b.addAction(button)
        }
        val n = b.build()
        runCatching { nm.notify(id, n) } // POST_NOTIFICATIONS may be denied: the in-app card still works
        if (alert == OverlayPolicy.ApprovalAlert.HEADS_UP) wakeScreen()
    }

    /**
     * A request on a sleeping phone lights the screen for a few seconds, so the lock screen shows the notification
     * ("Unlock to see the request"). The phone stays locked; nothing about the command is shown.
     */
    @Suppress("DEPRECATION")
    private fun wakeScreen() = runCatching {
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        if (pm.isInteractive) return@runCatching
        pm.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "kenai:approval")
            .acquire(WAKE_MS)
    }

    fun cancelApproval(fp: String) = nm.cancel(idFor(fp))

    /** An agent finished, failed, hit a limit or asks something. Quiet: Kenai's own sound and the pill are the alerts. */
    fun showStatus(pillId: String, agentName: String, state: BotState, statusText: String) {
        val what = context.getString(
            when (state) {
                BotState.ERROR -> R.string.notif_error
                BotState.QUESTION -> R.string.notif_question
                BotState.RATELIMIT -> R.string.notif_ratelimit
                else -> R.string.notif_finished
            },
        )
        val b = Notification.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_kenai)
            .setContentTitle(agentName)
            .setContentText(what)
            .setSubText(ToolLabels.label(statusText).takeIf { it.isNotBlank() })
            .setCategory(Notification.CATEGORY_STATUS)
            // A third group: a status notice is never bundled with a request or with the link (T20).
            .setGroup(GROUP_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, CH_UPDATES).setSmallIcon(R.drawable.ic_stat_kenai)
                    .setContentTitle(context.getString(R.string.app_name)).setContentText(what).build(),
            )
            .setAutoCancel(true)
            .setContentIntent(open(null, false, 0))
        // «Responder» when the agent asks something (KN-04 T4): it opens that question in the app, behind the same screen lock, and
        // carries nothing but its pill id — never a pick, never an answer. Nothing answers from the shade.
        for (a in NotificationPlan.statusActions(state)) {
            if (a != NotificationPlan.Action.RESPOND) continue
            b.addAction(
                Notification.Action.Builder(
                    null, context.getString(R.string.home_answer), openQuestion(pillId, statusId(pillId)),
                ).build(),
            )
        }
        val n = b.build()
        runCatching { nm.notify(STATUS_TAG, statusId(pillId), n) }
    }

    fun cancelStatus(pillId: String) = runCatching { nm.cancel(STATUS_TAG, statusId(pillId)) }

    /** Kenai is on screen: the notices are redundant. */
    fun cancelAllStatus() {
        runCatching { nm.activeNotifications.filter { it.tag == STATUS_TAG }.forEach { nm.cancel(it.tag, it.id) } }
    }

    private fun statusId(pillId: String) = pillId.hashCode() and 0x3fffffff

    /** Opens the app on the biometric prompt for this request (the same as tapping Allow in the notification). */
    fun openAllow(fp: String) {
        Log.d("KenaiLaunch", "open the app on Allow (tap on the pill)")
        runCatching { open(fp, true, idFor(fp) + 1).send() }
    }

    /**
     * «Responder» on a question's notice: the app on that question, through the non-exported entry point, with the pill id as its
     * only extra. The answer itself still takes the app's own screen-lock prompt; nothing is answered from the shade (KN-04 T4).
     */
    private fun openQuestion(pillId: String, code: Int): PendingIntent =
        PendingIntent.getActivity(
            context, code,
            Intent(context, LaunchActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(LaunchActivity.EXTRA_QUESTION, pillId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun openApp() {
        Log.d("KenaiLaunch", "open the app (tap on the pill)")
        runCatching { open(null, false, 0).send() }
    }

    companion object {
        const val CH_LINK = "link"
        private const val WAKE_MS = 6_000L
        const val CH_APPROVAL = "approvals_v3"
        const val CH_APPROVAL_QUIET = "approvals_quiet"
        const val CH_UPDATES = "updates"
        private const val STATUS_TAG = "status"
        const val ONGOING_ID = 1
        /** One explicit group per kind of notification: Android only autogroups the ones that have no group (T20). */
        const val GROUP_APPROVALS = "kenai.approvals"
        const val GROUP_LINK = "kenai.link"
        const val GROUP_STATUS = "kenai.status"
        const val ACTION_DENY = "com.kenai.android.DENY"
        const val EXTRA_FP = "fingerprint"
        const val EXTRA_ALLOW = "allow"
        /** Even ids for the request, +1 for its Allow action and +2 for its «Review request»: never collides with ONGOING_ID. */
        fun idFor(fp: String) = (fp.hashCode() and 0x3fffffff) * 2 + 2
    }
}

/** Handles the Deny button. Denying needs no biometric check: it can only make things safer. Nothing in a notification sends an allow. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Notifications.ACTION_DENY) return
        val fp = intent.getStringExtra(Notifications.EXTRA_FP) ?: return
        (context.applicationContext as KenaiApp).model.decide(fp, allow = false)
    }
}
