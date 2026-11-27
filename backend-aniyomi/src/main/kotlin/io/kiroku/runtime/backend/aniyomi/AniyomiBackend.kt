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
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import io.kiroku.runtime.backend.BackendException
import io.kiroku.runtime.backend.ExtensionBackend
import io.kiroku.runtime.backend.MangaBackend
import io.kiroku.runtime.backend.PackageInspection
import io.kiroku.runtime.backend.aniyomi.AniyomiMangaMapper.toBridgeChapter
import io.kiroku.runtime.backend.aniyomi.AniyomiMangaMapper.toBridgeManga
import io.kiroku.runtime.backend.aniyomi.AniyomiMangaMapper.toBridgePage
import io.kiroku.runtime.backend.aniyomi.AniyomiMapper.toBridgeAnime
import io.kiroku.runtime.backend.aniyomi.AniyomiMapper.toBridgeEpisode
import io.kiroku.runtime.backend.aniyomi.AniyomiMapper.toStreamSource
import io.kiroku.runtime.ipc.Anime
import io.kiroku.runtime.ipc.AnimeDetails
import io.kiroku.runtime.ipc.BackendInfo
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.Chapter
import io.kiroku.runtime.ipc.Episode
import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.Extension
import io.kiroku.runtime.ipc.ExtensionError
import io.kiroku.runtime.ipc.ExtensionRepository
import io.kiroku.runtime.ipc.InstallationProgress
import io.kiroku.runtime.ipc.Manga
import io.kiroku.runtime.ipc.MangaDetails
import io.kiroku.runtime.ipc.MangaPage
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
 * The Aniyomi backend: installs extension APKs and executes their sources.
 *
 * The backend is named after Aniyomi because that is the ecosystem its packages come from, but the
 * API surface it provides is taken from the maintained hosts:
 *  - anime packages run against **Animiru**'s `animesource` API (extensions-lib 14, 16 and 17),
 *  - manga packages run against **Mihon**'s `source` API (extensions-lib 1.4-1.6).
 *
 * CloudStream is a separate backend; nothing here is shared with it except the bridge models.
 */
