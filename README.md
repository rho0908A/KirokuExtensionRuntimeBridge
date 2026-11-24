# KirokuExtensionRuntimeBridge

An Android-only runtime that executes **Aniyomi** (APK) and **CloudStream** (`.cs3`) extensions
outside the host application, plus a small SDK any Kotlin app can use to talk to it.

The bridge is independent of Kiroku (or any other player). Kiroku is simply the first adopter.

```
 Host app (Kotlin / Compose)                 Runtime Host APK
 ┌────────────────────────────┐   Binder    ┌──────────────────────────────┐
 │ :bridge-sdk                │ ──────────▶ │ :runtime-host                │
 │  ExtensionBridge           │  JSON RPC   │  RuntimeHostService (AIDL)   │
 │  KirokuRuntimeBridge       │ ◀────────── │  RuntimeManager              │
 └────────────────────────────┘   events    │  :backend-aniyomi  :backend-cloudstream
                                            └──────────────────────────────┘
```

## Modules

| Module | Output | Purpose |
| --- | --- | --- |
| `bridge-ipc` | AAR | AIDL contract, protocol constants, unified models |
| `bridge-sdk` | AAR | Public SDK: `connect`, `getExtensions`, `search`, `getDetails`, `getEpisodes`, `extractStreams`, events, reconnect |
| `backend-api` | AAR | `ExtensionBackend` abstraction shared by ecosystems |
| `aniyomi-api` | AAR | Host-side Aniyomi `extensions-lib` API surface (lib 12–17) |
| `backend-aniyomi` | AAR | Loads Aniyomi APK extensions, executes anime sources |
| `backend-cloudstream` | AAR | Loads `.cs3` plugins against the CloudStream 4.9.1 library |
| `runtime-host` | APK | The runtime service + Extension Manager UI |
| `sample-host` | APK | Reference integration using only the SDK |

Everything is Kotlin + Gradle Kotlin DSL + native Android APIs. There is no Dart, React Native,
Node, Python or web runtime anywhere in this project; the Python files you may have seen live in
the *upstream extension repositories* (`yuzono/anime-extensions`) and are only their CI helpers for
publishing extension indexes.

## Status (read this before trusting it)

* The library modules (`bridge-ipc`, `bridge-sdk`, `backend-api`, `aniyomi-api`,
  `backend-aniyomi`, `backend-cloudstream`) and their unit tests have been compiled. The app modules
  (`runtime-host`, `sample-host`) and the unit tests have **not** been built to completion yet; the
  CI workflow in `.github/workflows/android.yml` is the first place that will.
* **No device or emulator testing has been performed.** Extension loading, playback and the Binder
  path are implemented against the real APIs but are not yet verified on hardware. Treat every
  runtime claim as unverified until the checklist in `docs/troubleshooting.md` has been run.
* CloudStream's library AAR declares `minCompileSdk=37`. This project compiles against 34 and
  disables that metadata check (`android.experimental.disableCompileSdkChecks`); it binds only to
  stable JVM-level CloudStream classes.
* Aniyomi manga extensions are not supported: the runtime ships an anime API only.

## Quick start

Gradle 8.7 and JDK 17 are expected. `gradle wrapper` generates `./gradlew` if you prefer the
wrapper; the CI workflow sets Gradle up itself.

```bash
# Build everything
gradle :runtime-host:assembleDebug :sample-host:assembleDebug :bridge-sdk:assembleRelease

# Install both apps (same signing key, so the runtime accepts the sample)
adb install -r runtime-host/build/outputs/apk/debug/runtime-host-debug.apk
adb install -r sample-host/build/outputs/apk/debug/sample-host-debug.apk
```

The Runtime Host ships with two real repositories enabled:

* `yuzono/anime-repo` — Aniyomi anime extensions (API 12–17)
* `phisher98/cloudstream-extensions-phisher` — prebuilt CloudStream `.cs3` plugins

## Documentation

* [Architecture](docs/architecture.md)
* [Installation](docs/installation.md)
* [SDK integration](docs/sdk-integration.md)
* [Aniyomi backend](docs/backend-aniyomi.md) · [CloudStream backend](docs/backend-cloudstream.md)
* [Communication protocol](docs/communication.md) · [API reference](docs/api-reference.md)
* [Security model](docs/security.md)
* [Troubleshooting](docs/troubleshooting.md) · [Building & CI](docs/building.md) ·
  [Adding a backend](docs/future-backends.md)

## License

Apache-2.0. Aniyomi and CloudStream remain the property of their respective authors; this project
ships no extension code and no content.
