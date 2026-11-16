package com.kenai.android.core

/**
 * The engine's conversations as the phone keeps them (KN-01): the list it last sent, which one is open, and, for this
 * run of the app, the messages of every conversation the user has shown.
 *
 * Only a backend with several conversations ever answers a list; a desktop of 0.1 never does, [known] stays false and
 * the chat is the single conversation of 0.1 — nothing here changes it. Pure Kotlin: no clock, no Android and no wire,
 * so every rule is unit-tested; `AppModel` owns the backend and the screen renders what this says.
 */
class ChatConversations {
    /** The whole list, as the engine last sent it: never a patch, and empty until it sends one or when it holds none. */
    var items: List<ChatConversationOption> = emptyList(); private set

    /** The engine answered a list at least once, so it has conversations. A desktop of 0.1 never does. */
    var known: Boolean = false; private set

    /** The conversation on screen, as the engine has it open; null when there is none, or no list has arrived yet. */
    var open: String? = null; private set

    /** What the phone holds of each conversation it has shown. Kept for one run; persisting it is T10. */
    private val held = HashMap<String, List<ChatMessage>>()

    /** A list arrived while an answer was streaming: it moves the screen when that answer ends ([turnEnded]). */
    var switchPending: Boolean = false; private set

    /**
     * A list arrived. Returns the messages the screen must show when the open conversation changed, and null when it
     * did not: a list with no conversation marked open says nothing about what the phone shows, so it keeps it.
     *
     * [running] is the phone's own answer being streamed: nothing switches under it (the engine refuses to open another
     * conversation mid-turn for the same reason), so the list is remembered and applied by [turnEnded].
     */
    fun onList(next: List<ChatConversationOption>, showing: List<ChatMessage>, running: Boolean = false): List<ChatMessage>? {
        items = next
        known = true
        // The full list is the whole truth about what exists: what the phone held for a conversation the engine no longer
        // has goes with it, so it can neither be opened again nor come back from the dead.
        held.keys.retainAll(next.mapTo(HashSet()) { it.id })
        val wanted = next.firstOrNull { it.open }?.id ?: return null
        if (open == null) {
            // The first list: the messages already on screen are the ones of the conversation the engine has open (the
            // phone was writing there before it knew the id), so they stay and the screen does not move.
            open = wanted
            held[wanted] = showing
            switchPending = false
            return null
        }
        if (wanted == open) {
            switchPending = false
            return null
        }
        if (running) {
            switchPending = true
            return null
        }
        switchPending = false
        return switch(wanted, showing)
    }

    /**
     * The answer is over: a list that arrived while it was streaming moves the screen now, to the conversation the
     * engine has open in the latest of those lists. Null when nothing was waiting, or it names the one already shown.
     */
    fun turnEnded(showing: List<ChatMessage>): List<ChatMessage>? {
        if (!switchPending) return null
        switchPending = false
        val wanted = items.firstOrNull { it.open }?.id ?: return null
        if (wanted == open) return null
        if (open == null) {
            open = wanted
            held[wanted] = showing
            return null
        }
        return switch(wanted, showing)
    }

    /** May [id] be deleted now? The open conversation may not while its answer streams: the phone would orphan it. */
    fun canDelete(id: String, running: Boolean): Boolean = !(running && id == open)

    /** The user opened [id] (or created it): the screen switches at once, without waiting for the engine's next list. */
    fun opened(id: String, showing: List<ChatMessage>): List<ChatMessage> {
        items = items.map { it.copy(open = it.id == id) }
        switchPending = false
        return switch(id, showing)
    }

    /** The messages on screen changed: they are what the phone holds for the open conversation. */
    fun keep(showing: List<ChatMessage>) {
        open?.let { held[it] = showing }
    }

    /**
     * The engine deleted [id] (Q11): what the phone held for it goes, and the row leaves the list. Returns the empty
     * screen when the conversation that was showing is the one deleted, else null.
     */
    fun onDeleted(id: String): List<ChatMessage>? {
        held.remove(id)
        items = items.filter { it.id != id }
        if (switchPending && items.firstOrNull { it.open }?.id == null) switchPending = false
        if (open != id) return null
        open = null
        return emptyList()
    }

    /** Clear: the phone forgets what it held of the open conversation (the engine starts a new one on `chatReset`). */
    fun cleared(): List<ChatMessage> {
        open?.let { held.remove(it) }
        open = null
        switchPending = false
        return emptyList()
    }

    /** Another computer is paired (or this one is forgotten): nothing of its conversations stays on the phone. */
    fun reset() {
        items = emptyList()
        known = false
        open = null
        switchPending = false
        held.clear()
    }

    private fun switch(to: String, showing: List<ChatMessage>): List<ChatMessage> {
        // What was on screen is kept for the conversation it belonged to only while the engine still lists it: a
        // conversation the list no longer names cannot be opened again, so its messages must not come back.
        open?.takeIf { id -> items.any { it.id == id } }?.let { held[it] = showing }
        if (to != open) open = to
        return held[to].orEmpty()
    }
}
