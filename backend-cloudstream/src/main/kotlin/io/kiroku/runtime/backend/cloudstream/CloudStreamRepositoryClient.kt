package io.kiroku.runtime.backend.cloudstream

import io.kiroku.runtime.backend.BackendException
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.ExtensionRepository
import io.kiroku.runtime.ipc.RepositoryExtension
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * CloudStream repository client.
 *
 * Two documents are involved:
 *  - `repo.json`    -> `{ "name", "description", "manifestVersion", "pluginLists": ["<plugins.json>", …] }`
 *  - `plugins.json` -> array of plugin entries: `internalName`, `url`, `version`, `name`, `iconUrl`, …
 *
 * Both formats have changed between CloudStream versions, so parsing is intentionally lenient:
 * unknown keys are ignored and missing keys degrade to sensible defaults instead of failing the
 * whole repository.
 */
internal class CloudStreamRepositoryClient(private val http: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(repository: ExtensionRepository): List<RepositoryExtension> = withContext(Dispatchers.IO) {
        val base = repository.url.trimEnd('/')
        val pluginLists = if (base.endsWith(".json")) {
            listOf(base)
        } else {
            val repoJson = getOrNull("$base/repo.json")
            if (repoJson != null) {
                runCatching {
                    json.parseToJsonElement(repoJson).jsonObject["pluginLists"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?.map { resolve(base, it) }
                }.getOrNull().orEmpty().ifEmpty { listOf("$base/plugins.json") }
            } else {
                listOf("$base/plugins.json")
            }
        }

        val result = mutableListOf<RepositoryExtension>()
        var lastError: Exception? = null

        for (listUrl in pluginLists) {
            try {
                val body = get(listUrl)
                val array = json.parseToJsonElement(body).jsonArray
                array.forEach { element ->
                    (element as? JsonObject)?.toRepositoryExtension(repository, base)?.let(result::add)
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (result.isEmpty() && lastError != null) {
            throw BackendException(
                "CloudStream repository ${repository.name} could not be read: ${lastError.message}",
                ErrorCode.REPOSITORY_UNREACHABLE,
                lastError,
                retryable = true,
            )
        }
        result.distinctBy { it.id }
    }

    suspend fun download(
        remote: RepositoryExtension,
        targetDir: File,
        onChunk: suspend (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        targetDir.mkdirs()
        val target = File(targetDir, "${remote.id}-${remote.versionCode}.cs3.part")
        val request = Request.Builder().url(remote.apkUrl).build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw BackendException(
                        "Plugin download failed with HTTP ${response.code}",
                        ErrorCode.REPOSITORY_UNREACHABLE,
                        retryable = response.code >= 500,
                    )
                }
                val body = response.body ?: throw BackendException("Plugin download returned no body", ErrorCode.NETWORK)
                val total = body.contentLength()
                var downloaded = 0L
                target.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            onChunk(downloaded, total)
                        }
                    }
                }
            }
        } catch (e: BackendException) {
            target.delete()
            throw e
        } catch (e: IOException) {
            target.delete()
            throw BackendException("Plugin download failed: ${e.message}", ErrorCode.NETWORK, e, retryable = true)
        }
        target
    }

    private fun JsonObject.toRepositoryExtension(repository: ExtensionRepository, base: String): RepositoryExtension? {
        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
            this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }

        val name = str("name", "internalName") ?: return null
        val url = str("url", "filePath") ?: return null
        val versionCode = this["version"]?.jsonPrimitive?.intOrNull ?: 0
        val internalName = str("internalName") ?: name
        val id = internalName.replace(' ', '-').lowercase()

        return RepositoryExtension(
            repositoryId = repository.id,
            backend = BackendType.CLOUDSTREAM,
            id = id,
            name = name,
            versionName = versionCode.toString(),
            versionCode = versionCode.toLong(),
            language = str("language"),
            apkUrl = resolve(base, url),
            iconUrl = str("iconUrl")?.let { resolve(base, it) },
            libVersion = "cloudstream",
            isNsfw = this["tvTypes"]?.jsonArray?.any {
                it.jsonPrimitive.contentOrNull.equals("NSFW", ignoreCase = true)
            } ?: false,
        )
    }

    private fun resolve(base: String, path: String): String =
        if (path.startsWith("http://") || path.startsWith("https://")) path else "$base/${path.trimStart('/')}"

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw BackendException("HTTP ${response.code} for $url", ErrorCode.REPOSITORY_UNREACHABLE)
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun getOrNull(url: String): String? = runCatching { get(url) }.getOrNull()

    @Suppress("unused")
    private fun JsonArray.firstObjectOrNull(): JsonObject? = firstOrNull() as? JsonObject

    @Suppress("unused")
    private fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull
}
