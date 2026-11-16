package com.kenai.android.app

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.PowerManager
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kenai.android.R
import com.kenai.android.core.ChatTab
import com.kenai.android.core.ChatTabState
import com.kenai.android.core.Decision
import com.kenai.android.core.DecisionLog
import com.kenai.android.core.KeyValueStore
import com.kenai.android.core.IslandPlan
import com.kenai.android.core.OverlayChoice
import com.kenai.android.core.StatusPolicy
import com.kenai.android.core.UserSettings
import com.kenai.android.core.OverlayPolicy
import com.kenai.android.core.Arrival
import com.kenai.android.core.BuzzMemory
import com.kenai.android.core.VibratePolicy
import com.kenai.android.core.DecisionOutcomes
import com.kenai.android.core.Answer
import com.kenai.android.core.Outcome
import com.kenai.android.core.OutcomeKind
import com.kenai.android.core.WishStore
import com.kenai.android.core.Pills
import com.kenai.android.ui.IslandOverlay
import com.kenai.android.link.Protocol
import com.kenai.android.link.ChatModel
import com.kenai.android.core.PairingScan
import com.kenai.android.core.ScanDecision
import com.kenai.android.core.ChatHistory
import com.kenai.android.core.ChatBackend
import com.kenai.android.core.ChatModelOption
import com.kenai.android.core.ChatModels
import com.kenai.android.core.ChatSession
import com.kenai.android.core.Words
import com.kenai.android.link.AddressFinder
import com.kenai.android.link.ApprovalRequest
import com.kenai.android.link.QuestionRequest
import com.kenai.android.link.Cancelable
import com.kenai.android.link.Discovery
import com.kenai.android.link.DiscoveryPolicy
import com.kenai.android.link.DiscoveryState
import com.kenai.android.link.PinnedProbe
import com.kenai.android.link.DemoLink
import com.kenai.android.link.Connector
import com.kenai.android.link.DesktopLink
import com.kenai.android.link.PinnedTls
import com.kenai.android.link.LinkClient
import com.kenai.android.link.LinkListener
import com.kenai.android.link.LinkState
import com.kenai.android.link.ChatLinkBackend
import com.kenai.android.link.BatteryHint
import com.kenai.android.link.DiagResult
import com.kenai.android.link.DiagStep
import com.kenai.android.link.Diagnostics
import com.kenai.android.link.Found
import com.kenai.android.link.LanDiagnostics
import com.kenai.android.link.LinkHealth
import com.kenai.android.link.LostReason
import com.kenai.android.link.PairingPayload
import com.kenai.android.link.ReconnectPolicy
import com.kenai.android.link.Wake
import com.kenai.android.link.SessionInfo
import com.kenai.android.sound.SoundPlayer
import com.kenai.android.character.BotState

enum class Mode { NONE, DEMO, PAIRED }

/** The one place the UI, the service and the notification actions share. Created by [KenaiApp]. */
class AppModel(private val context: Context) : LinkListener, ChatBackend.Listener {
    private val main = Handler(Looper.getMainLooper())
    private val store = SecureStore(context)
    val sounds = SoundPlayer(context)
    val notifier = Notifications(context)
    private val haptics = Haptics(context)
    /** What was already buzzed for, so one request or question buzzes once (see [VibratePolicy]). */
    private val buzzMemory = BuzzMemory()

    /** Set only by LaunchActivity (not exported), read by MainActivity: see [com.kenai.android.core.PendingLaunch]. */
    val launch = com.kenai.android.core.PendingLaunch()

    /** Agent tasks finished today, for the Today widget. Kept on this phone only (see [TodayStore]). */
    private val todayCounter = com.kenai.android.core.TodayCounter(TodayStore(context))

    var mode by mutableStateOf(Mode.NONE); private set
    var linkState by mutableStateOf(LinkState.DISCONNECTED); private set
    var desktopName by mutableStateOf<String?>(null); private set
    var sessions by mutableStateOf<List<SessionInfo>>(emptyList()); private set
    var approvals by mutableStateOf<List<ApprovalRequest>>(emptyList()); private set
    /** When each pending request arrived, by this phone's elapsed-time clock, for Home's "Expires in m:ss" (kenai-0.1 S9). */
    var arrivals by mutableStateOf<Map<String, Long>>(emptyMap()); private set
    /** Questions Claude Code asks that this phone may answer (only with the `answers` capability); kept in memory, never written. */
    var questions by mutableStateOf<List<QuestionRequest>>(emptyList()); private set
    var message by mutableStateOf<String?>(null)

    /** What became of the decision just made (kenai-0.1 S9, screen 3; T14): Inicio's hero until dismissed, replaced or 2 min old. */
    private val outcomes = DecisionOutcomes()
    var outcome by mutableStateOf<Outcome?>(null); private set

    /**
     * What became of the answer to a question (kenai-0.1 S9, T19): Home's other «what just happened» card. It takes the slot from a
     * result and a result takes it from it, so Home never shows both; same life: dismissed, replaced, or 2 min old.
     */
    var answerNote by mutableStateOf<Answer?>(null); private set
    private val cardGone = Runnable { outcomes.dismiss(); outcomes.dismissAnswer(); outcome = null; answerNote = null }

