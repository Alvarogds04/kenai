package com.kenai.android.core

import java.text.Normalizer

/**
 * Local search over the conversations the phone holds (KN-15 T3): the title and, when the cached messages are given,
 * their text. Pure and offline: it reads only what [ChatConversations] already keeps, and nothing goes to the engine.
 */
object ChatSearch {
    /** Lower case without accents, so "cancion" finds "Canción". */
    internal fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    /**
     * The conversations in which every word of [query] appears (in the title or in a cached message), in their
     * original order. A blank query is no search: the list comes back as it is.
     */
    fun filter(items: List<ChatConversationOption>, held: Map<String, List<ChatMessage>>, query: String): List<ChatConversationOption> {
        return index(items, held).filter(query)
    }

    /** The folded text of every conversation, built once: typing then only looks words up in it. */
    class Index internal constructor(private val items: List<ChatConversationOption>, private val haystacks: List<String>) {
        fun filter(query: String): List<ChatConversationOption> {
            val words = words(query)
            if (words.isEmpty()) return items
            return items.filterIndexed { i, _ -> words.all { haystacks[i].contains(it) } }
        }
    }

    fun index(items: List<ChatConversationOption>, held: Map<String, List<ChatMessage>>): Index =
        Index(items, items.map { c -> fold(c.title + "\n" + held[c.id].orEmpty().joinToString("\n") { it.text }) })

    private fun words(query: String) = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
}
