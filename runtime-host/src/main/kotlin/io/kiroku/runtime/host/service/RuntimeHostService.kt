package io.kiroku.runtime.host.service

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.os.RemoteCallbackList
import io.kiroku.runtime.host.core.RuntimeManager
import io.kiroku.runtime.host.core.toExtensionError
import io.kiroku.runtime.ipc.Anime
import io.kiroku.runtime.ipc.AnimeDetails
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.Chapter
import io.kiroku.runtime.ipc.Episode
import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.Extension
import io.kiroku.runtime.ipc.ExtensionError
import io.kiroku.runtime.ipc.ExtensionRepository
import io.kiroku.runtime.ipc.IRuntimeCallback
import io.kiroku.runtime.ipc.IRuntimeHost
import io.kiroku.runtime.ipc.LogEntry
import io.kiroku.runtime.ipc.Manga
import io.kiroku.runtime.ipc.MangaDetails
import io.kiroku.runtime.ipc.MangaPage
import io.kiroku.runtime.ipc.RepositoryExtension
import io.kiroku.runtime.ipc.RpcRequest
import io.kiroku.runtime.ipc.RpcResponse
import io.kiroku.runtime.ipc.RuntimeEvent
import io.kiroku.runtime.ipc.RuntimeInfo
import io.kiroku.runtime.ipc.RuntimeMethods
import io.kiroku.runtime.ipc.RuntimeParams
import io.kiroku.runtime.ipc.RuntimeProtocol
import io.kiroku.runtime.ipc.StreamSource
import io.kiroku.runtime.ipc.jsonOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/**
 * The bound service host applications talk to.
 *
 * Every method crosses the Binder boundary as JSON, which keeps the AIDL surface at two methods and
 * lets the models evolve without Parcelable versioning. Requests are dispatched on the service's
 * coroutine scope and executed by [RuntimeManager], so the UI of this same app observes the exact
 * same state.
 */
class RuntimeHostService : Service() {

