package com.kenai.android.core

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * What the phone keeps of the engine's conversations between two runs of the app (KN-01 AC-2): the list the engine last
 * sent, which conversation was open and a bounded copy of what the user read of some of them, so the chat can be read
 * with the engine off. Nothing of the wire is in it: ids, titles, roles, text and a fixed reason code, so it can hold no
 * key, no address and no token (AC-4 by construction). A desktop of 0.1 never sends a list, so it never writes one.
 */
data class ChatCacheSnapshot(
    /** The engine answered a list, so it holds conversations. */
    val known: Boolean = false,
    val items: List<ChatConversationOption> = emptyList(),
    /** The conversation that was open on screen. */
    val open: String? = null,
    /** What the phone showed of each conversation it opened, least recently opened first. */
    val held: Map<String, List<ChatMessage>> = emptyMap(),
)

/**
 * The copy's file format. It is bounded on purpose: a chat the user may want to read again is not an archive of every
 * answer, and a phone should never grow a file of tens of megabytes for a chat it can ask the engine for.
 */
object ChatCache {
    private const val VERSION = 1
    /** The engine sends at most 200; a longer list is cut by the phone. */
    const val MAX_CONVERSATIONS = 200
    /** How many conversations keep a readable copy: the ones opened most recently. */
    const val MAX_HELD = 10
    /** Per conversation, the newest messages. */
    const val MAX_MESSAGES = 50
    /** Per message, the beginning of its text; the whole answer lives on the engine. */
    const val MAX_TEXT = 4_000
    private const val MAX_TITLE = 120
    private val ID = Regex("[A-Za-z0-9_-]{1,64}")

    fun encode(snapshot: ChatCacheSnapshot): String {
        val items = snapshot.items.take(MAX_CONVERSATIONS)
        val ids = items.mapTo(HashSet()) { it.id }
        val list = JSONArray()
        for (c in items) {
            list.put(
                JSONObject().put("id", c.id).put("title", c.title.take(MAX_TITLE))
                    .put("updatedAt", c.updatedAtMs).put("open", c.open),
            )
        }
        // The list is the whole truth about what exists: a conversation it no longer names keeps no copy here either.
        val copies = JSONArray()
        for ((id, messages) in snapshot.held.entries.filter { it.key in ids }.takeLast(MAX_HELD)) {
            val a = JSONArray()
            for (m in messages.takeLast(MAX_MESSAGES)) a.put(message(m))
            copies.put(JSONObject().put("id", id).put("messages", a))
        }
        return JSONObject().put("v", VERSION).put("known", snapshot.known).put("items", list)
            .put("open", snapshot.open ?: JSONObject.NULL).put("held", copies).toString()
    }

    /** Reading a damaged or unknown file gives nothing rather than an error: the chat then starts as it did in 0.1. */
    fun decode(text: String?): ChatCacheSnapshot {
        if (text.isNullOrBlank()) return ChatCacheSnapshot()
        return try {
            val o = JSONObject(text)
            if (o.optInt("v", 0) != VERSION) return ChatCacheSnapshot()
            val items = conversations(o.optJSONArray("items"))
            ChatCacheSnapshot(
                known = o.optBoolean("known", false),
                items = items,
                open = o.optString("open", "").takeIf { id -> items.any { it.id == id } },
                held = held(o.optJSONArray("held"), items),
            )
        } catch (_: JSONException) {
            ChatCacheSnapshot()
        }
    }

    private fun conversations(a: JSONArray?): List<ChatConversationOption> {
        a ?: return emptyList()
        val out = ArrayList<ChatConversationOption>()
        for (i in 0 until minOf(a.length(), MAX_CONVERSATIONS)) {
            val c = a.optJSONObject(i) ?: continue
            val id = c.optString("id", "")
            if (!ID.matches(id) || out.any { it.id == id }) continue
            out += ChatConversationOption(
                id = id,
                title = c.optString("title", "").take(MAX_TITLE),
                updatedAtMs = c.optLong("updatedAt", 0).coerceAtLeast(0),
                open = c.optBoolean("open", false),
            )
        }
        return out
    }

    private fun held(a: JSONArray?, items: List<ChatConversationOption>): Map<String, List<ChatMessage>> {
        a ?: return emptyMap()
        val out = LinkedHashMap<String, List<ChatMessage>>()
        for (i in 0 until a.length()) {
            val e = a.optJSONObject(i) ?: continue
            val id = e.optString("id", "")
            if (items.none { it.id == id } || out.containsKey(id)) continue
            val arr = e.optJSONArray("messages") ?: continue
            val messages = (0 until arr.length()).mapNotNull { message(arr.optJSONObject(it)) }
            if (messages.isNotEmpty()) out[id] = messages.takeLast(MAX_MESSAGES)
        }
        return out
    }

    private fun message(m: ChatMessage): JSONObject {
        val o = JSONObject().put("id", m.id).put("role", m.role.name.lowercase())
            .put("text", m.text.take(MAX_TEXT)).put("status", m.status.name.lowercase())
        m.reason?.let { o.put("reason", it.take(40)) }
        return o
    }

    private fun message(o: JSONObject?): ChatMessage? {
        o ?: return null
        val id = o.optString("id", "")
        val role = ChatRole.entries.firstOrNull { it.name.equals(o.optString("role"), ignoreCase = true) } ?: return null
        if (id.isBlank()) return null
        var status = ChatStatus.entries.firstOrNull { it.name.equals(o.optString("status"), ignoreCase = true) } ?: ChatStatus.DONE
        var reason = o.optString("reason", "").ifEmpty { null }
        // An answer that was still arriving when the app closed will never arrive: it is shown as interrupted, as ChatHistory does.
        if (status == ChatStatus.STREAMING) { status = ChatStatus.FAILED; reason = "interrupted" }
        return ChatMessage(id, role, o.optString("text", "").take(MAX_TEXT), status, reason)
    }
}
