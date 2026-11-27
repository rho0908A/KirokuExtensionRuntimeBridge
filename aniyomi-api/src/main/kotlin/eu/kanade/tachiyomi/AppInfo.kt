package eu.kanade.tachiyomi

/**
 * Info about the host app (the Runtime Host), not the extension.
 *
 * The runtime fills these in during backend initialisation so extensions that build a User-Agent or
 * compare versions see real values instead of a stub.
 *
 * The values live in properties that do not generate the `getVersionCode`/`getVersionName` JVM
 * accessors the extensions-lib declares as functions.
 */
object AppInfo {

    @Volatile
    var hostVersionCode: Int = 0

    @Volatile
    var hostVersionName: String = "0"

    fun getVersionCode(): Int = hostVersionCode

    fun getVersionName(): String = hostVersionName
}
