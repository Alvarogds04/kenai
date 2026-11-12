package com.kenai.android.core

import com.kenai.android.link.DiagStep
import com.kenai.android.link.LostReason
import java.util.Locale

/**
 * The words the app builds in Kotlin rather than reading from resources: the glance (ongoing notification, widgets, tile), the
 * widget lines, Home's sentences, the tool labels of the island and the notifications, the link's health, the "Can't connect?" checks
 * and their copied report, the notification channels and the demo's texts (kenai-0.1 S6). Two tables, [EN] and [ES] (Rioplatense voseo, as the design artboards); [current] picks Spanish when
 * the phone is in Spanish and English for every other language (kenai-0.1 S2.6, L5 option 2a). No per-app picker. Pure, so every
 * phrase is a unit test; agent and product names (Claude Code, Codex, Koda, Kenai) are never translated. The Spanish follows res/values-es: the «emparejar» family
 * (not the other verb), «computadora», «pedido» for a permission request, «Ajustes», «Inicio», «Denegar».
 */
interface Words {
    /** The language for day and month names. */
    val locale: Locale

    // ── Glance ──────────────────────────────────────────────────────────────────────────────────────
    val notPaired: String
    val openToConnect: String
    val connecting: String
    val notConnected: String
    val reachingComputer: String
    val cannotReachComputer: String
    val waitingForYou: String
    fun requestsToApprove(n: Int): String
    val waitingForAnswer: String
    fun questionsFromAgents(n: Int): String
    val agentNeedsALook: String
    fun agentsStopped(n: Int): String
    fun isWorking(agent: String): String
    fun agentsWorking(n: Int): String
    fun onComputer(name: String): String
    fun toComputer(name: String): String
    val working: String
    val allDone: String
    val finished: String
    val connected: String
    val nothingRunning: String

    // ── One agent's state, in a few words ───────────────────────────────────────────────────────────
    val waitingForApproval: String
    val hasAQuestion: String
    val stoppedOnAnError: String
    val hitALimit: String
    val needsALook: String
    val idle: String
    val thinking: String
    val searching: String

    // ── Widgets ─────────────────────────────────────────────────────────────────────────────────────
    fun resetsAt(clock: String): String
    fun resetsOnDay(day: String, clock: String): String
    fun resetsOnDate(month: String, day: Int, clock: String): String
    val nearLimit: String
    val waitingForComputer: String
    val noAgentsRunning: String
    fun waiting(n: Int): String
    val allCalm: String
    val waitingForPc: String
    val nothingNeedsYou: String
    val yourPc: String
    /** An agent with no name, as a subject: "An agent". */
    val anAgent: String
    /** The plan's weekly window: "Claude · week". */
    val week: String

    // ── Home ────────────────────────────────────────────────────────────────────────────────────────
    /** "Allowed: edit files". */
    fun recent(allowed: Boolean, action: String): String
    /** "You allowed Claude Code to run a command"; a blank agent is "an agent". */
    fun decision(allowed: Boolean, agent: String, action: String): String

    // ── Inicio: the strip, the request hero, the calm card (kenai-0.1 S9) ───────────────────────────
    /** The strip's title while a request waits: "Claude Code is waiting for your yes". */
    fun waitsForYourYes(agent: String): String
    /** The approval notification's title: "Claude Code needs your approval" (kenai-0.1 S9, screen 5). */
    fun needsYourApproval(agent: String): String
    /** A waiting agent's row on «Aprobaciones pendientes»: "Waiting for your OK" (the same words as the app's state label). */
    val waitingForYourOk: String
    /** The count on «Aprobaciones pendientes», short enough to sit beside Koda on a 3x2: "2 wait for you". */
    fun alertCount(n: Int): String
    /** The strip's title while only a question waits. */
    fun asksYou(agent: String): String
    /** The strip's subtitle over a question while a request waits too: a tap reviews it (T15). */
    val tapToReview: String
    /** "Alvaro PC · 3 agents". */
    fun computerAgents(computer: String, n: Int): String
    /** "Alvaro PC · all calm". */
    fun computerCalm(computer: String): String
    /** "No connection to Alvaro PC". */
    fun offlineFrom(computer: String): String
    /** "Last heard 3 min ago" ([ago] from LinkHealthText.ago). */
    fun lastHeard(ago: String): String
    /** The heading of the kept list while offline: "What we last knew · 3 min ago". */
    fun lastKnown(ago: String): String
    /** The strip's chip while calm: "2 running". */
    fun runningCount(n: Int): String
    /** What TalkBack says for the strip's count: "2 are waiting for you". */
    fun waitingForYouCount(n: Int): String
    /** "Expires in 1:59". */
    fun expiresIn(clock: String): String
    /** "Wants to run a command". */
    fun wants(action: String): String
    /** "Wants to run a command in kenai". */
    fun wantsIn(action: String, project: String): String
    /** What a recognised destructive command would do: "Deletes files". */
    fun risk(kind: RiskKind): String
    /** After the risk, where: "inside kenai.". */
    fun riskWhere(project: String): String
    /** The calm card while an agent works: "Codex is working in app. I'll tell you as soon as someone needs your yes."; a blank project is left out. */
    fun calmWorking(agent: String, project: String): String

