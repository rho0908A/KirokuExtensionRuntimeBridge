package eu.kanade.tachiyomi.animesource.model

/**
 * Filter model mirroring Aniyomi's `AnimeFilter` hierarchy. Extensions build these lists and the
 * runtime applies host-supplied state before calling `getSearchAnime`.
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

/** Ordered collection of filters handed to a source's search method. */
class AnimeFilterList(vararg filters: AnimeFilter<*>) {

    val list: MutableList<AnimeFilter<*>> = filters.toMutableList()

    val size: Int get() = list.size

    fun add(filter: AnimeFilter<*>) {
        list.add(filter)
    }

    fun addAll(filters: List<AnimeFilter<*>>) {
        list.addAll(filters)
    }

    fun get(index: Int): AnimeFilter<*> = list[index]

    operator fun plus(other: AnimeFilterList): AnimeFilterList =
        AnimeFilterList(*list.toTypedArray(), *other.list.toTypedArray())

    fun isEmpty(): Boolean = list.isEmpty()
}
