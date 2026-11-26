package com.kenai.android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.kenai.android.core.IconKind
import com.kenai.android.core.ChatConversationOption
import com.kenai.android.core.ChatHeader
import com.kenai.android.core.ChatHeaderAction
import com.kenai.android.core.IslandSurface
import com.kenai.android.core.Spacing
import com.kenai.android.core.TypeScale
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.kenai.android.R
import com.kenai.android.app.AppModel
import com.kenai.android.core.ChatActions
import com.kenai.android.core.ChatDrafts
import com.kenai.android.core.ChatMessage
import com.kenai.android.core.ChatRetry
import com.kenai.android.core.ChatSearch
import com.kenai.android.core.ChatOffline
import com.kenai.android.core.ChatReasons
import com.kenai.android.core.ChatTab
import com.kenai.android.core.ChatTabState
import com.kenai.android.core.Screen
import com.kenai.android.character.BotState
import com.kenai.android.character.CharacterEngine
import com.kenai.android.character.CharacterView
import com.kenai.android.core.ChatRole
import com.kenai.android.core.ChatSession
import com.kenai.android.core.ChatStatus
import com.kenai.android.core.MarkdownLite
import com.kenai.android.core.MdBlock
import com.kenai.android.core.MdSpan
import com.kenai.android.link.Protocol

/** The words for a code the computer (or this app) gives when an answer did not complete. */
@Composable
private fun reasonText(code: String?): String = stringResource(
    when (ChatReasons.key(code)) {
        "off" -> R.string.chat_err_off
        "not_allowed" -> R.string.chat_err_not_allowed
        "busy" -> R.string.chat_err_busy
        "rate" -> R.string.chat_err_rate
        "too_long" -> R.string.chat_err_too_long
        "empty" -> R.string.chat_err_empty
        "no_key" -> R.string.chat_err_no_key
        "unreachable" -> R.string.chat_err_unreachable
        "auth" -> R.string.chat_err_auth
        "provider" -> R.string.chat_err_provider
        "canceled" -> R.string.chat_err_canceled
        "connection" -> R.string.chat_err_connection
        "interrupted" -> R.string.chat_err_interrupted
        else -> R.string.chat_err_internal
    },
)

internal fun chatStateTitle(state: ChatTabState): Int = when (state) {
    ChatTabState.NOT_PAIRED -> R.string.chat_off_not_paired_title
    ChatTabState.NOT_CONNECTED -> R.string.chat_off_offline_title
    ChatTabState.CHAT_OFF -> R.string.chat_off_title
    ChatTabState.NO_MODELS -> R.string.chat_nomodel_title
    ChatTabState.READY -> R.string.chat_title
}

internal fun chatStateBody(state: ChatTabState): Int = when (state) {
    ChatTabState.NOT_PAIRED -> R.string.chat_off_not_paired_body
    ChatTabState.NOT_CONNECTED -> R.string.chat_off_offline_body
    ChatTabState.CHAT_OFF, ChatTabState.NO_MODELS -> R.string.chat_off_hint
    ChatTabState.READY -> R.string.chat_title
}

/** The Chat tab with nothing to chat with: a sleeping Kenai, what is wrong in plain words, and the one button that helps (if any). */
@Composable
private fun ChatEmpty(state: ChatTabState, onHome: () -> Unit, onSettings: () -> Unit) {
    val t = tokens()
    val engine = remember { CharacterEngine({ android.os.SystemClock.elapsedRealtimeNanos() / 1e6 }).apply { setState(BotState.SLEEPING, force = true) } }
    Column(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
        ScreenTitle(stringResource(R.string.chat_title), null)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            CharacterView(engine, Modifier.size(120.dp))
            Text(
                stringResource(chatStateTitle(state)), Modifier.padding(top = 16.dp),
                style = TypeScale.TITLE.style(t.text.c()).copy(textAlign = TextAlign.Center),
            )
            Text(
                stringResource(chatStateBody(state)), Modifier.padding(top = 8.dp, bottom = 20.dp),
                style = TypeScale.BODY.style(t.textDim.c()).copy(textAlign = TextAlign.Center),
            )
            when (ChatTab.action(state)) {
                Screen.HOME -> PillButton(stringResource(R.string.chat_go_pair), onHome, Modifier.fillMaxWidth(), PillKind.PRIMARY)
                Screen.SETTINGS -> PillButton(stringResource(R.string.chat_go_settings), onSettings, Modifier.fillMaxWidth(), PillKind.PRIMARY)
                else -> {}
            }
        }
    }
}

