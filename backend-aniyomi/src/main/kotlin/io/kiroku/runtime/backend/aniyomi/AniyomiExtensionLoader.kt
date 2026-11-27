package io.kiroku.runtime.backend.aniyomi

import android.content.Context
import android.content.pm.PackageInfo
import android.util.Log
import dalvik.system.PathClassLoader
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.util.system.ChildFirstPathClassLoader
import io.kiroku.runtime.backend.BackendException
import io.kiroku.runtime.backend.LoadedExtension
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.Extension
import io.kiroku.runtime.ipc.ExtensionCapabilities
import io.kiroku.runtime.ipc.ExtensionError
import io.kiroku.runtime.ipc.ExtensionSourceInfo
import java.io.File

/** A loaded extension: the bridge model plus the live source instances. */
internal class AniyomiLoadedExtension(
    override val extension: Extension,
    val kind: AniyomiExtensionKind,
    val animeSources: List<AnimeSource>,
    val mangaSources: List<Source>,
    val factoryClassName: String?,
    val classLoader: ClassLoader,
) : LoadedExtension {
    override val handle: Any get() = this
}

/**
 * Instantiates Animiru anime and Mihon manga extension APKs.
 *
 * The extension code is loaded with a child-first [PathClassLoader]: the APK's own classes come
 * first (extensions often bundle Jsoup/OkHttp), while everything the extension only references —
 * the API implemented by `aniyomi-api` — resolves through the runtime's own class loader.
 */
internal class AniyomiExtensionLoader(private val context: Context) {

    private val parent: ClassLoader = AnimeSource::class.java.classLoader
        ?: AniyomiExtensionLoader::class.java.classLoader!!

    fun load(file: File, info: PackageInfo, repositoryId: String? = null): AniyomiLoadedExtension {
        val kind = AniyomiPackage.kind(info)
            ?: throw BackendException(
                "Extension ${info.packageName} is neither an anime nor a manga extension",
                ErrorCode.EXTENSION_UNSUPPORTED,
            )
        val metadata = AniyomiPackage.metadata(context, info)
        val classNames = AniyomiPackage.sourceClasses(info)
        if (classNames.isEmpty()) {
            throw BackendException(
                "Extension ${info.packageName} declares no source class",
                ErrorCode.EXTENSION_INVALID_PACKAGE,
            )
        }

        val classLoader = createClassLoader(file)
        val animeSources = mutableListOf<AnimeSource>()
        val mangaSources = mutableListOf<Source>()

        for (className in classNames) {
            when (kind) {
                AniyomiExtensionKind.ANIME -> animeSources += instantiateAnime(className, classLoader, info.packageName)
                AniyomiExtensionKind.MANGA -> mangaSources += instantiateManga(className, classLoader, info.packageName)
            }
        }

        if (animeSources.isEmpty() && mangaSources.isEmpty()) {
            throw BackendException(
                "Extension ${info.packageName} produced no sources",
                ErrorCode.EXTENSION_LOAD_FAILED,
            )
        }

        val capabilities = when (kind) {
            AniyomiExtensionKind.ANIME -> ExtensionCapabilities(
                search = animeSources.any { it is AnimeCatalogueSource },
                popular = animeSources.any { it is AnimeCatalogueSource },
                latest = animeSources.any { it is AnimeCatalogueSource && it.supportsLatest },
                details = true,
                episodes = true,
                streams = true,
                subtitles = true,
                audioTracks = true,
                preferences = animeSources.any { it is ConfigurableAnimeSource },
                manga = false,
            )

            AniyomiExtensionKind.MANGA -> ExtensionCapabilities(
                search = mangaSources.any { it is CatalogueSource },
                popular = mangaSources.any { it is CatalogueSource },
                latest = mangaSources.any { it is CatalogueSource && it.supportsLatest },
                details = true,
                episodes = false,
                streams = false,
                manga = true,
                chapters = true,
                pages = true,
            )
        }

        val sourceInfos = when (kind) {
            AniyomiExtensionKind.ANIME -> animeSources.map { source ->
                val catalogue = source as? AnimeCatalogueSource
                ExtensionSourceInfo(
                    id = source.id.toString(),
                    name = source.name,
                    language = catalogue?.lang,
                    baseUrl = (source as? AnimeHttpSource)?.baseUrl,
                    supportsSearch = catalogue != null,
                    supportsPopular = catalogue != null,
                    supportsLatest = catalogue?.supportsLatest == true,
                )
            }

            AniyomiExtensionKind.MANGA -> mangaSources.map { source ->
                val catalogue = source as? CatalogueSource
                ExtensionSourceInfo(
                    id = source.id.toString(),
                    name = source.name,
                    language = source.lang,
                    baseUrl = null,
                    supportsSearch = catalogue != null,
                    supportsPopular = catalogue != null,
                    supportsLatest = catalogue?.supportsLatest == true,
                    supportsDetails = true,
                    supportsEpisodes = false,
                    supportsStreams = false,
                )
            }
        }

        val language = sourceInfos.mapNotNull { it.language?.takeIf(String::isNotBlank) }.distinct().let {
            when (it.size) {
                0 -> metadata.language
                1 -> it.first()
                else -> "all"
            }
        }

        val extension = Extension(
            id = metadata.packageName,
            metadata = metadata.copy(language = language, sizeBytes = file.length()),
            backend = BackendType.ANIYOMI,
            repositoryId = repositoryId,
            isInstalled = true,
            capabilities = capabilities,
            sources = sourceInfos,
        )

        Log.i(
            TAG,
            "Loaded ${kind.name.lowercase()} extension ${metadata.name} (${metadata.packageName}) with " +
                "${animeSources.size + mangaSources.size} source(s)",
        )
        return AniyomiLoadedExtension(
            extension = extension,
            kind = kind,
            animeSources = animeSources,
            mangaSources = mangaSources,
            factoryClassName = AniyomiPackage.factoryClass(info),
            classLoader = classLoader,
        )
    }

