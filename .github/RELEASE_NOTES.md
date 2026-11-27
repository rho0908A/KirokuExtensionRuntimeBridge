## Kiroku Runtime Bridge 1.2.2

Installs now go through on Android 14 and later, and anime extensions are recognised instead of
being rejected before they are even loaded.

### What the two errors in 1.2.1 were

**`APK declares neither tachiyomi.animeextension nor tachiyomi.extension`** — an extension declares
its ecosystem with a `<uses-feature>` tag, and Android only reports those tags through
`PackageInfo.reqFeatures` when the APK is parsed with `PackageManager.GET_CONFIGURATIONS`. The
runtime did not ask for them, so every package looked like it belonged to no ecosystem and was
refused. Packages are now parsed with that flag, and the `<meta-data>` keys and the canonical
package prefix are checked as fallbacks (that flag is also what OEM parsers are least reliable
about).

**`Could not create plugin class loader`** — this was Android 14's safer dynamic code loading. For
apps targeting API 34, ART refuses to load a DEX/JAR/APK that is writable, and both backends load
code from files they copy into the app's own data directory, which is writable by default. The
extracted dex and the installed APK are now marked read-only before the class loader opens them, as
the Android 14 guidance requires. Loader failures now also carry the underlying exception, so the
next Logs entry names the real cause instead of only the wrapper.

### Interface

* **Repositories** shows one card per manager (**Aniyomi**, **CloudStream**) with its badge, an
  installed-count pill and its repository rows (path, host, copy, delete), ending in
  **＋ Add Repository to …**.
* **Available** groups installable packages under the same manager cards with
  `name` / `version • backend • language` rows and an accent **Install** action.

### Downloads

| File | What it is |
| --- | --- |
| `KirokuRuntimeHost-1.2.2.apk` | The **only installable file**. The Runtime Host app: runs Animiru anime extensions, Mihon manga extensions and CloudStream `.cs3` plugins in a separate, isolated process. |
| `bridge-sdk-1.2.2.aar` | Library for your own app. Contains `io.kiroku.runtime.bridge.ExtensionBridge` and the Binder client. Not installable. |
| `bridge-ipc-1.2.2.aar` | Library for your own app. Shared IPC models, manga models and the AIDL service interface. Not installable — the SDK needs it next to it. |

### Upgrade notes

1. Install `KirokuRuntimeHost-1.2.2.apk` over 1.2.1 or earlier (same signing key).
2. Extensions that 1.2.1 rejected were never stored, so install them again from **Available**.
   Extensions that were stored but failed to load are picked up on the next **Refresh**.
3. Open **Repositories** → **Load catalogue** if the lists look empty; anime repositories come from
   `yuzono/anime-repo`, manga from `keiyoushi/extensions`, plugins from the Phisher CloudStream
   repository, plus whatever you add.

### Known limitations

* The two backend modules compile and their unit tests pass, but nothing here ran on a device or
  emulator: on-device install and playback remain unverified.
* Playback belongs to the host app: the runtime returns streams, subtitles and audio tracks only.

### Using the SDK

```kotlin
implementation(files("libs/bridge-sdk-1.2.2.aar"))
implementation(files("libs/bridge-ipc-1.2.2.aar"))
```

Declare the package query so the SDK can find the runtime:

```xml
<queries>
    <package android:name="io.kiroku.runtime.host" />
</queries>
