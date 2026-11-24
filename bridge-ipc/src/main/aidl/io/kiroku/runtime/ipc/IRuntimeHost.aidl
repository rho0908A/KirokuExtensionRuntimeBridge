package io.kiroku.runtime.ipc;

import io.kiroku.runtime.ipc.IRuntimeCallback;

/**
 * Stable Binder contract exposed by the Kiroku Runtime Host.
 *
 * The surface is deliberately small and versioned by {@link #getProtocolDescriptor()}:
 *  - one synchronous, JSON-in/JSON-out request channel (`execute`), and
 *  - one event channel (`registerCallback`) for loading / execution status.
 *
 * Keeping the transaction payload JSON-encoded means the AIDL contract does not have to change
 * every time a model gains a field, which matters for an SDK that third-party applications ship.
 */
interface IRuntimeHost {

    /**
     * Returns a JSON document describing the runtime, its protocol version and supported backends.
     * Safe to call before any other operation and used for compatibility checks.
     */
    String getProtocolDescriptor();

    /**
     * Executes a single JSON `RpcRequest` and returns a JSON `RpcResponse`.
     * Implementations must never throw across Binder; failures are encoded in the response.
     */
    String execute(String requestJson);

    /** Registers a callback that receives JSON-encoded runtime events. */
    void registerCallback(IRuntimeCallback callback);

    /** Removes a previously registered callback. */
    void unregisterCallback(IRuntimeCallback callback);
}