    /** Publishes the machine's card and (re)arms its two-minute limit (main thread). */
    private fun showOutcome() {
        outcome = outcomes.current
        answerNote = outcomes.answer
        main.removeCallbacks(cardGone)
        val now = android.os.SystemClock.elapsedRealtime()
        val left = listOfNotNull(
            outcome?.let { it.sinceMs + DecisionOutcomes.SHOW_MS - now },
            answerNote?.let { it.sinceMs + DecisionOutcomes.SHOW_MS - now },
        ).maxOrNull()
        left?.let { main.postDelayed(cardGone, it.coerceAtLeast(0)) }
    }

    /** «Cerrar» on the result, or «Revisar» the request that follows. */
    fun dismissOutcome() {
        outcomes.dismiss()
        showOutcome()
    }

    /**
     * «Respuesta enviada» (or that it could not be sent, or that the question was answered elsewhere): Home's card for the answer to a
     * question, replacing whatever the last decision left there (kenai-0.1 S9, T19; L29 a).
     */
    fun answered(text: String) {
        outcomes.answered(text, android.os.SystemClock.elapsedRealtime())
        showOutcome()
    }

    /** «Cerrar» on the answer. */
    fun dismissAnswer() {
        outcomes.dismissAnswer()
        showOutcome()
    }

    /**
     * «Volver a intentar» on «No se pudo enviar»: the result goes; when the request is still here it is returned with what was
     * decided (Allow goes through the lock again, Deny is sent again). Otherwise the link is retried now and the computer offers the
     * request again once it is back.
     */
    fun retryDecision(): Pair<ApprovalRequest, Boolean>? {
        val o = outcome?.takeIf { it.kind == OutcomeKind.FAILED }
        dismissOutcome()
        val r = o?.let { f -> approvals.firstOrNull { it.fingerprint == f.fingerprint } }
        if (o == null || r == null || o.allow == null || linkState != LinkState.CONNECTED) {
            retryNow()
            return null
        }
        return r to o.allow
    }

    private fun expiresAtWall(fingerprint: String): Long? =
        DecisionOutcomes.expiresAtWall(arrivals[fingerprint], android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis())

