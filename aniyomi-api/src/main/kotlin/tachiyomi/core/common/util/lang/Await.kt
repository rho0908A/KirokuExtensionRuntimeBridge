package tachiyomi.core.common.util.lang

import eu.kanade.tachiyomi.util.awaitSingle as awaitSingleItem
import rx.Observable

/**
 * Anikku/Mihon's multiplatform helper, provided here as a plain JVM implementation.
 *
 * Both `AnimeSource` (Anikku) and `Source` (Mihon) declare their suspend methods in terms of this
 * function, so extensions compiled against either project reference it by this name.
 */
suspend fun <T : Any> Observable<T>.awaitSingle(): T = awaitSingleItem()