    // ── Inicio: the result of a decision (kenai-0.1 S9, screen 3; T14) ──────────────────────────────
    /** The strip while sending: "Sending to Alvaro PC…". */
    fun sendingTo(computer: String): String
    /** The strip once confirmed: "Allowed · Alvaro PC got it". */
    fun received(allowed: Boolean, computer: String): String
    /** The strip and heading of an expired request. */
    val notPendingAnyMore: String
    /** The strip when the decision could not be sent. */
    val couldNotSend: String
    val sendingTitle: String
    val sendingBody: String
    /** "You let it run a command" ([action] from [ToolLabels.action]). */
    fun youDecided(allowed: Boolean, action: String): String
    /** "Claude Code keeps working in kenai."; a blank project is left out. */
    fun keepsWorking(agent: String, project: String): String
    /** "Claude Code won't run it. …"; [command]: a shell command ("run"), else any action ("do"). */
    fun willNotDo(agent: String, command: Boolean): String
    /** Expired, nothing sent from the phone: "It expired at 14:45 or was answered on your computer. …". */
    fun expiredBody(clock: String): String
    /** Expired after the phone sent it, with no confirmation. */
    fun expiredUnconfirmed(clock: String): String
    val didNotArrive: String
    val failedBody: String
    /** "14:41 · buzzed once · saved in Activity"; [buzzes] 0 leaves the buzz out. */
    fun resultMeta(clock: String, buzzes: Int): String

    // ── Tools ───────────────────────────────────────────────────────────────────────────────────────
    /** "Running a command". */
    fun toolLabel(kind: ToolKind): String
    /** "run a command"; null for a tool with no action of its own (its cleaned name is used). */
    fun toolAction(kind: ToolKind): String?
    val doSomething: String

    // ── The link ────────────────────────────────────────────────────────────────────────────────────
    val lessThanAMinute: String
    fun connectionLost(ago: String): String
    fun tries(n: Int): String
    fun lostWhy(reason: LostReason): String
    fun batterySteps(samsung: Boolean): List<String>

    // ── "Can't connect?" ────────────────────────────────────────────────────────────────────────────
    fun diagTitle(step: DiagStep): String
    val needsWifi: String
    val needsTheAddress: String
    val needsTheSecureConnection: String
    fun checkFailed(what: String): String
    val tryAgain: String
    val aNameHidden: String
    val wifiConnected: String
    val notOnWifi: String
    val joinTheSameWifi: String
    fun answersIn(ms: Long, host: String): String
    fun noAnswerFrom(host: String): String
    val noAnswerHint: String
    fun cannotReachHost(host: String): String
    val newAddressHint: String
    val notAnnouncing: String
    val notAnnouncingHint: String
    val foundAtTheSavedAddress: String
    fun foundElsewhere(host: String): String
    val foundElsewhereHint: String
    val certificateMatches: String
    val notThePairedComputer: String
    val reinstalledHint: String
    val handshakeFailed: String
    val handshakeHint: String
    val secureNotOpened: String
    val secureNotOpenedHint: String
    val couldNotSayHello: String
    val seeTheLinesAbove: String
    val closedAtOnce: String
    fun codeAccepted(version: Int): String
    fun versionMismatch(theirs: Int, ours: Int): String
    val updateEither: String
    val codeRefused: String
    val codeRefusedHint: String
    fun answeredWithError(code: String): String
    val unexpectedAnswer: String
    val updateBoth: String
    val noAnswerToHello: String
    val busyOrAsleep: String
    val brokeDuringHello: String

    // ── The copied "Can't connect?" report ──────────────────────────────────────────────────────────
    /** Its first line: "Kenai for Android 0.1.0 · Android 14 · Pixel 7". */
    fun reportHeader(appVersion: String, androidVersion: String, device: String): String
    /** Its second line; [host] is already masked, [via] is [viaDirectLink] or [viaNotConnected]. */
    fun reportComputer(host: String, via: String): String
    /** The mark of a check that found a problem ("PROBLEM"); a passed check is "OK" in every language. */
    val reportProblem: String
    /** The mark of a check not asked ("SKIPPED"). */
    val reportSkipped: String
    val viaDirectLink: String
    val viaNotConnected: String
    /** No pairing to name a computer from. */
    val noComputer: String

    // ── The demo (it stands for the desktop's own words) ────────────────────────────────────────────
    /** The demo computer's name. */
    val demoComputer: String
    val demoReading: String
    val demoEditing: String
    val demoSearching: String
    val demoWaiting: String
    val demoTesting: String
    val demoBuilding: String
    val demoWhichBranch: String
    val demoDone: String
    val demoTestsFailed: String
    val demoRateLimited: String
    val demoIdle: String

