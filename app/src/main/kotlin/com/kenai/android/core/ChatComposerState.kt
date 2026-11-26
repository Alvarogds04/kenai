package com.kenai.android.core

/**
 * What the composer remembers while the user moves around (KN-15 T4): one unsent draft per conversation, and the text a
 * failed turn would resend. Pure; nothing is sent from here: a retry goes through the same `chatSend` as any message.
 */
class ChatDrafts(private val max: Int = 50) {
    companion object {
        /** Rebuilds the drafts from [save]'s list; a list that does not make pairs keeps only the whole pairs. */
        fun restore(saved: List<String>, max: Int = 50): ChatDrafts = ChatDrafts(max).also { d ->
            for (i in 0 until saved.size / 2) d.set(saved[2 * i], saved[2 * i + 1])
        }
    }

    /** Every draft, oldest first, as a flat list of key, text, key, text... — what the saveable state of a rotation or a
     *  process death can hold. The key of the 0.1 single chat is the empty string. */
    fun save(): List<String> = drafts.flatMap { (k, v) -> listOf(k, v) }

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

/** KN-15 T5: regenerate and edit are NOT in the protocol (no frame rewrites a turn), so the client only offers new turns. */
object ChatAgain {
    /** "Ask again": the text of the user message before the LAST finished answer, resent as a new turn. Null otherwise. */
    fun textFor(messages: List<ChatMessage>): String? {
        val last = messages.lastOrNull() ?: return null
        if (last.role != ChatRole.ASSISTANT || last.status != ChatStatus.DONE || last.text.isBlank()) return null
        val before = messages.getOrNull(messages.size - 2) ?: return null
        return before.text.takeIf { before.role == ChatRole.USER && it.isNotBlank() }
    }

    /** "Edit": any user message can be loaded into the composer; sending it is a new turn, the old one stays in the history. */
    fun editable(m: ChatMessage): Boolean = m.role == ChatRole.USER && m.text.isNotBlank()

    /** The composer after "Edit": a blank draft becomes the message; a draft being typed is kept and the message is added below it. */
    fun editDraft(current: String, m: ChatMessage): String = when {
        current.isBlank() -> m.text
        current.trim() == m.text.trim() -> current
        else -> current.trimEnd() + "\n" + m.text
    }
}
