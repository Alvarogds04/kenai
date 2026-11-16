package com.kenai.android.provider

/**
 * Where the key for [providerId] comes from: `app/ProviderVault`, one encrypted blob per provider in the Android Keystore
 * (T1a-3a). No other part of the app sees the key, and [OpenAiCompatibleBackend] only ever puts it in the
 * `Authorization: Bearer` header. A null or blank answer means "no key", which is the `no_key` reason.
 */
fun interface KeyProvider {
    fun key(providerId: String): String?
}
