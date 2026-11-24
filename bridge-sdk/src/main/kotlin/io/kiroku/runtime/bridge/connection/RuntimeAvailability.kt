package io.kiroku.runtime.bridge.connection

import android.content.Context
import android.content.pm.PackageManager
import io.kiroku.runtime.ipc.RuntimeProtocol

/** Result of checking whether the Runtime Host APK is present and usable. */
sealed interface RuntimeAvailability {
    data class Installed(
        val versionName: String?,
        val versionCode: Long,
        val targetSdk: Int,
    ) : RuntimeAvailability

    data object NotInstalled : RuntimeAvailability

    /** The runtime is installed but could not be queried (e.g. package visibility restrictions). */
    data class Unknown(val reason: String) : RuntimeAvailability

    val isInstalled: Boolean get() = this is Installed

    companion object {
        /**
         * Checks the runtime package.
         *
         * Android 11+ requires the host application to declare the runtime package in its manifest:
         * ```xml
         * <queries>
         *     <package android:name="io.kiroku.runtime.host" />
         * </queries>
         * ```
         * Without that declaration [Unknown] is returned instead of a misleading [NotInstalled].
         */
        fun check(context: Context): RuntimeAvailability {
            val pm = context.packageManager
            return try {
                val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(RuntimeProtocol.RUNTIME_PACKAGE, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(RuntimeProtocol.RUNTIME_PACKAGE, 0)
                }
                val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
                Installed(
                    versionName = info.versionName,
                    versionCode = code,
                    targetSdk = info.applicationInfo?.targetSdkVersion ?: 0,
                )
            } catch (_: PackageManager.NameNotFoundException) {
                NotInstalled
            } catch (e: SecurityException) {
                // Package visibility (Android 11+) refuses the query unless the host declares
                // <queries><package android:name="io.kiroku.runtime.host"/></queries>.
                Unknown(e.message ?: "Package visibility denied")
            } catch (t: Throwable) {
                Unknown(t.message ?: t.javaClass.simpleName)
            }
        }

        /** True when the installed runtime reported a protocol this SDK can speak. */
        fun isProtocolCompatible(protocolVersion: Int): Boolean =
            protocolVersion == RuntimeProtocol.PROTOCOL_VERSION

        /** Major version of the SDK itself, surfaced to the runtime during the handshake. */
        fun sdkProtocolVersion(): Int = RuntimeProtocol.PROTOCOL_VERSION
    }
}
