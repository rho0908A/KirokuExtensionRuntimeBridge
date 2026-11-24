package io.kiroku.runtime.backend.cloudstream

import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * CloudStream plugin package handling.
 *
 * A `.cs3` file is a ZIP archive that must contain:
 *  - `classes.dex` — the compiled plugin, and
 *  - `manifest.json` — `{ "name", "pluginClassName", "requiresResources", "version" }`
 *    (the serialised `BasePlugin.Manifest` of the CloudStream API).
 *
 * Plugins are a completely different ecosystem from Aniyomi APKs: no package manager metadata, no
 * manifest features, no `<meta-data>`. They are validated against their own manifest instead.
 */
internal object CloudStreamPackage {

    private const val MAX_PLUGIN_BYTES = 64L * 1024 * 1024
    private const val MAX_ENTRIES = 4096

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    @Serializable
    data class Manifest(
        @SerialName("name") val name: String = "",
        @SerialName("pluginClassName") val pluginClassName: String = "",
        @SerialName("requiresResources") val requiresResources: Boolean = false,
        @SerialName("version") val version: Int = 0,
    )

    data class Extraction(
        val root: File,
        val dexFile: File,
        val manifest: Manifest,
        val resourcesDir: File?,
    )

    /** Extracts a `.cs3` into [targetDir] (which is cleared first), validating the archive. */
    fun extract(file: File, targetDir: File): Extraction {
        require(file.isFile) { "Plugin file does not exist: ${file.name}" }
        require(file.length() in 1..MAX_PLUGIN_BYTES) { "Unexpected plugin size: ${file.length()} bytes" }

        targetDir.deleteRecursively()
        if (!targetDir.mkdirs()) throw IOException("Could not create ${targetDir.absolutePath}")

        val canonicalRoot = targetDir.canonicalFile
        var entries = 0

        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ENTRIES) throw IOException("Plugin archive has too many entries")

                val name = entry.name.replace('\\', '/')
                if (name.startsWith("/") || name.contains("..")) {
                    throw IOException("Plugin archive contains an unsafe path: $name")
                }
                val out = File(canonicalRoot, name)
                if (!out.canonicalPath.startsWith(canonicalRoot.path + File.separator)) {
                    throw IOException("Plugin archive escapes its directory: $name")
                }

                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
            }
        }

        val dex = File(canonicalRoot, "classes.dex")
        if (!dex.isFile || dex.length() == 0L) {
            throw IOException("Plugin archive does not contain classes.dex")
        }

        val manifestFile = File(canonicalRoot, "manifest.json")
        if (!manifestFile.isFile) {
            throw IOException("Plugin archive does not contain manifest.json")
        }

        val manifest = try {
            json.decodeFromString(Manifest.serializer(), manifestFile.readText())
        } catch (e: Exception) {
            throw IOException("Plugin manifest.json is invalid: ${e.message}", e)
        }
        if (manifest.pluginClassName.isBlank()) {
            throw IOException("Plugin manifest does not declare pluginClassName")
        }

        val resources = listOf("resources", "assets")
            .map { File(canonicalRoot, it) }
            .firstOrNull { it.exists() }

        return Extraction(root = canonicalRoot, dexFile = dex, manifest = manifest, resourcesDir = resources)
    }

    fun readManifest(file: File): Manifest {
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "manifest.json") {
                    val text = zip.readBytes().toString(Charsets.UTF_8)
                    return json.decodeFromString(Manifest.serializer(), text)
                }
                zip.closeEntry()
            }
        }
        throw IOException("manifest.json not found in ${file.name}")
    }
}