    // ── Finding the computer again when its address changed (docs/ANDROID_LINK.md, "Finding the computer") ──
    /** What the search for the computer is doing, for the status line and the hint. */
    var discovery by mutableStateOf(DiscoveryState.IDLE); private set
    /** The pairing the running link uses (with the address in force); the token stays in memory only as long as the link does. */
    @Volatile private var currentPairing: PairingPayload? = null
    private val probeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "kenai-probe").apply { isDaemon = true } }


    private val netWatch = NetworkWatch(context, { up -> main.post { onNetworkChanged(up) } }, { main.post { onWake(Wake.NEW_NETWORK) } })
    private val finder = AddressFinder(
        source = NsdDiscovery(context), probe = PinnedProbe,
        pairing = { currentPairing }, allowed = { mayDiscover() },
        onAddress = { host, port -> main.post { onAddressFound(host, port) } },
        onState = { st -> main.post { discovery = st } },
        scheduler = { delay, task -> val r = Runnable { task() }; main.postDelayed(r, delay); Cancelable { main.removeCallbacks(r) } },
        runner = { task -> probeExecutor.execute { task() } },
    )

    private fun mayDiscover() = DiscoveryPolicy.mayDiscover(
        paired = currentPairing != null, demo = mode == Mode.DEMO, wifiUp = netWatch.wifiUp, linkConnected = linkState == LinkState.CONNECTED,
    )

    private val prefs = context.getSharedPreferences("kenai_ui", Context.MODE_PRIVATE)
    private val kv = object : KeyValueStore {
        override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
        override fun getInt(key: String, default: Int) = prefs.getInt(key, default)
        override fun getFloat(key: String, default: Float) = prefs.getFloat(key, default)
        override fun getString(key: String, default: String) = prefs.getString(key, default) ?: default
        override fun put(key: String, value: Any) {
            prefs.edit().apply {
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                }
            }.apply()
        }
    }

    /** What the user chose in Settings (sound, notices, quiet hours). */
    var settings by mutableStateOf(UserSettings.load(kv)); private set

    fun updateSettings(next: UserSettings) {
        settings = next
        UserSettings.save(kv, next)
        applySound()
    }

    private fun applySound() {
        sounds.enabled = settings.soundOn
        sounds.volume = settings.volume
    }

    init { applySound() }

    /** The phone's own list of decisions. Never sent anywhere. */
    private val decisionLog = DecisionLog(kv)
    var decisions by mutableStateOf(decisionLog.all()); private set

    fun clearDecisions() {
        decisionLog.clear()
        decisions = emptyList()
    }

    fun recordDecision(d: Decision) {
        decisionLog.add(d)
        decisions = decisionLog.all()
    }

    // ── Chat through the computer (docs/ANDROID_LINK.md). The UI comes in the next step. ──────────

    private val chatHistory = ChatHistory(ChatFile(java.io.File(context.filesDir, "chat-history.json")))
    private val chatSession = ChatSession(chatHistory.load())

    /**
     * The chat's one seam: today the link to the computer. A second backend (a provider called straight from the phone,
     * T1a-2) is one new [ChatBackend] class and the line below, where the one in use is chosen: everything else goes
     * through [chatBackend], never through the link.
     */
    private val chatLink = ChatLinkBackend({ link }, this)
    private val chatBackend: ChatBackend get() = chatLink

    /** What the user reads: this phone's own copy, kept in a private file. */
    var chatMessages by mutableStateOf(chatSession.messages); private set

    /** An answer is being waited for. */
    var chatBusy by mutableStateOf(false); private set

    /** The computer offers chat to this phone right now (its switch is on and we are connected). */
    var chatOffered by mutableStateOf(false); private set

    /** The models the backend offers; on the computer the keys never leave it. */
    var chatModels by mutableStateOf<List<ChatModelOption>>(emptyList()); private set

    var chatModel by mutableStateOf<String?>(prefs.getString("chat_model", null)); private set

    /** The computer offers session details (steps, last message, project folder, colour) to this phone. */
    var detailsOffered by mutableStateOf(false); private set
    /** The computer lists the files an agent changed and can send their lines (its switch is on). */
    var diffsOffered by mutableStateOf(false); private set
    /** The Claude and Codex plan usage the computer sent (its switch is on); null: nothing to show. */
    var usage by mutableStateOf<com.kenai.android.link.UsageSnapshot?>(null); private set
    /** The read-only service cards the user allowed on the computer; empty: none. */
    var services by mutableStateOf<List<com.kenai.android.link.ServiceCard>>(emptyList()); private set

    /** What the Chat tab shows: not paired, not reachable, chat off on the computer, no model allowed, or ready. */
    val chatTabState: ChatTabState get() = ChatTab.state(
        paired = mode == Mode.PAIRED, connected = linkState == LinkState.CONNECTED, offered = chatOffered, modelCount = chatModels.size,
    )

    /** Chat is usable: offered, at least one model allowed, connected. */
    val chatAvailable: Boolean get() = chatOffered && chatModels.isNotEmpty() && linkState == LinkState.CONNECTED

    private fun chatChanged(save: Boolean) {
        chatMessages = chatSession.messages
        chatBusy = chatSession.running != null
        if (save) chatHistory.save(chatSession.messages)
    }

    fun chatSelect(id: String) {
        if (chatModels.any { it.id == id }) {
            chatModel = id
            prefs.edit().putString("chat_model", id).apply()
        }
    }

    /** Asks the backend again which models it offers (when the Chat screen opens). */
    fun chatRefreshModels() { chatBackend.models() }

    /** Null when sent, else why not (for a message under the box). */
    fun chatSend(text: String): ChatSession.Refusal? {
        val sent = chatSession.send(text, chatModel, connected = chatAvailable, maxText = chatBackend.maxText)
        return when (sent) {
            is ChatSession.Sent.No -> sent.why
            is ChatSession.Sent.Ok -> {
                chatChanged(save = true)
                if (!chatBackend.send(sent.id, sent.model, sent.text)) {
                    chatSession.onDisconnected()
                    chatChanged(save = true)
                }
                null
            }
        }
    }

    fun chatCancel() {
        chatSession.cancel()?.let { chatBackend.cancel(it) }
        chatChanged(save = true)
    }

    /** Clear: forgets the history here and on the backend's side (on the computer, when that is the backend). */
    fun chatClear() {
        chatSession.cancel()?.let { chatBackend.cancel(it) }
        chatSession.clear()
        chatHistory.clear()
        chatBackend.reset()
        chatChanged(save = false)
    }

    private fun chatLinkLost() {
        detailsOffered = false
        chatOffered = false
        chatModels = emptyList()
        if (chatSession.running != null) {
            chatSession.onDisconnected()
            chatChanged(save = true)
        }
    }

    private val choice = OverlayChoice(object : WishStore {
        override fun read() = prefs.getBoolean("overlay", false)
        // commit(): a tiny file, and it must survive the process being killed while the user is in Settings.
        override fun write(on: Boolean) { prefs.edit().putBoolean("overlay", on).commit() }
    })

    /** The user's wish for the pill over other apps (off until they turn it on). Saved when they tap. */
    var overlayWished by mutableStateOf(choice.wished); private set

    /** The system permission "display over other apps", re-read when the app comes to the front. */
    var overlayPermission by mutableStateOf(false); private set

    /** The app is on screen: it shows everything itself, so the pill stays away. */
    var inForeground = false
        set(value) {
            field = value
            if (value) {
                overlay.hide()
                notifier.cancelAllStatus()
            } else {
                refreshIsland() // the user left the app: an agent at work shows on the island at once
            }
        }

    private val overlay = IslandOverlay(
        context, { android.os.SystemClock.elapsedRealtimeNanos() / 1e6 },
        onAllow = { notifier.openAllow(it) },
        onDeny = { decide(it, allow = false) },
        onOpen = { notifier.openApp() },
    )

    fun overlayPermitted() = overlay.permitted()

    /** What the switch shows: wished and allowed by the system. */
    val overlayOn: Boolean get() = overlayWished && overlayPermission

    fun refreshOverlayPermission() { overlayPermission = overlay.permitted() }

    /** The user tapped the switch: remember the choice right now (see [OverlayChoice]). */
    fun useOverlay(on: Boolean) {
        choice.choose(on)
        overlayWished = on
        refreshOverlayPermission()
        refreshIsland()
    }

    private fun islandAllowed(): Boolean {
        val connected = mode != Mode.PAIRED || linkState == LinkState.CONNECTED
        val why = OverlayPolicy.blocker(choice.wished, overlay.permitted(), inForeground)
            ?: if (!connected) "not connected to a computer" else null
        if (why != null) Log.d("KenaiOverlay", "no island: $why")
        return why == null
    }

    /**
     * What the island shows right now (an agent at work, a question, a request), from the current
     * sessions and requests. When it may not show at all it is removed completely.
     */
    /** What the notification, the widget and the tile say right now. */
    fun glance(): com.kenai.android.core.Glance = com.kenai.android.core.GlanceBuilder.build(
        mode != Mode.NONE || isPaired, linkState, desktopName, sessions, approvals.size, this::agentName,
    )

    /** What the Widgets draw now: the glance, the sessions, the plan usage the computer sent, today's finished tasks and the computer's name. */
    fun widgetContent(g: com.kenai.android.core.Glance = glance()): com.kenai.android.core.WidgetContent =
        com.kenai.android.core.WidgetData.build(
            g, sessions, this::agentName, usage, todayCounter.count(), System.currentTimeMillis(), desktopName,
            // What the phone holds for you, so the alert's count and rows agree (kenai-0.1 T18).
            requests = approvals.map { it.pillId }, questions = questions.map { it.pillId },
        )

    /** The small surfaces follow every change of sessions, requests and link; none of them runs a timer. */
    private fun pushGlance() {
        val g = glance()
        notifier.updateOngoing(this, g)
        GlanceWidget.update(context, g)
        WidgetHub.update(context, widgetContent(g))
        GlanceTile.requestUpdate(context)
    }

    private fun refreshIsland() {
        pushGlance()
        if (!islandAllowed()) {
            overlay.hide()
            return
        }
        overlay.setActive(IslandPlan.active(sessions, approvals, allowed = true, this::agentName))
    }

    /**
     * Allow was tapped on a notification before the link was back (the app had been closed): the
     * desktop offers the request again as soon as it reconnects, and the biometric prompt opens then.
     */
    var wantAllow by mutableStateOf<String?>(null)

    private val expiry = HashMap<String, Runnable>()
    private var link: DesktopLink? = null
    private val lastState = HashMap<String, BotState>()

    /** The name the user knows the agent by: the session's own name, else the pill catalog. */
    fun agentName(pillId: String): String =
        sessions.firstOrNull { it.pillId == pillId }?.agent?.takeIf { it.isNotBlank() } ?: Pills.byId(pillId)?.name ?: Words.current().anAgent

    val isPaired get() = store.loadPairing() != null

    /** Called at launch and by the service: reconnects to the stored desktop, if any. */
    fun resume() {
        if (mode == Mode.DEMO || link != null) return
        val p = store.loadPairing() ?: return
        connect(p)
    }

    /**
     * A pairing link waiting for the user's OK: read from the QR code, or opened from the camera app. Held in
     * memory only while the question is on screen; it is never logged and goes nowhere but into [pair].
     */
    var pairRequest by mutableStateOf<String?>(null); private set

    /** True if [text] is a Kenai pairing link (the user is now asked to confirm); false for any other text. */
    fun requestPairing(text: String?): Boolean {
        val d = PairingScan.decide(text)
        if (d !is ScanDecision.Pairing) return false
        pairRequest = d.link
        return true
    }

    fun confirmPairing() {
        val link = pairRequest ?: return
        pairRequest = null
        pair(link)
    }

    fun cancelPairing() { pairRequest = null }

    fun pair(text: String): Boolean {
        val p = PairingPayload.parse(text)
        if (p == null) { message = context.getString(R.string.msg_bad_link); return false }
        stopLink()
        store.savePairing(p)
        connect(p)
        return true
    }

    private fun connect(p: PairingPayload) {
        mode = Mode.PAIRED
        desktopName = p.desktopName.ifBlank { p.host }
        currentPairing = p
        link = newLink(p).also { it.start() }
        netWatch.start()
        screenWatch.start()
        KeepAliveAlarm.arm(context)
        LinkService.start(context)
    }

    /** The saved address is replaced; the token and the pinned certificate are the same. The link restarts at once. */
    private fun useAddress(host: String, port: Int) {
        val p = currentPairing ?: return
        val q = Discovery.withAddress(p, host, port)
        store.savePairing(q)
        currentPairing = q
        restartLink(q)
    }

    /** A link for this pairing: the direct connection on the local network (Kenai is LAN only). */
    private fun newLink(p: PairingPayload): LinkClient {
        val connector: Connector = PinnedTls.connector(p)
        return LinkClient(p, Build.MODEL ?: "Android", this, connector = connector)
    }

    private fun restartLink(p: PairingPayload) {
        link?.stop()
        link = newLink(p).also { it.start() }
    }

    /** A computer matching the paired fingerprint answered with the pinned certificate (checked by a bare TLS handshake). */
    private fun onAddressFound(host: String, port: Int) {
        if (mode != Mode.PAIRED || currentPairing == null) return
        Log.d("KenaiDiscovery", "address updated")
        useAddress(host, port)
    }

    // ── Keeping the link alive while the phone sleeps (docs/ANDROID_LINK.md, "Keeping the link alive") ──

    /** What happened to the link: when it was lost and why, and how often the screen had been off when it was. */
    var health by mutableStateOf(LinkHealth(dropsWhileScreenOff = kv.getInt("link_drops_off", 0))); private set
    /** The screen was off at some point since the link last came up. */
    @Volatile private var screenWasOff = false
    private val power = context.getSystemService(PowerManager::class.java)
    private val screenWatch = ScreenWatch(context) { on -> main.post { if (on) onWake(Wake.SCREEN_ON) else screenWasOff = true } }
    private var batteryTick by mutableStateOf(0)
    var batterySnoozedAt by mutableStateOf(kv.getString("battery_snoozed_at", "").toLongOrNull()); private set

    /** The screen turned on, a network appeared, or the alarm fired: look at the link now (rules in [ReconnectPolicy]). */
    private fun onWake(event: Wake) {
        when (ReconnectPolicy.decide(event, mode == Mode.PAIRED, linkState == LinkState.CONNECTED, netWatch.wifiUp)) {
            ReconnectPolicy.Action.RETRY_NOW -> link?.retryNow()
            ReconnectPolicy.Action.CHECK_NOW -> link?.checkNow()
            ReconnectPolicy.Action.NONE -> {}
        }
    }

    /** "Try again" on Home while the computer is away: reconnect now instead of waiting out the backoff. */
    fun retryNow() {
        if (mode != Mode.PAIRED) return
        if (link == null) resume() else link?.retryNow()
        if (mayDiscover()) finder.request()
    }

    /** The periodic alarm. If Android had stopped the app, this is also what brings the link back (the pairing is stored). */
    fun keepAliveTick() {
        if (mode == Mode.NONE) resume()
        if (mode != Mode.PAIRED) return
        onWake(Wake.ALARM)
        KeepAliveAlarm.arm(context)
    }

    /** Suggest the battery setting only when the link ended on its own after the screen had been off and the app is still restricted. */
    val batteryHintVisible: Boolean get() {
        batteryTick // read so a change of the setting redraws Home
        return BatteryHint.shouldShow(mode == Mode.PAIRED, health, power?.isIgnoringBatteryOptimizations(context.packageName) == true, batterySnoozedAt, System.currentTimeMillis())
    }
    val batterySteps: List<String> get() = BatteryHint.steps(BatteryHint.isSamsung(Build.MANUFACTURER))
    /** The user may have changed the setting in Android's screen: look again when the app comes back. */
    fun refreshBattery() { batteryTick++ }
    fun snoozeBatteryHint() {
        val now = System.currentTimeMillis()
        kv.put("battery_snoozed_at", now.toString())
        batterySnoozedAt = now
    }

    // ── "Can't connect?" ──
    var diagResults by mutableStateOf<List<Pair<DiagStep, DiagResult>>>(emptyList()); private set
    var diagRunning by mutableStateOf(false); private set

    fun runDiagnostics() {
        val p = currentPairing ?: return
        if (diagRunning) return
        diagRunning = true
        diagResults = emptyList()
        probeExecutor.execute {
            val env = LanDiagnostics(
                p, wifiUp = { netWatch.isWifiUp() }, discover = { browse(4_000) },
            )
            Diagnostics.run(env) { step, result -> main.post { diagResults = diagResults + (step to result) } }
            main.post { diagRunning = false }
        }
    }

    /** Listens for the computer's announcement for [ms] on a separate browser, so the link's own search is not disturbed. */
    private fun browse(ms: Long): List<Found> {
        val seen = java.util.concurrent.CopyOnWriteArrayList<Found>()
        val source = NsdDiscovery(context)
        source.start { seen.add(it) }
        try { Thread.sleep(ms) } finally { source.stop() }
        return seen.toList()
    }

    /** The text the user can copy: the checks and what they found, with no code, fingerprint, name or full address. */
    fun diagnosticsReport(): String {
        val p = currentPairing
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        val w = Words.current() // the copied report is in the phone's language, as the screen (kenai-0.1 S6)
        val via = when {
            linkState != LinkState.CONNECTED -> w.viaNotConnected
            else -> w.viaDirectLink
        }
        val host = p?.let { Diagnostics.maskHost(it.host) } ?: w.noComputer // maskHost's words are the phone's too
        return Diagnostics.report(version, Build.VERSION.RELEASE, "${Build.MANUFACTURER} ${Build.MODEL}", diagResults, host, via, w)
    }

    private fun onNetworkChanged(wifiUp: Boolean) {
        if (mode != Mode.PAIRED) return
        if (!wifiUp) { finder.stop(); return } // no Wi-Fi: nothing to scan
        link?.retryNow() // do not wait out the backoff on a new network
        if (mayDiscover()) finder.networkChanged()
    }

    /**
     * "Enter address manually": a host or host:port typed by the user. The pairing, the token and the pinned
     * certificate are unchanged, so a wrong or hostile address still cannot get past the certificate check.
     */
    fun setAddress(text: String): Boolean {
        val (host, port) = Discovery.parseAddress(text) ?: return false
        if (currentPairing == null) return false
        useAddress(host, port)
        return true
    }

    fun startDemo() {
        stopLink()
        mode = Mode.DEMO
        link = DemoLink(this).also { it.start() }
    }

    /** Forgets the desktop: closes the link and deletes the stored secret. */
    fun unpair() {
        stopLink()
        store.clearPairing()
        LinkService.stop(context)
    }

    fun stopDemo() = stopLink()

    private fun stopLink() {
        closeDiff()
        usage = null
        services = emptyList()
        finder.stop()
        netWatch.stop()
        screenWatch.stop()
        KeepAliveAlarm.cancel(context)
        health = health.recovered()
        currentPairing = null
        link?.stop()
        link = null
        mode = Mode.NONE
        linkState = LinkState.DISCONNECTED
        desktopName = null
        sessions = emptyList()
        dropApprovals()
        chatLinkLost()
        wantAllow = null
        lastState.clear()
        notifier.cancelAllStatus()
        dismissOutcome() // a result is about this link only
        dismissAnswer() // and so is the answer Home was showing
    }

    /** True if the decision was sent (or applied to the demo). Allow is gated by the biometric prompt in the UI. */
    fun decide(fingerprint: String, allow: Boolean): Boolean {
        val request = approvals.firstOrNull { it.fingerprint == fingerprint }
        if (allow && request != null && !com.kenai.android.core.ApprovalSheetPlan.canAllowHere(request)) {
            // Whatever asked (a stale notification, a test): a command that was cut is never allowed from here. It stays pending.
            message = context.getString(R.string.approval_on_computer)
            return false
        }
        val ok = link?.decide(fingerprint, allow) ?: false
        val now = android.os.SystemClock.elapsedRealtime()
        if (ok && request != null) {
            // The phone's own history never keeps the command: a label and at most the program's name.
            recordDecision(Decision(agentName(request.pillId), request.tool, com.kenai.android.core.SafeText.requestLabel(request.tool, request.command), allow, System.currentTimeMillis()))
            // Sending until the computer says it got it (onApprovalResolved); the buzz waits for that too.
            outcomes.decided(request, allow, now, System.currentTimeMillis(), expiresAtWall(fingerprint))
            showOutcome()
        }
        if (!ok) {
            // Home says it in its result («Ese pedido ya no está pendiente»); the message is for a request Home did not know.
            if (request != null) {
                outcomes.refused(request, now, System.currentTimeMillis(), expiresAtWall(fingerprint))
                showOutcome()
            } else {
                message = context.getString(R.string.msg_not_pending)
            }
            removeApproval(fingerprint) // it cannot be answered any more: no card or pill that lingers
        }
        return ok
    }

    /** Every card goes: the desktop offers a request again if it is still pending once we reconnect. */
    private fun dropApprovals() {
        questions.forEach { expiry.remove(it.fingerprint)?.let { r -> main.removeCallbacks(r) } }
        questions = emptyList()
        overlay.hide()
        expiry.values.forEach { main.removeCallbacks(it) }
        expiry.clear()
        approvals.forEach { notifier.cancelApproval(it.fingerprint) }
        approvals = emptyList()
        arrivals = emptyMap()
        refreshIsland()
    }

    private fun removeApproval(fingerprint: String) {
        expiry.remove(fingerprint)?.let { main.removeCallbacks(it) }
        approvals = approvals.filter { it.fingerprint != fingerprint }
        arrivals = arrivals - fingerprint
        notifier.cancelApproval(fingerprint)
        refreshIsland()
    }

    // ── LinkListener (link thread) ───────────────────────────────────────────

    override fun onState(state: LinkState) {
        main.post {
            linkState = state
            if (state == LinkState.CONNECTED) {
                health = health.recovered()
                screenWasOff = power?.isInteractive == false // the screen is off right now: this link starts its life asleep
            }
            if (state == LinkState.CONNECTED) finder.connected() // found it (or never lost it): stop looking
            // A decision still being sent did not get its confirmation: «No se pudo enviar» (the request may come back).
            if (state != LinkState.CONNECTED) { outcomes.linkLost(); showOutcome() }
            // A card for a request we can no longer answer would only produce "no longer pending".
            if (state != LinkState.CONNECTED && mode == Mode.PAIRED) dropApprovals()
            if (state != LinkState.CONNECTED) chatLinkLost()
            refreshIsland()
        }
    }

    override fun onWelcome(desktopName: String, os: String) {
        main.post { if (mode != Mode.DEMO && desktopName.isNotBlank()) this.desktopName = desktopName }
    }

    override fun onCaps(caps: Set<String>) {
        main.post {
            chatOffered = Protocol.CAP_CHAT in caps
            detailsOffered = Protocol.CAP_DETAILS in caps
            diffsOffered = Protocol.CAP_DIFFS in caps
            if (Protocol.CAP_USAGE !in caps) usage = null
            pushGlance() // the Usage Ring says to turn it on
            if (Protocol.CAP_SERVICES !in caps) services = emptyList()
        }
    }

    override fun onUsage(usage: com.kenai.android.link.UsageSnapshot) {
        main.post {
            this.usage = usage.takeIf { it.claude != null || it.codex != null }
            pushGlance() // the Usage Ring follows it
        }
    }

    override fun onServices(cards: List<com.kenai.android.link.ServiceCard>) {
        main.post { services = cards }
    }

    override fun onChatModels(models: List<ChatModel>) {
        chatLink.onChatModels(models)
    }

    override fun onChatDelta(id: String, text: String) {
        chatLink.onChatDelta(id, text)
    }

    override fun onChatDone(id: String, text: String?) {
        chatLink.onChatDone(id, text)
    }

    override fun onChatError(id: String, reason: String, message: String) {
        chatLink.onChatError(id, reason, message)
    }

    // ── ChatBackend.Listener (the link's thread, marshalled to the main one) ─────────────────────

    override fun onModels(models: List<ChatModelOption>) {
        main.post {
            chatModels = models
            chatModel = ChatModels.pick(models, chatModel)
        }
    }

    override fun onDelta(id: String, text: String) {
        main.post { chatSession.onDelta(id, text); chatChanged(save = false) }
    }

    override fun onDone(id: String, text: String?) {
        main.post { chatSession.onDone(id, text); chatChanged(save = true) }
    }

    override fun onError(id: String, reason: String, message: String) {
        main.post { chatSession.onError(id, reason); chatChanged(save = true) }
    }

    override fun onSessions(sessions: List<SessionInfo>) {
        main.post {
            this.sessions = sessions
            val minute = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            for (s in sessions) {
                val prev = lastState.put(s.pillId, s.state)
                // A task just finished (not one already finished when the link came back), on a real computer: the demo loops and never counts.
                if (mode == Mode.PAIRED && prev != null && prev != BotState.FINISHED && s.state == BotState.FINISHED) todayCounter.increment()
                val name = agentName(s.pillId)
                val sound = com.kenai.android.character.StateColors.STATE_SOUND[s.state]
                if (sound != null && StatusPolicy.stateSoundWanted(prev, s.state, settings)) sounds.play(sound)
                val a = StatusPolicy.announce(prev, s.state, settings, minute, inForeground)
                if (a.sound && sound != null) sounds.play(sound)
                if (a.notify) notifier.showStatus(s.pillId, name, s.state, s.statusText)
                IslandPlan.flash(prev, s, settings, minute, islandAllowed(), this::agentName)?.let { overlay.flash(it) }
                // A question also arrives as this change (always, even without the `answers` capability): one buzz with onQuestion.
                if (VibratePolicy.questionArrived(prev, s.state)) buzzOnArrival(Arrival.QUESTION, s.pillId, null)
                // The agent moved on: the notice about the last state is stale.
                if (prev != s.state && s.state !in NEWSWORTHY) notifier.cancelStatus(s.pillId)
            }
            lastState.keys.retainAll(sessions.map { it.pillId }.toSet())
            refreshIsland()
        }
    }

    override fun onApproval(request: ApprovalRequest) {
        main.post {
            approvals = approvals.filter { it.fingerprint != request.fingerprint } + request
            // A new request (or one offered again after a failure) takes Home from the last result.
            outcomes.arrived(request.fingerprint)
            showOutcome()
            // The same clock as the expiry below: offered again after a reconnection, it counts from now, as the removal does.
            arrivals = arrivals + (request.fingerprint to android.os.SystemClock.elapsedRealtime())
            // One announcement only: the island if it can show, else the heads-up notification.
            refreshIsland()
            val alert = OverlayPolicy.approvalAlert(overlay.isShowingRequest())
            notifier.showApproval(request, agentName(request.pillId), alert)
            // The heads-up already vibrates; the island (quiet channel) does not, so the phone buzzes then.
            buzzOnArrival(Arrival.APPROVAL, request.pillId, request.fingerprint, alert)
            // Offered for 120 s from now, by this phone's clock (the notification times out then too).
            expiry.remove(request.fingerprint)?.let { main.removeCallbacks(it) }
            val gone = Runnable { requestExpired(request.fingerprint) }
            expiry[request.fingerprint] = gone
            main.postDelayed(gone, Protocol.APPROVAL_TTL_MS)
        }
    }

    override fun onQuestion(request: QuestionRequest) {
        main.post {
            questions = questions.filter { it.fingerprint != request.fingerprint } + request
            // The alert widget lists the phone's questions: it follows their arrival and their going (T18).
            pushGlance()
            buzzOnArrival(Arrival.QUESTION, request.pillId, request.fingerprint)
            // Offered for 120 s from now, like an approval; the computer drops it at 115 s.
            expiry.remove(request.fingerprint)?.let { main.removeCallbacks(it) }
            val gone = Runnable { removeQuestion(request.fingerprint) }
            expiry[request.fingerprint] = gone
            main.postDelayed(gone, Protocol.APPROVAL_TTL_MS)
        }
    }

    /** The phone's 120 s for a request ran out: Home says so if it was on screen, or if its decision was still unconfirmed. */
    private fun requestExpired(fingerprint: String) {
        val shown = com.kenai.android.core.ApprovalSheetPlan.hero(approvals)?.takeIf { it.fingerprint == fingerprint }
        outcomes.expired(fingerprint, shown, android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), expiresAtWall(fingerprint))
        showOutcome()
        removeApproval(fingerprint)
    }

    private fun removeQuestion(fingerprint: String) {
        expiry.remove(fingerprint)?.let { main.removeCallbacks(it) }
        questions = questions.filter { it.fingerprint != fingerprint }
        pushGlance()
    }

    /** One short buzz for an arriving request or question, when the user wants it (main thread). */
    private fun buzzOnArrival(kind: Arrival, pillId: String, fingerprint: String?, alert: OverlayPolicy.ApprovalAlert? = null) {
        val now = android.os.SystemClock.elapsedRealtime()
        val buzz = VibratePolicy.onArrival(kind, settings, alert, buzzMemory.alreadyBuzzed(kind, pillId, fingerprint, now))
        buzzMemory.remember(kind, pillId, fingerprint, now)
        if (buzz) haptics.buzz()
    }

    /** The question of this agent, if one waits for the phone's answer. */
    fun questionFor(pillId: String): QuestionRequest? = questions.firstOrNull { it.pillId == pillId }

    /**
     * Sends the picks; the lock check has already happened in the UI (BiometricGate). True if it was sent. What was
     * picked is not logged, not stored and not put in the history.
     */
    fun answerQuestion(fingerprint: String, picks: List<List<String>>): Boolean {
        val sent = link?.answer(fingerprint, picks) ?: false
        if (sent) removeQuestion(fingerprint)
        return sent
    }

    // ── A file's change (cap `diffs`) ──────────────────────────────────────────────────

    private val diffAssembler = com.kenai.android.core.DiffAssembler()
    private val diffTimeout = Runnable {
        (diffState as? com.kenai.android.core.DiffState.Loading)?.let { diffState = com.kenai.android.core.DiffState.Failed(it.name) }
        diffAssembler.cancel()
    }

    /** What the file-change sheet shows; Idle closes it. The lines are held only while it is open. */
    var diffState by mutableStateOf<com.kenai.android.core.DiffState>(com.kenai.android.core.DiffState.Idle); private set

    /** Opens the sheet for a file of a session's list and asks the computer for its lines. */
    fun openDiff(pillId: String, file: com.kenai.android.link.FileChange) {
        diffState = com.kenai.android.core.DiffState.Loading(pillId, file.id, file.name)
        diffAssembler.expect(pillId, file.id)
        main.removeCallbacks(diffTimeout)
        if (link?.getDiff(pillId, file.id) == true) main.postDelayed(diffTimeout, DIFF_WAIT_MS)
        else diffState = com.kenai.android.core.DiffState.Failed(file.name)
    }

    /** Closes the sheet and forgets the lines. */
    fun closeDiff() {
        main.removeCallbacks(diffTimeout)
        diffAssembler.cancel()
        diffState = com.kenai.android.core.DiffState.Idle
    }

    override fun onDiff(part: com.kenai.android.link.ServerMsg.Diff) {
        main.post {
            val done = diffAssembler.accept(part) ?: return@post
            main.removeCallbacks(diffTimeout)
            if (diffState is com.kenai.android.core.DiffState.Loading) diffState = com.kenai.android.core.DiffState.Ready(done)
        }
    }

    override fun onApprovalResolved(fingerprint: String) {
        main.post {
            // Before the card goes: was it the request on Home, and when would it have expired?
            val shown = com.kenai.android.core.ApprovalSheetPlan.hero(approvals)?.takeIf { it.fingerprint == fingerprint }
            val o = outcomes.resolved(fingerprint, shown, android.os.SystemClock.elapsedRealtime(), System.currentTimeMillis(), expiresAtWall(fingerprint))
            // The computer got the decision: one tap for allow, two for deny (only now, never on the tap; only with the switch on).
            val pattern = o?.let { VibratePolicy.onDecision(it.kind, settings) }
            if (o != null && pattern != null) {
                haptics.buzz(pattern)
                outcomes.buzzed(fingerprint, VibratePolicy.taps(pattern))
            }
            showOutcome()
            removeQuestion(fingerprint)
            removeApproval(fingerprint)
            if (wantAllow == fingerprint) wantAllow = null
        }
    }

    override fun onDecisionNotSent(fingerprint: String) {
        main.post { outcomes.sendFailed(fingerprint); showOutcome() }
    }

    /** A link that had worked ended on its own: remembered with its reason, so Home can say what happened. */
    override fun onLinkLost(reason: LostReason) {
        main.post {
            health = health.lost(System.currentTimeMillis(), reason, screenWasOff)
            kv.put("link_drops_off", health.dropsWhileScreenOff)
        }
    }

    override fun onConnectFailed(consecutive: Int) {
        main.post { health = health.failedAttempt(consecutive) }
        // The saved address failed: now (and only now) look for the computer on the network.
        if (consecutive >= DiscoveryPolicy.FAILURES_BEFORE_DISCOVERY) main.post { if (mayDiscover()) finder.request() }
    }

    override fun onError(code: String, message: String) {
        main.post {
            // A decision that could not be sent is already Home's result («No se pudo enviar»): no second message.
            if (code == "send" && outcome?.kind == OutcomeKind.FAILED) return@post
            this.message = if (code == "auth") context.getString(R.string.msg_rejected)
            else "${context.getString(R.string.msg_link_error)}: $message"
        }
    }

    private companion object {
        /** How long the sheet waits for the lines of a file before saying it could not load them. */
        const val DIFF_WAIT_MS = 10_000L
        val NEWSWORTHY = setOf(BotState.QUESTION, BotState.ERROR, BotState.FINISHED, BotState.RATELIMIT)
    }
}
