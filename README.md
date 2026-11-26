# KirokuExtensionRuntimeBridge

An Android-only runtime that executes **Aniyomi** (APK) and **CloudStream** (`.cs3`) extensions
outside the host application, plus a small SDK any Kotlin app can use to talk to it.

The bridge is independent of Kiroku (or any other player). Kiroku is simply the first adopter.

```
 Host app (Kotlin / Compose)                 Runtime Host APK
 ┌────────────────────────────┐   Binder    ┌──────────────────────────────┐
 │ :bridge-sdk                │ ──────────▶ │ :runtime-host                │
 │  ExtensionBridge           │  JSON RPC   │  RuntimeHostService (AIDL)   │
 │  KirokuRuntimeBridge       │ ◀────────── │  RuntimeManager              │
 └────────────────────────────┘   events    │  :backend-aniyomi  :backend-cloudstream
                                            └──────────────────────────────┘
```

## Modules

| Module | Output | Purpose |
| --- | --- | --- |
| `bridge-ipc` | AAR | AIDL contract, protocol constants, unified models |
| `bridge-sdk` | AAR | Public SDK: `connect`, `getExtensions`, `search`, `getDetails`, `getEpisodes`, `extractStreams`, events, reconnect |
| `backend-api` | AAR | `ExtensionBackend` abstraction shared by ecosystems |
| `aniyomi-api` | AAR | Host-side API surface: Anikku `animesource` (lib 12–16) + Mihon `source` (lib 1.4–1.6) |
| `backend-aniyomi` | AAR | Loads Aniyomi/Anikku anime and Mihon manga APK extensions, executes both |
| `backend-cloudstream` | AAR | Loads `.cs3` plugins against the CloudStream 4.9.1 library |
| `runtime-host` | APK | The runtime service + Extension Manager UI |
| `sample-host` | APK | Reference integration using only the SDK |

Everything is Kotlin + Gradle Kotlin DSL + native Android APIs. There is no Dart, React Native,
Node, Python or web runtime anywhere in this project.

## What's new in 1.1.0

* **Manga extensions.** Mihon manga APKs (`tachiyomi.extension`, lib 1.4–1.6) load and execute next to
  the anime ones, and the SDK gained `searchManga`, `popularManga`, `latestManga`, `getMangaDetails`,
  `getChapters` and `getPages`.
* **Anime layer retargeted to Anikku** (`komikku-app/anikku`, extensions-lib 12–16) instead of the
  archived Aniyomi app. The backend keeps the `aniyomi` name, so host integrations do not change.
* **Real repository indexes.** `repo.json` → `index_v2` → gzipped `index.pb` is decoded, so
  Keiyoushi's catalogue lists real manga extensions instead of the two placeholder entries that
  `index.min.json` now contains.

## Status (read this before trusting it)

