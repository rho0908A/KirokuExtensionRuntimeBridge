package io.kiroku.runtime.backend.cloudstream

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import io.kiroku.runtime.backend.BackendException
import io.kiroku.runtime.backend.ExtensionBackend
import io.kiroku.runtime.backend.PackageInspection
import io.kiroku.runtime.backend.cloudstream.CloudStreamMapper.toBridgeAnime
import io.kiroku.runtime.backend.cloudstream.CloudStreamMapper.toBridgeEpisodes
import io.kiroku.runtime.backend.cloudstream.CloudStreamMapper.toBridgeStream
import io.kiroku.runtime.backend.cloudstream.CloudStreamMapper.toBridgeSubtitle
import io.kiroku.runtime.ipc.Anime
import io.kiroku.runtime.ipc.AnimeDetails
import io.kiroku.runtime.ipc.BackendInfo
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.Episode
import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.Extension
import io.kiroku.runtime.ipc.ExtensionError
import io.kiroku.runtime.ipc.ExtensionRepository
import io.kiroku.runtime.ipc.InstallationProgress
import io.kiroku.runtime.ipc.RepositoryExtension
import io.kiroku.runtime.ipc.StreamSource
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * CloudStream backend: installs `.cs3` plugins and executes the providers they register.
 *
 * Every provider is a `MainAPI` instance created by the plugin, so the operations below are thin
 * translations of the CloudStream contract into the bridge contract — the plugin stays in charge of
 * scraping, extraction and stream metadata.
 */
