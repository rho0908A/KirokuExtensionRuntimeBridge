package io.kiroku.runtime.host.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.kiroku.runtime.host.core.RuntimeManager
import io.kiroku.runtime.host.core.toExtensionError
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.InstallationProgress
import io.kiroku.runtime.ipc.LogEntry
import io.kiroku.runtime.ipc.RuntimeInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state holder for the Runtime Host screens. */
class RuntimeHostViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = RuntimeManager.get(application)

    val settings = manager.settings.state
    val installed = manager.installed
    val available = manager.available
    val repositories = manager.repositories.state
    val logs = manager.logs.entries

    private val _info = MutableStateFlow<RuntimeInfo?>(null)
    val info: StateFlow<RuntimeInfo?> = _info.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _progress = MutableStateFlow<InstallationProgress?>(null)
    val progress: StateFlow<InstallationProgress?> = _progress.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            runCatching { manager.initialize() }
                .onFailure { notify("Runtime initialisation failed: ${it.message}") }
            refreshInfo()
        }
        viewModelScope.launch {
            manager.progress.collect { _progress.value = it }
        }
        viewModelScope.launch {
            manager.installed.collect { refreshInfo() }
        }
    }

    fun refreshInfo() {
        viewModelScope.launch {
            _info.value = runCatching { manager.runtimeInfo() }.getOrNull()
        }
    }

    fun refreshCatalogue() = run("Refreshing repositories") {
        val extensions = manager.refreshAvailable()
        notify("${extensions.size} extension(s) available")
    }

    fun install(extensionId: String) = run("Installing $extensionId") {
        val extension = manager.install(extensionId)
        notify(if (extension.loadError == null) "Installed ${extension.metadata.name}" else "Installed, but loading failed")
    }

    fun update(extensionId: String) = run("Updating $extensionId") {
        val extension = manager.update(extensionId)
        notify("Updated ${extension.metadata.name} to ${extension.metadata.versionName}")
    }

    fun updateAll() = run("Updating extensions") {
        manager.refreshAvailable()
        val updated = manager.updateAll()
        notify(if (updated.isEmpty()) "Everything is up to date" else "Updated ${updated.size} extension(s)")
    }

    /** Reinstalls every installed extension from its repository, ignoring the stored copy. */
    fun forceRedownload() = run("Re-downloading extensions") {
        manager.refreshAvailable()
        val ids = manager.installed.value.map { it.id }
        if (ids.isEmpty()) {
            notify("No extensions installed")
            return@run
        }
        var failures = 0
        ids.forEach { id ->
            runCatching { manager.install(id) }.onFailure { failures++ }
        }
        notify(if (failures == 0) "Reinstalled ${ids.size} extension(s)" else "Reinstalled with $failures failure(s)")
    }

    fun uninstall(extensionId: String) = run("Uninstalling $extensionId") {
        val removed = manager.uninstall(extensionId)
        notify(if (removed) "Uninstalled $extensionId" else "Nothing to uninstall")
    }

    fun rollback(extensionId: String) = run("Rolling back $extensionId") {
        val extension = manager.rollback(extensionId)
        notify("Rolled back ${extension.metadata.name} to ${extension.metadata.versionName}")
    }

    fun importFromCustomPath() = run("Importing from custom path") {
        val count = manager.importFromCustomPath()
        notify("Imported $count extension(s)")
    }

    fun installFromFile(path: String) = run("Installing package") {
        val extension = manager.installFile(path)
        notify("Installed ${extension.metadata.name}")
    }

    fun addRepository(url: String, backend: BackendType, name: String?) = run("Adding repository") {
        val repository = manager.addRepository(url, backend, name)
        notify("Added ${repository.name}")
    }

    fun removeRepository(id: String) = run("Removing repository") {
        notify(if (manager.removeRepository(id)) "Repository removed" else "Repository not found")
    }

    fun setClientAllowed(packageName: String, allowed: Boolean) {
        manager.settings.setClientAllowed(packageName, allowed)
        notify(if (allowed) "$packageName can now bind" else "$packageName blocked")
    }

    fun setInternalAnimeInstalls(enabled: Boolean) {
        manager.settings.setAniyomiInternalAnimeInstalls(enabled)
        notify(if (enabled) "Installs stay inside the runtime" else "Installs are mirrored to the custom path")
    }

    fun setAnimePath(path: String) {
        manager.settings.setAniyomiAnimeApkPath(path)
        notify("Custom anime APK path updated")
    }

    fun clearLogs() {
        manager.logs.clear()
    }

    /** Number of packages kept for [extensionId] before the last update. */
    fun availableBackupCount(extensionId: String): Int = manager.availableBackups(extensionId).size

    /** Surfaces a message from the UI layer (e.g. a failed file pick). */
    fun report(message: String) = notify(message)

    private fun run(label: String, block: suspend () -> Unit) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = label
            _progress.value = null
            try {
                block()
            } catch (t: Throwable) {
                val error = t.toExtensionError()
                notify("$label failed: ${error.message}")
            } finally {
                _busy.value = null
                _progress.value = null
                refreshInfo()
            }
        }
    }

    private fun notify(message: String) {
        _messages.tryEmit(message)
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                RuntimeHostViewModel(application) as T
        }
    }
}

/** Convenience for log rendering. */
fun LogEntry.displayTime(): String {
    val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
    return format.format(java.util.Date(timestamp))
}