    // ── Notification channels ───────────────────────────────────────────────────────────────────────
    val channelLink: String
    val channelLinkAbout: String
    val channelUpdates: String
    val channelUpdatesAbout: String
    val channelApprovalsQuiet: String
    val channelApprovalsQuietAbout: String
    val channelApprovals: String
    val channelApprovalsAbout: String

    companion object {
        val EN: Words = English
        val ES: Words = Spanish

        /** Spanish when the phone's language is Spanish (any country), English otherwise. Read at each use, so a change applies. */
        fun current(locale: Locale = Locale.getDefault()): Words = if (locale.language == "es") ES else EN
    }
}

/** The tools [ToolLabels] knows, by what they do. */
enum class ToolKind { ASK, RUN, READ, EDIT, SEARCH_FILES, LOOK, SEARCH_WEB, READ_WEB, SUB_AGENT, PLAN }

private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

private object English : Words {
    override val locale: Locale = Locale.ENGLISH

    override val notPaired = "Not paired"
    override val openToConnect = "Open Kenai to connect to your computer"
    override val connecting = "Connecting…"
    override val notConnected = "Not connected"
    override val reachingComputer = "Reaching your computer"
    override val cannotReachComputer = "Kenai cannot reach your computer"
    override val waitingForYou = "Waiting for you"
    override fun requestsToApprove(n: Int) = plural(n, "1 request to approve", "$n requests to approve")
    override val waitingForAnswer = "Waiting for your answer"
    override fun questionsFromAgents(n: Int) = plural(n, "1 question from an agent", "$n questions from agents")
    override val agentNeedsALook = "An agent needs a look"
    override fun agentsStopped(n: Int) = plural(n, "1 agent stopped", "$n agents stopped")
    override fun isWorking(agent: String) = "$agent is working"
    override fun agentsWorking(n: Int) = plural(n, "1 agent is working", "$n agents are working")
    override fun onComputer(name: String) = "On $name"
    override fun toComputer(name: String) = "To $name"
    override val working = "Working"
    override val allDone = "All done"
    override val finished = "Finished"
    override val connected = "Connected"
    override val nothingRunning = "Nothing is running"

    override val waitingForApproval = "Waiting for your approval"
    override val hasAQuestion = "Has a question"
    override val stoppedOnAnError = "Stopped on an error"
    override val hitALimit = "Hit a limit"
    override val needsALook = "Needs a look"
    override val idle = "Idle"
    override val thinking = "Thinking"
    override val searching = "Searching"

    override fun resetsAt(clock: String) = "resets at $clock"
    override fun resetsOnDay(day: String, clock: String) = "resets $day $clock"
    override fun resetsOnDate(month: String, day: Int, clock: String) = "resets $month $day $clock"
    override val nearLimit = "Near limit"
    override val waitingForComputer = "Waiting for your computer"
    override val noAgentsRunning = "No agents running"
    override fun waiting(n: Int) = "$n waiting"
    override val allCalm = "All calm"
    override val waitingForPc = "Waiting for your PC"
    override val nothingNeedsYou = "Nothing needs you"
    override val yourPc = "Your PC"
    override val anAgent = "An agent"
    override val week = "week"

    override fun recent(allowed: Boolean, action: String) = (if (allowed) "Allowed: " else "Denied: ") + action
    override fun decision(allowed: Boolean, agent: String, action: String) =
        "You ${if (allowed) "allowed" else "denied"} ${agent.ifBlank { "an agent" }} to $action"

    override fun waitsForYourYes(agent: String) = "$agent is waiting for your yes"
    override fun needsYourApproval(agent: String) = "$agent needs your approval"
    override val waitingForYourOk = "Waiting for your OK"
    override fun alertCount(n: Int) = plural(n, "1 waits for you", "$n wait for you")
    override fun asksYou(agent: String) = "$agent has a question"
    override val tapToReview = "Tap to review the request"
    override fun computerAgents(computer: String, n: Int) = "$computer · " + plural(n, "1 agent", "$n agents")
    override fun computerCalm(computer: String) = "$computer · all calm"
    override fun offlineFrom(computer: String) = "No connection to $computer"
    override fun lastHeard(ago: String) = "Last heard $ago ago"
    override fun lastKnown(ago: String) = "What we last knew · $ago ago"
    override fun runningCount(n: Int) = "$n running"
    override fun waitingForYouCount(n: Int) = plural(n, "1 is waiting for you", "$n are waiting for you")
    override fun expiresIn(clock: String) = "Expires in $clock"
    override fun wants(action: String) = "Wants to $action"
    override fun wantsIn(action: String, project: String) = "Wants to $action in $project"
    override fun risk(kind: RiskKind) = when (kind) {
        RiskKind.WIPES_DISK -> "Erases a disk"
        RiskKind.DELETES_DATA -> "Deletes data"
        RiskKind.DELETES_FILES -> "Deletes files"
        RiskKind.REWRITES_HISTORY -> "Rewrites the git history"
        RiskKind.DISCARDS_CHANGES -> "Throws away unsaved changes"
        RiskKind.RUNS_DOWNLOADED_CODE -> "Runs code from the internet"
        RiskKind.OPENS_PERMISSIONS -> "Opens the permissions to everyone"
        RiskKind.EMPTIES_FILE -> "Empties a file"
    }
    override fun riskWhere(project: String) = "inside $project."
    override fun calmWorking(agent: String, project: String) =
        "$agent is working" + (if (project.isBlank()) "" else " in $project") + ". I'll tell you as soon as someone needs your yes."

