package io.kiroku.runtime.host.core

import android.content.Context
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.ExtensionError
import io.kiroku.runtime.ipc.ExtensionRepository
import io.kiroku.runtime.ipc.RuntimeProtocol
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer

/**
 * Persisted repository list.
 *
 * Two real, publicly published repositories are seeded on first run so the runtime is usable out of
 * the box; they are ordinary entries the user can disable or delete. No extension data is invented:
 * everything listed comes from the repository index at refresh time.
 */
class RepositoryStore(context: Context, private val logs: LogStore) {

    private val prefs = context.getSharedPreferences("runtime_host_repositories", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(ExtensionRepository.serializer())

    private val _state = MutableStateFlow(load())
    val state: StateFlow<List<ExtensionRepository>> = _state.asStateFlow()

    val value: List<ExtensionRepository> get() = _state.value

    fun ensureSeeded() {
        if (prefs.contains(KEY_REPOSITORIES)) return
        save(defaults())
        logs.info(TAG, "Seeded ${defaults().size} built-in repositories")
    }

    fun add(url: String, backend: BackendType, name: String? = null): ExtensionRepository {
        val normalised = url.trim()
        require(normalised.startsWith("http://") || normalised.startsWith("https://")) {
            "Repository URL must be http(s)"
        }
        val repository = ExtensionRepository(
            id = UUID.randomUUID().toString(),
            name = name?.takeIf { it.isNotBlank() } ?: normalised.substringAfterLast('/').ifBlank { normalised },
            url = normalised,
            backend = backend,
        )
        save(value + repository)
        logs.info(TAG, "Added repository ${repository.name} (${repository.backend})")
        return repository
    }

    fun remove(id: String): Boolean {
        val next = value.filterNot { it.id == id }
        if (next.size == value.size) return false
        save(next)
        logs.info(TAG, "Removed repository $id")
        return true
    }

    fun setEnabled(id: String, enabled: Boolean) {
        save(value.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun markRefreshed(id: String, error: ExtensionError?) {
        save(
            value.map {
                if (it.id == id) {
                    it.copy(
                        lastRefreshedAt = if (error == null) System.currentTimeMillis() else it.lastRefreshedAt,
                        lastError = error,
                    )
                } else {
                    it
                }
            },
        )
    }

    fun find(id: String): ExtensionRepository? = value.firstOrNull { it.id == id }

    private fun save(repositories: List<ExtensionRepository>) {
        prefs.edit().putString(KEY_REPOSITORIES, RuntimeProtocol.JSON.encodeToString(serializer, repositories)).apply()
        _state.value = repositories
    }

    private fun load(): List<ExtensionRepository> {
        val raw = prefs.getString(KEY_REPOSITORIES, null) ?: return defaults()
        return runCatching { RuntimeProtocol.JSON.decodeFromString(serializer, raw) }
            .getOrElse {
                logs.warn(TAG, "Repository list was unreadable, restoring defaults: ${it.message}")
                defaults()
            }
    }

    private fun defaults(): List<ExtensionRepository> = listOf(
        ExtensionRepository(
            id = BUILTIN_ANIYOMI,
            name = "Yūzōnō Anime Extensions",
            url = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
            backend = BackendType.ANIYOMI,
            isBuiltIn = true,
        ),
        ExtensionRepository(
            id = BUILTIN_CLOUDSTREAM,
            name = "Phisher CloudStream Extensions",
            url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/repo.json",
            backend = BackendType.CLOUDSTREAM,
            isBuiltIn = true,
        ),
    )

    private companion object {
        const val TAG = "Repositories"
        const val KEY_REPOSITORIES = "repositories_json"
        const val BUILTIN_ANIYOMI = "builtin-aniyomi-yuzono"
        const val BUILTIN_CLOUDSTREAM = "builtin-cloudstream-phisher"
    }
}
