package com.kenai.android.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kenai.android.R
import com.kenai.android.app.AppModel
import com.kenai.android.character.BotState
import com.kenai.android.character.KodaRole
import com.kenai.android.character.KodaView
import com.kenai.android.core.ApprovalSheetPlan
import com.kenai.android.core.HomePanel
import com.kenai.android.core.HomeText
import com.kenai.android.core.IconKind
import com.kenai.android.core.IslandSurface
import com.kenai.android.core.Pills
import com.kenai.android.core.Spacing
import com.kenai.android.core.TypeScale
import com.kenai.android.link.ApprovalRequest
import kotlinx.coroutines.delay

/**
 * Inicio's hero while a request waits (kenai-0.1 S9, «1 · Decidí en un toque»; L21 «a»): the dark hero with a sun wash, the
 * strip on top, Koda waiting in the agent's light, who and what ("Quiere ejecutar un comando en kenai"), how long it is still
 * offered, the exact command in full, the risk line, and Denegar / Permitir. Home hides from screenshots while this shows
 * ([com.kenai.android.core.SecureScreens]). Allow goes to the lock prompt ([onAllow]); Deny needs nothing. Taps in the first
 * 600 ms are ignored, as on the sheet. A command the computer cut is said, and Allow is not offered here.
 */
@Composable
fun RequestHero(model: AppModel, r: ApprovalRequest, strip: @Composable () -> Unit, onAllow: (ApprovalRequest) -> Unit) {
    var armed by remember(r.fingerprint) { mutableStateOf(false) }
    LaunchedEffect(r.fingerprint) { delay(ApprovalSheetPlan.GUARD_MS); armed = true }
    // The phone's clock, once a second, for "Caduca en m:ss" (the request is removed at 0 by the model's own timer).
    val now by produceState(SystemClock.elapsedRealtime(), r.fingerprint) {
        while (true) {
            value = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val arrived = model.arrivals[r.fingerprint]
    val remaining = arrived?.let { HomePanel.remainingMs(it, now) }
    val session = model.sessions.firstOrNull { it.pillId == r.pillId }
    // The project only when the computer offers the details; never a path.
    val project = model.sessions.firstOrNull { it.pillId == r.pillId }?.project
    val agentHex = session?.let { HomePanel.colorHex(it) } ?: Pills.byId(r.pillId)?.colorHex
    val agentLight = agentHex?.let { HomePanel.rgb(it) }?.let(::rgbColor) ?: Color(KodaRole.SUN)
    val white = Color(IslandSurface.TEXT)
    val dim = Color(IslandSurface.TEXT_DIM)
    val sun = Color(KodaRole.SUN)

    HeroPanel(wash = Color(KodaRole.SUN)) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = Spacing.INSIDE.dp, end = Spacing.INSIDE.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            strip()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Koda waiting, lit by the agent's colour, with the agent's dot.
                Box(Modifier.size(72.dp)) {
                    Box(
                        Modifier.matchParentSize().drawBehind {
                            drawCircle(Brush.radialGradient(listOf(agentLight.copy(alpha = 0.45f), Color.Transparent), center = Offset(size.width / 2, size.height / 2), radius = size.minDimension / 1.6f))
                        },
                    )
                    KodaView(BotState.APPROVAL, Modifier.size(72.dp))
                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(12.dp).clip(CircleShape).background(agentLight))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        model.agentName(r.pillId), Modifier.semantics { heading() }, style = TypeScale.TITLE.style(white),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(HomeText.wants(r.tool, project), style = TypeScale.SECONDARY.style(dim), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            if (remaining != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier.heightIn(min = 28.dp).clip(CircleShape).background(sun).padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KenaiIcon(IconKind.CLOCK, tint = Color(KodaRole.ON_ROLE), size = 14.dp)
                        Text(HomePanel.expiry(remaining), style = TypeScale.LABEL.style(Color(KodaRole.ON_ROLE)).copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                    }
                    Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(HomePanel.expiryFraction(remaining)).background(sun))
                    }
                }
            }
            // The exact command that was sent, whole and scrollable: never cut by a line limit.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.request_command), style = TypeScale.LABEL.style(dim), maxLines = 1)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.07f)).padding(12.dp)
                        .heightIn(max = ApprovalSheetPlan.COMMAND_BOX_MAX_DP.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text(r.command, style = TypeScale.MONO.style(white).copy(fontFamily = FontFamily.Monospace))
                }
            }
            val canAllow = ApprovalSheetPlan.canAllowHere(r)
            if (!canAllow) {
                // Said without a tap: the command is not all here, so it is answered on the computer.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.approval_too_long), style = TypeScale.HEADLINE.style(white))
                    Text(stringResource(R.string.approval_on_computer), style = TypeScale.SECONDARY.style(dim))
                }
            }
            // What the local detector recognises, if anything (it never says a command is safe), then the caution.
            val risk = HomeText.riskLine(r.command, project)
            val caution = stringResource(R.string.approval_caution)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                if (risk != null) KenaiIcon(IconKind.WARNING, Modifier.padding(top = 1.dp), tint = sun, size = 18.dp)
                Text(
                    buildAnnotatedString {
                        if (risk != null) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = white)) { append(risk.first) }
                            append(risk.second)
                            append(" ")
                        }
                        append(caution)
                    },
                    style = TypeScale.SECONDARY.style(if (risk != null) white else dim),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.action_deny), { model.decide(r.fingerprint, allow = false) }, Modifier.weight(1f), onDark = true, enabled = armed)
                if (canAllow) {
                    PillButton(
                        stringResource(R.string.action_allow), { onAllow(r) }, Modifier.weight(1f), PillKind.GO, enabled = armed, onDark = true,
                        icon = { KenaiIcon(IconKind.FINGERPRINT, tint = Color(KodaRole.ON_ROLE), size = 18.dp) },
                    )
                }
            }
            if (canAllow) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    KenaiIcon(IconKind.LOCK, tint = dim, size = 16.dp)
                    Text(stringResource(R.string.request_hint), style = TypeScale.LABEL.style(dim))
                }
            }
        }
    }
}