    override fun sendingTo(computer: String) = "Sending to $computer…"
    override fun received(allowed: Boolean, computer: String) = (if (allowed) "Allowed" else "Denied") + " · $computer got it"
    override val notPendingAnyMore = "That request is no longer pending"
    override val couldNotSend = "Couldn't send"
    override val sendingTitle = "Sending your decision"
    override val sendingBody = "Kenai is waiting for your computer to confirm it got it. Don't close anything."
    override fun youDecided(allowed: Boolean, action: String) = (if (allowed) "You let it " else "You didn't let it ") + action
    override fun keepsWorking(agent: String, project: String) =
        "${agent.ifBlank { "The agent" }} keeps working" + (if (project.isBlank()) "." else " in $project.")
    override fun willNotDo(agent: String, command: Boolean) =
        "${agent.ifBlank { "The agent" }} won't ${if (command) "run" else "do"} it. If you like, tell it why on your computer."
    override fun expiredBody(clock: String) = "It expired at $clock or was answered on your computer. Nothing was sent from the phone."
    override fun expiredUnconfirmed(clock: String) = "It expired at $clock before your computer confirmed your decision."
    override val didNotArrive = "Your decision didn't arrive"
    override val failedBody = "The connection to your computer was lost. The request is still open: trying again is safe."
    override fun resultMeta(clock: String, buzzes: Int) = listOfNotNull(
        clock,
        when (buzzes) { 0 -> null; 1 -> "buzzed once"; 2 -> "buzzed twice"; else -> "buzzed $buzzes times" },
        "saved in Activity",
    ).joinToString(" · ")

    override fun toolLabel(kind: ToolKind) = when (kind) {
        ToolKind.ASK -> "Asking a question"
        ToolKind.RUN -> "Running a command"
        ToolKind.READ -> "Reading files"
        ToolKind.EDIT -> "Editing files"
        ToolKind.SEARCH_FILES -> "Searching files"
        ToolKind.LOOK -> "Looking at files"
        ToolKind.SEARCH_WEB -> "Searching the web"
        ToolKind.READ_WEB -> "Reading a web page"
        ToolKind.SUB_AGENT -> "Running a sub-agent"
        ToolKind.PLAN -> "Planning"
    }

    override fun toolAction(kind: ToolKind) = when (kind) {
        ToolKind.ASK -> "ask a question"
        ToolKind.RUN -> "run a command"
        ToolKind.READ -> "read files"
        ToolKind.EDIT -> "edit files"
        ToolKind.SEARCH_FILES -> "search files"
        ToolKind.LOOK -> "look at files"
        ToolKind.SEARCH_WEB -> "search the web"
        ToolKind.READ_WEB -> "read a web page"
        ToolKind.SUB_AGENT -> "run a sub-agent"
        ToolKind.PLAN -> null
    }

    override val doSomething = "do something"

    override val lessThanAMinute = "less than a minute"
    override fun connectionLost(ago: String) = "Connection lost $ago ago, reconnecting"
    override fun tries(n: Int) = "($n tries)"
    override fun lostWhy(reason: LostReason) = when (reason) {
        LostReason.SILENT -> "The computer stopped answering. This is usual after the phone slept."
        LostReason.CLOSED -> "The computer closed the connection."
        LostReason.NETWORK -> "The network dropped."
    }

    override fun batterySteps(samsung: Boolean) =
        if (samsung) listOf(
            "Open Settings > Apps > Kenai for Android > Battery and choose Unrestricted.",
            "Then Settings > Battery > Background usage limits: make sure Kenai is not in Sleeping apps or Deep sleeping apps (add it to Never sleeping apps).",
        ) else listOf(
            "Open the app's battery setting and choose Unrestricted (or Don't optimise).",
        )

    override fun diagTitle(step: DiagStep) = when (step) {
        DiagStep.NETWORK -> "Phone network"
        DiagStep.ADDRESS -> "Saved address answers"
        DiagStep.DISCOVERY -> "Computer announces itself"
        DiagStep.SECURE -> "Secure connection (pinned certificate)"
        DiagStep.TOKEN -> "Pairing code and version"
    }

