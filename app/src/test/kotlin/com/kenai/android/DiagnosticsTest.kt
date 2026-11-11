package com.kenai.android

import com.kenai.android.link.DiagEnv
import com.kenai.android.link.DiagResult
import com.kenai.android.link.Diagnostics
import com.kenai.android.link.FakeDesktop
import com.kenai.android.link.LanDiagnostics
import com.kenai.android.link.PairingPayload
import com.kenai.android.link.PinnedTls
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The copied "Can't connect?" report follows the phone's language (kenai-0.1 S6, «todo en español»): its frame, the names of the
 * checks and the hidden-name wording are Spanish on a Spanish phone and exactly as before in English. The redaction does not
 * change with the language: no pairing code, fingerprint, computer name or full address. The checks themselves are pinned in
 * link/DiagnosticsTest.
 */
class DiagnosticsTest {
    private lateinit var saved: Locale
    private val closeables = mutableListOf<AutoCloseable>()

    @Before fun remember() { saved = Locale.getDefault() }

    @After fun restore() {
        Locale.setDefault(saved)
        closeables.forEach { runCatching { it.close() } }
    }

    private fun spanish() = Locale.setDefault(Locale.forLanguageTag("es-AR"))

    /** Wi-Fi up, the address does not answer: one OK, one problem, the rest skipped or found. */
    private val env = object : DiagEnv {
        override fun network() = DiagResult.Ok("wifi")
        override fun address() = DiagResult.Problem("closed", "hint")
        override fun discovery() = DiagResult.Ok("found")
        override fun secure() = DiagResult.Ok("never asked")
        override fun token() = DiagResult.Ok("never asked")
    }

    @Test fun theCopiedReportIsSpanishOnASpanishPhone() {
        spanish()
        val results = Diagnostics.run(env)
        val text = Diagnostics.report("0.1.0", "14", "Google Pixel 7", results, Diagnostics.maskHost("my-pc.local"), "el enlace directo")
        assertEquals(
            listOf(
                "Kenai para Android 0.1.0 · Android 14 · Google Pixel 7",
                "Computadora: un nombre (oculto) · conectada por: el enlace directo",
                "OK          Red del teléfono: wifi",
                "PROBLEMA    La dirección guardada responde: closed",
                "OK          La computadora se anuncia: found",
                "SIN PROBAR  Conexión segura (certificado fijado): Necesita que la dirección responda",
                "SIN PROBAR  Código de emparejamiento y versión: Necesita la conexión segura",
            ),
            text.lines(),
        )
    }

    @Test fun theCopiedReportStaysExactlyAsBeforeInEnglish() {
        Locale.setDefault(Locale.ENGLISH)
        val results = Diagnostics.run(env)
        val text = Diagnostics.report("0.1.0", "14", "Google Pixel 7", results, Diagnostics.maskHost("my-pc.local"), "the direct link")
        assertEquals(
            listOf(
                "Kenai for Android 0.1.0 · Android 14 · Google Pixel 7",
                "Computer: a name (hidden) · connected via: the direct link",
                "OK       Phone network: wifi",
                "PROBLEM  Saved address answers: closed",
                "OK       Computer announces itself: found",
                "SKIPPED  Secure connection (pinned certificate): Needs the address to answer",
                "SKIPPED  Pairing code and version: Needs the secure connection",
            ),
            text.lines(),
        )
    }

    @Test fun theSpanishReportHidesTheSameSecrets() {
        spanish()
        val d = FakeDesktop(tls = true).also { closeables += it }
        val token = "T".repeat(20)
        val p = PairingPayload("192.168.1.20", d.port, d.certSha256, token, "La compu de Álvaro")
        val masked = Diagnostics.maskHost(p.host)
        assertEquals("192.168.x.x", masked)
        val local = p.copy(host = "127.0.0.1")
        val results = Diagnostics.run(LanDiagnostics(local, { true }, { emptyList() }, PinnedTls.connector(local), timeoutMs = 1_500))
        val text = Diagnostics.report("0.1.0", "14", "Pixel", results, masked, "el enlace directo")
        for (secret in listOf(token, p.certSha256, p.desktopName, "192.168.1.20", "127.0.0.1")) assertFalse("report mentions $secret", text.contains(secret))
        assertTrue(text, text.contains("Computadora: 192.168.x.x · conectada por: el enlace directo"))
        assertTrue(text, text.lines().drop(2).all { it.startsWith("OK ") || it.startsWith("PROBLEMA ") || it.startsWith("SIN PROBAR ") })
    }
}
