package eu.kanade.tachiyomi.source

/** Factory used by multi-source manga extensions. */
interface SourceFactory {
    fun createSources(): List<Source>
}
