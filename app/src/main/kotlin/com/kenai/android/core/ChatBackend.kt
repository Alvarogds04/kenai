package com.kenai.android.core

/**
 * A model a backend offers. [id] is what the phone sends back with the question; [provider] is the short name the
 * backend groups it under, and [label] is what the chip shows.
 */
data class ChatModelOption(val id: String, val provider: String, val label: String)

/**
 * A conversation the backend holds: its [id], the [title] the backend names it with, when it last changed, and whether
 * it is the one open there. Only a backend with several conversations sends these.
 */
data class ChatConversationOption(val id: String, val title: String, val updatedAtMs: Long = 0, val open: Boolean = false)

/**
 * Where the chat goes. Today the phone link to the computer, which talks to its own providers; later also a provider
 * the phone calls itself (T1a-2). Pure Kotlin, so [ChatSession] and its tests never see the wire: the link is one
 * implementation and the only place that knows `ClientMsg` and `ServerMsg`.
 */
interface ChatBackend {
    /** The longest question this backend accepts, in characters. Its own rule, not a model's. */
    val maxText: Int

    /**
     * Asks which models are offered right now; false when the backend cannot ask (the link is down).
     * The list comes back through [Listener.onModels].
     */
    fun models(): Boolean

    /**
     * Starts a question: [id], [model] and [text] are already validated by [ChatSession]. False when it cannot be sent
     * now, for example because the link is down; the answer, if any, comes through the [Listener]. [conversation] picks
     * the backend's conversation (absent: its open one, which is all a backend with one conversation has).
     */
    fun send(id: String, model: String, text: String, conversation: String? = null): Boolean

    /** The user pressed Cancel for [id]: nothing more will come for it. */
    fun cancel(id: String)

    /** Clear: the conversation is forgotten on the backend's side too. */
    fun reset()

    // ── Several conversations (the engine). A backend that has only one leaves these as they are. ──

    /**
     * Asks the backend for its conversation list; false when it has none to ask for (a desktop of 0.1) or cannot ask now.
     * The answer comes through [Listener.onConversations]; the first one is what tells the phone the list is there.
     */
    fun conversations(): Boolean = false

    /** A new conversation (the backend opens it too); false when the backend has no conversations. */
    fun newConversation(title: String?): Boolean = false

    /** Opens [id] on the backend's side; false when it has no conversations or that one is not there. */
    fun openConversation(id: String): Boolean = false

    /** Irreversible: the backend removes the conversation and the history it keeps (Q3). The caller asks the user first. */
    fun deleteConversation(id: String): Boolean = false

    /** The four things a backend says about a conversation. Called on the backend's own thread. */
    interface Listener {
        /** The models offered right now; empty means none. */
        fun onModels(models: List<ChatModelOption>)

        /** More text of the answer to [id]. */
        fun onDelta(id: String, text: String)

        /** The end of the answer to [id]; [text] replaces what was streamed when the backend had to correct it. */
        fun onDone(id: String, text: String?)

        /** It stopped: [reason] is a fixed code and [message] a sentence of the backend's own (may be blank). */
        fun onError(id: String, reason: String, message: String)

        /** The backend's conversations, whole list; the first one means it has them and the chat is not the 0.1 one. */
        fun onConversations(items: List<ChatConversationOption>) {}

        /** The backend deleted [id]: what the phone held for it goes, it cannot be opened there any more (Q11). */
        fun onConversationDeleted(id: String) {}
    }
}
