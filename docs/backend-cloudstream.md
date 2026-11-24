# CloudStream backend

## What it loads

`.cs3` files: ZIP archives produced by CloudStream's plugin build containing `classes.dex` and
`manifest.json` (`name`, `pluginClassName`, `requiresResources`, `version`). The plugin class extends
`BasePlugin` and is annotated with `@CloudstreamPlugin`.

## How it loads

1. The archive is extracted to `files/extensions/cloudstream/<pluginId>/` with zip-slip protection.
2. `classes.dex` is loaded with a `DexClassLoader` whose parent is the runtime loader, so plugins see
   the bundled CloudStream 4.9.1 API (`com.lagradost.cloudstream3.*`).
3. The class named in `manifest.json` is instantiated and `load()` is called.
4. CloudStream registers providers into `APIHolder.apis`; the loader snapshots that list before and
   after `load()` and keeps only the new entries, so each plugin owns exactly its own providers.
   Unloading reverses this (`beforeUnload()` plus mapping removal).

## Where the API comes from

`com.github.recloudstream.cloudstream:library-android:4.9.1` (JitPack) — the same artifact plugins
compile against. Its AAR declares `minCompileSdk=37`; this project builds with compileSdk 34 and sets
`android.experimental.disableCompileSdkChecks=true`, because only stable JVM-level types are
referenced (`MainAPI`, `SearchResponse*`, `LoadResponse*`, `ExtractorLink`, `SubtitleFile`,
`BasePlugin`, `APIHolder`).

## Operation mapping

| Bridge call | CloudStream API |
| --- | --- |
| `search` | `search(query, page)` → `SearchResponseList.items` |
| `popular` / `latest` | `getMainPage(page, MainPageRequest(...))`, errors when `hasMainPage == false` |
| `details` | `load(url)` |
| `episodes` | `load(url)` → `TvSeriesLoadResponse` / `AnimeLoadResponse` / `MovieLoadResponse` |
| `streams` | `loadLinks(data, false, onSubtitle, onLink)`, falling back to `loadExtractor` |

Episode ids are CloudStream's opaque `Episode.data` values and are passed back verbatim.

## Repository format

`repo.json` → `pluginLists`: URLs of `plugins.json` arrays. Each entry needs `name`/`internalName`,
`url` and `version`. Parsing is lenient (unknown keys ignored, optional keys defaulted); an
unreachable repository becomes `REPOSITORY_UNREACHABLE` instead of an empty catalogue. The built-in
Phisher repository publishes prebuilt `.cs3` files from its `builds` branch.

## Known limitations

* Plugins depending on host globals (`app`, `Context`, WebView helpers, host resources) may fail;
  reported as `EXTENSION_LOAD_FAILED` with the plugin class name.
* `requiresResources` plugins work only as far as identity resources resolve; plugin resources are
  not merged into the host.
* Magnet/torrent links are passed through as URLs; playback depends on the player.
* No plugin has been executed on a device yet.
