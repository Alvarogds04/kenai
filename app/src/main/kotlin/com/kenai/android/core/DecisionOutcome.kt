package com.kenai.android.core

import com.kenai.android.character.BotState
import com.kenai.android.character.KodaRole
import com.kenai.android.link.ApprovalRequest
import com.kenai.android.link.Protocol
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** What became of a request the phone was showing (kenai-0.1 S9, screen 3 «Resultado»). */
enum class OutcomeKind { SENDING, ALLOWED, DENIED, EXPIRED, FAILED }

/**
 * The one result Inicio shows. Names and the tool only: never the command.
 *
 * [allow] is what the phone sent (true allow, false deny); null when the phone sent nothing that could have arrived (refused as no
 * longer pending, answered on the computer, or the send failed before it expired).
 */
data class Outcome(
    val fingerprint: String,
    val kind: OutcomeKind,
    val allow: Boolean?,
    val pillId: String,
    val tool: String,
    /** When this outcome began, by the phone's elapsed clock: Home shows it at most [DecisionOutcomes.SHOW_MS] from here. */
    val sinceMs: Long,
    /** The wall clock when it happened, for «14:41». */
    val atWallMs: Long,
    /** The wall clock when the request stops being offered (arrival + 120 s), for «Caducó a las 14:45»; null when not known. */
    val expiresAtWallMs: Long?,
    /** How many taps the confirmation buzzed with (0: it did not, the switch is off). */
    val buzzes: Int = 0,
)

/**
 * What became of the decision the phone sent, derived from what the phone can know (kenai-0.1 S9, T14):
 *
 * - sending: the phone decided and the computer has not said the request is resolved yet;
 * - allowed / denied: the computer resolved a request this phone decided;
 * - expired: the computer resolved the request on screen that the phone did not decide (answered there), the phone's decision was
 *   refused as no longer pending, or the request ran out of its 120 s;
 * - failed: the decision could not be written, or the link went down while sending (the request may be offered again).
 *
 * A new request replaces the result, the user can dismiss it, and it never shows after [SHOW_MS]. Pure, so it is unit-tested.
 */
class DecisionOutcomes {
    private class Decided(val allow: Boolean, val pillId: String, val tool: String)

    /** Fingerprints this phone decided whose resolution has not come yet; bounded. */
    private val decided = LinkedHashMap<String, Decided>()

    var current: Outcome? = null
        private set

    fun decided(r: ApprovalRequest, allow: Boolean, nowMs: Long, wallMs: Long, expiresAtWallMs: Long?): Outcome {
        decided.remove(r.fingerprint)
        decided[r.fingerprint] = Decided(allow, r.pillId, r.tool)
        while (decided.size > MAX) decided.remove(decided.keys.first())
        return set(Outcome(r.fingerprint, OutcomeKind.SENDING, allow, r.pillId, r.tool, nowMs, wallMs, expiresAtWallMs))
    }

    /** The phone's decision was refused: the request was no longer pending. Nothing was sent. */
    fun refused(r: ApprovalRequest, nowMs: Long, wallMs: Long, expiresAtWallMs: Long?): Outcome {
        decided.remove(r.fingerprint)
        return set(Outcome(r.fingerprint, OutcomeKind.EXPIRED, null, r.pillId, r.tool, nowMs, wallMs, expiresAtWallMs))
    }

    /**
     * The computer resolved [fingerprint]. For a request this phone decided: allowed or denied, returned even when another result
     * holds Home (the buzz still says it), and shown only when its own result is the one on screen. For any other: expired, only
     * when it was the request on Home ([shown]) and no result holds Home. Null when there is nothing to say.
     */
    fun resolved(fingerprint: String, shown: ApprovalRequest?, nowMs: Long, wallMs: Long, expiresAtWallMs: Long?): Outcome? {
        val mine = decided.remove(fingerprint)
        if (mine != null) {
            val c = current
            val o = Outcome(
                fingerprint, if (mine.allow) OutcomeKind.ALLOWED else OutcomeKind.DENIED, mine.allow, mine.pillId, mine.tool,
                nowMs, wallMs, c?.takeIf { it.fingerprint == fingerprint }?.expiresAtWallMs ?: expiresAtWallMs,
            )
            if (c != null && c.fingerprint == fingerprint) current = o
            return o
        }
        if (current != null || shown == null || shown.fingerprint != fingerprint) return null
        return set(Outcome(fingerprint, OutcomeKind.EXPIRED, null, shown.pillId, shown.tool, nowMs, wallMs, expiresAtWallMs))
    }

