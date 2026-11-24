package io.kiroku.runtime.backend.cloudstream

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.BasePlugin
import dalvik.system.DexClassLoader
import io.kiroku.runtime.backend.BackendException
import io.kiroku.runtime.backend.LoadedExtension
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.Extension
import io.kiroku.runtime.ipc.ExtensionCapabilities
import io.kiroku.runtime.ipc.ExtensionError
import io.kiroku.runtime.ipc.ExtensionMetadata
import io.kiroku.runtime.ipc.ExtensionSourceInfo
import java.io.File

/** A loaded CloudStream plugin together with the providers it registered. */
internal class CloudStreamLoadedPlugin(
    override val extension: Extension,
    val plugin: BasePlugin,
    val providers: List<MainAPI>,
    val classLoader: ClassLoader,
    val extraction: CloudStreamPackage.Extraction,
) : LoadedExtension {
    override val handle: Any get() = this
}

/**
 * Loads CloudStream `.cs3` plugins.
 *
 * Plugin classes are loaded with a [DexClassLoader] whose parent is the runtime, so the plugin links
 * against the CloudStream API shipped in this APK. `BasePlugin.load()` performs registration; the
 * loader observes `APIHolder` before and after so it knows exactly which providers belong to this
 * plugin (and can unregister them on unload).
 */
internal class CloudStreamPluginLoader(private val context: Context) {

    private val parent: ClassLoader = BasePlugin::class.java.classLoader
        ?: CloudStreamPluginLoader::class.java.classLoader!!

    fun inspect(file: File): Pair<CloudStreamPackage.Manifest?, ExtensionError?> = try {
        val manifest = CloudStreamPackage.readManifest(file)
        if (manifest.pluginClassName.isBlank()) {
            manifest to error("Plugin manifest does not declare pluginClassName")
        } else {
            manifest to null
        }
    } catch (e: Throwable) {
        null to error(e.message ?: "Invalid CloudStream plugin package")
    }

    fun load(file: File, pluginId: String, repositoryId: String?): CloudStreamLoadedPlugin {
        val workDir = File(context.filesDir, "extensions/cloudstream/$pluginId")
        val extraction = try {
            CloudStreamPackage.extract(file, workDir)
        } catch (e: Throwable) {
            throw BackendException(
                "Plugin package is invalid: ${e.message}",
                ErrorCode.EXTENSION_INVALID_PACKAGE,
                e,
            )
        }

        val optimized = File(context.codeCacheDir, "cloudstream/$pluginId").apply { mkdirs() }
        val loader = try {
            DexClassLoader(extraction.dexFile.absolutePath, optimized.absolutePath, null, parent)
        } catch (e: Throwable) {
            throw BackendException("Could not create plugin class loader", ErrorCode.EXTENSION_LOAD_FAILED, e)
        }

        val before = APIHolder.apis.toList()

        val plugin = try {
            val clazz = Class.forName(extraction.manifest.pluginClassName, true, loader)
            val instance = clazz.getDeclaredConstructor().newInstance()
            (instance as? BasePlugin) ?: throw BackendException(
                "${extraction.manifest.pluginClassName} does not extend BasePlugin",
                ErrorCode.EXTENSION_UNSUPPORTED,
            )
        } catch (e: BackendException) {
            throw e
        } catch (e: Throwable) {
            throw BackendException(
                "Could not instantiate plugin ${extraction.manifest.pluginClassName}: ${e.message}",
                ErrorCode.EXTENSION_LOAD_FAILED,
                e,
            )
        }

        try {
            plugin.load()
        } catch (e: Throwable) {
            Log.e(TAG, "Plugin ${pluginId} failed to load", e)
            throw BackendException(
                "Plugin ${extraction.manifest.name} failed during load(): ${e.message}",
                ErrorCode.EXTENSION_LOAD_FAILED,
                e,
            )
        }

        val after = APIHolder.apis.toList()
        val registered = after.filterNot { candidate -> before.any { it === candidate } }
        if (registered.isEmpty()) {
            throw BackendException(
                "Plugin ${extraction.manifest.name} registered no providers",
                ErrorCode.EXTENSION_LOAD_FAILED,
            )
        }

        val providers = registered.distinctBy { it.name }
        val sources = providers.mapIndexed { index, api ->
            ExtensionSourceInfo(
                id = api.name.ifBlank { "provider-$index" },
                name = api.name.ifBlank { "Provider ${index + 1}" },
                language = api.lang.takeIf { it.isNotBlank() },
                baseUrl = api.mainUrl.takeIf { it.isNotBlank() },
                supportsSearch = true,
                supportsPopular = api.hasMainPage,
                supportsLatest = api.hasMainPage,
                supportsDetails = true,
                supportsEpisodes = true,
                supportsStreams = true,
            )
        }

        val language = providers.map { it.lang }.filter { it.isNotBlank() }.distinct()
            .let { when (it.size) { 0 -> null; 1 -> it.first(); else -> "all" } }

        val extension = Extension(
            id = pluginId,
            metadata = ExtensionMetadata(
                name = extraction.manifest.name.ifBlank { pluginId },
                packageName = pluginId,
                versionName = extraction.manifest.version.toString(),
                versionCode = extraction.manifest.version.toLong(),
                language = language,
                libVersion = "cloudstream",
                sizeBytes = file.length(),
            ),
            backend = BackendType.CLOUDSTREAM,
            repositoryId = repositoryId,
            isInstalled = true,
            capabilities = ExtensionCapabilities(
                search = true,
                popular = providers.any { it.hasMainPage },
                latest = providers.any { it.hasMainPage },
                details = true,
                episodes = true,
                streams = true,
                subtitles = true,
                audioTracks = true,
            ),
            sources = sources,
        )

        Log.i(TAG, "Loaded CloudStream plugin ${extension.metadata.name} with ${providers.size} provider(s)")
        return CloudStreamLoadedPlugin(
            extension = extension,
            plugin = plugin,
            providers = providers,
            classLoader = loader,
            extraction = extraction,
        )
    }

    fun unload(loaded: CloudStreamLoadedPlugin) {
        runCatching { loaded.plugin.beforeUnload() }
        loaded.providers.forEach { api -> runCatching { APIHolder.removePluginMapping(api) } }
        runCatching { loaded.extraction.root.deleteRecursively() }
    }

    private fun error(message: String) = ExtensionError(
        code = ErrorCode.EXTENSION_INVALID_PACKAGE,
        message = message,
        backend = BackendType.CLOUDSTREAM,
    )

    private companion object {
        const val TAG = "CloudStreamLoader"
    }
}
