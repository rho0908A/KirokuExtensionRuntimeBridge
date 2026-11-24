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

/** A loaded Aniyomi extension: the bridge model plus the live source instances. */
internal class AniyomiLoadedExtension(
    override val extension: Extension,
    val sources: List<AnimeSource>,
    val factoryClassName: String?,
    val classLoader: ClassLoader,
) : LoadedExtension {
    override val handle: Any get() = this
}

/**
 * Instantiates Aniyomi extension APKs.
 *
 * The extension code is loaded with a child-first [PathClassLoader]: the APK's own classes come
 * first (extensions often bundle Jsoup/OkHttp), while everything the extension only references —
 * the Aniyomi API implemented by `aniyomi-api` — resolves through the runtime's own class loader.
 */
internal class AniyomiExtensionLoader(private val context: Context) {

    private val parent: ClassLoader = AnimeSource::class.java.classLoader
        ?: AniyomiExtensionLoader::class.java.classLoader!!

    fun load(file: File, info: PackageInfo, repositoryId: String? = null): AniyomiLoadedExtension {
        val metadata = AniyomiPackage.metadata(context, info)
        val classNames = AniyomiPackage.sourceClasses(info)
        if (classNames.isEmpty()) {
            throw BackendException(
                "Extension ${info.packageName} declares no source class",
                ErrorCode.EXTENSION_INVALID_PACKAGE,
            )
        }

        val classLoader = createClassLoader(file)

        val sources = mutableListOf<AnimeSource>()
        for (className in classNames) {
            sources += instantiate(className, classLoader, info.packageName)
        }
        if (sources.isEmpty()) {
            throw BackendException(
                "Extension ${info.packageName} produced no sources",
                ErrorCode.EXTENSION_LOAD_FAILED,
            )
        }

        val capabilities = ExtensionCapabilities(
            search = sources.any { it is AnimeCatalogueSource },
            popular = sources.any { it is AnimeCatalogueSource },
            latest = sources.any { it is AnimeCatalogueSource && it.supportsLatest },
            details = true,
            episodes = true,
            streams = true,
            subtitles = true,
            audioTracks = true,
            preferences = sources.any { it is ConfigurableAnimeSource },
        )

        val sourceInfos = sources.map { source ->
            val catalogue = source as? AnimeCatalogueSource
            ExtensionSourceInfo(
                id = source.id.toString(),
                name = source.name,
                language = source.lang,
                baseUrl = (source as? AnimeHttpSource)?.baseUrl,
                supportsSearch = catalogue != null,
                supportsPopular = catalogue != null,
                supportsLatest = catalogue?.supportsLatest == true,
            )
        }

        val language = sourceInfos.mapNotNull { it.language }.distinct().let {
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

        Log.i(TAG, "Loaded Aniyomi extension ${metadata.name} (${metadata.packageName}) with ${sources.size} source(s)")
        return AniyomiLoadedExtension(
            extension = extension,
            sources = sources,
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
        loadError = error,
    )

    private fun createClassLoader(file: File): ClassLoader {
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
            "Could not create a class loader for ${file.name}",
            ErrorCode.EXTENSION_LOAD_FAILED,
            last,
        )
    }

    private fun instantiate(className: String, classLoader: ClassLoader, packageName: String): List<AnimeSource> = try {
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

    private companion object {
        const val TAG = "AniyomiLoader"
    }
}
