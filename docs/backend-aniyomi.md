# Aniyomi backend

The backend keeps the name **Aniyomi** because that is the ecosystem its packages come from, but it
implements the APIs of the maintained hosts instead of the archived Aniyomi app:

| Package kind | API implemented | Feature | Library versions |
| --- | --- | --- | --- |
| Anime | **Anikku** (`komikku-app/anikku`) `eu.kanade.tachiyomi.animesource` | `tachiyomi.animeextension` | 12 – 16 |
| Manga | **Mihon** (`mihonapp/mihon`) `eu.kanade.tachiyomi.source` | `tachiyomi.extension` | 1.4, 1.5, 1.6 |

## What it loads

A package is accepted when:

* it is a readable APK,
* its manifest declares `tachiyomi.animeextension` (anime) or `tachiyomi.extension` (manga),
* it exposes the matching `*.class` metadata (several classes separated by `;`, a leading `.` is
  relative to the APK package) or `*.factory`,
* the library version is inside the range above — anime from `versionName` (`16.5.3` → `16`), manga
  from `tachiyomi.extension.lib` when present and otherwise from `versionName` (`1.6.1` → `1.6`),
* it requests no dangerous permissions.

## How it loads

1. `PackageManager.getPackageArchiveInfo` reads metadata straight from the file; `sourceDir` is set
   explicitly because Android 13+ requires it.
2. A child-first `PathClassLoader` is created with the APK plus the runtime classpath, so extension
   code sees `aniyomi-api` but keeps its own bundled dependencies.
3. The declared class is instantiated (no-arg constructor), or the factory's `createSources()` is
   called: `AnimeSource` / `AnimeSourceFactory` for anime, `Source` / `SourceFactory` for manga.
4. Each source becomes an `ExtensionSourceInfo` with its real capabilities; the extension's
   `capabilities.manga` flag tells clients which media type it serves.

## Why `aniyomi-api` exists

Neither Aniyomi nor Anikku publishes a usable `extensions-lib` artifact while extensions compile
against it with `compileOnly`, so the host must supply those classes at runtime. `aniyomi-api`
reproduces the package names, types and signatures of both maintained APIs:

* Anikku: `AnimeSource`/`AnimeCatalogueSource` (suspend methods with the RxJava `fetch*` fallbacks),
  `SAnime`/`SEpisode` interfaces with `create()`, `Video`, `Hoster`, `AnimesPage`,
  `AnimeFilterList` (a `List` delegate), `AnimeHttpSource`/`ParsedAnimeHttpSource`,
  season/hoster hooks, `eu.kanade.tachiyomi.util.awaitSingle`,
  `tachiyomi.core.common.util.lang.awaitSingle`, `logcat`, `sourcePreferences`.
* Mihon: `Source` (1.6 `getMangaUpdate`), `CatalogueSource`, `ConfigurableSource`,
  `SourceFactory`, `SManga`/`SChapter` (with `memo`), `Page`, `MangasPage`, `Filter`/`FilterList`,
  `HttpSource`/`ParsedHttpSource`.

Anikku's `Source`/`SManga` are typealiases to the anime types, so they add no class-level conflict
with Mihon's real `eu.kanade.tachiyomi.source.Source` interface. Both ecosystems therefore run in a
single process.

## Repositories

* **Mihon/Keiyoushi:** `repo.json` declares `index_v2` (`index.pb`, gzip + protobuf). The client
  decodes that index (name, package, APK URL, icon URL, lib version, version code/name, NSFW flag,
  source languages). `index.min.json` is only a placeholder on that repository and is not used when
  `index_v2` exists.
* **Aniyomi/Anikku:** the legacy `index.min.json` array with `apk/` and `icon/` folders.

## Operation mapping

| Bridge call | Anime (Anikku API) | Manga (Mihon API) |
| --- | --- | --- |
| `search` / `searchManga` | `getSearchAnime(page, query, filters)` | `getSearchManga(page, query, filters)` |
| `popular` / `popularManga` | `getPopularAnime(page)` | `getPopularManga(page)` |
| `latest` / `latestManga` | `getLatestUpdates(page)` (errors unless `supportsLatest`) | `getLatestUpdates(page)` |
| `details` / `mangaDetails` | `getAnimeDetails` + `getEpisodeList` | `getMangaUpdate(fetchDetails, fetchChapters)` |
| `episodes` / `chapters` | `getEpisodeList` | `getMangaUpdate(fetchDetails = false)` |
| `streams` / `pages` | `getVideoList` (direct, else `getHosterList`) | `getPageList(chapter)` |
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
