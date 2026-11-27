package eu.kanade.tachiyomi.util

import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/*
 * Lives in `util/JsoupExtensions.kt` so extensions resolve `JsoupExtensionsKt.asJsoup`.
 */

/**
 * Returns a Jsoup document for this response, using the response URL as base URI.
 *
 * @param html the already-read body, for extensions that consumed the stream first.
 */
fun Response.asJsoup(html: String? = null): Document =
    Jsoup.parse(html ?: body?.string().orEmpty(), request.url.toString())