/**
 * Chat with the computer's AI providers, like the PC's chat panel: your messages on the right, the
 * answers as plain light Markdown, three dots while waiting, the model above the box. The computer's
 * API key is used on the computer; this screen only ever has text.
 */
@Composable
fun ChatScreen(model: AppModel, onHome: () -> Unit, onSettings: () -> Unit) {
    // One unsent draft per conversation: switching conversations (or leaving the tab) does not lose what was typed.
    val drafts = rememberSaveable(saver = androidx.compose.runtime.saveable.Saver<ChatDrafts, String>(
        save = { it.get(null) }, restore = { ChatDrafts().also { d -> d.set(null, it) } },
    )) { ChatDrafts() }
    var text by remember(model.chatOpenId) { mutableStateOf(drafts.get(model.chatOpenId)) }
    var refusal by remember { mutableStateOf<ChatSession.Refusal?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ChatConversationOption?>(null) }
    val listState = rememberLazyListState()
    val messages = model.chatMessages

    LaunchedEffect(Unit) { model.chatRefreshModels(); model.chatRefreshConversations() }
    // Follow the answer as it grows.
    val lastLength = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastLength) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    val tabState = model.chatTabState
    val engineDown = ChatOffline.saysEngineDown(model.chatHasConversations)
    // Nothing to chat with and nothing to read: say why, calmly, with the one thing to do about it. With the engine's
    // list on the phone there is always something to read, so the copy of the chat is shown instead (KN-01 AC-2).
    if (tabState != ChatTabState.READY && !model.chatHasConversations && messages.isEmpty()) {
        ChatEmpty(tabState, onHome, onSettings)
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
        ScreenTitle(
            stringResource(R.string.chat_title), null,
            trailing = {
                // The header's rule (behaviour-tested in ChatTabTest): with conversations it opens the list; a desktop of
                // 0.1 keeps Clear, whose sentence is true there.
                when (ChatHeader.action(model.chatHasConversations, messages.isNotEmpty())) {
                    ChatHeaderAction.CONVERSATIONS -> Text(
                        stringResource(R.string.chat_conversations),
                        Modifier.heightIn(min = Spacing.MIN_TOUCH.dp).clickable(role = Role.Button) { listOpen = true }.padding(horizontal = 12.dp, vertical = 14.dp),
                        style = TypeScale.LABEL.style(tokens().textDim.c()), maxLines = 1,
                    )
                    ChatHeaderAction.CLEAR -> Text(
                        stringResource(R.string.chat_clear),
                        Modifier.heightIn(min = Spacing.MIN_TOUCH.dp).clickable(role = Role.Button) { confirmClear = true }.padding(horizontal = 12.dp, vertical = 14.dp),
                        style = TypeScale.LABEL.style(tokens().textDim.c()), maxLines = 1,
                    )
                    ChatHeaderAction.NONE -> {}
                }
            },
        )
        Text(
            stringResource(R.string.chat_note), Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            style = TypeScale.SECONDARY.style(tokens().textDim.c()),
        )
        if (!model.chatAvailable) {
            // The conversation stays readable; the reason sits above it, in the engine's own words when it has one.
            Panel(Modifier.padding(vertical = 4.dp)) {
                Column(Modifier.padding(Gutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(if (engineDown) R.string.chat_engine_down_title else chatStateTitle(tabState)),
                        style = TypeScale.HEADLINE.style(tokens().text.c()),
                    )
                    Text(
                        stringResource(if (engineDown) R.string.chat_engine_down_body else chatStateBody(tabState)),
                        style = TypeScale.SECONDARY.style(tokens().textDim.c()),
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty()) {
                Text(
                    stringResource(R.string.chat_empty), Modifier.align(Alignment.Center).padding(Gutter),
                    style = TypeScale.BODY.style(tokens().textDim.c()).copy(textAlign = TextAlign.Center),
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MessageGap)) {
                    items(messages, key = { it.id }) { m ->
                        Bubble(m)
                        // Retry only on the last turn, only when it failed or was stopped, and only when it could be sent now.
                        val retry = if (m.id == messages.last().id && model.chatAvailable && !model.chatBusy) ChatRetry.textFor(messages) else null
                        if (retry != null) {
                            Text(
                                stringResource(R.string.chat_retry),
                                Modifier.heightIn(min = Spacing.MIN_TOUCH.dp).clickable(role = Role.Button) { refusal = model.chatSend(retry) }
                                    .padding(horizontal = 8.dp, vertical = 14.dp),
                                style = TypeScale.LABEL.style(tokens().send.c()),
                            )
                        }
                    }
                }
            }
        }
        Composer(
            model, text, refusal,
            onText = { text = it; refusal = null; drafts.set(model.chatOpenId, it) },
            onSend = {
                val r = model.chatSend(text)
                if (r == null) { text = ""; refusal = null; drafts.set(model.chatOpenId, "") } else refusal = r
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.chat_clear_title)) },
            text = { Text(stringResource(R.string.chat_clear_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; model.chatClear() }) {
                    Text(stringResource(R.string.chat_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    // Deleting is the one destructive thing the phone can ask the engine to do (DESIGN Q3), and it says so.
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.chat_conversations_delete_title)) },
            text = { Text(stringResource(R.string.chat_conversations_delete_body)) },
            confirmButton = {
                TextButton(onClick = { deleteTarget = null; model.chatDeleteConversation(target.id) }) {
                    Text(stringResource(R.string.chat_conversations_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (listOpen) {
        ConversationsSheet(model, onDismiss = { listOpen = false }, onDelete = { deleteTarget = it })
    }
}

/**
 * The engine's conversations (KN-01): tap one to read it and write in it, the word on the right asks before deleting it
 * for ever, and the button below starts another. Only drawn when an engine that holds conversations answered a list.
 */
@Composable
private fun ConversationsSheet(model: AppModel, onDismiss: () -> Unit, onDelete: (ChatConversationOption) -> Unit) {
    val white = Color(IslandSurface.TEXT)
    val dim = Color(IslandSurface.TEXT_DIM)
    BottomSheetHost(onDismiss = onDismiss) {
        Text(stringResource(R.string.chat_conversations), style = TypeScale.TITLE.style(white))
        var query by rememberSaveable { mutableStateOf("") }
        val all = model.chatConversationList
        // Works from what the phone holds, so it also works with the engine off. Titles only here: the cached messages
        // are not exposed by AppModel (outside this task's surfaces), see the feature document.
        val rows = remember(all, query) { ChatSearch.filter(all, emptyMap(), query) }
        if (all.size > 3 || query.isNotEmpty()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(100) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text(stringResource(R.string.chat_search_hint), color = Color(IslandSurface.TEXT_DIM)) },
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = white, unfocusedTextColor = white, cursorColor = white,
                    focusedBorderColor = dim, unfocusedBorderColor = dim.copy(alpha = 0.4f),
                ),
            )
        }
        if (rows.isEmpty() && query.isNotEmpty()) {
            Text(stringResource(R.string.chat_search_none), Modifier.padding(top = 12.dp), style = TypeScale.SECONDARY.style(dim))
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(top = 6.dp)) {
            items(rows, key = { it.id }) { c ->
                ConversationRow(
                    c,
                    // The one that is still answering cannot go: the engine refuses it, and the phone would orphan the answer.
                    // With the engine off nothing can be deleted either: the frame would never arrive (KN-01 AC-2).
                    canDelete = model.canDeleteConversation(c.id) && model.chatAvailable,
                    onOpen = { model.chatOpenConversation(c.id); onDismiss() },
                    onDelete = { onDelete(c) },
                )
            }
        }
        PillButton(
            stringResource(R.string.chat_conversations_new),
            { model.chatNewConversation(); onDismiss() },
            Modifier.fillMaxWidth().padding(top = 12.dp), kind = PillKind.PRIMARY, enabled = model.chatAvailable, onDark = true,
        )
        PillButton(stringResource(R.string.chat_conversations_close), onDismiss, Modifier.fillMaxWidth().padding(top = 8.dp), onDark = true)
        Text(
            stringResource(R.string.chat_note), Modifier.padding(top = 10.dp),
            style = TypeScale.SECONDARY.style(dim),
        )
    }
}

@Composable
private fun ConversationRow(c: ChatConversationOption, canDelete: Boolean, onOpen: () -> Unit, onDelete: () -> Unit) {
    val t = tokens()
    Row(Modifier.fillMaxWidth().heightIn(min = Spacing.MIN_TOUCH.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            c.title.ifBlank { stringResource(R.string.chat_conversations_new) },
            Modifier.weight(1f).heightIn(min = Spacing.MIN_TOUCH.dp).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 14.dp),
            style = TypeScale.BODY.style(if (c.open) Color(IslandSurface.TEXT) else Color(IslandSurface.TEXT_DIM)),
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.chat_conversations_delete),
            Modifier.alpha(if (canDelete) 1f else 0.4f).heightIn(min = Spacing.MIN_TOUCH.dp)
                .clickable(enabled = canDelete, role = Role.Button, onClick = onDelete).padding(horizontal = 12.dp, vertical = 14.dp),
            style = TypeScale.LABEL.style(t.danger.c()), maxLines = 1,
        )
    }
}

