package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Selector driven anime source, matching Aniyomi/Anikku's `ParsedAnimeHttpSource`.
 *
 * Only the selectors are abstract in the real class; here they are open so packages built against
 * a different library generation still load.
 */
abstract class ParsedAnimeHttpSource : AnimeHttpSource() {

    protected open fun popularAnimeSelector(): String = ""
    protected open fun popularAnimeFromElement(element: Element): SAnime = SAnime.create()
    protected open fun popularAnimeNextPageSelector(): String? = null

    override fun popularAnimeParse(response: Response): AnimesPage = parseAnimePage(
        response.asJsoup(),
        popularAnimeSelector(),
        ::popularAnimeFromElement,
        ::popularAnimeNextPageSelector,
    )

    protected open fun searchAnimeSelector(): String = popularAnimeSelector()
    protected open fun searchAnimeFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    protected open fun searchAnimeNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun searchAnimeParse(response: Response): AnimesPage = parseAnimePage(
        response.asJsoup(),
        searchAnimeSelector(),
        ::searchAnimeFromElement,
        ::searchAnimeNextPageSelector,
    )

    protected open fun latestUpdatesSelector(): String = popularAnimeSelector()
    protected open fun latestUpdatesFromElement(element: Element): SAnime = popularAnimeFromElement(element)
    protected open fun latestUpdatesNextPageSelector(): String? = popularAnimeNextPageSelector()

    override fun latestUpdatesParse(response: Response): AnimesPage = parseAnimePage(
        response.asJsoup(),
        latestUpdatesSelector(),
        ::latestUpdatesFromElement,
        ::latestUpdatesNextPageSelector,
    )

    protected open fun animeDetailsParse(document: Document): SAnime = SAnime.create()

    override fun animeDetailsParse(response: Response): SAnime = animeDetailsParse(response.asJsoup())

    protected open fun episodeListSelector(): String = ""
    protected open fun episodeFromElement(element: Element): SEpisode = SEpisode.create()

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val selector = episodeListSelector()
        if (selector.isBlank()) return emptyList()
        return document.select(selector).map(::episodeFromElement)
    }

    protected open fun seasonListSelector(): String = episodeListSelector()
    protected open fun seasonFromElement(element: Element): SAnime = popularAnimeFromElement(element)

    override fun seasonListParse(response: Response): List<SAnime> =
        response.asJsoup().select(seasonListSelector()).map(::seasonFromElement)

    protected open fun hosterListSelector(): String = "a"
    protected open fun hosterFromElement(element: Element): Hoster = Hoster(
        hosterUrl = element.absUrl("href"),
        hosterName = element.text(),
    )

    override fun hosterListParse(response: Response): List<Hoster> {
        val selector = hosterListSelector()
        if (selector.isBlank()) return emptyList()
        return response.asJsoup().select(selector).map(::hosterFromElement).filter { it.hosterUrl.isNotBlank() }
    }

    protected open fun videoListParse(document: Document): List<Video> = emptyList()

    override fun videoListParse(response: Response): List<Video> = videoListParse(response.asJsoup())

    protected open fun videoListParse(document: Document, hoster: Hoster): List<Video> =
        videoListParse(document)

    override fun videoListParse(response: Response, hoster: Hoster): List<Video> =
        videoListParse(response.asJsoup(), hoster)

    private fun parseAnimePage(
        document: Document,
        selector: String,
        mapper: (Element) -> SAnime,
        nextPageSelector: () -> String?,
    ): AnimesPage {
        if (selector.isBlank()) return AnimesPage(emptyList(), false)
        val animes = document.select(selector).map(mapper)
        val hasNext = nextPageSelector()?.let { document.select(it).isNotEmpty() } ?: false
        return AnimesPage(animes, hasNext)
    }
}
