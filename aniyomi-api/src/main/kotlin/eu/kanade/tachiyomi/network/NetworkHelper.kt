package eu.kanade.tachiyomi.network

import android.content.Context
import okhttp3.OkHttpClient

/**
 * `NetworkHelper` as declared by Aniyomi/Animiru extensions-lib.
 *
 * Extensions normally read their client from `AnimeHttpSource.client`, but some (and some shared
 * helper libraries bundled into extensions) reference `NetworkHelper` directly. The runtime has one
 * shared client, so every accessor returns the same instance.
 */
@Suppress("unused_parameter", "unused")
class NetworkHelper(context: Context) {

    val client: OkHttpClient
        get() = NETWORK_CLIENT

    @Deprecated("The provided regular client should have cloudflare handling capability")
    val cloudflareClient: OkHttpClient
        get() = NETWORK_CLIENT

    fun defaultUserAgentProvider(): String = DEFAULT_USER_AGENT

    companion object {
        const val DEFAULT_USER_AGENT: String =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
