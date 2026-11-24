package io.kiroku.runtime.backend.aniyomi

import android.content.Context
import android.content.pm.PackageInfo
import android.util.Log
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.kiroku.runtime.backend.BackendException
import io.kiroku.runtime.backend.aniyomi.AniyomiMapper.toBridgeAnime
import io.kiroku.runtime.backend.aniyomi.AniyomiMapper.toBridgeEpisode
import io.kiroku.runtime.backend.aniyomi.AniyomiMapper.toStreamSource
import io.kiroku.runtime.backend.ExtensionBackend
import io.kiroku.runtime.backend.PackageInspection
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
 * Aniyomi backend: installs Aniyomi APK extensions and executes their anime sources.
 *
 * Execution is isolated from CloudStream: nothing here is shared with the other backend except the
 * bridge models, and every failure is converted to [BackendException] with an explicit error code.
 */
class AniyomiBackend(
    context: Context,
    private val http: OkHttpClient = defaultHttpClient(),
) : ExtensionBackend {

    private val appContext = context.applicationContext
    private val extensionDir = File(appContext.filesDir, "extensions/aniyomi")
    private val tempDir = File(appContext.cacheDir, "aniyomi-downloads")
    private val loader = AniyomiExtensionLoader(appContext)
    private val repositoryClient = AniyomiRepositoryClient(http)

    private val loaded = ConcurrentHashMap<String, AniyomiLoadedExtension>()

    private val _installed = MutableStateFlow<List<Extension>>(emptyList())
    override val installed: StateFlow<List<Extension>> = _installed.asStateFlow()

    override val storageDirectory: File get() = extensionDir

    override val type: BackendType = BackendType.ANIYOMI

    override val info: BackendInfo = BackendInfo(
        type = BackendType.ANIYOMI,
        implementationVersion = "1.0.0",
        apiVersion = "aniyomi extensions-lib ${AniyomiPackage.SUPPORTED_LIB_VERSIONS.first}..${AniyomiPackage.SUPPORTED_LIB_VERSIONS.last}",
        supportedExtensionKinds = listOf("Aniyomi anime extension APK"),
        notes = "Sources are loaded child-first with PathClassLoader against the runtime-provided Aniyomi API.",
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
        loaded.clear()
        val files = extensionDir.listFiles { file -> file.isFile && file.name.endsWith(".apk", true) }
            ?.sortedBy { it.name }
            .orEmpty()

        val extensions = files.map { file ->
            val info = AniyomiPackage.packageInfo(appContext, file)
            if (info == null) {
                Log.w(TAG, "Skipping unreadable extension package ${file.name}")
                return@map null
            }
            val validation = AniyomiPackage.validate(appContext, file)
            if (validation != null) {
                return@map loader.failedExtension(info, file, validation)
            }
            try {
                val extension = loader.load(file, info)
                loaded[extension.extension.id] = extension
                extension.extension
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load ${file.name}", e)
                loader.failedExtension(
                    info,
                    file,
                    ExtensionError(
                        code = ErrorCode.EXTENSION_LOAD_FAILED,
                        message = e.message ?: "Extension failed to load",
                        details = e.javaClass.name,
                        extensionId = info.packageName,
                        backend = BackendType.ANIYOMI,
                    ),
                )
            }
        }.filterNotNull()

        _installed.value = extensions
        return extensions
    }

    override suspend fun inspectPackage(file: File): PackageInspection {
        val error = AniyomiPackage.validate(appContext, file)
        if (error != null) {
            return PackageInspection(valid = false, metadata = null, error = error)
        }
        val info = AniyomiPackage.packageInfo(appContext, file)
            ?: return PackageInspection(
                valid = false,
                metadata = null,
                error = ExtensionError(
                    code = ErrorCode.EXTENSION_INVALID_PACKAGE,
                    message = "APK could not be parsed",
                    backend = BackendType.ANIYOMI,
                ),
            )
        return PackageInspection(
            valid = true,
            metadata = AniyomiPackage.metadata(appContext, info),
            sourceIds = emptyList(),
        )
    }

    override suspend fun installPackage(
        file: File,
        repositoryId: String?,
        onProgress: (suspend (InstallationProgress) -> Unit)?,
    ): Extension = withContext(Dispatchers.IO) {
        val validation = AniyomiPackage.validate(appContext, file)
        if (validation != null) throw BackendException(validation.message, validation.code)

        val info = AniyomiPackage.packageInfo(appContext, file)
            ?: throw BackendException("APK could not be parsed", ErrorCode.EXTENSION_INVALID_PACKAGE)

        val extensionId = info.packageName
        extensionDir.mkdirs()
        val target = File(extensionDir, "$extensionId.apk")
        val staging = File(extensionDir, "$extensionId.apk.part")

        onProgress?.invoke(
            InstallationProgress(
                extensionId = extensionId,
                backend = BackendType.ANIYOMI,
                phase = "installing",
                totalBytes = file.length(),
            ),
        )

        try {
            file.inputStream().use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            }
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
            throw BackendException("Could not store extension: ${e.message}", ErrorCode.STORAGE, e)
        }

        val loadedExtension = try {
            loader.load(target, info, repositoryId)
        } catch (e: Throwable) {
            target.delete()
            throw if (e is BackendException) e else BackendException(
                "Extension installed but failed to load: ${e.message}",
                ErrorCode.EXTENSION_LOAD_FAILED,
                e,
            )
        }

        loaded[extensionId] = loadedExtension
        _installed.value = rebuildInstalledList()

        onProgress?.invoke(
            InstallationProgress(
                extensionId = extensionId,
                backend = BackendType.ANIYOMI,
                phase = "completed",
                bytesDownloaded = file.length(),
                totalBytes = file.length(),
            ),
        )
        loadedExtension.extension
    }

    override suspend fun uninstall(extensionId: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(extensionDir, "$extensionId.apk")
        loaded.remove(extensionId)
        val deleted = !target.exists() || target.delete()
        _installed.value = rebuildInstalledList()
        deleted
    }

    override suspend fun availableExtensions(repository: ExtensionRepository): List<RepositoryExtension> =
        repositoryClient.fetch(repository)

    /** Downloads a repository extension into cache and installs it. */
    override suspend fun installFromRepository(
        remote: RepositoryExtension,
        onProgress: (suspend (InstallationProgress) -> Unit)?,
    ): Extension {
        val (file, _) = repositoryClient.download(remote, tempDir) { downloaded, total ->
            onProgress?.invoke(
                InstallationProgress(
                    extensionId = remote.id,
                    backend = BackendType.ANIYOMI,
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

    override fun isLibraryVersionSupported(libVersion: String?): Boolean {
        val major = libVersion?.substringBefore('.')?.toIntOrNull() ?: return false
        return major in AniyomiPackage.SUPPORTED_LIB_VERSIONS
    }

    override suspend fun search(extensionId: String, sourceId: String?, query: String, page: Int): List<Anime> {
        val source = catalogue(extensionId, sourceId)
        return guard(extensionId) {
            val filters = runCatching { source.getFilterList() }.getOrDefault(AnimeFilterList())
            source.getSearchAnime(page.coerceAtLeast(1), query, filters).animes
                .map { it.toBridgeAnime(extensionId) }
        }
    }

    override suspend fun popular(extensionId: String, sourceId: String?, page: Int): List<Anime> {
        val source = catalogue(extensionId, sourceId)
        return guard(extensionId) {
            source.getPopularAnime(page.coerceAtLeast(1)).animes.map { it.toBridgeAnime(extensionId) }
        }
    }

    override suspend fun latest(extensionId: String, sourceId: String?, page: Int): List<Anime> {
        val source = catalogue(extensionId, sourceId)
        if (!source.supportsLatest) {
            throw BackendException("Source ${source.name} does not support latest updates", ErrorCode.EXTENSION_UNSUPPORTED)
        }
        return guard(extensionId) {
            source.getLatestUpdates(page.coerceAtLeast(1)).animes.map { it.toBridgeAnime(extensionId) }
        }
    }

    override suspend fun details(extensionId: String, sourceId: String?, animeUrl: String): AnimeDetails {
        val source = source(extensionId, sourceId)
        return guard(extensionId) {
            val stub = SAnime().apply { url = animeUrl }
            val details = runCatching { source.getAnimeDetails(stub) }.getOrElse { stub }
            val episodes = runCatching { source.getEpisodeList(details) }.getOrDefault(emptyList())
            AnimeDetails(
                anime = details.toBridgeAnime(extensionId),
                episodes = episodes.map { it.toBridgeEpisode(details.url) },
                extensionId = extensionId,
            )
        }
    }

    override suspend fun episodes(extensionId: String, sourceId: String?, animeUrl: String): List<Episode> {
        val source = source(extensionId, sourceId)
        return guard(extensionId) {
            val stub = SAnime().apply { url = animeUrl }
            source.getEpisodeList(stub).map { it.toBridgeEpisode(animeUrl) }
        }
    }

    override suspend fun streams(extensionId: String, sourceId: String?, episodeUrl: String): List<StreamSource> {
        val source = source(extensionId, sourceId)
        return guard(extensionId) {
            val stub = SEpisode().apply { url = episodeUrl }
            val direct = runCatching { source.getVideoList(stub) }.getOrDefault(emptyList())
            val videos: List<Video> = if (direct.isNotEmpty()) {
                direct
            } else {
                // Newer sources resolve through hosters; an empty direct list is not an error.
                val catalogue = source as? AnimeCatalogueSource
                val hosters = runCatching { catalogue?.getHosterList(stub) }.getOrNull().orEmpty()
                hosters.flatMap { hoster ->
                    val hosterVideos = runCatching { catalogue?.getVideoList(hoster) }
                        .getOrNull()
                        .orEmpty()
                    hosterVideos.map { video ->
                        if (video.videoTitle.isNullOrBlank() && hoster.hosterName.isNotBlank()) {
                            video.videoTitle = hoster.hosterName
                        }
                        video
                    }
                }
            }
            videos.map { it.toStreamSource(episodeUrl) }
        }
    }

    private fun rebuildInstalledList(): List<Extension> {
        val onDisk = extensionDir.listFiles { file -> file.isFile && file.name.endsWith(".apk", true) }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            .orEmpty()
        return _installed.value
            .filter { it.id in onDisk }
            .map { extension ->
                loaded[extension.id]?.extension ?: extension
            }
    }

    private fun source(extensionId: String, sourceId: String?): AnimeSource {
        val extension = loaded[extensionId] ?: throw BackendException(
            "Extension $extensionId is not installed or failed to load",
            ErrorCode.EXTENSION_NOT_FOUND,
        )
        if (sourceId == null) return extension.sources.firstOrNull()
            ?: throw BackendException("Extension $extensionId has no sources", ErrorCode.SOURCE_NOT_FOUND)
        return extension.sources.firstOrNull { it.id.toString() == sourceId }
            ?: throw BackendException("Source $sourceId not found in $extensionId", ErrorCode.SOURCE_NOT_FOUND)
    }

    private fun catalogue(extensionId: String, sourceId: String?): AnimeCatalogueSource {
        val source = source(extensionId, sourceId)
        return source as? AnimeCatalogueSource ?: throw BackendException(
            "Source ${source.name} does not support browsing or search",
            ErrorCode.EXTENSION_UNSUPPORTED,
        )
    }

    private suspend fun <T> guard(extensionId: String?, block: suspend () -> T): T = try {
        block()
    } catch (e: BackendException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w(TAG, "Aniyomi extension $extensionId failed", e)
        throw BackendException(
            e.message ?: "Extension execution failed",
            ErrorCode.BACKEND_FAILURE,
            e,
        )
    }

    companion object {
        private const val TAG = "AniyomiBackend"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
