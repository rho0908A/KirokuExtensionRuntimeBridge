# Aniyomi backend

## What it loads

Aniyomi **anime** extension APKs (manga extensions are rejected). A package is accepted when:

* it is a readable APK,
* its manifest declares the `tachiyomi.animeextension` feature,
* it exposes `tachiyomi.animeextension.class` (possibly several, leading `.` = relative to the APK
  package) or `tachiyomi.animeextension.factory`,
* the library version derived from `versionName` is within **12..17**,
* it requests no dangerous permissions.

## How it loads

1. `PackageManager.getPackageArchiveInfo` reads metadata straight from the file; `sourceDir` is set
   explicitly because Android 13+ requires it.
2. A child-first `PathClassLoader` is created with the APK plus the runtime classpath, so extension
   code sees `aniyomi-api` but keeps its own bundled dependencies.
3. The declared class is instantiated (no-arg constructor), or the factory's `createSources()` is
   called.
4. Each source becomes an `ExtensionSourceInfo`; `AnimeCatalogueSource` additionally enables
   search/popular/latest.

## Why `aniyomi-api` exists

Aniyomi publishes no usable `extensions-lib` artifact on a public Maven repo (the JitPack build
produces an empty root jar) while extensions compile against it with `compileOnly`. The host must
therefore supply those classes at runtime. `aniyomi-api` reproduces the package names, types and
signatures for lib 12–17, plus minimal helpers (`AnimeHttpSource`, `NETWORK_CLIENT`,
`ChildFirstPathClassLoader`, a small Injekt shim). It contains no extension logic.

## Operation mapping

| Bridge call | Aniyomi API |
| --- | --- |
| `search` | `getSearchAnime(page, query, filters)` |
| `popular` | `getPopularAnime(page)` |
| `latest` | `getLatestUpdates(page)` (errors unless `supportsLatest`) |
| `details` | `getAnimeDetails` + `getEpisodeList` |
| `episodes` | `getEpisodeList` |
| `streams` | `getVideoList(episode)`, then `getHosterList` + `getVideoList(hoster)` when empty |

`Video` → `StreamSource` keeps URL, quality, title, headers and subtitle/audio tracks. The bridge
never invents a stream: an empty result set is reported as such, never as a fake link.

## Known limitations

* Manga extensions are refused (no manga API here).
* Extensions reflecting on Aniyomi app internals (`App`, preference UI, WebView interceptors) may
  fail; they surface as `EXTENSION_LOAD_FAILED` with the class name.
* Library versions outside 12..17 are refused rather than loaded optimistically.
* Validated against `yuzono/anime-extensions` sources (lib 16) and its repo index; no extension has
  been executed on a device yet.