    private val manager: RuntimeManager by lazy { RuntimeManager.get(this) }
    private val json = RuntimeProtocol.JSON
    private val callbacks = RemoteCallbackList<IRuntimeCallback>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val binder = object : IRuntimeHost.Stub() {

        override fun getProtocolDescriptor(): String {
            guardCaller()
            return runBlocking {
                manager.initialize()
                json.encodeToString(RuntimeInfo.serializer(), manager.runtimeInfo())
            }
        }

        override fun execute(request: String): String {
            guardCaller()
            val parsed = try {
                RpcRequest.decode(request)
            } catch (e: Exception) {
                return RpcResponse.failure(
                    id = "",
                    error = ExtensionError(
                        code = ErrorCode.INVALID_REQUEST,
                        message = "Malformed request envelope: ${e.message}",
                    ),
                ).encode()
            }
            return runBlocking { dispatch(parsed) }.encode()
        }

        override fun registerCallback(callback: IRuntimeCallback?) {
            guardCaller()
            if (callback != null) callbacks.register(callback)
        }

        override fun unregisterCallback(callback: IRuntimeCallback?) {
            if (callback != null) callbacks.unregister(callback)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            manager.events.collect(::broadcast)
        }
        scope.launch {
            runCatching { manager.initialize() }
                .onFailure { manager.logs.error(TAG, "Initialisation failed: ${it.message}") }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        callbacks.kill()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------------
    // Dispatch
    // ---------------------------------------------------------------------------------------------

    private suspend fun dispatch(request: RpcRequest): RpcResponse = try {
        manager.initialize()
        RpcResponse.success(request.id, handle(request))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (t: Throwable) {
        val error = t.toExtensionError()
        manager.logs.error(TAG, "${request.method} failed: ${error.message}")
        RpcResponse.failure(request.id, error)
    }

    private suspend fun handle(request: RpcRequest): JsonElement = when (request.method) {
        RuntimeMethods.PING -> jsonOf(
            "protocolVersion" to RuntimeProtocol.PROTOCOL_VERSION,
            "runtimePackage" to RuntimeProtocol.RUNTIME_PACKAGE,
        )

        RuntimeMethods.RUNTIME_INFO -> json.encodeToJsonElement(RuntimeInfo.serializer(), manager.runtimeInfo())

        RuntimeMethods.RUNTIME_LOGS -> {
            val limit = request.int(RuntimeParams.LIMIT, 200)
            json.encodeToJsonElement(ListSerializer(LogEntry.serializer()), manager.logs.snapshot(limit))
        }

        RuntimeMethods.RUNTIME_CLEAR_LOGS -> {
            manager.logs.clear()
            JsonPrimitive(true)
        }

        RuntimeMethods.EXTENSIONS_LIST ->
            json.encodeToJsonElement(ListSerializer(Extension.serializer()), manager.installedExtensions())

        RuntimeMethods.EXTENSIONS_AVAILABLE ->
            json.encodeToJsonElement(ListSerializer(RepositoryExtension.serializer()), manager.refreshAvailable())

        RuntimeMethods.EXTENSIONS_INSTALL -> {
            val id = request.requireString(RuntimeParams.EXTENSION_ID)
            json.encodeToJsonElement(Extension.serializer(), manager.install(id))
        }

        RuntimeMethods.EXTENSIONS_INSTALL_FILE -> {
            val path = request.requireString(RuntimeParams.FILE_PATH)
            json.encodeToJsonElement(Extension.serializer(), manager.installFile(path))
        }

        RuntimeMethods.EXTENSIONS_UPDATE -> {
            val id = request.requireString(RuntimeParams.EXTENSION_ID)
            json.encodeToJsonElement(Extension.serializer(), manager.update(id))
        }

        RuntimeMethods.EXTENSIONS_UNINSTALL -> {
            val id = request.requireString(RuntimeParams.EXTENSION_ID)
            JsonPrimitive(manager.uninstall(id))
        }

        RuntimeMethods.EXTENSIONS_RELOAD ->
            json.encodeToJsonElement(ListSerializer(Extension.serializer()), manager.reload())

        RuntimeMethods.REPOSITORIES_LIST ->
            json.encodeToJsonElement(ListSerializer(ExtensionRepository.serializer()), manager.repositories.value)

        RuntimeMethods.REPOSITORIES_ADD -> {
            val url = request.requireString(RuntimeParams.URL)
            val backend = BackendType.fromWire(request.string(RuntimeParams.BACKEND))
            val name = request.string(RuntimeParams.NAME)
            json.encodeToJsonElement(
                ExtensionRepository.serializer(),
                manager.addRepository(url, backend, name),
            )
        }

        RuntimeMethods.REPOSITORIES_REMOVE -> {
            val id = request.requireString(RuntimeParams.REPOSITORY_ID)
            JsonPrimitive(manager.removeRepository(id))
        }

        RuntimeMethods.REPOSITORIES_REFRESH ->
            json.encodeToJsonElement(ListSerializer(RepositoryExtension.serializer()), manager.refreshAvailable())

        RuntimeMethods.SEARCH -> json.encodeToJsonElement(
            ListSerializer(Anime.serializer()),
            manager.search(
                extensionId = request.requireString(RuntimeParams.EXTENSION_ID),
                sourceId = request.string(RuntimeParams.SOURCE_ID),
                query = request.requireString(RuntimeParams.QUERY),
                page = request.int(RuntimeParams.PAGE, 1),
            ),
        )

        RuntimeMethods.POPULAR -> json.encodeToJsonElement(
            ListSerializer(Anime.serializer()),
            manager.popular(
                extensionId = request.requireString(RuntimeParams.EXTENSION_ID),
                sourceId = request.string(RuntimeParams.SOURCE_ID),
                page = request.int(RuntimeParams.PAGE, 1),
            ),
        )

        RuntimeMethods.LATEST -> json.encodeToJsonElement(
            ListSerializer(Anime.serializer()),
            manager.latest(
                extensionId = request.requireString(RuntimeParams.EXTENSION_ID),
                sourceId = request.string(RuntimeParams.SOURCE_ID),
                page = request.int(RuntimeParams.PAGE, 1),
            ),
        )

        RuntimeMethods.DETAILS -> json.encodeToJsonElement(
            AnimeDetails.serializer(),
            manager.details(
                extensionId = request.requireString(RuntimeParams.EXTENSION_ID),
                sourceId = request.string(RuntimeParams.SOURCE_ID),
                animeUrl = request.requireString(RuntimeParams.ANIME_ID),
            ),
        )

        RuntimeMethods.EPISODES -> json.encodeToJsonElement(
            ListSerializer(Episode.serializer()),
            manager.episodes(
                extensionId = request.requireString(RuntimeParams.EXTENSION_ID),
                sourceId = request.string(RuntimeParams.SOURCE_ID),
                animeUrl = request.requireString(RuntimeParams.ANIME_ID),
            ),
        )

        RuntimeMethods.STREAMS -> json.encodeToJsonElement(
            ListSerializer(StreamSource.serializer()),
            manager.streams(
                extensionId = request.requireString(RuntimeParams.EXTENSION_ID),
                sourceId = request.string(RuntimeParams.SOURCE_ID),
                episodeUrl = request.requireString(RuntimeParams.EPISODE_ID),
            ),
        )

        RuntimeMethods.MANGA_SEARCH -> json.encodeToJsonElement(
            ListSerializer(Manga.serializer()),
            manager.searchManga(
                extensionId = request.requireString(RuntimeParams.EXTENSION_ID),
                sourceId = request.string(RuntimeParams.SOURCE_ID),
                query = request.requireString(RuntimeParams.QUERY),
                page = request.int(RuntimeParams.PAGE, 1),
            ),
        )

        RuntimeMethods.MANGA_POPULAR -> json.encodeToJsonElement(
            ListSerializer(Manga.serializer()),
            manager.popularManga(
                extensionId = request.requireString(RuntimeParams.EXTENSION_ID),
                sourceId = request.string(RuntimeParams.SOURCE_ID),
                page = request.int(RuntimeParams.PAGE, 1),
            ),
        )

        RuntimeMethods.MANGA_LATEST -> json.encodeToJsonElement(
            ListSerializer(Manga.serializer()),
            manager.latestManga(
                extensionId = request.requireString(RuntimeParams.EXTENSION_ID),
                sourceId = request.string(RuntimeParams.SOURCE_ID),
                page = request.int(RuntimeParams.PAGE, 1),
            ),
        )

        RuntimeMethods.MANGA_DETAILS -> json.encodeToJsonElement(
            MangaDetails.serializer(),
            manager.mangaDetails(
                extensionId = request.requireString(RuntimeParams.EXTENSION_ID),
                sourceId = request.string(RuntimeParams.SOURCE_ID),
                mangaUrl = request.requireString(RuntimeParams.MANGA_ID),
            ),
        )

        RuntimeMethods.MANGA_CHAPTERS -> json.encodeToJsonElement(
            ListSerializer(Chapter.serializer()),
            manager.chapters(
                extensionId = request.requireString(RuntimeParams.EXTENSION_ID),
                sourceId = request.string(RuntimeParams.SOURCE_ID),
                mangaUrl = request.requireString(RuntimeParams.MANGA_ID),
            ),
        )

        RuntimeMethods.MANGA_PAGES -> json.encodeToJsonElement(
            ListSerializer(MangaPage.serializer()),
            manager.pages(
                extensionId = request.requireString(RuntimeParams.EXTENSION_ID),
                sourceId = request.string(RuntimeParams.SOURCE_ID),
                chapterUrl = request.requireString(RuntimeParams.CHAPTER_ID),
            ),
        )

        else -> throw IllegalArgumentException("Unknown method '${request.method}'").also {
            manager.logs.warn(TAG, "Unknown method '${request.method}'")
        }
    }

    private fun RpcRequest.requireString(key: String): String =
        string(key)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Missing required parameter '$key'")

    // ---------------------------------------------------------------------------------------------
    // Client authorisation
    // ---------------------------------------------------------------------------------------------

    /**
     * Same-signature callers are always accepted (the bundled apps in this repository are signed
     * together in debug builds); other packages must be listed in the runtime's allow-list.
     */
    private fun isCallerAllowed(): Boolean {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return true
        return try {
            val pm = packageManager
            if (pm.checkSignatures(callingUid, Process.myUid()) == PackageManager.SIGNATURE_MATCH) return true
            val packageName = pm.getNameForUid(callingUid) ?: return false
            manager.settings.isClientAllowed(packageName)
        } catch (_: Exception) {
            false
        }
    }

    private fun guardCaller() {
        if (isCallerAllowed()) return
        val caller = runCatching { packageManager.getNameForUid(Binder.getCallingUid()) }.getOrNull()
        throw SecurityException(
            "Package ${caller ?: "unknown"} is not allowed to use this runtime. " +
                "Allow it in the Extension Manager first.",
        )
    }

    private fun broadcast(event: RuntimeEvent) {
        val encoded = event.encode()
        val count = callbacks.beginBroadcast()
        try {
            for (index in 0 until count) {
                runCatching { callbacks.getBroadcastItem(index).onEvent(encoded) }
            }
        } finally {
            callbacks.finishBroadcast()
        }
    }

    private companion object {
        const val TAG = "RuntimeHostService"
    }
}