    /** Builds an [Extension] with a load error instead of throwing, so failures stay visible. */
    fun failedExtension(info: PackageInfo, file: File, error: ExtensionError): Extension = Extension(
        id = info.packageName,
        metadata = AniyomiPackage.metadata(context, info).copy(sizeBytes = file.length()),
        backend = BackendType.ANIYOMI,
        isInstalled = true,
        capabilities = ExtensionCapabilities(manga = AniyomiPackage.kind(info) == AniyomiExtensionKind.MANGA),
        loadError = error,
    )

    private fun createClassLoader(file: File): ClassLoader {
        // From Android 14 (API 34) ART refuses to load code from a writable file, and extensions are
        // copied into the app's own data directory, which is writable. Re-label the APK before the
        // loader opens it, otherwise loading throws "Writable dex file ... is not allowed".
        markReadOnly(file)
        val attempts = listOf<() -> ClassLoader>(
            { ChildFirstPathClassLoader(file.absolutePath, null, parent) },
            { PathClassLoader(file.absolutePath, null, parent) },
        )
        var last: Throwable? = null
        for (attempt in attempts) {
            try {
                return attempt()
            } catch (t: Throwable) {
                last = t
            }
        }
        throw BackendException(
            "Could not create a class loader for ${file.name}" +
                (last?.let { ": ${it.javaClass.simpleName}: ${it.message}" } ?: ""),
            ErrorCode.EXTENSION_LOAD_FAILED,
            last,
        )
    }

    /** Marks a dynamically loaded package read-only; returns false when the platform refuses. */
    private fun markReadOnly(file: File): Boolean {
        if (!file.canWrite()) return true
        val marked = runCatching { file.setReadOnly() }.getOrDefault(false)
        if (!marked) Log.w(TAG, "Could not mark ${file.name} read-only before loading it")
        return marked
    }

    private fun instantiateAnime(className: String, classLoader: ClassLoader, packageName: String): List<AnimeSource> = try {
        val clazz = Class.forName(className, false, classLoader)
        when (val instance = clazz.getDeclaredConstructor().newInstance()) {
            is AnimeSource -> listOf(instance)
            is AnimeSourceFactory -> instance.createSources()
            else -> throw BackendException(
                "Class $className is neither an AnimeSource nor an AnimeSourceFactory",
                ErrorCode.EXTENSION_LOAD_FAILED,
            )
        }
    } catch (e: BackendException) {
        throw e
    } catch (e: Throwable) {
        throw BackendException(
            "Failed to instantiate $className from $packageName: ${e.javaClass.simpleName}: ${e.message}",
            ErrorCode.EXTENSION_LOAD_FAILED,
            e,
        )
    }

    private fun instantiateManga(className: String, classLoader: ClassLoader, packageName: String): List<Source> = try {
        val clazz = Class.forName(className, false, classLoader)
        when (val instance = clazz.getDeclaredConstructor().newInstance()) {
            is Source -> listOf(instance)
            is SourceFactory -> instance.createSources()
            else -> throw BackendException(
                "Class $className is neither a Source nor a SourceFactory",
                ErrorCode.EXTENSION_LOAD_FAILED,
            )
        }
    } catch (e: BackendException) {
        throw e
    } catch (e: Throwable) {
        throw BackendException(
            "Failed to instantiate $className from $packageName: ${e.javaClass.simpleName}: ${e.message}",
            ErrorCode.EXTENSION_LOAD_FAILED,
            e,
        )
    }

    private companion object {
        const val TAG = "AniyomiLoader"
    }
}
