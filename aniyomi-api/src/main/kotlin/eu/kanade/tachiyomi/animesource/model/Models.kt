package eu.kanade.tachiyomi.animesource.model

import java.io.Serializable
import okhttp3.Headers

/**
 * Anime entry, mirroring `eu.kanade.tachiyomi.animesource.model.SAnime` from Aniyomi.
 *
 * Extensions assign these properties directly (`anime.title = ...`), so they stay mutable and use
 * the same names — including Aniyomi's snake_case fields.
 */
class SAnime : Serializable {
    var url: String = ""
    var title: String = ""
    var artist: String? = null
    var author: String? = null
    var description: String? = null
    var genre: String? = null
    var status: Int = 0
    var thumbnail_url: String? = null
    var update_strategy: Int = 0
    var initialized: Boolean = false

    override fun equals(other: Any?): Boolean = other is SAnime && other.url == url

    override fun hashCode(): Int = url.hashCode()

    override fun toString(): String = "SAnime(url='$url', title='$title')"

    companion object {
        fun create(): SAnime = SAnime()
    }
}

/** Episode entry, mirroring Aniyomi's `SEpisode`. */
class SEpisode : Serializable {
    var url: String = ""
    var name: String = ""
    var date_upload: Long = 0L
    var episode_number: Float = 0f
    var scanlator: String? = null
    var filler: Boolean = false
    var thumbnail_url: String? = null

    override fun equals(other: Any?): Boolean = other is SEpisode && other.url == url

    override fun hashCode(): Int = url.hashCode()

    override fun toString(): String = "SEpisode(url='$url', name='$name')"
}

/** A single subtitle track exposed by a source. */
data class SubtitleTrack(val url: String, val lang: String)

/** A single audio track exposed by a source. */
data class AudioTrack(val url: String, val lang: String)

/** Playable video returned by `getVideoList`. */
class Video(
    var videoUrl: String,
    var videoTitle: String? = null,
    var quality: String? = null,
    var headers: Headers = Headers.Builder().build(),
    var subtitleTracks: List<SubtitleTrack>? = null,
    var audioTracks: List<AudioTrack>? = null,
) {
    override fun toString(): String = "Video(url='$videoUrl', quality='$quality')"
}

/** Page of results, as returned by popular / latest / search. */
data class AnimesPage(val animes: List<SAnime>, val hasNextPage: Boolean) {
    constructor() : this(emptyList(), false)
}

/** A hoster groups videos behind an intermediate page. */
class Hoster(
    var hosterName: String = "",
    var hosterUrl: String = "",
    var videoList: List<Video> = emptyList(),
)

/** Source status values used by `SAnime.status`. */
object AnimeStatus {
    const val UNKNOWN = 0
    const val ONGOING = 1
    const val COMPLETED = 2
    const val LICENSED = 3
    const val PUBLISHING_FINISHED = 4
    const val CANCELLED = 5
    const val ON_HIATUS = 6
}
