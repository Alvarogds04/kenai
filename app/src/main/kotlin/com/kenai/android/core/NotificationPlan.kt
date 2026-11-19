package com.kenai.android.core

import com.kenai.android.character.BotState
import com.kenai.android.link.ApprovalRequest

/**
 * What a notification offers, as data (KN-04 T4, S3/S9/S10). Pure, so each rule is a unit test and the shade can be built from
 * nothing else: a button that is not here cannot be posted.
 *
 * One rule runs through all of it: a notification never decides anything that needs the user's consent. Deny is instant, because it
 * can only make things safer; Allow only opens the app on that request's own biometric / screen-lock prompt (kenai-0.2 S4: Allow
 * always asks, and an intent never carries an allow); «Responder» only opens the app on that question, where the answer goes behind
 * the same prompt. And Kenai launches no command, from a notification or from anywhere else (KN-04 S9).
 */
object NotificationPlan {
    /** A button in the shade. Only [DENY] sends a decision by itself, and it is always a refusal. */
    enum class Action { DENY, REVIEW, ALLOW, RESPOND }

    /**
     * A request's buttons, in the order they are drawn: Deny, «Review request», and last — furthest from Deny — Allow, which opens
     * the app's own prompt instead of deciding.
     *
     * Allow is left out when the command arrived cut ([ApprovalSheetPlan.canAllowHere]): the phone cannot show what would run, so it
     * is not allowed from here. The other two stay, because the request is still the user's to refuse or to read.
     */
    fun approvalActions(r: ApprovalRequest): List<Action> =
        if (ApprovalSheetPlan.canAllowHere(r)) listOf(Action.DENY, Action.REVIEW, Action.ALLOW) else listOf(Action.DENY, Action.REVIEW)

    /**
     * A status notice's buttons: «Responder» when the agent asks something, and nothing at all for a finish, a failure or a limit.
     * It opens that question; it never carries a pick and it never answers from the shade.
     */
    fun statusActions(state: BotState): List<Action> = if (state == BotState.QUESTION) listOf(Action.RESPOND) else emptyList()
}
