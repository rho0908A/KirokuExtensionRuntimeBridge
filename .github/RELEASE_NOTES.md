## Kiroku Runtime Bridge 1.2.0

Anime extensions install **and load** now. This release ports the anime API to Animiru's
`extensions-lib` v17 surface, fixes adding `repo.json` repositories, and keeps extensions whose
code fails to instantiate instead of deleting them.

### Why anime extensions disappeared before

Anime APKs link their host API by exact JVM class name — `eu/kanade/tachiyomi/network/RequestsKt`,
`OkHttpExtensionsKt`, `util/JsoupExtensionsKt`, `util/JsonExtensionsKt`, `NetworkHelper`, and the
`animesource/model` types. This runtime had the same helpers under a different class name
(`NetworkKt`), so `install` succeeded, `load` threw `NoClassDefFoundError`, the APK was deleted and
the Installed tab stayed empty. The helpers now live under the names the extensions reference.

### Highlights

* **Anime API ported to Animiru (`animiruorg/Animiru`) `extensions-lib` v17** — the versions
  Animiru itself accepts (14, 16, 17), with the RxJava `fetch*` fallbacks kept so lib-14 packages
  still work. The backend keeps the `aniyomi` name; the anime layer follows Animiru and the manga
  layer follows Mihon.
* **`repo.json` repositories work.** Adding a repository by its descriptor URL used to fail with
  *does not declare an index* (Aniyomi) or *Element … is not a JsonArray* (CloudStream). The
  Aniyomi client now reads the descriptor, follows `index_v2`, keeps the legacy JSON array path and
  falls back to a sibling `index.min.json`; the CloudStream client follows
  `pluginLists`/`pluginList` and falls back to `plugins.json`.
* **Failed loads keep the APK.** A package that installs but cannot be instantiated stays listed
  with its error instead of being silently removed, so the Logs screen explains what happened.
* **Anime source language** is read from `AnimeCatalogueSource`, matching v17.
* **`kotlin-reflect` stays pinned** to the project's Kotlin version (the 1.1.0 startup crash).

### Downloads

| File | What it is |
| --- | --- |
| `KirokuRuntimeHost-1.2.0.apk` | The **only installable file**. The Runtime Host app: runs Animiru anime extensions, Mihon manga extensions and CloudStream `.cs3` plugins in a separate, isolated process. |
| `bridge-sdk-1.2.0.aar` | Library for your own app. Contains `io.kiroku.runtime.bridge.ExtensionBridge` and the Binder client. Not installable. |
| `bridge-ipc-1.2.0.aar` | Library for your own app. Shared IPC models, manga models and the AIDL service interface. Not installable — the SDK needs it next to it. |

### Upgrade notes

1. Install `KirokuRuntimeHost-1.2.0.apk` over any earlier version (same signing key).
2. Open **Repositories**, then **Load catalogue**. Anime repositories are listed from
   `keiyoushi/extensions` (manga, `index.pb`) and `recloudstream/cloudstream` (plugins), plus any
   anime repository you add — `https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json`
   is a good one.
3. Install an extension from **Available**, then switch to **Installed** — it should appear with its
   sources. If it does not, **Logs** now reports the reason instead of dropping the package.

### Known limitations

* Nothing in this release has been verified on a device or emulator; the build is green and the
  APIs match the ones the extensions actually call, but treat runtime behaviour as unverified.
* Playback belongs to the host app: the runtime returns streams, subtitles and audio tracks only.

### Using the SDK

```kotlin
implementation(files("libs/bridge-sdk-1.2.0.aar"))
implementation(files("libs/bridge-ipc-1.2.0.aar"))
```

Declare the package query so the SDK can find the runtime:

```xml
<queries>
    <package android:name="io.kiroku.runtime.host" />
</queries>
```
