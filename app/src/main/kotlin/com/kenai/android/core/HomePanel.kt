package com.kenai.android.core

import com.kenai.android.link.ApprovalRequest
import com.kenai.android.link.LinkHealthText
import com.kenai.android.link.Protocol
import com.kenai.android.link.SessionInfo
import com.kenai.android.character.BotState
import com.kenai.android.character.Rgb

/**
 * What the Home screen shows, like the PC's Home panel: one agent card for the agent that matters
 * most (or the one the user tapped), and a grid of the others. Pure, so it is unit-tested.
 */
object HomePanel {
    private val URGENCY = listOf(
        BotState.APPROVAL, BotState.QUESTION, BotState.ERROR, BotState.RATELIMIT, BotState.DIZZY,
        BotState.WORKING, BotState.SEARCHING, BotState.THINKING, BotState.FINISHED, BotState.SLEEPING, BotState.IDLE,
    )

    /** The tapped agent if it is still there, else the one that needs the user most (the first on a tie). */
    fun focus(sessions: List<SessionInfo>, selectedPillId: String?): SessionInfo? =
        sessions.firstOrNull { it.pillId == selectedPillId }
            ?: sessions.minByOrNull { URGENCY.indexOf(it.state).let { i -> if (i < 0) URGENCY.size else i } }

    /** The others, in the order the desktop sent them. */
    fun others(sessions: List<SessionInfo>, focus: SessionInfo?): List<SessionInfo> =
        sessions.filter { it.pillId != focus?.pillId }

    /**
     * The one link on the agent card: answer a waiting request first, else pair when there is no
     * computer yet, else nothing (Settings has its own button at the top).
     */
    enum class Link { NONE, APPROVAL, PAIR }

    fun link(paired: Boolean, demo: Boolean, hasApproval: Boolean): Link = when {
        hasApproval -> Link.APPROVAL
        !paired && !demo -> Link.PAIR
        else -> Link.NONE
    }

    /** There is something to open: steps, a last message or a project name (only with the "details" capability). */
    fun hasDetails(s: SessionInfo): Boolean = s.steps.isNotEmpty() || !s.finalLine.isNullOrBlank() || s.project != null || s.files.isNotEmpty()

    /** The colour of an agent: the one the user gave its pill on the computer, else the catalog's. */
    fun colorHex(s: SessionInfo): String? = s.color ?: Pills.byId(s.pillId)?.colorHex

    /** Newest first, as the detail screen lists them. */
    fun stepsNewestFirst(s: SessionInfo): List<String> = s.steps.asReversed()

    /** "#8AB4F8" to a colour for a mini Kenai's body; a malformed value gives null (the default body). */
    fun rgb(hex: String): Rgb? {
        val h = hex.trim().removePrefix("#")
        if (h.length != 6) return null
        val v = h.toIntOrNull(16) ?: return null
        return Rgb(((v shr 16) and 0xFF) / 255.0, ((v shr 8) and 0xFF) / 255.0, (v and 0xFF) / 255.0)
    }

    // ── Inicio, "Decidí en un toque" (kenai-0.1 S9, T13) ──

    /**
     * What Inicio is about now. RESULT: what became of the decision just made (kenai-0.1 S9, T14); it holds Home until a request
     * arrives after it, even while the computer is away («No se pudo enviar» is about that). OFFLINE: a paired computer is not
     * connected (nothing can be answered; requests were dropped). REQUEST: a request waits; it is the hero. WAITING: only a
     * question waits; the agent card offers it. CALM: nothing waits.
     */
    enum class Scene { REQUEST, WAITING, CALM, OFFLINE, RESULT }

    /** [newestArrivalMs]: the latest request's arrival on the same elapsed clock as [Outcome.sinceMs]; a newer one beats the result. */
    fun scene(
        paired: Boolean, connected: Boolean, approvals: Int, sessions: List<SessionInfo>,
        result: Outcome? = null, newestArrivalMs: Long? = null,
    ): Scene = when {
        result != null && (newestArrivalMs == null || newestArrivalMs <= result.sinceMs) -> Scene.RESULT
        paired && !connected -> Scene.OFFLINE
        approvals > 0 -> Scene.REQUEST
        sessions.any { it.state == BotState.QUESTION } -> Scene.WAITING
        else -> Scene.CALM
    }

    private val WAITS = setOf(BotState.APPROVAL, BotState.QUESTION)
    private val RUNS = setOf(BotState.WORKING, BotState.THINKING, BotState.SEARCHING)

    /** "También te espera" (a request or a question, but not the hero's agent), "En curso" (busy), and the rest; each in the desktop's order. */
    data class Groups(val waiting: List<SessionInfo>, val running: List<SessionInfo>, val rest: List<SessionInfo>)

    /**
     * [requestPills]: the agents whose request is on the phone (null: not known, every APPROVAL counts). An agent in APPROVAL with no
     * request here (already decided, or not arrived yet) cannot be answered from the phone, so it is listed with the rest.
     */
    fun groups(sessions: List<SessionInfo>, heroPillId: String?, requestPills: Set<String>? = null): Groups {
        val shown = sessions.filter { it.pillId != heroPillId || it.state !in WAITS }
        fun waits(s: SessionInfo) = s.state == BotState.QUESTION || (s.state == BotState.APPROVAL && (requestPills == null || s.pillId in requestPills))
        return Groups(
            waiting = shown.filter { waits(it) },
            running = shown.filter { it.state in RUNS },
            rest = shown.filter { !waits(it) && it.state !in RUNS },
        )
    }

