package io.kiroku.runtime.backend

import io.kiroku.runtime.ipc.Chapter
import io.kiroku.runtime.ipc.Manga
import io.kiroku.runtime.ipc.MangaDetails
import io.kiroku.runtime.ipc.MangaPage

/**
 * Manga execution contract, implemented by backends that can run manga sources.
 *
 * It is separate from [ExtensionBackend] so that anime-only ecosystems (CloudStream) are not forced
 * to invent manga behaviour: the runtime answers `EXTENSION_UNSUPPORTED` when a backend does not
 * implement this interface.
 */
interface MangaBackend {

    suspend fun searchManga(extensionId: String, sourceId: String?, query: String, page: Int): List<Manga>

    suspend fun popularManga(extensionId: String, sourceId: String?, page: Int): List<Manga>

    suspend fun latestManga(extensionId: String, sourceId: String?, page: Int): List<Manga>

    suspend fun mangaDetails(extensionId: String, sourceId: String?, mangaUrl: String): MangaDetails

    suspend fun chapters(extensionId: String, sourceId: String?, mangaUrl: String): List<Chapter>

    suspend fun pages(extensionId: String, sourceId: String?, chapterUrl: String): List<MangaPage>
}
