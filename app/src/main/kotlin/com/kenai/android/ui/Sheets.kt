package com.kenai.android.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kenai.android.R
import com.kenai.android.app.AppModel
import com.kenai.android.core.ApprovalSheetPlan
import com.kenai.android.core.HomePanel
import com.kenai.android.core.IconKind
import com.kenai.android.core.IslandSurface
import com.kenai.android.core.MotionSpec
import com.kenai.android.core.Nav
import com.kenai.android.core.Pills
import com.kenai.android.core.Screen
import com.kenai.android.core.Radii
import com.kenai.android.core.Spacing
import com.kenai.android.core.TypeScale
import com.kenai.android.link.ApprovalRequest
import com.kenai.android.character.BotState
import com.kenai.android.character.StateColors
import com.kenai.android.character.CharacterEngine
import com.kenai.android.character.CharacterView
import kotlinx.coroutines.delay

/**
 * The sheets a screen opens itself: Pairing's "Paste the link" and Home's address. The activity draws them in the same layer
 * as the approval sheet, over the bottom bar, never inside the screen.
 */
enum class ScreenSheet { PASTE, ADDRESS }

/** Where sheets sit relative to the bar. Pure, so the rules are unit tests (S7: the bar never covers a sheet's buttons). */
object SheetLayer {
    /** The bar shows as before ([Nav.barVisible]), and never while a sheet is up: the sheet's buttons sit where the bar would be. */
    fun barVisible(screen: Screen, keyboardOpen: Boolean, sheetOpen: Boolean): Boolean = !sheetOpen && Nav.barVisible(screen, keyboardOpen)

    /** The screen sheet to draw now: only over the screen that opened it (Paste while pairing, the address while paired, both on Home). */
    fun shown(open: ScreenSheet?, screen: Screen, pairing: Boolean): ScreenSheet? = when (open) {
        ScreenSheet.PASTE -> open.takeIf { screen == Screen.HOME && pairing }
        ScreenSheet.ADDRESS -> open.takeIf { screen == Screen.HOME && !pairing }
        null -> null
    }
}

/**
 * A panel that rises from the bottom over whatever is on screen, with a dim scrim behind it. It is black in
 * both themes, like the island it comes from. Tapping the scrim or pressing Back closes it; taps on the
 * panel itself never reach the scrim. It reaches the bottom edge of the screen (drawn outside the screens' inset padding)
 * and keeps its content above the navigation bar and the keyboard.
 */
@Composable
fun BottomSheetHost(onDismiss: () -> Unit, wash: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val shape = RoundedCornerShape(topStart = Radii.SHEET.dp, topEnd = Radii.SHEET.dp)
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        AnimatedVisibility(
            visible = shown, modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (reducedMotion()) fadeIn(tween(MotionSpec.REDUCED_MS)) else slideInVertically(spring(dampingRatio = 0.72f, stiffness = 400f)) { it } + fadeIn(),
        ) {
            Column(
                Modifier.fillMaxWidth().clip(shape).background(Color(IslandSurface.PANEL)).border(1.dp, Color.White.copy(alpha = 0.08f), shape)
                    .then(if (wash != null) Modifier.drawBehind { drawWash(wash, 0.45f) } else Modifier)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
                content = {
                    AlwaysDark {
                        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f)))
                        Box(Modifier.size(14.dp))
                        content()
                    }
                },
            )
        }
    }
}

/** The colour of an agent for its Kenai: the user's choice on the computer, else the catalog's, else none. */
private fun agentColour(model: AppModel, pillId: String) =
    (model.sessions.firstOrNull { it.pillId == pillId }?.let { HomePanel.colorHex(it) } ?: Pills.byId(pillId)?.colorHex)?.let { HomePanel.rgb(it) }

@Composable
private fun SheetCharacter(state: BotState, colour: com.kenai.android.character.Rgb?) {
    val engine = remember { CharacterEngine({ SystemClock.elapsedRealtimeNanos() / 1e6 }).apply { setState(state, force = true) } }
    engine.bodyColor = colour
    CharacterView(engine, Modifier.size(84.dp))
}