/** KN-03: the artboard's 6dp between messages — tighter than the shared [Gap] (12dp), so it is the chat's own, not the scale's. */
private val MessageGap = 6.dp

@Composable
private fun Bubble(m: ChatMessage) {
    if (m.role == ChatRole.USER) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SelectionContainer {
                Text(
                    m.text,
                    Modifier.widthIn(max = 300.dp)
                        // KN-03: Koda's bubble — radius 12/12/4/12 with the small 4dp corner at the bottom-end (bottom-right
                        // in LTR, like Koda's CSS `12px 12px 4px 12px`), its translucent `--bubble` fill, the 8/12 padding.
                        .clip(RoundedCornerShape(12.dp, 12.dp, 4.dp, 12.dp))
                        .background(tokens().bubble.c())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = TypeScale.BODY.style(tokens().text.c()),
                )
            }
          }
          MessageActions(m)
        }
        return
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (m.text.isEmpty() && m.status == ChatStatus.STREAMING) TypingDots()
        else if (m.text.isNotEmpty()) SelectionContainer { MarkdownText(m.text) }
        if (m.status == ChatStatus.FAILED) {
            Text(reasonText(m.reason), style = TypeScale.SECONDARY.style(tokens().danger.c()))
        }
        MessageActions(m)
    }
}

