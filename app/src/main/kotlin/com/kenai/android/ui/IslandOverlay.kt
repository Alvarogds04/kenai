package com.kenai.android.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kenai.android.core.IconKind
import com.kenai.android.core.MotionSpec
import com.kenai.android.core.IslandSurface
import com.kenai.android.core.Tokens
import com.kenai.android.core.TypeScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kenai.android.R
import com.kenai.android.core.CirclePlan
import com.kenai.android.core.PendingPlan
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kenai.android.core.IslandBox
import com.kenai.android.core.IslandGeometry
import com.kenai.android.core.IslandSpec
import com.kenai.android.core.IslandTimeline
import com.kenai.android.core.OverlayPolicy
import com.kenai.android.core.PxRect
import com.kenai.android.core.Tracked
import com.kenai.android.character.BotState
import com.kenai.android.character.CharacterEngine
import com.kenai.android.character.CharacterView

/**
 * The notch of the desktop, for the phone: a black island hanging from the top of the screen,
 * centred on the camera cut-out, over whatever app is open. It drops while an agent works, shows
 * how it ended for 10 s, then goes back up with the PC's motion (spring when it grows, a 340 ms
 * curve when it shrinks). A permission request or a question keeps it open.
 *
 * When it opens and closes is decided by [IslandTimeline] (pure, tested); this class only draws it
 * and keeps one timer, for the next deadline. Nothing exists while it is hidden: the window is added
 * when it opens and removed when it has gone up, so there is no timer and no frame loop.
 * Needs the user's one-time "display over other apps" permission.
 *
 * A question whose frame is on this phone ([IslandSpec.canAnswerHere]) opens THAT question on a tap; every other tap opens the app.
 * Nothing is answered or allowed here: those decisions stay behind the app's own screen lock (KN-04 S10).
 */
