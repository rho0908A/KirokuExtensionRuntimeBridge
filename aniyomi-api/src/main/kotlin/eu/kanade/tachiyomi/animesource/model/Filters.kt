package eu.kanade.tachiyomi.animesource.model

/**
 * Filter model mirroring Anikku's `AnimeFilter` hierarchy. Extensions build these lists and pass
 * them to `getSearchAnime`; the runtime keeps the extension's own state.
 */
open class AnimeFilter<T>(val name: String, var state: T) {

    class Header(name: String) : AnimeFilter<Any>(name, Any())

    class Separator(name: String = "") : AnimeFilter<Any>(name, Any())

    class Text(name: String, state: String = "") : AnimeFilter<String>(name, state)

    class Select<V>(name: String, val values: Array<V>, state: Int = 0) : AnimeFilter<Int>(name, state) {
        fun getSelected(): V = values[state]
    }

    class CheckBox(name: String, state: Boolean = false) : AnimeFilter<Boolean>(name, state)

    class TriState(name: String, state: Int = STATE_IGNORE) : AnimeFilter<Int>(name, state) {
        fun isIgnored(): Boolean = state == STATE_IGNORE
        fun isIncluded(): Boolean = state == STATE_INCLUDE
        fun isExcluded(): Boolean = state == STATE_EXCLUDE
    }

    class Group<V>(name: String, state: List<V>) : AnimeFilter<List<V>>(name, state)

    class Sort(name: String, val values: Array<String>, state: Selection) :
        AnimeFilter<Sort.Selection>(name, state) {
        data class Selection(val index: Int = 0, val ascending: Boolean = true)
    }

    companion object {
        const val STATE_IGNORE = 0
        const val STATE_INCLUDE = 1
        const val STATE_EXCLUDE = 2
    }
}

/**
 * Ordered collection of filters handed to a source's search method.
 *
 * Anikku made this a `List` delegate (`data class AnimeFilterList(val list: List<AnimeFilter<*>>) :
 * List<AnimeFilter<*>> by list`), which extensions rely on when they iterate the list.
 */
data class AnimeFilterList(val list: List<AnimeFilter<*>>) : List<AnimeFilter<*>> by list {

    constructor(vararg fs: AnimeFilter<*>) : this(if (fs.isNotEmpty()) fs.asList() else emptyList())

    override fun equals(other: Any?): Boolean = false

    override fun hashCode(): Int = list.hashCode()
}
