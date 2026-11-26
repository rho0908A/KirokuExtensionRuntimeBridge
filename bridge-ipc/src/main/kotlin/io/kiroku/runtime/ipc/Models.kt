package io.kiroku.runtime.ipc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Backend ecosystems the runtime can execute.
 *
 * The two ecosystems are deliberately modelled as distinct values instead of a generic "plugin"
 * type: Aniyomi extensions and CloudStream plugins have different packaging, APIs and lifetimes,
 * and host applications need to present them differently.
 */
@Serializable
enum class BackendType {
    @SerialName("aniyomi")
    ANIYOMI,

    @SerialName("cloudstream")
    CLOUDSTREAM,

    @SerialName("unknown")
    UNKNOWN;

    companion object {
        fun fromWire(value: String?): BackendType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
    }
}

/** High level state of the runtime connection as observed by a host application. */
@Serializable
enum class RuntimeState {
    /** No attempt has been made yet. */
    IDLE,

    /** The runtime package is not installed on the device. */
    NOT_INSTALLED,

    /** The runtime is installed but its protocol/version is not compatible with this SDK. */
    INCOMPATIBLE,

    /** A bind to the runtime service is in flight. */
    CONNECTING,

    /** Bound and protocol compatible. */
    CONNECTED,

    /** The runtime process died or the service was unbound. */
    DISCONNECTED,

    /** The runtime reported a fatal error. */
    FAILED,
}

/** Global capability flags reported by the runtime. */
@Serializable
data class RuntimeCapabilities(
    val backends: List<BackendType> = emptyList(),
    val repositoryManagement: Boolean = false,
    val extensionInstallation: Boolean = false,
    val extensionUpdates: Boolean = false,
    val streamExtraction: Boolean = false,
    val subtitleExtraction: Boolean = false,
    val audioTrackExtraction: Boolean = false,
    val runtimeLogs: Boolean = false,
    /** True when at least one backend can execute manga sources. */
    val mangaExtraction: Boolean = false,
) {
    fun supports(backend: BackendType): Boolean = backend in backends
}

/** Identity and compatibility information for the installed Runtime Host APK. */
@Serializable
data class RuntimeInfo(
    val protocolVersion: Int,
    val runtimeVersion: String,
    val runtimeVersionCode: Long,
    val androidSdk: Int,
    val capabilities: RuntimeCapabilities = RuntimeCapabilities(),
    val installedExtensions: Int = 0,
    val backends: List<BackendInfo> = emptyList(),
)

/** Per-backend identity, version and compatibility window. */
@Serializable
data class BackendInfo(
    val type: BackendType,
    val implementationVersion: String,
    val apiVersion: String? = null,
    val supportedExtensionKinds: List<String> = emptyList(),
    val notes: String? = null,
)

/** Outcome of {@code connect()}. */
@Serializable
data class RuntimeStatus(
    val state: RuntimeState,
    val info: RuntimeInfo? = null,
    val message: String? = null,
    val error: ExtensionError? = null,
) {
    val isConnected: Boolean get() = state == RuntimeState.CONNECTED
}

/** Optional extension capabilities, derived from the concrete extension object at load time. */
@Serializable
data class ExtensionCapabilities(
    val search: Boolean = false,
    val popular: Boolean = false,
    val latest: Boolean = false,
    val details: Boolean = false,
    val episodes: Boolean = false,
    val streams: Boolean = false,
    val subtitles: Boolean = false,
    val audioTracks: Boolean = false,
    val filters: Boolean = false,
    val preferences: Boolean = false,
    val hosts: Boolean = false,
    /** True when the extension executes a manga ecosystem (Mihon APK). */
    val manga: Boolean = false,
    /** True when the source exposes chapter lists. */
    val chapters: Boolean = false,
    /** True when the source exposes page images. */
    val pages: Boolean = false,
)

/** Metadata that is read from the extension package without executing extension code. */
@Serializable
data class ExtensionMetadata(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val description: String? = null,
    val language: String? = null,
    val isNsfw: Boolean = false,
    val iconUrl: String? = null,
    /** Extension-library version the package was built against (ecosystem specific). */
    val libVersion: String? = null,
    val authors: List<String> = emptyList(),
    val sizeBytes: Long = 0L,
)

/** A single source inside an extension. Aniyomi calls these sources, CloudStream calls them providers. */
@Serializable
data class ExtensionSourceInfo(
    val id: String,
    val name: String,
    val language: String? = null,
    val baseUrl: String? = null,
    val supportsSearch: Boolean = true,
    val supportsPopular: Boolean = true,
    val supportsLatest: Boolean = false,
    val supportsDetails: Boolean = true,
    val supportsEpisodes: Boolean = true,
    val supportsStreams: Boolean = true,
)

