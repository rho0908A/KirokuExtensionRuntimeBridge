package eu.kanade.tachiyomi.source.model

/** Mihon's result page. */
class MangasPage(val mangas: List<SManga>, val hasNextPage: Boolean) {

    operator fun component1(): List<SManga> = mangas

    operator fun component2(): Boolean = hasNextPage

    fun copy(mangas: List<SManga> = this.mangas, hasNextPage: Boolean = this.hasNextPage): MangasPage =
        MangasPage(mangas, hasNextPage)

    override fun equals(other: Any?): Boolean =
        other is MangasPage && other.mangas == mangas && other.hasNextPage == hasNextPage

    override fun hashCode(): Int = 31 * mangas.hashCode() + hasNextPage.hashCode()

    override fun toString(): String = "MangasPage(mangas=$mangas, hasNextPage=$hasNextPage)"
}
