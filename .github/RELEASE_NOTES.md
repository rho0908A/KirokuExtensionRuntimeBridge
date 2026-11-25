## What this release contains

| Asset | What it is |
| --- | --- |
| `KirokuRuntimeHost-<version>.apk` | The Runtime Host app. Install this to run Aniyomi extensions and CloudStream `.cs3` plugins in a separate, isolated process. |
| `KirokuRuntimeHost-<version>-debug.apk` | Same build with debug logging enabled, for troubleshooting. |
| `KirokuSampleHost-<version>.apk` | Small demo client that binds to the Runtime Host over Binder and browses extensions. Useful to confirm the bridge works end to end. |
| `bridge-sdk-<version>.aar` | The SDK your own app embeds. Contains `io.kiroku.runtime.bridge.ExtensionBridge` and the client implementation. |
| `bridge-ipc-<version>.aar` | Shared IPC models and the AIDL service interface. Required by both the host app and the SDK. |

## Install

1. Install `KirokuRuntimeHost-<version>.apk` on an Android 8.0+ (API 26) device.
2. Open the Runtime Host, choose Aniyomi and/or CloudStream under **Aniyomi settings**, and load the
   extension APK or `.cs3` plugin.
3. Install `KirokuSampleHost-<version>.apk` if you want to try the bridge from a client app.

The APKs are signed with the standard debug keystore so they install without extra setup. They are
not Play-Store distributable: rebuild with your own keystore before distributing.

## Use the SDK in your app

```kotlin
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
