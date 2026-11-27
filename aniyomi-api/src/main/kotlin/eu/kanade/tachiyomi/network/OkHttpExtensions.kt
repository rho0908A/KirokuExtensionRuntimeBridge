package eu.kanade.tachiyomi.network

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/*
 * `Call.await()`/`awaitSuccess()` must stay in `OkHttpExtensions.kt`: extensions are linked against
 * `OkHttpExtensionsKt` and resolve these by class name, so a copy in another file is never found.
 */

/** Awaits a call without suspending a thread; cancellation cancels the underlying call. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

/** Awaits a call and throws when the response is not successful. Caller owns the response. */
suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (!response.isSuccessful) {
        val body = runCatching { response.peekBody(4096).string() }.getOrNull()
        response.close()
        throw IllegalStateException("HTTP ${response.code} for ${response.request.url}: ${body.orEmpty().take(200)}")
    }
    return response
}
