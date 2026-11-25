package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.asJsoup
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Selector driven manga source, matching Mihon's `ParsedHttpSource`.
 *
 * The selector members are open (Mihon marks several abstract) so that packages built against a
 * different extension-library generation still load.
 */
abstract class ParsedHttpSource : HttpSource() {

    protected open fun popularMangaSelector(): String = ""
    protected open fun popularMangaFromElement(element: Element): SManga = SManga.create()
    protected open fun popularMangaNextPageSelector(): String? = null

    protected fun parseMangas(
        document: Document,
        selector: String,
        mapper: (Element) -> SManga,
        nextPageSelector: () -> String?,
    ): MangasPage {
        if (selector.isBlank()) return MangasPage(emptyList(), false)
        val mangas = document.select(selector).map(mapper)
        val hasNext = nextPageSelector()?.let { document.select(it).isNotEmpty() } ?: false
        return MangasPage(mangas, hasNext)
    }

    override fun popularMangaParse(response: Response): MangasPage =
        parseMangas(response.asJsoup(), popularMangaSelector(), ::popularMangaFromElement, ::popularMangaNextPageSelector)

    protected open fun searchMangaSelector(): String = popularMangaSelector()
    protected open fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    protected open fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun searchMangaParse(response: Response): MangasPage =
        parseMangas(response.asJsoup(), searchMangaSelector(), ::searchMangaFromElement, ::searchMangaNextPageSelector)

    protected open fun latestUpdatesSelector(): String = popularMangaSelector()
    protected open fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    protected open fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun latestUpdatesParse(response: Response): MangasPage =
        parseMangas(response.asJsoup(), latestUpdatesSelector(), ::latestUpdatesFromElement, ::latestUpdatesNextPageSelector)

    protected open fun mangaDetailsParse(document: Document): SManga = SManga.create()

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    protected open fun chapterListSelector(): String = ""
    protected open fun chapterFromElement(element: Element): SChapter = SChapter.create()

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val selector = chapterListSelector()
        if (selector.isBlank()) return emptyList()
        return document.select(selector).map(::chapterFromElement)
    }

    protected open fun pageListParse(document: Document): List<Page> = emptyList()

    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    protected open fun imageUrlParse(document: Document): String = ""

    override fun imageUrlParse(response: Response): String = imageUrlParse(response.asJsoup())
}
