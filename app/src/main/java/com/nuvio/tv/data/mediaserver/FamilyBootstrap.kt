package com.nuvio.tv.data.mediaserver

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.local.AppOnboardingDataStore
import com.nuvio.tv.data.local.ExperienceModeDataStore
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.PlayerSettingsDataStore
import com.nuvio.tv.data.local.StreamAutoPlayMode
import com.nuvio.tv.data.local.StreamAutoPlaySource
import com.nuvio.tv.data.local.TrailerSettingsDataStore
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.domain.model.HomeImdbRatingsVisibility
import com.nuvio.tv.domain.model.HomeLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Singleton
class FamilyBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    repository: ServerRepository,
    private val player: PlayerSettingsDataStore,
    private val layout: LayoutPreferenceDataStore,
    private val experience: ExperienceModeDataStore,
    private val trailers: TrailerSettingsDataStore,
    private val onboarding: AppOnboardingDataStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val coordinator = FamilySetupCoordinator(
        repository,
        FamilyCredentials(BuildConfig.FAMILY_EMBY_URL, BuildConfig.FAMILY_EMBY_USERNAME, BuildConfig.FAMILY_EMBY_PASSWORD),
        ::applyDefaults
    )
    val state = if (BuildConfig.FAMILY_BUILD) coordinator.state
        else MutableStateFlow(FamilySetupState(loading = false))

    fun start() {
        if (BuildConfig.FAMILY_BUILD) scope.launch { coordinator.prepare() }
    }

    private suspend fun applyDefaults() {
        val preferences = context.getSharedPreferences("family_setup", Context.MODE_PRIVATE)
        if (preferences.getBoolean("defaults_applied", false)) return
        player.setStreamAutoPlayMode(StreamAutoPlayMode.FIRST_STREAM)
        player.setStreamAutoPlaySource(StreamAutoPlaySource.ALL_SOURCES)
        player.setPreferredAudioLanguage("pl")
        player.setSubtitlePreferredLanguage("pl")
        trailers.setEnabled(false)
        layout.setLayout(HomeLayout.CLASSIC)
        layout.setHomeImdbRatingsVisibility(HomeImdbRatingsVisibility.SHOW_ALL)
        experience.setMode(ExperienceMode.ESSENTIAL)
        experience.setAddonSetupSkipped(true)
        onboarding.setHasSeenAuthQrOnFirstLaunch(true)
        check(preferences.edit().putBoolean("defaults_applied", true).commit())
    }
}
