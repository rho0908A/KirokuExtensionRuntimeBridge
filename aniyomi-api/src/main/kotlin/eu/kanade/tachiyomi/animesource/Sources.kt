package eu.kanade.tachiyomi.animesource

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

/** Base type for anything installable, mirroring Aniyomi's `eu.kanade.tachiyomi.source.Source`. */
interface Source {
    val id: Long
    val name: String
}

/** Base anime source contract. */
interface AnimeSource : Source {
    val lang: String

    fun getFilterList(): AnimeFilterList = AnimeFilterList()

    suspend fun getAnimeDetails(anime: SAnime): SAnime

    suspend fun getEpisodeList(anime: SAnime): List<SEpisode>

    suspend fun getVideoList(episode: SEpisode): List<Video>
}

/** Catalogue source: adds browsing and search. */
interface AnimeCatalogueSource : AnimeSource {

    val supportsLatest: Boolean get() = true

    suspend fun getPopularAnime(page: Int): AnimesPage

    suspend fun getLatestUpdates(page: Int): AnimesPage

    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage

    /**
     * Optional hoster indirection used by newer extensions: when a source overrides this, video
     * resolution goes through [getHosterList] and then [getVideoList] for each hoster.
     */
    suspend fun getHosterList(episode: SEpisode): List<Hoster> = emptyList()

    suspend fun getVideoList(hoster: Hoster): List<Video> = hoster.videoList
}

/** Sources that expose user-configurable preferences. */
interface ConfigurableAnimeSource : AnimeSource {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}

/** Factory used by multi-source extensions. */
interface AnimeSourceFactory {
    fun createSources(): List<AnimeSource>
}
