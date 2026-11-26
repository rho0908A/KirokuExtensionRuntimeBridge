package io.kiroku.runtime.backend.aniyomi

import io.kiroku.runtime.backend.BackendException
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.ExtensionRepository
import io.kiroku.runtime.ipc.RepositoryExtension
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

/**
 * Repository client for both index formats in use today.
 *
 *  - Mihon/Keiyoushi: `<repo>/repo.json` describes the store and points at `index_v2` (`index.pb`,
 *    a gzip compressed protobuf). The JSON `index.min.json` is now only a two-entry placeholder
 *    telling users to update the app, so it must not be used as the catalogue.
 *  - Aniyomi/Anikku anime repositories: a plain `index.min.json` array, `apk/` and `icon/` folders.
 *
 * The client picks the descriptor when present and falls back to the legacy JSON index, so both
 * ecosystems keep working.
 */
internal class AniyomiRepositoryClient(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun indexUrl(repository: ExtensionRepository): String {
        val url = repository.url.trimEnd('/')
        return when {
            url.endsWith(".json") || url.endsWith(".pb") -> url
            else -> "$url/index.min.json"
        }
    }

    fun baseUrl(repository: ExtensionRepository): String =
        indexUrl(repository).substringBeforeLast('/')

    suspend fun fetch(repository: ExtensionRepository): List<RepositoryExtension> = withContext(Dispatchers.IO) {
        val url = repository.url.trimEnd('/')

        // Direct protobuf index.
        if (url.endsWith(".pb")) {
            return@withContext parseIndexBytes(repository, get(url))
        }

        // Directory URL: Mihon/Keiyoushi publishes a descriptor that points at index_v2.
        if (!url.endsWith(".json")) {
            val descriptor = getOrNull("$url/repo.json")?.toString(Charsets.UTF_8)
            val indexV2 = indexV2Of(descriptor)
            if (indexV2 != null) {
                return@withContext parseIndexBytes(repository, get(resolve(url, indexV2)))
            }
        }

        val body = get(indexUrl(repository))
        val text = body.toString(Charsets.UTF_8)
        when {
            text.trimStart().startsWith("[") -> parseLegacyJson(repository, text)

            text.trimStart().startsWith("{") -> {
                val indexV2 = indexV2Of(text)
                    ?: throw BackendException(
                        "Repository ${repository.name} does not declare an index",
                        ErrorCode.REPOSITORY_INVALID,
                    )
                parseIndexBytes(repository, get(resolve(baseUrl(repository), indexV2)))
            }

            else -> parseIndexBytes(repository, body)
        }
    }

    private fun indexV2Of(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            json.parseToJsonElement(text).jsonObject["index_v2"]?.jsonPrimitive?.contentOrNullSafe()
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun parseLegacyJson(repository: ExtensionRepository, text: String): List<RepositoryExtension> {
        val entries = try {
            json.decodeFromString(ListSerializerHolder.indexList, text)
        } catch (e: Exception) {
            throw BackendException(
                "Repository ${repository.name} returned an invalid index: ${e.message}",
                ErrorCode.REPOSITORY_INVALID,
                e,
            )
        }
        val base = baseUrl(repository)
        return entries.mapNotNull { entry ->
            if (entry.pkg.isBlank() || entry.apk.isBlank()) return@mapNotNull null
            if (!AniyomiPackage.isSupportedLibVersion(entry.version)) return@mapNotNull null
            RepositoryExtension(
                repositoryId = repository.id,
                backend = BackendType.ANIYOMI,
                id = entry.pkg,
                name = entry.name.removePrefix("Aniyomi: ").removePrefix("Tachiyomi: ").trim(),
                versionName = entry.version,
                versionCode = entry.code,
                language = entry.lang.takeIf { it.isNotBlank() },
                apkUrl = "$base/apk/${entry.apk}",
                iconUrl = "$base/icon/${entry.pkg}.png",
                libVersion = AniyomiPackage.libVersion(entry.version),
                isNsfw = entry.nsfw == 1,
            )
        }
    }

    private fun parseIndexBytes(repository: ExtensionRepository, bytes: ByteArray): List<RepositoryExtension> {
        val entries = try {
            ProtoIndex.decode(bytes)
        } catch (e: Exception) {
            throw BackendException(
                "Repository ${repository.name} returned an invalid index.pb: ${e.message}",
                ErrorCode.REPOSITORY_INVALID,
                e,
            )
        }
        return entries.mapNotNull { entry ->
            val libVersion = entry.libVersion
            val supported = AniyomiPackage.isSupportedLibVersion(entry.versionName) ||
                AniyomiPackage.isMangaLibVersionSupported(libVersion)
            if (!supported) return@mapNotNull null
            RepositoryExtension(
                repositoryId = repository.id,
                backend = BackendType.ANIYOMI,
                id = entry.pkg,
                name = entry.name.removePrefix("Aniyomi: ").removePrefix("Tachiyomi: ").trim(),
                versionName = entry.versionName,
                versionCode = entry.versionCode,
                language = entry.language,
                apkUrl = entry.apkUrl,
                iconUrl = entry.iconUrl,
                libVersion = libVersion ?: AniyomiPackage.libVersion(entry.versionName),
                isNsfw = entry.nsfw,
            )
        }
    }

    private fun get(url: String): ByteArray {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, application/octet-stream, */*")
            .build()
        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw BackendException(
                        "Repository request to $url failed with HTTP ${response.code}",
                        ErrorCode.REPOSITORY_UNREACHABLE,
                        retryable = response.code >= 500,
                    )
                }
                response.body?.bytes() ?: throw BackendException(
                    "Repository request to $url returned an empty body",
                    ErrorCode.REPOSITORY_UNREACHABLE,
                )
            }
        } catch (e: BackendException) {
            throw e
        } catch (e: IOException) {
            throw BackendException(
                "Repository is unreachable: ${e.message}",
                ErrorCode.REPOSITORY_UNREACHABLE,
                e,
                retryable = true,
            )
        }
    }

    private fun getOrNull(url: String): ByteArray? = runCatching { get(url) }.getOrNull()

    private fun resolve(base: String, reference: String): String {
        if (reference.startsWith("http://") || reference.startsWith("https://")) return reference
        return base.toHttpUrlOrNull()?.resolve(reference)?.toString() ?: reference
    }

    /** Downloads an APK into [targetDir], returning the written file and its SHA-256 digest. */
    suspend fun download(
        remote: RepositoryExtension,
        targetDir: File,
        onChunk: suspend (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Pair<File, String> = withContext(Dispatchers.IO) {
        targetDir.mkdirs()
        val target = File(targetDir, "${remote.id}-${remote.versionCode}.apk.part")
        val digest = MessageDigest.getInstance("SHA-256")

        val request = Request.Builder().url(remote.apkUrl).build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw BackendException(
                        "Download of ${remote.id} failed with HTTP ${response.code}",
                        ErrorCode.REPOSITORY_UNREACHABLE,
                        retryable = response.code >= 500,
                    )
                }
                val body = response.body ?: throw BackendException(
                    "Download of ${remote.id} returned an empty body",
                    ErrorCode.NETWORK,
                )
                val total = body.contentLength()
                var downloaded = 0L
                target.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read
                            onChunk(downloaded, total)
                        }
                    }
                }
                if (downloaded == 0L) {
                    throw BackendException("Downloaded extension is empty", ErrorCode.NETWORK)
                }
            }
        } catch (e: BackendException) {
            target.delete()
            throw e
        } catch (e: IOException) {
            target.delete()
            throw BackendException(
                "Download of ${remote.id} failed: ${e.message}",
                ErrorCode.NETWORK,
                e,
                retryable = true,
            )
        }

        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        target to sha
    }

    private object ListSerializerHolder {
        val indexList = kotlinx.serialization.builtins.ListSerializer(AniyomiIndexEntry.serializer())
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (isString) content else content.takeIf { it != "null" }

@Serializable
internal data class AniyomiIndexEntry(
    val name: String = "",
    val pkg: String = "",
    val apk: String = "",
    val lang: String = "",
    val code: Long = 0,
    val version: String = "",
    val nsfw: Int = 0,
    @SerialName("hasReadme") val hasReadme: Int = 0,
    @SerialName("hasChangelog") val hasChangelog: Int = 0,
    val sources: List<AniyomiIndexSource>? = null,
)

@Serializable
internal data class AniyomiIndexSource(
    val id: Long = 0,
    val lang: String = "",
    val name: String = "",
    @SerialName("baseUrl") val baseUrl: String = "",
)
