package eu.kanade.tachiyomi.network

/** Progress callback used by Mihon's `Page` model. */
interface ProgressListener {
    fun update(bytesRead: Long, contentLength: Long, done: Boolean)
}
