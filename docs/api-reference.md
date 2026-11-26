# API reference

All calls suspend, run off the main thread and throw `ExtensionBridgeException` on failure.
`connect()` is the only call that returns a status instead of throwing.

## `KirokuRuntimeBridge`

```kotlin
class KirokuRuntimeBridge(
    context: Context,
    config: BridgeConfig = BridgeConfig()
) : ExtensionBridge, AutoCloseable
```

| Member | Signature | Notes |
| --- | --- | --- |
| `connect` | `suspend fun connect(): RuntimeStatus` | binds and verifies protocol |
| `disconnect` | `suspend fun disconnect()` | unbinds, cancels reconnect |
| `close` | `fun close()` | unbinds and cancels the SDK scope |
| `status` | `StateFlow<RuntimeStatus>` | `IDLE`, `NOT_INSTALLED`, `INCOMPATIBLE`, `CONNECTING`, `CONNECTED`, `DISCONNECTED`, `FAILED` |
| `events` | `SharedFlow<BridgeEvent>` | runtime pushes |
| `getRuntimeInfo` | `suspend fun getRuntimeInfo(): RuntimeInfo` | version, protocol, backends |

### Extensions

```kotlin
suspend fun getExtensions(): List<Extension>
suspend fun getAvailableExtensions(): List<RepositoryExtension>
suspend fun installExtension(id: String): Extension
suspend fun installExtensionFromFile(path: String): Extension
suspend fun updateExtension(id: String): Extension
suspend fun uninstallExtension(id: String): Boolean
suspend fun reloadExtensions(): List<Extension>
```

### Repositories

```kotlin
suspend fun getRepositories(): List<ExtensionRepository>
suspend fun addRepository(url: String, backend: BackendType, name: String? = null): ExtensionRepository
suspend fun removeRepository(repositoryId: String): Boolean
```

Refreshing the catalogue is `getAvailableExtensions()`.

### Anime sources

Anikku anime extensions (extensions-lib 12–16). `EXTENSION_UNSUPPORTED` when the source does not offer
the call.

```kotlin
suspend fun search(extensionId: String, query: String, sourceId: String? = null, page: Int = 1): List<Anime>
suspend fun popular(extensionId: String, sourceId: String? = null, page: Int = 1): List<Anime>
suspend fun latest(extensionId: String, sourceId: String? = null, page: Int = 1): List<Anime>
suspend fun getDetails(extensionId: String, animeId: String, sourceId: String? = null): AnimeDetails
suspend fun getEpisodes(extensionId: String, animeId: String, sourceId: String? = null): List<Episode>
suspend fun extractStreams(extensionId: String, episodeId: String, sourceId: String? = null): List<StreamSource>
```

### Manga sources

Mihon manga extensions (extensions-lib 1.4–1.6), identified by `extension.capabilities.manga`.
`EXTENSION_UNSUPPORTED` when the extension is an anime source or does not expose chapters/pages.

```kotlin
suspend fun searchManga(extensionId: String, query: String, sourceId: String? = null, page: Int = 1): List<Manga>
suspend fun popularManga(extensionId: String, sourceId: String? = null, page: Int = 1): List<Manga>
suspend fun latestManga(extensionId: String, sourceId: String? = null, page: Int = 1): List<Manga>
suspend fun getMangaDetails(extensionId: String, mangaId: String, sourceId: String? = null): MangaDetails
suspend fun getChapters(extensionId: String, mangaId: String, sourceId: String? = null): List<Chapter>
suspend fun getPages(extensionId: String, chapterId: String, sourceId: String? = null): List<MangaPage>
```

### Diagnostics

```kotlin
suspend fun getLogs(limit: Int = 200): List<LogEntry>
suspend fun clearLogs()
```

## Models (`bridge-ipc`)

