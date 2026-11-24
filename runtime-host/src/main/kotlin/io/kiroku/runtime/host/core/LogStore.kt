package io.kiroku.runtime.host.core

import io.kiroku.runtime.ipc.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Bounded in-memory log of runtime operations.
 *
 * Line-based Android logging is not captured: this store records what the runtime itself did
 * (installs, loads, failures) and is what both the UI and `runtime.logs` expose.
 */
class LogStore(private val capacity: Int = 500) {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun add(level: String, tag: String, message: String) {
        _entries.update { current ->
            (current + LogEntry(System.currentTimeMillis(), level, tag, message)).takeLast(capacity)
        }
    }

    fun info(tag: String, message: String) = add("INFO", tag, message)

    fun warn(tag: String, message: String) = add("WARN", tag, message)

    fun error(tag: String, message: String) = add("ERROR", tag, message)

    fun clear() {
        _entries.value = emptyList()
    }

    fun snapshot(limit: Int): List<LogEntry> = _entries.value.takeLast(limit.coerceAtLeast(1))
}