* **Builds are green.** The `Android` workflow runs `gradle test` and assembles
  `:runtime-host:assembleDebug`, `:sample-host:assembleDebug`, `:bridge-sdk:assembleRelease` and
  `:bridge-ipc:assembleRelease` on every push to `main`. The installable APK and the two SDK
  libraries for the current version (`1.1.0`) are attached to
  [Releases](https://github.com/rho0908A/KirokuExtensionRuntimeBridge/releases); debug builds and the
  sample client are on the run page under **Artifacts → `kiroku-runtime-bridge`**.
* **Runtime behaviour is not verified on hardware.** No device or emulator test has been run:
  extension loading, playback and the Binder round trip are implemented against the real APIs and
  compile, but treat them as unverified until the checklist in `docs/troubleshooting.md` has been
  executed on a device.
* CloudStream's library AAR declares `minCompileSdk=37`. This project compiles against 34 and
  disables that metadata check (`android.experimental.disableCompileSdkChecks`); it binds only to
  stable JVM-level CloudStream classes.
* **Anime and manga extension APKs are both supported by the Aniyomi backend.** Anime packages run
  against Anikku's `animesource` API (extensions-lib 12–16) and manga packages against Mihon's
  `source` API (extensions-lib 1.4–1.6). The archived Aniyomi app is no longer the reference: the
  anime layer follows Anikku, the manga layer follows Mihon, and the backend keeps the `aniyomi`
  name so host integrations do not have to change.
* **Repository indexes:** Keiyoushi's `index.min.json` is now only a two-entry "update your app"
  placeholder. The runtime follows `repo.json` → `index_v2` and decodes the gzipped `index.pb`
  protobuf index, so real manga extensions are listed; legacy Aniyomi `index.min.json` anime
  repositories still work the same way.

## Quick start (build from source)

Requirements: JDK 17, Android SDK platform 34, Gradle 8.7 (a wrapper is not checked in; CI supplies
Gradle itself — run `gradle wrapper` once if you want `./gradlew`).

```bash
# Everything the runtime ships
gradle :runtime-host:assembleDebug :sample-host:assembleDebug \
       :bridge-sdk:assembleRelease :bridge-ipc:assembleRelease

# Install the runtime and the reference client
adb install -r runtime-host/build/outputs/apk/debug/runtime-host-debug.apk
adb install -r sample-host/build/outputs/apk/debug/sample-host-debug.apk
```

Both debug builds share one Gradle debug keystore, so the sample is accepted by the runtime without
any setup. If you have no local Android SDK, push to `main` and use the downloaded CI artifacts.

The Runtime Host ships with two real repositories enabled:

* `yuzono/anime-repo` — Aniyomi/Anikku anime extensions (API 12–16)
* `keiyoushi/extensions` — Mihon manga extensions (`repo.json` → `index_v2`, lib 1.6)
* `phisher98/cloudstream-extensions-phisher` — prebuilt CloudStream `.cs3` plugins

## Use it in your app

There are two halves: the **Runtime Host APK**, which owns extension loading and execution, and the
**SDK AAR**, which your app links against. Your app never loads extension code itself.

### 1. Get the runtime APK

Download `KirokuRuntimeHost-<version>.apk` from the latest
[release](https://github.com/rho0908A/KirokuExtensionRuntimeBridge/releases) (one APK, no debug
variant), or build `:runtime-host:assembleRelease` yourself.
Ship it next to your app (bundled asset, your own download link, or the Play/F-Droid store under a
separate package). It must be installed and the user must launch it once.

### 2. Add the SDK to your app

The release page publishes both libraries with the release version in their names, so the fastest
path is to download `bridge-sdk-1.1.0.aar` and `bridge-ipc-1.1.0.aar` into `app/libs/`. To build them
yourself instead:

```bash
gradle :bridge-sdk:assembleRelease :bridge-ipc:assembleRelease
cp bridge-sdk/build/outputs/aar/bridge-sdk-release.aar  app/libs/
cp bridge-ipc/build/outputs/aar/bridge-ipc-release.aar  app/libs/
```

```kotlin
// app/build.gradle.kts
android {
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // names as published on the release page; rename if you built the AARs yourself
    implementation(files("libs/bridge-sdk-1.1.0.aar"))
    implementation(files("libs/bridge-ipc-1.1.0.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
```

Inside this repository the sample uses `implementation(project(":bridge-sdk"))` instead. The SDK
pulls in `bridge-ipc` transitively (`api`), so both AARs must be present in `libs/`.

### 3. Declare package visibility (required on Android 11+)

```xml
<!-- AndroidManifest.xml -->
<queries>
    <package android:name="io.kiroku.runtime.host" />
</queries>
```

Without this, `connect()` returns `FAILED` / `PERMISSION_DENIED` instead of `NOT_INSTALLED`.

### 4. Get permission to call the runtime

The runtime accepts a caller when it is either

* **signed with the same certificate** as the runtime (apps built from this repository in one Gradle
  run share the debug keystore), or
* **listed by package name** in the runtime UI: *Extension Manager → Client access → Allow Client
  App*.

Otherwise every call fails with `PERMISSION_DENIED`. This is deliberate: the runtime executes
arbitrary extension code on behalf of its clients.

### 5. Connect

```kotlin
val bridge = KirokuRuntimeBridge(context)   // one instance per process is enough

lifecycleScope.launch {
    val status = bridge.connect()
    when {
        status.isConnected -> loadExtensions()
        status.state == RuntimeState.NOT_INSTALLED -> offerRuntimeInstall()
        else -> showMessage(status.message ?: "Runtime unavailable")
    }
}
```

`connect()` performs the package check, binds to `RuntimeHostService` and runs the protocol
handshake. `NOT_INSTALLED`, `INCOMPATIBLE` and `FAILED` are returned as state, not thrown, because a
UI has to render them. After a successful connect every other call throws
`ExtensionBridgeException` on failure.

### 6. List, search, play

```kotlin
val extensions = bridge.getExtensions()               // installed extensions + sources
val extension  = extensions.first { it.loadError == null }

val results = bridge.search(extension.id, "frieren")  // also popular() / latest()
val anime   = results.first()
val details = bridge.getDetails(extension.id, anime.url ?: anime.id)
val episode = details.episodes.first()
val streams = bridge.extractStreams(extension.id, episode.url ?: episode.id)

streams.forEach { stream ->
    val headers = stream.headers.toMutableMap()
    stream.referer?.let { headers["Referer"] = it }
    stream.userAgent?.let { headers["User-Agent"] = it }
    player.play(stream.url, headers)
}
```

* Pass the third argument `sourceId` when an extension exposes several sources
  (`extension.sources`); otherwise the first source is used.
* `Anime.id`, `Episode.id` and `StreamSource.url` come straight from the extension — pass them back
  unchanged. `url` is nullable for models the extension built itself, so fall back to `id`.
* `StreamSource` also carries `subtitles`, `audioTracks`, `mimeType`, `isM3u8`, `isDash` and
  `qualityValue`. Playback belongs to your app; the runtime only returns what the extension found.

### 6b. Manga sources (Mihon)

Sources that need manga calls set `extension.capabilities.manga = true`; those are Mihon APKs
(`tachiyomi.extension`, lib 1.4–1.6). Anime sources answer the calls above, manga sources answer the
calls below, and calling the wrong half throws `EXTENSION_UNSUPPORTED` — check the capability first:

```kotlin
val mangaExtensions = bridge.getExtensions().filter { it.capabilities.manga }
val extension = mangaExtensions.first { it.loadError == null }

val mangas   = bridge.searchManga(extension.id, "frieren")      // also popularManga() / latestManga()
val manga    = mangas.first()
val details  = bridge.getMangaDetails(extension.id, manga.id)
val chapters = bridge.getChapters(extension.id, manga.id)
val pages    = bridge.getPages(extension.id, chapters.first().id)

pages.forEach { page ->
    imageLoader.load(page.imageUrl ?: page.url, page.headers)   // forward the headers
}
```

* `Manga.id`, `Chapter.id` and `MangaPage.url` are the extension's own values — send them back
  unchanged. As with anime, pass the third argument `sourceId` when the extension exposes several
  sources.
* `MangaPage` carries its own `headers` (Referer/User-Agent); the image loader must forward them or
  many sources return 403.
* The runtime only resolves page URLs. Downloading, CBZ storage and the reader are the host app's
  job.

### 7. Install and update extensions

```kotlin
val repoExtensions = bridge.getAvailableExtensions()      // every enabled repository
val candidate = repoExtensions.first { it.id == "eu.kanade.tachiyomi.animeextension.en.allanime" }

bridge.installExtension(candidate.id)                     // download + verify + load
bridge.updateExtension(candidate.id)                      // keeps the old package for rollback
bridge.uninstallExtension(candidate.id)
bridge.installExtensionFromFile(pickedApkOrCs3Path)        // user-picked file (SAF: copy to cache first)
bridge.reloadExtensions()                                 // rescan after the user installs by hand
```

Repositories are managed from the app too:

```kotlin
bridge.addRepository("https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
                     BackendType.ANIYOMI, name = "Aniyomi extensions")
bridge.getRepositories().forEach { println("${it.name} enabled=${it.enabled}") }
bridge.removeRepository(repositoryId)
```

### 8. Observe state and progress

```kotlin
bridge.status.collect { status -> renderStatus(status.state, status.info?.runtimeVersion) }

bridge.events.collect { event ->
    when (event) {
        is BridgeEvent.InstallProgress -> showProgress(event.bytesDownloaded, event.totalBytes)
        is BridgeEvent.ExtensionLoaded   -> refreshExtensionList()
        is BridgeEvent.Installed         -> refreshExtensionList()
        is BridgeEvent.RuntimeError      -> showError(event.message)
        is BridgeEvent.Log               -> debugLog(event.entry)
        else -> Unit
    }
}
```

`status` is a `StateFlow` that also reports `DISCONNECTED` after the runtime process dies and then
reconnects automatically (exponential backoff, `BridgeConfig.maxReconnectAttempts`). Requests
in flight during a death are retried once against the new binder. Logs from the runtime are
available with `bridge.getLogs()` / `bridge.clearLogs()`.

### 9. Handle errors by code, not by empty lists

```kotlin
try {
    bridge.search(extension.id, query)
} catch (e: ExtensionBridgeException) {
    when (e.code) {
        ErrorCode.RUNTIME_NOT_INSTALLED -> offerRuntimeInstall()
        ErrorCode.PROTOCOL_MISMATCH     -> offerRuntimeUpdate()
        ErrorCode.PERMISSION_DENIED     -> explainClientAllowList()
        ErrorCode.EXTENSION_LOAD_FAILED -> showBrokenExtension(e.message)
        ErrorCode.NETWORK               -> offerRetry()
        else                            -> show(e.message)
    }
}
```

The SDK never converts a failed operation into an empty result: "the source returned nothing" and
"the source failed" stay distinguishable. `ErrorCode.userMessage()` gives display-ready text, and
`e.retryable` says whether a retry is worth offering.

### 10. Lifecycle

```kotlin
class MyActivity : ComponentActivity() {
    private val bridge by lazy { KirokuRuntimeBridge(applicationContext) }

    override fun onDestroy() {
        bridge.close()   // unbinds the service, cancels reconnects and in-flight collection
        super.onDestroy()
    }
}
```

Tunables live in `BridgeConfig`: `bindTimeoutMs`, `controlTimeoutMs`, `operationTimeoutMs`,
`autoReconnect`, `reconnectInitialDelayMs`, `reconnectMaxDelayMs`, `maxReconnectAttempts`, `logTag`.

### Complete working example

`sample-host/src/main/kotlin/io/kiroku/runtime/sample/MainActivity.kt` is a ~200-line Compose screen
that connects, lists extensions, searches, opens details, extracts streams and prints the runtime
log. It uses only the public SDK — copy it as the starting point.

### Runtime Host UI cheat sheet

| Screen | What it does |
| --- | --- |
| Extension Manager | Plugin Installed / Version card, Update Plugin, Load Plugin APK, Force Re-download, Rollback Version, Runtime Host Releases |
| Extensions | Installed extensions, enable/disable internal anime/manga handling, custom anime/manga APK paths |
| Repositories | Add/remove/toggle Aniyomi and CloudStream indexes; **Load catalogue** fetches real indexes |
| Logs | Live runtime log, error codes and the last failed operation |

## Documentation

* [Architecture](docs/architecture.md)
* [Installation](docs/installation.md)
* [SDK integration](docs/sdk-integration.md)
* [Aniyomi backend](docs/backend-aniyomi.md) · [CloudStream backend](docs/backend-cloudstream.md)
* [Communication protocol](docs/communication.md) · [API reference](docs/api-reference.md)
* [Security model](docs/security.md)
* [Troubleshooting](docs/troubleshooting.md) · [Building & CI](docs/building.md) ·
  [Adding a backend](docs/future-backends.md)

## License

Apache-2.0. Aniyomi and CloudStream remain the property of their respective authors; this project
ships no extension code and no content.
