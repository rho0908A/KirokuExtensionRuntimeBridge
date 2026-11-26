package io.kiroku.runtime.backend.aniyomi

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/**
 * Minimal decoder for the `index.pb` extension index that the maintained repositories publish.
 *
 * Keiyoushi's `repo.json` now points at `index.pb` (`index_v2`) and its `index.min.json` only
 * contains two "update your app" placeholder entries, so reading the JSON index alone no longer
 * discovers real extensions. The protobuf layout, as produced by Mihon's index generator:
 *
 * ```
 * Index {
 *   string name = 1;
 *   string shortName = 2;
 *   string signingKeyFingerprint = 3;
 *   Links links = 4;
 *   repeated Extension extensions = 101;
 * }
 * Extension {
 *   string name = 1;
 *   string pkg = 2;
 *   Resources resources = 3;      // 1 = apkUrl, 2 = iconUrl
 *   string libVersion = 4;
 *   uint64 versionCode = 5;
 *   string versionName = 6;
 *   uint32 nsfw = 7;
 *   repeated Source sources = 8;  // 1 = id, 2 = name, 3 = lang, 4 = baseUrl
 * }
 * ```
 *
 * Only the fields the runtime needs are decoded; unknown fields are skipped, so a newer index
 * schema still parses.
 */
internal object ProtoIndex {

    data class Entry(
        val name: String,
        val pkg: String,
        val apkUrl: String,
        val iconUrl: String?,
        val libVersion: String?,
        val versionName: String,
        val versionCode: Long,
        val language: String?,
        val nsfw: Boolean,
    )

    private data class Field(val number: Int, val wireType: Int, val int: Long = 0, val bytes: ByteArray? = null)

    fun decode(raw: ByteArray): List<Entry> {
        val payload = gunzipIfNeeded(raw)
        return parseMessage(payload)
            .filter { it.number == 101 && it.wireType == 2 }
            .mapNotNull { field -> field.bytes?.let(::parseEntry) }
            .filter { it.pkg.isNotBlank() && it.apkUrl.isNotBlank() }
    }

    private fun parseEntry(bytes: ByteArray): Entry {
        var name = ""
        var pkg = ""
        var apkUrl = ""
        var iconUrl: String? = null
        var libVersion: String? = null
        var versionName = ""
        var versionCode = 0L
        var nsfw = 0
        val languages = LinkedHashSet<String>()

        parseMessage(bytes).forEach { field ->
            when (field.number) {
                1 -> name = field.text()
                2 -> pkg = field.text()
                3 -> parseMessage(field.bytes.orEmpty()).forEach { sub ->
                    when (sub.number) {
                        1 -> apkUrl = sub.text()
                        2 -> iconUrl = sub.text()
                    }
                }
                4 -> libVersion = field.text().takeIf { it.isNotBlank() }
                5 -> versionCode = field.int
                6 -> versionName = field.text()
                7 -> nsfw = field.int.toInt()
                8 -> parseMessage(field.bytes.orEmpty())
                    .firstOrNull { it.number == 3 }?.text()?.takeIf { it.isNotBlank() }?.let(languages::add)
            }
        }

        return Entry(
            name = name,
            pkg = pkg,
            apkUrl = apkUrl,
            iconUrl = iconUrl,
            libVersion = libVersion,
            versionName = versionName.ifBlank { libVersion.orEmpty() },
            versionCode = versionCode,
            language = when {
                languages.isEmpty() -> null
                languages.size == 1 -> languages.first()
                else -> "all"
            },
            // 3 = safe, 2 = mixed, 1 = NSFW in Mihon's content-warning enum.
            nsfw = nsfw != 3 && nsfw != 0,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Wire format
    // ---------------------------------------------------------------------------------------------

    private fun parseMessage(bytes: ByteArray): List<Field> {
        val fields = mutableListOf<Field>()
        var index = 0
        while (index < bytes.size) {
            val (key, afterKey) = readVarint(bytes, index)
            index = afterKey
            val number = (key ushr 3).toInt()
            val wireType = (key and 0x7).toInt()
            when (wireType) {
                0 -> {
                    val (value, next) = readVarint(bytes, index)
                    index = next
                    fields += Field(number, wireType, int = value)
                }
                2 -> {
                    val (length, next) = readVarint(bytes, index)
                    index = next
                    val end = index + length.toInt()
                    if (end > bytes.size) return fields
                    fields += Field(number, wireType, bytes = bytes.copyOfRange(index, end))
                    index = end
                }
                5 -> {
                    if (index + 4 > bytes.size) return fields
                    index += 4
                }
                1 -> {
                    if (index + 8 > bytes.size) return fields
                    index += 8
                }
                else -> return fields
            }
        }
        return fields
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var index = start
        while (index < bytes.size) {
            val byte = bytes[index].toInt()
            index++
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result to index
            shift += 7
            if (shift > 63) break
        }
        return result to index
    }

    private fun Field.text(): String = bytes?.toString(Charsets.UTF_8).orEmpty()

    private fun gunzipIfNeeded(raw: ByteArray): ByteArray {
        if (raw.size < 2 || raw[0] != 0x1f.toByte() || raw[1] != 0x8b.toByte()) return raw
        return GZIPInputStream(raw.inputStream()).use { input ->
            val output = ByteArrayOutputStream()
            input.copyTo(output)
            output.toByteArray()
        }
    }
}