    /**
     * The agent whose request is Home's hero (or is offered next under a result) also has a question (kenai-0.1 S9, T17; L24):
     * the computer shows it asking, or the phone holds a question of it ([questionPills]). [groups] leaves that agent out, so its
     * question gets its own «Responder» row in «También te espera». Null when there is no request hero or it does not ask.
     */
    fun heroAsks(sessions: List<SessionInfo>, requestPillId: String?, questionPills: Set<String>): SessionInfo? {
        requestPillId ?: return null
        return sessions.firstOrNull { it.pillId == requestPillId && (it.state == BotState.QUESTION || it.pillId in questionPills) }
    }

    /** The bar's badge on Inicio: the requests plus the questions that wait. */
    fun waitingCount(approvals: Int, sessions: List<SessionInfo>): Int = approvals + sessions.count { it.state == BotState.QUESTION }

    /** What is left of a request's 120 s, counted from its arrival on this phone's clock; never below zero or above the whole. */
    fun remainingMs(arrivedMs: Long, nowMs: Long, ttlMs: Long = Protocol.APPROVAL_TTL_MS): Long =
        (ttlMs - (nowMs - arrivedMs).coerceAtLeast(0)).coerceIn(0, ttlMs)

    /** "1:59", rounded up to the second, so the first second still reads 2:00 and 0:00 means it is gone. */
    fun clock(remainingMs: Long): String {
        val s = (remainingMs.coerceAtLeast(0) + 999) / 1000
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** "Caduca en 1:59". */
    fun expiry(remainingMs: Long, w: Words = Words.current()): String = w.expiresIn(clock(remainingMs))

    /** How full the expiry bar is: 1 at arrival, 0 when it expires. */
    fun expiryFraction(remainingMs: Long, ttlMs: Long = Protocol.APPROVAL_TTL_MS): Float =
        (remainingMs.coerceIn(0, ttlMs).toDouble() / ttlMs).toFloat()

    /**
     * The strip's role: sun while something waits, go while calm, red while the computer is away; for a result (T14): live while
     * sending, go once allowed (DONE), faint when denied or expired, red (OFFLINE) when it could not be sent.
     */
    enum class Tone { WAITING, CALM, OFFLINE, SENDING, DONE, FAINT }

    /**
     * The strip at the top of Inicio: title, subtitle and the chip's number (what waits, or what runs while calm; 0 hides it);
     * [spinner] while a decision is being sent.
     */
    data class Strip(val tone: Tone, val title: String, val subtitle: String, val count: Int, val spinner: Boolean = false)

    fun strip(
        scene: Scene, computer: String, sessions: List<SessionInfo>, approvals: List<ApprovalRequest>, nameOf: (String) -> String,
        lostAtMs: Long?, connecting: Boolean, nowMs: Long, w: Words = Words.current(), result: Outcome? = null,
    ): Strip = when (scene) {
        // The result's subtitle is always the computer's name: it is about what that computer got.
        Scene.RESULT -> when (result?.kind) {
            OutcomeKind.SENDING -> Strip(Tone.SENDING, w.sendingTo(computer), computer, 0, spinner = true)
            OutcomeKind.ALLOWED -> Strip(Tone.DONE, w.received(true, computer), computer, 0)
            OutcomeKind.DENIED -> Strip(Tone.FAINT, w.received(false, computer), computer, 0)
            OutcomeKind.FAILED -> Strip(Tone.OFFLINE, w.couldNotSend, computer, 0)
            OutcomeKind.EXPIRED, null -> Strip(Tone.FAINT, w.notPendingAnyMore, computer, 0)
        }
        Scene.OFFLINE -> Strip(
            Tone.OFFLINE, w.offlineFrom(computer),
            lostAtMs?.let { w.lastHeard(LinkHealthText.ago(nowMs - it, w)) } ?: if (connecting) w.connecting else w.notConnected, 0,
        )
        Scene.REQUEST -> Strip(
            Tone.WAITING, w.waitsForYourYes(approvals.firstOrNull()?.let { nameOf(it.pillId) } ?: w.anAgent), w.computerAgents(computer, sessions.size),
            waitingCount(approvals.size, sessions),
        )
        Scene.WAITING -> Strip(
            Tone.WAITING, w.asksYou(sessions.firstOrNull { it.state == BotState.QUESTION }?.let { nameOf(it.pillId) } ?: w.anAgent), w.computerAgents(computer, sessions.size),
            waitingCount(approvals.size, sessions),
        )
        Scene.CALM -> Strip(Tone.CALM, w.computerCalm(computer), w.connected, HomeText.running(sessions))
    }

    /** What «Lo siguiente» offers after a result: the next request (it expires soonest), else the first agent with a question. */
    enum class NextKind { APPROVAL, QUESTION }

    /** [fingerprint]: the request's (null for a question). */
    data class Next(val kind: NextKind, val pillId: String, val fingerprint: String?)

    /** The next thing that waits after a result, leaving out the requests in [exclude] (the one just decided, ones still being sent). */
    fun next(approvals: List<ApprovalRequest>, sessions: List<SessionInfo>, exclude: Set<String>): Next? {
        approvals.firstOrNull { it.fingerprint !in exclude }?.let { return Next(NextKind.APPROVAL, it.pillId, it.fingerprint) }
        return sessions.firstOrNull { it.state == BotState.QUESTION }?.let { Next(NextKind.QUESTION, it.pillId, null) }
    }
}
