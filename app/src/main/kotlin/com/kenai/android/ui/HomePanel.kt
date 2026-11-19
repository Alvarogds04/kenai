package com.kenai.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kenai.android.R
import com.kenai.android.core.Decision
import com.kenai.android.core.HistoryDays
import com.kenai.android.core.HomePanel
import com.kenai.android.core.HomeText
import com.kenai.android.core.IconKind
import com.kenai.android.core.IslandSurface
import com.kenai.android.core.Pills
import com.kenai.android.core.Spacing
import com.kenai.android.core.TypeScale
import com.kenai.android.link.SessionInfo
import com.kenai.android.character.BotState
import com.kenai.android.character.StateColors
import com.kenai.android.character.CharacterEngine
import com.kenai.android.character.CharacterView
import com.kenai.android.character.KodaRole
import com.kenai.android.character.KodaView
import com.kenai.android.character.Rgb
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun stateLabel(state: BotState): Int = when (state) {
    BotState.IDLE -> R.string.state_ready
    BotState.WORKING -> R.string.state_working
    BotState.THINKING -> R.string.state_thinking
    BotState.SEARCHING -> R.string.state_searching
    BotState.APPROVAL -> R.string.state_approval
    BotState.QUESTION -> R.string.state_question
    BotState.ERROR -> R.string.state_error
    BotState.FINISHED -> R.string.state_finished
    BotState.RATELIMIT -> R.string.state_ratelimit
    BotState.SLEEPING -> R.string.state_sleeping
    BotState.DIZZY -> R.string.state_dizzy
}

internal fun rgbColor(c: Rgb) = Color(c.r.toFloat(), c.g.toFloat(), c.b.toFloat())


/** The colour of an agent's state (Louis's state colours), for dots and glows. */
internal fun stateColor(state: BotState): Color =
    if (state == BotState.IDLE) StatusColors.online else StateColors.STATES[state]?.color?.let(::rgbColor) ?: StatusColors.offline

/**
 * The hero of Home: Louis's island grown to full width and hanging from the top edge of the screen. It
 * holds the agent that matters most, with its own Kenai in its own colour (the one you chose on the
 * computer), its name, one plain sentence, and at most two buttons. Black, with a soft
 * glow in the state's colour. No step counter: the computer cannot know how many steps are left.
 */
@Composable
fun HeroCard(
    focus: SessionInfo?, engine: CharacterEngine, touch: Modifier, link: HomePanel.Link, running: Int,
    linkDot: Color, linkText: String, onLink: () -> Unit, onDetails: (() -> Unit)?,
    /** Opens the question sheet; non-null only while the agent has a question. */
    onQuestion: (() -> Unit)? = null,
    /** The phone can answer this question here (the `answers` capability): the button says so. */
    waitingForRequest: Boolean = false,
    /** Inicio's status strip (kenai-0.1 S9): when given, it replaces the link row at the top. */
    strip: (@Composable () -> Unit)? = null,
) {
    val state = focus?.state ?: BotState.SLEEPING
    val wash = Color(KodaRole.of(state))
    val dim = Color(IslandSurface.TEXT_DIM)
    val white = Color(IslandSurface.TEXT)
    HeroPanel(wash = wash) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = Spacing.INSIDE.dp, end = Spacing.INSIDE.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (strip != null) strip() else Row(verticalAlignment = Alignment.CenterVertically) {
                StateDot(linkDot)
                Spacer(Modifier.width(8.dp))
                Text(linkText, Modifier.weight(1f), style = TypeScale.LABEL.style(dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (running > 0) Text(stringResource(R.string.home_running, running), style = TypeScale.LABEL.style(dim), maxLines = 1)
            }
            if (focus == null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CharacterView(engine, Modifier.size(128.dp).then(touch), stringResource(R.string.koda_description))
                    Text(stringResource(R.string.home_empty_title), style = TypeScale.TITLE.style(white))
                    Text(stringResource(R.string.home_empty_hint), style = TypeScale.SECONDARY.style(dim), maxLines = 3)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CharacterView(engine, Modifier.size(96.dp).then(touch), stringResource(R.string.koda_description))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val pillName = Pills.byId(focus.pillId)?.name.orEmpty()
                        val name = focus.agent.ifBlank { pillName }.ifBlank { stringResource(R.string.app_name) }
                        Text(name, style = TypeScale.TITLE.style(white), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (pillName.isNotBlank() && pillName != name) pillName else stringResource(R.string.agent_kind),
                            style = TypeScale.SECONDARY.style(dim), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            HomeText.line(focus) ?: stringResource(stateLabel(state)), Modifier.padding(top = 4.dp),
                            style = TypeScale.HEADLINE.style(white), maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (link != HomePanel.Link.NONE || onDetails != null || onQuestion != null || waitingForRequest) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (link != HomePanel.Link.NONE) {
                        PillButton(
                            stringResource(if (link == HomePanel.Link.PAIR) R.string.link_pair else R.string.link_approval), onLink,
                            kind = PillKind.PRIMARY, onDark = true,
                        )
                    }
                    if (onQuestion != null) PillButton(stringResource(R.string.question_see_answer), onQuestion, kind = PillKind.PRIMARY, onDark = true)
                    if (waitingForRequest) Text(stringResource(R.string.home_waiting_request), style = TypeScale.SECONDARY.style(dim))
                    if (onDetails != null) {
                        PillButton(
                            stringResource(R.string.session_details), onDetails, onDark = true,
                            icon = { KenaiIcon(IconKind.CHEVRON, tint = white, size = 18.dp) },
                        )
                    }
                }
            }
        }
    }
}

