package eu.kanade.tachiyomi.network

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/*
 * Only helpers that no extension resolves by class name live here.
 *
 * The request/await/jsoup/json entry points extensions call (`GET`, `Call.await`,
 * `Response.asJsoup`, `Response.parseAs`) are declared in Requests.kt, OkHttpExtensions.kt,
 * util/JsoupExtensions.kt and util/JsonExtensions.kt so their JVM class names match the
 * extensions-lib they were compiled against.
 */

/** Shared JSON parser used by the runtime and by `parseAs`. */
val aniyomiJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

fun String.toJsoup(baseUrl: String? = null): Document = Jsoup.parse(this, baseUrl.orEmpty())

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
