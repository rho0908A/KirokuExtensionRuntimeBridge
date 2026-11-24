# SDK integration

## 1. Add the dependency

Build the AAR and copy it into your app's `libs/` folder:

```bash
gradle :bridge-sdk:assembleRelease
# bridge-sdk/build/outputs/aar/bridge-sdk-release.aar
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