class IslandOverlay(
    private val context: Context,
    private val clock: () -> Double,
    private val onAllow: (fingerprint: String) -> Unit,
    private val onDeny: (fingerprint: String) -> Unit,
    private val onOpen: () -> Unit,
    private val onAnswer: (pillId: String) -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val timeline = IslandTimeline(clock)

    private var view: ComposeView? = null
    private var owner: Owner? = null
    private var box: IslandBox? = null
    private var shownAt = 0.0

    private var spec by mutableStateOf<IslandSpec?>(null)
    private val width = Tracked(0.0)
    private val height = Tracked(0.0)
    private val radius = Tracked(0.0)
    /** Bumped whenever a new target is set, to (re)start the frame loop that only runs while moving. */
    private var motionKey by mutableIntStateOf(0)

    private val deadline = Runnable { safely { timeline.tick(); sync() } }

    fun permitted(): Boolean = Settings.canDrawOverlays(context)

    /** Something is going on right now: an agent works, a question waits, a request waits. Null: nothing. */
    fun setActive(next: IslandSpec?) = safely {
        timeline.setActive(next)
        sync()
    }

    /** An agent finished, failed or hit a limit: shown for 10 s, then the island goes up. */
    fun flash(next: IslandSpec) = safely {
        timeline.flash(next)
        sync()
    }

    /** Close at once, without animation (Kenai came to the front, the switch went off, the link dropped). */
    fun hide() = safely {
        timeline.dismiss()
        removeView()
    }

    /** True when the island is on screen showing a request: the notification can then stay quiet. */
    fun isShowingRequest(): Boolean =
        view != null && timeline.phase == IslandTimeline.Phase.OPEN && timeline.current?.kind == IslandSpec.Kind.APPROVAL &&
            seenByUser()

    /** The island is a window of its own: nobody sees it with the screen off, in a screen saver or behind the lock screen. */
    private fun seenByUser(): Boolean = runCatching {
        context.getSystemService(android.os.PowerManager::class.java).isInteractive &&
            !context.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
    }.getOrDefault(false)

    /** The island is a nicety on top of the app: nothing that goes wrong with the window may reach the caller. */
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "island failed: ${e.javaClass.simpleName}")
            runCatching { timeline.dismiss(); removeView() }
        }
    }

    // ── From the timeline to the window ──────────────────────────────────────

    private fun sync() {
        main.removeCallbacks(deadline)
        val phase = timeline.phase
        val current = timeline.current
        Log.d(TAG, "phase=$phase content=${current?.kind}")
        if (phase == IslandTimeline.Phase.HIDDEN || current == null) {
            removeView()
            return
        }
        val opening = view == null
        if (!ensureView()) return
        val b = box ?: return
        if (spec?.kind != current.kind || spec?.fingerprint != current.fingerprint) shownAt = clock()
        spec = current
        val now = clock()
        if (opening) {
            width.jump(b.notchWidth.toDouble()); height.jump(0.0); radius.jump(b.cornerSmall.toDouble())
        }
        val reduced = MotionSpec.isReduced(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
        if (phase == IslandTimeline.Phase.OPEN) {
            val (w, h) = IslandGeometry.sizeFor(b, current.kind)
            val corner = (if (current.kind == IslandSpec.Kind.WORKING) b.cornerSmall else b.cornerLarge).toDouble()
            // "Remove animations": no spring, the island simply appears at its size.
            if (reduced) { width.jump(w.toDouble()); height.jump(h.toDouble()); radius.jump(corner) }
            else { width.goTo(w.toDouble(), now); height.goTo(h.toDouble(), now); radius.goTo(corner, now) }
        } else if (reduced) {
            width.jump(b.notchWidth.toDouble()); height.jump(0.0); radius.jump(b.cornerSmall.toDouble())
        } else {
            // Back up into the notch: same curve as the PC.
            width.curveTowards(b.notchWidth.toDouble(), now); height.curveTowards(0.0, now); radius.curveTowards(b.cornerSmall.toDouble(), now)
        }
        motionKey++
        timeline.nextDeadline()?.let { main.postDelayed(deadline, (it - clock()).toLong().coerceAtLeast(0)) }
    }

    private fun removeView() {
        main.removeCallbacks(deadline)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        owner?.destroy()
        owner = null
        spec = null
    }

    private fun measure(): IslandBox {
        val density = context.resources.displayMetrics.density
        val metrics = wm.currentWindowMetrics
        val insets = metrics.windowInsets
        val cutout = insets.displayCutout?.boundingRectTop?.let { PxRect(it.left, it.top, it.right, it.bottom) }
        val statusBar = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
            .takeIf { it > 0 } ?: (24 * density).toInt()
        return IslandGeometry.place(metrics.bounds.width(), density, cutout, statusBar)
    }

    private fun ensureView(): Boolean {
        if (view != null) return true
        val b = measure()
        box = b
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable and not modal: touches outside the island go to the app underneath. It may
            // extend into the display cut-out so its black merges with the camera hole.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = b.centerOffsetX
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val lifecycleOwner = Owner()
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent { Island() }
        }
        return try {
            wm.addView(v, params)
            owner = lifecycleOwner
            view = v
            true
        } catch (e: Exception) {
            Log.w(TAG, "cannot add the window: ${e.javaClass.simpleName}") // permission taken away meanwhile
            lifecycleOwner.destroy()
            false
        }
    }

    /** Every tap goes through here: one made in the first moments is ignored (see OverlayPolicy). */
    private fun tap(what: String, action: () -> Unit) {
        if (!OverlayPolicy.tapAccepted(shownAt, clock())) {
            Log.d(TAG, "ignored an early tap on $what")
            return
        }
        action()
    }

    // ── What is drawn ────────────────────────────────────────────────────────

    // The island is black: it is the camera hole grown. Colours are the shared always-dark ones.
    private val ink = Color(IslandSurface.BLACK)
    private val dim = Color(IslandSurface.TEXT_DIM)

    @Composable
    private fun Island() {
        var frame by remember { mutableLongStateOf(0L) }
        // Runs only while something is moving; when it stops, nothing redraws and nothing is scheduled.
        LaunchedEffect(motionKey) {
            var last = 0L
            while (width.animating || height.animating || radius.animating) {
                androidx.compose.runtime.withFrameNanos { ns ->
                    val dt = if (last == 0L) 1.0 / 60 else ((ns - last) / 1e9).coerceIn(0.0, 0.1)
                    last = ns
                    val now = clock()
                    width.step(dt, now); height.step(dt, now); radius.step(dt, now)
                    frame = ns
                }
            }
            if (timeline.phase == IslandTimeline.Phase.RETRACTING) {
                timeline.retractFinished()
                removeView()
            }
        }
        frame // read, so the island redraws as it moves
        val b = box ?: return
        val s = spec ?: return
        val density = LocalDensity.current
        fun px(v: Double): Dp = with(density) { v.toFloat().toDp() }
        val topInset = px(b.topInset.toDouble())
        val (targetW, targetH) = IslandGeometry.sizeFor(b, s.kind)
        // The content fades in as the island opens and out as it goes up.
        val open = ((height.value / targetH.toDouble()) - 0.35) / 0.55
        val corner = px(radius.value)
        val ear = IslandGeometry.EAR_DP.dp
        CompositionLocalProvider(LocalTokens provides Tokens.DARK) {
            // The island plus a concave flare on each side: its top is flush with the screen's edge and
            // merges with the camera hole, instead of hanging as a bar below it.
            Box(Modifier.width(px(width.value) + ear * 2).height(px(height.value))) {
                Canvas(Modifier.matchParentSize()) { drawFlares(ear.toPx().coerceAtMost(size.height), ink) }
                Box(
                    Modifier.align(Alignment.TopCenter).width(px(width.value)).fillMaxHeight()
                        .clip(RoundedCornerShape(bottomStart = corner, bottomEnd = corner)).background(ink),
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(top = topInset).alpha(open.coerceIn(0.0, 1.0).toFloat()),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(Modifier.requiredWidth(px(targetW.toDouble())).wrapContentHeight(Alignment.Top, unbounded = true)) {
                            when (s.kind) {
                                IslandSpec.Kind.WORKING -> WorkingStrip(s)
                                IslandSpec.Kind.APPROVAL -> RequestCard(s)
                                else -> ResultCard(s)
                            }
                        }
                    }
                }
            }
        }
    }

    /** The two flares: each is the square beside the island minus a quarter circle, so the black curves out to the screen's top edge. */
    private fun DrawScope.drawFlares(e: Float, color: Color) {
        if (e <= 0f) return
        val w = size.width
        val left = Path().apply {
            moveTo(0f, 0f); lineTo(e, 0f); lineTo(e, e)
            arcTo(Rect(Offset(0f, e), e), 0f, -90f, false)
            close()
        }
        val right = Path().apply {
            moveTo(w, 0f); lineTo(w - e, 0f); lineTo(w - e, e)
            arcTo(Rect(Offset(w, e), e), 180f, 90f, false)
            close()
        }
        drawPath(left, color)
        drawPath(right, color)
    }

    @Composable
    private fun WorkingStrip(s: IslandSpec) {
        val engine = remember(s.state) { CharacterEngine(clock).apply { setState(s.state, force = true) } }
        Row(
            Modifier.fillMaxWidth().clickable { tap("the working strip") { onOpen() } }.padding(horizontal = 18.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CharacterView(engine, Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.agent, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.text.isNotBlank()) Text(s.text, color = dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun ResultCard(s: IslandSpec) {
        val engine = remember(s.state) { CharacterEngine(clock).apply { setState(s.state, force = true) } }
        val label = when (s.kind) {
            IslandSpec.Kind.ERROR -> R.string.notif_error
            IslandSpec.Kind.RATELIMIT -> R.string.notif_ratelimit
            IslandSpec.Kind.QUESTION -> R.string.notif_question
            else -> R.string.notif_finished
        }
        // A question this phone holds opens on itself; anything else opens the app, exactly as before.
        val tapped = {
            if (s.kind == IslandSpec.Kind.QUESTION && s.canAnswerHere) tap("the question card") { onAnswer(s.pillId) }
            else tap("the result card") { onOpen() }
        }
        Row(
            Modifier.fillMaxWidth().clickable { tapped() }.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CharacterView(engine, Modifier.size(52.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.agent, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(label), color = dim, fontSize = 12.sp, maxLines = 1)
                }
                val text = when {
                    s.kind != IslandSpec.Kind.QUESTION -> s.text
                    // Answered in the app, behind its own lock: the island never answers (S10). No frame here: the old line stays.
                    s.canAnswerHere -> stringResource(R.string.island_answer_here)
                    else -> stringResource(R.string.island_answer_on_pc)
                }
                if (text.isNotBlank()) Text(text, color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @Composable
    private fun RequestCard(s: IslandSpec) {
        val engine = remember { CharacterEngine(clock).apply { setState(BotState.APPROVAL, force = true) } }
        val fingerprint = s.fingerprint ?: return
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.clickable { tap("the request header") { onOpen() } }, verticalAlignment = Alignment.CenterVertically) {
                CharacterView(engine, Modifier.size(40.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.agent, style = TypeScale.SECONDARY.style(dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // What kind of action, never the command: the exact text is in the app and in the lock prompt.
                    Text(stringResource(R.string.approval_wants, s.text), style = TypeScale.HEADLINE.style(Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.action_deny), { tap("Deny") { onDeny(fingerprint) } }, Modifier.weight(1f), onDark = true)
                if (s.canAllow) {
                    PillButton(
                        stringResource(R.string.action_allow), { tap("Allow") { onAllow(fingerprint) } }, Modifier.weight(1f), PillKind.PRIMARY, onDark = true,
                        icon = { KenaiIcon(IconKind.LOCK, tint = Color(IslandSurface.ON_PRIMARY), size = 18.dp) },
                    )
                }
            }
            Text(stringResource(if (s.canAllow) R.string.approval_hint else R.string.approval_on_computer), style = TypeScale.LABEL.style(dim), maxLines = 2)
        }
    }

    /** A window outside any activity has no lifecycle of its own: Compose needs one to run. */
    private class Owner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        init {
            controller.performAttach()
            controller.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }

    private companion object { const val TAG = "KenaiIsland" }
}

/**
 * Koda's floating circle (KN-12): a small circle at the edge of the screen while requests or questions wait, pulsing discreetly with
 * their number. A tap opens a panel with one row per pending item. Deny resolves from the row ([onDeny], the app's existing path);
 * everything else hands off to Kenai through its existing doors, because Allow and answers need the app's own screen lock (KN-04 S10).
 * Same window plumbing and rules as [IslandOverlay]: needs the "display over other apps" permission, early taps are ignored,
 * and nothing that goes wrong with the window reaches the caller. Nothing exists while nothing is pending.
 */
class CircleOverlay(
    private val context: Context,
    private val clock: () -> Double,
    private val onDeny: (fingerprint: String) -> Unit,
    private val onAllow: (fingerprint: String) -> Unit,
    private val onAnswer: (pillId: String) -> Unit,
    private val onOpen: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: LifecycleHolder? = null
    private var shownAt = 0.0

    private var rows by mutableStateOf<List<CirclePlan.Row>>(emptyList())
    private var expanded by mutableStateOf(false)
    private var reduced by mutableStateOf(false)

    /** Every change of what waits comes here; nothing pending removes the window. */
    fun setPending(items: List<PendingPlan.Item>, approvals: List<com.kenai.android.link.ApprovalRequest>) = safely {
        val next = CirclePlan.panel(items, approvals)
        if (next.isEmpty()) { hide(); return@safely }
        reduced = MotionSpec.isReduced(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
        if (view == null) { if (!add()) return@safely; shownAt = clock() }
        rows = next
    }

    fun hide() = safely { removeView() }

    private inline fun safely(block: () -> Unit) {
        try { block() } catch (e: Exception) {
            Log.w(TAG, "island failed: ${e.javaClass.simpleName}")
            runCatching { removeView() }
        }
    }

    private fun removeView() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        owner?.destroy()
        owner = null
        expanded = false
        rows = emptyList()
    }

    private fun add(): Boolean {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = (8 * context.resources.displayMetrics.density).toInt()
        }
        val holder = LifecycleHolder()
        val v = ComposeView(context).apply {
            setViewTreeLifecycleOwner(holder)
            setViewTreeSavedStateRegistryOwner(holder)
            setViewTreeViewModelStoreOwner(holder)
            setContent { Circle() }
        }
        return try {
            wm.addView(v, params)
            owner = holder
            view = v
            true
        } catch (e: Exception) {
            Log.w(TAG, "cannot add the window: ${e.javaClass.simpleName}")
            holder.destroy()
            false
        }
    }

    private fun tap(what: String, action: () -> Unit) {
        if (!OverlayPolicy.tapAccepted(shownAt, clock())) { Log.d(TAG, "ignored an early tap on $what"); return }
        action()
    }

    private fun handoff(h: CirclePlan.Handoff) {
        expanded = false
        when (h) {
            is CirclePlan.Handoff.Allow -> onAllow(h.fingerprint)
            is CirclePlan.Handoff.Question -> onAnswer(h.pillId)
            CirclePlan.Handoff.App -> onOpen()
        }
    }

    private val ink = Color(IslandSurface.BLACK)
    private val dim = Color(IslandSurface.TEXT_DIM)

    @Composable
    private fun Circle() {
        val plan = CirclePlan.circle(rows.map { PendingPlan.Item(it.kind, it.fingerprint, it.pillId, it.agent, 0L, it.leftMs) }, reduced)
        var scale by remember { mutableStateOf(1f) }
        // The pulse frame loop runs only while it pulses and the panel is closed.
        LaunchedEffect(plan.pulses, expanded) {
            if (!plan.pulses || expanded) { scale = 1f; return@LaunchedEffect }
            val start = clock()
            while (true) {
                androidx.compose.runtime.withFrameNanos { scale = CirclePlan.pulseScale((clock() - start).toLong()).toFloat() }
            }
        }
        CompositionLocalProvider(LocalTokens provides Tokens.DARK) {
            if (expanded) Panel() else {
                val cd = stringResource(R.string.circle_cd, plan.count)
                Box(
                    Modifier.size(52.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                        .semantics { contentDescription = cd }
                        .clip(CircleShape).background(ink).clickable { tap("the circle") { expanded = true } },
                    contentAlignment = Alignment.Center,
                ) {
                    val engine = remember { CharacterEngine(clock).apply { setState(BotState.APPROVAL, force = true) } }
                    CharacterView(engine, Modifier.size(40.dp))
                    Text(
                        CirclePlan.badge(plan.count), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).clip(CircleShape).background(Color(IslandSurface.BUTTON)).padding(horizontal = 5.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun Panel() {
        Column(
            Modifier.width(300.dp).clip(RoundedCornerShape(20.dp)).background(ink).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.circle_title, rows.size), style = TypeScale.HEADLINE.style(Color.White), modifier = Modifier.weight(1f))
                Text(stringResource(R.string.circle_close), style = TypeScale.SECONDARY.style(dim), modifier = Modifier.clickable { tap("close") { expanded = false } }.padding(6.dp))
            }
            rows.take(MAX_ROWS).forEach { r -> PanelRow(r) }
            Text(stringResource(R.string.circle_open), style = TypeScale.SECONDARY.style(dim), modifier = Modifier.clickable { tap("open the app") { expanded = false; onOpen() } }.padding(vertical = 4.dp))
        }
    }

    @Composable
    private fun PanelRow(r: CirclePlan.Row) {
        val question = r.kind == PendingPlan.Kind.QUESTION
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.agent, style = TypeScale.SECONDARY.style(Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (question) R.string.pending_question else R.string.pending_approval), style = TypeScale.LABEL.style(dim), maxLines = 1)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.canDeny) PillButton(stringResource(R.string.action_deny), { tap("Deny") { onDeny(r.fingerprint) } }, Modifier.weight(1f), onDark = true)
                val label = when (r.handoff) {
                    is CirclePlan.Handoff.Allow -> R.string.circle_allow
                    is CirclePlan.Handoff.Question -> R.string.circle_answer
                    CirclePlan.Handoff.App -> R.string.circle_open
                }
                PillButton(stringResource(label), { tap("hand off") { handoff(r.handoff) } }, Modifier.weight(1f), PillKind.PRIMARY, onDark = true)
            }
        }
    }

    private class LifecycleHolder : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        init { controller.performAttach(); controller.performRestore(null); registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear() }
    }

    private companion object { const val TAG = "KenaiIsland"; const val MAX_ROWS = 4 }
}
