package eu.kanade.tachiyomi.network

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Shared JSON parser used by `parseAs`; extensions that bring their own Json still work. */
val aniyomiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

fun GET(url: String, headers: Headers = Headers.Builder().build()): Request =
    Request.Builder().url(url).headers(headers).build()

fun GET(url: String, headers: Map<String, String>): Request =
    GET(url, Headers.Builder().apply { headers.forEach { (k, v) -> add(k, v) } }.build())

fun POST(url: String, body: okhttp3.RequestBody, headers: Headers = Headers.Builder().build()): Request =
    Request.Builder().url(url).headers(headers).post(body).build()

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

inline fun <reified T> Response.parseAs(): T = aniyomiJson.decodeFromString(
    kotlinx.serialization.serializer<T>(),
    body?.string().orEmpty(),
)

fun <T> Response.parseAs(serializer: KSerializer<T>): T =
    aniyomiJson.decodeFromString(serializer, body?.string().orEmpty())

/** Parses a response body as HTML, with the source URL set as base URI. */
fun Response.asJsoup(): Document = Jsoup.parse(body?.string().orEmpty(), request.url.toString())

fun Response.asJsoup(url: String): Document = Jsoup.parse(body?.string().orEmpty(), url)

fun String.toJsoup(baseUrl: String? = null): Document = Jsoup.parse(this, baseUrl.orEmpty())

fun String.toJsoup(headers: Headers, baseUrl: String? = null): Document = Jsoup.parse(this, baseUrl.orEmpty())

/**
 * Resolves a possibly-relative URL returned by an extension against the source's base URL.
 * Extensions are inconsistent here; the runtime normalises before handing media to a host.
 */
fun normalizeMediaUrl(url: String?, baseUrl: String?): String? {
    if (url.isNullOrBlank()) return url
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    val base = baseUrl?.toHttpUrlOrNull() ?: return url
    return base.resolve(url)?.toString() ?: url
}

/** OkHttp is provided by the runtime; extensions obtain their client from `AnimeHttpSource`. */
object NetworkDefaults {
    @Volatile
    var client: okhttp3.OkHttpClient? = null
}
