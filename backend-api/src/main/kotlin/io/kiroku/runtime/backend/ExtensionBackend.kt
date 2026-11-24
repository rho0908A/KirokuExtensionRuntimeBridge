package io.kiroku.runtime.backend

import io.kiroku.runtime.ipc.Anime
import io.kiroku.runtime.ipc.AnimeDetails
import io.kiroku.runtime.ipc.BackendInfo
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.Episode
import io.kiroku.runtime.ipc.Extension
import io.kiroku.runtime.ipc.ExtensionRepository
import io.kiroku.runtime.ipc.InstallationProgress
import io.kiroku.runtime.ipc.RepositoryExtension
import io.kiroku.runtime.ipc.StreamSource
import java.io.File
import kotlinx.coroutines.flow.StateFlow

/**
 * One loaded extension plus the ecosystem specific object graph behind it.
 *
 * Backends return their own subtype of this class; the runtime stores the handle so that later
 * calls (search, details, streams) do not have to re-load and re-initialise the extension.
 */
interface LoadedExtension {
    val extension: Extension

    /** Ecosystem specific object; only the owning backend may interpret it. */
    val handle: Any
}

/** Raised by backends for failures that should be surfaced to a host as [io.kiroku.runtime.ipc.ExtensionError]. */
class BackendException(
    message: String,
    val code: io.kiroku.runtime.ipc.ErrorCode = io.kiroku.runtime.ipc.ErrorCode.BACKEND_FAILURE,
    cause: Throwable? = null,
    val retryable: Boolean = false,
) : Exception(message, cause)

/**
 * Contract every extension ecosystem implements.
 *
 * The runtime never assumes one ecosystem's objects are compatible with another's: this interface is
 * the only thing the runtime host knows about, and each implementation owns loading, execution and
 * its own package format.
 */
interface ExtensionBackend {

    val type: BackendType

    /** Identity/version of this backend implementation, reported to hosts for compatibility checks. */
    val info: BackendInfo

    /** Installed extensions, kept up to date by the backend after install/uninstall/reload. */
    val installed: StateFlow<List<Extension>>

    /**
     * Directory holding the packages this backend installed.
     *
     * Exposed so the runtime can back up a package before an update (rollback) and mirror it to a
     * user-chosen directory without knowing the ecosystem's file naming rules.
     */
    val storageDirectory: File

    /** Initialises the backend (directories, HTTP client, persistence). Must be idempotent. */
    suspend fun initialize()

    /** Rescans the extension directory, loading every compatible package. */
    suspend fun reload(): List<Extension>

    /** Validates a package on disk without installing it. */
    suspend fun inspectPackage(file: File): PackageInspection

    /**
     * Installs (or replaces) an extension package that was already downloaded to [file].
     * [onProgress] receives download/install phases so the runtime UI and SDK can display progress.
     */
    suspend fun installPackage(
        file: File,
        repositoryId: String?,
        onProgress: (suspend (io.kiroku.runtime.ipc.InstallationProgress) -> Unit)? = null,
    ): Extension

    /** Removes an installed extension. Returns false when the extension is unknown. */
    suspend fun uninstall(extensionId: String): Boolean

    /** Fetches everything a repository advertises. */
    suspend fun availableExtensions(repository: ExtensionRepository): List<RepositoryExtension>

    /** Downloads [remote] from its repository and installs it. */
    suspend fun installFromRepository(
        remote: RepositoryExtension,
        onProgress: (suspend (InstallationProgress) -> Unit)? = null,
    ): Extension

    /** Lower bound of the ecosystem's extension-library version accepted by this backend. */
    fun isLibraryVersionSupported(libVersion: String?): Boolean

    suspend fun search(extensionId: String, sourceId: String?, query: String, page: Int): List<Anime>

    suspend fun popular(extensionId: String, sourceId: String?, page: Int): List<Anime>

    suspend fun latest(extensionId: String, sourceId: String?, page: Int): List<Anime>

    suspend fun details(extensionId: String, sourceId: String?, animeUrl: String): AnimeDetails

    suspend fun episodes(extensionId: String, sourceId: String?, animeUrl: String): List<Episode>

    suspend fun streams(extensionId: String, sourceId: String?, episodeUrl: String): List<StreamSource>
}

/** Result of validating an extension package before installation. */
data class PackageInspection(
    val valid: Boolean,
    val metadata: io.kiroku.runtime.ipc.ExtensionMetadata?,
    val error: io.kiroku.runtime.ipc.ExtensionError? = null,
    /** Source ids discovered without fully executing sources where the format allows it. */
    val sourceIds: List<String> = emptyList(),
)
