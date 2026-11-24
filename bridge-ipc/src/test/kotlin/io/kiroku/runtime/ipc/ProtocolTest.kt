package io.kiroku.runtime.ipc

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    @Test
    fun `request round trips with typed params`() {
        val request = RpcRequest(
            id = "abc",
            method = RuntimeMethods.SEARCH,
            params = jsonOf(
                RuntimeParams.EXTENSION_ID to "eu.kanade.test",
                RuntimeParams.PAGE to 3,
                RuntimeParams.QUERY to "one piece",
            ),
        )

        val decoded = RpcRequest.decode(request.encode())

        assertEquals("abc", decoded.id)
        assertEquals(RuntimeMethods.SEARCH, decoded.method)
        assertEquals("eu.kanade.test", decoded.string(RuntimeParams.EXTENSION_ID))
        assertEquals(3, decoded.int(RuntimeParams.PAGE))
        assertEquals("one piece", decoded.string(RuntimeParams.QUERY))
    }

    @Test
    fun `unknown fields are ignored so newer runtimes stay compatible`() {
        val raw = """{"id":"1","method":"runtime.ping","params":{},"futureField":true}"""

        val decoded = RpcRequest.decode(raw)

        assertEquals("runtime.ping", decoded.method)
        assertEquals("1", decoded.id)
    }

    @Test
    fun `failed response carries a structured error`() {
        val response = RpcResponse.failure(
            "7",
            ExtensionError(
                code = ErrorCode.EXTENSION_LOAD_FAILED,
                message = "boom",
                backend = BackendType.ANIYOMI,
            ),
        )

        val decoded = RpcResponse.decode(response.encode())

        assertFalse(decoded.ok)
        assertNull(decoded.result)
        assertEquals(ErrorCode.EXTENSION_LOAD_FAILED, decoded.error?.code)
        assertEquals(BackendType.ANIYOMI, decoded.error?.backend)
    }

    @Test
    fun `extension lists keep their backend discriminator`() {
        val extension = Extension(
            id = "eu.kanade.test",
            metadata = ExtensionMetadata(
                name = "Test",
                packageName = "eu.kanade.test",
                versionName = "16.1",
                versionCode = 161,
            ),
            backend = BackendType.ANIYOMI,
            isInstalled = true,
        )

        val raw = RuntimeProtocol.JSON.encodeToString(ListSerializer(Extension.serializer()), listOf(extension))
        val decoded = RuntimeProtocol.JSON.decodeFromString(ListSerializer(Extension.serializer()), raw)

        assertEquals(BackendType.ANIYOMI, decoded.single().backend)
        assertEquals(161L, decoded.single().metadata.versionCode)
        assertTrue(decoded.single().isInstalled)
    }

    @Test
    fun `events round trip with their payload`() {
        val event = RuntimeEvent.of(
            RuntimeEventType.INSTALL_PROGRESS,
            jsonOf(RuntimeParams.EXTENSION_ID to "x", "phase" to "downloading"),
        )

        val decoded = RuntimeEvent.decode(event.encode())

        assertEquals(RuntimeEventType.INSTALL_PROGRESS, decoded.type)
        assertEquals("downloading", decoded.payload["phase"]?.jsonPrimitive?.content)
    }
}
