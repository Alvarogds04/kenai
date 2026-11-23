package com.kenai.android.link

import com.kenai.android.core.Refusal
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An answer this phone sent that the engine REFUSED (KN-04 T3, S4/S12; abito-core PROTOCOL.md AC-07 U1). A `decision` or an
 * `answer` comes back as a v1 `error` with a fixed code — `off`, `resolved`, `truncated`, `invalid_answer`, `bad_request` — and
 * NO fingerprint, so the client is the one that knows what was in flight:
 *
 * - the codes that leave the request open give the claim back, so the card can be answered again (deny is still taken on a cut
 *   card, and a correct answer is still taken on a rejected one);
 * - `resolved` keeps it claimed and the card goes: one request, one answer, whoever answered it;
 * - a refusal is not a link error, so the app never shows «Link error: <the engine's sentence>» for its own answer.
 *
 * A `bad_request` with nothing in flight is a chat frame's refusal (the engine refuses those with the same code): that one stays a
 * link error, exactly as before.
 */
class LinkRefusalTest {
    private val closeables = mutableListOf<AutoCloseable>()

    @After fun cleanup() = closeables.forEach { runCatching { it.close() } }

    private fun <T : AutoCloseable> T.track(): T { closeables.add(this); return this }

    private class Recorder : LinkListener {
        val connected = CountDownLatch(1)
        val approvals = LinkedBlockingQueue<ApprovalRequest>()
        val questions = LinkedBlockingQueue<QuestionRequest>()
        val errors = LinkedBlockingQueue<String>()
        val refusals = LinkedBlockingQueue<Pair<String?, Refusal>>()
        override fun onWelcome(desktopName: String, os: String) { connected.countDown() }
        override fun onApproval(request: ApprovalRequest) { approvals.add(request) }
        override fun onQuestion(request: QuestionRequest) { questions.add(request) }
        override fun onError(code: String, message: String) { errors.add(code) }
        override fun onAnswerRefused(fingerprint: String?, refusal: Refusal) { refusals.add(fingerprint to refusal) }
    }

    private fun payload(d: FakeDesktop) = PairingPayload("127.0.0.1", d.port, "ab".repeat(32), "T".repeat(20), "PC")

    private fun client(d: FakeDesktop, rec: Recorder): LinkClient {
        val connector = Connector { ms -> Socket().apply { connect(InetSocketAddress("127.0.0.1", d.port), ms) } }
        return LinkClient(
            payload(d), "Pixel", rec, connector, readTimeoutMs = 2_000, pingEveryMs = 100, backoffMs = longArrayOf(50), pongWaitMs = 300,
        ).also { closeables.add(AutoCloseable { it.stop() }) }
    }

    private fun connected(d: FakeDesktop, rec: Recorder): LinkClient {
        val c = client(d, rec)
        c.start()
        assertTrue("never connected", rec.connected.await(5, TimeUnit.SECONDS))
        return c
    }

    private fun refusal(d: FakeDesktop, code: String) =
        """{"type":"error","code":"$code","message":"the engine's own sentence"}"""

    /** The same frame naming its request: the engine's optional, additive `fingerprint` (PROTOCOL.md AC-07). */
    private fun namedRefusal(code: String, fingerprint: String) =
        """{"type":"error","code":"$code","message":"the engine's own sentence","fingerprint":"$fingerprint"}"""

    private fun approval(fingerprint: String) =
        """{"type":"approval","pillId":"agent_codex","fingerprint":"$fingerprint","tool":"Bash","command":"ls","createdAt":${System.currentTimeMillis()}}"""

    /** Waits until [fingerprint] is NOT answerable any more (its claim was never given back). */
    private fun assertStillClaimed(c: LinkClient, fingerprint: String) {
        assertEquals(false, c.decide(fingerprint, allow = true))
    }