/** The other agents, one full-width row each: its small Kenai in its colour, its name and one plain line. Tap to make it the main one. */
@Composable
fun AgentRows(others: List<SessionInfo>, engineFor: (SessionInfo) -> CharacterEngine, onPick: (SessionInfo) -> Unit) {
    val t = tokens()
    Panel {
        others.forEachIndexed { i, s ->
            if (i > 0) RowDivider()
            val name = s.agent.ifBlank { Pills.byId(s.pillId)?.name.orEmpty() }
            val state = stringResource(stateLabel(s.state))
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button) { onPick(s) }
                    .padding(horizontal = Spacing.INSIDE.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(Spacing.GAP.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                KodaView(s.state, Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = TypeScale.BODY.style(t.text.c()).copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(HomeText.line(s) ?: state, style = TypeScale.SECONDARY.style(t.textDim.c()), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StateDot(Color(KodaRole.of(s.state)))
            }
        }
    }
}

// ── Inicio, "Decidí en un toque" (kenai-0.1 S9, T13) ─────────────────────────────────────────────────────────────

/** The hero while nothing waits: a big resting Koda, "Nada te espera", and who is working where (or how to start one). */
@Composable
fun CalmHero(engine: CharacterEngine, touch: Modifier, sentence: String?, strip: @Composable () -> Unit) {
    val dim = Color(IslandSurface.TEXT_DIM)
    val white = Color(IslandSurface.TEXT)
    HeroPanel {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = Spacing.INSIDE.dp, end = Spacing.INSIDE.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            strip()
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CharacterView(engine, Modifier.size(128.dp).then(touch), stringResource(R.string.koda_description))
                Text(stringResource(R.string.home_calm_title), Modifier.semantics { heading() }, style = TypeScale.TITLE.style(white))
                Text(sentence ?: stringResource(R.string.home_empty_hint), style = TypeScale.SECONDARY.style(dim).copy(textAlign = TextAlign.Center), maxLines = 3)
            }
        }
    }
}

/**
 * The hero while the paired computer is away: what to check, "Volver a probar" (reconnect now) and "¿No se conecta?" (the checks).
 * It says what the link panel and the discovery hint said, in one place; when the search found nothing it keeps their two ways out.
 */
@Composable
fun OfflineHero(
    why: String?, notFound: Boolean, strip: @Composable () -> Unit, onRetry: () -> Unit, onHelp: () -> Unit,
    onPairAgain: () -> Unit, onAddress: () -> Unit,
) {
    val dim = Color(IslandSurface.TEXT_DIM)
    val white = Color(IslandSurface.TEXT)
    HeroPanel(wash = Color(KodaRole.RED)) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = Spacing.INSIDE.dp, end = Spacing.INSIDE.dp, top = 12.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            strip()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KodaView(BotState.ERROR, Modifier.size(56.dp))
                Text(stringResource(R.string.discovery_title), Modifier.weight(1f).semantics { heading() }, style = TypeScale.HEADLINE.style(white))
            }
            if (!why.isNullOrBlank()) Text(why, style = TypeScale.SECONDARY.style(white))
            Text(stringResource(R.string.home_offline_body), style = TypeScale.SECONDARY.style(dim))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(stringResource(R.string.home_retry), onRetry, Modifier.weight(1f), PillKind.PRIMARY, onDark = true)
                PillButton(stringResource(R.string.diag_title), onHelp, Modifier.weight(1f), onDark = true)
            }
            if (notFound) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(stringResource(R.string.discovery_pair_again), onPairAgain, Modifier.weight(1f), onDark = true)
                    PillButton(stringResource(R.string.discovery_enter_address), onAddress, Modifier.weight(1f), onDark = true)
                }
            }
        }
    }
}