class CloudStreamBackend(
    context: Context,
    private val http: OkHttpClient = defaultHttpClient(),
) : ExtensionBackend {

    private val appContext = context.applicationContext
    private val extensionDir = File(appContext.filesDir, "extensions/cloudstream")
    private val tempDir = File(appContext.cacheDir, "cloudstream-downloads")
    private val loader = CloudStreamPluginLoader(appContext)
    private val repositoryClient = CloudStreamRepositoryClient(http)

    private val loaded = ConcurrentHashMap<String, CloudStreamLoadedPlugin>()

    private val _installed = MutableStateFlow<List<Extension>>(emptyList())
    override val installed: StateFlow<List<Extension>> = _installed.asStateFlow()

    override val storageDirectory: File get() = extensionDir

    override val type: BackendType = BackendType.CLOUDSTREAM

    override val info: BackendInfo = BackendInfo(
        type = BackendType.CLOUDSTREAM,
        implementationVersion = "1.0.0",
        apiVersion = "cloudstream library 4.9.1",
        supportedExtensionKinds = listOf("CloudStream .cs3 plugin"),
        notes = "Plugins are loaded through DexClassLoader against the bundled CloudStream API.",
    )

    override suspend fun initialize() {
        withContext(Dispatchers.IO) {
            extensionDir.mkdirs()
            tempDir.mkdirs()
            reloadInternal()
        }
    }

    override suspend fun reload(): List<Extension> = withContext(Dispatchers.IO) { reloadInternal() }

    private fun reloadInternal(): List<Extension> {
        // Unregister providers of plugins that are no longer on disk.
        val files = extensionDir.listFiles { file -> file.isFile && file.name.endsWith(".cs3", true) }
            ?.sortedBy { it.name }
            .orEmpty()
        val presentIds = files.map { it.nameWithoutExtension }.toSet()

        loaded.keys.filter { it !in presentIds }.forEach { stale ->
            loaded.remove(stale)?.let(loader::unload)
        }

        val extensions = files.mapNotNull { file ->
            val pluginId = file.nameWithoutExtension
            loaded[pluginId]?.let { return@mapNotNull it.extension }
            try {
                val plugin = loader.load(file, pluginId, null)
                loaded[pluginId] = plugin
                plugin.extension
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load plugin ${file.name}", e)
                Extension(
                    id = pluginId,
                    metadata = io.kiroku.runtime.ipc.ExtensionMetadata(
                        name = pluginId,
                        packageName = pluginId,
                        versionName = "0",
                        versionCode = 0,
                        sizeBytes = file.length(),
                    ),
                    backend = BackendType.CLOUDSTREAM,
                    isInstalled = true,
                    loadError = ExtensionError(
                        code = ErrorCode.EXTENSION_LOAD_FAILED,
                        message = e.message ?: "Plugin failed to load",
                        details = e.javaClass.name,
                        extensionId = pluginId,
                        backend = BackendType.CLOUDSTREAM,
                    ),
                )
            }
        }

        _installed.value = extensions
        return extensions
    }

    override suspend fun inspectPackage(file: File): PackageInspection {
        val (manifest, error) = loader.inspect(file)
        if (error != null || manifest == null) {
            return PackageInspection(valid = false, metadata = null, error = error)
        }
        return PackageInspection(
            valid = true,
            metadata = io.kiroku.runtime.ipc.ExtensionMetadata(
                name = manifest.name.ifBlank { file.nameWithoutExtension },
                packageName = file.nameWithoutExtension,
                versionName = manifest.version.toString(),
                versionCode = manifest.version.toLong(),
                libVersion = "cloudstream",
                sizeBytes = file.length(),
            ),
        )
    }

    override suspend fun installPackage(
        file: File,
        repositoryId: String?,
        onProgress: (suspend (InstallationProgress) -> Unit)?,
    ): Extension = withContext(Dispatchers.IO) {
        if (!file.name.endsWith(".cs3", ignoreCase = true)) {
            throw BackendException(
                "CloudStream plugins use the .cs3 extension",
                ErrorCode.EXTENSION_INVALID_PACKAGE,
            )
        }
        val (manifest, error) = loader.inspect(file)
        if (error != null || manifest == null) {
            throw BackendException(error?.message ?: "Invalid plugin package", error?.code ?: ErrorCode.EXTENSION_INVALID_PACKAGE)
        }

        val pluginId = manifest.name.ifBlank { file.nameWithoutExtension }
            .replace(Regex("[^A-Za-z0-9._-]"), "-")
            .lowercase()
            .ifBlank { file.nameWithoutExtension }

        onProgress?.invoke(
            InstallationProgress(
                extensionId = pluginId,
                backend = BackendType.CLOUDSTREAM,
                phase = "installing",
                totalBytes = file.length(),
            ),
        )

        extensionDir.mkdirs()
        val target = File(extensionDir, "$pluginId.cs3")
        val staging = File(extensionDir, "$pluginId.cs3.part")
        try {
            file.inputStream().use { input -> staging.outputStream().use { output -> input.copyTo(output) } }
            if (target.exists() && !target.delete()) {
                throw BackendException("Could not replace ${target.name}", ErrorCode.STORAGE)
            }
            if (!staging.renameTo(target)) {
                throw BackendException("Could not finalise ${target.name}", ErrorCode.STORAGE)
            }
        } catch (e: BackendException) {
            staging.delete()
            throw e
        } catch (e: IOException) {
            staging.delete()
            throw BackendException("Could not store plugin: ${e.message}", ErrorCode.STORAGE, e)
        }

        loaded.remove(pluginId)?.let(loader::unload)
        val plugin = try {
            loader.load(target, pluginId, repositoryId)
        } catch (e: Throwable) {
            target.delete()
            throw if (e is BackendException) e else BackendException(
                "Plugin installed but failed to load: ${e.message}",
                ErrorCode.EXTENSION_LOAD_FAILED,
                e,
            )
        }
        loaded[pluginId] = plugin
        _installed.value = rebuildInstalledList()

        onProgress?.invoke(
            InstallationProgress(
                extensionId = pluginId,
                backend = BackendType.CLOUDSTREAM,
                phase = "completed",
                bytesDownloaded = file.length(),
                totalBytes = file.length(),
            ),
        )
        plugin.extension
    }

    override suspend fun uninstall(extensionId: String): Boolean = withContext(Dispatchers.IO) {
        loaded.remove(extensionId)?.let(loader::unload)
        val target = File(extensionDir, "$extensionId.cs3")
        val deleted = !target.exists() || target.delete()
        _installed.value = rebuildInstalledList()
        deleted
    }

    override suspend fun availableExtensions(repository: ExtensionRepository): List<RepositoryExtension> =
        repositoryClient.fetch(repository)

    override suspend fun installFromRepository(
        remote: RepositoryExtension,
        onProgress: (suspend (InstallationProgress) -> Unit)?,
    ): Extension {
        val file = repositoryClient.download(remote, tempDir) { downloaded, total ->
            onProgress?.invoke(
                InstallationProgress(
                    extensionId = remote.id,
                    backend = BackendType.CLOUDSTREAM,
                    phase = "downloading",
                    bytesDownloaded = downloaded,
                    totalBytes = total,
                ),
            )
        }
        return try {
            installPackage(file, remote.repositoryId, onProgress)
        } finally {
            file.delete()
        }
    }

    override fun isLibraryVersionSupported(libVersion: String?): Boolean = true

    override suspend fun search(extensionId: String, sourceId: String?, query: String, page: Int): List<Anime> {
        val provider = provider(extensionId, sourceId)
        return guard(extensionId) {
            provider.search(query, page.coerceAtLeast(1))?.items.orEmpty()
                .map { it.toBridgeAnime(extensionId) }
        }
    }

    override suspend fun popular(extensionId: String, sourceId: String?, page: Int): List<Anime> =
        homePage(extensionId, sourceId, page.coerceAtLeast(1))

    override suspend fun latest(extensionId: String, sourceId: String?, page: Int): List<Anime> =
        homePage(extensionId, sourceId, page.coerceAtLeast(1))

    private suspend fun homePage(extensionId: String, sourceId: String?, page: Int): List<Anime> {
        val provider = provider(extensionId, sourceId)
        if (!provider.hasMainPage) {
            throw BackendException(
                "Provider ${provider.name} does not expose a home page",
                ErrorCode.EXTENSION_UNSUPPORTED,
            )
        }
        return guard(extensionId) {
            val response = provider.getMainPage(
                page,
                MainPageRequest(data = "", name = provider.name, horizontalImages = false),
            ) ?: throw BackendException(
                "Provider ${provider.name} returned no home page",
                ErrorCode.BACKEND_FAILURE,
            )
            response.items.flatMap { list -> list.list.orEmpty() }
                .distinctBy { it.url }
                .map { it.toBridgeAnime(extensionId) }
        }
    }

    override suspend fun details(extensionId: String, sourceId: String?, animeUrl: String): AnimeDetails {
        val provider = provider(extensionId, sourceId)
        return guard(extensionId) {
            val response = provider.load(animeUrl)
                ?: throw BackendException("Provider returned no details for $animeUrl", ErrorCode.BACKEND_FAILURE)
            AnimeDetails(
                anime = response.toBridgeAnime(extensionId),
                episodes = response.toBridgeEpisodes(animeUrl),
                extensionId = extensionId,
            )
        }
    }

    override suspend fun episodes(extensionId: String, sourceId: String?, animeUrl: String): List<Episode> {
        val provider = provider(extensionId, sourceId)
        return guard(extensionId) {
            val response = provider.load(animeUrl)
                ?: throw BackendException("Provider returned no details for $animeUrl", ErrorCode.BACKEND_FAILURE)
            response.toBridgeEpisodes(animeUrl)
        }
    }

    override suspend fun streams(extensionId: String, sourceId: String?, episodeUrl: String): List<StreamSource> {
        val provider = provider(extensionId, sourceId)
        return guard(extensionId) {
            val subtitles = mutableListOf<io.kiroku.runtime.ipc.Subtitle>()
            val links = mutableListOf<ExtractorLink>()

            runCatching {
                provider.loadLinks(
                    episodeUrl,
                    false,
                    { subtitle: SubtitleFile -> subtitles.add(subtitle.toBridgeSubtitle()) },
                    { link: ExtractorLink -> links.add(link) },
                )
            }.onFailure { Log.w(TAG, "loadLinks failed for ${provider.name}", it) }

            if (links.isEmpty()) {
                // CloudStream providers frequently return raw embed URLs; the library's extractor
                // registry can still resolve them. This is the same fallback CloudStream itself uses.
                runCatching {
                    com.lagradost.cloudstream3.utils.loadExtractor(
                        episodeUrl,
                        provider.mainUrl,
                        { subtitle: SubtitleFile -> subtitles.add(subtitle.toBridgeSubtitle()) },
                        { link: ExtractorLink -> links.add(link) },
                    )
                }.onFailure { Log.w(TAG, "loadExtractor failed for ${provider.name}", it) }
            }

            links
                .distinctBy { it.url }
                .map { it.toBridgeStream(subtitles.toList(), episodeUrl) }
        }
    }

    private fun rebuildInstalledList(): List<Extension> {
        val onDisk = extensionDir.listFiles { file -> file.isFile && file.name.endsWith(".cs3", true) }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            .orEmpty()
        return _installed.value
            .filter { it.id in onDisk }
            .map { extension -> loaded[extension.id]?.extension ?: extension }
    }

    private fun provider(extensionId: String, sourceId: String?): MainAPI {
        val plugin = loaded[extensionId] ?: throw BackendException(
            "Plugin $extensionId is not installed or failed to load",
            ErrorCode.EXTENSION_NOT_FOUND,
        )
        if (sourceId.isNullOrBlank()) {
            return plugin.providers.firstOrNull()
                ?: throw BackendException("Plugin $extensionId registered no providers", ErrorCode.SOURCE_NOT_FOUND)
        }
        return plugin.providers.firstOrNull { it.name == sourceId }
            ?: APIHolder.getApiFromNameNull(sourceId)
            ?: throw BackendException("Provider $sourceId not found in $extensionId", ErrorCode.SOURCE_NOT_FOUND)
    }

    private suspend fun <T> guard(extensionId: String?, block: suspend () -> T): T = try {
        block()
    } catch (e: BackendException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w(TAG, "CloudStream plugin $extensionId failed", e)
        throw BackendException(
            e.message ?: "Plugin execution failed",
            ErrorCode.BACKEND_FAILURE,
            e,
        )
    }

    companion object {
        private const val TAG = "CloudStreamBackend"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
