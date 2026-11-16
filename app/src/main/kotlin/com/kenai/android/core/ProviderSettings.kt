package com.kenai.android.core

import com.kenai.android.provider.ProviderConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Whether the phone holds the key of a provider: [PRESENT] it does, [ABSENT] it never had one or the user cleared it,
 * [LOST] there is one but it cannot be decrypted any more (a reset Keystore, a corrupted file). A lost key is reported
 * and kept, never cleared behind the user's back: the key of a provider cannot simply be made again, the way a pairing
 * can (`app/ProviderVault.kt`).
 */
enum class ProviderKeyState { PRESENT, ABSENT, LOST }

/**
 * A starting point the providers screen offers (NaN, OpenAI). It adds nothing by itself: a preset is a suggestion the
 * user copies into a provider he keeps, and it carries no key. There is no preset for the local model through LiteLLM:
 * that address belongs to each user's own machine (S10), so it can only be a setting.
 */
data class ProviderPreset(
    val id: String,
    val name: String,
    /** `https://…`, without the trailing slash. */
    val baseUrl: String,
    /** The model to start with; blank when the name is the user's to choose (or to read from `GET /models`). */
    val defaultModel: String,
) {
    /** The provider this preset would create, as the user would type it. */
    fun asConfig(): ProviderConfig = ProviderConfig(id, name, baseUrl, defaultModel)
}

object ProviderPresets {
    /** NaN builders, with the `nan-default` model it documents (L14). */
    val NAN = ProviderPreset("nan", "nan builders", "https://api.nan.builders/v1", "nan-default")

    /** OpenAI: its address; which model to use is the user's to type in. */
    val OPENAI = ProviderPreset("openai", "OpenAI", "https://api.openai.com/v1", "")

    val ALL = listOf(NAN, OPENAI)
}

/**
 * What the user configured for the phone's own chat: the providers themselves, and which one the chat talks to.
 * [activeChat] is a provider id, or null for the computer, which is the default and behaves exactly as in 0.1.
 *
 * One JSON document in a [KeyValueStore], next to the rest of the settings. No key ever comes in here: the keys live in
 * the Android Keystore (`app/ProviderVault.kt`), behind a port, and the chat only asks for one when it makes a request.
 * The loader is tolerant and bounded — a document this version does not understand, or a broken one, means no providers
 * and never a crash; at most [MAX_PROVIDERS] providers with at most [MAX_MODELS] models each.
 */
data class ProviderSettings(
    val providers: List<ProviderConfig> = emptyList(),
    /** The provider the chat talks to; null means the computer, as in 0.1. */
    val activeChat: String? = null,
) {
    /** The provider in use, or null: the computer, or a choice that no longer exists. */
    fun activeProvider(): ProviderConfig? = activeChat?.let { id -> providers.firstOrNull { it.id == id } }

    /** [config] added, or replacing the one with its id in its own place. A full list refuses a new one instead of dropping another. */
    fun with(config: ProviderConfig): ProviderSettings {
        val index = providers.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            val next = providers.toMutableList()
            next[index] = config
            return copy(providers = next)
        }
        if (providers.size >= MAX_PROVIDERS) return this
        return copy(providers = providers + config)
    }

    /** Without the provider [id]; the chat goes back to the computer when that was the one in use. */
    fun without(id: String): ProviderSettings = copy(
        providers = providers.filter { it.id != id },
        activeChat = activeChat?.takeIf { it != id },
    )

    companion object {
        const val KEY = "providers"
        const val VERSION = 1
        /** More providers than this is a document no screen can offer anyway. */
        const val MAX_PROVIDERS = 8
        /** Koda's own gateway bound for one provider's list (L14). */
        const val MAX_MODELS = 128
        const val ID_MAX = 40
        const val NAME_MAX = 60
        const val URL_MAX = 400
        const val MODEL_MAX = 128

        private const val VERSION_FIELD = "version"
        private const val PROVIDERS = "providers"
        private const val ACTIVE = "activeChat"

        fun load(store: KeyValueStore): ProviderSettings = decode(store.getString(KEY, ""))

        fun save(store: KeyValueStore, s: ProviderSettings) {
            store.put(KEY, encode(s))
        }

        /** The document these settings mean. Nothing that cannot be used is written: a broken address is dropped. */
        fun encode(s: ProviderSettings): String {
            val array = JSONArray()
            val kept = ArrayList<String>()
            for (config in s.providers) {
                val valid = config.validated() ?: continue
                if (kept.size >= MAX_PROVIDERS || kept.contains(valid.id)) continue
                kept += valid.id
                array.put(
                    JSONObject()
                        .put("id", valid.id.take(ID_MAX))
                        .put("name", valid.name.take(NAME_MAX))
                        .put("baseUrl", valid.baseUrl.take(URL_MAX))
                        .put("defaultModel", valid.defaultModel.take(MODEL_MAX))
                        .put("models", modelsJson(valid)),
                )
            }
            val active = s.activeChat?.takeIf { id -> kept.contains(id) } ?: ""
            return JSONObject().put(VERSION_FIELD, VERSION).put(PROVIDERS, array).put(ACTIVE, active).toString()
        }

        /** A document this version does not know, or a broken one, means no providers and the computer: never a crash. */
        fun decode(raw: String): ProviderSettings {
            if (raw.isBlank()) return ProviderSettings()
            val document = try {
                JSONObject(raw)
            } catch (_: JSONException) {
                return ProviderSettings()
            }
            if (document.optInt(VERSION_FIELD, 0) != VERSION) return ProviderSettings()
            val array = document.optJSONArray(PROVIDERS) ?: JSONArray()
            val providers = ArrayList<ProviderConfig>()
            for (i in 0 until array.length()) {
                val config = array.optJSONObject(i)?.let(::configOf) ?: continue
                if (providers.any { it.id == config.id }) continue
                providers += config
                if (providers.size == MAX_PROVIDERS) break
            }
            val active = document.optString(ACTIVE, "")
                .takeIf { it.isNotBlank() }
                ?.takeIf { id -> providers.any { it.id == id } }
            return ProviderSettings(providers, active)
        }

        private fun configOf(o: JSONObject): ProviderConfig? {
            val id = o.optString("id", "").trim().take(ID_MAX)
            if (id.isEmpty()) return null
            val models = ArrayList<ChatModelOption>()
            val array = o.optJSONArray("models") ?: JSONArray()
            for (i in 0 until minOf(array.length(), MAX_MODELS)) {
                val m = array.optJSONObject(i) ?: continue
                val modelId = m.optString("id", "").trim().take(MODEL_MAX)
                if (modelId.isEmpty()) continue
                models += ChatModelOption(modelId, m.optString("provider", id).trim().take(ID_MAX), m.optString("label", modelId).take(MODEL_MAX))
            }
            return ProviderConfig(
                id = id,
                name = o.optString("name", id).trim().take(NAME_MAX),
                baseUrl = o.optString("baseUrl", "").trim().take(URL_MAX),
                defaultModel = o.optString("defaultModel", "").trim().take(MODEL_MAX),
                models = models,
            ).validated()
        }

        private fun modelsJson(config: ProviderConfig): JSONArray {
            val array = JSONArray()
            for (model in config.models.take(MAX_MODELS)) {
                val id = model.id.trim().take(MODEL_MAX)
                if (id.isEmpty()) continue
                array.put(JSONObject().put("id", id).put("provider", model.provider.trim().take(ID_MAX)).put("label", model.label.take(MODEL_MAX)))
            }
            return array
        }
    }
}