    override val needsWifi = "Needs the Wi-Fi"
    override val needsTheAddress = "Needs the address to answer"
    override val needsTheSecureConnection = "Needs the secure connection"
    override fun checkFailed(what: String) = "The check itself failed ($what)"
    override val tryAgain = "Try again."
    override val aNameHidden = "a name (hidden)"
    override val wifiConnected = "Wi-Fi is connected"
    override val notOnWifi = "The phone is not on a Wi-Fi network"
    override val joinTheSameWifi = "Join the same Wi-Fi as your computer."
    override fun answersIn(ms: Long, host: String) = "Answers in $ms ms at $host"
    override fun noAnswerFrom(host: String) = "No answer from $host"
    override val noAnswerHint =
        "Is the computer on and Kenai running with the Phone link switched on? Same Wi-Fi? A guest network or client isolation blocks it."
    override fun cannotReachHost(host: String) = "Cannot reach $host"
    override val newAddressHint = "The computer may have a new address: see the next line, or type the address in Home."
    override val notAnnouncing = "The computer is not announcing itself here"
    override val notAnnouncingHint = "Some networks block this. If the saved address answers, you can ignore it; otherwise type the address in Home."
    override val foundAtTheSavedAddress = "Found, same address as the saved one"
    override fun foundElsewhere(host: String) = "Found at $host, not at the saved address"
    override val foundElsewhereHint = "Open Home: the phone updates the address by itself when it is not connected."
    override val certificateMatches = "The computer's certificate is the one you paired with"
    override val notThePairedComputer = "This is not the computer you paired with (its certificate differs)"
    override val reinstalledHint = "Koda may have been reinstalled, or this is another computer. Pair again."
    override val handshakeFailed = "The secure handshake failed"
    override val handshakeHint = "Try again. If it keeps failing, pair again."
    override val secureNotOpened = "The secure connection could not be opened"
    override val secureNotOpenedHint = "The address answers but nothing speaks Kenai there. Is the Phone link switched on?"
    override val couldNotSayHello = "Could not connect to say hello"
    override val seeTheLinesAbove = "See the lines above."
    override val closedAtOnce = "The computer closed the connection at once"
    override fun codeAccepted(version: Int) = "Pairing code accepted, protocol version $version"
    override fun versionMismatch(theirs: Int, ours: Int) = "The computer speaks version $theirs, this app speaks $ours"
    override val updateEither = "Update Koda on the computer or Kenai on the phone."
    override val codeRefused = "The pairing code was refused"
    override val codeRefusedHint = "The computer made a new code (Pair again). Scan its new code."
    override fun answeredWithError(code: String) = "The computer answered with an error ($code)"
    override val unexpectedAnswer = "An unexpected answer to hello"
    override val updateBoth = "Update Koda on the computer and Kenai on the phone."
    override val noAnswerToHello = "No answer to hello"
    override val busyOrAsleep = "The computer is busy or asleep. Try again."
    override val brokeDuringHello = "The connection broke while saying hello"

    override fun reportHeader(appVersion: String, androidVersion: String, device: String) = "Kenai for Android $appVersion · Android $androidVersion · $device"
    override fun reportComputer(host: String, via: String) = "Computer: $host · connected via: $via"
    override val reportProblem = "PROBLEM"
    override val reportSkipped = "SKIPPED"
    override val viaDirectLink = "the direct link"
    override val viaNotConnected = "not connected"
    override val noComputer = "none"

    override val demoComputer = "Demo"
    override val demoReading = "Reading the project"
    override val demoEditing = "Editing files"
    override val demoSearching = "Searching the code"
    override val demoWaiting = "Waiting for your approval"
    override val demoTesting = "Running tests"
    override val demoBuilding = "Building"
    override val demoWhichBranch = "Which branch?"
    override val demoDone = "Done"
    override val demoTestsFailed = "Tests failed"
    override val demoRateLimited = "Rate limited"
    override val demoIdle = "Idle"

    override val channelLink = "Connection"
    override val channelLinkAbout = "Shows that Kenai is connected to your computer"
    override val channelUpdates = "Updates"
    override val channelUpdatesAbout = "An agent finished, failed or has a question"
    override val channelApprovalsQuiet = "Approvals (quiet)"
    override val channelApprovalsQuietAbout = "A waiting request, kept in the shade while the pill shows it"
    override val channelApprovals = "Approvals"
    override val channelApprovalsAbout = "An agent is waiting for your permission"
}

private object Spanish : Words {
    override val locale: Locale = Locale.forLanguageTag("es")

