package com.kenai.android.provider

import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException

/**
 * How a provider's request is opened. One seam, so no test ever touches the internet: production is [HttpsOpener], the
 * tests use an opener that allows loopback only. Nothing else in the app opens a connection to a provider.
 */
fun interface HttpOpener {
    /** A connection to [url], ready to be configured; nothing is sent until the caller connects. */
    fun open(url: String, method: String): HttpURLConnection
}

/**
 * The production opener: https only, never a user or a password in the address, and a redirect is answered as a
 * redirect rather than followed, so nobody can move a request (and its key) to another host. A refusal is an
 * [IllegalArgumentException] with a sentence that never repeats the address's userinfo.
 */
class HttpsOpener : HttpOpener {
    override fun open(url: String, method: String): HttpURLConnection {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            throw IllegalArgumentException("provider: the address is not a URL")
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) throw IllegalArgumentException("provider: https only")
        if (uri.userInfo != null) throw IllegalArgumentException("provider: the address cannot carry a user")
        if (uri.host.isNullOrBlank()) throw IllegalArgumentException("provider: the address has no host")
        val connection = uri.toURL().openConnection()
        if (connection !is HttpURLConnection) throw IllegalArgumentException("provider: the address is not HTTP")
        return connection.apply {
            instanceFollowRedirects = false
            useCaches = false
            requestMethod = method
        }
    }
}
