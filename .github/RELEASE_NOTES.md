## What this release contains

| Asset | What it is |
| --- | --- |
| `KirokuRuntimeHost-<version>.apk` | The **only installable file**. The Runtime Host app: runs Aniyomi extensions and CloudStream `.cs3` plugins in a separate, isolated process. |
| `bridge-sdk-<version>.aar` | Library for your own app. Contains `io.kiroku.runtime.bridge.ExtensionBridge` and the Binder client. Not installable. |
| `bridge-ipc-<version>.aar` | Library for your own app. Shared IPC models and the AIDL service interface. Not installable — the SDK needs it next to it. |

The AARs are the "SDK" side of the project. An APK is an app you install; an AAR is compiled library
code you reference from an app you build yourself. If you only want to use the Runtime Host, download
the APK and ignore the two `.aar` files.

Debug and sample-client builds are not published here — they are available under the
**Actions → Android → artifacts** of each run. Install the sample client only if you want to try the
Binder bridge from a client app.

## Install

1. Install `KirokuRuntimeHost-<version>.apk` on an Android 8.0+ (API 26) device.
2. Open the Runtime Host, choose Aniyomi and/or CloudStream under **Aniyomi settings**, and load the
   extension APK or `.cs3` plugin.

The APK is signed with the standard debug keystore so it installs without extra setup. It is not
Play-Store distributable: rebuild with your own keystore before distributing.

## Use the SDK in your app

```kotlin
// settings.gradle.kts / build.gradle.kts
implementation(files("libs/bridge-sdk-<version>.aar"))
implementation(files("libs/bridge-ipc-<version>.aar"))
```

The client also needs a package visibility entry, because it binds to a service in another app:

```xml
<queries>
    <package android:name="io.kiroku.runtime.host" />
</queries>
```

Then connect with `BridgeConfig`, call `search`, `getDetails` and `extractStreams`, and collect
`BridgeEvent`s for state changes. `README.md` and `docs/sdk-integration.md` describe the full flow.

## Limitations

The project builds, passes its unit tests and packages these artifacts, but extension loading,
playback and the Binder round trip have not been verified on a real device in this environment.
See `docs/troubleshooting.md` for a device verification checklist.