    override val notPaired = "Sin emparejar"
    override val openToConnect = "Abrí Kenai para conectarte con tu computadora"
    override val connecting = "Conectando…"
    override val notConnected = "Sin conexión"
    override val reachingComputer = "Buscando tu computadora"
    override val cannotReachComputer = "Kenai no llega a tu computadora"
    override val waitingForYou = "Te está esperando"
    override fun requestsToApprove(n: Int) = plural(n, "1 pedido para aprobar", "$n pedidos para aprobar")
    override val waitingForAnswer = "Esperando tu respuesta"
    override fun questionsFromAgents(n: Int) = plural(n, "1 pregunta de un agente", "$n preguntas de agentes")
    override val agentNeedsALook = "Un agente necesita que lo mires"
    override fun agentsStopped(n: Int) = plural(n, "1 agente se detuvo", "$n agentes se detuvieron")
    override fun isWorking(agent: String) = "$agent está trabajando"
    override fun agentsWorking(n: Int) = plural(n, "1 agente está trabajando", "$n agentes están trabajando")
    override fun onComputer(name: String) = "En $name"
    override fun toComputer(name: String) = "Con $name"
    override val working = "Trabajando"
    override val allDone = "Todo listo"
    override val finished = "Terminó"
    override val connected = "Conectado"
    override val nothingRunning = "No hay nada en marcha"

    override val waitingForApproval = "Esperando tu aprobación"
    override val hasAQuestion = "Tiene una pregunta"
    override val stoppedOnAnError = "Se detuvo por un error"
    override val hitALimit = "Llegó a un límite"
    override val needsALook = "Necesita que lo mires"
    override val idle = "En reposo"
    override val thinking = "Pensando"
    override val searching = "Buscando"

    override fun resetsAt(clock: String) = "se reinicia a las $clock"
    override fun resetsOnDay(day: String, clock: String) = "se reinicia el $day $clock"
    override fun resetsOnDate(month: String, day: Int, clock: String) = "se reinicia el $day $month $clock"
    override val nearLimit = "Cerca del límite"
    override val waitingForComputer = "Esperando tu computadora"
    override val noAgentsRunning = "No hay agentes en marcha"
    override fun waiting(n: Int) = "$n esperando"
    override val allCalm = "Todo tranquilo"
    override val waitingForPc = "Esperando tu computadora"
    override val nothingNeedsYou = "Nada te necesita"
    override val yourPc = "Tu computadora"
    override val anAgent = "Un agente"
    override val week = "semana"

    override fun recent(allowed: Boolean, action: String) = (if (allowed) "Permitiste: " else "Denegaste: ") + action
    override fun decision(allowed: Boolean, agent: String, action: String) =
        "Le ${if (allowed) "permitiste" else "denegaste"} a ${agent.ifBlank { "un agente" }} $action"

    override fun waitsForYourYes(agent: String) = "$agent espera tu sí"
    override fun needsYourApproval(agent: String) = "$agent necesita tu aprobación"
    override val waitingForYourOk = "Espera tu sí"
    override fun alertCount(n: Int) = plural(n, "1 te espera", "$n te esperan")
    override fun asksYou(agent: String) = "$agent tiene una pregunta"
    override val tapToReview = "Tocá para revisar el pedido"
    override fun computerAgents(computer: String, n: Int) = "$computer · " + plural(n, "1 agente", "$n agentes")
    override fun computerCalm(computer: String) = "$computer · todo tranquilo"
    override fun offlineFrom(computer: String) = "Sin conexión con $computer"
    override fun lastHeard(ago: String) = "Último dato hace $ago"
    override fun lastKnown(ago: String) = "Lo último que sabemos · hace $ago"
    override fun runningCount(n: Int) = "$n en curso"
    override fun waitingForYouCount(n: Int) = plural(n, "1 te espera", "$n te esperan")
    override fun expiresIn(clock: String) = "Caduca en $clock"
    override fun wants(action: String) = "Quiere $action"
    override fun wantsIn(action: String, project: String) = "Quiere $action en $project"
    override fun risk(kind: RiskKind) = when (kind) {
        RiskKind.WIPES_DISK -> "Borra un disco"
        RiskKind.DELETES_DATA -> "Borra datos"
        RiskKind.DELETES_FILES -> "Borra archivos"
        RiskKind.REWRITES_HISTORY -> "Reescribe el historial de git"
        RiskKind.DISCARDS_CHANGES -> "Descarta cambios sin guardar"
        RiskKind.RUNS_DOWNLOADED_CODE -> "Ejecuta código de internet"
        RiskKind.OPENS_PERMISSIONS -> "Abre los permisos a todos"
        RiskKind.EMPTIES_FILE -> "Vacía un archivo"
    }
    override fun riskWhere(project: String) = "dentro de $project."
    override fun calmWorking(agent: String, project: String) =
        "$agent está trabajando" + (if (project.isBlank()) "" else " en $project") + ". Te aviso apenas alguien necesite tu sí."

