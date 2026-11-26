package eu.kanade.tachiyomi.animesource

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

/**
 * Anime source contract, mirroring `eu.kanade.tachiyomi.animesource.AnimeSource` from Anikku
 * (the maintained Aniyomi fork) as of extensions-lib 16.
 *
 * The suspend methods carry implementations that delegate to the RxJava `fetch*` methods, exactly
 * like Anikku: extensions built against lib 12-14 only override `fetch*`, extensions built against
 * 1.5+ override the suspend variants.
 *
 * Note: Anikku declares `AnimeSource` without extending the manga `Source` interface, so the
 * runtime can host both the Aniyomi/Anikku anime API and the Mihon manga API in one process.
 */
interface AnimeSource {

    /** ID for the source. Must be unique. */
    val id: Long

    /** Name of the source. */
    val name: String

    /** ISO 639-1 language code, empty when the source does not declare one. */
    val lang: String
        get() = ""

    suspend fun getAnimeDetails(anime: SAnime): SAnime = fetchAnimeDetails(anime).awaitSingle()

    suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = fetchEpisodeList(anime).awaitSingle()

    /**
     * Seasons supported since extensions-lib 16. Sources built against older libraries do not
     * implement this, so it has a default instead of Anikku's abstract declaration.
     */
    suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()

    suspend fun getHosterList(episode: SEpisode): List<Hoster> = throw IllegalStateException("Not used")

    suspend fun getVideoList(hoster: Hoster): List<Video> = throw IllegalStateException("Not used")

    suspend fun getVideoList(episode: SEpisode): List<Video> = fetchVideoList(episode).awaitSingle()

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getAnimeDetails"))
    fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> = throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getEpisodeList"))
    fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> = throw IllegalStateException("Not used")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getVideoList"))
    fun fetchVideoList(episode: SEpisode): Observable<List<Video>> = throw IllegalStateException("Not used")
}

/** Catalogue source: adds browsing, search and filters. */
interface AnimeCatalogueSource : AnimeSource {

    override val lang: String

    /** Whether the source has support for latest updates. */
    val supportsLatest: Boolean

    suspend fun getPopularAnime(page: Int): AnimesPage = fetchPopularAnime(page).awaitSingle()

    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        fetchSearchAnime(page, query, filters).awaitSingle()

    suspend fun getLatestUpdates(page: Int): AnimesPage = fetchLatestUpdates(page).awaitSingle()

    fun getFilterList(): AnimeFilterList

    // Should be replaced as soon as anime extensions reach 1.5
    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPopularAnime"))
    fun fetchPopularAnime(page: Int): Observable<AnimesPage>

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getSearchAnime"))
    fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage>

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getLatestUpdates"))
    fun fetchLatestUpdates(page: Int): Observable<AnimesPage>

    /** Related-content hooks added by Komikku/Anikku (`komikku/extensions-lib 1.6`). */
    val supportsRelatedAnimes: Boolean get() = false
    val disableRelatedAnimes: Boolean get() = false

    suspend fun fetchRelatedAnimeList(anime: SAnime): List<SAnime> =
        throw UnsupportedOperationException("Unsupported!")
}

/** Sources that expose user-configurable preferences. */
interface ConfigurableAnimeSource : AnimeSource {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}

/** Factory used by multi-source extensions. */
interface AnimeSourceFactory {
    fun createSources(): List<AnimeSource>
}
