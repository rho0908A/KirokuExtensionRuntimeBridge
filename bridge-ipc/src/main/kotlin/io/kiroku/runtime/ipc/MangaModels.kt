package io.kiroku.runtime.ipc

import kotlinx.serialization.Serializable

/** Which kind of media a backend/source exposes. */
@Serializable
enum class SourceMediaType {
    ANIME,
    MANGA,
}

/** Search / browse result entry for manga. */
@Serializable
data class Manga(
    val id: String,
    val title: String,
    val url: String? = null,
    val coverUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    val extensionId: String? = null,
)

/** A manga plus the chapter list the source returned with it. */
@Serializable
data class MangaDetails(
    val manga: Manga,
    val chapters: List<Chapter> = emptyList(),
    val extensionId: String? = null,
)

/** A chapter of a manga. [url] doubles as the stable id. */
@Serializable
data class Chapter(
    val id: String,
    val mangaId: String? = null,
    val name: String,
    val url: String? = null,
    val chapterNumber: Double? = null,
    val scanlator: String? = null,
    val dateUpload: Long? = null,
)

/** A single page image of a chapter. */
@Serializable
data class MangaPage(
    val index: Int,
    val url: String,
    val imageUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
)
