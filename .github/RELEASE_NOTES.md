## Kiroku Runtime Bridge 1.2.1

Installing extensions works again, and both the repository screen and the extension list now look
like a proper extension manager.

### Why installs failed in 1.2.0

Every repository download was written to disk as `<id>-<version>.apk.part` (CloudStream:
`.cs3.part`) and then handed straight to the installer. The installers look at the file name and
reject anything that does not end in `.apk` or `.cs3`, so a perfectly good download was refused
with *Extensions are APK packages* or *CloudStream plugins use the .cs3 extension*.

Downloads are now staged as `.part` and renamed to the real extension once the transfer completes
(a failed transfer deletes the staging file). The installer suffix checks are unchanged: the file
really is an APK/CS3 by then.

### Interface

* **Repositories** is a per-manager list: a card for **Aniyomi** and one for **CloudStream**, each
  with its badge, an installed-count pill, and its repository rows showing path and host with
  **copy** and **delete** actions. Each card ends with **＋ Add Repository to …**, which opens a
  dialog for that manager only.
* **Available** groups installable packages under the same manager cards, with `name` /
  `version • backend • language` rows and an accent **Install** action (plus **Update** and
  **Installed** states). Both screens share one styling source, so the two managers render
  identically.

### Downloads

| File | What it is |
| --- | --- |
| `KirokuRuntimeHost-1.2.1.apk` | The **only installable file**. The Runtime Host app: runs Animiru anime extensions, Mihon manga extensions and CloudStream `.cs3` plugins in a separate, isolated process. |
| `bridge-sdk-1.2.1.aar` | Library for your own app. Contains `io.kiroku.runtime.bridge.ExtensionBridge` and the Binder client. Not installable. |
| `bridge-ipc-1.2.1.aar` | Library for your own app. Shared IPC models, manga models and the AIDL service interface. Not installable — the SDK needs it next to it. |

### Upgrade notes

1. Install `KirokuRuntimeHost-1.2.1.apk` over any earlier version (same signing key).
2. Open **Repositories**, then **Load catalogue**. Anime repositories are listed from
   `keiyoushi/extensions` (manga, `index.pb`) and `recloudstream/cloudstream` (plugins), plus any
   repository you add — `https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json`
   is a good one.
3. Install an extension from **Available**; it now completes instead of failing on the file
   extension. Switch to **Installed** to see it with its sources.

### Known limitations

* Nothing in this release has been verified on a device or emulator; the build is green and the two
  repository clients are covered by unit tests, but treat on-device behaviour as unverified.
* Playback belongs to the host app: the runtime returns streams, subtitles and audio tracks only.

### Using the SDK

```kotlin
implementation(files("libs/bridge-sdk-1.2.1.aar"))
implementation(files("libs/bridge-ipc-1.2.1.aar"))
```

Declare the package query so the SDK can find the runtime:

```xml
<queries>
    <package android:name="io.kiroku.runtime.host" />
</queries>
