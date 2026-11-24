# Troubleshooting

## `RUNTIME_NOT_INSTALLED` although the runtime is installed

Android 11+ package visibility. Add to the host app's manifest:

```xml
<queries><package android:name="io.kiroku.runtime.host" /></queries>
```

## `PERMISSION_DENIED`

The player is signed with a different key than the runtime. Open
**Kiroku Runtime → Extension Manager → Client access** and allow the player's package name.

## `INCOMPATIBLE`

The SDK and runtime were built from different protocol versions. Install both from the same release.

## Extensions list is empty

1. Open **Repositories** and check the built-in indexes are enabled.
2. Tap **Load catalogue** / *Update plugin*; check **Logs** for `REPOSITORY_UNREACHABLE`.
3. GitHub raw URLs are rate-limited and blocked in some networks; add a mirror index if needed.

## `EXTENSION_INVALID_PACKAGE`

The validation reason is in the error details:

| Detail | Cause |
| --- | --- |
| missing the `tachiyomi.animeextension` feature | not an Aniyomi anime extension (manga extensions are refused) |
| library outside 12..17 | `versionName` does not start with a supported library version |
| dangerous permissions | APK asks for permissions the runtime will not grant |
| no `manifest.json` | `.cs3` without a plugin manifest |
| unsafe archive path | plugin archive with absolute or `..` entries |

## `EXTENSION_LOAD_FAILED`

Usually a missing class. Check **Logs** for the class name:

* Aniyomi: the extension reflects on Aniyomi app internals (`App`, preferences, WebView helpers).
* CloudStream: the plugin needs host globals or resources the runtime does not provide.

Reinstall the extension; if the failure repeats with the same class, the extension is not compatible
with the runtime's API surface.

## Playback starts but nothing plays

The runtime returns the URL the extension produced. Empty or expired URLs are the extension's or the
host's problem, not the bridge's: verify the same URL in a browser and check the `Referer` header is
forwarded to the player.

## Binder death / reconnect loops

`Logs` shows a disconnect. The SDK rebinds up to 5 times; if it keeps failing, the runtime was
force-stopped or the OS reclaimed it. Run the runtime in a foreground service if this happens
frequently on low-memory devices.

## On-device verification checklist

Not yet performed for this repository. Run it before trusting a release:

1. Install runtime + `sample-host`; connect and read `getRuntimeInfo()`.
2. Install one real Aniyomi extension and one real `.cs3` plugin from the built-in repositories.
3. Search, open details, list episodes, extract streams; confirm the URL plays in an external player.
4. Kill the runtime process mid-session; confirm the SDK rebinds and the next call succeeds.
5. Revoke a package from the allow-list; confirm `PERMISSION_DENIED`.