/** Copy, copy code and share for one finished message. Text selection is the [SelectionContainer] around the text. */
@Composable
private fun MessageActions(m: ChatMessage) {
    if (!ChatActions.available(m)) return
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val t = tokens()
    val code = remember(m.text) { ChatActions.codeBlocks(m.text) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val style = TypeScale.SECONDARY.style(t.textDim.c())
        @Composable fun action(label: Int, onClick: () -> Unit) = Text(
            stringResource(label),
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
            style = style,
        )
        action(R.string.chat_copy) { clipboard.setText(AnnotatedString(ChatActions.copyText(m))) }
        if (code.isNotEmpty()) action(R.string.chat_copy_code) { clipboard.setText(AnnotatedString(code.first())) }
        action(R.string.chat_share) {
            val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, ChatActions.copyText(m))
            runCatching { context.startActivity(android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/** The three dots of the PC's chat; they only move while this is on screen. */
@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "dots",
    )
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val on = phase.toInt() == i
            Box(
                Modifier.size(8.dp).clip(CircleShape)
                    .background(tokens().textDim.c().copy(alpha = if (on) 1f else 0.35f)),
            )
        }
    }
}

@Composable
private fun MarkdownText(text: String) {
    val blocks = remember(text) { MarkdownLite.parse(text) }
    val t = tokens()
    val code = t.panel2.c()
    val body = TypeScale.BODY.style(t.text.c())
    val linkColor = t.send.c()
    // A link is never opened by itself: a tap shows the address first and opens it only on a second, explicit tap.
    var pending by remember { mutableStateOf<String?>(null) }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val onLink: (String) -> Unit = { if (MarkdownLite.isWebAddress(it)) pending = it }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) {
            when (b) {
                is MdBlock.Para -> Text(styled(b.spans, code, linkColor, onLink), style = body)
                is MdBlock.Heading -> Text(styled(b.spans, code, linkColor, onLink), style = body.copy(fontWeight = FontWeight.Bold))
                is MdBlock.Bullet -> Row(Modifier.padding(start = (16 * b.depth).dp)) {
                    Text(if (b.depth == 0) "•  " else "◦  ", style = body)
                    Text(styled(b.spans, code, linkColor, onLink), Modifier.weight(1f), style = body)
                }
                is MdBlock.Numbered -> Row(Modifier.padding(start = (16 * b.depth).dp)) {
                    Text("${b.number}.  ", style = body)
                    Text(styled(b.spans, code, linkColor, onLink), Modifier.weight(1f), style = body)
                }
                is MdBlock.Table -> Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, t.line.c(), RoundedCornerShape(12.dp)),
                ) {
                    TableRow(b.header, code, linkColor, onLink, body.copy(fontWeight = FontWeight.Bold), Modifier.background(code))
                    for (r in b.rows) TableRow(r, code, linkColor, onLink, body, Modifier)
                }
                is MdBlock.Code -> Text(
                    b.text,
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(code)
                        .border(1.dp, t.line.c(), RoundedCornerShape(12.dp)).padding(12.dp),
                    style = TypeScale.MONO.style(t.text.c()).copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
    pending?.let { target ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.chat_link_title)) },
            text = { Text(target) },
            confirmButton = { TextButton(onClick = { pending = null; runCatching { uri.openUri(MarkdownLite.normalizedAddress(target)) } }) { Text(stringResource(R.string.chat_link_open)) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.chat_link_cancel)) } },
        )
    }
}

