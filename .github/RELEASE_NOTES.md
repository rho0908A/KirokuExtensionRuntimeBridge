## Kiroku Runtime Bridge 1.1.0

### What's new

* **Manga extensions.** Mihon manga extension APKs (`tachiyomi.extension`, extensions-lib 1.4–1.6)
  load and execute in the same runtime as the anime ones. The SDK gained `searchManga`,
  `popularManga`, `latestManga`, `getMangaDetails`, `getChapters` and `getPages`; manga sources
  advertise themselves with `capabilities.manga`.
* **Anime layer retargeted to Anikku.** Anime extensions now run against Anikku's `animesource` API
  (extensions-lib 12–16) instead of the archived Aniyomi app. The backend keeps the `aniyomi` name,
  so existing host integrations do not change.
* **Real repository indexes.** Keiyoushi's `index.min.json` is now a two-entry placeholder; the runtime
  follows `repo.json` → `index_v2` and decodes the gzipped `index.pb` protobuf index, so the catalogue
  lists real extensions. Legacy Aniyomi `index.min.json` anime repositories keep working.
* The first 1.0.1 build failed its unit tests; that is fixed here, and `main` builds green again.

### Assets

| Asset | What it is |
| --- | --- |
| `KirokuRuntimeHost-1.1.0.apk` | The **only installable file**. The Runtime Host app: runs Aniyomi/Anikku anime extensions, Mihon manga extensions and CloudStream `.cs3` plugins in a separate, isolated process. |
| `bridge-sdk-1.1.0.aar` | Library for your own app. Contains `io.kiroku.runtime.bridge.ExtensionBridge` and the Binder client. Not installable. |
| `bridge-ipc-1.1.0.aar` | Library for your own app. Shared IPC models, manga models and the AIDL service interface. Not installable — the SDK needs it next to it. |

The AARs are the "SDK" side of the project. An APK is an app you install; an AAR is compiled library
code you reference from an app you build yourself. If you only want to use the Runtime Host, download
the APK and ignore the two `.aar` files.

Debug builds and the sample client are not published here — they are available under
**Actions → Android → artifacts** of each run.

### Install

1. Install `KirokuRuntimeHost-1.1.0.apk` on an Android 8.0+ (API 26) device.
2. Open the Runtime Host, go to **Repositories** and load the catalogue, then install Aniyomi/Anikku
   anime extensions or Mihon manga extensions from it (or use **Extensions → Load extension APK** for
   a file you already have).

The APK is signed with the standard debug keystore so it installs without extra setup. It is not
Play-Store distributable: rebuild with your own keystore before distributing.

### Use the SDK in your app

Put both AARs into your app's `libs/` folder:

```kotlin
dependencies {
    implementation(files("libs/bridge-sdk-1.1.0.aar"))
    implementation(files("libs/bridge-ipc-1.1.0.aar"))
}
```

The client also needs a package visibility entry, because it binds to a service in another app:

```xml
<queries>
    <package android:name="io.kiroku.runtime.host" />
</queries>
```

Then `connect()`, call `search`/`getDetails`/`extractStreams` for anime or
`searchManga`/`getChapters`/`getPages` for manga, and collect `BridgeEvent`s for state changes.
`README.md` and `docs/sdk-integration.md` describe the full flow, `docs/api-reference.md` lists every
call and model.

### Limitations

Extension loading, playback and the Binder round trip compile and pass unit tests, but have not been
verified on a physical device in this environment. See `docs/troubleshooting.md` for the device
verification checklist.
