package eu.kanade.tachiyomi.animesource.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import uy.kohesive.injekt.Injekt

/** Gets preference key for source with [id]. */
fun preferencesKey(id: Long) = "source_$id"

/** Gets preference key for source. */
fun AnimeSource.preferencesKey(): String = preferencesKey(id)

/** Gets instance of [SharedPreferences] scoped to the specific source. */
fun sourcePreferences(key: String): SharedPreferences =
    Injekt.get<Application>().getSharedPreferences(key, Context.MODE_PRIVATE)

/** Gets instance of [SharedPreferences] scoped to the specific source. */
fun AnimeSource.sourcePreferences(): SharedPreferences = sourcePreferences(preferencesKey())

/** Gets instance of [SharedPreferences] scoped to the specific source id. */
fun sourcePreferences(id: Long): SharedPreferences = sourcePreferences(preferencesKey(id))