    /** Waits until the book holds [fingerprint] again (the refusal gave the claim back). */
    private fun assertGivenBack(c: LinkClient, fingerprint: String) {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline && c.approvals.active().none { it.fingerprint == fingerprint }) Thread.sleep(20)
        assertTrue("the request is answerable again", c.approvals.active().any { it.fingerprint == fingerprint })
    }

    @Test fun anAllowRefusedAsOffIsGivenBackToTheBook() {
        val d = FakeDesktop(tls = false).track()
        val rec = Recorder()
        val c = connected(d, rec)
        d.push(d.approvalJson(System.currentTimeMillis()))
        val req = rec.approvals.poll(5, TimeUnit.SECONDS)
        assertNotNull(req)
        assertTrue(c.decide(req!!.fingerprint, allow = true))
        d.push(refusal(d, "off"))
        assertGivenBack(c, req.fingerprint)
        assertEquals(req.fingerprint to Refusal.OFF, rec.refusals.poll(5, TimeUnit.SECONDS))
        assertNull("a refusal is not a link error", rec.errors.poll(500, TimeUnit.MILLISECONDS))
    }

    @Test fun everyCodeThatLeavesTheRequestOpenGivesTheClaimBack() {
        for ((code, refusal) in listOf(
            "off" to Refusal.OFF,
            "truncated" to Refusal.TRUNCATED,
            "invalid_answer" to Refusal.INVALID_ANSWER,
            "bad_request" to Refusal.BAD_REQUEST,
        )) {
            val d = FakeDesktop(tls = false).track()
            val rec = Recorder()
            val c = connected(d, rec)
            d.push(d.approvalJson(System.currentTimeMillis()))
            val req = rec.approvals.poll(5, TimeUnit.SECONDS)!!
            assertTrue(code, c.decide(req.fingerprint, allow = true))
            d.push(refusal(d, code))
            assertGivenBack(c, req.fingerprint)
            assertEquals(code, req.fingerprint to refusal, rec.refusals.poll(5, TimeUnit.SECONDS))
            assertNull("$code is not a link error", rec.errors.poll(500, TimeUnit.MILLISECONDS))
            // A card that was cut is never offered an allow again, here or on the screen that draws it.
            assertEquals(code == "truncated", c.approvals.active().any { it.truncated })
            // Deny is still taken by the engine, so it must still be sendable from here.
            assertTrue("$code still allows a deny", c.decide(req.fingerprint, allow = false))
        }
    }

    @Test fun aResolvedRefusalKeepsTheRequestClaimed() {
        val d = FakeDesktop(tls = false).track()
        val rec = Recorder()
        val c = connected(d, rec)
        d.push(d.approvalJson(System.currentTimeMillis()))
        val req = rec.approvals.poll(5, TimeUnit.SECONDS)!!
        assertTrue(c.decide(req.fingerprint, allow = true))
        d.push(refusal(d, "resolved"))
        assertEquals(req.fingerprint to Refusal.RESOLVED, rec.refusals.poll(5, TimeUnit.SECONDS))
        assertTrue("nothing to answer any more", c.approvals.active().none { it.fingerprint == req.fingerprint })
        assertNull("a refusal is not a link error", rec.errors.poll(500, TimeUnit.MILLISECONDS))
        assertEquals("answered elsewhere: one request, one answer", false, c.decide(req.fingerprint, allow = true))
    }

    @Test fun aQuestionAnswerRefusedIsToldWithItsFingerprint() {
        val d = FakeDesktop(tls = false).track()
        d.welcomeExtra = ",\"caps\":[\"answers\"]"
        val rec = Recorder()
        val c = connected(d, rec)
        val fp = "cd".repeat(32)
        d.push("""{"type":"question","pillId":"agent_pi","fingerprint":"$fp","createdAt":1,"questions":[{"question":"Which?","options":[{"label":"A"},{"label":"B"}]}]}""")
        assertNotNull(rec.questions.poll(5, TimeUnit.SECONDS))
        assertTrue(c.answer(fp, listOf(listOf("A"))))
        d.push(refusal(d, "off"))
        assertEquals(fp to Refusal.OFF, rec.refusals.poll(5, TimeUnit.SECONDS))
        assertNull("a refusal is not a link error", rec.errors.poll(500, TimeUnit.MILLISECONDS))
        // The question is the app's, so the client holds nothing about it: a refusal only names it.
        assertTrue("nothing was added to the approval book", c.approvals.active().isEmpty())
    }

    @Test fun aResolvedAnswerSettlesWhatAQuestionAnswerWasWaitingFor() {
        val d = FakeDesktop(tls = false).track()
        d.welcomeExtra = ",\"caps\":[\"answers\"]"
        val rec = Recorder()
        val c = connected(d, rec)
        val fp = "cd".repeat(32)
        d.push("""{"type":"question","pillId":"agent_pi","fingerprint":"$fp","createdAt":1,"questions":[{"question":"Which?","options":[{"label":"A"},{"label":"B"}]}]}""")
        assertNotNull(rec.questions.poll(5, TimeUnit.SECONDS))
        assertTrue(c.answer(fp, listOf(listOf("A"))))
        d.push("""{"type":"approvalResolved","fingerprint":"$fp"}""")
        // A later refusal of anything at all must not be blamed on the answer that was already settled.
        d.push(refusal(d, "bad_request"))
        assertEquals("bad_request", rec.errors.poll(5, TimeUnit.SECONDS))
        assertNull("no answer was in flight any more", rec.refusals.poll(300, TimeUnit.MILLISECONDS))
    }

    // ── Two answers in flight at once: the frame that names one is about that one (T3b review finding 1) ────────────

    private val fpOne = "11".repeat(32)
    private val fpTwo = "22".repeat(32)

    /** Two approvals, both decided by this phone: two answers in flight, nothing settled. */
    private fun twoAnswersInFlight(d: FakeDesktop, rec: Recorder): LinkClient {
        val c = connected(d, rec)
        d.push(approval(fpOne))
        d.push(approval(fpTwo))
        assertNotNull(rec.approvals.poll(5, TimeUnit.SECONDS))
        assertNotNull(rec.approvals.poll(5, TimeUnit.SECONDS))
        assertTrue(c.decide(fpOne, allow = true))
        assertTrue(c.decide(fpTwo, allow = true))
        return c
    }

    @Test fun aRefusalNamingOneOfTwoAnswersIsAboutThatAnswer() {
        val d = FakeDesktop(tls = false).track()
        val rec = Recorder()
        val c = twoAnswersInFlight(d, rec)
        // The SECOND answer is the one refused; the first must keep its claim untouched.
        d.push(namedRefusal("off", fpTwo))
        assertEquals(fpTwo to Refusal.OFF, rec.refusals.poll(5, TimeUnit.SECONDS))
        assertGivenBack(c, fpTwo)
        assertStillClaimed(c, fpOne)
        assertNull("a refusal is not a link error", rec.errors.poll(500, TimeUnit.MILLISECONDS))
    }

    @Test fun aRefusalWithNoFingerprintIsAboutTheOldestAnswerInFlight() {
        val d = FakeDesktop(tls = false).track()
        val rec = Recorder()
        val c = twoAnswersInFlight(d, rec)
        // An older engine names nothing: the oldest answer in flight is the one it is about.
        d.push(refusal(d, "off"))
        assertEquals(fpOne to Refusal.OFF, rec.refusals.poll(5, TimeUnit.SECONDS))
        assertGivenBack(c, fpOne)
        assertStillClaimed(c, fpTwo)
        assertNull("a refusal is not a link error", rec.errors.poll(500, TimeUnit.MILLISECONDS))
    }

    @Test fun aRefusalNamingARequestThatIsNotInFlightRestoresNoClaim() {
        val d = FakeDesktop(tls = false).track()
        val rec = Recorder()
        val c = connected(d, rec)
        d.push(approval(fpOne))
        assertNotNull(rec.approvals.poll(5, TimeUnit.SECONDS))
        // Settled on the computer (or never this phone's): the book holds nothing, and a claim is never invented for it.
        d.push("""{"type":"approvalResolved","fingerprint":"$fpOne"}""")
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline && c.approvals.active().isNotEmpty()) Thread.sleep(20)
        d.push(namedRefusal("off", fpOne))
        assertEquals("the refusal is still reported with the request it names", fpOne to Refusal.OFF, rec.refusals.poll(5, TimeUnit.SECONDS))
        assertTrue("nothing is answerable again behind the phone's back", c.approvals.active().isEmpty())
        assertStillClaimed(c, fpOne)
        assertNull("a refusal is not a link error", rec.errors.poll(500, TimeUnit.MILLISECONDS))
    }

    @Test fun aFailedRefusalKeepsTheCardEvenThoughNothingWasSettled() {
        val d = FakeDesktop(tls = false).track()
        val rec = Recorder()
        val c = connected(d, rec)
        d.push(d.approvalJson(System.currentTimeMillis()))
        val req = rec.approvals.poll(5, TimeUnit.SECONDS)!!
        assertTrue(c.decide(req.fingerprint, allow = true))
        // jesscore: an unmapped registry refusal — NOTHING settled, so the request stays pending until approvalResolved.
        d.push(namedRefusal("failed", req.fingerprint))
        assertEquals(req.fingerprint to Refusal.FAILED, rec.refusals.poll(5, TimeUnit.SECONDS))
        assertGivenBack(c, req.fingerprint)
        assertTrue("a failed answer can still be denied", c.decide(req.fingerprint, allow = false))
        assertNull("a refusal is not a link error", rec.errors.poll(500, TimeUnit.MILLISECONDS))
    }

    @Test fun aFailedWithNothingInFlightIsStillAChatFramesLinkError() {
        val d = FakeDesktop(tls = false).track()
        val rec = Recorder()
        connected(d, rec)
        // The engine refuses a `chatNew` with the very same code and no fingerprint: that one is not about an answer.
        d.push(refusal(d, "failed"))
        assertEquals("failed", rec.errors.poll(5, TimeUnit.SECONDS))
        assertNull("nothing was refused as an answer", rec.refusals.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test fun aBadRequestWithNothingInFlightIsStillALinkError() {
        val d = FakeDesktop(tls = false).track()
        val rec = Recorder()
        connected(d, rec)
        // The engine refuses a `chatNew` with the same code (PROTOCOL.md AC-07): that one is not about an answer.
        d.push(refusal(d, "bad_request"))
        assertEquals("bad_request", rec.errors.poll(5, TimeUnit.SECONDS))
        assertNull("nothing was refused as an answer", rec.refusals.poll(300, TimeUnit.MILLISECONDS))
    }
}
