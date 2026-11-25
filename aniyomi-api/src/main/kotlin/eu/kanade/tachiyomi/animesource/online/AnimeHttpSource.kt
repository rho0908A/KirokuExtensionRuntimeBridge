package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.NETWORK_CLIENT
import eu.kanade.tachiyomi.network.awaitSuccess
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.schedulers.Schedulers

/**
 * Base class for HTTP backed anime sources, matching Anikku's `AnimeHttpSource`.
 *
 * Extensions override the `*Request` / `*Parse` members and the runtime drives them. Every hook is
 * `open` rather than `abstract` where Anikku marks it abstract, so packages built against older
 * Aniyomi libraries (lib 12-15) still instantiate: calling a hook they never implemented returns
 * the safe default instead of `AbstractMethodError`.
 */
abstract class AnimeHttpSource : AnimeCatalogueSource {

    abstract val baseUrl: String

    /** Extensions bump this to force a new source id after changing their URL scheme. */
    open val versionId: Int = 1

    override val id: Long by lazy { generateId(name, lang, versionId) }

    open val client: OkHttpClient
        get() = NETWORK_CLIENT

    open val headers: Headers
        get() = headersBuilder().build()

    protected open fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)

    override val supportsLatest: Boolean
        get() = true

    override fun toString(): String = "$name (${lang.uppercase()})"

    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> = rx {
        execute(popularAnimeRequest(page)).use { popularAnimeParse(it) }
    }

    protected open fun popularAnimeRequest(page: Int): Request = Request.Builder()
        .url("$baseUrl/$page")
        .headers(headers)
        .build()

    protected open fun popularAnimeParse(response: Response): AnimesPage = AnimesPage(emptyList(), false)

    override fun fetchSearchAnime(page: Int, query: String, filters: AnimeFilterList): Observable<AnimesPage> = rx {
        execute(searchAnimeRequest(page, query, filters)).use { searchAnimeParse(it) }
    }

    protected open fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        Request.Builder()
            .url("$baseUrl/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&page=$page")
            .headers(headers)
            .build()

    protected open fun searchAnimeParse(response: Response): AnimesPage = AnimesPage(emptyList(), false)

    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> = rx {
        execute(latestUpdatesRequest(page)).use { latestUpdatesParse(it) }
    }

    protected open fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)

    protected open fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> = rx {
        execute(animeDetailsRequest(anime)).use { animeDetailsParse(it).apply { url = anime.url } }
    }

    protected open fun animeDetailsRequest(anime: SAnime): Request = Request.Builder()
        .url(anime.url)
        .headers(headers)
        .build()

    protected open fun animeDetailsParse(response: Response): SAnime =
        SAnime.create().apply { url = response.request.url.toString() }

    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> = rx {
        execute(episodeListRequest(anime)).use { episodeListParse(it) }
    }

    protected open fun episodeListRequest(anime: SAnime): Request = Request.Builder()
        .url(anime.url)
        .headers(headers)
        .build()

    protected open fun episodeListParse(response: Response): List<SEpisode> = emptyList()

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> =
        execute(seasonListRequest(anime)).use { seasonListParse(it) }

    protected open fun seasonListRequest(anime: SAnime): Request = animeDetailsRequest(anime)

    protected open fun seasonListParse(response: Response): List<SAnime> = emptyList()

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> =
        execute(hosterListRequest(episode)).use { hosterListParse(it) }

    protected open fun hosterListRequest(episode: SEpisode): Request = Request.Builder()
        .url(episode.url)
        .headers(headers)
        .build()

    protected open fun hosterListParse(response: Response): List<Hoster> = emptyList()

    override suspend fun getVideoList(hoster: Hoster): List<Video> {
        if (!hoster.lazy && !hoster.videoList.isNullOrEmpty()) return hoster.videoList.orEmpty()
        return execute(videoListRequest(hoster)).use { videoListParse(it, hoster) }
    }

    protected open fun videoListRequest(hoster: Hoster): Request = Request.Builder()
        .url(hoster.hosterUrl)
        .headers(headers)
        .build()

    protected open fun videoListParse(response: Response, hoster: Hoster): List<Video> =
        hoster.videoList.orEmpty()

    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> = rx {
        execute(videoListRequest(episode)).use { videoListParse(it) }
    }

    protected open fun videoListRequest(episode: SEpisode): Request = Request.Builder()
        .url(episode.url)
        .headers(headers)
        .build()

    protected open fun videoListParse(response: Response): List<Video> = emptyList()

    open fun List<Hoster>.sortHosters(): List<Hoster> = sortedBy { it.hosterName.lowercase() }

    open fun List<Video>.sortVideos(): List<Video> =
        sortedWith(compareByDescending<Video> { it.resolution ?: 0 }.thenByDescending { it.videoTitle })

    protected open fun List<Video>.sort(): List<Video> = sortVideos()

    protected fun generateId(name: String, lang: String, versionId: Int): Long =
        "$name/$lang/$versionId".hashCode().toLong() and 0xFFFFFFFFL

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    protected suspend fun execute(request: Request): Response = client.newCall(request).awaitSuccess()

    /** Bridges an RxJava observable to a coroutine. */
    protected fun <T> rx(block: suspend () -> T): Observable<T> =
        Observable.defer { Observable.just(kotlinx.coroutines.runBlocking { block() }) }
            .subscribeOn(Schedulers.io())

    companion object {
        const val USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
