package io.kiroku.runtime.host.core

import android.content.Context
import android.os.Build
import io.kiroku.runtime.backend.BackendException
import io.kiroku.runtime.backend.ExtensionBackend
import io.kiroku.runtime.backend.aniyomi.AniyomiBackend
import io.kiroku.runtime.backend.cloudstream.CloudStreamBackend
import io.kiroku.runtime.ipc.Anime
import io.kiroku.runtime.ipc.AnimeDetails
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.Episode
import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.Extension
import io.kiroku.runtime.ipc.ExtensionRepository
import io.kiroku.runtime.ipc.InstallationProgress
import io.kiroku.runtime.ipc.RepositoryExtension
import io.kiroku.runtime.ipc.RuntimeCapabilities
import io.kiroku.runtime.ipc.RuntimeEvent
import io.kiroku.runtime.ipc.RuntimeEventType
import io.kiroku.runtime.ipc.RuntimeInfo
import io.kiroku.runtime.ipc.RuntimeParams
import io.kiroku.runtime.ipc.RuntimeProtocol
import io.kiroku.runtime.ipc.StreamSource
import io.kiroku.runtime.ipc.jsonOf
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single source of truth for the Runtime Host process.
 *
 * It owns the two independent backends, the repository list, settings, logs and the installation
 * pipeline. Both the UI and the Binder service call into this object, so a host app and the bundled
 * management screen always observe the same state.
 */
class RuntimeManager private constructor(private val context: Context) {

    val settings = RuntimeSettings(context)
    val logs = LogStore()
    val repositories = RepositoryStore(context, logs)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initMutex = Mutex()
    private val installMutex = Mutex()

    private val aniyomi = AniyomiBackend(context)
    private val cloudstream = CloudStreamBackend(context)
    private val backends: List<ExtensionBackend> = listOf(aniyomi, cloudstream)

    private val backupsDir = File(context.filesDir, "backups")

    private val _installed = MutableStateFlow<List<Extension>>(emptyList())
    val installed: StateFlow<List<Extension>> = _installed.asStateFlow()

    private val _available = MutableStateFlow<List<RepositoryExtension>>(emptyList())
    val available: StateFlow<List<RepositoryExtension>> = _available.asStateFlow()

    private val _progress = MutableSharedFlow<InstallationProgress>(extraBufferCapacity = 64)
    val progress: SharedFlow<InstallationProgress> = _progress.asSharedFlow()

    private val _events = MutableSharedFlow<RuntimeEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<RuntimeEvent> = _events.asSharedFlow()

    @Volatile
    private var initialised = false

