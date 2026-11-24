package io.kiroku.runtime.bridge.client

import android.content.Context
import android.os.DeadObjectException
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import io.kiroku.runtime.bridge.BridgeConfig
import io.kiroku.runtime.bridge.api.BridgeEvent
import io.kiroku.runtime.bridge.api.ExtensionBridge
import io.kiroku.runtime.bridge.connection.RuntimeAvailability
import io.kiroku.runtime.bridge.connection.RuntimeConnection
import io.kiroku.runtime.bridge.errors.ExtensionBridgeException
import io.kiroku.runtime.ipc.Anime
import io.kiroku.runtime.ipc.AnimeDetails
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.Episode
import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.Extension
import io.kiroku.runtime.ipc.ExtensionError
import io.kiroku.runtime.ipc.ExtensionRepository
import io.kiroku.runtime.ipc.IRuntimeCallback
import io.kiroku.runtime.ipc.IRuntimeHost
import io.kiroku.runtime.ipc.LogEntry
import io.kiroku.runtime.ipc.RepositoryExtension
import io.kiroku.runtime.ipc.RpcRequest
import io.kiroku.runtime.ipc.RpcResponse
import io.kiroku.runtime.ipc.RuntimeEvent
import io.kiroku.runtime.ipc.RuntimeEventType
import io.kiroku.runtime.ipc.RuntimeInfo
import io.kiroku.runtime.ipc.RuntimeMethods
import io.kiroku.runtime.ipc.RuntimeParams
import io.kiroku.runtime.ipc.RuntimeProtocol
import io.kiroku.runtime.ipc.RuntimeState
import io.kiroku.runtime.ipc.RuntimeStatus
import io.kiroku.runtime.ipc.StreamSource
import io.kiroku.runtime.ipc.jsonOf
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Default [ExtensionBridge] implementation.
 *
 * Design notes:
 *  - One Binder request is one JSON envelope; all model decoding happens here, so the runtime can
 *    evolve its payloads without breaking older clients (unknown fields are ignored).
 *  - Every failure becomes an [ExtensionBridgeException]; no operation silently returns empty data.
 *  - A dead runtime process invalidates the binder; the bridge rebinds with exponential backoff and
 *    replays the request once, which is the behaviour host applications need when the user updates
 *    or force-stops the runtime.
 */
