## Kiroku Runtime Bridge 1.2.3

Every manager repository now lists its catalogue, and the two install errors from 1.2.1 are fixed.

### Repositories other than the anime one showed nothing

`Keiyoushi` (the manga repository) publishes its catalogue as `index.pb`, a gzipped protobuf index
that `repo.json` points at through `index_v2`. Its `index.min.json` is now only a two-entry "update
your app" placeholder. The runtime did follow `index_v2`, but it read the protobuf wrongly: field
101 is not the extension repeated — it is one wrapper message whose own field 1 repeats every
extension. Each wrapper was therefore parsed as a single malformed entry and thrown away, so the
repository reported **0 extension(s)**.

Both layouts are decoded now, and the real Keiyoushi index decodes to **1708 extensions** instead of
zero. A regression test covers the wrapped and the flat layout. Repositories that still serve
`index.min.json` / `index.json` (yuzono anime repo, custom Aniyomi repositories) are unchanged.

### The install errors from 1.2.1

* **`APK declares neither tachiyomi.animeextension nor tachiyomi.extension`** — an extension declares
  its ecosystem with a `<uses-feature>` tag, and Android only reports those tags when the archive is
  parsed with `PackageManager.GET_CONFIGURATIONS`. Packages are now read with that flag, with the
  `<meta-data>` keys and the package-name prefix as fallbacks.
* **`Could not create plugin class loader`** — Android 14's safer dynamic code loading refuses to
  load DEX/APK files that are writable, and both backends load packages copied into the app's data
  directory. The extracted dex and the installed APK are now marked read-only before the class
  loader opens them, as the Android 14 guidance requires. Loader failures now carry the underlying
  exception too, so the next Logs entry names the real cause.

If you are on 1.2.1 you are seeing those two errors: this build contains their fixes as well.

### Downloads

| File | What it is |
| --- | --- |
| `KirokuRuntimeHost-1.2.3.apk` | The **only installable file**. The Runtime Host app: runs Animiru anime extensions, Mihon manga extensions and CloudStream `.cs3` plugins in a separate, isolated process. |
| `bridge-sdk-1.2.3.aar` | Library for your own app. Contains `io.kiroku.runtime.bridge.ExtensionBridge` and the Binder client. Not installable. |
| `bridge-ipc-1.2.3.aar` | Library for your own app. Shared IPC models, manga models and the AIDL service interface. Not installable — the SDK needs it next to it. |

### Upgrade notes

1. Install `KirokuRuntimeHost-1.2.3.apk` over any earlier version (same signing key).
2. Open **Repositories** → **Load catalogue**; the manga card should now report its extensions.
3. Reinstall anything that failed to install before.

### Known limitations

* The modules compile and their unit tests pass (including the real-index decode check), but nothing
  in this release ran on a device or emulator.
* Playback belongs to the host app: the runtime returns streams, subtitles and audio tracks only.

### Using the SDK

```kotlin
implementation(files("libs/bridge-sdk-1.2.3.aar"))
implementation(files("libs/bridge-ipc-1.2.3.aar"))
```

Declare the package query so the SDK can find the runtime:

```xml
<queries>
    <package android:name="io.kiroku.runtime.host" />
</queries>
