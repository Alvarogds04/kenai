package com.kenai.android.provider

/**
 * Where the key for [providerId] comes from. T1a-3 implements it with `SecureStore` (the Android Keystore); no other
 * part of the app sees the key, and [OpenAiCompatibleBackend] only ever puts it in the `Authorization: Bearer` header.
 * A null or blank answer means "no key", which is the `no_key` reason.
 */
fun interface KeyProvider {
    fun key(providerId: String): String?
}