class KirokuRuntimeBridge(
    context: Context,
    private val config: BridgeConfig = BridgeConfig(),
) : ExtensionBridge, AutoCloseable {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionMutex = Mutex()

    private val _status = MutableStateFlow(RuntimeStatus(state = RuntimeState.IDLE))
    private val _events = MutableSharedFlow<BridgeEvent>(replay = 0, extraBufferCapacity = 64)

    private var runtimeInfo: RuntimeInfo? = null

    private var reconnectJob: Job? = null

    private val connection = RuntimeConnection(
        context = appContext,
        onServiceConnected = { /* binder stored internally; handshake happens in connect() */ },
        onServiceDisconnected = {
            runtimeInfo = null
            publishStatus(RuntimeStatus(state = RuntimeState.DISCONNECTED, message = "Runtime disconnected"))
            scheduleReconnect()
        },
    )

    private val deathRecipient = IBinder.DeathRecipient {
        runtimeInfo = null
        publishStatus(RuntimeStatus(state = RuntimeState.DISCONNECTED, message = "Runtime process died"))
        scheduleReconnect()
    }

    private val callback = object : IRuntimeCallback.Stub() {
        override fun onEvent(eventJson: String) {
            runCatching { RuntimeEvent.decode(eventJson) }
                .onSuccess { emitEvent(it) }
                .onFailure { Log.w(config.logTag, "Dropped malformed runtime event", it) }
        }
    }

    override val status: StateFlow<RuntimeStatus> = _status.asStateFlow()
    override val events: SharedFlow<BridgeEvent> = _events.asSharedFlow()

    override fun currentStatus(): RuntimeStatus = _status.value

    override suspend fun connect(): RuntimeStatus = connectionMutex.withLock {
        if (_status.value.state == RuntimeState.CONNECTED && connection.isBound) {
            return@withLock _status.value
        }

        when (val availability = RuntimeAvailability.check(appContext)) {
            is RuntimeAvailability.NotInstalled -> {
                val status = RuntimeStatus(
                    state = RuntimeState.NOT_INSTALLED,
                    message = "Install the Kiroku extension runtime to use extensions.",
                    error = ExtensionBridgeException.runtimeMissing().toError(),
                )
                publishStatus(status)
                return@withLock status
            }

            is RuntimeAvailability.Unknown -> {
                val status = RuntimeStatus(
                    state = RuntimeState.FAILED,
                    message = "Runtime package could not be queried: ${availability.reason}",
                    error = io.kiroku.runtime.ipc.ExtensionError(
                        code = ErrorCode.PERMISSION_DENIED,
                        message = availability.reason,
                        details = "Declare <queries><package android:name=\"${RuntimeProtocol.RUNTIME_PACKAGE}\"/></queries>",
                    ),
                )
                publishStatus(status)
                return@withLock status
            }

            is RuntimeAvailability.Installed -> Unit
        }

        publishStatus(RuntimeStatus(state = RuntimeState.CONNECTING))

        val host = try {
            connection.bind(config.bindTimeoutMs)
        } catch (e: SecurityException) {
            val status = RuntimeStatus(
                state = RuntimeState.FAILED,
                message = "The runtime refused this application: ${e.message}",
                error = io.kiroku.runtime.ipc.ExtensionError(
                    code = ErrorCode.PERMISSION_DENIED,
                    message = e.message ?: "Binding denied",
                    details = "The runtime only accepts clients allowed by its host allow-list.",
                ),
            )
            publishStatus(status)
            return@withLock status
        }

        if (host == null) {
            val status = RuntimeStatus(
                state = RuntimeState.DISCONNECTED,
                message = "Timed out while binding to the runtime service.",
                error = ExtensionBridgeException(
                    code = ErrorCode.TIMEOUT,
                    message = "bindService did not connect within ${config.bindTimeoutMs}ms",
                    retryable = true,
                ).toError(),
            )
            publishStatus(status)
            return@withLock status
        }

        runCatching { host.asBinder().linkToDeath(deathRecipient, 0) }

        val info = try {
            val raw = withTimeout(config.controlTimeoutMs) {
                withContext(Dispatchers.IO) { host.protocolDescriptor }
            }
            RuntimeProtocol.JSON.decodeFromString(RuntimeInfo.serializer(), raw)
        } catch (e: Exception) {
            val status = RuntimeStatus(
                state = RuntimeState.FAILED,
                message = "Runtime handshake failed: ${e.message}",
                error = ExtensionError(
                    code = ErrorCode.PROTOCOL_MISMATCH,
                    message = e.message ?: "Handshake failed",
                    retryable = true,
                ),
            )
            publishStatus(status)
            return@withLock status
        }

        if (!RuntimeAvailability.isProtocolCompatible(info.protocolVersion)) {
            val status = RuntimeStatus(
                state = RuntimeState.INCOMPATIBLE,
                info = info,
                message = "Runtime protocol ${info.protocolVersion} is not compatible with SDK protocol ${RuntimeProtocol.PROTOCOL_VERSION}.",
                error = ExtensionError(
                    code = ErrorCode.PROTOCOL_MISMATCH,
                    message = "Protocol mismatch",
                    details = "sdk=${RuntimeProtocol.PROTOCOL_VERSION} runtime=${info.protocolVersion}",
                ),
            )
            publishStatus(status)
            return@withLock status
        }

        runtimeInfo = info
        runCatching { host.registerCallback(callback) }

        val status = RuntimeStatus(state = RuntimeState.CONNECTED, info = info)
        publishStatus(status)
        status
    }

    override suspend fun disconnect() {
        connectionMutex.withLock {
            reconnectJob?.cancel()
            reconnectJob = null
            runCatching { connection.current?.unregisterCallback(callback) }
            runCatching { connection.current?.asBinder()?.unlinkToDeath(deathRecipient, 0) }
            connection.unbind()
            runtimeInfo = null
            publishStatus(RuntimeStatus(state = RuntimeState.IDLE))
        }
    }

    override suspend fun getRuntimeInfo(): RuntimeInfo = runtimeInfo ?: run {
        connect()
        runtimeInfo ?: throw ExtensionBridgeException.notConnected()
    }

    override suspend fun getExtensions(): List<Extension> =
        callForList(RuntimeMethods.EXTENSIONS_LIST, jsonOf(), Extension.serializer())

    override suspend fun getRepositories(): List<ExtensionRepository> =
        callForList(RuntimeMethods.REPOSITORIES_LIST, jsonOf(), ExtensionRepository.serializer())

    override suspend fun getAvailableExtensions(): List<RepositoryExtension> =
        callForList(RuntimeMethods.EXTENSIONS_AVAILABLE, jsonOf(), RepositoryExtension.serializer())

    override suspend fun addRepository(url: String, backend: BackendType, name: String?): ExtensionRepository =
        decode(
            call(
                RuntimeMethods.REPOSITORIES_ADD,
                jsonOf(RuntimeParams.URL to url, RuntimeParams.BACKEND to backend.name, RuntimeParams.NAME to name),
            ),
            ExtensionRepository.serializer(),
        )

    override suspend fun removeRepository(repositoryId: String): Boolean =
        decode(
            call(RuntimeMethods.REPOSITORIES_REMOVE, jsonOf(RuntimeParams.REPOSITORY_ID to repositoryId)),
            Boolean.serializer(),
        )

    override suspend fun installExtension(id: String): Extension =
        decode(call(RuntimeMethods.EXTENSIONS_INSTALL, jsonOf(RuntimeParams.EXTENSION_ID to id)), Extension.serializer())

    override suspend fun installExtensionFromFile(path: String): Extension =
        decode(call(RuntimeMethods.EXTENSIONS_INSTALL_FILE, jsonOf(RuntimeParams.FILE_PATH to path)), Extension.serializer())

    override suspend fun updateExtension(id: String): Extension =
        decode(call(RuntimeMethods.EXTENSIONS_UPDATE, jsonOf(RuntimeParams.EXTENSION_ID to id)), Extension.serializer())

    override suspend fun uninstallExtension(id: String): Boolean =
        decode(call(RuntimeMethods.EXTENSIONS_UNINSTALL, jsonOf(RuntimeParams.EXTENSION_ID to id)), Boolean.serializer())

    override suspend fun reloadExtensions(): List<Extension> =
        callForList(RuntimeMethods.EXTENSIONS_RELOAD, jsonOf(), Extension.serializer())

    override suspend fun search(extensionId: String, query: String, sourceId: String?, page: Int): List<Anime> =
        callForList(
            RuntimeMethods.SEARCH,
            jsonOf(
                RuntimeParams.EXTENSION_ID to extensionId,
                RuntimeParams.SOURCE_ID to sourceId,
                RuntimeParams.QUERY to query,
                RuntimeParams.PAGE to page,
            ),
            Anime.serializer(),
        )

    override suspend fun popular(extensionId: String, sourceId: String?, page: Int): List<Anime> =
        callForList(
            RuntimeMethods.POPULAR,
            jsonOf(
                RuntimeParams.EXTENSION_ID to extensionId,
                RuntimeParams.SOURCE_ID to sourceId,
                RuntimeParams.PAGE to page,
            ),
            Anime.serializer(),
        )

    override suspend fun latest(extensionId: String, sourceId: String?, page: Int): List<Anime> =
        callForList(
            RuntimeMethods.LATEST,
            jsonOf(
                RuntimeParams.EXTENSION_ID to extensionId,
                RuntimeParams.SOURCE_ID to sourceId,
                RuntimeParams.PAGE to page,
            ),
            Anime.serializer(),
        )

    override suspend fun getDetails(extensionId: String, animeId: String, sourceId: String?): AnimeDetails =
        decode(
            call(
                RuntimeMethods.DETAILS,
                jsonOf(
                    RuntimeParams.EXTENSION_ID to extensionId,
                    RuntimeParams.SOURCE_ID to sourceId,
                    RuntimeParams.ANIME_ID to animeId,
                ),
            ),
            AnimeDetails.serializer(),
        )

    override suspend fun getEpisodes(extensionId: String, animeId: String, sourceId: String?): List<Episode> =
        callForList(
            RuntimeMethods.EPISODES,
            jsonOf(
                RuntimeParams.EXTENSION_ID to extensionId,
                RuntimeParams.SOURCE_ID to sourceId,
                RuntimeParams.ANIME_ID to animeId,
            ),
            Episode.serializer(),
        )

    override suspend fun extractStreams(extensionId: String, episodeId: String, sourceId: String?): List<StreamSource> =
        callForList(
            RuntimeMethods.STREAMS,
            jsonOf(
                RuntimeParams.EXTENSION_ID to extensionId,
                RuntimeParams.SOURCE_ID to sourceId,
                RuntimeParams.EPISODE_ID to episodeId,
            ),
            StreamSource.serializer(),
        )

    override suspend fun getLogs(limit: Int): List<LogEntry> =
        callForList(RuntimeMethods.RUNTIME_LOGS, jsonOf(RuntimeParams.LIMIT to limit), LogEntry.serializer())

    override suspend fun clearLogs() {
        execute(RuntimeMethods.RUNTIME_CLEAR_LOGS, jsonOf(), config.controlTimeoutMs)
    }

    override fun close() {
        scope.launch { disconnect() }
        scope.cancel()
    }

    // ---------------------------------------------------------------------------------------------
    // Transport
    // ---------------------------------------------------------------------------------------------

    private suspend fun <T> callForList(
        method: String,
        params: kotlinx.serialization.json.JsonObject,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T> {
        val element = execute(method, params, config.operationTimeoutMs)
        return RuntimeProtocol.JSON.decodeFromJsonElement(ListSerializer(serializer), element)
    }

    private fun <T> decode(element: JsonElement, serializer: kotlinx.serialization.KSerializer<T>): T =
        RuntimeProtocol.JSON.decodeFromJsonElement(serializer, element)

    private suspend fun execute(
        method: String,
        params: kotlinx.serialization.json.JsonObject,
        timeoutMs: Long,
    ): JsonElement {
        ensureConnected()
        return try {
            dispatch(method, params, timeoutMs)
        } catch (e: DeadObjectException) {
            retryAfterReconnect(method, params, timeoutMs, e)
        } catch (e: RemoteException) {
            if (e is DeadObjectException) retryAfterReconnect(method, params, timeoutMs, e) else throw mapRemote(e)
        }
    }

    private suspend fun dispatch(
        method: String,
        params: kotlinx.serialization.json.JsonObject,
        timeoutMs: Long,
    ): JsonElement {
        val host = connection.current ?: throw ExtensionBridgeException.notConnected()
        val request = RpcRequest(id = UUID.randomUUID().toString(), method = method, params = params)
        val raw = try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.IO) { host.execute(request.encode()) }
            }
        } catch (e: TimeoutCancellationException) {
            throw ExtensionBridgeException.timedOut(method, timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            throw ExtensionBridgeException(
                code = ErrorCode.PERMISSION_DENIED,
                message = e.message ?: "The runtime denied this request.",
            )
        } catch (e: DeadObjectException) {
            throw e
        } catch (e: RemoteException) {
            throw mapRemote(e)
        }

        val response = try {
            RpcResponse.decode(raw)
        } catch (e: Exception) {
            throw ExtensionBridgeException(
                code = ErrorCode.PROTOCOL_MISMATCH,
                message = "Runtime returned an unreadable response for '$method'.",
                details = e.message,
            )
        }

        if (!response.ok) {
            throw ExtensionBridgeException.from(
                response.error ?: io.kiroku.runtime.ipc.ExtensionError(
                    code = ErrorCode.UNKNOWN,
                    message = "Runtime reported failure without an error payload.",
                ),
            )
        }
        return response.result ?: JsonNull
    }

    private suspend fun retryAfterReconnect(
        method: String,
        params: kotlinx.serialization.json.JsonObject,
        timeoutMs: Long,
        cause: Throwable,
    ): JsonElement {
        if (!config.autoReconnect) {
            throw ExtensionBridgeException(
                code = ErrorCode.NOT_CONNECTED,
                message = "Runtime process died and auto-reconnect is disabled.",
                retryable = true,
                cause = cause,
            )
        }
        val rebound = connection.rebind(config.bindTimeoutMs)
            ?: throw ExtensionBridgeException(
                code = ErrorCode.NOT_CONNECTED,
                message = "Runtime process died and could not be rebound.",
                retryable = true,
                cause = cause,
            )
        runCatching {
            rebound.asBinder().linkToDeath(deathRecipient, 0)
            rebound.registerCallback(callback)
        }
        publishStatus(RuntimeStatus(state = RuntimeState.CONNECTED, info = runtimeInfo))
        return dispatch(method, params, timeoutMs)
    }

    private suspend fun ensureConnected() {
        if (_status.value.state == RuntimeState.CONNECTED && connection.isBound) return
        val status = connect()
        if (!status.isConnected) {
            throw status.error?.let { ExtensionBridgeException.from(it) }
                ?: ExtensionBridgeException.notConnected()
        }
    }

    private fun mapRemote(e: RemoteException): ExtensionBridgeException = ExtensionBridgeException(
        code = ErrorCode.BACKEND_FAILURE,
        message = "Binder transaction failed: ${e.message}",
        retryable = true,
        cause = e,
    )

    private fun scheduleReconnect() {
        if (!config.autoReconnect || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var delayMs = config.reconnectInitialDelayMs
            repeat(config.maxReconnectAttempts) { attempt ->
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(config.reconnectMaxDelayMs)
                val bound = connection.bind(config.bindTimeoutMs)
                if (bound != null) {
                    runCatching {
                        bound.asBinder().linkToDeath(deathRecipient, 0)
                        bound.registerCallback(callback)
                    }
                    val raw = runCatching { bound.protocolDescriptor }.getOrNull()
                    val info = raw?.let {
                        runCatching { RuntimeProtocol.JSON.decodeFromString(RuntimeInfo.serializer(), it) }.getOrNull()
                    }
                    runtimeInfo = info
                    publishStatus(RuntimeStatus(state = RuntimeState.CONNECTED, info = info))
                    return@launch
                }
                Log.d(config.logTag, "Reconnect attempt ${attempt + 1} failed")
            }
            publishStatus(
                RuntimeStatus(
                    state = RuntimeState.DISCONNECTED,
                    message = "Runtime is unavailable after ${config.maxReconnectAttempts} reconnect attempts.",
                ),
            )
        }
    }

    private fun publishStatus(newStatus: RuntimeStatus) {
        _status.value = newStatus
        _events.tryEmit(BridgeEvent.StateChanged(newStatus))
    }

    private fun emitEvent(event: RuntimeEvent) {
        val mapped: BridgeEvent = when (event.type) {
            RuntimeEventType.LOG -> runCatching {
                BridgeEvent.Log(RuntimeProtocol.JSON.decodeFromJsonElement(LogEntry.serializer(), event.payload))
            }.getOrDefault(BridgeEvent.RuntimeError("Malformed log event", "PROTOCOL"))
            RuntimeEventType.INSTALL_PROGRESS -> BridgeEvent.InstallProgress(
                extensionId = event.payload[RuntimeParams.EXTENSION_ID]?.toString()?.trim('"').orEmpty(),
                phase = event.payload["phase"]?.toString()?.trim('"').orEmpty(),
                bytesDownloaded = event.payload["bytesDownloaded"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                totalBytes = event.payload["totalBytes"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
            RuntimeEventType.EXTENSION_LOADED -> BridgeEvent.ExtensionLoaded(
                extensionId = event.payload[RuntimeParams.EXTENSION_ID]?.toString()?.trim('"').orEmpty(),
                name = event.payload[RuntimeParams.NAME]?.toString()?.trim('"').orEmpty(),
            )
            RuntimeEventType.EXTENSION_FAILED -> BridgeEvent.ExtensionFailed(
                extensionId = event.payload[RuntimeParams.EXTENSION_ID]?.toString()?.trim('"').orEmpty(),
                message = event.payload["message"]?.toString()?.trim('"').orEmpty(),
            )
            RuntimeEventType.INSTALL_COMPLETED, RuntimeEventType.UPDATE_COMPLETED -> BridgeEvent.Installed(
                extensionId = event.payload[RuntimeParams.EXTENSION_ID]?.toString()?.trim('"').orEmpty(),
            )
            RuntimeEventType.UNINSTALLED -> BridgeEvent.Uninstalled(
                extensionId = event.payload[RuntimeParams.EXTENSION_ID]?.toString()?.trim('"').orEmpty(),
            )
            RuntimeEventType.ERROR -> BridgeEvent.RuntimeError(
                message = event.payload["message"]?.toString()?.trim('"').orEmpty(),
                code = event.payload["code"]?.toString()?.trim('"'),
            )
            else -> BridgeEvent.RuntimeError("Unhandled runtime event '${event.type}'", null)
        }
        _events.tryEmit(mapped)
    }

    /** Used by the sample host and tests to await a terminal install event. */
    internal fun awaitEvent(predicate: (BridgeEvent) -> Boolean): CompletableDeferred<BridgeEvent> {
        val deferred = CompletableDeferred<BridgeEvent>()
        scope.launch {
            events.collect { if (predicate(it)) deferred.complete(it) }
        }
        return deferred
    }
}
