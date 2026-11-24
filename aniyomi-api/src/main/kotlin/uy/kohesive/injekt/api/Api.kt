package uy.kohesive.injekt.api

import uy.kohesive.injekt.Injekt

/** `Injekt.get<T>()`, matching the API older Aniyomi extensions were compiled against. */
inline fun <reified T : Any> Injekt.get(): T = get(T::class.java)

/** Lazily resolved singleton, resolved on first access. */
inline fun <reified T : Any> injectLazy(): Lazy<T> = lazy { Injekt.get<T>() }
