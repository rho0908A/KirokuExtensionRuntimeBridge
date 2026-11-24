package io.kiroku.runtime.bridge.api

import io.kiroku.runtime.ipc.Anime
import io.kiroku.runtime.ipc.AnimeDetails
import io.kiroku.runtime.ipc.Episode
import io.kiroku.runtime.ipc.Extension
import io.kiroku.runtime.ipc.ExtensionRepository
import io.kiroku.runtime.ipc.LogEntry
import io.kiroku.runtime.ipc.RepositoryExtension
import io.kiroku.runtime.ipc.RuntimeInfo
import io.kiroku.runtime.ipc.RuntimeStatus
import io.kiroku.runtime.ipc.StreamSource
import kotlinx.coroutines.flow.Flow

/**
 * Backend-agnostic entry point for host applications.
 *
 * Implementations are expected to be safe to share across the whole application (one instance per
 * process is enough) and to expose failures as exceptions instead of empty lists, so that a UI can
 * distinguish "the source found nothing" from "the source failed".
 *
 * The signatures below started from the project's initial API proposal and were extended only where
 * the real backends expose something the original set could not express (paging, source selection
 * inside a multi-source extension, repository browsing and update checks).
 */
interface ExtensionBridge {

    /** Current connection state, observable by UI. Emits on every state change. */
    val status: Flow<RuntimeStatus>

    /** Loading / execution / installation events pushed by the runtime. */
    val events: Flow<BridgeEvent>

    /** Latest known status without collecting. */
    fun currentStatus(): RuntimeStatus

    /** Installs-check + binds to the runtime and performs the protocol handshake. */
    suspend fun connect(): RuntimeStatus

    /** Unbinds and releases the runtime connection. Safe to call when not connected. */
    suspend fun disconnect()

    /** Describes the connected runtime. Throws when not connected. */
    suspend fun getRuntimeInfo(): RuntimeInfo

    suspend fun getExtensions(): List<Extension>

    suspend fun getRepositories(): List<ExtensionRepository>

    /** Extensions advertised by every enabled repository. */
    suspend fun getAvailableExtensions(): List<RepositoryExtension>

    suspend fun addRepository(url: String, backend: io.kiroku.runtime.ipc.BackendType, name: String? = null): ExtensionRepository

    suspend fun removeRepository(repositoryId: String): Boolean

    /** Installs by extension id, resolving the package through the extension's repository. */
    suspend fun installExtension(id: String): Extension

    /** Installs a package already present on the device (e.g. user-picked APK or .cs3 file). */
    suspend fun installExtensionFromFile(path: String): Extension

    suspend fun updateExtension(id: String): Extension

    suspend fun uninstallExtension(id: String): Boolean

    /** Forces the runtime to rescan installed packages. */
    suspend fun reloadExtensions(): List<Extension>

    suspend fun search(
        extensionId: String,
        query: String,
        sourceId: String? = null,
        page: Int = 1,
    ): List<Anime>

    suspend fun popular(extensionId: String, sourceId: String? = null, page: Int = 1): List<Anime>

    suspend fun latest(extensionId: String, sourceId: String? = null, page: Int = 1): List<Anime>

    suspend fun getDetails(
        extensionId: String,
        animeId: String,
        sourceId: String? = null,
    ): AnimeDetails

    suspend fun getEpisodes(
        extensionId: String,
        animeId: String,
        sourceId: String? = null,
    ): List<Episode>

    suspend fun extractStreams(
        extensionId: String,
        episodeId: String,
        sourceId: String? = null,
    ): List<StreamSource>

    suspend fun getLogs(limit: Int = 200): List<LogEntry>

    suspend fun clearLogs()
}

/** Asynchronous notifications emitted by the runtime. */
sealed interface BridgeEvent {
    val timestamp: Long

    data class StateChanged(val status: RuntimeStatus, override val timestamp: Long = System.currentTimeMillis()) : BridgeEvent

    data class ExtensionLoaded(val extensionId: String, val name: String, override val timestamp: Long = System.currentTimeMillis()) : BridgeEvent

    data class ExtensionFailed(val extensionId: String, val message: String, override val timestamp: Long = System.currentTimeMillis()) : BridgeEvent

    data class InstallProgress(
        val extensionId: String,
        val phase: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        override val timestamp: Long = System.currentTimeMillis(),
    ) : BridgeEvent

    data class Installed(val extensionId: String, override val timestamp: Long = System.currentTimeMillis()) : BridgeEvent

    data class Uninstalled(val extensionId: String, override val timestamp: Long = System.currentTimeMillis()) : BridgeEvent

    data class Log(val entry: LogEntry, override val timestamp: Long = System.currentTimeMillis()) : BridgeEvent

    data class RuntimeError(val message: String, val code: String?, override val timestamp: Long = System.currentTimeMillis()) : BridgeEvent
}
