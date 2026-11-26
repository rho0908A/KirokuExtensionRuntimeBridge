package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.NETWORK_CLIENT
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.schedulers.Schedulers

/**
 * Base class for HTTP backed manga sources, matching Mihon's `HttpSource`.
 *
 * Extensions override the `*Request` / `*Parse` hooks; the runtime drives them and exposes them as
 * suspend calls. Hooks that Mihon marks abstract are open here so packages built against older
 * extension libraries still instantiate.
 */
abstract class HttpSource : CatalogueSource {

    abstract val baseUrl: String

    open val client: OkHttpClient
        get() = NETWORK_CLIENT

    open val headers: Headers
        get() = headersBuilder().build()

    protected open fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)

    override val supportsLatest: Boolean
        get() = true

    // ---------------------------------------------------------------------------------------------
    // Browsing
    // ---------------------------------------------------------------------------------------------

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = rx {
        client.newCall(popularMangaRequest(page)).awaitSuccess().use { popularMangaParse(it) }
    }

    protected open fun popularMangaRequest(page: Int): Request = Request.Builder()
        .url("$baseUrl/library?page=$page")
        .headers(headers)
        .build()

    protected open fun popularMangaParse(response: Response): MangasPage = MangasPage(emptyList(), false)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = rx {
        client.newCall(searchMangaRequest(page, query, filters)).awaitSuccess().use { searchMangaParse(it) }
    }

    protected open fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        Request.Builder()
            .url("$baseUrl/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}&page=$page")
            .headers(headers)
            .build()

    protected open fun searchMangaParse(response: Response): MangasPage = MangasPage(emptyList(), false)

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = rx {
        client.newCall(latestUpdatesRequest(page)).awaitSuccess().use { latestUpdatesParse(it) }
    }

    protected open fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    protected open fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ---------------------------------------------------------------------------------------------
    // Details / chapters / pages
    // ---------------------------------------------------------------------------------------------

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> = rx { fetchMangaDetailsNow(manga) }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = rx { fetchChapterListNow(manga) }

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = supervisorScope {
        val asyncManga = if (fetchDetails) async { fetchMangaDetailsNow(manga) } else null
        val asyncChapters = if (fetchChapters) async { fetchChapterListNow(manga) } else null
        SMangaUpdate(asyncManga?.await() ?: manga, asyncChapters?.await() ?: chapters)
    }

    private suspend fun fetchMangaDetailsNow(manga: SManga): SManga {
        val response = client.newCall(mangaDetailsRequest(manga)).awaitSuccess()
        return response.use { mangaDetailsParse(it).apply { url = manga.url } }
    }

    private suspend fun fetchChapterListNow(manga: SManga): List<SChapter> {
        val response = client.newCall(chapterListRequest(manga)).awaitSuccess()
        return response.use { chapterListParse(it) }
    }

    protected open fun mangaDetailsRequest(manga: SManga): Request = Request.Builder()
        .url(manga.url)
        .headers(headers)
        .build()

    protected open fun mangaDetailsParse(response: Response): SManga =
        SManga.create().apply { url = response.request.url.toString() }

    protected open fun chapterListRequest(manga: SManga): Request = Request.Builder()
        .url(manga.url)
        .headers(headers)
        .build()

    protected open fun chapterListParse(response: Response): List<SChapter> = emptyList()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = rx {
        client.newCall(pageListRequest(chapter)).awaitSuccess().use { pageListParse(it) }
    }

    protected open fun pageListRequest(chapter: SChapter): Request = Request.Builder()
        .url(chapter.url)
        .headers(headers)
        .build()

    protected open fun pageListParse(response: Response): List<Page> = emptyList()

    protected open fun imageUrlRequest(page: Page): Request = Request.Builder()
        .url(page.url)
        .headers(headers)
        .build()

    protected open fun imageUrlParse(response: Response): String = response.request.url.toString()

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    override fun getFilterList(): FilterList = FilterList()

    override fun toString(): String = "$name (${lang.uppercase()})"

    protected fun <T> rx(block: suspend () -> T): Observable<T> =
        Observable.defer { Observable.just(kotlinx.coroutines.runBlocking { block() }) }
            .subscribeOn(Schedulers.io())

    companion object {
        const val USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
