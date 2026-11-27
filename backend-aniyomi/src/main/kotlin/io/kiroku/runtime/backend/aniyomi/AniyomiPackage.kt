package io.kiroku.runtime.backend.aniyomi

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.kiroku.runtime.ipc.BackendType
import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.ExtensionError
import io.kiroku.runtime.ipc.ExtensionMetadata
import java.io.File

/** The two package formats the Aniyomi backend understands. */
enum class AniyomiExtensionKind {
    /** Animiru anime extension (`tachiyomi.animeextension`, lib 14, 16, 17). */
    ANIME,

    /** Mihon manga extension (`tachiyomi.extension`, lib 1.4-1.6). */
    MANGA,
}

/**
 * Reads and validates Animiru anime and Mihon manga extension APKs without executing the extension
 * code.
 *
 * The backend keeps the `aniyomi` name, but the contracts come from the two hosts that replaced the
 * archived Aniyomi app:
 *  - anime: Animiru (an Aniyomi fork) `ExtensionLoader` - feature `tachiyomi.animeextension`,
 *    metadata `tachiyomi.animeextension.class` / `.factory` / `.nsfw`, lib versions 14, 16 and 17.
 *  - manga: Mihon `ExtensionLoader` - feature `tachiyomi.extension`, metadata
 *    `tachiyomi.extension.class` / `.factory` / `.nsfw` / `.lib`, lib versions 1.4, 1.5 and 1.6.
 *
 * Both use `<libVersion>.<extensionVersion>` as `versionName`; Mihon 1.6 also writes the library
 * version into the `tachiyomi.extension.lib` float metadata, which takes precedence when present.
 */
object AniyomiPackage {

    const val FEATURE_ANIME_EXTENSION = "tachiyomi.animeextension"
    const val FEATURE_MANGA_EXTENSION = "tachiyomi.extension"

    const val META_ANIME_CLASS = "tachiyomi.animeextension.class"
    const val META_ANIME_FACTORY = "tachiyomi.animeextension.factory"
    const val META_ANIME_NSFW = "tachiyomi.animeextension.nsfw"

    const val META_MANGA_CLASS = "tachiyomi.extension.class"
    const val META_MANGA_FACTORY = "tachiyomi.extension.factory"
    const val META_MANGA_NSFW = "tachiyomi.extension.nsfw"
    const val META_MANGA_LIB = "tachiyomi.extension.lib"

    const val META_LANG = "tachiyomi.animeextension.lang"

    /** Animiru's `ExtensionLoader.SUPPORTED_LIB_VERSIONS` is exactly 14, 16 and 17. */
    val SUPPORTED_ANIME_LIB_VERSIONS = listOf(14, 16, 17)

    /** Mihon accepts 1.4 and 1.6; 1.5 packages use the same contract as 1.4. */
    val SUPPORTED_MANGA_LIB_VERSIONS = listOf(1.4, 1.5, 1.6)

    /** Backwards compatible alias used by older call sites. */
    val SUPPORTED_LIB_VERSIONS: List<Int> get() = SUPPORTED_ANIME_LIB_VERSIONS

    private const val MAX_PACKAGE_BYTES = 128L * 1024 * 1024

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS: Int =
        PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES

