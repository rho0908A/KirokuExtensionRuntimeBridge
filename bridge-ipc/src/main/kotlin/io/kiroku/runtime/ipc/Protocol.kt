package io.kiroku.runtime.ipc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Wire protocol shared by the SDK and the Runtime Host.
 *
 * Versioning rules:
 *  - [PROTOCOL_VERSION] changes only for breaking envelope changes.
 *  - New methods and new optional response fields do not require a version bump.
 *  - A client must refuse to talk to a runtime whose major protocol differs.
 */
object RuntimeProtocol {
    /** Version of the AIDL + JSON envelope contract implemented by this source tree. */
    const val PROTOCOL_VERSION: Int = 1

    /** Fully qualified component of the Runtime Host service. */
    const val RUNTIME_PACKAGE: String = "io.kiroku.runtime.host"
    const val RUNTIME_SERVICE_CLASS: String = "io.kiroku.runtime.host.service.RuntimeHostService"
    const val SERVICE_ACTION: String = "io.kiroku.runtime.host.BIND_RUNTIME"

    /**
     * Signature-protected permission declared by the runtime and required from clients.
     *
     * Signature protection means only applications signed with the same certificate as the runtime
     * can bind. For independent host applications the runtime additionally supports an explicit
     * package allow-list (see `docs/security.md`); the allow-list is enforced in the service, not
     * in the SDK, so it cannot be bypassed by a modified client.
     */
    const val PERMISSION_BIND: String = "io.kiroku.runtime.host.permission.BIND"

    val JSON: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = false
        allowStructuredMapKeys = true
    }
}

/** Logical method names carried inside [RpcRequest]. */
object RuntimeMethods {
    const val PING = "runtime.ping"
    const val RUNTIME_INFO = "runtime.info"
    const val RUNTIME_LOGS = "runtime.logs"
    const val RUNTIME_CLEAR_LOGS = "runtime.clearLogs"

    const val EXTENSIONS_LIST = "extensions.list"
    const val EXTENSIONS_AVAILABLE = "extensions.available"
    const val EXTENSIONS_INSTALL = "extensions.install"
    const val EXTENSIONS_INSTALL_FILE = "extensions.installFile"
    const val EXTENSIONS_UNINSTALL = "extensions.uninstall"
    const val EXTENSIONS_UPDATE = "extensions.update"
    const val EXTENSIONS_RELOAD = "extensions.reload"

    const val REPOSITORIES_LIST = "repositories.list"
    const val REPOSITORIES_ADD = "repositories.add"
    const val REPOSITORIES_REMOVE = "repositories.remove"
    const val REPOSITORIES_REFRESH = "repositories.refresh"

    const val SEARCH = "sources.search"
    const val POPULAR = "sources.popular"
    const val LATEST = "sources.latest"
    const val DETAILS = "sources.details"
    const val EPISODES = "sources.episodes"
    const val STREAMS = "sources.streams"

    const val MANGA_SEARCH = "sources.searchManga"
    const val MANGA_POPULAR = "sources.popularManga"
    const val MANGA_LATEST = "sources.latestManga"
    const val MANGA_DETAILS = "sources.mangaDetails"
    const val MANGA_CHAPTERS = "sources.chapters"
    const val MANGA_PAGES = "sources.pages"
}

/** Parameter keys used in [RpcRequest.params]. */
object RuntimeParams {
    const val EXTENSION_ID = "extensionId"
    const val SOURCE_ID = "sourceId"
    const val QUERY = "query"
    const val PAGE = "page"
    const val ANIME_ID = "animeId"
    const val EPISODE_ID = "episodeId"
    const val MANGA_ID = "mangaId"
    const val CHAPTER_ID = "chapterId"
    const val URL = "url"
    const val REPOSITORY_ID = "repositoryId"
    const val NAME = "name"
    const val BACKEND = "backend"
    const val FILE_PATH = "filePath"
    const val LIMIT = "limit"
}

@Serializable
data class RpcRequest(
    /** Client generated correlation id; echoed back verbatim. */
    val id: String,
    val method: String,
    val params: JsonObject = JsonObject(emptyMap()),
) {
    fun encode(): String = RuntimeProtocol.JSON.encodeToString(serializer(), this)

    fun string(key: String): String? = params[key]?.jsonPrimitiveOrNull()?.contentOrNull()
    fun int(key: String, default: Int = 0): Int = string(key)?.toIntOrNull() ?: default
    fun bool(key: String, default: Boolean = false): Boolean = string(key)?.toBooleanStrictOrNull() ?: default

    companion object {
        fun decode(raw: String): RpcRequest = RuntimeProtocol.JSON.decodeFromString(serializer(), raw)
    }
}

@Serializable
data class RpcResponse(
    val id: String,
    val ok: Boolean,
    val result: JsonElement? = null,
    val error: ExtensionError? = null,
) {
    fun encode(): String = RuntimeProtocol.JSON.encodeToString(serializer(), this)

    inline fun <reified T> requireResult(): T {
        val payload = result ?: throw IllegalStateException("Runtime returned no payload for $id")
        return RuntimeProtocol.JSON.decodeFromJsonElement(kotlinx.serialization.serializer<T>(), payload)
    }

    companion object {
        fun decode(raw: String): RpcResponse = RuntimeProtocol.JSON.decodeFromString(serializer(), raw)

        fun success(id: String, result: JsonElement): RpcResponse = RpcResponse(id = id, ok = true, result = result)

        fun failure(id: String, error: ExtensionError): RpcResponse =
            RpcResponse(id = id, ok = false, error = error)
    }
}

/** Event kinds pushed from the runtime to registered clients. */
@Serializable
object RuntimeEventType {
    const val RUNTIME_STATE = "runtime.state"
    const val EXTENSION_LOADED = "extension.loaded"
    const val EXTENSION_FAILED = "extension.failed"
    const val INSTALL_PROGRESS = "extension.installProgress"
    const val INSTALL_COMPLETED = "extension.installed"
    const val UPDATE_COMPLETED = "extension.updated"
    const val UNINSTALLED = "extension.uninstalled"
    const val LOG = "runtime.log"
    const val ERROR = "runtime.error"
}

@Serializable
data class RuntimeEvent(
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: JsonObject = JsonObject(emptyMap()),
) {
    fun encode(): String = RuntimeProtocol.JSON.encodeToString(serializer(), this)

    companion object {
        fun decode(raw: String): RuntimeEvent = RuntimeProtocol.JSON.decodeFromString(serializer(), raw)

        fun of(type: String, payload: JsonObject = JsonObject(emptyMap())): RuntimeEvent =
            RuntimeEvent(type = type, payload = payload)
    }
}

/** Small helpers so call sites do not juggle `JsonPrimitive` construction by hand. */
fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    pairs.forEach { (key, value) ->
        when (value) {
            null -> Unit
            is JsonElement -> put(key, value)
            is String -> put(key, value)
            is Boolean -> put(key, value)
            is Int -> put(key, value)
            is Long -> put(key, value)
            is Double -> put(key, value)
            is Float -> put(key, value)
            else -> put(key, JsonPrimitive(value.toString()))
        }
    }
}

fun JsonElement?.jsonPrimitiveOrNull(): JsonPrimitive? = (this as? JsonPrimitive)

fun JsonPrimitive.contentOrNull(): String? = if (this.isString) content else content

fun JsonObject.toEventPayload(): JsonObject = this

internal fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitiveOrNull()?.contentOrNull()

@Suppress("unused")
internal fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
