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

`.github/workflows/android.yml` builds on every push to `main`, every pull request and on manual
dispatch:

1. JDK 17 + Android SDK (platform 34, build-tools 35.0.0) and Gradle 8.7 via `gradle/actions/setup-gradle`.
2. `gradle test`.
3. `gradle :runtime-host:assembleDebug :runtime-host:assembleRelease :sample-host:assembleDebug
   :sample-host:assembleRelease :bridge-sdk:assembleRelease :bridge-ipc:assembleRelease`.
4. Uploads the APKs, the AARs and the test reports as workflow artifacts (`kiroku-runtime-bridge`).

A plain build needs no secrets. Only the release step uses `GITHUB_TOKEN`, which GitHub provides.

## Releasing

The release is produced by the same workflow, with the `release_tag` dispatch input set. Run it
against `main` after the version bump is merged:

```bash
gh workflow run android.yml -f release_tag=v1.1.0
```

That run does the normal build, then copies
`runtime-host/build/outputs/apk/release/runtime-host-release.apk` to
`KirokuRuntimeHost-<version>.apk` and the two AARs to `bridge-sdk-<version>.aar` /
`bridge-ipc-<version>.aar`, and publishes them as a GitHub Release tagged `v<version>` using
`.github/RELEASE_NOTES.md` as the body. `--latest` is applied, so the newest release is the one the
README links to.

Checklist for a release:

1. Bump `versionCode` and `versionName` in `runtime-host/build.gradle.kts` (single source of truth —
   the workflow reads the name from there).
2. Update `README.md` (what's new) and `.github/RELEASE_NOTES.md`.
3. Push to `main`, wait for the `Android` run to pass.
4. `gh workflow run android.yml -f release_tag=v<version>` and confirm the release assets.

## Versioning

* `PROTOCOL_VERSION` in `bridge-ipc` guards wire compatibility.
* `versionName` of the runtime and the SDK move together per release; the AAR assets are published
  with the same version in their file names.
* Backend API surfaces follow the upstream library versions they emulate (Anikku anime extensions-lib
  12–16, Mihon manga extensions-lib 1.4–1.6, CloudStream 4.9.1).
