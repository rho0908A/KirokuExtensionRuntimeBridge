package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.NETWORK_CLIENT
import eu.kanade.tachiyomi.network.awaitSuccess
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Base class for HTTP backed Aniyomi anime sources.
 *
 * The request/parse split matches Aniyomi's `AnimeHttpSource`: extensions override the `*Request`
 * and `*Parse` members, while the runtime drives the calls and owns the HTTP client.
 */
abstract class AnimeHttpSource : AnimeCatalogueSource {

    abstract val baseUrl: String

    open val client: OkHttpClient
        get() = NETWORK_CLIENT

    open val headers: Headers
        get() = Headers.Builder().add("User-Agent", USER_AGENT).build()

    override val supportsLatest: Boolean
        get() = true

    override suspend fun getPopularAnime(page: Int): AnimesPage {
        val request = popularAnimeRequest(page)
        val response = client.newCall(request).awaitSuccess()
        return response.use { popularAnimeParse(it) }
    }

    protected abstract fun popularAnimeRequest(page: Int): Request

    protected abstract fun popularAnimeParse(response: Response): AnimesPage

    override suspend fun getLatestUpdates(page: Int): AnimesPage {
        val request = latestUpdatesRequest(page)
        val response = client.newCall(request).awaitSuccess()
        return response.use { latestUpdatesParse(it) }
    }

    protected open fun latestUpdatesRequest(page: Int): Request = popularAnimeRequest(page)

    protected open fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val request = searchAnimeRequest(page, query, filters)
        val response = client.newCall(request).awaitSuccess()
        return response.use { searchAnimeParse(it) }
    }

    protected open fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request =
        Request.Builder()
            .url("$baseUrl/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&page=$page")
            .headers(headers)
            .build()

    protected abstract fun searchAnimeParse(response: Response): AnimesPage

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        val request = animeDetailsRequest(anime)
        val response = client.newCall(request).awaitSuccess()
        return response.use { animeDetailsParse(it).apply { url = anime.url } }
    }

    protected abstract fun animeDetailsRequest(anime: SAnime): Request

    protected abstract fun animeDetailsParse(response: Response): SAnime

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val request = episodeListRequest(anime)
        val response = client.newCall(request).awaitSuccess()
        return response.use { episodeListParse(it) }
    }

    protected abstract fun episodeListRequest(anime: SAnime): Request

    protected abstract fun episodeListParse(response: Response): List<SEpisode>

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val request = videoListRequest(episode)
        val response = client.newCall(request).awaitSuccess()
        return response.use { videoListParse(it) }
    }

    protected abstract fun videoListRequest(episode: SEpisode): Request

    protected abstract fun videoListParse(response: Response): List<Video>

    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    override fun toString(): String = "$name (${javaClass.simpleName})"

    companion object {
        const val USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
