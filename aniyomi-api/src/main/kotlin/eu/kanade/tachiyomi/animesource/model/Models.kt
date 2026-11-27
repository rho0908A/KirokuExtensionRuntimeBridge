@file:Suppress("PropertyName", "UNUSED", "DEPRECATION")

package eu.kanade.tachiyomi.animesource.model

import java.io.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers

/**
 * Anime entry, mirroring `eu.kanade.tachiyomi.animesource.model.SAnime` from the Aniyomi/Animiru
 * extensions-lib (v17).
 *
 * Referenced by extensions as an interface with `SAnime.create()`, so the shape here is the library
 * contract, not an internal model.
 */
interface SAnime : Serializable {

    var url: String
    var title: String
    var artist: String?
    var author: String?
    var description: String?
    var genre: String?
    var status: Int
    var thumbnail_url: String?
    var background_url: String?

    var update_strategy: AnimeUpdateStrategy

    var fetch_type: FetchType
    var season_number: Double

    /** Source-specific metadata, added in extensions-lib 17. */
    var memo: JsonObject

    var initialized: Boolean

    fun getGenres(): List<String>? {
        if (genre.isNullOrBlank()) return null
        return genre?.split(", ")?.map { it.trim() }?.filterNot { it.isBlank() }?.distinct()
    }

    // "SY" compatibility accessors kept by the host library.
    val originalTitle: String get() = title
    val originalAuthor: String? get() = author
    val originalArtist: String? get() = artist
    val originalThumbnailUrl: String? get() = thumbnail_url
    val originalDescription: String? get() = description
    val originalGenre: String? get() = genre
    val originalStatus: Int get() = status

    /** Legacy setter: older libraries exposed `update_strategy` as an Int. */
    fun setUpdate_strategy(value: Int) {
        update_strategy =
            if (value == 1) AnimeUpdateStrategy.ONLY_FETCH_ONCE else AnimeUpdateStrategy.ALWAYS_UPDATE
    }

    fun copy(): SAnime = create().also {
        it.url = url
        it.title = title
        it.artist = artist
        it.author = author
        it.thumbnail_url = thumbnail_url
        it.description = description
        it.genre = genre
        it.status = status
        it.background_url = background_url
        it.update_strategy = update_strategy
        it.fetch_type = fetch_type
        it.season_number = season_number
        it.memo = memo
        it.initialized = initialized
    }

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6
        const val UPCOMING = 7

        fun create(): SAnime = SAnimeImpl()
    }
}

/** Concrete [SAnime] used by [SAnime.create]. */
class SAnimeImpl : SAnime {
    override var url: String = ""
    override var title: String = ""
    override var artist: String? = null
    override var author: String? = null
    override var description: String? = null
    override var genre: String? = null
    override var status: Int = 0
    override var thumbnail_url: String? = null
    override var background_url: String? = null
    override var update_strategy: AnimeUpdateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE
    override var fetch_type: FetchType = FetchType.Episodes
    override var season_number: Double = -1.0
    override var memo: JsonObject = JsonObject(emptyMap())
    override var initialized: Boolean = false

    override fun equals(other: Any?): Boolean = other is SAnime && other.url == url
    override fun hashCode(): Int = url.hashCode()
    override fun toString(): String = "SAnime(url='$url', title='$title')"
}

/** Episode entry, mirroring `SEpisode` from the anime extensions-lib. */
interface SEpisode : Serializable {

    var url: String
    var name: String
    var date_upload: Long
    var episode_number: Float
    var fillermark: Boolean
    var scanlator: String?
    var summary: String?
    var preview_url: String?

    /** Source-specific metadata, added in extensions-lib 17. */
    var memo: JsonObject

    /** Legacy aliases kept for extensions built against older libraries. */
    var filler: Boolean
        get() = fillermark
        set(value) {
            fillermark = value
        }

    var thumbnail_url: String?
        get() = preview_url
        set(value) {
            preview_url = value
        }

    var chapter_number: Float
        get() = episode_number
        set(value) {
            episode_number = value
        }

    fun copyFrom(other: SEpisode) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        episode_number = other.episode_number
        fillermark = other.fillermark
        scanlator = other.scanlator
        summary = other.summary
        preview_url = other.preview_url
        memo = other.memo
    }

    companion object {
        fun create(): SEpisode = SEpisodeImpl()
    }
}

/** Concrete [SEpisode] used by [SEpisode.create]. */
class SEpisodeImpl : SEpisode {
    override var url: String = ""
    override var name: String = ""
    override var date_upload: Long = 0L
    override var episode_number: Float = 0f
    override var fillermark: Boolean = false
    override var scanlator: String? = null
    override var summary: String? = null
    override var preview_url: String? = null
    override var memo: JsonObject = JsonObject(emptyMap())

    override fun equals(other: Any?): Boolean = other is SEpisode && other.url == url
    override fun hashCode(): Int = url.hashCode()
    override fun toString(): String = "SEpisode(url='$url', name='$name')"
}

