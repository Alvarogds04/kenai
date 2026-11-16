package com.kenai.android.provider

import com.kenai.android.core.ChatBackend
import com.kenai.android.core.ChatMessage
import com.kenai.android.core.ChatModelOption
import com.kenai.android.core.ChatRole
import com.kenai.android.core.ChatSession
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The phone's own chat with a provider that speaks the OpenAI chat API: GPT, `api.nan.builders/v1`, a LiteLLM or any
 * other gateway with the same shape. A [ChatBackend], so [ChatSession] and its tests never see the wire.
 *
 * It is engine only: no screen and no settings know it yet (T1a-3 wires it, with the key in the Keystore). It differs
 * from the link backend in two ways that matter:
 *  - a direct provider keeps no context, so this class keeps the conversation in memory and sends the whole thing with
 *    every question, appending the user's text on send and the answer on done; [seed] restores it from the history the
 *    phone already has, and [reset] forgets it;
 *  - its question limit is its own ([maxText]), not the computer's 4000.
 *
 * The key never lives in this class: [keys] hands it over for one request and it goes into the `Authorization: Bearer`
 * header and nowhere else — not the URL, not a log, not a message.
 */
class OpenAiCompatibleBackend(
    private val config: ProviderConfig,
    private val keys: KeyProvider,
    private val listener: ChatBackend.Listener,
    private val opener: HttpOpener = HttpsOpener(),
    private val timeouts: Timeouts = Timeouts(),
) : ChatBackend {
    /** Koda's own gateway bounds, so a hung provider cannot hold the phone: 10 s for the list, 60 s idle for a stream. */
    data class Timeouts(
        /** `GET /models`: how long the whole request may take. */
        val modelsMs: Int = 10_000,
        /** Connecting to the provider. */
        val connectMs: Int = 10_000,
        /** No text for this long while an answer streams: the stream is dead. */
        val idleMs: Int = 60_000,
    )

    /** Longer than the computer's 4000, still shorter than what the screen can show. */
    override val maxText: Int = MAX_QUESTION

    private val base = config.baseUrl.trim().trimEnd('/')
    private val lock = Any()

    /** The conversation, because the provider keeps none: the whole thing goes with every question. */
    private val conversation = ArrayList<Turn>()

    /** The answer being received right now, if any: one at a time. */
    private var active: Answer? = null

    private data class Turn(val role: String, val content: String)

    /** One answer in flight. [connection] is what Cancel closes; [text] is guarded by [lock]. */
    private class Answer(val id: String) {
        @Volatile var canceled = false
        @Volatile var connection: HttpURLConnection? = null
        val text = StringBuilder()
    }

    // ── Models ───────────────────────────────────────────────────────────────────────────────────

    /** Fixed in the config, or read from `GET {base}/models`; either way the answer comes through the listener. */
    override fun models(): Boolean {
        val fixed = config.models
        if (fixed.isNotEmpty()) {
            start { listener.onModels(fixed.take(MAX_MODELS)) }
            return true
        }
        val key = keys.key(config.id)
        if (key.isNullOrBlank()) {
            listener.onError("", NO_KEY, noKeyMessage())
            return false
        }
        start { readModels(key) }
        return true
    }

    private fun readModels(key: String) {
        var connection: HttpURLConnection? = null
        try {
            val opened = opener.open("$base/models", "GET")
            connection = opened
            opened.connectTimeout = timeouts.modelsMs
            opened.readTimeout = timeouts.modelsMs
            opened.setRequestProperty("Accept", "application/json")
            opened.setRequestProperty("Authorization", "Bearer $key")
            val code = opened.responseCode
            if (code != 200) {
                listener.onError("", reasonFor(code), "the provider answered $code")
                return
            }
            val body = readBounded(opened.inputStream, MAX_LIST_BYTES)
            if (body == null) {
                listener.onError("", "provider", "the model list is too large")
                return
            }
            listener.onModels(modelsOf(body))
        } catch (_: SocketTimeoutException) {
            listener.onError("", "unreachable", "the provider did not answer in time")
        } catch (_: IOException) {
            listener.onError("", "unreachable", "the provider could not be reached")
        } catch (_: IllegalArgumentException) {
            listener.onError("", "provider", "the provider address cannot be used")
        } catch (_: Exception) {
            listener.onError("", "internal", "the model list could not be read")
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun modelsOf(body: String): List<ChatModelOption> = try {
        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        (0 until data.length()).mapNotNull { i ->
            data.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
                ?.let { ChatModelOption(it, config.id, it) }
        }.take(MAX_MODELS)
    } catch (_: JSONException) {
        emptyList()
    }

    // ── One answer ───────────────────────────────────────────────────────────────────────────────

    /** False when it cannot start (no key, one answer already running); the reason arrives through the listener. */
    override fun send(id: String, model: String, text: String): Boolean {
        val key = keys.key(config.id)
        if (key.isNullOrBlank()) {
            listener.onError(id, NO_KEY, noKeyMessage())
            return false
        }
        val answer = Answer(id)
        val busy = synchronized(lock) {
            if (active != null) {
                true
            } else {
                conversation += Turn("user", text)
                active = answer
                false
            }
        }
        if (busy) {
            listener.onError(id, "busy", "one answer at a time")
            return false
        }
        start { run(answer, model, key) }
        return true
    }

    private fun run(answer: Answer, model: String, key: String) {
        var connected = false
        try {
            val turns = synchronized(lock) { conversation.toList() }
            val messages = JSONArray()
            for (turn in turns) messages.put(JSONObject().put("role", turn.role).put("content", turn.content))
            val body = JSONObject().put("model", model).put("stream", true).put("messages", messages)
                .toString().toByteArray(Charsets.UTF_8)

            val connection = opener.open("$base/chat/completions", "POST")
            answer.connection = connection
            connection.connectTimeout = timeouts.connectMs
            connection.readTimeout = timeouts.idleMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "text/event-stream")
            // The only place the key is ever written.
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (answer.canceled) return

            val code = connection.responseCode
            connected = true
            if (code != 200) {
                fail(answer, reasonFor(code), "the provider answered $code")
                return
            }
            val type = connection.contentType.orEmpty().lowercase()
            if (type.contains("application/json")) readWhole(connection, answer) else readStream(connection, answer)
            complete(answer)
        } catch (_: SocketTimeoutException) {
            if (!answer.canceled) fail(answer, if (connected) "connection" else "unreachable", "the provider stopped answering")
        } catch (_: IOException) {
            if (!answer.canceled) {
                fail(
                    answer,
                    if (connected) "connection" else "unreachable",
                    if (connected) "the connection broke" else "the provider could not be reached",
                )
            }
        } catch (_: IllegalArgumentException) {
            fail(answer, "provider", "the provider address cannot be used")
        } catch (_: Exception) {
            fail(answer, "internal", "the answer could not be read")
        } finally {
            runCatching { answer.connection?.disconnect() }
            synchronized(lock) { if (active === answer) active = null }
        }
    }

    /** The streamed case: one line at a time until `[DONE]`, the end of the body, or the text limit. */
    private fun readStream(connection: HttpURLConnection, answer: Answer) {
        val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
        val sse = SseReader()
        while (!answer.canceled) {
            val line = reader.readLine() ?: return
            when (val event = sse.feed(line)) {
                is SseReader.Event.Delta -> if (!push(answer, event.text)) return
                is SseReader.Event.Full -> {
                    push(answer, event.text)
                    return
                }
                SseReader.Event.End -> return
                null -> {}
            }
        }
    }

    /** The gateway ignored `stream:true`: the whole answer is one JSON body. */
    private fun readWhole(connection: HttpURLConnection, answer: Answer) {
        val body = readBounded(connection.inputStream, MAX_ANSWER_BYTES)
        if (body == null) {
            fail(answer, "too_long", "the answer is too large")
            return
        }
        val full = SseReader.fullAnswer(body)
        if (full == null) {
            fail(answer, "provider", "the provider sent no answer")
            return
        }
        synchronized(lock) { answer.text.append(full.take(ChatSession.MAX_TEXT)) }
    }

    /**
     * Adds text to the answer and shows it. False when the answer reached [ChatSession.MAX_TEXT]: the phone would drop
     * anything more, so reading stops there and the answer ends as done.
     */
    private fun push(answer: Answer, piece: String): Boolean {
        var shown: String? = null
        var more = false
        synchronized(lock) {
            val room = ChatSession.MAX_TEXT - answer.text.length
            if (room > 0) {
                val add = if (piece.length > room) piece.substring(0, room) else piece
                answer.text.append(add)
                shown = add
                more = answer.text.length < ChatSession.MAX_TEXT
            }
        }
        shown?.let { listener.onDelta(answer.id, it) }
        return more
    }

    /** The end: the answer is kept as the provider's side of the conversation, and only then. */
    private fun complete(answer: Answer) {
        var full: String? = null
        var empty = false
        synchronized(lock) {
            if (active === answer) {
                active = null
                val text = answer.text.toString()
                if (text.isEmpty()) {
                    empty = true
                } else {
                    conversation += Turn("assistant", text)
                    full = text
                }
            }
        }
        if (empty) listener.onError(answer.id, "provider", "the provider answered nothing")
        full?.let { listener.onDone(answer.id, it) }
    }

    private fun fail(answer: Answer, reason: String, message: String) {
        val mine = synchronized(lock) {
            if (active === answer) {
                active = null
                true
            } else {
                false
            }
        }
        if (mine) listener.onError(answer.id, reason, message)
    }

    // ── Cancel, reset, seed ──────────────────────────────────────────────────────────────────────

    /** Closes the connection and ends the answer with `canceled`; a half answer is not kept in memory. */
    override fun cancel(id: String) {
        val answer = synchronized(lock) {
            val current = active
            if (current != null && current.id == id) {
                active = null
                current
            } else {
                null
            }
        } ?: return
        answer.canceled = true
        close(answer)
        listener.onError(id, "canceled", "")
    }

    /** Clear: the conversation is forgotten here; an answer still streaming is stopped with `canceled`. */
    override fun reset() {
        val answer = synchronized(lock) {
            val current = active
            active = null
            current
        }
        if (answer != null) {
            answer.canceled = true
            close(answer)
            listener.onError(answer.id, "canceled", "")
        }
        synchronized(lock) { conversation.clear() }
    }

    /**
     * The conversation this backend keeps, restored from the history the phone already has: what AppModel does once,
     * right after building the backend, with `ChatSession.messages`. Blank texts are skipped (a failed answer that
     * arrived empty is no context); a partial answer that was kept on screen is sent as the assistant's turn.
     */
    fun seed(messages: List<ChatMessage>) {
        synchronized(lock) {
            conversation.clear()
            for (message in messages.takeLast(ChatSession.MAX_MESSAGES)) {
                val text = message.text.trim()
                if (text.isEmpty()) continue
                conversation += Turn(if (message.role == ChatRole.USER) "user" else "assistant", text)
            }
        }
    }

    // ── Plumbing ─────────────────────────────────────────────────────────────────────────────────

    private fun close(answer: Answer) {
        val connection = answer.connection
        runCatching { connection?.inputStream?.close() }
        runCatching { connection?.disconnect() }
    }

    /** The closed set of reasons in `ChatReasons.KNOWN`: nothing new is invented here. */
    private fun reasonFor(code: Int): String = when {
        code == 401 || code == 403 -> "auth"
        code == 429 -> "rate"
        else -> "provider"
    }

    private fun noKeyMessage() = "no key for ${config.name}"

    /** Reads at most [limit] bytes; null when the body is larger, so a runaway answer cannot fill the phone. */
    private fun readBounded(input: InputStream, limit: Int): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    /** Every request runs on its own daemon thread: nothing of it can hold the app open. */
    private fun start(work: () -> Unit) {
        Thread { work() }.apply {
            isDaemon = true
            name = "kenai-provider-${config.id}"
            start()
        }
    }

    companion object {
        /** The longest question this backend accepts. */
        const val MAX_QUESTION = 32_000

        /** Koda's gateway bound: never more models than this from `GET /models`. */
        const val MAX_MODELS = 128

        /** Koda's gateway bound: how much of the model list is read at most (256 KiB). */
        const val MAX_LIST_BYTES = 256 * 1024

        /** The same bound for an answer that came as one JSON body instead of a stream. */
        const val MAX_ANSWER_BYTES = 256 * 1024

        private const val NO_KEY = "no_key"
    }
}
