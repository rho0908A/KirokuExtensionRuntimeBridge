package eu.kanade.tachiyomi.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Shared OkHttp client handed to every source that does not override `client`.
 *
 * The runtime replaces [NETWORK_CLIENT] during initialisation so that DNS, timeouts and logging can
 * be configured in one place. Extensions see a regular `OkHttpClient`, exactly as in Aniyomi.
 */
@Volatile
var NETWORK_CLIENT: OkHttpClient = defaultClient()

private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .followRedirects(true)
    .build()
