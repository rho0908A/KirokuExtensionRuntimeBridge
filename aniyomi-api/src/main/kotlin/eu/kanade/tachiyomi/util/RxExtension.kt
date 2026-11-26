package eu.kanade.tachiyomi.util

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import rx.Subscriber
import rx.Subscription

/**
 * Awaits the single item of an RxJava 1 `Observable`.
 *
 * Anikku declares this as an `expect` function because its source-api is multiplatform; the host
 * runtime only ships the JVM implementation.
 */
suspend fun <T> Observable<T>.awaitSingle(): T = suspendCancellableCoroutine { cont ->
    var value: T? = null
    var hasValue = false
    val subscription: Subscription = subscribe(
        object : Subscriber<T>() {
            override fun onNext(item: T) {
                value = item
                hasValue = true
            }

            override fun onCompleted() {
                if (!cont.isActive) return
                val result = value
                if (hasValue && result != null) {
                    cont.resume(result)
                } else {
                    cont.resumeWithException(NoSuchElementException("Observable completed without a value"))
                }
            }

            override fun onError(e: Throwable) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        },
    )
    cont.invokeOnCancellation { runCatching { subscription.unsubscribe() } }
}
