# Communication protocol

## Transport

A bound Android service. The AIDL surface is small on purpose:

```aidl
interface IRuntimeHost {
    String getProtocolDescriptor();       // RuntimeInfo as JSON
    String execute(String requestJson);   // RpcRequest -> RpcResponse
    void registerCallback(IRuntimeCallback callback);
    void unregisterCallback(IRuntimeCallback callback);
}
interface IRuntimeCallback { void onEvent(String eventJson); }
```

## Envelopes

```json
{ "id": "uuid", "method": "sources.search",
  "params": { "extensionId": "…", "query": "…", "page": 1 } }

{ "id": "uuid", "ok": true, "result": [ … ] }

{ "id": "uuid", "ok": false,
  "error": { "code": "EXTENSION_LOAD_FAILED", "message": "…", "retryable": false } }
```

Rules:

* `id` is client generated and echoed verbatim.
* `result` is the raw payload; the SDK decodes it into the concrete model type.
* Unknown fields are ignored on both sides (`ignoreUnknownKeys = true`), so adding fields is not a
  protocol break.
* Removing a method or changing the envelope requires a `PROTOCOL_VERSION` bump; the SDK refuses a
  runtime with a different version and reports `INCOMPATIBLE`.

## Methods

| Method | Params | Result |
| --- | --- | --- |
| `runtime.ping` | — | `{ protocolVersion, runtimePackage }` |
| `runtime.info` | — | `RuntimeInfo` |
| `runtime.logs` | `limit` | `LogEntry[]` |
| `runtime.clearLogs` | — | `true` |
| `extensions.list` | — | `Extension[]` |
| `extensions.available` | — | `RepositoryExtension[]` |
| `extensions.install` | `extensionId` | `Extension` |
| `extensions.installFile` | `filePath` | `Extension` |
| `extensions.update` | `extensionId` | `Extension` |
| `extensions.uninstall` | `extensionId` | `Boolean` |
| `extensions.reload` | — | `Extension[]` |
| `repositories.list` | — | `ExtensionRepository[]` |
| `repositories.add` | `url`, `backend`, `name?` | `ExtensionRepository` |
| `repositories.remove` | `repositoryId` | `Boolean` |
| `repositories.refresh` | — | `RepositoryExtension[]` |
| `sources.search` | `extensionId`, `query`, `sourceId?`, `page?` | `Anime[]` |
| `sources.popular` | `extensionId`, `sourceId?`, `page?` | `Anime[]` |
| `sources.latest` | `extensionId`, `sourceId?`, `page?` | `Anime[]` |
| `sources.details` | `extensionId`, `animeId`, `sourceId?` | `AnimeDetails` |
| `sources.episodes` | `extensionId`, `animeId`, `sourceId?` | `Episode[]` |
| `sources.streams` | `extensionId`, `episodeId`, `sourceId?` | `StreamSource[]` |

`details`, `episodes` and `streams` take the extension's URL in the `animeId`/`episodeId` fields —
that URL is the identifier both ecosystems use.

## Events

Pushed through `IRuntimeCallback.onEvent` to registered clients only:

| Type | Payload |
| --- | --- |
| `extension.installProgress` | `extensionId`, `backend`, `phase`, `bytesDownloaded`, `totalBytes` |
| `extension.loaded` | `extensionId`, `name` |
| `extension.failed` | `extensionId`, `message` |
| `extension.installed` / `extension.updated` | `extensionId`, `backend` |
| `extension.uninstalled` | `extensionId` |

Events are best effort (bounded ring buffer). Authoritative state is always re-read with
`extensions.list`, never reconstructed from events.

## Timeouts and recovery

* Bind: 5 s (`BridgeConfig.bindTimeoutMs`)
* Control calls: 15 s
* Extension work: 90 s
* On `DeadObjectException` / `onBindingDied` the SDK rebinds (5 attempts, backoff 500 ms → 30 s).
