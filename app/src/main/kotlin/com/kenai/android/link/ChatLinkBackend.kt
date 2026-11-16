package com.kenai.android.link

import com.kenai.android.core.ChatBackend
import com.kenai.android.core.ChatModelOption

/**
 * The chat as it is today: carried over the phone link to the computer, which talks to its own providers
 * (docs/ANDROID_LINK.md, Chat). The first [ChatBackend], and the only place that knows the wire: the chat methods of
 * [DesktopLink] and the four chat callbacks of [LinkListener], translated into the neutral [ChatBackend.Listener].
 */
class ChatLinkBackend(
    /** The link in use right now; null when nothing is paired or the app has stopped it. */
    private val link: () -> DesktopLink?,
    private val listener: ChatBackend.Listener,
) : ChatBackend, LinkListener {
    /** What the computer accepts for one message; a longer one is refused there, so it is refused here first. */
    override val maxText: Int = Protocol.CHAT_MAX_TEXT

    override fun models(): Boolean {
        val l = link() ?: return false
        l.chatModels()
        return true
    }

    override fun send(id: String, model: String, text: String): Boolean = link()?.chatSend(id, model, text) == true

    override fun cancel(id: String) {
        link()?.chatCancel(id)
    }

    override fun reset() {
        link()?.chatReset()
    }

    // ── LinkListener (link thread): the wire's four chat events in the app's own words ────────────

    override fun onChatModels(models: List<ChatModel>) {
        listener.onModels(models.map { ChatModelOption(it.id, it.provider, it.label) })
    }

    override fun onChatDelta(id: String, text: String) {
        listener.onDelta(id, text)
    }

    override fun onChatDone(id: String, text: String?) {
        listener.onDone(id, text)
    }

    override fun onChatError(id: String, reason: String, message: String) {
        listener.onError(id, reason, message)
    }
}
