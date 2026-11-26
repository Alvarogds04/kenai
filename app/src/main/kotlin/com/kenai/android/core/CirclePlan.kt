package com.kenai.android.core

import com.kenai.android.link.ApprovalRequest
import kotlin.math.PI
import kotlin.math.sin

/**
 * Koda's floating circle and its panel (KN-12). Pure: it decides what the circle shows and what each panel row offers.
 * Only Deny resolves inside the overlay; Allow and answers never complete here, they hand off to the app, behind its own lock (KN-04 S10).
 */
object CirclePlan {
    /** [count]: pending requests; [pulses]: the discreet pulse runs (never with nothing pending, never with reduced motion). */
    data class Circle(val count: Int, val pulses: Boolean)

    fun circle(items: List<PendingPlan.Item>, reducedMotion: Boolean): Circle =
        Circle(items.size, pulses = items.isNotEmpty() && !reducedMotion)

    const val PULSE_PERIOD_MS = 2400L
    private const val PULSE_DEPTH = 0.06

    /** Scale of the circle at [elapsedMs]: starts at 1.0, swells at most 6 % and returns. */
    fun pulseScale(elapsedMs: Long): Double {
        val phase = (elapsedMs % PULSE_PERIOD_MS).toDouble() / PULSE_PERIOD_MS
        return 1.0 + PULSE_DEPTH * (1 - kotlin.math.cos(2 * PI * phase)) / 2
    }

    fun badge(count: Int): String = if (count > 99) "99+" else count.toString()

    /** What a row's main button does when it is not Deny: always through the app. */
    sealed interface Handoff {
        data class Allow(val fingerprint: String) : Handoff
        data class Question(val pillId: String) : Handoff
        data object App : Handoff
    }

    data class Row(
        val kind: PendingPlan.Kind, val fingerprint: String, val pillId: String, val agent: String, val leftMs: Long,
        val canDeny: Boolean, val handoff: Handoff,
    )

    fun panel(items: List<PendingPlan.Item>, approvals: List<ApprovalRequest>): List<Row> = items.map { i ->
        when (i.kind) {
            PendingPlan.Kind.QUESTION -> Row(i.kind, i.fingerprint, i.pillId, i.agent, i.leftMs, canDeny = false, handoff = Handoff.Question(i.pillId))
            PendingPlan.Kind.APPROVAL -> {
                val held = approvals.firstOrNull { it.fingerprint == i.fingerprint }
                val handoff = if (held != null && ApprovalSheetPlan.canAllowHere(held)) Handoff.Allow(i.fingerprint) else Handoff.App
                Row(i.kind, i.fingerprint, i.pillId, i.agent, i.leftMs, canDeny = held != null, handoff = handoff)
            }
        }
    }
}
