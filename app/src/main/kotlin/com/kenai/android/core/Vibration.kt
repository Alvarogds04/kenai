package com.kenai.android.core

import com.kenai.android.character.BotState

/** What just arrived that needs the user: a permission request or a question. */
enum class Arrival { APPROVAL, QUESTION }

/**
 * The buzz when an approval or a question arrives (kenai-0.1 S2.7a, decided L5 option 3a).
 *
 * - The "Vibrate" switch governs it; it is on by default.
 * - Quiet hours never stop it: requests and questions always notify, like their notices.
 * - No double buzz: a request announced by the heads-up notification already vibrates through its channel,
 *   so the phone adds nothing. A request shown by the island (quiet channel) and a question have no buzz of
 *   their own, so the phone gives one.
 * - One buzz per request or question: [BuzzMemory] tells what was already felt.
 */
object VibratePolicy {
    /** Off, on, off, on in milliseconds: two short taps, felt but not an alarm. */
    val PATTERN: LongArray = longArrayOf(0, 90, 80, 90)

    fun onArrival(kind: Arrival, s: UserSettings, alert: OverlayPolicy.ApprovalAlert?, alreadyBuzzed: Boolean): Boolean {
        if (!s.vibrate || alreadyBuzzed) return false
        if (kind == Arrival.APPROVAL && alert == OverlayPolicy.ApprovalAlert.HEADS_UP) return false
        return true
    }

    /** A session changed into a question; never the first picture after connecting (sessions that waited before). */
    fun questionArrived(previous: BotState?, now: BotState): Boolean =
        previous != null && previous != now && now == BotState.QUESTION
}

/**
 * What was already buzzed for. A request or a question has a fingerprint and buzzes once, even when the
 * computer offers it again after a reconnection. A question can also arrive as a session changing into
 * "question", which carries no fingerprint: that and the question itself come together, in either order,
 * so an agent's question within [PAIR_WINDOW_MS] of the last one counts as the same.
 */
class BuzzMemory {
    private val fingerprints = LinkedHashSet<String>()
    private val questionAt = LinkedHashMap<String, Long>()

    fun alreadyBuzzed(kind: Arrival, pillId: String, fingerprint: String?, nowMs: Long): Boolean {
        if (fingerprint != null && fingerprint in fingerprints) return true
        if (kind != Arrival.QUESTION) return false
        val last = questionAt[pillId] ?: return false
        return nowMs - last in 0..PAIR_WINDOW_MS
    }

    fun remember(kind: Arrival, pillId: String, fingerprint: String?, nowMs: Long) {
        if (fingerprint != null) {
            fingerprints.remove(fingerprint)
            fingerprints.add(fingerprint)
            while (fingerprints.size > MAX) fingerprints.remove(fingerprints.first())
        }
        if (kind == Arrival.QUESTION) {
            questionAt.remove(pillId)
            questionAt[pillId] = nowMs
            while (questionAt.size > MAX) questionAt.remove(questionAt.keys.first())
        }
    }

    fun size(): Int = fingerprints.size + questionAt.size

    companion object {
        /** How close a session's change into "question" and the question itself arrive. */
        const val PAIR_WINDOW_MS = 15_000L
        const val MAX = 32
    }
}
