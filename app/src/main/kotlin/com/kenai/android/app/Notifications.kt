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
import com.kenai.android.core.ApprovalSheetPlan
import com.kenai.android.core.Glance
import com.kenai.android.core.GlanceTone
import com.kenai.android.core.OverlayPolicy
import com.kenai.android.core.SafeText
import com.kenai.android.core.ToolLabels
import com.kenai.android.core.Words
import com.kenai.android.link.ApprovalRequest
import com.kenai.android.character.BotState

/**
 * Notifications: one quiet ongoing notification for the link, and a loud one per approval.
 * Deny acts straight from the notification. Allow always opens the app for the biometric
 * check first, and the command is hidden on the lock screen.
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
     * The quiet notification that keeps the link alive: the headline of [Glance] as its title, the detail under it and,
     * when expanded, one line per agent ("Claude Code: Working"). Only states, never a command or a path. On a locked
     * screen it shows the plain "Connected" line, like the other notifications of the app.
     */
    fun ongoing(model: AppModel?): Notification = ongoing(model?.glance())

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
        if (model.mode == Mode.PAIRED) runCatching { nm.notify(ONGOING_ID, ongoing(g)) }
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
        val n = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_kenai)
            .setContentTitle(agentName)
            .setSubText(context.getString(R.string.approval_title))
            // A label and at most the program's name: the exact command is on the approval sheet, behind a tap.
            .setContentText(SafeText.requestLabel(r.tool, r.command))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setTimeoutAfter(com.kenai.android.link.Protocol.APPROVAL_TTL_MS)
            .setContentIntent(open(r.fingerprint, false, id))
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_deny), deny).build())
            .apply {
                // A command the phone cannot show in full is answered on the computer: no Allow here.
                if (ApprovalSheetPlan.canAllowHere(r)) addAction(Notification.Action.Builder(null, context.getString(R.string.action_allow), open(r.fingerprint, true, id + 1)).build())
            }
            .build()
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
        val n = Notification.Builder(context, CH_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_kenai)
            .setContentTitle(agentName)
            .setContentText(what)
            .setSubText(ToolLabels.label(statusText).takeIf { it.isNotBlank() })
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, CH_UPDATES).setSmallIcon(R.drawable.ic_stat_kenai)
                    .setContentTitle(context.getString(R.string.app_name)).setContentText(what).build(),
            )
            .setAutoCancel(true)
            .setContentIntent(open(null, false, 0))
            .build()
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
        const val ACTION_DENY = "com.kenai.android.DENY"
        const val EXTRA_FP = "fingerprint"
        const val EXTRA_ALLOW = "allow"
        /** Even ids for the request, +1 for its Allow action; never collides with ONGOING_ID. */
        fun idFor(fp: String) = (fp.hashCode() and 0x3fffffff) * 2 + 2
    }
}

/** Handles the Deny button. Denying needs no biometric check: it can only make things safer. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Notifications.ACTION_DENY) return
        val fp = intent.getStringExtra(Notifications.EXTRA_FP) ?: return
        (context.applicationContext as KenaiApp).model.decide(fp, allow = false)
    }
}
