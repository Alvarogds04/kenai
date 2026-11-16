package com.kenai.android.provider

import com.kenai.android.core.ChatMessage
import com.kenai.android.core.ChatModelOption
import com.kenai.android.core.ChatRole
import com.kenai.android.core.ChatSession
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The OpenAI-compatible backend against a fake provider on 127.0.0.1: what the wire says, how a stream becomes four
 * events, and every bound Koda's own gateway keeps. No test touches the internet.
 */
class OpenAiCompatibleBackendTest {
    private lateinit var server: FakeProvider
    private lateinit var recorder: Recorder

    /** The key of every test, long enough to be recognisable in a header and in a search of every line. */
    private val key = "sk-test-1234567890abcdef"

    @Before
    fun setUp() {
        server = FakeProvider()
        recorder = Recorder()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun config(
        baseUrl: String = server.baseUrl,
        models: List<ChatModelOption> = emptyList(),
    ) = ProviderConfig("litellm", "LiteLLM", baseUrl, "local-model", models)

    private fun backend(
        config: ProviderConfig = config(),
        opener: HttpOpener = LoopbackOpener(),
        key: String? = this.key,
        timeouts: OpenAiCompatibleBackend.Timeouts = OpenAiCompatibleBackend.Timeouts(),
    ) = OpenAiCompatibleBackend(config, { key }, recorder, opener, timeouts).also { b ->
        // The conversation is the backend's own: a fresh one has nothing in it.
        b.reset()
        recorder.drain()
    }

    /** The chat requests that really carried a question, in the order they arrived. */
    private fun chatRequests(): List<FakeProvider.Request> =
        server.requests.filter { it.isChat() && it.body.isNotEmpty() }

    /** The bodies of those requests. */
    private fun chatBodies(): List<String> = chatRequests().map { it.body }

    /** The `GET /models` requests, in the order they arrived. */
    private fun modelRequests(): List<FakeProvider.Request> = server.requests.filter { it.isModels() }

    private fun turns(body: String): List<Pair<String, String>> {
        val a = JSONObject(body).getJSONArray("messages")
        return (0 until a.length()).map { a.getJSONObject(it).getString("role") to a.getJSONObject(it).getString("content") }
    }

    // ── One answer ───────────────────────────────────────────────────────────────────────────────

    @Test fun aStreamedAnswerArrivesInDeltasAndThenDone() {
        server.answer = { _, ex ->
            ex.head(200, "text/event-stream")
            ex.event("""{"choices":[{"delta":{"role":"assistant"}}]}""")
            ex.event("""{"choices":[{"delta":{"content":"Hel"}}]}""")
            ex.event("""{"choices":[{"delta":{"content":"lo"}}]}""")
            ex.write("data: [DONE]\n\n")
            ex.end()
        }
        val b = backend()
        assertTrue(b.send("c1", "local-model", "hi"))
        val events = recorder.untilEnd()
        assertEquals("Hello", events.streamed())
        val done = events.last() as Recorder.Event.Done
        assertEquals("c1", done.id)
        assertEquals("Hello", done.text)
        val sent = chatRequests().single()
        assertEquals("POST", sent.method)
        assertEquals("/v1/chat/completions", sent.path)
        assertTrue("the stream is asked for", JSONObject(sent.body).getBoolean("stream"))
        assertEquals("local-model", JSONObject(sent.body).getString("model"))
        assertEquals(listOf("user" to "hi"), turns(sent.body))
    }

    @Test fun aStreamThatJustEndsIsAlsoDone() {
        server.answer = { _, ex ->
            ex.head(200, "text/event-stream")
            ex.event("""{"choices":[{"delta":{"content":"ok"}}]}""")
            ex.end()
        }
        val b = backend()
        assertTrue(b.send("c1", "local-model", "hi"))
        val events = recorder.untilEnd()
        assertEquals("ok", events.streamed())
        assertEquals("ok", (events.last() as Recorder.Event.Done).text)
    }

    @Test fun theSecondQuestionCarriesTheConversation() {
        server.answer = { req, ex ->
            ex.head(200, "text/event-stream")
            when (req.index) {
                1 -> ex.event("""{"choices":[{"delta":{"content":"first answer"}}]}""")
                2 -> ex.event("""{"choices":[{"delta":{"content":"second answer"}}]}""")
            }
            ex.write("data: [DONE]\n\n")
            ex.end()
        }
        val b = backend()
        assertTrue(b.send("c1", "local-model", "one"))
        recorder.untilEnd()
        assertTrue(b.send("c2", "local-model", "two"))
        recorder.untilEnd()
        assertEquals(
            listOf("user" to "one", "assistant" to "first answer", "user" to "two"),
            turns(chatBodies()[1]),
        )
    }

    @Test fun doneCommentsJunkAndASplitLineAreTolerated() {
        server.answer = { _, ex ->
            ex.head(200, "text/event-stream")
            ex.write(": keep-alive\n\n")
            ex.write("\n\n")
            ex.write("event: message\n\n")
            ex.write("not a data line at all\n\n")
            ex.event("""{"id":"junk","object":"chat.completion.chunk"}""")
            ex.event("""{"choices":[{"delta":{"role":"assistant"}}]}""")
            ex.write("data: {\"choices\":[{\"delta\":{\"cont")
            ex.sleep(80)
            ex.write("ent\":\"Hel\"}}]}\n\n")
            ex.event("""{"choices":[{"delta":{"content":"lo"}}]}""")
            ex.write("data: [DONE]\n\n")
            ex.end()
        }
        val b = backend()
        assertTrue(b.send("c1", "local-model", "hi"))
        val events = recorder.untilEnd()
        assertEquals("Hello", events.streamed())
        assertEquals("Hello", (events.last() as Recorder.Event.Done).text)
    }

    @Test fun aServerThatIgnoresStreamAndAnswersJsonGivesTheWholeAnswer() {
        server.answer = { _, ex ->
            ex.head(200, "application/json")
            ex.write("""{"choices":[{"message":{"role":"assistant","content":"all at once"}}]}""")
            ex.end()
        }
        val b = backend()
        assertTrue(b.send("c1", "local-model", "hi"))
        val events = recorder.untilEnd()
        assertEquals("no deltas: the whole answer came at once", "", events.streamed())
        val done = events.single() as Recorder.Event.Done
        assertEquals("all at once", done.text)
    }

    // ── Errors, and only the closed set of reasons ───────────────────────────────────────────────

    private fun errorFor(code: Int): Recorder.Event.Error {
        server.answer = { _, ex ->
            ex.head(code, "application/json")
            ex.write("""{"error":{"message":"nope","type":"invalid_request_error"}}""")
            ex.end()
        }
        val b = backend()
        assertTrue("started: the answer comes through the listener", b.send("c1", "local-model", "hi"))
        val events = recorder.untilEnd()
        val error = events.last() as Recorder.Event.Error
        assertTrue("no new reason: $error", error.reason in com.kenai.android.core.ChatReasons.KNOWN)
        assertFalse("the key is never in a message", error.message.contains(key))
        return error
    }

    @Test fun unauthorizedAndForbiddenAreAuth() {
        assertEquals("auth", errorFor(401).reason)
        assertEquals("auth", errorFor(403).reason)
    }

    @Test fun tooManyRequestsIsRate() {
        assertEquals("rate", errorFor(429).reason)
    }

    @Test fun everyOtherRefusalIsProvider() {
        assertEquals("provider", errorFor(500).reason)
        assertEquals("provider", errorFor(503).reason)
        assertEquals("provider", errorFor(400).reason)
    }

    @Test fun aClosedPortIsUnreachable() {
        val closed = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val port = closed.localPort
        closed.close()
        val b = backend(config(baseUrl = "http://127.0.0.1:$port/v1"))
        assertTrue(b.send("c1", "local-model", "hi"))
        val error = recorder.untilEnd().last() as Recorder.Event.Error
        assertEquals("unreachable", error.reason)
        assertFalse(error.message.contains(key))
    }

    @Test fun aMissingKeyIsNoKeyAndNothingIsSent() {
        val b = backend(key = null)
        assertFalse("it cannot start without a key", b.send("c1", "local-model", "hi"))
        val error = recorder.next() as Recorder.Event.Error
        assertEquals("no_key", error.reason)
        assertTrue("no request reached the server", server.requests.isEmpty())
    }

    @Test fun anIdleStreamEndsAsAConnectionProblem() {
        server.answer = { _, ex ->
            ex.head(200, "text/event-stream")
            ex.event("""{"choices":[{"delta":{"content":"half"}}]}""")
            ex.sleep(5_000)
            ex.end()
        }
        val b = backend(timeouts = OpenAiCompatibleBackend.Timeouts(idleMs = 400))
        assertTrue(b.send("c1", "local-model", "hi"))
        val events = recorder.untilEnd()
        assertEquals("half", events.streamed())
        val error = events.last() as Recorder.Event.Error
        assertTrue("a stalled stream is a connection, not a new reason: ${error.reason}", error.reason in com.kenai.android.core.ChatReasons.KNOWN)
        assertEquals("connection", error.reason)
    }

    @Test fun cancelEndsTheAnswerAndKeepsNothingOfItInMemory() {
        server.answer = { req, ex ->
            ex.head(200, "text/event-stream")
            if (req.index == 1) {
                repeat(50) {
                    ex.event("""{"choices":[{"delta":{"content":"x"}}]}""")
                    ex.sleep(40)
                }
            } else {
                ex.event("""{"choices":[{"delta":{"content":"clean"}}]}""")
                ex.write("data: [DONE]\n\n")
            }
            ex.end()
        }
        val b = backend()
        assertTrue(b.send("c1", "local-model", "one"))
        recorder.next() // the first delta
        b.cancel("c1")
        val canceled = recorder.untilEnd().last() as Recorder.Event.Error
        assertEquals("canceled", canceled.reason)
        assertTrue(b.send("c2", "local-model", "two"))
        recorder.untilEnd()
        assertEquals(
            "a canceled answer is not part of the conversation",
            listOf("user" to "one", "user" to "two"),
            turns(chatBodies()[1]),
        )
    }

    @Test fun aSecondSendWhileOneIsRunningIsBusy() {
        val release = java.util.concurrent.CountDownLatch(1)
        server.answer = { _, ex ->
            ex.head(200, "text/event-stream")
            ex.event("""{"choices":[{"delta":{"content":"slow"}}]}""")
            release.await(5, java.util.concurrent.TimeUnit.SECONDS)
            ex.write("data: [DONE]\n\n")
            ex.end()
        }
        val b = backend()
        assertTrue(b.send("c1", "local-model", "one"))
        recorder.next()
        assertFalse("one answer at a time", b.send("c2", "local-model", "two"))
        val busy = recorder.next() as Recorder.Event.Error
        assertEquals("busy", busy.reason)
        release.countDown()
        b.cancel("c1")
        assertEquals("the second question never reached the server", 1, chatBodies().size)
    }

    @Test fun theAnswerStopsAtTheSessionsLimit() {
        server.answer = { _, ex ->
            ex.head(200, "text/event-stream")
            val piece = "y".repeat(100)
            repeat(600) {
                ex.event("""{"choices":[{"delta":{"content":"$piece"}}]}""")
            }
            ex.write("data: [DONE]\n\n")
            ex.end()
        }
        val b = backend()
        assertTrue(b.send("c1", "local-model", "hi"))
        val events = recorder.untilEnd()
        assertEquals(ChatSession.MAX_TEXT, events.streamed().length)
        val done = events.last() as Recorder.Event.Done
        assertEquals(ChatSession.MAX_TEXT, done.text!!.length)
    }

    // ── Models ───────────────────────────────────────────────────────────────────────────────────

    @Test fun modelsReadsTheEndpoint() {
        server.answer = { _, ex ->
            ex.head(200, "application/json")
            ex.write("""{"object":"list","data":[{"id":"a"},{"id":"b"}]}""")
            ex.end()
        }
        val b = backend()
        assertTrue(b.models())
        val models = (recorder.next() as Recorder.Event.Models).models
        assertEquals(listOf("a", "b"), models.map { it.id })
        assertEquals(listOf("litellm", "litellm"), models.map { it.provider })
        assertEquals(listOf("a", "b"), models.map { it.label })
        assertEquals("one request, and no chat request", 1, modelRequests().size)
        assertEquals("GET", modelRequests().single().method)
        assertEquals("/v1/models", modelRequests().single().path)
    }

    @Test fun modelsFixedInTheConfigContactNothing() {
        val b = backend(config(models = listOf(ChatModelOption("local-model", "litellm", "Local"))))
        assertTrue(b.models())
        assertEquals(listOf("local-model"), (recorder.next() as Recorder.Event.Models).models.map { it.id })
        assertTrue(server.requests.isEmpty())
    }

    @Test fun modelsStopsAtOneHundredAndTwentyEight() {
        server.answer = { _, ex ->
            ex.head(200, "application/json")
            ex.write("""{"data":[${(1..200).joinToString(",") { """{"id":"m$it"}""" }}]}""")
            ex.end()
        }
        val b = backend()
        assertTrue(b.models())
        assertEquals(128, (recorder.next() as Recorder.Event.Models).models.size)
    }

    @Test fun modelsReadsAtMostOneQuarterOfAMegabyte() {
        server.answer = { _, ex ->
            ex.head(200, "application/json")
            ex.write("""{"data":[{"id":"a"}],"pad":"${"p".repeat(300 * 1024)}"}""")
            ex.end()
        }
        val b = backend()
        assertTrue(b.models())
        val error = recorder.next() as Recorder.Event.Error
        assertTrue(error.reason in com.kenai.android.core.ChatReasons.KNOWN)
        assertEquals("provider", error.reason)
    }

    @Test fun modelsWithoutAKeyIsNoKeyAndNothingIsSent() {
        val b = backend(key = null)
        assertFalse(b.models())
        assertEquals("no_key", (recorder.next() as Recorder.Event.Error).reason)
        assertTrue(server.requests.isEmpty())
    }

    @Test fun modelsThatAreNotAJsonListAreNoModels() {
        server.answer = { _, ex ->
            ex.head(200, "application/json")
            ex.write("not json at all")
            ex.end()
        }
        val b = backend()
        assertTrue(b.models())
        assertEquals(emptyList<ChatModelOption>(), (recorder.next() as Recorder.Event.Models).models)
    }

    // ── The key, and the scheme ──────────────────────────────────────────────────────────────────

    @Test fun theKeyTravelsOnlyInTheAuthorizationHeader() {
        server.answer = { _, ex ->
            ex.head(200, "text/event-stream")
            ex.event("""{"choices":[{"delta":{"content":"ok"}}]}""")
            ex.write("data: [DONE]\n\n")
            ex.end()
        }
        val opener = LoopbackOpener()
        val b = backend(opener = opener)
        assertTrue(b.send("c1", "local-model", "hi"))
        recorder.untilEnd()
        val withKey = server.rawLines.filter { it.contains(key) }
        assertTrue("the key was sent", withKey.isNotEmpty())
        assertTrue(
            "the key is in the Authorization header and nowhere else: $withKey",
            withKey.all { it == "Authorization: Bearer $key" },
        )
        assertFalse("not in the request line or a body", chatBodies().single().contains(key))
        assertTrue("not in any address the opener was given: ${opener.opened}", opener.opened.none { it.contains(key) })
        assertEquals("https is never needed for loopback in tests", 1, opener.opened.size)
        assertTrue(opener.opened.single().startsWith("http://127.0.0.1:"))
    }

    @Test fun aNotFoundModelStillNeverLeaksTheKeyInItsMessage() {
        server.answer = { _, ex ->
            ex.head(404, "application/json")
            ex.write("""{"error":{"message":"model not found"}}""")
            ex.end()
        }
        val b = backend()
        assertTrue(b.send("c1", "local-model", "hi"))
        val error = recorder.untilEnd().last() as Recorder.Event.Error
        assertEquals("provider", error.reason)
        assertFalse(error.message.contains(key))
    }

    @Test fun anHttpAddressIsRefusedByTheProductionOpenerBeforeAnythingIsContacted() {
        val b = OpenAiCompatibleBackend(
            config(baseUrl = "http://127.0.0.1:${server.port}/v1"),
            { key },
            recorder,
            HttpsOpener(),
        )
        assertTrue(b.send("c1", "local-model", "hi"))
        val error = recorder.untilEnd().last() as Recorder.Event.Error
        assertEquals("provider", error.reason)
        assertTrue("nothing reached the server", server.requests.isEmpty())
    }

    @Test fun theBoundsAreKodasAndTheLimitIsTheProvidersOwn() {
        val t = OpenAiCompatibleBackend.Timeouts()
        assertEquals("GET /models has 10 s", 10_000, t.modelsMs)
        assertEquals("connect has 10 s", 10_000, t.connectMs)
        assertEquals("a streamed answer has 60 s idle", 60_000, t.idleMs)
        val b = backend()
        assertEquals(32_000, b.maxText)
        assertTrue("shorter than what the session can show", b.maxText < ChatSession.MAX_TEXT)
    }

    // ── The conversation the backend keeps, and how AppModel seeds it ───────────────────────────

    @Test fun seedRestoresTheConversationFromThePhonesHistory() {
        server.answer = { _, ex ->
            ex.head(200, "text/event-stream")
            ex.event("""{"choices":[{"delta":{"content":"third"}}]}""")
            ex.write("data: [DONE]\n\n")
            ex.end()
        }
        val b = backend()
        b.seed(
            listOf(
                ChatMessage("u-1", ChatRole.USER, "restored question"),
                ChatMessage("c1", ChatRole.ASSISTANT, "restored answer"),
                ChatMessage("u-2", ChatRole.USER, "  "),
            ),
        )
        assertTrue(b.send("c3", "local-model", "new question"))
        recorder.untilEnd()
        assertEquals(
            listOf("user" to "restored question", "assistant" to "restored answer", "user" to "new question"),
            turns(chatBodies().single()),
        )
    }

    @Test fun resetForgetsTheConversation() {
        server.answer = { _, ex ->
            ex.head(200, "text/event-stream")
            ex.event("""{"choices":[{"delta":{"content":"a"}}]}""")
            ex.write("data: [DONE]\n\n")
            ex.end()
        }
        val b = backend()
        assertTrue(b.send("c1", "local-model", "one"))
        recorder.untilEnd()
        b.reset()
        assertTrue(b.send("c2", "local-model", "two"))
        recorder.untilEnd()
        assertEquals(listOf("user" to "two"), turns(chatBodies()[1]))
    }

    @Test fun aSeedIsNotSavedAsAQuestionOfItsOwn() {
        val b = backend()
        b.seed(emptyList())
        assertNull(recorder.events.poll())
    }
}
