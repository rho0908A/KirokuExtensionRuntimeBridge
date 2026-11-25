package tachiyomi.core.common.util.system

import android.util.Log

/** Log priorities as declared by Anikku/Mihon's `logcat` utility. */
enum class LogPriority {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ASSERT,
}

/**
 * Minimal `logcat` implementation.
 *
 * Anikku's source API calls `logcat(LogPriority.ERROR, e) { "..." }` from default interface
 * methods, so the runtime has to provide it for those defaults to link.
 */
fun logcat(priority: LogPriority = LogPriority.DEBUG, throwable: Throwable? = null, message: () -> String) {
    val tag = "Extensions"
    val text = message()
    when (priority) {
        LogPriority.VERBOSE -> Log.v(tag, text, throwable)
        LogPriority.DEBUG -> Log.d(tag, text, throwable)
        LogPriority.INFO -> Log.i(tag, text, throwable)
        LogPriority.WARN -> Log.w(tag, text, throwable)
        LogPriority.ERROR -> Log.e(tag, text, throwable)
        LogPriority.ASSERT -> Log.wtf(tag, text, throwable)
    }
}
