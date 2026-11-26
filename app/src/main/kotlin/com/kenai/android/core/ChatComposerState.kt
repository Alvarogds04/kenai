package com.kenai.android.core

/**
 * What the composer remembers while the user moves around (KN-15 T4): one unsent draft per conversation, and the text a
 * failed turn would resend. Pure; nothing is sent from here: a retry goes through the same `chatSend` as any message.
 */
class ChatDrafts(private val max: Int = 50) {
    private val drafts = LinkedHashMap<String, String>()

    /** The draft of conversation [id] (null is the 0.1 single chat); empty when there is none. */
    fun get(id: String?): String = drafts[id.orEmpty()].orEmpty()

    /** Keeps [text] for [id]; an empty draft is forgotten. The oldest ones go when there are more than [max]. */
    fun set(id: String?, text: String) {
        val key = id.orEmpty()
        drafts.remove(key)
        if (text.isNotEmpty()) drafts[key] = text
        while (drafts.size > max) drafts.remove(drafts.keys.first())
    }
}

object ChatRetry {
    /**
     * The text to resend when the LAST turn failed or was stopped: the user message right before the failed answer.
     * Null when the last message is not a failed answer, or nothing came before it. A retry is a new turn with the
     * same text (the engine decides what it does with the old one), never a rewrite of the old one.
     */
    fun textFor(messages: List<ChatMessage>): String? {
        val last = messages.lastOrNull() ?: return null
        if (last.role != ChatRole.ASSISTANT || last.status != ChatStatus.FAILED) return null
        val before = messages.getOrNull(messages.size - 2) ?: return null
        return before.text.takeIf { before.role == ChatRole.USER && it.isNotBlank() }
    }
}
