package com.kenai.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenai.android.R
import com.kenai.android.app.AppModel
import com.kenai.android.character.BotState
import com.kenai.android.character.KodaView
import com.kenai.android.core.DecisionOutcomes
import com.kenai.android.core.HomePanel
import com.kenai.android.core.HomeText
import com.kenai.android.core.IconKind
import com.kenai.android.core.IslandSurface
import com.kenai.android.core.Outcome
import com.kenai.android.core.OutcomeKind
import com.kenai.android.core.Spacing
import com.kenai.android.core.TypeScale
import com.kenai.android.core.Words

/**
 * Inicio's hero right after a decision (kenai-0.1 S9, «1 · Decidí en un toque», screen 3 «Resultado»; T14): the dark hero washed
 * in the result's role colour, the strip on top (what the computer got, always naming it), Koda in the result's pose, a heading, a
 * sentence that is honest about what reached the computer, and for a confirmed decision the time, the buzz and that it is in
 * Actividad. While sending both buttons stay, disabled; a failed send offers «Volver a intentar» and «¿No se conecta?»; the
 * others close with «Cerrar». The words come from [HomeText] (core, tested); this never decides anything itself.
 */
@Composable
fun ResultHero(
    o: Outcome, agent: String, project: String?, strip: @Composable () -> Unit,
    onClose: () -> Unit, onRetry: () -> Unit, onHelp: () -> Unit,
) {
    val white = Color(IslandSurface.TEXT)
    val dim = Color(IslandSurface.TEXT_DIM)
    HeroPanel(wash = Color(DecisionOutcomes.role(o.kind))) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = Spacing.INSIDE.dp, end = Spacing.INSIDE.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            strip()
            // One polite live region: TalkBack hears «Enviando…» turn into «Permitiste…» once, without moving focus.
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KodaView(DecisionOutcomes.pose(o.kind), Modifier.size(96.dp))
                Text(
                    HomeText.resultTitle(o), Modifier.semantics { heading() },
                    style = TypeScale.TITLE.style(white).copy(textAlign = TextAlign.Center), maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Text(HomeText.resultBody(o, agent, project), style = TypeScale.SECONDARY.style(white).copy(textAlign = TextAlign.Center))
                HomeText.resultMeta(o)?.let { meta ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        KenaiIcon(IconKind.CLOCK, tint = dim, size = 16.dp)
                        Text(meta, style = TypeScale.LABEL.style(dim), maxLines = 2)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (o.kind) {
                    // Where the request's buttons were, so nothing jumps; neither can be tapped until the computer answers.
                    OutcomeKind.SENDING -> {
                        PillButton(stringResource(R.string.action_deny), {}, Modifier.weight(1f), onDark = true, enabled = false)
                        PillButton(stringResource(R.string.result_sending), {}, Modifier.weight(1f), PillKind.GO, enabled = false, onDark = true)
                    }
                    // Trying again is safe: the request is still open on the computer.
                    OutcomeKind.FAILED -> {
                        PillButton(stringResource(R.string.result_retry), onRetry, Modifier.weight(1f), PillKind.PRIMARY, onDark = true)
                        PillButton(stringResource(R.string.diag_title), onHelp, Modifier.weight(1f), onDark = true)
                    }
                    else -> PillButton(stringResource(R.string.action_close), onClose, Modifier.weight(1f), onDark = true)
                }
            }
        }
    }
}

/** «Lo siguiente» follows a result once nothing is in flight: allowed, denied or expired (not while sending or after a failure). */
internal fun showsNext(o: Outcome): Boolean =
    o.kind == OutcomeKind.ALLOWED || o.kind == OutcomeKind.DENIED || o.kind == OutcomeKind.EXPIRED

/**
 * «Lo siguiente»: what waits next ([HomePanel.next]): another request («Claude Code espera tu sí», what it wants, «Revisar») or a
 * question («Gemini CLI tiene una pregunta», the question and the project, «Responder»).
 */
@Composable
fun NextUp(model: AppModel, next: HomePanel.Next, onQuestion: (String) -> Unit, onReview: (String) -> Unit) {
    val t = tokens()
    val w = Words.current()
    val s = model.sessions.firstOrNull { it.pillId == next.pillId }
    val agent = model.agentName(next.pillId)
    val title: String
    val line: String
    val state: BotState
    when (next.kind) {
        HomePanel.NextKind.APPROVAL -> {
            val r = model.approvals.firstOrNull { it.fingerprint == next.fingerprint }
            title = w.waitsForYourYes(agent)
            line = HomeText.wants(r?.tool.orEmpty(), s?.project)
            state = BotState.APPROVAL
        }
        HomePanel.NextKind.QUESTION -> {
            title = w.asksYou(agent)
            line = s?.let { HomeText.nextLine(model.questionFor(next.pillId)?.questions?.firstOrNull()?.question, it) }.orEmpty()
            state = BotState.QUESTION
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(stringResource(R.string.result_next), Modifier.semantics { heading() })
        Panel {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.INSIDE.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.GAP.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                KodaView(s?.state ?: state, Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = TypeScale.BODY.style(t.text.c()).copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (line.isNotBlank()) Text(line, style = TypeScale.SECONDARY.style(t.textDim.c()), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                when (next.kind) {
                    HomePanel.NextKind.QUESTION ->
                        if (model.questionFor(next.pillId) != null) PillButton(stringResource(R.string.home_answer), { onQuestion(next.pillId) }, kind = PillKind.PRIMARY)
                        else Text(stringResource(R.string.home_waiting_request), style = TypeScale.SECONDARY.style(t.textDim.c()))
                    HomePanel.NextKind.APPROVAL -> next.fingerprint?.let { fp ->
                        PillButton(stringResource(R.string.home_review), { onReview(fp) }, kind = PillKind.PRIMARY)
                    }
                }
            }
        }
    }
}
