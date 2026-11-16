package com.kenai.android.provider

import com.kenai.android.core.ChatBackend
import com.kenai.android.core.ChatModelOption
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A pretend provider: a real socket on 127.0.0.1 that answers HTTP/1.1 by hand, so the OpenAI-compatible backend is
 * tested against the bytes it would really read. Nothing leaves the machine, no provider is called and nothing costs
 * money. The same idea as `FakeDesktop` in `link/LinkClientTest.kt`, with HTTP instead of the JSON line protocol.
 */
internal class FakeProvider : AutoCloseable {
    /** One request as it arrived; headers lower-cased, body read by its Content-Length. */
    data class Request(
        /** 1 for the first request this server saw, 2 for the next... */
        val index: Int,
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: String,
    ) {
        fun isChat() = path.endsWith("/chat/completions")
        fun isModels() = path.endsWith("/models")
    }

    val requests = CopyOnWriteArrayList<Request>()

    /** The request and header lines exactly as sent, for the "where does the key go" checks. */
    val rawLines = CopyOnWriteArrayList<String>()

    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))

    val port: Int get() = server.localPort

    /** The address a [ProviderConfig] would carry; a loopback http URL, which only the test opener allows. */
    val baseUrl: String get() = "http://127.0.0.1:$port/v1"

    /** What to answer; the test may branch on [Request.index], so question one and two get different answers. */
    @Volatile
    var answer: (Request, Exchange) -> Unit = { _, ex ->
        ex.head(200, "application/json")
        ex.write("""{"choices":[]}""")
        ex.end()
    }

    init {
        Thread {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                Thread { serve(socket) }.apply { isDaemon = true; start() }
            }
        }.apply { isDaemon = true; name = "fake-provider"; start() }
    }

    private fun serve(socket: Socket) {
        socket.use { s ->
            try {
                val input = s.getInputStream()
                val head = readHead(input) ?: return
                rawLines.addAll(head)
                val first = head.first().split(" ")
                val headers = head.drop(1).filter { it.contains(":") }.associate {
                    it.substringBefore(":").trim().lowercase() to it.substringAfter(":").trim()
                }
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (length > 0) String(readExactly(input, length), Charsets.UTF_8) else ""
                val request = Request(requests.size + 1, first[0], first.getOrElse(1) { "" }, headers, body)
                requests.add(request)
                answer(request, Exchange(s))
                // Read whatever the client still sends before the socket closes: closing with data in flight sends a
                // reset, and the JVM answers a reset by retrying the request, which would look like a stray request.
                runCatching {
                    s.soTimeout = 200
                    while (input.read() >= 0) {
                        // drained on purpose
                    }
                }
            } catch (_: Exception) {
                // The client closed the connection (Cancel, the text limit): that is the test's point.
            }
        }
    }

    /** The request head, split into lines, up to the empty line. Null when the client said nothing. */
    private fun readHead(input: InputStream): List<String>? {
        val all = StringBuilder()
        while (!all.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0) return null
            all.append(b.toChar())
            if (all.length > 16_384) return null
        }
        return all.toString().trimEnd('\r', '\n').split("\r\n").filter { it.isNotEmpty() }
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val out = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(out, read, length - read)
            if (n < 0) break
            read += n
        }
        return if (read == length) out else out.copyOf(read)
    }

    override fun close() {
        runCatching { server.close() }
    }

    /** The writing side of one connection. */
    internal class Exchange(private val socket: Socket) {
        private val out = socket.getOutputStream()

        fun head(code: Int, contentType: String, extra: List<String> = emptyList()) {
            val text = buildString {
                append("HTTP/1.1 $code ").append(reason(code)).append("\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Connection: close\r\n")
                for (h in extra) append(h).append("\r\n")
                append("\r\n")
            }
            write(text)
        }

        /** Writes and flushes at once: a test can split a frame mid-line with two calls. */
        fun write(text: String) {
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        }

        /** One server-sent event frame. */
        fun event(json: String) {
            write("data: $json\n\n")
        }

        /** The end of the response body. */
        fun end() {
            runCatching { socket.shutdownOutput() }
        }

        fun sleep(millis: Long) {
            Thread.sleep(millis)
        }

        private fun reason(code: Int) = when (code) {
            200 -> "OK"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            429 -> "Too Many Requests"
            else -> "Server Error"
        }
    }
}

/**
 * The four things the backend says, collected in one queue so a test can read them in the order they arrived, whatever
 * thread they came from.
 */
internal class Recorder : ChatBackend.Listener {
    sealed interface Event {
        data class Models(val models: List<ChatModelOption>) : Event
        data class Delta(val id: String, val text: String) : Event
        data class Done(val id: String, val text: String?) : Event
        data class Error(val id: String, val reason: String, val message: String) : Event
    }

    val events = LinkedBlockingQueue<Event>()

    override fun onModels(models: List<ChatModelOption>) {
        events.add(Event.Models(models))
    }

    override fun onDelta(id: String, text: String) {
        events.add(Event.Delta(id, text))
    }

    override fun onDone(id: String, text: String?) {
        events.add(Event.Done(id, text))
    }

    override fun onError(id: String, reason: String, message: String) {
        events.add(Event.Error(id, reason, message))
    }

    fun next(timeoutMillis: Long = 5_000): Event =
        events.poll(timeoutMillis, TimeUnit.MILLISECONDS) ?: throw AssertionError("no event arrived in ${timeoutMillis}ms")

    /** Everything up to and including the end of one answer (done or error). */
    fun untilEnd(timeoutMillis: Long = 10_000): List<Event> {
        val out = ArrayList<Event>()
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            val left = (deadline - System.currentTimeMillis()).coerceAtLeast(1)
            val e = events.poll(left, TimeUnit.MILLISECONDS) ?: throw AssertionError("the answer never ended: $out")
            out.add(e)
            if (e is Event.Done || e is Event.Error) return out
        }
    }

    fun drain(): List<Event> {
        val out = ArrayList<Event>()
        while (true) out.add(events.poll(100, TimeUnit.MILLISECONDS) ?: return out)
    }
}

/** The text the deltas added up to. */
internal fun List<Recorder.Event>.streamed(): String =
    filterIsInstance<Recorder.Event.Delta>().joinToString("") { it.text }
