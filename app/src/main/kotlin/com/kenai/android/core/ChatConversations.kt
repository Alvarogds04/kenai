package com.kenai.android.core

/**
 * The engine's conversations as the phone keeps them (KN-01): the list it last sent, which one is open, and the messages
 * of every conversation the user has shown — kept in a private file between two runs, so the chat can be read with the
 * engine off (AC-2).
 *
 * Only a backend with several conversations ever answers a list; a desktop of 0.1 never does, [known] stays false, nothing
 * is ever written and the chat is the single conversation of 0.1 — nothing here changes it. Pure Kotlin: no clock, no
 * Android and no wire, so every rule is unit-tested; `AppModel` owns the backend and the screen renders what this says.
 */
class ChatConversations(private val store: TextFile? = null) {
    /** The whole list, as the engine last sent it: never a patch, and empty until it sends one or when it holds none. */
    var items: List<ChatConversationOption> = emptyList(); private set

    /** The engine answered a list at least once, so it has conversations. A desktop of 0.1 never does. */
    var known: Boolean = false; private set

    /** The conversation on screen, as the engine has it open; null when there is none, or no list has arrived yet. */
    var open: String? = null; private set

    /** What the phone holds of each conversation it has shown, least recently opened first. Kept in [store]. */
    private val held = LinkedHashMap<String, List<ChatMessage>>()

    /** Something changed that the file does not have yet. */
    private var dirty = false

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
        val shown = onOpenIn(next, showing, running)
        changed()
        return shown
    }

    private fun onOpenIn(next: List<ChatConversationOption>, showing: List<ChatMessage>, running: Boolean): List<ChatMessage>? {
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
            keepHeld(wanted, showing)
            changed()
            return null
        }
        val shown = switch(wanted, showing)
        changed()
        return shown
    }

    /** May [id] be deleted now? The open conversation may not while its answer streams: the phone would orphan it. */
    fun canDelete(id: String, running: Boolean): Boolean = !(running && id == open)

    /** The user opened [id] (or created it): the screen switches at once, without waiting for the engine's next list. */
    fun opened(id: String, showing: List<ChatMessage>): List<ChatMessage> {
        items = items.map { it.copy(open = it.id == id) }
        switchPending = false
        val shown = switch(id, showing)
        changed()
        return shown
    }

    /** The messages on screen changed: they are what the phone holds for the open conversation. Written by [persist]. */
    fun keep(showing: List<ChatMessage>) {
        val id = open ?: return
        keepHeld(id, showing)
        dirty = true
    }

    /** Writes the copy when it changed: after a turn ends, so an answer is not rewritten on every piece of it. */
    fun persist() {
        if (!dirty) return
        dirty = false
        val snapshot = snapshot()
        if (!snapshot.known && snapshot.items.isEmpty() && snapshot.held.isEmpty()) store?.delete()
        else store?.write(ChatCache.encode(snapshot))
    }

    /** What the phone would write: the whole copy, as [ChatCache] reads and writes it. */
    fun snapshot(): ChatCacheSnapshot = ChatCacheSnapshot(known, items, open, held)

    /** The messages the phone held for the open conversation: what the screen shows when the app starts again. */
    fun openMessages(): List<ChatMessage> = open?.let { held[it] }.orEmpty()

    /**
     * The engine deleted [id] (Q11): what the phone held for it goes, and the row leaves the list. Returns the empty
     * screen when the conversation that was showing is the one deleted, else null.
     */
    fun onDeleted(id: String): List<ChatMessage>? {
        held.remove(id)
        items = items.filter { it.id != id }
        if (switchPending && items.firstOrNull { it.open }?.id == null) switchPending = false
        changed()
        if (open != id) return null
        open = null
        return emptyList()
    }

    /** Clear: the phone forgets what it held of the open conversation (the engine starts a new one on `chatReset`). */
    fun cleared(): List<ChatMessage> {
        open?.let { held.remove(it) }
        open = null
        switchPending = false
        changed()
        return emptyList()
    }

    /** Another computer is paired (or this one is forgotten): nothing of its conversations stays on the phone. */
    fun reset() {
        items = emptyList()
        known = false
        open = null
        switchPending = false
        held.clear()
        changed()
    }

    private fun switch(to: String, showing: List<ChatMessage>): List<ChatMessage> {
        // What was on screen is kept for the conversation it belonged to only while the engine still lists it: a
        // conversation the list no longer names cannot be opened again, so its messages must not come back.
        open?.takeIf { id -> items.any { it.id == id } }?.let { keepHeld(it, showing) }
        if (to != open) open = to
        return held[to].orEmpty()
    }

    /** Holds [messages] for [id], as the most recently opened one, and bounds how many conversations keep a copy. */
    private fun keepHeld(id: String, messages: List<ChatMessage>) {
        held.remove(id)
        held[id] = messages
        while (held.size > ChatCache.MAX_HELD) held.remove(held.keys.first())
    }

    private fun changed() {
        dirty = true
        persist()
    }

    companion object {
        /**
         * The copy a previous run left in [store] (or a bank desktop of 0.1, which has none): the list, the open
         * conversation and what the user read of them, ready to be shown with no engine answering.
         */
        fun restored(store: TextFile? = null): ChatConversations {
            val snapshot = ChatCache.decode(store?.read())
            return ChatConversations(store).apply {
                items = snapshot.items
                known = snapshot.known
                open = snapshot.open
                held.putAll(snapshot.held)
            }
        }
    }
}
