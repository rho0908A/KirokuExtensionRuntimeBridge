package io.kiroku.runtime.host.core

import io.kiroku.runtime.backend.BackendException
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMappingTest {

    @Test
    fun `backend exceptions keep their code and retry flag`() {
        val error = BackendException(
            "repository is down",
            ErrorCode.REPOSITORY_UNREACHABLE,
            retryable = true,
        ).toExtensionError(BackendType.CLOUDSTREAM)

        assertEquals(ErrorCode.REPOSITORY_UNREACHABLE, error.code)
        assertTrue(error.retryable)
        assertEquals(BackendType.CLOUDSTREAM, error.backend)
    }

    @Test
    fun `io failures become retryable network errors`() {
        val error = java.io.IOException("offline").toExtensionError()

        assertEquals(ErrorCode.NETWORK, error.code)
        assertTrue(error.retryable)
    }

    @Test
    fun `unexpected failures are reported as backend failures`() {
        val error = IllegalStateException("unexpected").toExtensionError(BackendType.ANIYOMI)

        assertEquals(ErrorCode.BACKEND_FAILURE, error.code)
        assertFalse(error.retryable)
    }
}
