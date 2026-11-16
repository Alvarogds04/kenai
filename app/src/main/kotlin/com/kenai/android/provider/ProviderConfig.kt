package com.kenai.android.provider

import com.kenai.android.core.ChatModelOption
import java.net.URI
import java.net.URISyntaxException

/**
 * One provider the phone calls itself, as the user configures it: `baseUrl` is the address every endpoint is built from
 * (`{base}/chat/completions`, `{base}/models`), [defaultModel] the model to start with, and [models] the list fixed in
 * the config — empty when the list is to be read from the endpoint (Koda's own provider record, L14).
 *
 * A plain data class: it holds no key. The key comes from a [KeyProvider] when a request is made, and only ever into
 * the `Authorization` header.
 */
data class ProviderConfig(
    /** Short name, `litellm`, `gpt`, `nan`... It is also the `provider` of every [ChatModelOption] read. */
    val id: String,
    /** What the providers screen shows. */
    val name: String,
    /** `https://…`, without the trailing slash; the endpoints add their own path. */
    val baseUrl: String,
    /** The model used when the user has not picked another. */
    val defaultModel: String,
    /** The models this provider offers; empty means "ask `GET /models`". */
    val models: List<ChatModelOption> = emptyList(),
) {
    /** The address the endpoints are built from: no spaces around it, no trailing slash. */
    fun normalised(): ProviderConfig = copy(baseUrl = baseUrl.trim().trimEnd('/'))

    /**
     * This config, normalised, or null when the address cannot be used: https only ([HttpsOpener] enforces the same
     * rule on the wire), a host, no user or password in the URL, no fragment and no query, and a path that does not
     * climb up with `..`.
     */
    fun validated(): ProviderConfig? {
        if (id.isBlank()) return null
        val uri = try {
            URI(baseUrl.trim())
        } catch (_: URISyntaxException) {
            return null
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null) return null
        if (uri.fragment != null) return null
        if (uri.query != null) return null
        val path = uri.path.orEmpty()
        if (path.split('/').any { it == ".." }) return null
        return normalised()
    }
}
