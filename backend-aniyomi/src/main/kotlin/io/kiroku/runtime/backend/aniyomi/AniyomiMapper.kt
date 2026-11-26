package io.kiroku.runtime.backend.aniyomi

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import io.kiroku.runtime.ipc.Anime
import io.kiroku.runtime.ipc.AudioTrack
import io.kiroku.runtime.ipc.Episode
import io.kiroku.runtime.ipc.StreamSource
import io.kiroku.runtime.ipc.Subtitle

/**
 * Converts Anikku anime models to bridge models.
 *
 * Only values the source actually exposes are copied: no invented qualities, languages or tracks.
 * `anime.url` doubles as the stable anime id, and `episode.url` as the episode id, because that is
 * what sources require to resolve details, episodes and videos later on.
 */
internal object AniyomiMapper {

    fun SAnime.toBridgeAnime(extensionId: String?): Anime = Anime(
        id = url,
        title = title,
        url = url,
        coverUrl = thumbnail_url?.takeIf { it.isNotBlank() },
        description = description?.takeIf { it.isNotBlank() },
        genres = genre?.split(',', '/')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
        author = author?.takeIf { it.isNotBlank() },
        artist = artist?.takeIf { it.isNotBlank() },
        status = status.toStatusName(),
        extensionId = extensionId,
    )

    fun SEpisode.toBridgeEpisode(animeUrl: String?): Episode = Episode(
        id = url,
        animeId = animeUrl,
        name = name.ifBlank { "Episode ${episode_number.toInt()}" },
        url = url,
        episodeNumber = episode_number.toDouble(),
        description = summary?.takeIf { it.isNotBlank() },
        thumbnailUrl = preview_url?.takeIf { it.isNotBlank() },
        scanlator = scanlator?.takeIf { it.isNotBlank() },
        dateUpload = date_upload.takeIf { it > 0 },
        filler = fillermark,
    )

    fun Video.toStreamSource(episodeUrl: String?): StreamSource {
        val headerMap = headers?.toMultimap()
            ?.mapValues { (_, values) -> values.joinToString(", ") }
            .orEmpty()
        val referer = headerMap.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
        val userAgent = headerMap.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value

        return StreamSource(
            url = videoUrl,
            quality = videoTitle.takeIf { it.isNotBlank() },
            qualityValue = resolution ?: qualityValue(videoTitle),
            server = null,
            title = videoTitle.takeIf { it.isNotBlank() },
            referer = referer,
            userAgent = userAgent,
            mimeType = mimeTypeFor(videoUrl),
            isM3u8 = videoUrl.contains(".m3u8", ignoreCase = true),
            isDash = videoUrl.contains(".mpd", ignoreCase = true),
            headers = headerMap,
            subtitles = subtitleTracks.map { Subtitle(url = it.url, label = it.lang, language = it.lang) },
            audioTracks = audioTracks.map { AudioTrack(url = it.url, label = it.lang, language = it.lang) },
            episodeId = episodeUrl,
        )
    }

    fun qualityValue(quality: String?): Int? {
        if (quality.isNullOrBlank()) return null
        val digits = quality.filter { it.isDigit() }
        if (digits.isNotEmpty()) return digits.toIntOrNull()
        return when (quality.lowercase()) {
            "4k", "uhd" -> 2160
            "fhd" -> 1080
            "hd" -> 720
            "sd" -> 480
            else -> null
        }
    }

    private fun mimeTypeFor(url: String): String? = when {
        url.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
        url.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
        url.contains(".mp4", ignoreCase = true) -> "video/mp4"
        url.contains(".webm", ignoreCase = true) -> "video/webm"
        url.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
        else -> null
    }

    private fun Int.toStatusName(): String? = when (this) {
        1 -> "Ongoing"
        2 -> "Completed"
        3 -> "Licensed"
        4 -> "Publishing finished"
        5 -> "Cancelled"
        6 -> "On hiatus"
        else -> null
    }
}
