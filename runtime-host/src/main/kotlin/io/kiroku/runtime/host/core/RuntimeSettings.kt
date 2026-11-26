package io.kiroku.runtime.host.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-visible settings of the Runtime Host, mirrored by the Extension Manager screen.
 *
 * Anime packages (Anikku API) and manga packages (Mihon API) are installed by the same backend, so
 * both sets of settings have a concrete effect.
 */
data class HostSettings(
    /** Copy installed Aniyomi anime APKs into the runtime's private store. */
    val aniyomiInternalAnimeInstalls: Boolean = true,
    /** Copy installed Mihon manga APKs into the runtime's private store. */
    val aniyomiInternalMangaInstalls: Boolean = true,
    /** Directory the user picked as their custom Aniyomi anime APK path. */
    val aniyomiAnimeApkPath: String? = null,
    /** Directory the user picked as their custom manga APK path. */
    val aniyomiMangaApkPath: String? = null,
    /** Packages (other than same-signature ones) allowed to bind to the runtime. */
    val allowedClients: Set<String> = emptySet(),
)

class RuntimeSettings(context: Context) {

    private val prefs = context.getSharedPreferences("runtime_host_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<HostSettings> = _state.asStateFlow()

    val value: HostSettings get() = _state.value

    fun setAniyomiInternalAnimeInstalls(enabled: Boolean) = update {
        it.copy(aniyomiInternalAnimeInstalls = enabled)
    }

    fun setAniyomiAnimeApkPath(path: String?) = update {
        it.copy(aniyomiAnimeApkPath = path?.takeIf { value -> value.isNotBlank() })
    }

    fun setAniyomiInternalMangaInstalls(enabled: Boolean) = update {
        it.copy(aniyomiInternalMangaInstalls = enabled)
    }

    fun setAniyomiMangaApkPath(path: String?) = update {
        it.copy(aniyomiMangaApkPath = path?.takeIf { value -> value.isNotBlank() })
    }

    fun setClientAllowed(packageName: String, allowed: Boolean) = update {
        if (allowed) it.copy(allowedClients = it.allowedClients + packageName)
        else it.copy(allowedClients = it.allowedClients - packageName)
    }

    fun isClientAllowed(packageName: String): Boolean = value.allowedClients.contains(packageName)

    private fun update(transform: (HostSettings) -> HostSettings) {
        val next = transform(_state.value)
        prefs.edit()
            .putBoolean(KEY_INTERNAL_ANIME, next.aniyomiInternalAnimeInstalls)
            .putBoolean(KEY_INTERNAL_MANGA, next.aniyomiInternalMangaInstalls)
            .putString(KEY_ANIME_PATH, next.aniyomiAnimeApkPath)
            .putString(KEY_MANGA_PATH, next.aniyomiMangaApkPath)
            .putStringSet(KEY_CLIENTS, next.allowedClients)
            .apply()
        _state.value = next
    }

    private fun read(): HostSettings = HostSettings(
        aniyomiInternalAnimeInstalls = prefs.getBoolean(KEY_INTERNAL_ANIME, true),
        aniyomiInternalMangaInstalls = prefs.getBoolean(KEY_INTERNAL_MANGA, true),
        aniyomiAnimeApkPath = prefs.getString(KEY_ANIME_PATH, null),
        aniyomiMangaApkPath = prefs.getString(KEY_MANGA_PATH, null),
        allowedClients = prefs.getStringSet(KEY_CLIENTS, emptySet()).orEmpty(),
    )

    private companion object {
        const val KEY_INTERNAL_ANIME = "aniyomi_internal_anime"
        const val KEY_INTERNAL_MANGA = "aniyomi_internal_manga"
        const val KEY_ANIME_PATH = "aniyomi_anime_path"
        const val KEY_MANGA_PATH = "aniyomi_manga_path"
        const val KEY_CLIENTS = "allowed_clients"
    }
}