    init {
        scope.launch {
            combine(aniyomi.installed, cloudstream.installed) { a, c -> a + c }
                .collect { _installed.value = it }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    suspend fun initialize() {
        initMutex.withLock {
            if (initialised) return
            repositories.ensureSeeded()
            backends.forEach { backend ->
                try {
                    backend.initialize()
                    val loaded = backend.installed.value
                    logs.info(TAG, "${backend.type} backend ready (${loaded.size} extension(s))")
                } catch (e: Throwable) {
                    logs.error(TAG, "${backend.type} backend failed to initialise: ${e.message}")
                }
            }
            initialised = true
        }
    }

    suspend fun runtimeInfo(): RuntimeInfo {
        val packageInfo = packageInfo()
        return RuntimeInfo(
            protocolVersion = RuntimeProtocol.PROTOCOL_VERSION,
            runtimeVersion = packageInfo?.versionName ?: "unknown",
            runtimeVersionCode = packageInfo?.versionCode ?: 0L,
            androidSdk = Build.VERSION.SDK_INT,
            capabilities = RuntimeCapabilities(
                backends = backends.map { it.type },
                repositoryManagement = true,
                extensionInstallation = true,
                extensionUpdates = true,
                streamExtraction = true,
                subtitleExtraction = true,
                audioTrackExtraction = true,
                runtimeLogs = true,
            ),
            installedExtensions = _installed.value.size,
            backends = backends.map { it.info },
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Extensions
    // ---------------------------------------------------------------------------------------------

    suspend fun installedExtensions(): List<Extension> {
        initialize()
        return _installed.value
    }

    suspend fun reload(): List<Extension> {
        initialize()
        backends.forEach { backend ->
            try {
                backend.reload()
                logs.info(TAG, "${backend.type} reloaded")
            } catch (e: Throwable) {
                logs.error(TAG, "${backend.type} reload failed: ${e.message}")
            }
        }
        return _installed.value
    }

    /** Refreshes every enabled repository and returns the merged catalogue. */
    suspend fun refreshAvailable(): List<RepositoryExtension> {
        initialize()
        val merged = mutableListOf<RepositoryExtension>()
        repositories.value.filter { it.enabled }.forEach { repository ->
            val backend = backendFor(repository.backend)
            try {
                val remote = backend.availableExtensions(repository)
                merged += remote
                repositories.markRefreshed(repository.id, null)
                logs.info(TAG, "${repository.name}: ${remote.size} extension(s)")
            } catch (e: Throwable) {
                val error = e.toExtensionError(repository.backend)
                repositories.markRefreshed(repository.id, error)
                logs.error(TAG, "${repository.name} refresh failed: ${error.message}")
            }
        }
        _available.value = merged.distinctBy { it.id }
        return _available.value
    }

    suspend fun install(extensionId: String): Extension = installMutex.withLock {
        initialize()
        val remote = resolveRemote(extensionId)
        val backend = backendFor(remote.backend)
        backupInstalled(extensionId, backend)
        logs.info(TAG, "Installing ${remote.name} ${remote.versionName} (${remote.backend})")
        val extension = backend.installFromRepository(remote) { publishProgress(it) }
        logLoaded(extension)
        mirrorToCustomPath(extension)
        extension
    }

    suspend fun installFile(path: String): Extension = installMutex.withLock {
        initialize()
        val file = File(path)
        if (!file.isFile) {
            throw BackendException("Package not found: $path", ErrorCode.EXTENSION_INVALID_PACKAGE)
        }
        val aniyomiInspection = runCatching { aniyomi.inspectPackage(file) }.getOrNull()
        val backend: ExtensionBackend = when {
            aniyomiInspection?.valid == true -> aniyomi
            runCatching { cloudstream.inspectPackage(file) }.getOrNull()?.valid == true -> cloudstream
            else -> throw BackendException(
                "Not a supported package: expected an Aniyomi extension APK or a CloudStream .cs3 plugin",
                ErrorCode.EXTENSION_INVALID_PACKAGE,
            )
        }
        backupInstalled(aniyomiInspection?.metadata?.packageName ?: file.nameWithoutExtension, backend)
        val extension = backend.installPackage(file, null) { publishProgress(it) }
        logLoaded(extension)
        mirrorToCustomPath(extension)
        extension
    }

    suspend fun update(extensionId: String): Extension = install(extensionId)

    suspend fun updateAll(): List<Extension> {
        val targets = _installed.value.filter { installedExtension ->
            _available.value.any { it.id == installedExtension.id && it.versionCode > installedExtension.metadata.versionCode }
        }
        if (_available.value.isEmpty()) refreshAvailable()
        return targets.mapNotNull { target ->
            runCatching { update(target.id) }.onFailure {
                logs.error(TAG, "Update of ${target.id} failed: ${it.message}")
            }.getOrNull()
        }
    }

    suspend fun uninstall(extensionId: String): Boolean {
        initialize()
        val backend = backendForExtensionOrNull(extensionId)
            ?: throw BackendException("$extensionId is not installed", ErrorCode.EXTENSION_NOT_FOUND)
        val removed = backend.uninstall(extensionId)
        if (removed) {
            logs.info(TAG, "Uninstalled $extensionId")
            _events.tryEmit(RuntimeEvent.of(RuntimeEventType.UNINSTALLED, jsonOf(RuntimeParams.EXTENSION_ID to extensionId)))
        }
        return removed
    }

    /** Imports every Aniyomi APK found in the user-selected custom APK path. */
    suspend fun importFromCustomPath(): Int {
        initialize()
        val path = settings.value.aniyomiAnimeApkPath
            ?: throw BackendException("No custom anime APK path is configured", ErrorCode.INVALID_REQUEST)
        val directory = File(path)
        if (!directory.isDirectory) {
            throw BackendException("$path is not a readable directory", ErrorCode.STORAGE)
        }
        val packages = directory.listFiles { file -> file.isFile && file.name.endsWith(".apk", true) }.orEmpty()
        if (packages.isEmpty()) {
            throw BackendException("No .apk files in $path", ErrorCode.EXTENSION_INVALID_PACKAGE)
        }
        var imported = 0
        packages.forEach { file ->
            try {
                aniyomi.installPackage(file, null) { publishProgress(it) }
                imported++
                logs.info(TAG, "Imported ${file.name}")
            } catch (e: Throwable) {
                logs.error(TAG, "Import of ${file.name} failed: ${e.message}")
            }
        }
        return imported
    }

    /** Restores the most recent package the runtime stored before an update. */
    suspend fun rollback(extensionId: String): Extension {
        val backup = backupsDir.listFiles { file -> file.isFile && file.name.startsWith("$extensionId.") }
            ?.maxByOrNull { it.lastModified() }
            ?: throw BackendException("No previous version of $extensionId is stored", ErrorCode.EXTENSION_NOT_FOUND)
        logs.info(TAG, "Rolling $extensionId back to ${backup.name}")
        return installFile(backup.absolutePath)
    }

    fun availableBackups(extensionId: String): List<String> =
        backupsDir.listFiles { file -> file.isFile && file.name.startsWith("$extensionId.") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { it.name }
            .orEmpty()

    // ---------------------------------------------------------------------------------------------
    // Source operations
    // ---------------------------------------------------------------------------------------------

    suspend fun search(extensionId: String, sourceId: String?, query: String, page: Int): List<Anime> =
        backendForExtension(extensionId).search(extensionId, sourceId, query, page)

    suspend fun popular(extensionId: String, sourceId: String?, page: Int): List<Anime> =
        backendForExtension(extensionId).popular(extensionId, sourceId, page)

    suspend fun latest(extensionId: String, sourceId: String?, page: Int): List<Anime> =
        backendForExtension(extensionId).latest(extensionId, sourceId, page)

    suspend fun details(extensionId: String, sourceId: String?, animeUrl: String): AnimeDetails =
        backendForExtension(extensionId).details(extensionId, sourceId, animeUrl)

    suspend fun episodes(extensionId: String, sourceId: String?, animeUrl: String): List<Episode> =
        backendForExtension(extensionId).episodes(extensionId, sourceId, animeUrl)

    suspend fun streams(extensionId: String, sourceId: String?, episodeUrl: String): List<StreamSource> =
        backendForExtension(extensionId).streams(extensionId, sourceId, episodeUrl)

    // ---------------------------------------------------------------------------------------------
    // Repositories
    // ---------------------------------------------------------------------------------------------

    suspend fun addRepository(url: String, backend: BackendType, name: String?): ExtensionRepository {
        initialize()
        val repository = repositories.add(url, backend, name)
        runCatching { refreshRepository(repository) }
            .onFailure { logs.warn(TAG, "New repository ${repository.name} is not reachable yet: ${it.message}") }
        return repository
    }

    suspend fun removeRepository(repositoryId: String): Boolean {
        val removed = repositories.remove(repositoryId)
        if (removed) {
            _available.value = _available.value.filterNot { it.repositoryId == repositoryId }
        }
        return removed
    }

    private suspend fun refreshRepository(repository: ExtensionRepository) {
        val backend = backendFor(repository.backend)
        val remote = backend.availableExtensions(repository)
        repositories.markRefreshed(repository.id, null)
        _available.value = (_available.value.filterNot { it.repositoryId == repository.id } + remote)
            .distinctBy { it.id }
    }

    // ---------------------------------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------------------------------

    fun backendFor(type: BackendType): ExtensionBackend = when (type) {
        BackendType.ANIYOMI -> aniyomi
        BackendType.CLOUDSTREAM -> cloudstream
        BackendType.UNKNOWN -> throw BackendException("Unknown backend", ErrorCode.EXTENSION_UNSUPPORTED)
    }

    private fun backendForExtension(extensionId: String): ExtensionBackend =
        backendForExtensionOrNull(extensionId)
            ?: throw BackendException("$extensionId is not installed", ErrorCode.EXTENSION_NOT_FOUND)

    private fun backendForExtensionOrNull(extensionId: String): ExtensionBackend? =
        _installed.value.firstOrNull { it.id == extensionId }?.let { backendFor(it.backend) }

    private suspend fun resolveRemote(extensionId: String): RepositoryExtension {
        _available.value.firstOrNull { it.id == extensionId }?.let { return it }
        refreshAvailable()
        return _available.value.firstOrNull { it.id == extensionId }
            ?: throw BackendException(
                "Extension $extensionId is not published by any enabled repository",
                ErrorCode.EXTENSION_NOT_FOUND,
            )
    }

    private fun packageFile(backend: ExtensionBackend, extensionId: String): File? =
        backend.storageDirectory.listFiles()
            ?.firstOrNull { it.isFile && it.nameWithoutExtension == extensionId }

    private suspend fun backupInstalled(extensionId: String, backend: ExtensionBackend) = withContext(Dispatchers.IO) {
        val current = packageFile(backend, extensionId) ?: return@withContext
        runCatching {
            backupsDir.mkdirs()
            val extension = current.extension.ifBlank { backend.type.name.lowercase() }
            val target = File(backupsDir, "$extensionId.${System.currentTimeMillis()}.$extension")
            current.copyTo(target, overwrite = true)
            val keep = backupsDir.listFiles { file -> file.isFile && file.name.startsWith("$extensionId.") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(3)
                .orEmpty()
            keep.forEach { it.delete() }
            logs.info(TAG, "Backed up ${current.name} before updating")
        }.onFailure { logs.warn(TAG, "Could not back up $extensionId: ${it.message}") }
    }

    /**
     * When the user disables "internal" installs and configured a custom APK path, keep a copy of the
     * package there so it can be shared or re-imported without the runtime store.
     */
    private suspend fun mirrorToCustomPath(extension: Extension) = withContext(Dispatchers.IO) {
        if (settings.value.aniyomiInternalAnimeInstalls) return@withContext
        if (extension.backend != BackendType.ANIYOMI) return@withContext
        val path = settings.value.aniyomiAnimeApkPath ?: return@withContext
        val backend = backendFor(extension.backend)
        val current = packageFile(backend, extension.id) ?: return@withContext
        runCatching {
            File(path).mkdirs()
            current.copyTo(File(path, current.name), overwrite = true)
            logs.info(TAG, "Mirrored ${current.name} to $path")
        }.onFailure { logs.warn(TAG, "Could not mirror ${current.name} to $path: ${it.message}") }
    }

    private fun logLoaded(extension: Extension) {
        val error = extension.loadError
        if (error != null) {
            logs.error(TAG, "${extension.metadata.name} installed but failed to load: ${error.message}")
            _events.tryEmit(
                RuntimeEvent.of(
                    RuntimeEventType.EXTENSION_FAILED,
                    jsonOf(RuntimeParams.EXTENSION_ID to extension.id, "message" to error.message),
                ),
            )
        } else {
            logs.info(TAG, "Loaded ${extension.metadata.name} ${extension.metadata.versionName}")
            _events.tryEmit(
                RuntimeEvent.of(
                    RuntimeEventType.EXTENSION_LOADED,
                    jsonOf(RuntimeParams.EXTENSION_ID to extension.id, RuntimeParams.NAME to extension.metadata.name),
                ),
            )
        }
        _events.tryEmit(
            RuntimeEvent.of(
                RuntimeEventType.INSTALL_COMPLETED,
                jsonOf(RuntimeParams.EXTENSION_ID to extension.id, RuntimeParams.BACKEND to extension.backend.name),
            ),
        )
    }

    private fun publishProgress(progress: InstallationProgress) {
        _progress.tryEmit(progress)
        _events.tryEmit(
            RuntimeEvent.of(
                RuntimeEventType.INSTALL_PROGRESS,
                jsonOf(
                    RuntimeParams.EXTENSION_ID to progress.extensionId,
                    RuntimeParams.BACKEND to progress.backend.name,
                    "phase" to progress.phase,
                    "bytesDownloaded" to progress.bytesDownloaded,
                    "totalBytes" to progress.totalBytes,
                ),
            ),
        )
    }

    private fun packageInfo(): PackageInfoCompat? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        PackageInfoCompat(versionName = info.versionName, versionCode = code)
    }.getOrNull()

    private data class PackageInfoCompat(val versionName: String?, val versionCode: Long)

    companion object {
        private const val TAG = "RuntimeManager"

        @Volatile
        private var instance: RuntimeManager? = null

        fun get(context: Context): RuntimeManager =
            instance ?: synchronized(this) {
                instance ?: RuntimeManager(context.applicationContext).also { instance = it }
            }
    }
}
