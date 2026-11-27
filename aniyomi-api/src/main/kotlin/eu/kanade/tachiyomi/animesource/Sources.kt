package eu.kanade.tachiyomi.animesource

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

/**
 * Anime source contract, mirroring `eu.kanade.tachiyomi.animesource.AnimeSource` from the Aniyomi /
 * Animiru extensions-lib (v17).
 *
 * Every member has a default implementation that delegates to the older RxJava hooks, so extension
 * APKs built against lib 14 (still the majority in the public repositories) keep working, while
 * lib 16/17 packages that override the suspend API are called directly.
 *
 * `AnimeSource` deliberately does not extend the Mihon manga `Source`, so one runtime can host the
 * Animiru anime API and the Mihon manga API side by side.
 */
interface AnimeSource {

    /** ID for the source. Must be unique. */
    val id: Long

    /** Name of the source. */
    val name: String

    /** Whether the source has support for latest updates. */
    val supportsLatest: Boolean get() = true

    fun getFilterList(): AnimeFilterList = AnimeFilterList()

    suspend fun getPopularAnime(page: Int): AnimesPage = fetchPopularAnime(page).awaitSingle()

    suspend fun getLatestUpdates(page: Int): AnimesPage = fetchLatestUpdates(page).awaitSingle()

    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        fetchSearchAnime(page, query, filters).awaitSingle()

    /** Called by update jobs with the episodes the host already knows about (lib 17). */
    suspend fun getAnimeEpisodeUpdate(anime: SAnime, episodes: List<SEpisode>): SAnimeEpisodeUpdate =
        SAnimeEpisodeUpdate(anime, getEpisodeList(anime))

    /** Called for sources that expose seasons instead of episodes (lib 17). */
    suspend fun getAnimeSeasonUpdate(anime: SAnime, seasons: List<SAnime>): SAnimeSeasonUpdate =
        SAnimeSeasonUpdate(anime, getSeasonList(anime))

    /** Whether [getRelatedAnimeList] has anything to say (lib 17). */
    val supportsRelatedAnime: Boolean get() = false

    suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> = emptyList()

    suspend fun getAnimeDetails(anime: SAnime): SAnime = fetchAnimeDetails(anime).awaitSingle()

    suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = fetchEpisodeList(anime).awaitSingle()

    /** Seasons supported since extensions-lib 16. */
    suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()

    suspend fun getHosterList(episode: SEpisode): List<Hoster> =
        throw IllegalStateException("Source does not expose hosters")

    suspend fun getVideoList(hoster: Hoster): List<Video> =
        throw IllegalStateException("Source does not expose hosters")

    suspend fun getVideoList(episode: SEpisode): List<Video> = fetchVideoList(episode).awaitSingle()

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getAnimeDetails"))
    fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> =
        throw IllegalStateException("Source implements the suspend API")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getEpisodeList"))
    fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> =
        throw IllegalStateException("Source implements the suspend API")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getVideoList"))
    fun fetchVideoList(episode: SEpisode): Observable<List<Video>> =
        throw IllegalStateException("Source implements the suspend API")

    // RxJava hooks used by lib 14 extensions. Host calls go through the suspend members above.

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPopularAnime"))
    fun fetchPopularAnime(page: Int): Observable<AnimesPage> =
        throw IllegalStateException("Source implements the suspend API")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getSearchAnime"))
    fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> =
        throw IllegalStateException("Source implements the suspend API")

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getLatestUpdates"))
    fun fetchLatestUpdates(page: Int): Observable<AnimesPage> =
        throw IllegalStateException("Source implements the suspend API")
}

/** Catalogue source: adds the source language. */
interface AnimeCatalogueSource : AnimeSource {

    val lang: String
}

/** Sources that expose user-configurable preferences. */
interface ConfigurableAnimeSource : AnimeSource {
    fun setupPreferenceScreen(screen: PreferenceScreen)
}

/** Factory used by multi-source extensions. */
interface AnimeSourceFactory {
    fun createSources(): List<AnimeSource>
}
