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
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Aniyomi repository (`index.min.json`) client.
 *
 * Repository layout, as produced by Aniyomi's own extensions repo tooling:
 * ```
 * <repo>/index.min.json
 * <repo>/apk/<file>.apk
 * <repo>/icon/<package>.png
 * ```
 */
internal class AniyomiRepositoryClient(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun indexUrl(repository: ExtensionRepository): String {
        val url = repository.url.trimEnd('/')
        return if (url.endsWith(".json")) url else "$url/index.min.json"
    }

    fun baseUrl(repository: ExtensionRepository): String =
        indexUrl(repository).substringBeforeLast('/')

    suspend fun fetch(repository: ExtensionRepository): List<RepositoryExtension> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(indexUrl(repository))
            .header("Accept", "application/json")
            .build()

        val body = try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw BackendException(
                        "Repository ${repository.name} returned HTTP ${response.code}",
                        ErrorCode.REPOSITORY_UNREACHABLE,
                        retryable = response.code >= 500,
                    )
                }
                response.body?.string().orEmpty()
            }
        } catch (e: BackendException) {
            throw e
        } catch (e: IOException) {
            throw BackendException(
                "Repository ${repository.name} is unreachable: ${e.message}",
                ErrorCode.REPOSITORY_UNREACHABLE,
                e,
                retryable = true,
            )
        }

        val entries = try {
            json.decodeFromString(ListSerializerHolder.indexList, body)
        } catch (e: Exception) {
            throw BackendException(
                "Repository ${repository.name} returned an invalid index: ${e.message}",
                ErrorCode.REPOSITORY_INVALID,
                e,
            )
        }

        val base = baseUrl(repository)
        entries.mapNotNull { entry ->
            if (entry.pkg.isBlank() || entry.apk.isBlank()) return@mapNotNull null
            if (!AniyomiPackage.isSupportedLibVersion(entry.version)) return@mapNotNull null
            RepositoryExtension(
                repositoryId = repository.id,
                backend = BackendType.ANIYOMI,
                id = entry.pkg,
                name = entry.name.removePrefix("Aniyomi: ").trim(),
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
