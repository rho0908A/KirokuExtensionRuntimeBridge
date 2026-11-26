package eu.kanade.tachiyomi.source

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen

/** Mihon's configurable source contract. */
interface ConfigurableSource : Source {

    fun getSourcePreferences(): SharedPreferences

    fun setupPreferenceScreen(screen: PreferenceScreen)
}

/** Sources that do not count against metered connections. */
interface UnmeteredSource
