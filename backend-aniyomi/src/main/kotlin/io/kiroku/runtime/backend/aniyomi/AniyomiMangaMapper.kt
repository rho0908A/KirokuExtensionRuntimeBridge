package io.kiroku.runtime.backend.aniyomi

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import io.kiroku.runtime.ipc.Chapter
import io.kiroku.runtime.ipc.Manga
import io.kiroku.runtime.ipc.MangaPage

/**
 * Converts Mihon manga models to bridge models.
 *
 * `manga.url` is the stable id and `chapter.url` the chapter id, because that is what sources need
 * to resolve details, chapters and pages later.
 */
internal object AniyomiMangaMapper {

    fun SManga.toBridgeManga(extensionId: String?): Manga = Manga(
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

    fun SChapter.toBridgeChapter(mangaUrl: String?): Chapter = Chapter(
        id = url,
        mangaId = mangaUrl,
        name = name.ifBlank { "Chapter ${chapter_number.toInt()}" },
        url = url,
        chapterNumber = chapter_number.toDouble().takeIf { it >= 0 },
        scanlator = scanlator?.takeIf { it.isNotBlank() },
        dateUpload = date_upload.takeIf { it > 0 },
    )

    fun Page.toBridgePage(): MangaPage = MangaPage(
        index = index,
        url = url,
        imageUrl = imageUrl?.takeIf { it.isNotBlank() } ?: url.takeIf { it.isNotBlank() },
        headers = emptyMap(),
    )

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
