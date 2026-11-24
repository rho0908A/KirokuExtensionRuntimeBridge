# Building and CI

## Toolchain

* JDK 17
* Android SDK with **platform 34** and **build-tools 35.0.0**
* Gradle 8.7

`local.properties` must contain `sdk.dir=/path/to/Android/sdk`. The repository has no checked-in
wrapper; run `gradle wrapper` once if you prefer `./gradlew`.

## Commands

```bash
gradle :bridge-ipc:assembleRelease :bridge-sdk:assembleRelease
gradle :backend-aniyomi:assembleRelease :backend-cloudstream:assembleRelease
gradle :runtime-host:assembleDebug :sample-host:assembleDebug
gradle test            # unit tests: protocol, SDK compatibility, package validation, error mapping
```

Artifacts:

| Output | Path |
| --- | --- |
| Runtime Host APK | `runtime-host/build/outputs/apk/debug/runtime-host-debug.apk` |
| Sample host APK | `sample-host/build/outputs/apk/debug/sample-host-debug.apk` |
| SDK AAR | `bridge-sdk/build/outputs/aar/bridge-sdk-release.aar` |
| IPC AAR | `bridge-ipc/build/outputs/aar/bridge-ipc-release.aar` |

## Build notes

* **compileSdk 34 vs CloudStream 4.9.1.** The CloudStream library AAR declares `minCompileSdk=37`.
  `gradle.properties` sets `android.experimental.disableCompileSdkChecks=true`; only stable JVM-level
  CloudStream types are referenced, so the check is not meaningful here. Remove the flag once AGP and
  a platform 37 are available and raise `compileSdk` instead.
* **JitPack filter.** `settings.gradle.kts` maps `com.github.*` (NiceHttp, NewPipeExtractor, the
  CloudStream library) to JitPack; without the filter, resolution of nested artifacts fails.
* **Signing.** Debug builds of `runtime-host` and `sample-host` share the debug keystore, which is
  why the runtime accepts the sample without an allow-list entry.

## CI

`.github/workflows/android.yml` builds on every push and pull request:

1. JDK 17 + Android SDK (platform 34, build-tools 35.0.0) and Gradle 8.7 via `gradle/actions/setup-gradle`.
2. `gradle test`.
3. `gradle :runtime-host:assembleDebug :sample-host:assembleDebug :bridge-sdk:assembleRelease`.
4. Uploads the APKs, the AARs and the test reports as workflow artifacts.

The workflow only produces artifacts. Nothing is published or signed for release automatically, and
no secrets are required.

## Versioning

* `PROTOCOL_VERSION` in `bridge-ipc` guards wire compatibility.
* `versionName` of the runtime and the SDK move together per release.
* Backend API surfaces follow the upstream library versions they emulate (Aniyomi lib 12–17,
  CloudStream 4.9.1).
