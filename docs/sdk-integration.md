# SDK integration

## 1. Add the dependency

Download `bridge-sdk-<version>.aar` and `bridge-ipc-<version>.aar` from the latest
[release](https://github.com/rho0908A/KirokuExtensionRuntimeBridge/releases) into your app's `libs/`
folder, or build them from source:

```bash
gradle :bridge-sdk:assembleRelease :bridge-ipc:assembleRelease
# bridge-sdk/build/outputs/aar/bridge-sdk-release.aar
# bridge-ipc/build/outputs/aar/bridge-ipc-release.aar
```

```kotlin
dependencies {
    implementation(files("libs/bridge-sdk-release.aar"))
    implementation(files("libs/bridge-ipc-release.aar"))
}
```

Or, inside this repository, `implementation(project(":bridge-sdk"))`.

## 2. Declare package visibility

Android 11+ hides installed packages by default. Without this the SDK reports `FAILED` with
`PERMISSION_DENIED` instead of `NOT_INSTALLED`:

```xml
<queries>
    <package android:name="io.kiroku.runtime.host" />
</queries>
```

## 3. Connect

```kotlin
val bridge = KirokuRuntimeBridge(context)

lifecycleScope.launch {
    val status = bridge.connect()
    if (!status.isConnected) {
        showMessage(status.message ?: "Runtime unavailable")   // NOT_INSTALLED / INCOMPATIBLE / FAILED
    }
}
```

`connect()` returns a `RuntimeStatus` instead of throwing for the "not installed / incompatible"
cases, because those are normal states a UI must render. Once connected, every other call
throws `ExtensionBridgeException` on failure.

## 4. Query and play

```kotlin
val extensions = bridge.getExtensions()          // installed, with capabilities + sources
val extension  = extensions.first { it.loadError == null }

val results  = bridge.search(extension.id, "frieren")
val details  = bridge.getDetails(extension.id, results.first().url!!)
val streams  = bridge.extractStreams(extension.id, details.episodes.first().url!!)
streams.forEach { println("${it.quality} ${it.url}") }
```

`Anime.id`, `Episode.id` and `StreamSource.url` come straight from the extension. Pass
`sourceId` as the third argument when an extension exposes several sources.

### Manga

Manga extensions are Mihon APKs (`tachiyomi.extension`, lib 1.4–1.6) and advertise themselves with
`capabilities.manga`; they answer the manga calls and reject the anime ones with
`EXTENSION_UNSUPPORTED`.

```kotlin
val mangaExtensions = bridge.getExtensions().filter { it.capabilities.manga }
val extension = mangaExtensions.first { it.loadError == null }

val mangas   = bridge.searchManga(extension.id, "frieren")
val details  = bridge.getMangaDetails(extension.id, mangas.first().id)
val chapters = bridge.getChapters(extension.id, mangas.first().id)
val pages    = bridge.getPages(extension.id, chapters.first().id)

pages.forEach { page -> reader.load(page.imageUrl ?: page.url, page.headers) }
```

`popularManga` and `latestManga` mirror `popular`/`latest`. Pass `sourceId` when a manga extension
exposes several sources. Every `MangaPage` carries the `headers` its image request needs (Referer,
User-Agent), so forward them to the image loader.

## 5. Observe state

```kotlin
bridge.status.collect { status -> render(status) }   // StateFlow
bridge.events.collect { event ->
    when (event) {
        is BridgeEvent.InstallProgress -> showProgress(event.bytesDownloaded, event.totalBytes)
        is BridgeEvent.Installed -> reloadList()
        is BridgeEvent.RuntimeError -> showError(event.message)
        else -> Unit
    }
}
```

## 6. Lifecycle

```kotlin
override fun onDestroy() {
    bridge.close()   // unbinds and cancels reconnects
    super.onDestroy()
}
```

One instance per process is enough. Tunables live in `BridgeConfig` (bind timeout, control timeout,
operation timeout, reconnect policy).

## Error handling

```kotlin
try {
    bridge.search(extension.id, query)
} catch (e: ExtensionBridgeException) {
    when (e.code) {
        ErrorCode.RUNTIME_NOT_INSTALLED -> offerInstall()
        ErrorCode.PROTOCOL_MISMATCH    -> offerRuntimeUpdate()
        ErrorCode.PERMISSION_DENIED    -> explainAllowList()
        else                           -> show(e.message)
    }
}
```

Never treat an exception as "no results": that distinction is the reason the SDK does not catch
backend errors internally.