/** A sub/dub track. */
data class Track(val url: String, val lang: String)

/** Timestamp kind, as defined by extensions-lib v17. */
enum class ChapterType {
    Opening,
    Ending,
    Recap,
    MixedOp,
    Other,
}

/** A timestamp displayed as a video chapter. */
data class TimeStamp(
    val start: Double,
    val end: Double,
    val name: String,
    val type: ChapterType = ChapterType.Other,
)

/**
 * Playable video returned by `getVideoList`.
 *
 * The parameter list is the extensions-lib v17 contract (including `memo`); extensions construct it
 * positionally or by name, so the arity and order must not change.
 */
data class Video(
    val videoUrl: String = "",
    val videoTitle: String = "",
    val resolution: Int? = null,
    val bitrate: Int? = null,
    val headers: Headers? = null,
    val preferred: Boolean = false,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val timestamps: List<TimeStamp> = emptyList(),
    val mpvArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
    @Deprecated("Use memo instead", level = DeprecationLevel.ERROR)
    val internalData: String = "",
    val initialized: Boolean = false,
    val memo: JsonObject = JsonObject(emptyMap()),
) {
    @Deprecated("Use the new Video constructor", level = DeprecationLevel.ERROR)
    constructor(
        url: String,
        quality: String,
        videoUrl: String?,
        headers: Headers? = null,
        subtitleTracks: List<Track> = emptyList(),
        audioTracks: List<Track> = emptyList(),
    ) : this(
        videoTitle = quality,
        videoUrl = videoUrl ?: "null",
        headers = headers,
        subtitleTracks = subtitleTracks,
        audioTracks = audioTracks,
    )

    @Deprecated("Use videoTitle instead", ReplaceWith("videoTitle"))
    val quality: String get() = videoTitle

    @Deprecated("Use videoUrl instead", ReplaceWith("videoUrl"))
    val url: String get() = videoUrl

    /** Aniyomi lib 12-15 constructor: `Video(url, quality, videoUrl, uri, headers)`. */
    @Deprecated("Use the primary constructor")
    constructor(
        url: String,
        quality: String,
        videoUrl: String?,
        uri: android.net.Uri?,
        headers: Headers? = null,
    ) : this(url, quality, videoUrl, headers)

    override fun toString(): String = "Video(url='$videoUrl', title='$videoTitle')"
}

/** A hoster groups videos behind an intermediate page. */
class Hoster(
    val hosterUrl: String = "",
    val hosterName: String = "",
    val videoList: List<Video>? = null,
    @Deprecated("Use memo instead", level = DeprecationLevel.ERROR)
    val internalData: String = "",
    val lazy: Boolean = false,
    val memo: JsonObject = JsonObject(emptyMap()),
) {
    companion object {
        const val NO_HOSTER_LIST = "no_hoster_list"

        fun List<Video>.toHosterList(): List<Hoster> =
            listOf(Hoster(hosterUrl = "", hosterName = NO_HOSTER_LIST, videoList = this))
    }
}

/** A named group of anime related to a specific entry (extensions-lib 17). */
class AnimeRelation(
    val name: String,
    val animes: List<SAnime>,
)

/** Page of results, as returned by popular / latest / search. */
open class AnimesPage(open val animes: List<SAnime>, open val hasNextPage: Boolean) {

    constructor() : this(emptyList(), false)

    val mangas: List<SAnime> get() = animes

    fun copy(animes: List<SAnime> = this.animes, hasNextPage: Boolean = this.hasNextPage): AnimesPage =
        AnimesPage(animes, hasNextPage)

    operator fun component1(): List<SAnime> = animes
    operator fun component2(): Boolean = hasNextPage

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnimesPage) return false
        return animes == other.animes && hasNextPage == other.hasNextPage
    }

    override fun hashCode(): Int = 31 * animes.hashCode() + hasNextPage.hashCode()

    override fun toString(): String = "AnimesPage(animes=$animes, hasNextPage=$hasNextPage)"
}

/** Thumbnail tiles information (extensions-lib 17). */
open class ThumbnailInfo(
    val tileInfo: List<TileInfo>,
    val imageTileUrls: List<String>,
)

/** One tile of a thumbnail sprite sheet. */
data class TileInfo(
    val imageIndex: Int,
    val timeMs: Long,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** Update strategy for a single anime. */
enum class AnimeUpdateStrategy {
    ALWAYS_UPDATE,
    ONLY_FETCH_ONCE,
}

/** What an [SAnime] entry should resolve (since extensions-lib 16). */
enum class FetchType {
    Seasons,
    Episodes,
}

/** Source status values used by `SAnime.status`. */
object AnimeStatus {
    const val UNKNOWN = 0
    const val ONGOING = 1
    const val COMPLETED = 2
    const val LICENSED = 3
    const val PUBLISHING_FINISHED = 4
    const val CANCELLED = 5
    const val ON_HIATUS = 6
    const val UPCOMING = 7
}
