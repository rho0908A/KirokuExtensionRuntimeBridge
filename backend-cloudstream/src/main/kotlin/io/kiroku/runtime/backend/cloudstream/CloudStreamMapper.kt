package io.kiroku.runtime.backend.cloudstream

import com.lagradost.cloudstream3.AudioFile
import com.lagradost.cloudstream3.Episode as CsEpisode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import io.kiroku.runtime.ipc.Anime
import io.kiroku.runtime.ipc.AudioTrack
import io.kiroku.runtime.ipc.Episode
import io.kiroku.runtime.ipc.StreamSource
import io.kiroku.runtime.ipc.Subtitle

/**
 * CloudStream -> bridge model conversion.
 *
 * CloudStream's `Episode.data` is an opaque payload the provider later passes to `loadLinks`; it is
 * therefore used verbatim as the episode id. Nothing that the plugin did not expose is invented.
 */
internal object CloudStreamMapper {

    fun SearchResponse.toBridgeAnime(extensionId: String): Anime = Anime(
        id = url,
        title = name,
        url = url,
        coverUrl = posterUrl?.takeIf { it.isNotBlank() },
        description = null,
        extensionId = extensionId,
    )

    fun LoadResponse.toBridgeAnime(extensionId: String): Anime = Anime(
        id = url,
        title = name,
        url = url,
        coverUrl = posterUrl?.takeIf { it.isNotBlank() },
        description = plot?.takeIf { it.isNotBlank() },
        genres = tags.orEmpty(),
        year = year,
        extensionId = extensionId,
    )

    fun LoadResponse.toBridgeEpisodes(animeUrl: String?): List<Episode> = when (this) {
        is TvSeriesLoadResponse -> episodes.map { it.toBridgeEpisode(animeUrl, null) }
        is AnimeLoadResponse -> episodes.entries.flatMap { (dub, list) ->
            list.map { it.toBridgeEpisode(animeUrl, dub.name) }
        }.distinctBy { it.url }
        is MovieLoadResponse -> listOf(
            Episode(
                id = dataUrl,
                animeId = animeUrl ?: url,
                name = name,
                url = dataUrl,
                episodeNumber = 1.0,
                thumbnailUrl = posterUrl?.takeIf { it.isNotBlank() },
            ),
        )
        else -> emptyList()
    }

    private fun CsEpisode.toBridgeEpisode(animeUrl: String?, group: String?): Episode = Episode(
        id = data.orEmpty(),
        animeId = animeUrl,
        name = name.orEmpty().ifBlank { episode?.let { "Episode $it" } ?: "Episode" },
        url = data,
        episodeNumber = episode?.toDouble(),
        seasonNumber = season,
        description = description?.takeIf { it.isNotBlank() },
        thumbnailUrl = posterUrl?.takeIf { it.isNotBlank() },
        scanlator = group,
        dateUpload = date,
    )

    fun SubtitleFile.toBridgeSubtitle(): Subtitle = Subtitle(
        url = url,
        label = lang,
        language = lang,
        headers = headers.orEmpty(),
    )

    fun ExtractorLink.toBridgeStream(
        subtitles: List<Subtitle>,
        episodeId: String?,
    ): StreamSource = StreamSource(
        url = url,
        quality = qualityLabel(quality),
        qualityValue = quality.takeIf { it > 0 },
        server = source.takeIf { it.isNotBlank() },
        title = name.takeIf { it.isNotBlank() },
        referer = referer?.takeIf { it.isNotBlank() },
        userAgent = headers?.entries?.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value,
        mimeType = mimeTypeFor(type),
        isM3u8 = type == ExtractorLinkType.M3U8,
        isDash = type == ExtractorLinkType.DASH,
        headers = headers.orEmpty().toMutableMap().apply {
            val refererValue = referer
            if (!refererValue.isNullOrBlank() && none { it.key.equals("Referer", true) }) {
                put("Referer", refererValue)
            }
        },
        subtitles = subtitles,
        audioTracks = audioTracks.orEmpty().map { it.toBridgeAudioTrack() },
        episodeId = episodeId,
    )

    private fun AudioFile.toBridgeAudioTrack(): AudioTrack = AudioTrack(
        url = url,
        label = null,
        language = null,
        headers = headers.orEmpty(),
    )

    fun qualityLabel(quality: Int): String? = when {
        quality <= 0 || quality == 400 -> null
        quality >= 2160 -> "4K"
        quality >= 1080 -> "1080p"
        quality >= 720 -> "720p"
        quality >= 480 -> "480p"
        quality >= 360 -> "360p"
        else -> "${quality}p"
    }

    private fun mimeTypeFor(type: ExtractorLinkType?): String? = when (type) {
        ExtractorLinkType.M3U8 -> "application/x-mpegURL"
        ExtractorLinkType.DASH -> "application/dash+xml"
        ExtractorLinkType.VIDEO -> "video/mp4"
        else -> null
    }
}
