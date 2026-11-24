# Installation

## Requirements

* Android 8.0 (API 26) or newer
* Runtime Host APK and the host application installed side by side

## Install the runtime

```bash
adb install -r runtime-host/build/outputs/apk/debug/runtime-host-debug.apk
```

Package `io.kiroku.runtime.host`, service `io.kiroku.runtime.host.service.RuntimeHostService`,
action `io.kiroku.runtime.host.BIND_RUNTIME`.

## First run

1. Open **Kiroku Runtime**.
2. The Extension Manager card shows *Plugin Installed* and the runtime version.
3. **Extensions → Load catalogue** fetches both built-in repositories (real public indexes; nothing
   is bundled or invented).
4. **Repositories** lets you add any other Aniyomi or CloudStream index URL.

## Client access

The runtime accepts a caller when it is

* signed with the same certificate (apps built from this repository in one Gradle run share the
  debug keystore), or
* listed in **Extension Manager → Client access → Allow Client App** by package name.

## Storage

| Path | Contents |
| --- | --- |
| `files/extensions/aniyomi/` | Installed Aniyomi APK packages |
| `files/extensions/cloudstream/` | Installed `.cs3` plugins |
| `files/backups/` | Previous packages kept for **Rollback** |
| `cache/aniyomi-downloads/`, `cache/cloudstream-downloads/` | Temporary downloads |

Uninstalling the runtime removes installed extensions. The runtime is a normal app: no root, no
system partition, no privileged permissions.

## Network

Extension hosts are arbitrary and many Aniyomi sources still serve plain HTTP, so cleartext traffic
is permitted. Tighten the network security configuration if your deployment is HTTPS-only.
