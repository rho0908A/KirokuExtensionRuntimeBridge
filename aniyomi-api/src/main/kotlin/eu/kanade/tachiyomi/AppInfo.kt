package eu.kanade.tachiyomi

/**
 * Info about the host app (the Runtime Host), not the extension.
 *
 * The runtime fills these in during backend initialisation so extensions that build a User-Agent or
 * compare versions see real values instead of a stub.
 */
object AppInfo {

    @Volatile
    var versionCode: Int = 0

    @Volatile
    var versionName: String = "0"

    fun getVersionCode(): Int = versionCode

    fun getVersionName(): String = versionName
}