/** An extension known to the runtime, whether installed or only available from a repository. */
@Serializable
data class Extension(
    val id: String,
    val metadata: ExtensionMetadata,
    val backend: BackendType,
    val repositoryId: String? = null,
    val isInstalled: Boolean = false,
    val isUpdateAvailable: Boolean = false,
    val capabilities: ExtensionCapabilities = ExtensionCapabilities(),
    val sources: List<ExtensionSourceInfo> = emptyList(),
    val loadError: ExtensionError? = null,
)

/** A repository that publishes an extension index for one backend. */
@Serializable
data class ExtensionRepository(
    val id: String,
    val name: String,
    val url: String,
    val backend: BackendType,
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val lastRefreshedAt: Long? = null,
    val lastError: ExtensionError? = null,
)

/** A (possibly not installed) extension advertised by a repository. */
@Serializable
data class RepositoryExtension(
    val repositoryId: String,
    val backend: BackendType,
    val id: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val language: String? = null,
    val apkUrl: String,
    val iconUrl: String? = null,
    val libVersion: String? = null,
    val isNsfw: Boolean = false,
) {
    fun toExtension(installed: Extension?): Extension = Extension(
        id = id,
        metadata = ExtensionMetadata(
            name = name,
            packageName = id,
            versionName = versionName,
            versionCode = versionCode,
            language = language,
            isNsfw = isNsfw,
            iconUrl = iconUrl,
            libVersion = libVersion,
        ),
        backend = backend,
        repositoryId = repositoryId,
        isInstalled = installed != null,
        isUpdateAvailable = installed != null && installed.metadata.versionCode < versionCode,
        capabilities = installed?.capabilities ?: ExtensionCapabilities(),
        sources = installed?.sources.orEmpty(),
    )
}

/** Search / browse result entry. */
@Serializable
data class Anime(
    val id: String,
    val title: String,
    val url: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    val year: Int? = null,
    val extensionId: String? = null,
)

@Serializable
data class AnimeDetails(
    val anime: Anime,
    val episodes: List<Episode> = emptyList(),
    val extensionId: String? = null,
)

@Serializable
data class Episode(
    val id: String,
    val animeId: String? = null,
    val name: String,
    val url: String? = null,
    val episodeNumber: Double? = null,
    val seasonNumber: Int? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val scanlator: String? = null,
    val dateUpload: Long? = null,
    val filler: Boolean = false,
)

@Serializable
data class Subtitle(
    val url: String,
    val label: String? = null,
    val language: String? = null,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

/** Audio track exposed by an extension. Never synthesised by the bridge. */
@Serializable
data class AudioTrack(
    val url: String? = null,
    val label: String? = null,
    val language: String? = null,
    val isDefault: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * A playable stream. The host application owns playback: this object only carries what the
 * extension actually exposed (URL, quality, headers, tracks, subtitles).
 */
@Serializable
data class StreamSource(
    val url: String,
    val quality: String? = null,
    val qualityValue: Int? = null,
    val server: String? = null,
    val title: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val mimeType: String? = null,
    val isM3u8: Boolean = false,
    val isDash: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<Subtitle> = emptyList(),
    val audioTracks: List<AudioTrack> = emptyList(),
    val episodeId: String? = null,
)

/** Video entry kept for API symmetry with Aniyomi's model; streams are exposed as [StreamSource]. */
@Serializable
data class Video(
    val id: String,
    val title: String,
    val stream: StreamSource,
    val thumbnailUrl: String? = null,
)

@Serializable
enum class ErrorCode {
    RUNTIME_NOT_INSTALLED,
    RUNTIME_INCOMPATIBLE,
    PROTOCOL_MISMATCH,
    PERMISSION_DENIED,
    TIMEOUT,
    NOT_CONNECTED,
    INVALID_REQUEST,
    UNKNOWN_METHOD,
    EXTENSION_NOT_FOUND,
    EXTENSION_ALREADY_INSTALLED,
    EXTENSION_LOAD_FAILED,
    EXTENSION_UNSUPPORTED,
    EXTENSION_INVALID_PACKAGE,
    SOURCE_NOT_FOUND,
    REPOSITORY_INVALID,
    REPOSITORY_UNREACHABLE,
    NETWORK,
    BACKEND_FAILURE,
    STORAGE,
    CANCELLED,
    UNKNOWN,
}

/** Structured error returned by the runtime; never a silent empty result. */
@Serializable
data class ExtensionError(
    val code: ErrorCode,
    val message: String,
    val details: String? = null,
    val extensionId: String? = null,
    val backend: BackendType? = null,
    val retryable: Boolean = false,
)

/** A single runtime log line, exposed to both the host UI and SDK clients. */
@Serializable
data class LogEntry(
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String,
)

/** Installation / update progress for long running operations. */
@Serializable
data class InstallationProgress(
    val extensionId: String,
    val backend: BackendType,
    val phase: String,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null,
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
}
