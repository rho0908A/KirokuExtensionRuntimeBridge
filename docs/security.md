# Security model

## Trust boundary

Extensions are untrusted code. The runtime is the only component that loads them, and it does so in
its own process, so a crashing or hostile extension cannot take down the player.

## Caller authorisation

The service is exported, and that is intentional: the player is a different app. Access is controlled
by `RuntimeHostService` on every call:

1. Caller UID → package names via `PackageManager`.
2. Accept if any package shares the runtime's signing certificate (`checkSignatures`), or if the
   package is in the runtime allow-list (`RuntimeSettings.allowedClients`, edited in the Extension
   Manager).
3. Otherwise reject with `PERMISSION_DENIED`.

A signature-only `<permission android:protectionLevel="signature">` was rejected as the sole control
because it would make the runtime unusable for independently signed players; the allow-list keeps an
explicit, user-visible decision instead.

## Package validation before loading

* Aniyomi: feature/metadata checks, library version 12..17, dangerous permissions refused, size cap.
* CloudStream: archive entry paths validated (no `..`, no absolute paths), size and entry-count caps,
  `manifest.json` required, plugin class must resolve.

Rejected packages are never loaded; the reason is returned as `EXTENSION_INVALID_PACKAGE` with details.

## Process and class isolation

* Aniyomi extensions load through a child-first `PathClassLoader`; their bundled OkHttp/Jsoup win over
  the host's.
* CloudStream plugins load through a `DexClassLoader` against the bundled 4.9.1 API.

This is class-loader isolation, **not** a security sandbox: loaded code runs with the runtime's UID
and therefore its network, storage and package-visibility rights. Do not install extensions you do
not trust.

## Secrets and data

The runtime holds no API keys or accounts. It stores only installed packages, repository URLs,
settings and an in-memory log buffer; nothing is uploaded anywhere. Extension requests go directly
from the device to the extension's own hosts.

## Reporting

If you find a vulnerability, open a private advisory on the repository rather than a public issue.
