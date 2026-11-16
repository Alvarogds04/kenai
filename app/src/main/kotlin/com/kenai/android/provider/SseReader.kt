package com.kenai.android.provider

import org.json.JSONException
import org.json.JSONObject

/**
 * Reads the OpenAI-compatible stream one line at a time: `data:` lines carry a JSON chunk whose
 * `choices[0].delta.content` is more text. `data: [DONE]` ends the stream; comments, blank lines, other fields and junk
 * are ignored, in the same spirit as `Wire.decodeServer` returning null for a line it does not understand. A gateway
 * that ignores `stream:true` may answer one whole message instead, which becomes a single [Event.Full].
 *
 * Line-oriented and incremental on purpose: the caller buffers the characters of a line ([java.io.BufferedReader]
 * does it) and hands them over complete, so a chunk split in the middle of a line is nobody's problem here.
 */
class SseReader {
    sealed interface Event {
        /** More text of the answer. */
        data class Delta(val text: String) : Event

        /** The whole answer, sent as one `message` instead of deltas. */
        data class Full(val text: String) : Event

        /** `[DONE]`: the server said the answer is over. */
        data object End : Event
    }

    /** One line of the stream; null when it says nothing about the answer. */
    fun feed(line: String): Event? {
        val text = line.trim()
        if (text.isEmpty() || text.startsWith(":")) return null
        val colon = text.indexOf(':')
        if (colon < 0) return null
        if (text.substring(0, colon) != "data") return null
        val payload = text.substring(colon + 1).trim()
        if (payload == "[DONE]") return Event.End
        if (!payload.startsWith("{")) return null
        return try {
            val choice = JSONObject(payload).optJSONArray("choices")?.optJSONObject(0) ?: return null
            val delta = contentOf(choice.optJSONObject("delta"))
            if (delta.isNotEmpty()) return Event.Delta(delta)
            val whole = contentOf(choice.optJSONObject("message"))
            if (whole.isNotEmpty()) Event.Full(whole) else null
        } catch (_: JSONException) {
            null
        }
    }

    companion object {
        /**
         * The answer of a body the server sent as `application/json` instead of a stream:
         * `choices[0].message.content`, or null when the body is not one.
         */
        fun fullAnswer(body: String): String? = try {
            contentOf(JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message"))
                .takeIf { it.isNotEmpty() }
        } catch (_: JSONException) {
            null
        }

        /** Only a string counts: org.json would turn a number or a boolean into text, and that is junk, not an answer. */
        private fun contentOf(message: JSONObject?): String = (message?.opt("content") as? String).orEmpty()
    }
}