    override fun sendingTo(computer: String) = "Enviando a $computer…"
    override fun received(allowed: Boolean, computer: String) = (if (allowed) "Permitido" else "Denegado") + " · $computer lo recibió"
    override val notPendingAnyMore = "Ese pedido ya no está pendiente"
    override val couldNotSend = "No se pudo enviar"
    override val sendingTitle = "Enviando tu decisión"
    override val sendingBody = "Kenai espera que tu computadora confirme que la recibió. No cierres nada."
    override fun youDecided(allowed: Boolean, action: String) = (if (allowed) "Permitiste " else "Denegaste ") + action
    override fun keepsWorking(agent: String, project: String) =
        "${agent.ifBlank { "El agente" }} sigue trabajando" + (if (project.isBlank()) "." else " en $project.")
    override fun willNotDo(agent: String, command: Boolean) =
        "${agent.ifBlank { "El agente" }} no lo va a ${if (command) "ejecutar" else "hacer"}. Si querés, explicale por qué en tu computadora."
    override fun expiredBody(clock: String) = "Caducó a las $clock o se respondió en tu computadora. Desde el teléfono no se envió nada."
    override fun expiredUnconfirmed(clock: String) = "Caducó a las $clock sin que tu computadora confirmara tu decisión."
    override val didNotArrive = "Tu decisión no llegó"
    override val failedBody = "Se perdió la conexión con tu computadora. El pedido sigue abierto: volver a intentar es seguro."
    override fun resultMeta(clock: String, buzzes: Int) = listOfNotNull(
        clock,
        when (buzzes) { 0 -> null; 1 -> "vibró una vez"; 2 -> "vibró dos veces"; else -> "vibró $buzzes veces" },
        "quedó en Actividad",
    ).joinToString(" · ")

    override fun toolLabel(kind: ToolKind) = when (kind) {
        ToolKind.ASK -> "Haciendo una pregunta"
        ToolKind.RUN -> "Ejecutando un comando"
        ToolKind.READ -> "Leyendo archivos"
        ToolKind.EDIT -> "Editando archivos"
        ToolKind.SEARCH_FILES -> "Buscando en archivos"
        ToolKind.LOOK -> "Mirando archivos"
        ToolKind.SEARCH_WEB -> "Buscando en la web"
        ToolKind.READ_WEB -> "Leyendo una página web"
        ToolKind.SUB_AGENT -> "Ejecutando un subagente"
        ToolKind.PLAN -> "Planificando"
    }

    override fun toolAction(kind: ToolKind) = when (kind) {
        ToolKind.ASK -> "hacer una pregunta"
        ToolKind.RUN -> "ejecutar un comando"
        ToolKind.READ -> "leer archivos"
        ToolKind.EDIT -> "editar archivos"
        ToolKind.SEARCH_FILES -> "buscar en archivos"
        ToolKind.LOOK -> "mirar archivos"
        ToolKind.SEARCH_WEB -> "buscar en la web"
        ToolKind.READ_WEB -> "leer una página web"
        ToolKind.SUB_AGENT -> "ejecutar un subagente"
        ToolKind.PLAN -> null
    }

    override val doSomething = "hacer algo"

    override val lessThanAMinute = "menos de un minuto"
    override fun connectionLost(ago: String) = "Conexión perdida hace $ago, reconectando"
    override fun tries(n: Int) = "($n intentos)"
    override fun lostWhy(reason: LostReason) = when (reason) {
        LostReason.SILENT -> "La computadora dejó de responder. Es normal después de que el teléfono estuvo en reposo."
        LostReason.CLOSED -> "La computadora cerró la conexión."
        LostReason.NETWORK -> "Se cayó la red."
    }

    override fun batterySteps(samsung: Boolean) =
        if (samsung) listOf(
            "Abrí Ajustes > Aplicaciones > Kenai > Batería y elegí Sin restricciones.",
            "Después, en Ajustes > Batería > Límites de uso en segundo plano, fijate que Kenai no esté en Aplicaciones en suspensión ni en Aplicaciones en suspensión profunda (agregala a Aplicaciones que nunca se suspenden).",
        ) else listOf(
            "Abrí el ajuste de batería de la app y elegí Sin restricciones (o No optimizar).",
        )

    override fun diagTitle(step: DiagStep) = when (step) {
        DiagStep.NETWORK -> "Red del teléfono"
        DiagStep.ADDRESS -> "La dirección guardada responde"
        DiagStep.DISCOVERY -> "La computadora se anuncia"
        DiagStep.SECURE -> "Conexión segura (certificado fijado)"
        DiagStep.TOKEN -> "Código de emparejamiento y versión"
    }

