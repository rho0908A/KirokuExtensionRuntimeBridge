# Architecture

## Why a separate runtime

Extensions are code from third parties. Loading them in the player process means:

* an extension crash kills playback,
* extension dependencies (OkHttp, Jsoup, RxJava, CloudStream internals) pollute the player's
  classpath,
* every player has to reimplement Aniyomi/CloudStream compatibility.

The Runtime Host moves all of that into its own APK and process boundary. The player only talks to
a stable, versioned Binder contract.

## Components

### `bridge-ipc`

The shared contract, intentionally tiny:

* `IRuntimeHost.aidl` — `getProtocolDescriptor()`, `execute(String): String`,
  `registerCallback`, `unregisterCallback`
* `IRuntimeCallback.aidl` — `onEvent(String)`
* `Models.kt` — unified models (`Extension`, `Anime`, `AnimeDetails`, `Episode`, `Video`,
  `StreamSource`, `Subtitle`, `AudioTrack`, `RuntimeStatus`, `ExtensionError`, …)
* `Protocol.kt` — protocol version, method names, request/response envelopes, event types

Requests and responses are JSON inside a single string, so models can gain fields without breaking
older clients and without Parcelable versioning. The AIDL surface changes only for breaking
protocol work.

### `bridge-sdk`

`ExtensionBridge` is the public API; `KirokuRuntimeBridge` is the implementation:

* binds to `io.kiroku.runtime.host/.service.RuntimeHostService`
* checks availability and protocol compatibility before use
* maps every failure to `ExtensionBridgeException` (never an empty list on error)
* watches `DeathRecipient` + `onBindingDied`, then rebinds with exponential backoff
* forwards runtime events as a `SharedFlow<BridgeEvent>`

### `backend-api`

`ExtensionBackend` is the only thing the runtime knows about an ecosystem:

```kotlin
suspend fun installPackage(file: File, repositoryId: String?, onProgress: …): Extension
suspend fun search(extensionId, sourceId, query, page): List<Anime>
suspend fun details(extensionId, sourceId, animeUrl): AnimeDetails
suspend fun streams(extensionId, sourceId, episodeUrl): List<StreamSource>
```

Backends own loading, packaging rules, version compatibility and error codes. They never convert one
ecosystem's objects into another's.

### `runtime-host`

* `RuntimeManager` — singleton owning both backends, repositories, settings, logs, backups and the
  install/update pipeline. Used by both the UI and the service, so both see identical state.
* `RuntimeHostService` — Binder endpoint, caller authorisation, JSON dispatch, event fan-out.
* Compose UI — Extensions, Repositories, Logs and the Extension Manager screen (runtime status,
  Aniyomi settings, install/sync actions, client allow-list).

### Backends

`backend-aniyomi` validates APKs (`tachiyomi.animeextension` feature, metadata keys, lib 12–17,
dangerous permissions), loads sources with a child-first `PathClassLoader` and executes them against
`aniyomi-api`.

`backend-cloudstream` extracts `.cs3` archives (zip-slip safe), loads `classes.dex` with
`DexClassLoader`, calls `BasePlugin.load()`, diffs `APIHolder.apis` to discover the providers it
registered, and executes them against the bundled CloudStream 4.9.1 library.

## Data flow (search)

1. Host calls `bridge.search(extensionId, query)`.
2. SDK sends `{"id":…,"method":"sources.search","params":{…}}` to the service.
3. Service authorises the caller and dispatches to `RuntimeManager`.
4. Manager selects the backend that owns the extension and calls its `search`.
5. Backend executes the extension and maps results to `Anime`.
6. Response JSON is decoded by the SDK into `List<Anime>`.

Failures at any step become `ExtensionError { code, message, details, backend, retryable }`.

## State ownership

| State | Owner | Persistence |
| --- | --- | --- |
| Installed extensions | backend (`installed: StateFlow`) | package files in `files/extensions/<backend>` |
| Repositories | `RepositoryStore` | SharedPreferences JSON |
| Settings / allow-list | `RuntimeSettings` | SharedPreferences |
| Logs | `LogStore` | memory (bounded ring buffer) |
| Backups for rollback | `RuntimeManager` | `files/backups` |

## Deliberate limits

* One runtime process; no per-extension sandboxing beyond class-loader isolation.
* No conversion between Aniyomi and CloudStream objects.
* No extension code is bundled, generated or faked.
