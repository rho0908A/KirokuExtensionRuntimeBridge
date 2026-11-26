package eu.kanade.tachiyomi.source.model

/**
 * Filter hierarchy shared by Mihon manga sources. Structurally identical to the anime filters, but
 * under the manga package name, because extensions import exactly that.
 */
open class Filter<T>(val name: String, var state: T) {

    class Header(name: String) : Filter<Any>(name, Any())

    class Separator(name: String = "") : Filter<Any>(name, Any())

    class Text(name: String, state: String = "") : Filter<String>(name, state)

    class Select<V>(name: String, val values: Array<V>, state: Int = 0) : Filter<Int>(name, state) {
        fun getSelected(): V = values[state]
    }

    class CheckBox(name: String, state: Boolean = false) : Filter<Boolean>(name, state)

    class TriState(name: String, state: Int = STATE_IGNORE) : Filter<Int>(name, state) {
        fun isIgnored(): Boolean = state == STATE_IGNORE
        fun isIncluded(): Boolean = state == STATE_INCLUDE
        fun isExcluded(): Boolean = state == STATE_EXCLUDE
    }

    class Group<V>(name: String, state: List<V>) : Filter<List<V>>(name, state)

    class Sort(name: String, val values: Array<String>, state: Selection) :
        Filter<Sort.Selection>(name, state) {
        data class Selection(val index: Int = 0, val ascending: Boolean = true)
    }

    companion object {
        const val STATE_IGNORE = 0
        const val STATE_INCLUDE = 1
        const val STATE_EXCLUDE = 2
    }
}

/** Ordered collection of filters handed to a source's search method. */
class FilterList(vararg filters: Filter<*>) : List<Filter<*>> {

    private val items = filters.toMutableList()

    override val size: Int get() = items.size

    override fun isEmpty(): Boolean = items.isEmpty()

    override fun iterator(): Iterator<Filter<*>> = items.iterator()

    override fun listIterator(): ListIterator<Filter<*>> = items.listIterator()

    override fun listIterator(index: Int): ListIterator<Filter<*>> = items.listIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int): List<Filter<*>> = items.subList(fromIndex, toIndex)

    override fun get(index: Int): Filter<*> = items[index]

    override fun contains(element: Filter<*>): Boolean = items.contains(element)

    override fun containsAll(elements: Collection<Filter<*>>): Boolean = items.containsAll(elements)

    override fun indexOf(element: Filter<*>): Int = items.indexOf(element)

    override fun lastIndexOf(element: Filter<*>): Int = items.lastIndexOf(element)

    fun add(filter: Filter<*>) {
        items.add(filter)
    }

    fun addAll(filters: List<Filter<*>>) {
        items.addAll(filters)
    }

    fun remove(filter: Filter<*>) {
        items.remove(filter)
    }
}
