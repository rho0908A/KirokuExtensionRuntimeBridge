package io.kiroku.runtime.backend.aniyomi

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.kiroku.runtime.ipc.ErrorCode
import io.kiroku.runtime.ipc.ExtensionError
import io.kiroku.runtime.ipc.ExtensionMetadata
import java.io.File

/**
 * Reads and validates Aniyomi extension APKs without executing extension code.
 *
 * The contract comes from Aniyomi's `ExtensionLoader`:
 *  - the APK must declare the `tachiyomi.animeextension` feature,
 *  - metadata `tachiyomi.animeextension.class` holds one or more `;` separated source classes,
 *  - `versionName` is `<libVersion>.<extensionVersion>`, e.g. `16.5.3`,
 *  - the extension library major version must be inside the range the runtime implements.
 */
object AniyomiPackage {

    const val FEATURE_ANIME_EXTENSION = "tachiyomi.animeextension"

    const val META_CLASS = "tachiyomi.animeextension.class"
    const val META_FACTORY = "tachiyomi.animeextension.factory"
    const val META_NSFW = "tachiyomi.animeextension.nsfw"
    const val META_HAS_README = "tachiyomi.animeextension.hasReadme"
    const val META_HAS_CHANGELOG = "tachiyomi.animeextension.hasChangelog"
    const val META_LANG = "tachiyomi.animeextension.lang"

    /** Major library versions this API layer is compatible with. */
    val SUPPORTED_LIB_VERSIONS = 12..17

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

    fun isAnimeExtension(info: PackageInfo): Boolean =
        info.reqFeatures.orEmpty().any { it.name == FEATURE_ANIME_EXTENSION }

    fun sourceClasses(info: PackageInfo): List<String> {
        val raw = info.applicationInfo?.metaData?.getString(META_CLASS) ?: return emptyList()
        return raw.split(';').mapNotNull { entry ->
            val trimmed = entry.trim()
            when {
                trimmed.isEmpty() -> null
                trimmed.startsWith(".") -> info.packageName + trimmed
                else -> trimmed
            }
        }
    }

    fun factoryClass(info: PackageInfo): String? =
        info.applicationInfo?.metaData?.getString(META_FACTORY)?.trim()?.takeIf { it.isNotEmpty() }

    fun isNsfw(info: PackageInfo): Boolean =
        info.applicationInfo?.metaData?.getInt(META_NSFW, 0) == 1

    /** `<libVersion>.<version>` -> the library version string (`16` for `16.5.3`). */
    fun libVersion(versionName: String?): String? =
        versionName?.substringBeforeLast('.', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }

    fun libMajor(versionName: String?): Int? =
        libVersion(versionName)?.substringBefore('.')?.toIntOrNull()

    fun isSupportedLibVersion(versionName: String?): Boolean {
        val major = libMajor(versionName) ?: return false
        return major in SUPPORTED_LIB_VERSIONS
    }

    fun validate(context: Context, file: File): ExtensionError? {
        if (!file.isFile) {
            return error(ErrorCode.EXTENSION_INVALID_PACKAGE, "Extension file does not exist", file.name)
        }
        if (file.length() <= 0 || file.length() > MAX_PACKAGE_BYTES) {
            return error(ErrorCode.EXTENSION_INVALID_PACKAGE, "Unexpected extension size (${file.length()} bytes)", file.name)
        }
        if (!file.name.endsWith(".apk", ignoreCase = true)) {
            return error(ErrorCode.EXTENSION_INVALID_PACKAGE, "Aniyomi extensions are APK packages", file.name)
        }

        val info = packageInfo(context, file)
            ?: return error(ErrorCode.EXTENSION_INVALID_PACKAGE, "APK could not be parsed", file.name)

        if (!isAnimeExtension(info)) {
            return error(
                ErrorCode.EXTENSION_UNSUPPORTED,
                "APK does not declare the $FEATURE_ANIME_EXTENSION feature",
                file.name,
            )
        }
        if (sourceClasses(info).isEmpty()) {
            return error(ErrorCode.EXTENSION_INVALID_PACKAGE, "Missing $META_CLASS metadata", file.name)
        }
        if (!isSupportedLibVersion(info.versionName)) {
            return error(
                ErrorCode.EXTENSION_UNSUPPORTED,
                "Extension library ${libVersion(info.versionName) ?: "?"} is outside the supported range " +
                    "${SUPPORTED_LIB_VERSIONS.first}..${SUPPORTED_LIB_VERSIONS.last}",
                file.name,
            )
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
        return ExtensionMetadata(
            name = label.removePrefix("Aniyomi: ").trim(),
            packageName = info.packageName,
            versionName = info.versionName.orEmpty(),
            versionCode = versionCode(info),
            language = app?.metaData?.getString(META_LANG)?.takeIf { it.isNotBlank() },
            isNsfw = isNsfw(info),
            libVersion = libVersion(info.versionName),
        )
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
        backend = io.kiroku.runtime.ipc.BackendType.ANIYOMI,
    )
}
