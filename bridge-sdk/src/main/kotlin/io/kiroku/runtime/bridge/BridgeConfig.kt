package io.kiroku.runtime.bridge

/**
 * Tunables for the runtime connection.
 *
 * Defaults are chosen for a local Binder hop (no network involved): extension work happens inside
 * the runtime process, so timeouts exist to detect a wedged runtime rather than slow I/O.
 */
data class BridgeConfig(
    /** How long to wait for the runtime service to bind. */
    val bindTimeoutMs: Long = 5_000,
    /** Timeout for fast control operations (ping, info, extension list, logs). */
    val controlTimeoutMs: Long = 15_000,
    /** Timeout for extension execution (search, details, stream extraction, installation). */
    val operationTimeoutMs: Long = 90_000,
    /** Reconnect automatically after process death. */
    val autoReconnect: Boolean = true,
    /** Initial delay before the first reconnect attempt; doubled on each failure. */
    val reconnectInitialDelayMs: Long = 500,
    /** Upper bound for the reconnect backoff. */
    val reconnectMaxDelayMs: Long = 30_000,
    /** Maximum number of automatic reconnect attempts before the bridge reports DISCONNECTED. */
    val maxReconnectAttempts: Int = 5,
    /** Tag used for SDK side logging. */
    val logTag: String = "KirokuBridge",
)
