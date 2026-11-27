@file:Suppress("DEPRECATION", "UNCHECKED_CAST")

package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.network.aniyomiJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import okhttp3.Response

/*
 * `JsonExtensionsKt`: extensions call `response.parseAs<T>()`. These are inline in the original
 * library, so the JVM name of the enclosing file is part of the linkage contract.
 */

fun <T> Response.parseAs(serializer: KSerializer<T>): T =
    aniyomiJson.decodeFromString(serializer, body?.string().orEmpty())

inline fun <reified T> Response.parseAs(transform: (String) -> String): T =
    aniyomiJson.decodeFromString(transform(body?.string().orEmpty()))

inline fun <reified T> Response.parseAs(): T =
    aniyomiJson.decodeFromString(serializer<T>(), body?.string().orEmpty())

inline fun <reified T> String.parseAs(transform: (String) -> String): T =
    aniyomiJson.decodeFromString(transform(this))

inline fun <reified T> String.parseAs(): T =
    aniyomiJson.decodeFromString(this)