@Composable
private fun TableRow(
    cells: List<List<MdSpan>>, codeBackground: Color, linkColor: Color, onLink: (String) -> Unit,
    style: androidx.compose.ui.text.TextStyle, modifier: Modifier,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (c in cells) Text(styled(c, codeBackground, linkColor, onLink), Modifier.weight(1f), style = style)
    }
}

private fun styled(spans: List<MdSpan>, codeBackground: Color, linkColor: Color, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        val style = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontFamily = if (s.code) FontFamily.Monospace else null,
            background = if (s.code) codeBackground else Color.Unspecified,
        )
        val target = s.link
        if (target != null) {
            val link = androidx.compose.ui.text.LinkAnnotation.Clickable(
                tag = target,
                styles = androidx.compose.ui.text.TextLinkStyles(
                    SpanStyle(color = linkColor, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                ),
                linkInteractionListener = { onLink(target) },
            )
            withLink(link) { withStyle(style) { append(s.text) } }
        } else withStyle(style) { append(s.text) }
    }
}

@Composable
private fun Composer(model: AppModel, text: String, refusal: ChatSession.Refusal?, onText: (String) -> Unit, onSend: () -> Unit) {
    val limit = Protocol.CHAT_MAX_TEXT
    val length = text.trim().length
    val t = tokens()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ModelChip(model)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text, onValueChange = { onText(it.take(limit + 200)) }, modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_placeholder), color = t.textFaint.c()) },
                maxLines = 4, shape = RoundedCornerShape(26.dp), enabled = model.chatAvailable,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = t.panel.c(), unfocusedContainerColor = t.panel.c(), disabledContainerColor = t.panel.c(),
                    focusedBorderColor = t.textDim.c(), unfocusedBorderColor = t.line.c(), disabledBorderColor = t.line.c(),
                    focusedTextColor = t.text.c(), unfocusedTextColor = t.text.c(), cursorColor = t.text.c(),
                ),
            )
            if (model.chatBusy) {
                PillButton(stringResource(R.string.chat_stop), { model.chatCancel() }, Modifier.height(56.dp))
            } else {
                val canSend = model.chatAvailable && length in 1..limit
                val send = stringResource(R.string.chat_send)
                Box(
                    Modifier.alpha(if (canSend) 1f else 0.4f).size(56.dp).clip(CircleShape).background(t.send.c())
                        .clickable(enabled = model.chatAvailable && length in 1..limit, role = Role.Button, onClick = onSend)
                        .semantics { contentDescription = send },
                    contentAlignment = Alignment.Center,
                ) { KenaiIcon(IconKind.SEND, tint = t.onSend.c(), size = 22.dp) }
            }
        }
        val note = when {
            refusal == ChatSession.Refusal.TOO_LONG || length > limit -> stringResource(R.string.chat_ref_too_long)
            refusal == ChatSession.Refusal.NO_MODEL -> stringResource(R.string.chat_ref_no_model)
            refusal == ChatSession.Refusal.BUSY -> stringResource(R.string.chat_ref_busy)
            refusal == ChatSession.Refusal.OFFLINE -> stringResource(R.string.chat_ref_offline)
            else -> null
        }
        if (note != null) Text(note, style = TypeScale.SECONDARY.style(t.danger.c()))
        else if (length > limit * 9 / 10) {
            Text(stringResource(R.string.chat_count, length, limit), style = TypeScale.SECONDARY.style(t.textDim.c()))
        }
    }
}

/** The model above the box, like the PC's: tap to pick another of the ones the computer allows. */
@Composable
private fun ModelChip(model: AppModel) {
    var open by remember { mutableStateOf(false) }
    val models = model.chatModels
    val current = models.firstOrNull { it.id == model.chatModel }
    val t = tokens()
    Box {
        Row(
            Modifier.heightIn(min = Spacing.MIN_TOUCH.dp).clip(CircleShape).background(t.panel2.c())
                .clickable(enabled = models.size > 1, role = Role.Button) { open = true }.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.chat_model, current?.label ?: "—"), style = TypeScale.LABEL.style(t.text.c()), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (models.size > 1) KenaiIcon(IconKind.CHEVRON, tint = t.textDim.c(), size = 16.dp, modifier = Modifier.rotate(90f))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (m in models) {
                DropdownMenuItem(
                    text = { Text(m.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { model.chatSelect(m.id); open = false },
                )
            }
        }
    }
}