/**
 * A request for permission, in words: who, and what kind of action ("Wants to run a command"). The exact command is one tap
 * away ("Show exact command") and is always repeated by the lock prompt, so Allow is never blind. Allow needs the lock;
 * Deny does not. Taps in the first 600 ms are ignored.
 */
@Composable
fun ApprovalSheet(model: AppModel, r: ApprovalRequest, onAllow: (ApprovalRequest) -> Unit, onDismiss: () -> Unit) {
    var armed by remember(r.fingerprint) { mutableStateOf(false) }
    LaunchedEffect(r.fingerprint) { delay(ApprovalSheetPlan.GUARD_MS); armed = true }
    var showCommand by remember(r.fingerprint) { mutableStateOf(false) }
    val white = Color(IslandSurface.TEXT)
    val dim = Color(IslandSurface.TEXT_DIM)
    val wash = StateColors.STATES[BotState.APPROVAL]?.color?.let(::rgbColor)
    val name = model.agentName(r.pillId)
    val kind = Pills.byId(r.pillId)?.name.orEmpty()
    BottomSheetHost(onDismiss, wash) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SheetCharacter(BotState.APPROVAL, agentColour(model, r.pillId))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (kind.isNotBlank() && kind != name) "$name · $kind" else name, style = TypeScale.SECONDARY.style(dim), maxLines = 1)
                Text(stringResource(R.string.approval_wants, ApprovalSheetPlan.action(r.tool)), style = TypeScale.TITLE.style(white), maxLines = 3)
            }
        }
        Text(stringResource(R.string.approval_caution), Modifier.padding(top = 12.dp), style = TypeScale.SECONDARY.style(dim))
        Text(
            stringResource(if (showCommand) R.string.approval_hide_cmd else R.string.approval_show_cmd),
            Modifier.heightIn(min = Spacing.MIN_TOUCH.dp).clickable(role = Role.Button) { showCommand = !showCommand }.padding(vertical = 12.dp),
            style = TypeScale.SECONDARY.style(dim).copy(textDecoration = TextDecoration.Underline),
        )
        if (showCommand) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.07f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(r.tool, style = TypeScale.LABEL.style(dim), maxLines = 1)
                // The whole command that was sent, scrollable: never cut by a line limit.
                Column(Modifier.heightIn(max = ApprovalSheetPlan.COMMAND_BOX_MAX_DP.dp).verticalScroll(rememberScrollState())) {
                    Text(r.command, style = TypeScale.MONO.style(white).copy(fontFamily = FontFamily.Monospace))
                }
            }
            Box(Modifier.size(8.dp))
        }
        val canAllow = ApprovalSheetPlan.canAllowHere(r)
        if (!canAllow) {
            // Shown without a tap: the user must know the command is not all here before reading anything else.
            Text(stringResource(R.string.approval_too_long), Modifier.padding(bottom = 4.dp), style = TypeScale.TITLE.style(white))
            Text(stringResource(R.string.approval_on_computer), Modifier.padding(bottom = 10.dp), style = TypeScale.SECONDARY.style(dim))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(stringResource(R.string.action_deny), { model.decide(r.fingerprint, allow = false) }, Modifier.weight(1f), onDark = true, enabled = armed)
            if (canAllow) {
                PillButton(
                    stringResource(R.string.action_allow), { onAllow(r) }, Modifier.weight(1f), PillKind.PRIMARY, enabled = armed, onDark = true,
                    icon = { KenaiIcon(IconKind.LOCK, tint = Color(IslandSurface.ON_PRIMARY), size = 18.dp) },
                )
            }
        }
        if (canAllow) Text(stringResource(R.string.approval_hint), Modifier.padding(top = 10.dp), style = TypeScale.LABEL.style(dim))
        Text(
            stringResource(R.string.approval_later),
            Modifier.heightIn(min = Spacing.MIN_TOUCH.dp).clickable(role = Role.Button, onClick = onDismiss).padding(vertical = 14.dp),
            style = TypeScale.SECONDARY.style(dim),
        )
    }
}
