package io.kiroku.runtime.backend.aniyomi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The protobuf index has two layouts in the wild: field 101 holds one wrapper message whose field 1
 * repeats each extension (Keiyoushi, Mihon), or it holds a single extension directly. Reading the
 * wrapper as an entry is what made a whole repository list zero extensions.
 */
class ProtoIndexTest {

    @Test
    fun readsExtensionsFromTheWrappedList() {
        val wrapper = field(1, extension("Achy", "pkg.achy", "https://repo/a.apk", "1.6", "1.6.1")) +
            field(1, extension("Bara", "pkg.bara", "https://repo/b.apk", "1.6", "1.6.2"))
        val entries = ProtoIndex.decode(field(101, wrapper))

        assertEquals(2, entries.size)
        assertEquals("Achy", entries[0].name)
        assertEquals("pkg.achy", entries[0].pkg)
        assertEquals("https://repo/a.apk", entries[0].apkUrl)
        assertEquals("1.6", entries[0].libVersion)
        assertEquals("1.6.1", entries[0].versionName)
        assertEquals(106001L, entries[0].versionCode)
        assertEquals("es", entries[0].language)
        assertEquals("pkg.bara", entries[1].pkg)
    }

    @Test
    fun readsAnExtensionStoredDirectlyAtTheListField() {
        val entries = ProtoIndex.decode(field(101, extension("Solo", "pkg.solo", "https://repo/s.apk", "1.5", "1.5.4")))

        assertEquals(1, entries.size)
        assertEquals("pkg.solo", entries[0].pkg)
        assertTrue(entries[0].nsfw)
    }

    @Test
    fun ignoresAnIndexWithoutExtensions() {
        val header = text(1, "Keiyoushi") + text(2, "KEI")
        assertTrue(ProtoIndex.decode(header).isEmpty())
    }

    private fun extension(name: String, pkg: String, apk: String, libVersion: String, version: String): ByteArray =
        text(1, name) +
            text(2, pkg) +
            field(3, text(1, apk)) +
            text(4, libVersion) +
            number(5, 106001) +
            text(6, version) +
            number(7, 1) +
            field(8, number(1, 42) + text(2, name) + text(3, "es") + text(4, "https://site"))

    private fun field(fieldNumber: Int, payload: ByteArray): ByteArray =
        varint((fieldNumber shl 3) or 2) + varint(payload.size) + payload

    private fun text(fieldNumber: Int, value: String): ByteArray = field(fieldNumber, value.toByteArray())

    private fun number(fieldNumber: Int, value: Long): ByteArray =
        varint(fieldNumber shl 3) + varint(value)

    private fun varint(value: Int): ByteArray = varint(value.toLong())

    private fun varint(input: Long): ByteArray {
        var value = input
        val out = ArrayList<Byte>()
        while (true) {
            val byte = (value and 0x7F).toInt()
            value = value ushr 7
            if (value == 0L) {
                out += byte.toByte()
                return out.toByteArray()
            }
            out += (byte or 0x80).toByte()
        }
    }
}