    /** The phone's 120 s for [fingerprint] ran out. */
    fun expired(fingerprint: String, shown: ApprovalRequest?, nowMs: Long, wallMs: Long, expiresAtWallMs: Long?): Outcome? {
        decided.remove(fingerprint)
        val c = current
        if (c != null) {
            if (c.fingerprint != fingerprint || (c.kind != OutcomeKind.SENDING && c.kind != OutcomeKind.FAILED)) return null
            // Sent and never confirmed: it may have arrived. Failed: nothing did.
            val allow = if (c.kind == OutcomeKind.SENDING) c.allow else null
            return set(c.copy(kind = OutcomeKind.EXPIRED, allow = allow, sinceMs = nowMs, atWallMs = wallMs, expiresAtWallMs = expiresAtWallMs ?: c.expiresAtWallMs))
        }
        if (shown == null || shown.fingerprint != fingerprint) return null
        return set(Outcome(fingerprint, OutcomeKind.EXPIRED, null, shown.pillId, shown.tool, nowMs, wallMs, expiresAtWallMs))
    }

    /** The decision for [fingerprint] could not be written. */
    fun sendFailed(fingerprint: String): Outcome? {
        val c = current ?: return null
        if (c.fingerprint != fingerprint || c.kind != OutcomeKind.SENDING) return null
        return set(c.copy(kind = OutcomeKind.FAILED))
    }

    /** The link went down: a decision still sending did not get its confirmation. A confirmed result stays true. */
    fun linkLost(): Outcome? {
        val c = current ?: return null
        if (c.kind != OutcomeKind.SENDING) return null
        return set(c.copy(kind = OutcomeKind.FAILED))
    }

    /** A request arrived (new, or offered again after a reconnection): it takes Home, and an earlier decision of it did not land. */
    fun arrived(fingerprint: String) {
        decided.remove(fingerprint)
        current = null
    }

    /** The confirmation of [fingerprint] buzzed with [taps] taps. */
    fun buzzed(fingerprint: String, taps: Int) {
        current?.takeIf { it.fingerprint == fingerprint }?.let { current = it.copy(buzzes = taps) }
    }

    fun dismiss() { current = null }

    /** The result to show at [nowMs], or null once it is older than [SHOW_MS]. */
    fun visible(nowMs: Long): Outcome? = current?.takeIf { nowMs - it.sinceMs in 0..SHOW_MS }

    fun remembered(): Int = decided.size

    private fun set(o: Outcome): Outcome { current = o; return o }

    companion object {
        /** A result shows at most two minutes. */
        const val SHOW_MS = Protocol.APPROVAL_TTL_MS
        const val MAX = 32

        /** When a request that arrived at [arrivedElapsedMs] (elapsed clock) stops being offered, on the wall clock. */
        fun expiresAtWall(arrivedElapsedMs: Long?, nowElapsedMs: Long, nowWallMs: Long, ttlMs: Long = Protocol.APPROVAL_TTL_MS): Long? =
            arrivedElapsedMs?.let { nowWallMs - (nowElapsedMs - it) + ttlMs }

        /** «14:41» in [zone]. */
        fun clock(wallMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
            val t = Instant.ofEpochMilli(wallMs).atZone(zone)
            return String.format(Locale.ROOT, "%02d:%02d", t.hour, t.minute)
        }

        /** Koda's pose: waiting while sending, done when allowed, resting when denied or expired, the error pose when it failed. */
        fun pose(kind: OutcomeKind): BotState = when (kind) {
            OutcomeKind.SENDING -> BotState.APPROVAL
            OutcomeKind.ALLOWED -> BotState.FINISHED
            OutcomeKind.DENIED, OutcomeKind.EXPIRED -> BotState.IDLE
            OutcomeKind.FAILED -> BotState.ERROR
        }

        /** The role colour (design/system.md): live while sending, go when allowed, faint when denied or expired, red when it failed. */
        fun role(kind: OutcomeKind): Long = when (kind) {
            OutcomeKind.SENDING -> KodaRole.LIVE
            OutcomeKind.ALLOWED -> KodaRole.GO
            OutcomeKind.DENIED, OutcomeKind.EXPIRED -> KodaRole.FAINT
            OutcomeKind.FAILED -> KodaRole.RED
        }
    }
}
