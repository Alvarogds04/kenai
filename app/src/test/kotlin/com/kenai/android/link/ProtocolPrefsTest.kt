package com.kenai.android.link

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Kenai has no wardrobe: the app never asks for `prefs` and ignores it if a computer sends one (HU-049 T3). */
class ProtocolPrefsTest {
    @Test fun aPrefsMessageIsIgnored() {
        assertNull(Wire.decodeServer("""{"type":"prefs","outfit":"beanie"}"""))
    }

    @Test fun theHelloDoesNotAskForPrefs() {
        assertFalse("prefs" in Protocol.CAPABILITIES)
        val o = JSONObject(Wire.encode(ClientMsg.Hello(1, "T".repeat(20), "Phone", Protocol.CAPABILITIES)))
        assertFalse(o.getJSONArray("caps").toString().contains("prefs"))
    }
}
