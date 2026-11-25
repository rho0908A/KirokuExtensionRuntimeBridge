package tachiyomi.core.common.util.lang

/** Query cleanup used by Anikku's related-content defaults. */
object QuerySanitizer {

    private val ILLEGAL_CHARACTERS = Regex("[^\\p{L}\\p{N}\\s-]")

    fun String.sanitize(): String = this
        .replace(ILLEGAL_CHARACTERS, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
