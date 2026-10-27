package com.abito.android.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Abito is LAN only (HU-049 S4): links and stored pairings with an old cloud relay's fields still pair, over the LAN only. */
class LanOnlyPairingTest {
    private val fp = "ab".repeat(32)
    private val lan = "abito://pair?v=1&host=192.168.1.20&port=47821&fp=$fp&token=tok_en-1234567890abcd&name=PC"
    private val relayFields = "&relay=wss:%2F%2Frelay.example.workers.dev&room=AQEBAQEBAQEBAQEBAQEBAQ&key=" + "A".repeat(43) + "&access=" + "B".repeat(43)
    private val lanOnly = PairingPayload("192.168.1.20", 47821, fp, "tok_en-1234567890abcd", "PC")

    @Test fun aLanLinkPairs() {
        assertEquals(lanOnly, PairingPayload.parse(lan))
    }

    @Test fun aLinkWithRelayFieldsPairsAsTheSameLanPairing() {
        assertEquals(lanOnly, PairingPayload.parse(lan + relayFields))
        // Damaged relay fields do not matter either: the LAN part decides.
        assertEquals(lanOnly, PairingPayload.parse("$lan&relay=nonsense&key=x"))
    }

    @Test fun theLongestRealisticLinkIsStillAccepted() {
        val name = java.net.URLEncoder.encode("é".repeat(48), "UTF-8").replace("+", "%20")
        val link = "abito://pair?v=1&host=2001:db8:aaaa:bbbb:cccc:dddd:eeee:ffff&port=47821&fp=$fp&token=${"t".repeat(128)}&name=$name"
        assertTrue("${link.length} characters", link.length < com.abito.android.core.PairingScan.MAX_LINK)
        assertTrue(com.abito.android.core.PairingScan.decide(link) is com.abito.android.core.ScanDecision.Pairing)
        assertNotNull(PairingPayload.parse(link))
    }

    @Test fun aPairingIsStoredAsItsFiveLanFields() {
        val text = PairingCodec.encode(lanOnly)
        assertEquals(5, text.split("\n").size)
        assertEquals(lanOnly, PairingCodec.decode(text))
    }

    @Test fun aPairingStoredWithARelayByAnOlderBuildLoadsAsTheSameLanPairing() {
        val old = listOf("192.168.1.20", "47821", fp, "tok_en-1234567890abcd", "PC", "wss://relay.example.workers.dev", "room", "key", "access")
        assertEquals(lanOnly, PairingCodec.decode(old.joinToString("\n")))
    }

    @Test fun anUnreadablePairingIsNotAPairing() {
        assertNull(PairingCodec.decode(""))
        assertNull(PairingCodec.decode("a\nb"))
        assertNull(PairingCodec.decode("h\nnotaport\nfp\ntoken"))
    }

    @Test fun theHelloNeverAsksForARelayAndARelayMessageIsIgnored() {
        assertFalse("relay" in Protocol.CAPABILITIES)
        assertNull(Wire.decodeServer("""{"type":"relayAccess","access":"${"B".repeat(43)}"}"""))
    }
}
