package com.kenai.android.provider

import java.net.HttpURLConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The address of a provider, before any request is made: https, a host, no user or password in it, no fragment, no
 * query, a path that does not climb up, and the trailing slash removed because the endpoints are built by adding to it.
 */
class ProviderConfigTest {
    private fun config(baseUrl: String) = ProviderConfig("litellm", "LiteLLM", baseUrl, "local-model")

    @Test fun aPlainHttpsAddressIsAcceptedAsItIs() {
        val ok = config("https://api.openai.com/v1").validated()!!
        assertEquals("https://api.openai.com/v1", ok.baseUrl)
        assertEquals("litellm", ok.id)
    }

    @Test fun aTrailingSlashIsRemoved() {
        assertEquals("https://api.nan.builders/v1", config("https://api.nan.builders/v1/").validated()!!.baseUrl)
        assertEquals("https://api.openai.com", config("https://api.openai.com/").validated()!!.baseUrl)
        assertEquals("https://api.openai.com", config("https://api.openai.com").validated()!!.baseUrl)
    }

    @Test fun plainHttpIsRefused() {
        assertEquals(null, config("http://api.openai.com/v1").validated())
        assertEquals(null, config("http://192.168.1.20:4000/v1").validated())
        assertEquals(null, config("ftp://api.openai.com/v1").validated())
    }

    @Test fun anAddressWithoutAHostIsRefused() {
        assertEquals(null, config("https:///v1").validated())
        assertEquals(null, config("https://").validated())
        assertEquals(null, config("/v1").validated())
        assertEquals(null, config("not a url").validated())
        assertEquals(null, config("").validated())
    }

    @Test fun aUserOrAPasswordInTheAddressIsRefused() {
        assertEquals(null, config("https://user@api.openai.com/v1").validated())
        assertEquals(null, config("https://user:secret@api.openai.com/v1").validated())
    }

    @Test fun AFragmentOrAQueryIsRefused() {
        assertEquals(null, config("https://api.openai.com/v1#x").validated())
        assertEquals(null, config("https://api.openai.com/v1?key=abc").validated())
    }

    @Test fun aClimbingPathIsRefused() {
        assertEquals(null, config("https://api.openai.com/v1/../admin").validated())
        assertEquals(null, config("https://api.openai.com/..").validated())
    }

    @Test fun anEmptyIdIsRefused() {
        assertEquals(null, ProviderConfig(" ", "LiteLLM", "https://api.openai.com/v1", "m").validated())
    }

    @Test fun normalisedIsTheAddressTheEndpointsAreBuiltFrom() {
        assertEquals("https://api.openai.com/v1", config("https://api.openai.com/v1/").normalised().baseUrl)
        assertEquals("https://api.openai.com/v1", config(" https://api.openai.com/v1 ").normalised().baseUrl)
    }
}

/**
 * The production opener is the one place that decides the wire: https only, no userinfo, and a redirect is answered as
 * a redirect (the caller turns it into `provider`), never followed. Nothing here connects: making the connection
 * object is not a request, so the test touches no network.
 */
class HttpsOpenerTest {
    @Test fun httpsIsOpenedAndRedirectsAreNeverFollowed() {
        val c: HttpURLConnection = HttpsOpener().open("https://api.openai.com/v1/models", "GET")
        assertEquals("GET", c.requestMethod)
        assertFalse("a redirect must never be followed", c.instanceFollowRedirects)
    }

    @Test fun plainHttpIsRefused() {
        assertRefused("http://api.openai.com/v1/models")
        assertRefused("http://192.168.1.20:4000/v1/models")
        assertRefused("ftp://api.openai.com/v1")
    }

    @Test fun anAddressWithAUserIsRefusedAndItsPasswordIsNotInTheMessage() {
        val raised = assertRefused("https://user:hunter2@api.openai.com/v1")
        assertFalse("the password must never be repeated", raised.message.orEmpty().contains("hunter2"))
    }

    @Test fun anAddressWithoutAHostIsRefused() {
        assertRefused("https:///v1")
        assertRefused("")
        assertRefused("not a url")
    }

    private fun assertRefused(url: String): IllegalArgumentException {
        try {
            HttpsOpener().open(url, "GET")
        } catch (e: IllegalArgumentException) {
            assertTrue("the message must be a sentence: ${e.message}", !e.message.isNullOrBlank())
            return e
        }
        fail("the opener accepted $url")
        throw AssertionError("unreachable")
    }
}
