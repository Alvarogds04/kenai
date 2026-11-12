package com.kenai.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenai.android.R
import com.kenai.android.app.AppModel
import com.kenai.android.character.BotState
import com.kenai.android.character.KodaRole
import com.kenai.android.core.ApprovalSheetPlan
import com.kenai.android.core.HomePanel
import com.kenai.android.core.IconKind
import com.kenai.android.core.QuestionFlow
import com.kenai.android.core.QuestionPresence
import com.kenai.android.core.Spacing
import com.kenai.android.core.TypeScale
import com.kenai.android.link.AskedOption
import com.kenai.android.link.QuestionRequest
import kotlinx.coroutines.delay

/**
 * An agent's question as its own screen (kenai-0.1 S9, screen 4 «Pregunta»; T15): the status strip on top (another request that waits,
 * a tap reviews it), «Atrás» and «<agent> tiene una pregunta», «Pregunta 1 de 2» with one segment per question, the question, its options
 * with their descriptions, and «Atrás» / «Siguiente» or «Enviar respuesta». With the `answers` capability the options are here to tap and
 * the answer goes only after the screen lock ([onSend]; the labels are the computer's exact ones, nothing typed ever goes in). Without it
 * the screen says where to answer, as before. Answered or expired on the computer while open, it closes itself ([onGone]).
 * The picks live only while the screen is open: never saved, logged or stored.
 */
@Composable
fun QuestionScreen(
    model: AppModel, pillId: String, strip: HomePanel.Strip, onStrip: (() -> Unit)?,
    onBack: () -> Unit, onSend: (QuestionRequest, List<List<String>>) -> Unit, onGone: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val request = model.questionFor(pillId)
    var seen by remember(pillId) { mutableStateOf(request != null) }
    LaunchedEffect(request?.fingerprint) { if (request != null) seen = true }
    val asking = model.sessions.any { it.pillId == pillId && it.state == BotState.QUESTION }
    val presence = QuestionFlow.presence(hasRequest = request != null, seenRequest = seen, asking = asking)
    LaunchedEffect(presence) { if (presence == QuestionPresence.GONE) onGone() }
    val title = stringResource(R.string.question_title, model.agentName(pillId))
    when {
        presence == QuestionPresence.ANSWER && request != null -> Answering(request, strip, onStrip, title, onBack, onSend)
        presence == QuestionPresence.ON_COMPUTER -> QuestionFrame(strip, onStrip, title, onBack, body = {
            Text(stringResource(R.string.question_body), style = TypeScale.BODY.style(tokens().textDim.c()))
        }) {
            PillButton(stringResource(R.string.question_ok), onBack, Modifier.fillMaxWidth(), PillKind.PRIMARY)
        }
        // Gone: Home takes over at once; nothing to draw meanwhile.
        else -> QuestionFrame(strip, onStrip, title, onBack, body = {}) {}
    }
}

/** The strip, the header, a scrolling body and the buttons pinned under it. */
@Composable
private fun QuestionFrame(
    strip: HomePanel.Strip, onStrip: (() -> Unit)?, title: String, onBack: () -> Unit,
    body: @Composable ColumnScope.() -> Unit, buttons: @Composable () -> Unit,
) {
    val t = tokens()
    Column(Modifier.fillMaxSize()) {
        StatusStrip(strip, Modifier.padding(start = Gutter, end = Gutter, top = 8.dp), onClick = onStrip)
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = Gutter, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val back = stringResource(R.string.question_back)
            Box(
                Modifier.size(Spacing.MIN_TOUCH.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = back },
                contentAlignment = Alignment.Center,
            ) { KenaiIcon(IconKind.BACK, tint = t.text.c()) }
            Text(
                title, Modifier.weight(1f).semantics { heading() }, style = TypeScale.HEADLINE.style(t.text.c()),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Gap), content = body,
        )
        Box(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = Gutter)) { buttons() }
    }
}