    fun packageInfo(context: Context, file: File): PackageInfo? = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)
        }
        // Android 13+ leaves sourceDir unset for archive infos, which breaks asset access.
        info?.applicationInfo?.let { app ->
            if (app.sourceDir == null) app.sourceDir = file.absolutePath
            if (app.publicSourceDir == null) app.publicSourceDir = file.absolutePath
        }
        info
    } catch (_: Throwable) {
        null
    }

    fun kind(info: PackageInfo): AniyomiExtensionKind? {
        val features = info.reqFeatures.orEmpty().map { it.name }
        return when {
            FEATURE_ANIME_EXTENSION in features -> AniyomiExtensionKind.ANIME
            FEATURE_MANGA_EXTENSION in features -> AniyomiExtensionKind.MANGA
            else -> null
        }
    }

    fun isAnimeExtension(info: PackageInfo): Boolean = kind(info) == AniyomiExtensionKind.ANIME

    fun isMangaExtension(info: PackageInfo): Boolean = kind(info) == AniyomiExtensionKind.MANGA

    fun isExtension(info: PackageInfo): Boolean = kind(info) != null

    fun sourceClasses(info: PackageInfo): List<String> {
        val meta = info.applicationInfo?.metaData ?: return emptyList()
        val raw = when (kind(info)) {
            AniyomiExtensionKind.ANIME -> meta.getString(META_ANIME_CLASS)
            AniyomiExtensionKind.MANGA -> meta.getString(META_MANGA_CLASS)
            null -> null
        } ?: return emptyList()
        return raw.split(';').mapNotNull { entry ->
            val trimmed = entry.trim()
            when {
                trimmed.isEmpty() -> null
                trimmed.startsWith(".") -> info.packageName + trimmed
                else -> trimmed
            }
        }
    }

    fun factoryClass(info: PackageInfo): String? {
        val meta = info.applicationInfo?.metaData ?: return null
        val raw = when (kind(info)) {
            AniyomiExtensionKind.ANIME -> meta.getString(META_ANIME_FACTORY)
            AniyomiExtensionKind.MANGA -> meta.getString(META_MANGA_FACTORY)
            null -> null
        }
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun isNsfw(info: PackageInfo): Boolean {
        val meta = info.applicationInfo?.metaData ?: return false
        val key = when (kind(info)) {
            AniyomiExtensionKind.ANIME -> META_ANIME_NSFW
            AniyomiExtensionKind.MANGA -> META_MANGA_NSFW
            null -> return false
        }
        return meta.getInt(key, 0) == 1
    }

    /** `<libVersion>.<version>` -> the library version string (`16` for `16.5.3`). */
    fun libVersion(versionName: String?): String? =
        versionName?.substringBeforeLast('.', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }

    fun libMajor(versionName: String?): Int? = libVersion(versionName)?.substringBefore('.')?.toIntOrNull()

    /** Anime lib version: an integer, compared against 14/16/17. */
    fun animeLibVersion(info: PackageInfo): Int? = libMajor(info.versionName)

    /**
     * Manga lib version: `tachiyomi.extension.lib` when the package declares it, otherwise the
     * `<lib>.<version>` prefix of `versionName` (e.g. `1.6.1` -> `1.6`).
     */
    fun mangaLibVersion(info: PackageInfo): Double? {
        val fromMeta = runCatching {
            info.applicationInfo?.metaData?.getFloat(META_MANGA_LIB)
        }.getOrNull()
        if (fromMeta != null && fromMeta > 0f) return fromMeta.toDouble()
        val prefix = libVersion(info.versionName) ?: return null
        return prefix.toDoubleOrNull()
    }

    fun isAnimeLibSupported(info: PackageInfo): Boolean =
        animeLibVersion(info)?.let { it in SUPPORTED_ANIME_LIB_VERSIONS } == true

    fun isMangaLibSupported(info: PackageInfo): Boolean =
        mangaLibVersion(info)?.let { version -> SUPPORTED_MANGA_LIB_VERSIONS.any { kotlin.math.abs(it - version) < 0.001 } } == true

    fun isSupportedLibVersion(versionName: String?): Boolean =
        libMajor(versionName)?.let { it in SUPPORTED_ANIME_LIB_VERSIONS } == true

    /** Manga lib versions are decimals (`1.4`, `1.5`, `1.6`), unlike the anime integers. */
    fun isMangaLibVersionSupported(libVersion: String?): Boolean {
        val version = libVersion?.toDoubleOrNull() ?: return false
        return SUPPORTED_MANGA_LIB_VERSIONS.any { kotlin.math.abs(it - version) < 0.001 }
    }

    fun validate(context: Context, file: File): ExtensionError? = validate(context, file, AniyomiExtensionKind.ANIME)

    /**
     * Validates a package for a concrete ecosystem. Returns null when the package can be executed,
     * otherwise a structured error that is surfaced to the client instead of a silent skip.
     */
    fun validate(context: Context, file: File, expected: AniyomiExtensionKind? = null): ExtensionError? {
        if (!file.isFile) {
            return error(ErrorCode.EXTENSION_INVALID_PACKAGE, "Extension file does not exist", file.name)
        }
        if (file.length() <= 0 || file.length() > MAX_PACKAGE_BYTES) {
            return error(ErrorCode.EXTENSION_INVALID_PACKAGE, "Unexpected extension size (${file.length()} bytes)", file.name)
        }
        if (!file.name.endsWith(".apk", ignoreCase = true)) {
            return error(ErrorCode.EXTENSION_INVALID_PACKAGE, "Extensions are APK packages", file.name)
        }

        val info = packageInfo(context, file)
            ?: return error(ErrorCode.EXTENSION_INVALID_PACKAGE, "APK could not be parsed", file.name)

        val kind = kind(info)
            ?: return error(
                ErrorCode.EXTENSION_UNSUPPORTED,
                "APK declares neither $FEATURE_ANIME_EXTENSION nor $FEATURE_MANGA_EXTENSION",
                file.name,
            )
        if (expected != null && kind != expected) {
            return error(
                ErrorCode.EXTENSION_UNSUPPORTED,
                "APK is a ${kind.name.lowercase()} extension, not ${expected.name.lowercase()}",
                file.name,
            )
        }
        if (sourceClasses(info).isEmpty()) {
            return error(ErrorCode.EXTENSION_INVALID_PACKAGE, "Missing extension source class metadata", file.name)
        }
        when (kind) {
            AniyomiExtensionKind.ANIME -> if (!isAnimeLibSupported(info)) {
                return error(
                    ErrorCode.EXTENSION_UNSUPPORTED,
                    "Anime extension library ${animeLibVersion(info) ?: "?"} is not one of " +
                        SUPPORTED_ANIME_LIB_VERSIONS.joinToString(),
                    file.name,
                )
            }

            AniyomiExtensionKind.MANGA -> if (!isMangaLibSupported(info)) {
                return error(
                    ErrorCode.EXTENSION_UNSUPPORTED,
                    "Manga extension library ${mangaLibVersion(info) ?: "?"} is not one of " +
                        SUPPORTED_MANGA_LIB_VERSIONS.joinToString(),
                    file.name,
                )
            }
        }
        dangerousPermissions(info)?.let { permissions ->
            return error(
                ErrorCode.EXTENSION_INVALID_PACKAGE,
                "Extension requests dangerous permissions: ${permissions.joinToString()}",
                file.name,
            )
        }
        return null
    }

    /** Extensions never need dangerous permissions; requesting them is treated as hostile metadata. */
    private fun dangerousPermissions(info: PackageInfo): List<String>? {
        val requested = info.requestedPermissions.orEmpty()
        if (requested.isEmpty()) return null
        val dangerous = requested.filter { it in DANGEROUS_PERMISSIONS }
        return dangerous.ifEmpty { null }
    }

    private val DANGEROUS_PERMISSIONS = setOf(
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.READ_PHONE_STATE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES",
    )

    fun metadata(context: Context, info: PackageInfo): ExtensionMetadata {
        val app: ApplicationInfo? = info.applicationInfo
        val label = app?.let {
            runCatching { context.packageManager.getApplicationLabel(it).toString() }.getOrNull()
        } ?: info.packageName
        val kind = kind(info)
        return ExtensionMetadata(
            name = label.removePrefix("Aniyomi: ").removePrefix("Tachiyomi: ").prefixKind(kind).trim(),
            packageName = info.packageName,
            versionName = info.versionName.orEmpty(),
            versionCode = versionCode(info),
            language = app?.metaData?.getString(META_LANG)?.takeIf { it.isNotBlank() },
            isNsfw = isNsfw(info),
            libVersion = when (kind) {
                AniyomiExtensionKind.MANGA -> mangaLibVersion(info)?.toString()
                AniyomiExtensionKind.ANIME -> libVersion(info.versionName)
                null -> libVersion(info.versionName)
            },
        )
    }

    /** Keeps anime and manga packages visually distinguishable in client UIs. */
    private fun String.prefixKind(kind: AniyomiExtensionKind?): String = when (kind) {
        AniyomiExtensionKind.MANGA -> if (isBlank()) this else "[Manga] $this"
        else -> this
    }

    fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun error(code: ErrorCode, message: String, details: String?) = ExtensionError(
        code = code,
        message = message,
        details = details,
        backend = BackendType.ANIYOMI,
    )
}
