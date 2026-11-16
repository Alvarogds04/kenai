package com.kenai.android.core

enum class ChatRole { USER, ASSISTANT }

enum class ChatStatus {
    DONE,
    /** The answer is still arriving. */
    STREAMING,
    /** It stopped before the end; [ChatMessage.reason] says why and the text is what arrived. */
    FAILED,
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val status: ChatStatus = ChatStatus.DONE,
    /** A fixed code from the backend (rate, busy, auth...) or one of ours (canceled, connection, interrupted). */
    val reason: String? = null,
)

/**
 * The phone's side of one conversation with a [ChatBackend] (docs/ANDROID_LINK.md, Chat for the link): what is on
 * screen, which answer is being waited for, and what each of the backend's four events does. Pure Kotlin, no clock and
 * no Android, so every rule is unit-tested. The backend keeps its own copy of the conversation; this one is what the
 * user reads.
 */
class ChatSession(
    initial: List<ChatMessage> = emptyList(),
    private val newId: () -> String = ChatIds::random,
) {
    var messages: List<ChatMessage> = initial.takeLast(MAX_MESSAGES); private set

    /** The request id of the answer being waited for; one at a time. */
    var running: String? = null; private set

    enum class Refusal { EMPTY, TOO_LONG, NO_MODEL, BUSY, OFFLINE }

    sealed interface Sent {
        /** Validated and ready for a [ChatBackend]: the id to reach it by, the model and the text, and nothing of the wire. */
        data class Ok(val id: String, val model: String, val text: String) : Sent
        data class No(val why: Refusal) : Sent
    }

    /**
     * Starts a question. Nothing changes unless it is sent. [maxText] is the limit of the backend it will be handed to,
     * so a backend that accepts more (or less) than the link does needs no change here.
     */
    fun send(text: String, model: String?, connected: Boolean, maxText: Int): Sent {
        val clean = text.trim()
        if (clean.isEmpty()) return Sent.No(Refusal.EMPTY)
        if (clean.length > maxText) return Sent.No(Refusal.TOO_LONG)
        if (model.isNullOrBlank()) return Sent.No(Refusal.NO_MODEL)
        if (running != null) return Sent.No(Refusal.BUSY)
        if (!connected) return Sent.No(Refusal.OFFLINE)
        // The id rule (1..64 of [A-Za-z0-9_-]) lives in the wire, `Wire.isChatId`, and the tests check these ids
        // against it; this class only has to generate them.
        val id = newId()
        running = id
        messages = (messages + ChatMessage("u-$id", ChatRole.USER, clean) +
            ChatMessage(id, ChatRole.ASSISTANT, "", ChatStatus.STREAMING)).takeLast(MAX_MESSAGES)
        return Sent.Ok(id, model, clean)
    }

    /** Text of the answer being written; anything else (a late piece after Cancel) is ignored. */
    fun onDelta(id: String, text: String) {
        if (id != running) return
        change(id) { it.copy(text = (it.text + text).take(MAX_TEXT), status = ChatStatus.STREAMING) }
    }

    /** The end of the answer; [full] replaces what was streamed when the computer had to correct it. */
    fun onDone(id: String, full: String?) {
        if (id != running) return
        change(id) { it.copy(text = (full ?: it.text).take(MAX_TEXT), status = ChatStatus.DONE, reason = null) }
        running = null
    }

    fun onError(id: String, reason: String) {
        if (id != running) return
        fail(id, reason)
    }

    /** The user pressed Cancel: stop waiting at once. Returns the id to tell the computer about, or null. */
    fun cancel(): String? {
        val id = running ?: return null
        fail(id, "canceled")
        return id
    }

    /** The link dropped: an answer on its way will not arrive. */
    fun onDisconnected() {
        running?.let { fail(it, "connection") }
    }

    /** New chat / Clear. */
    fun clear() {
        messages = emptyList()
        running = null
    }

    private fun fail(id: String, reason: String) {
        change(id) { it.copy(status = ChatStatus.FAILED, reason = reason) }
        running = null
    }

    private fun change(id: String, f: (ChatMessage) -> ChatMessage) {
        messages = messages.map { if (it.id == id && it.role == ChatRole.ASSISTANT) f(it) else it }
    }

    companion object {
        const val MAX_MESSAGES = 200
        /** The computer never sends more than this for one answer. */
        const val MAX_TEXT = 40_000
    }
}

object ChatIds {
    private val rnd = java.security.SecureRandom()
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    /** 12 random characters; the id only has to be unique within a conversation. */
    fun random(): String = "c" + (1..12).map { ALPHABET[rnd.nextInt(ALPHABET.length)] }.joinToString("")
}

object ChatModels {
    /** The model to use once the list arrives: the saved one if still allowed, else the first. */
    fun pick(models: List<ChatModelOption>, saved: String?): String? =
        models.firstOrNull { it.id == saved }?.id ?: models.firstOrNull()?.id
}