/** The question to answer here: one at a time, then the lock. Taps on Send in the first 600 ms are ignored, as on Allow. */
@Composable
private fun Answering(
    request: QuestionRequest, strip: HomePanel.Strip, onStrip: (() -> Unit)?, title: String,
    onBack: () -> Unit, onSend: (QuestionRequest, List<List<String>>) -> Unit,
) {
    val t = tokens()
    var st by remember(request.fingerprint) { mutableStateOf(QuestionFlow.start(request.questions)) }
    var armed by remember(request.fingerprint) { mutableStateOf(false) }
    LaunchedEffect(request.fingerprint) { delay(ApprovalSheetPlan.GUARD_MS); armed = true }
    if (request.questions.isEmpty()) return
    val q = request.questions[st.index.coerceIn(0, request.questions.lastIndex)]
    QuestionFrame(strip, onStrip, title, onBack, body = {
        QuestionFlow.position(request.questions, st)?.let { (n, total) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.question_position, n, total), style = TypeScale.LABEL.style(t.textDim.c()))
                // One segment per question, filled up to this one; the line above says it in words.
                Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (filled in QuestionFlow.segments(request.questions, st)) {
                        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (filled) Color(KodaRole.LIVE) else t.line.c()))
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(q.question, Modifier.semantics { heading() }, style = TypeScale.TITLE.style(t.text.c()))
            if (q.multiSelect) Text(stringResource(R.string.question_pick_many), style = TypeScale.SECONDARY.style(t.textDim.c()))
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (o in q.options) {
                OptionRow(o, selected = o.label in st.picks[st.index], multi = q.multiSelect) { st = QuestionFlow.toggle(request.questions, st, o.label) }
            }
        }
        Text(stringResource(R.string.question_hint_answers), style = TypeScale.SECONDARY.style(t.textDim.c()))
    }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // «Atrás»: the previous question, or out of the screen from the first one.
            PillButton(stringResource(R.string.question_back), { if (st.index > 0) st = QuestionFlow.back(st) else onBack() }, Modifier.weight(1f))
            if (QuestionFlow.isLast(request.questions, st)) {
                PillButton(
                    stringResource(R.string.question_send),
                    { QuestionFlow.picks(request.questions, st)?.let { onSend(request, it) } }, Modifier.weight(1f), PillKind.PRIMARY,
                    enabled = armed && QuestionFlow.complete(request.questions, st),
                    icon = { KenaiIcon(IconKind.LOCK, tint = t.onPrimaryButton.c(), size = 18.dp) },
                )
            } else {
                PillButton(
                    stringResource(R.string.question_next), { st = QuestionFlow.next(request.questions, st) }, Modifier.weight(1f), PillKind.PRIMARY,
                    enabled = QuestionFlow.canAdvance(request.questions, st),
                )
            }
        }
    }
}

/** One option: a radio (single choice) or a check (multiple), its label and the description under it; picked, a live ring. */
@Composable
private fun OptionRow(option: AskedOption, selected: Boolean, multi: Boolean, onClick: () -> Unit) {
    val t = tokens()
    val live = Color(KodaRole.LIVE)
    val shape = RoundedCornerShape(16.dp)
    val markShape = if (multi) RoundedCornerShape(6.dp) else CircleShape
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.MIN_TOUCH.dp).clip(shape).background(t.panel.c())
            .border(if (selected) 2.dp else 1.dp, if (selected) live else t.line.c(), shape)
            .selectable(selected = selected, role = if (multi) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(markShape).background(if (selected) live else Color.Transparent)
                .border(1.5.dp, if (selected) live else t.textDim.c(), markShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) KenaiIcon(IconKind.CHECK, tint = Color(KodaRole.ON_ROLE), size = 16.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(option.label, style = TypeScale.BODY.style(t.text.c()).copy(fontWeight = FontWeight.SemiBold))
            if (option.description.isNotBlank()) Text(option.description, style = TypeScale.SECONDARY.style(t.textDim.c()))
        }
    }
}
