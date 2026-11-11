package com.kenai.android.link

import com.kenai.android.core.Words
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/** The questions "Can't connect?" asks, in the order that makes each answer meaningful. */
enum class DiagStep {
    NETWORK,
    ADDRESS,
    DISCOVERY,
    SECURE,
    TOKEN,
    ;

    /** Its name on the screen and in the copied report, in the phone's language ([Words]). */
    val title: String get() = Words.current().diagTitle(this)
}

sealed class DiagResult {
    /** [detail] says what was seen in a few words. */
    data class Ok(val detail: String) : DiagResult()
    /** [problem] is what is wrong; [hint] is what to try, calmly. */
    data class Problem(val problem: String, val hint: String) : DiagResult()
    /** Not asked, because a step before it already failed. */
    data class Skipped(val why: String) : DiagResult()
}

/** What the checks need from the phone; the real one is [LanDiagnostics], tests use fakes. */
interface DiagEnv {
    fun network(): DiagResult
    fun address(): DiagResult
    fun discovery(): DiagResult
    fun secure(): DiagResult
    fun token(): DiagResult
}

object Diagnostics {
    /** Runs every check, reporting each as it finishes. A step whose premise failed is skipped, not guessed. */
    fun run(env: DiagEnv, onResult: (DiagStep, DiagResult) -> Unit = { _, _ -> }): List<Pair<DiagStep, DiagResult>> {
        val out = ArrayList<Pair<DiagStep, DiagResult>>()
        fun put(step: DiagStep, r: DiagResult) { out += step to r; onResult(step, r) }
        fun failed(step: DiagStep) = out.any { it.first == step && it.second !is DiagResult.Ok }

        val w = Words.current()
        put(DiagStep.NETWORK, safe(w) { env.network() })
        put(DiagStep.ADDRESS, if (failed(DiagStep.NETWORK)) DiagResult.Skipped(w.needsWifi) else safe(w) { env.address() })
        put(DiagStep.DISCOVERY, if (failed(DiagStep.NETWORK)) DiagResult.Skipped(w.needsWifi) else safe(w) { env.discovery() })
        put(DiagStep.SECURE, if (failed(DiagStep.ADDRESS)) DiagResult.Skipped(w.needsTheAddress) else safe(w) { env.secure() })
        put(DiagStep.TOKEN, if (failed(DiagStep.ADDRESS) || failed(DiagStep.SECURE)) DiagResult.Skipped(w.needsTheSecureConnection) else safe(w) { env.token() })
        return out
    }

    private fun broken(e: Exception, w: Words) = DiagResult.Problem(w.checkFailed(e.javaClass.simpleName), w.tryAgain)

    private fun safe(w: Words, f: () -> DiagResult): DiagResult = try { f() } catch (e: Exception) { broken(e, w) }

    /**
     * "192.168.1.20" -> "192.168.x.x": enough to tell a home network from a hotspot, not enough to find a house. A name is "a name
     * (hidden)", in the phone's language ([Words]) like the rest of the report.
     */
    fun maskHost(host: String, w: Words = Words.current()): String {
        val parts = host.split(".")
        return if (parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.all(Char::isDigit) }) "${parts[0]}.${parts[1]}.x.x" else w.aNameHidden
    }

    /**
     * The report the user can copy and send, for support, in the phone's language ([w], kenai-0.1 S6): its frame, the names of the
     * checks and what each check found, as the screen showed it. It names the checks and what they found; it never contains the
     * pairing code, the certificate fingerprint, the computer's name, the full address or any key. The marks are padded to one column.
     */
    fun report(
        appVersion: String, androidVersion: String, device: String, results: List<Pair<DiagStep, DiagResult>>, hostMasked: String, via: String,
        w: Words = Words.current(),
    ): String =
        buildString {
            appendLine(w.reportHeader(appVersion, androidVersion, device))
            appendLine(w.reportComputer(hostMasked, via))
            val width = maxOf(OK.length, w.reportProblem.length, w.reportSkipped.length) + 2
            for ((step, r) in results) {
                val (mark, text) = when (r) {
                    is DiagResult.Ok -> OK to r.detail
                    is DiagResult.Problem -> w.reportProblem to r.problem
                    is DiagResult.Skipped -> w.reportSkipped to r.why
                }
                appendLine("${mark.padEnd(width)}${w.diagTitle(step)}: $text")
            }
        }.trimEnd()

    /** A passed check's mark: the same in every language, so it is not in [Words]. */
    private const val OK = "OK"
}

