@file:Suppress("DEPRECATION")

package eu.kanade.tachiyomi.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/*
 * `CoroutinesExtensionsKt`: parallel map helpers shipped since extensions-lib 14.
 */

suspend inline fun <A, B> Iterable<A>.parallelMap(crossinline f: suspend (A) -> B): List<B> =
    withContext(Dispatchers.IO) {
        map { async { f(it) } }.awaitAll()
    }

inline fun <A, B> Iterable<A>.parallelMapBlocking(crossinline f: suspend (A) -> B): List<B> =
    runBlocking { parallelMap(f) }

suspend inline fun <A, B> Iterable<A>.parallelMapNotNull(crossinline f: suspend (A) -> B?): List<B> =
    withContext(Dispatchers.IO) {
        map { async { f(it) } }.awaitAll().filterNotNull()
    }

inline fun <A, B> Iterable<A>.parallelMapNotNullBlocking(crossinline f: suspend (A) -> B?): List<B> =
    runBlocking { parallelMapNotNull(f) }

suspend inline fun <A, B> Iterable<A>.parallelFlatMap(crossinline f: suspend (A) -> Iterable<B>): List<B> =
    withContext(Dispatchers.IO) {
        map { async { f(it) } }.awaitAll().flatten()
    }

inline fun <A, B> Iterable<A>.parallelFlatMapBlocking(crossinline f: suspend (A) -> Iterable<B>): List<B> =
    runBlocking { parallelFlatMap(f) }

suspend inline fun <A, B> Iterable<A>.parallelCatchingFlatMap(crossinline f: suspend (A) -> Iterable<B>): List<B> =
    withContext(Dispatchers.IO) {
        map {
            async {
                try {
                    f(it)
                } catch (e: Throwable) {
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

inline fun <A, B> Iterable<A>.parallelCatchingFlatMapBlocking(crossinline f: suspend (A) -> Iterable<B>): List<B> =
    runBlocking { parallelCatchingFlatMap(f) }
