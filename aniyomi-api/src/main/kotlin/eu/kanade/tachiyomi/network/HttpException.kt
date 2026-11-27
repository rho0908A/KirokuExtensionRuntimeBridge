package eu.kanade.tachiyomi.network

/**
 * HTTP error raised by extensions since extensions-lib 17.
 *
 * @param code the HTTP status code.
 */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
