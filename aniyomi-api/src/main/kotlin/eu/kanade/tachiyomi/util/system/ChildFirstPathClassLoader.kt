package eu.kanade.tachiyomi.util.system

import dalvik.system.PathClassLoader

/**
 * A [PathClassLoader] that prefers classes from the extension APK over the host application.
 *
 * This matters when an extension bundles its own copy of a dependency (Jsoup, OkHttp, …) or a
 * different version of an API class: the extension must see its own classes first, while classes it
 * only references (the Aniyomi API) still resolve from the runtime through the parent loader.
 */
class ChildFirstPathClassLoader(
    dexPath: String?,
    librarySearchPath: String?,
    parent: ClassLoader?,
) : PathClassLoader(dexPath, librarySearchPath, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // 1. Already loaded by this loader?
        findLoadedClass(name)?.let { return it }

        // 2. Local (extension) classes win.
        try {
            return findClass(name)
        } catch (_: ClassNotFoundException) {
            // fall through to the parent chain
        }

        return super.loadClass(name, resolve)
    }
}
