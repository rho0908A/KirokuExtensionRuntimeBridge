package io.kiroku.runtime.bridge.connection

import io.kiroku.runtime.ipc.RuntimeProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolCompatibilityTest {

    @Test
    fun `runtime speaking the current protocol is accepted`() {
        assertTrue(RuntimeAvailability.isProtocolCompatible(RuntimeProtocol.PROTOCOL_VERSION))
    }

    @Test
    fun `other protocol versions are rejected`() {
        assertFalse(RuntimeAvailability.isProtocolCompatible(RuntimeProtocol.PROTOCOL_VERSION + 1))
        assertFalse(RuntimeAvailability.isProtocolCompatible(RuntimeProtocol.PROTOCOL_VERSION - 1))
    }

    @Test
    fun `the sdk reports the protocol it implements`() {
        assertEquals(RuntimeProtocol.PROTOCOL_VERSION, RuntimeAvailability.sdkProtocolVersion())
    }
}
