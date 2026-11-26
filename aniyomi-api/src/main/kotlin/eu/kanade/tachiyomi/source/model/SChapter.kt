@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source.model

import java.io.Serializable
import kotlinx.serialization.json.JsonObject

/** Mihon's chapter model (1.6). */
interface SChapter : Serializable {

    var url: String
    var name: String
    var chapter_number: Float
    var scanlator: String?
    var date_upload: Long

    var memo: JsonObject

    fun copyFrom(other: SChapter) {
        name = other.name
        url = other.url
        date_upload = other.date_upload
        chapter_number = other.chapter_number
        scanlator = other.scanlator
        memo = other.memo
    }

    companion object {
        fun create(): SChapter = SChapterImpl()
    }
}

/** Concrete [SChapter] used by [SChapter.create]. */
class SChapterImpl : SChapter {
    override var url: String = ""
    override var name: String = ""
    override var chapter_number: Float = -1f
    override var scanlator: String? = null
    override var date_upload: Long = 0L
    override var memo: JsonObject = JsonObject(emptyMap())

    override fun equals(other: Any?): Boolean = other is SChapter && other.url == url
    override fun hashCode(): Int = url.hashCode()
    override fun toString(): String = "SChapter(url='$url', name='$name')"
}

/** Result of [eu.kanade.tachiyomi.source.Source.getMangaUpdate] (Mihon 1.6). */
data class SMangaUpdate(val manga: SManga, val chapters: List<SChapter>)
