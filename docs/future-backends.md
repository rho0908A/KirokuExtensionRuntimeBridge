# Adding a backend

A new ecosystem plugs in without touching the IPC contract or the UI.

## 1. Implement `ExtensionBackend`

```kotlin
class MyBackend(context: Context) : ExtensionBackend {

    override val type = BackendType.UNKNOWN          // add an enum case in bridge-ipc
    override val info = BackendInfo(type = type, implementationVersion = "1.0.0")
    override val installed: StateFlow<List<Extension>> = _installed
    override val storageDirectory = File(context.filesDir, "extensions/custom")

    override suspend fun initialize() { /* scan storageDirectory */ }
    override suspend fun reload(): List<Extension> { /* rescan and load */ }
    override suspend fun inspectPackage(file: File): PackageInspection { /* validate without loading */ }

    override suspend fun installPackage(
        file: File,
        repositoryId: String?,
        onProgress: (suspend (InstallationProgress) -> Unit)?,
    ): Extension

    override suspend fun installFromRepository(
        remote: RepositoryExtension,
        onProgress: (suspend (InstallationProgress) -> Unit)?,
    ): Extension

    override suspend fun uninstall(extensionId: String): Boolean
    override suspend fun availableExtensions(repository: ExtensionRepository): List<RepositoryExtension>
    override fun isLibraryVersionSupported(libVersion: String?): Boolean

    override suspend fun search(extensionId: String, sourceId: String?, query: String, page: Int): List<Anime>
    override suspend fun popular(extensionId: String, sourceId: String?, page: Int): List<Anime>
    override suspend fun latest(extensionId: String, sourceId: String?, page: Int): List<Anime>
    override suspend fun details(extensionId: String, sourceId: String?, animeUrl: String): AnimeDetails
    override suspend fun episodes(extensionId: String, sourceId: String?, animeUrl: String): List<Episode>
    override suspend fun streams(extensionId: String, sourceId: String?, episodeUrl: String): List<StreamSource>
}
```

Rules the existing backends follow and new ones must too:

* Throw `BackendException(message, code, cause, retryable)` — never return an empty list to signal
  failure.
* Unsupported operations throw `EXTENSION_UNSUPPORTED`; the SDK hides them per source capability.
* Report install progress through the callback; no silent downloads.
* Keep everything suspend-based and off the main thread.

## 2. Register it

In `RuntimeManager`:

```kotlin
private val custom = MyBackend(context)
private val backends: List<ExtensionBackend> = listOf(aniyomi, cloudstream, custom)
```

`backendFor(type)` needs the matching `BackendType` case. The manager already handles repository
aggregation, install/update/rollback, event fan-out, logging and error mapping, so no service changes
are needed.

## 3. UI

`RepositoriesScreen` offers one chip per backend and `ExtensionManagerScreen` reads capabilities from
`RuntimeInfo`, so a new backend shows up automatically. Add a display name if you want nicer labels.

## 4. Tests

Add a `*PackageCompatibilityTest` for the new package format (accept/reject matrix) next to the
existing ones. Keep the protocol tests untouched: the wire format must not change for a new backend.

## What not to do

* Do not convert other ecosystems' objects into Aniyomi or CloudStream types; map to the unified
  models in `bridge-ipc` only.
* Do not add fake or demo data. A backend with nothing installed returns an empty list legitimately,
  but a failed call must throw.
* Do not load untrusted code in the host app's process — it belongs in a backend inside the runtime.
