package com.kenai.android.core

/**
 * A model a backend offers. [id] is what the phone sends back with the question; [provider] is the short name the
 * backend groups it under, and [label] is what the chip shows.
 */
data class ChatModelOption(val id: String, val provider: String, val label: String)

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
     * now, for example because the link is down; the answer, if any, comes through the [Listener].
     */
    fun send(id: String, model: String, text: String): Boolean

    /** The user pressed Cancel for [id]: nothing more will come for it. */
    fun cancel(id: String)

    /** Clear: the conversation is forgotten on the backend's side too. */
    fun reset()

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
    }
}
