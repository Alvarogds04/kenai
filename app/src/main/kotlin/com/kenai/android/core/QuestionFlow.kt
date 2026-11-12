package com.kenai.android.core

import com.kenai.android.link.ApprovalRequest
import com.kenai.android.link.AskedQuestion
import com.kenai.android.link.Protocol

/** Where the user is in answering a question from Claude Code: which question, and what is picked for each. */
data class QuestionState(val index: Int, val picks: List<List<String>>)

/**
 * What the question screen shows (kenai-0.1 S9, T15). ANSWER: the question is here to answer (the `answers` capability). ON_COMPUTER:
 * the agent asks but the phone may not answer (where to answer, as before). GONE: answered or expired on the computer: the screen closes.
 */
enum class QuestionPresence { ANSWER, ON_COMPUTER, GONE }

/**
 * The rules of answering (docs/ANDROID_LINK.md, "Answering Claude Code's questions"), pure so each is a unit test. The
 * labels are the computer's exact ones; nothing the user types ever goes into an answer, only a tap on an option.
 */
object QuestionFlow {
    fun start(questions: List<AskedQuestion>) = QuestionState(0, questions.map { emptyList() })

    private fun current(q: List<AskedQuestion>, s: QuestionState) = q[s.index.coerceIn(0, q.lastIndex)]

    /** A tap on an option: replaces the pick of a single choice, adds or removes one of a multiple choice. An unknown label does nothing. */
    fun toggle(questions: List<AskedQuestion>, s: QuestionState, label: String): QuestionState {
        if (questions.isEmpty() || s.picks.size != questions.size) return s
        val i = s.index.coerceIn(0, questions.lastIndex)
        val q = questions[i]
        if (q.options.none { it.label == label }) return s
        val now = s.picks[i]
        val next = when {
            !q.multiSelect -> listOf(label)
            label in now -> now - label
            else -> now + label
        }
        return s.copy(picks = s.picks.toMutableList().also { it[i] = ordered(q, next) })
    }

    /** In the order the options are listed, whatever the order of the taps. */
    private fun ordered(q: AskedQuestion, picked: List<String>) = q.options.map { it.label }.filter { it in picked }

    fun isLast(questions: List<AskedQuestion>, s: QuestionState) = s.index >= questions.lastIndex

    /** The current question has an answer (at least one pick). */
    fun canAdvance(questions: List<AskedQuestion>, s: QuestionState): Boolean =
        questions.isNotEmpty() && s.picks.size == questions.size && s.picks[s.index.coerceIn(0, questions.lastIndex)].isNotEmpty()

    fun next(questions: List<AskedQuestion>, s: QuestionState): QuestionState =
        if (canAdvance(questions, s) && !isLast(questions, s)) s.copy(index = s.index + 1) else s

    fun back(s: QuestionState): QuestionState = if (s.index > 0) s.copy(index = s.index - 1) else s

    /** Every question answered as the computer will accept it: one label for a single choice, one or more for a multiple choice. */
    fun complete(questions: List<AskedQuestion>, s: QuestionState): Boolean =
        questions.isNotEmpty() && s.picks.size == questions.size &&
            questions.indices.all { i ->
                val p = s.picks[i]
                val q = questions[i]
                p.isNotEmpty() && p.size <= q.options.size && (q.multiSelect || p.size == 1) &&
                    p.all { l -> q.options.any { it.label == l } } && p.distinct().size == p.size
            }

    /** What goes to the computer, or null if something is missing. */
    fun picks(questions: List<AskedQuestion>, s: QuestionState): List<List<String>>? =
        if (complete(questions, s)) s.picks else null

    /** For the lock prompt: the answers in a line ("Postgres; Typos, Facets"). The labels are the agent's own words. */
    fun summary(questions: List<AskedQuestion>, s: QuestionState): String =
        s.picks.take(questions.size).joinToString("; ") { it.joinToString(", ") }

    /** The computer stops accepting an answer after 115 s; the phone stops offering it at 120 s, like an approval. */
    fun expired(createdAtMs: Long, nowMs: Long): Boolean = nowMs - createdAtMs > Protocol.APPROVAL_TTL_MS

    /** "Question 2 of 3", or null for a single question. */
    fun position(questions: List<AskedQuestion>, s: QuestionState): Pair<Int, Int>? =
        if (questions.size > 1) (s.index + 1) to questions.size else null

    // ── The question screen (kenai-0.1 S9, screen 4; T15) ──

    /** The progress bar: one segment per question, filled up to the current one. */
    fun segments(questions: List<AskedQuestion>, s: QuestionState): List<Boolean> = questions.indices.map { it <= s.index }

    /**
     * [hasRequest]: the phone holds the question now; [seenRequest]: it held it while the screen was open; [asking]: the agent is still
     * in its question state. A question that was here and went (answered or expired on the computer) closes the screen.
     */
    fun presence(hasRequest: Boolean, seenRequest: Boolean, asking: Boolean): QuestionPresence = when {
        hasRequest -> QuestionPresence.ANSWER
        seenRequest || !asking -> QuestionPresence.GONE
        else -> QuestionPresence.ON_COMPUTER
    }

    /**
     * The strip over a question: the request that waits too (the one Home's hero shows), «Claude Code espera tu sí» / «Tocá para revisar
     * el pedido»; null when none waits (the normal strip).
     */
    fun strip(approvals: List<ApprovalRequest>, nameOf: (String) -> String, w: Words = Words.current()): HomePanel.Strip? =
        ApprovalSheetPlan.hero(approvals)?.let { HomePanel.Strip(HomePanel.Tone.WAITING, w.waitsForYourYes(nameOf(it.pillId)), w.tapToReview, 0) }
}