/** One agent row of Inicio's sections: its Koda, its name, one plain line, and something at the end (a button or a chip). */
@Composable
private fun SectionRow(s: SessionInfo, name: String, line: String, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    val t = tokens()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.INSIDE.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.GAP.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        KodaView(s.state, Modifier.size(40.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = TypeScale.BODY.style(t.text.c()).copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (line.isNotBlank()) Text(line, style = TypeScale.SECONDARY.style(t.textDim.c()), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

private fun nameOf(s: SessionInfo) = s.agent.ifBlank { Pills.byId(s.pillId)?.name.orEmpty() }

/**
 * "También te espera": the other agents with a question ("Responder" opens it) or a request ("Revisar" opens its sheet; null
 * when the phone has no request of theirs to show). [asks]: the request hero's own agent also asks (kenai-0.1 S9, T17; L24); its
 * question comes first, drawn as a question with [asksLine], so it is reachable before the request is decided.
 */
@Composable
fun AlsoWaiting(
    waiting: List<SessionInfo>, reviewFor: (SessionInfo) -> String?, onQuestion: (String) -> Unit, onReview: (String) -> Unit,
    asks: SessionInfo? = null, asksLine: String = "", canAnswer: (SessionInfo) -> Boolean = { true },
) {
    if (waiting.isEmpty() && asks == null) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(stringResource(R.string.home_also_waiting))
        Panel {
            asks?.let { a ->
                SectionRow(a.copy(state = BotState.QUESTION), nameOf(a), asksLine.ifBlank { stringResource(R.string.state_question) }, null) {
                    PillButton(stringResource(R.string.home_answer), { onQuestion(a.pillId) })
                }
            }
            waiting.forEachIndexed { i, s ->
                if (i > 0 || asks != null) RowDivider()
                val line = HomeText.line(s) ?: stringResource(stateLabel(s.state))
                SectionRow(s, nameOf(s), line, null) {
                    if (s.state == BotState.QUESTION) {
                        if (canAnswer(s)) PillButton(stringResource(R.string.home_answer), { onQuestion(s.pillId) })
                        else Text(stringResource(R.string.home_waiting_request), style = TypeScale.SECONDARY.style(tokens().textDim.c()))
                    }
                    else reviewFor(s)?.let { fp -> PillButton(stringResource(R.string.home_review), { onReview(fp) }) }
                }
            }
        }
    }
}

/**
 * "En curso · n" (busy agents with a live chip in their state colour), or the kept list while the computer is away ([unconfirmed]:
 * a grey "Sin confirmar" chip, since nothing on it is current). [heading] is the section's title. A row with details opens them.
 */
@Composable
fun AgentSection(heading: String, sessions: List<SessionInfo>, unconfirmed: Boolean, onOpen: (SessionInfo) -> Unit) {
    if (sessions.isEmpty()) return
    val t = tokens()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(heading)
        Panel {
            sessions.forEachIndexed { i, s ->
                if (i > 0) RowDivider()
                val state = stringResource(stateLabel(s.state))
                // The chip already names the state: the line says what it does, else where (never the state twice).
                SectionRow(s, nameOf(s), HomeText.line(s) ?: s.project.orEmpty(), if (HomePanel.hasDetails(s)) ({ onOpen(s) }) else null) {
                    if (unconfirmed) Chip(stringResource(R.string.home_unconfirmed), tint = t.textDim.c())
                    else Chip(state, tint = Color(KodaRole.of(s.state)))
                }
            }
        }
    }
}

/** The last few things you allowed or denied, in plain words, with "See all" to the full list. */
@Composable
fun RecentPanel(decisions: List<Decision>, onAll: () -> Unit) {
    if (decisions.isEmpty()) return
    val t = tokens()
    val zone = androidx.compose.runtime.remember { ZoneId.systemDefault() }
    val timeFormat = androidx.compose.runtime.remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val dateFormat = androidx.compose.runtime.remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }
    val now = System.currentTimeMillis()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeading(stringResource(R.string.recent_title), Modifier.weight(1f))
            Text(
                stringResource(R.string.recent_all),
                Modifier.heightIn(min = Spacing.MIN_TOUCH.dp).clickable(role = Role.Button, onClick = onAll).padding(horizontal = 4.dp, vertical = 12.dp),
                style = TypeScale.LABEL.style(t.textDim.c()),
            )
        }
        Panel {
            decisions.take(2).forEachIndexed { i, d ->
                if (i > 0) RowDivider()
                val (day, date) = HistoryDays.classify(d.atMs, now, zone)
                val whenText = when (day) {
                    HistoryDays.Day.TODAY -> timeFormat.format(Instant.ofEpochMilli(d.atMs).atZone(zone).toLocalTime())
                    HistoryDays.Day.YESTERDAY -> stringResource(R.string.history_yesterday)
                    HistoryDays.Day.OTHER -> dateFormat.format(date)
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onAll).padding(horizontal = Spacing.INSIDE.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.GAP.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    KenaiIcon(if (d.allowed) IconKind.CHECK else IconKind.CLOSE, tint = if (d.allowed) StatusColors.online else t.danger.c(), size = 20.dp)
                    // «Permitiste: editar archivos» over «Claude Code · Ayer» (kenai-0.1 S9).
                    Column(Modifier.weight(1f)) {
                        Text(HomeText.recent(d.allowed, d.tool), style = TypeScale.BODY.style(t.text.c()), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(d.agent, whenText).filter { it.isNotBlank() }.joinToString(" · "), style = TypeScale.SECONDARY.style(t.textDim.c()), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
