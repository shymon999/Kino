package com.nuvio.tv.data.local

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PosterPreferences(
    val polishPosters: Boolean,
    val hasApiKey: Boolean,
    val revision: Long = 0
)

@Singleton
class PosterPreferencesDataStore internal constructor(
    private val storage: ProtectedProfilePreferences,
    familyBuild: Boolean = false,
    bundledApiKey: String = ""
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        ProtectedProfilePreferences(context, "kino_posters", "kino_poster_credentials_v1"),
        BuildConfig.FAMILY_BUILD, BuildConfig.TMDB_API_KEY
    )

    private val stored = storage.read(0)?.split('\n', limit = 2)
    @Volatile private var apiKey = stored?.getOrNull(1).orEmpty().ifEmpty {
        if (stored == null && familyBuild) bundledApiKey else ""
    }
    private val mutableState = MutableStateFlow(PosterPreferences(
        polishPosters = stored?.firstOrNull()?.let { it == "1" } ?: familyBuild,
        hasApiKey = apiKey.isNotBlank()
    ))
    val state = mutableState.asStateFlow()

    fun savedApiKey(): String = apiKey
    fun activeApiKey(): String = if (state.value.polishPosters) apiKey else ""

    @Synchronized
    fun save(polishPosters: Boolean, key: String) {
        val trimmed = key.trim()
        require(validApiKey(trimmed) && (!polishPosters || trimmed.isNotEmpty()))
        storage.write(0, "${if (polishPosters) 1 else 0}\n$trimmed")
        apiKey = trimmed
        mutableState.value = PosterPreferences(polishPosters, trimmed.isNotEmpty(), state.value.revision + 1)
    }

    companion object {
        fun validApiKey(value: String): Boolean = value.isEmpty() || value.matches(Regex("[a-fA-F0-9]{32}"))
    }
}
