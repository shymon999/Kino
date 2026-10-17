package com.nuvio.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.tv.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class KinoPreferences(
    val bitrateMbps: Int? = null,
    val hiddenGenres: Set<String> = emptySet(),
    val serverSetupComplete: Boolean = false
) {
    val bitrateBps: Long? get() = bitrateMbps?.times(1_000_000L)

    companion object {
        val bitrateOptions = listOf(10, 15, 20, 25, 30, 40, 60, 80, 100)
    }
}

/** Installation-wide fork preferences. Credentials remain in the existing secure server store. */
@Singleton
class KinoPreferencesDataStore internal constructor(
    private val preferences: SharedPreferences,
    val familyBuild: Boolean
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        context.getSharedPreferences("kino_preferences", Context.MODE_PRIVATE), BuildConfig.FAMILY_BUILD
    )

    private fun read(): KinoPreferences {
        val saved = preferences.getInt("bitrate_mbps", if (familyBuild) 40 else 0)
        return KinoPreferences(
            bitrateMbps = saved.takeIf { it in KinoPreferences.bitrateOptions },
            hiddenGenres = preferences.getStringSet("hidden_genres", emptySet()).orEmpty().toSet(),
            serverSetupComplete = preferences.getBoolean("server_setup_complete", false)
        )
    }

    private val mutableState = MutableStateFlow(read())
    val state = mutableState.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        mutableState.value = read()
    }

    init { preferences.registerOnSharedPreferenceChangeListener(listener) }

    fun setBitrateMbps(value: Int?) {
        require(value == null || value in KinoPreferences.bitrateOptions)
        preferences.edit().putInt("bitrate_mbps", value ?: 0).apply()
        mutableState.value = read()
    }

    fun setGenreHidden(genre: String, hidden: Boolean) {
        val current = state.value.hiddenGenres
        val next = if (hidden) current + genre else current - genre
        setHiddenGenres(next)
    }

    fun setHiddenGenres(genres: Set<String>) {
        preferences.edit().putStringSet("hidden_genres", genres).apply()
        mutableState.value = read()
    }

    fun completeServerSetup() {
        preferences.edit().putBoolean("server_setup_complete", true).apply()
        mutableState.value = read()
    }
}
