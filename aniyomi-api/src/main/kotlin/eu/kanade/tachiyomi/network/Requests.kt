@file:Suppress("unused_parameter", "FunctionName", "DEPRECATION")

package eu.kanade.tachiyomi.network

import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

/*
 * Request helpers in the exact shape the Aniyomi/Animiru extensions-lib exposes.
 *
 * The JVM links these by class and method name, so they must live in `Requests.kt` (compiled to
 * `RequestsKt`) with the same parameters and default arguments as the library stubs. Moving them to
 * another file silently breaks every extension that calls GET/POST, with the install appearing to
 * succeed and the source then failing to load.
 */

private val DEFAULT_HEADERS: Headers = Headers.Builder().build()
private val DEFAULT_CACHE_CONTROL: CacheControl = CacheControl.Builder().build()

fun GET(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder()
    .url(url)
    .headers(headers)
    .cacheControl(cache)
    .build()

fun GET(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = GET(url.toString(), headers, cache)

fun POST(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = RequestBody.create(null, ByteArray(0)),
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request = Request.Builder()
    .url(url)
    .headers(headers)
    .cacheControl(cache)
    .post(body)
    .build()

@Deprecated("Source developers should define their own extension functions for more flexibility")
suspend fun OkHttpClient.get(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response = newCall(GET(url, headers, cache)).await()

@Deprecated("Source developers should define their own extension functions for more flexibility")
suspend fun OkHttpClient.get(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response = newCall(GET(url, headers, cache)).await()

@Deprecated("Source developers should define their own extension functions for more flexibility")
suspend fun OkHttpClient.post(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = RequestBody.create(null, ByteArray(0)),
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response = newCall(POST(url, headers, body, cache)).await()