```kotlin
enum class BackendType { ANIYOMI, CLOUDSTREAM, UNKNOWN }

enum class RuntimeState { IDLE, NOT_INSTALLED, INCOMPATIBLE, CONNECTING, CONNECTED, DISCONNECTED, FAILED }

data class RuntimeStatus(
    val state: RuntimeState,
    val info: RuntimeInfo? = null,
    val message: String? = null,
    val error: ExtensionError? = null,
) { val isConnected: Boolean }

data class RuntimeInfo(
    val protocolVersion: Int, val runtimeVersion: String, val runtimeVersionCode: Long,
    val androidSdk: Int, val capabilities: RuntimeCapabilities,
    val installedExtensions: Int, val backends: List<BackendInfo>,
)

data class Extension(
    val id: String, val metadata: ExtensionMetadata, val backend: BackendType,
    val repositoryId: String?, val isInstalled: Boolean, val isUpdateAvailable: Boolean,
    val capabilities: ExtensionCapabilities, val sources: List<ExtensionSourceInfo>,
    val loadError: ExtensionError?,
)

// capabilities.manga marks a Mihon manga extension; capabilities.chapters/pages follow from it.
data class ExtensionCapabilities(
    val search: Boolean, val popular: Boolean, val latest: Boolean, val details: Boolean,
    val episodes: Boolean, val streams: Boolean, val subtitles: Boolean, val audioTracks: Boolean,
    val filters: Boolean, val preferences: Boolean, val hosts: Boolean,
    val manga: Boolean, val chapters: Boolean, val pages: Boolean,
)

data class ExtensionMetadata(
    val name: String, val packageName: String, val versionName: String, val versionCode: Long,
    val description: String?, val language: String?, val isNsfw: Boolean,
    val iconUrl: String?, val libVersion: String?, val authors: List<String>, val sizeBytes: Long,
)

data class ExtensionSourceInfo(
    val id: String, val name: String, val language: String?, val baseUrl: String?,
    val supportsSearch: Boolean, val supportsPopular: Boolean, val supportsLatest: Boolean,
    val supportsDetails: Boolean, val supportsEpisodes: Boolean, val supportsStreams: Boolean,
)

data class ExtensionRepository(
    val id: String, val name: String, val url: String, val backend: BackendType,
    val enabled: Boolean, val isBuiltIn: Boolean, val lastRefreshedAt: Long?, val lastError: ExtensionError?,
)

data class Anime(
    val id: String, val title: String, val url: String?, val coverUrl: String?,
    val description: String?, val genres: List<String>, val author: String?, val artist: String?,
    val status: String?, val year: Int?, val extensionId: String?,
)

data class Manga(
    val id: String, val title: String, val url: String?, val coverUrl: String?,
    val description: String?, val genres: List<String>, val author: String?, val artist: String?,
    val status: String?, val extensionId: String?,
)

data class MangaDetails(val manga: Manga, val chapters: List<Chapter>, val extensionId: String?)

data class Chapter(
    val id: String, val mangaId: String?, val name: String, val url: String?,
    val chapterNumber: Double?, val scanlator: String?, val dateUpload: Long?,
)

data class MangaPage(
    val index: Int, val url: String, val imageUrl: String?, val headers: Map<String, String>,
)

data class AnimeDetails(val anime: Anime, val episodes: List<Episode>, val extensionId: String?)

data class Episode(
    val id: String, val animeId: String?, val name: String, val url: String?,
    val episodeNumber: Double?, val seasonNumber: Int?, val description: String?,
    val thumbnailUrl: String?, val scanlator: String?, val dateUpload: Long?, val filler: Boolean,
)

data class StreamSource(
    val url: String, val quality: String?, val qualityValue: Int?, val server: String?,
    val title: String?, val referer: String?, val userAgent: String?, val mimeType: String?,
    val isM3u8: Boolean, val isDash: Boolean, val headers: Map<String, String>,
    val subtitles: List<Subtitle>, val audioTracks: List<AudioTrack>, val episodeId: String?,
)

data class Subtitle(val url: String, val label: String?, val language: String?,
                    val mimeType: String?, val headers: Map<String, String>)

data class AudioTrack(val url: String?, val label: String?, val language: String?,
                      val isDefault: Boolean, val headers: Map<String, String>)

data class ExtensionError(val code: ErrorCode, val message: String, val details: String?,
                          val extensionId: String?, val backend: BackendType?, val retryable: Boolean)
```

## Error codes

| Code | Meaning | Suggested action |
| --- | --- | --- |
| `RUNTIME_NOT_INSTALLED` | package missing or not visible (`<queries>`) | offer install / fix manifest |
| `PROTOCOL_MISMATCH` / `RUNTIME_INCOMPATIBLE` | protocol version differs | require runtime update |
| `PERMISSION_DENIED` | caller not same-signature and not allow-listed | explain allow-list |
| `EXTENSION_NOT_FOUND` | unknown `extensionId` | refresh list |
| `EXTENSION_LOAD_FAILED` | class/plugin failed to load | show message, offer reinstall |
| `EXTENSION_UNSUPPORTED` | operation not offered by that source | hide the action |
| `EXTENSION_INVALID_PACKAGE` | APK/`.cs3` rejected by validation | show validation reason |
| `NETWORK` | extension HTTP failure | retry, check connection |
| `REPOSITORY_UNREACHABLE` / `REPOSITORY_INVALID` | index URL failed or unreadable | check URL |
| `TIMEOUT` | runtime did not answer in time | retry, check logs |
| `NOT_CONNECTED` | call made while unbound | call `connect()` |
| `STORAGE` | package could not be written | check free space |
| `CANCELLED` | caller cancelled | ignore |
| `BACKEND_FAILURE` | unexpected backend error | log details |

## `BridgeConfig`

| Field | Default | Purpose |
| --- | --- | --- |
| `bindTimeoutMs` | 5_000 | initial bind |
| `controlTimeoutMs` | 15_000 | control calls |
| `operationTimeoutMs` | 90_000 | extension work |
| `maxReconnectAttempts` | 5 | rebind policy |
| `autoReconnect` | true | rebind after process death |
