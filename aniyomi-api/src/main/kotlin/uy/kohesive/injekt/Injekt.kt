package uy.kohesive.injekt

import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal Injekt-compatible service locator.
 *
 * Some Aniyomi extensions resolve `OkHttpClient` (or their own singletons) through
 * `Injekt.get<T>()`. The real Injekt library is not required for that pattern, so the runtime
 * provides a small implementation with the same call-site API.
 */
object Injekt {

    private val singletons = ConcurrentHashMap<Class<*>, Any>()

    fun <T : Any> addSingleton(type: Class<T>, instance: T) {
        singletons[type] = instance
    }

    fun addSingleton(instance: Any) {
        singletons[instance.javaClass] = instance
        instance.javaClass.interfaces.forEach { singletons[it] = instance }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: Class<T>): T {
        singletons[type]?.let { return it as T }
        // Fall back to assignable registrations (interfaces / superclasses).
        singletons.entries.firstOrNull { type.isAssignableFrom(it.key) }?.let { return it.value as T }
        throw IllegalStateException("No singleton registered for ${type.name}")
    }

    fun clear() = singletons.clear()
}
