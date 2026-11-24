package io.kiroku.runtime.host.core

import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.ExtensionError

/** Maps any backend/runtime failure onto the wire error model. */
fun Throwable.toExtensionError(backend: BackendType? = null): ExtensionError {
    val (code, retryable) = when (this) {
        is io.kiroku.runtime.backend.BackendException -> code to retryable
        is kotlinx.coroutines.CancellationException -> ErrorCode.CANCELLED to false
        is java.io.IOException -> ErrorCode.NETWORK to true
        is SecurityException -> ErrorCode.PERMISSION_DENIED to false
        else -> ErrorCode.BACKEND_FAILURE to false
    }
    return ExtensionError(
        code = code,
        message = message ?: javaClass.simpleName,
        details = cause?.message,
        backend = backend,
        retryable = retryable,
    )
}