    override val needsWifi = "Necesita el Wi-Fi"
    override val needsTheAddress = "Necesita que la dirección responda"
    override val needsTheSecureConnection = "Necesita la conexión segura"
    override fun checkFailed(what: String) = "Falló la comprobación misma ($what)"
    override val tryAgain = "Probá de nuevo."
    override val aNameHidden = "un nombre (oculto)"
    override val wifiConnected = "El Wi-Fi está conectado"
    override val notOnWifi = "El teléfono no está en una red Wi-Fi"
    override val joinTheSameWifi = "Conectate al mismo Wi-Fi que tu computadora."
    override fun answersIn(ms: Long, host: String) = "Responde en $ms ms en $host"
    override fun noAnswerFrom(host: String) = "Sin respuesta de $host"
    override val noAnswerHint =
        "¿La computadora está prendida, con Koda abierto y el enlace con el teléfono activado? ¿Mismo Wi-Fi? Una red de invitados o el aislamiento de clientes lo bloquean."
    override fun cannotReachHost(host: String) = "No se llega a $host"
    override val newAddressHint = "Puede que la computadora tenga una dirección nueva: mirá la línea siguiente o escribí la dirección en Inicio."
    override val notAnnouncing = "La computadora no se anuncia en esta red"
    override val notAnnouncingHint = "Algunas redes lo bloquean. Si la dirección guardada responde, podés ignorarlo; si no, escribí la dirección en Inicio."
    override val foundAtTheSavedAddress = "Encontrada, en la misma dirección que la guardada"
    override fun foundElsewhere(host: String) = "Encontrada en $host, no en la dirección guardada"
    override val foundElsewhereHint = "Abrí Inicio: el teléfono actualiza la dirección solo cuando no está conectado."
    override val certificateMatches = "El certificado de la computadora es el mismo con el que la emparejaste"
    override val notThePairedComputer = "Esta no es la computadora que emparejaste (su certificado es distinto)"
    override val reinstalledHint = "Puede que Koda se haya reinstalado, o es otra computadora. Volvé a emparejar."
    override val handshakeFailed = "Falló el inicio de la conexión segura"
    override val handshakeHint = "Probá de nuevo. Si sigue fallando, volvé a emparejar."
    override val secureNotOpened = "No se pudo abrir la conexión segura"
    override val secureNotOpenedHint = "La dirección responde, pero ahí no hay nada que hable con Kenai. ¿Está activado el enlace con el teléfono?"
    override val couldNotSayHello = "No se pudo conectar para saludar"
    override val seeTheLinesAbove = "Mirá las líneas de arriba."
    override val closedAtOnce = "La computadora cerró la conexión enseguida"
    override fun codeAccepted(version: Int) = "Código de emparejamiento aceptado, versión del protocolo $version"
    override fun versionMismatch(theirs: Int, ours: Int) = "La computadora usa la versión $theirs y esta app usa la $ours"
    override val updateEither = "Actualizá Koda en la computadora o Kenai en el teléfono."
    override val codeRefused = "Se rechazó el código de emparejamiento"
    override val codeRefusedHint = "La computadora generó un código nuevo (Volver a emparejar). Escaneá el código nuevo."
    override fun answeredWithError(code: String) = "La computadora respondió con un error ($code)"
    override val unexpectedAnswer = "Una respuesta inesperada al saludo"
    override val updateBoth = "Actualizá Koda en la computadora y Kenai en el teléfono."
    override val noAnswerToHello = "Sin respuesta al saludo"
    override val busyOrAsleep = "La computadora está ocupada o dormida. Probá de nuevo."
    override val brokeDuringHello = "La conexión se cortó durante el saludo"

    override fun reportHeader(appVersion: String, androidVersion: String, device: String) = "Kenai para Android $appVersion · Android $androidVersion · $device"
    override fun reportComputer(host: String, via: String) = "Computadora: $host · conectada por: $via"
    override val reportProblem = "PROBLEMA"
    override val reportSkipped = "SIN PROBAR"
    override val viaDirectLink = "el enlace directo"
    override val viaNotConnected = "sin conexión"
    override val noComputer = "ninguna"

    override val demoComputer = "Computadora demo"
    override val demoReading = "Leyendo el proyecto"
    override val demoEditing = "Editando archivos"
    override val demoSearching = "Buscando en el código"
    override val demoWaiting = "Esperando tu aprobación"
    override val demoTesting = "Ejecutando los tests"
    override val demoBuilding = "Compilando"
    override val demoWhichBranch = "¿Qué rama uso?"
    override val demoDone = "Listo"
    override val demoTestsFailed = "Fallaron los tests"
    override val demoRateLimited = "Llegó al límite"
    override val demoIdle = "En reposo"

    override val channelLink = "Conexión"
    override val channelLinkAbout = "Muestra que Kenai está conectado a tu computadora"
    override val channelUpdates = "Novedades"
    override val channelUpdatesAbout = "Un agente terminó, falló o tiene una pregunta"
    override val channelApprovalsQuiet = "Aprobaciones (silenciosas)"
    override val channelApprovalsQuietAbout = "Un pedido en espera, guardado en el panel mientras la píldora lo muestra"
    override val channelApprovals = "Aprobaciones"
    override val channelApprovalsAbout = "Un agente espera tu permiso"
}
