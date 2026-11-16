package com.kenai.android.provider

import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The test opener: plain http is allowed only towards a loopback address, and every address it was handed is kept so a
 * test can prove the key never travelled in one. Production is [HttpsOpener]; nothing else may open a connection.
 */
internal class LoopbackOpener : HttpOpener {
    val opened = CopyOnWriteArrayList<String>()

    override fun open(url: String, method: String): HttpURLConnection {
        val uri = URI(url)
        val loopback = uri.host == "127.0.0.1" || uri.host == "localhost" || uri.host == "::1"
        require(uri.scheme == "http" && loopback && uri.userInfo == null) { "the test opener only allows loopback http" }
        opened.add(url)
        return (uri.toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = method
            useCaches = false
        }
    }
}