/**
 * The real checks against one pairing. Plain JVM except what the phone supplies: whether Wi-Fi is up, and a way to look the computer
 * up on the network ([discover]). The pairing code is used only to say hello, as the app does; it is never put in a result.
 */
class LanDiagnostics(
    private val p: PairingPayload,
    private val wifiUp: () -> Boolean,
    private val discover: () -> List<Found>,
    private val connector: Connector = PinnedTls.connector(p),
    private val timeoutMs: Int = 3_000,
) : DiagEnv {
    /** The phone's words, read at each check. */
    private val w: Words get() = Words.current()

    private fun masked(host: String) = Diagnostics.maskHost(host, w)

    override fun network(): DiagResult = when {
        wifiUp() -> DiagResult.Ok(w.wifiConnected)
        else -> DiagResult.Problem(w.notOnWifi, w.joinTheSameWifi)
    }

    override fun address(): DiagResult {
        val started = System.nanoTime()
        val s = Socket()
        return try {
            s.connect(InetSocketAddress(p.host, p.port), timeoutMs)
            DiagResult.Ok(w.answersIn((System.nanoTime() - started) / 1_000_000, masked(p.host)))
        } catch (_: SocketTimeoutException) {
            DiagResult.Problem(w.noAnswerFrom(masked(p.host)), w.noAnswerHint)
        } catch (_: IOException) {
            DiagResult.Problem(w.cannotReachHost(masked(p.host)), w.newAddressHint)
        } finally {
            runCatching { s.close() }
        }
    }

    override fun discovery(): DiagResult {
        val found = Discovery.candidates(discover(), p)
        return when {
            found.isEmpty() -> DiagResult.Problem(w.notAnnouncing, w.notAnnouncingHint)
            found.any { it.host == p.host && it.port == p.port } -> DiagResult.Ok(w.foundAtTheSavedAddress)
            else -> DiagResult.Problem(w.foundElsewhere(masked(found.first().host)), w.foundElsewhereHint)
        }
    }

    override fun secure(): DiagResult = try {
        connector.connect(timeoutMs).close()
        DiagResult.Ok(w.certificateMatches)
    } catch (e: SSLException) {
        if (generateSequence<Throwable>(e) { it.cause }.any { it is java.security.cert.CertificateException }) {
            DiagResult.Problem(w.notThePairedComputer, w.reinstalledHint)
        } else {
            DiagResult.Problem(w.handshakeFailed, w.handshakeHint)
        }
    } catch (_: IOException) {
        DiagResult.Problem(w.secureNotOpened, w.secureNotOpenedHint)
    }

    override fun token(): DiagResult {
        val s = try {
            connector.connect(timeoutMs)
        } catch (_: IOException) {
            return DiagResult.Problem(w.couldNotSayHello, w.seeTheLinesAbove)
        }
        return try {
            s.soTimeout = 5_000
            s.getOutputStream().apply { write((Wire.encode(ClientMsg.Hello(Protocol.VERSION, p.token, "Diagnostics", emptyList())) + "\n").toByteArray()); flush() }
            val line = readLine(s) ?: return DiagResult.Problem(w.closedAtOnce, w.tryAgain)
            when (val m = Wire.decodeServer(line)) {
                is ServerMsg.Welcome ->
                    if (m.version == Protocol.VERSION) DiagResult.Ok(w.codeAccepted(m.version))
                    else DiagResult.Problem(w.versionMismatch(m.version, Protocol.VERSION), w.updateEither)
                is ServerMsg.Error ->
                    if (m.code == "auth") DiagResult.Problem(w.codeRefused, w.codeRefusedHint)
                    else DiagResult.Problem(w.answeredWithError(m.code), w.tryAgain)
                else -> DiagResult.Problem(w.unexpectedAnswer, w.updateBoth)
            }
        } catch (_: SocketTimeoutException) {
            DiagResult.Problem(w.noAnswerToHello, w.busyOrAsleep)
        } catch (_: IOException) {
            DiagResult.Problem(w.brokeDuringHello, w.tryAgain)
        } finally {
            runCatching { s.getOutputStream().apply { write((Wire.encode(ClientMsg.Bye) + "\n").toByteArray()); flush() } }
            runCatching { s.close() }
        }
    }

    private fun readLine(s: Socket): String? {
        val b = java.io.ByteArrayOutputStream()
        while (b.size() < Protocol.MAX_LINE_BYTES) {
            val x = s.getInputStream().read()
            if (x < 0) return null
            if (x == '\n'.code) return b.toString("UTF-8")
            b.write(x)
        }
        return null
    }
}
