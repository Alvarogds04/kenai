package com.abito.android.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The relay fields of a pairing link, how they are kept, and the relay's text hints. */
class RelayLinkTest {
    private val room = RelayCrypto.base64UrlEncode(ByteArray(16) { 1 })
    private val key = RelayCrypto.base64UrlEncode(ByteArray(32) { 2 })
    private val access = RelayCrypto.base64UrlEncode(ByteArray(32) { 3 })
    private val fp = "ab".repeat(32)
    private val lan = "abito://pair?v=1&host=192.168.1.20&port=47821&fp=$fp&token=tok_en-1234567890abcd&name=PC"
    private val withRelay = "$lan&relay=wss:%2F%2Frelay.example.workers.dev&room=$room&key=$key&access=$access"

    @Test fun anOldLinkStillPairsOverTheLanOnly() {
        val p = PairingPayload.parse(lan)!!
        assertNull(p.relay)
        assertEquals("192.168.1.20", p.host)
    }

    @Test fun relayFieldsAreIgnored_AbitoIsLanOnly() {
        assertNull(PairingPayload.parse(withRelay)!!.relay)
        // Even damaged relay fields do not matter: the LAN part decides.
        assertNull(PairingPayload.parse(withRelay.replace(key, "x"))!!.relay)
    }

    @Test fun theLanPartIsUnchangedByTheRelayFields() {
        val a = PairingPayload.parse(lan)!!
        val b = PairingPayload.parse(withRelay)!!
        assertEquals(a.copy(relay = null), b.copy(relay = null))
    }

    @Test fun theLongestRealisticLinkIsStillAccepted() {
        val name = java.net.URLEncoder.encode("é".repeat(48), "UTF-8").replace("+", "%20")
        val host = "${"a".repeat(63)}.${"b".repeat(63)}.workers.dev"
        val link = "abito://pair?v=1&host=2001:db8:aaaa:bbbb:cccc:dddd:eeee:ffff&port=47821&fp=$fp&token=${"t".repeat(128)}&name=$name" +
            "&relay=wss:%2F%2F$host:8443&room=$room&key=$key&access=$access"
        assertTrue("${link.length} characters", link.length < com.abito.android.core.PairingScan.MAX_LINK)
        assertTrue(com.abito.android.core.PairingScan.decide(link) is com.abito.android.core.ScanDecision.Pairing)
        assertNull(PairingPayload.parse(link)!!.relay)
    }

    @Test fun theConfirmationShowsTheRelayHostBeforeAnythingIsStored() {
        val dialog = java.io.File("src/main/kotlin/com/abito/android/ui/PairConfirm.kt").readText()
        assertTrue(dialog.contains("p.relay?.relayUrl()?.host"))
        assertTrue(dialog.contains("R.string.pair_confirm_relay"))
    }

    @Test fun theHintsAreTheTwoExactMessages() {
        assertEquals(RelayHints.Hint.ONLINE, RelayHints.parse("""{"peer":"online"}"""))
        assertEquals(RelayHints.Hint.OFFLINE, RelayHints.parse("""{"peer":"offline"}"""))
        for (other in listOf("pong", "", """{"peer":"online","x":1}""", """{"peer":"gone"}""", "ONLINE")) {
            assertEquals(RelayHints.Hint.OTHER, RelayHints.parse(other))
        }
    }

    @Test fun aRelayAccessMessageIsAcceptedOnlyWithAWellFormedKey() {
        val ok = Wire.decodeServer("""{"type":"relayAccess","access":"$access"}""")
        assertTrue(ok is ServerMsg.RelayAccess && ok.access == access)
        assertNull(Wire.decodeServer("""{"type":"relayAccess","access":"short"}"""))
        assertNull(Wire.decodeServer("""{"type":"relayAccess"}"""))
    }
}

class PairingCodecTest {
    private val room = RelayCrypto.base64UrlEncode(ByteArray(16) { 1 })
    private val key = RelayCrypto.base64UrlEncode(ByteArray(32) { 2 })
    private val access = RelayCrypto.base64UrlEncode(ByteArray(32) { 3 })
    private val lanOnly = PairingPayload("192.168.1.20", 47821, "ab".repeat(32), "tok_en-1234567890abcd", "PC")

    @Test fun aLanPairingRoundTrips() {
        val text = PairingCodec.encode(lanOnly)
        assertEquals(5, text.split("\n").size)
        assertEquals(lanOnly, PairingCodec.decode(text))
    }

    @Test fun aStoredRelayIsDropped_AbitoIsLanOnly() {
        val p = lanOnly.copy(relay = RelayPairing("wss://relay.example.workers.dev", room, key, access))
        val back = PairingCodec.decode(PairingCodec.encode(p))!!
        assertEquals(lanOnly, back)
        assertNull(back.relay)
    }

    @Test fun aPairingSavedByAnOlderAppStillLoadsAsLanOnly() {
        val old = listOf("192.168.1.20", "47821", "ab".repeat(32), "tok_en-1234567890abcd", "PC").joinToString("\n")
        val p = PairingCodec.decode(old)!!
        assertNull(p.relay)
        assertEquals("192.168.1.20", p.host)
    }

    @Test fun anUnreadablePairingIsNotAPairing() {
        assertNull(PairingCodec.decode(""))
        assertNull(PairingCodec.decode("a\nb"))
        assertNull(PairingCodec.decode("h\nnotaport\nfp\ntoken"))
    }
}
