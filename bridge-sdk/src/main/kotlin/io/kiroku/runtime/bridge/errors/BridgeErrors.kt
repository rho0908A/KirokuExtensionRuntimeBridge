package io.kiroku.runtime.bridge.errors

import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.ExtensionError

/**
 * Exception thrown by the SDK whenever the runtime reports a failure or the transport fails.
 *
 * Host applications are expected to catch this type and branch on [code]; the runtime never returns
 * fabricated empty results for a failed operation.
 */
class ExtensionBridgeException(
    val code: ErrorCode,
    override val message: String,
    val details: String? = null,
    val extensionId: String? = null,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : Exception(message, cause) {

    fun toError(): ExtensionError = ExtensionError(
        code = code,
        message = message,
        details = details,
        extensionId = extensionId,
        retryable = retryable,
    )

    companion object {
        fun from(error: ExtensionError, cause: Throwable? = null): ExtensionBridgeException =
            ExtensionBridgeException(
                code = error.code,
                message = error.message,
                details = error.details,
                extensionId = error.extensionId,
                retryable = error.retryable,
                cause = cause,
            )

        fun notConnected(): ExtensionBridgeException = ExtensionBridgeException(
            code = ErrorCode.NOT_CONNECTED,
            message = "The extension runtime is not connected. Call connect() first.",
            retryable = true,
        )

        fun runtimeMissing(): ExtensionBridgeException = ExtensionBridgeException(
            code = ErrorCode.RUNTIME_NOT_INSTALLED,
            message = "The Kiroku extension runtime is not installed on this device.",
        )

        fun timedOut(operation: String, timeoutMs: Long): ExtensionBridgeException = ExtensionBridgeException(
            code = ErrorCode.TIMEOUT,
            message = "Runtime did not answer '$operation' within ${timeoutMs}ms.",
            details = "The runtime process may be busy or unresponsive.",
            retryable = true,
        )
    }
}

/** Human readable guidance for an error code, suitable for direct display in a host UI. */
fun ErrorCode.userMessage(): String = when (this) {
    ErrorCode.RUNTIME_NOT_INSTALLED -> "The runtime is not installed."
    ErrorCode.RUNTIME_INCOMPATIBLE -> "The installed runtime version is not compatible with this app."
    ErrorCode.PROTOCOL_MISMATCH -> "The runtime speaks a different bridge protocol."
    ErrorCode.PERMISSION_DENIED -> "This app is not allowed to use the runtime."
    ErrorCode.TIMEOUT -> "The runtime took too long to respond."
    ErrorCode.NOT_CONNECTED -> "The runtime is not connected."
    ErrorCode.INVALID_REQUEST -> "The request sent to the runtime was invalid."
    ErrorCode.UNKNOWN_METHOD -> "The runtime does not implement this operation."
    ErrorCode.EXTENSION_NOT_FOUND -> "The extension is not installed."
    ErrorCode.EXTENSION_ALREADY_INSTALLED -> "The extension is already installed."
    ErrorCode.EXTENSION_LOAD_FAILED -> "The extension could not be loaded."
    ErrorCode.EXTENSION_UNSUPPORTED -> "The extension is not supported by the runtime."
    ErrorCode.EXTENSION_INVALID_PACKAGE -> "The extension package is invalid."
    ErrorCode.SOURCE_NOT_FOUND -> "The requested source does not exist in this extension."
    ErrorCode.REPOSITORY_INVALID -> "The repository index is invalid."
    ErrorCode.REPOSITORY_UNREACHABLE -> "The repository could not be reached."
    ErrorCode.NETWORK -> "A network request made by the extension failed."
    ErrorCode.BACKEND_FAILURE -> "The extension backend reported a failure."
    ErrorCode.STORAGE -> "The runtime could not read or write extension storage."
    ErrorCode.CANCELLED -> "The operation was cancelled."
    ErrorCode.UNKNOWN -> "An unknown runtime error occurred."
}