class AniyomiBackend(
    context: Context,
    private val http: OkHttpClient = defaultHttpClient(),
) : ExtensionBackend, MangaBackend {

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
        implementationVersion = "1.2.0",
        apiVersion = "animiru animesource 14/16/17, mihon source 1.4..1.6",
        supportedExtensionKinds = listOf(
            "Animiru anime extension APK (tachiyomi.animeextension)",
            "Mihon manga extension APK (tachiyomi.extension)",
        ),
        notes = "Anime sources run against the Animiru API, manga sources against the Mihon API. " +
            "Both are loaded child-first with PathClassLoader.",
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
            val kind = AniyomiPackage.kind(info)
            val validation = AniyomiPackage.validate(appContext, file, kind)
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
        val info = AniyomiPackage.packageInfo(appContext, file)
        val kind = info?.let(AniyomiPackage::kind)
        val error = AniyomiPackage.validate(appContext, file, kind)
        if (error != null) {
            return PackageInspection(valid = false, metadata = null, error = error)
        }
        return PackageInspection(
            valid = true,
            metadata = info?.let { AniyomiPackage.metadata(appContext, it) },
            sourceIds = emptyList(),
        )
    }

    override suspend fun installPackage(
        file: File,
        repositoryId: String?,
        onProgress: (suspend (InstallationProgress) -> Unit)?,
    ): Extension = withContext(Dispatchers.IO) {
        val info = AniyomiPackage.packageInfo(appContext, file)
        val kind = info?.let(AniyomiPackage::kind)
        val validation = AniyomiPackage.validate(appContext, file, kind)
        if (validation != null) throw BackendException(validation.message, validation.code)

        checkNotNull(info) { "APK could not be parsed" }
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
            // The package arrived and is valid; only its code could not be instantiated. Keeping it
            // on disk and listing it with the reason beats deleting it, which is what used to happen
            // and left the Installed tab empty with no explanation.
            val failure = loader.failedExtension(
                info,
                target,
                ExtensionError(
                    code = ErrorCode.EXTENSION_LOAD_FAILED,
                    message = e.message ?: "Extension failed to load",
                    details = e.javaClass.name,
                    extensionId = extensionId,
                    backend = BackendType.ANIYOMI,
                ),
            )
            _installed.value = rebuildInstalledList().filterNot { it.id == extensionId } + failure
            Log.e(TAG, "Installed ${info.packageName} but it failed to load; package kept", e)
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

    override fun isLibraryVersionSupported(libVersion: String?): Boolean =
        AniyomiPackage.isSupportedLibVersion(libVersion) ||
            AniyomiPackage.isMangaLibVersionSupported(libVersion)

    // ---------------------------------------------------------------------------------------------
    // Anime execution (Animiru API)
    // ---------------------------------------------------------------------------------------------

    override suspend fun search(extensionId: String, sourceId: String?, query: String, page: Int): List<Anime> {
        val source = animeCatalogue(extensionId, sourceId)
        return guard(extensionId) {
            val filters = runCatching { source.getFilterList() }.getOrDefault(AnimeFilterList())
            source.getSearchAnime(page.coerceAtLeast(1), query, filters).animes
                .map { it.toBridgeAnime(extensionId) }
        }
    }

    override suspend fun popular(extensionId: String, sourceId: String?, page: Int): List<Anime> {
        val source = animeCatalogue(extensionId, sourceId)
        return guard(extensionId) {
            source.getPopularAnime(page.coerceAtLeast(1)).animes.map { it.toBridgeAnime(extensionId) }
        }
    }

    override suspend fun latest(extensionId: String, sourceId: String?, page: Int): List<Anime> {
        val source = animeCatalogue(extensionId, sourceId)
        if (!source.supportsLatest) {
            throw BackendException("Source ${source.name} does not support latest updates", ErrorCode.EXTENSION_UNSUPPORTED)
        }
        return guard(extensionId) {
            source.getLatestUpdates(page.coerceAtLeast(1)).animes.map { it.toBridgeAnime(extensionId) }
        }
    }

    override suspend fun details(extensionId: String, sourceId: String?, animeUrl: String): AnimeDetails {
        val source = animeSource(extensionId, sourceId)
        return guard(extensionId) {
            val stub = SAnime.create().apply { url = animeUrl }
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
        val source = animeSource(extensionId, sourceId)
        return guard(extensionId) {
            val stub = SAnime.create().apply { url = animeUrl }
            source.getEpisodeList(stub).map { it.toBridgeEpisode(animeUrl) }
        }
    }

    override suspend fun streams(extensionId: String, sourceId: String?, episodeUrl: String): List<StreamSource> {
        val source = animeSource(extensionId, sourceId)
        return guard(extensionId) {
            val stub = SEpisode.create().apply { url = episodeUrl }
            val direct = runCatching { source.getVideoList(stub) }.getOrDefault(emptyList())
            val videos: List<Video> = if (direct.isNotEmpty()) {
                direct
            } else {
                // Newer sources resolve through hosters; an empty direct list is not an error.
                val hosters = runCatching { source.getHosterList(stub) }.getOrNull().orEmpty()
                hosters.flatMap { hoster ->
                    hoster.videoList.orEmpty() + runCatching { source.getVideoList(hoster) }
                        .getOrDefault(emptyList())
                }.distinctBy { it.videoUrl }
            }
            videos.map { it.toStreamSource(episodeUrl) }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Manga execution (Mihon API)
    // ---------------------------------------------------------------------------------------------

    override suspend fun searchManga(extensionId: String, sourceId: String?, query: String, page: Int): List<Manga> {
        val source = mangaCatalogue(extensionId, sourceId)
        return guard(extensionId) {
            val filters = runCatching { source.getFilterList() }.getOrDefault(eu.kanade.tachiyomi.source.model.FilterList())
            source.getSearchManga(page.coerceAtLeast(1), query, filters).mangas
                .map { it.toBridgeManga(extensionId) }
        }
    }

    override suspend fun popularManga(extensionId: String, sourceId: String?, page: Int): List<Manga> {
        val source = mangaCatalogue(extensionId, sourceId)
        return guard(extensionId) {
            source.getPopularManga(page.coerceAtLeast(1)).mangas.map { it.toBridgeManga(extensionId) }
        }
    }

    override suspend fun latestManga(extensionId: String, sourceId: String?, page: Int): List<Manga> {
        val source = mangaCatalogue(extensionId, sourceId)
        if (!source.supportsLatest) {
            throw BackendException("Source ${source.name} does not support latest updates", ErrorCode.EXTENSION_UNSUPPORTED)
        }
        return guard(extensionId) {
            source.getLatestUpdates(page.coerceAtLeast(1)).mangas.map { it.toBridgeManga(extensionId) }
        }
    }

    override suspend fun mangaDetails(extensionId: String, sourceId: String?, mangaUrl: String): MangaDetails {
        val source = mangaSource(extensionId, sourceId)
        return guard(extensionId) {
            val stub = SManga.create().apply { url = mangaUrl }
            val update = runCatching { source.getMangaUpdate(stub, emptyList(), true, true) }.getOrNull()
            val manga = update?.manga ?: stub
            val chapters = update?.chapters
                ?: runCatching { source.getMangaUpdate(stub, emptyList(), false, true).chapters }
                    .getOrDefault(emptyList())
            MangaDetails(
                manga = manga.toBridgeManga(extensionId),
                chapters = chapters.map { it.toBridgeChapter(manga.url) },
                extensionId = extensionId,
            )
        }
    }

    override suspend fun chapters(extensionId: String, sourceId: String?, mangaUrl: String): List<Chapter> {
        val source = mangaSource(extensionId, sourceId)
        return guard(extensionId) {
            val stub = SManga.create().apply { url = mangaUrl }
            source.getMangaUpdate(stub, emptyList(), false, true).chapters
                .map { it.toBridgeChapter(mangaUrl) }
        }
    }

    override suspend fun pages(extensionId: String, sourceId: String?, chapterUrl: String): List<MangaPage> {
        val source = mangaSource(extensionId, sourceId)
        return guard(extensionId) {
            val stub = SChapter.create().apply { url = chapterUrl }
            source.getPageList(stub).map { it.toBridgePage() }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------------

    private fun rebuildInstalledList(): List<Extension> {
        val onDisk = extensionDir.listFiles { file -> file.isFile && file.name.endsWith(".apk", true) }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            .orEmpty()
        return _installed.value
            .filter { it.id in onDisk }
            .map { extension -> loaded[extension.id]?.extension ?: extension }
    }

    private fun loadedExtension(extensionId: String): AniyomiLoadedExtension =
        loaded[extensionId] ?: throw BackendException(
            "Extension $extensionId is not installed or failed to load",
            ErrorCode.EXTENSION_NOT_FOUND,
        )

    private fun animeSource(extensionId: String, sourceId: String?): AnimeSource {
        val extension = loadedExtension(extensionId)
        if (extension.kind != AniyomiExtensionKind.ANIME) {
            throw BackendException("$extensionId is a manga extension, not an anime extension", ErrorCode.EXTENSION_UNSUPPORTED)
        }
        if (sourceId == null) return extension.animeSources.firstOrNull()
            ?: throw BackendException("Extension $extensionId has no sources", ErrorCode.SOURCE_NOT_FOUND)
        return extension.animeSources.firstOrNull { it.id.toString() == sourceId }
            ?: throw BackendException("Source $sourceId not found in $extensionId", ErrorCode.SOURCE_NOT_FOUND)
    }

    private fun animeCatalogue(extensionId: String, sourceId: String?): AnimeCatalogueSource {
        val source = animeSource(extensionId, sourceId)
        return source as? AnimeCatalogueSource ?: throw BackendException(
            "Source ${source.name} does not support browsing or search",
            ErrorCode.EXTENSION_UNSUPPORTED,
        )
    }

    private fun mangaSource(extensionId: String, sourceId: String?): Source {
        val extension = loadedExtension(extensionId)
        if (extension.kind != AniyomiExtensionKind.MANGA) {
            throw BackendException("$extensionId is an anime extension, not a manga extension", ErrorCode.EXTENSION_UNSUPPORTED)
        }
        if (sourceId == null) return extension.mangaSources.firstOrNull()
            ?: throw BackendException("Extension $extensionId has no sources", ErrorCode.SOURCE_NOT_FOUND)
        return extension.mangaSources.firstOrNull { it.id.toString() == sourceId }
            ?: throw BackendException("Source $sourceId not found in $extensionId", ErrorCode.SOURCE_NOT_FOUND)
    }

    private fun mangaCatalogue(extensionId: String, sourceId: String?): CatalogueSource {
        val source = mangaSource(extensionId, sourceId)
        return source as? CatalogueSource ?: throw BackendException(
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
