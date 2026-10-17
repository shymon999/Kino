@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.local.*
import com.nuvio.tv.data.mediaserver.ServerRepository
import com.nuvio.tv.domain.model.ExperienceMode
import com.nuvio.tv.domain.model.HomeLayout
import com.nuvio.tv.ui.components.BrandWordmark
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class UniversalEmbySetupViewModel @Inject constructor(
    private val repository: ServerRepository,
    private val preferences: KinoPreferencesDataStore,
    private val layout: LayoutPreferenceDataStore,
    private val experience: ExperienceModeDataStore,
    private val onboarding: AppOnboardingDataStore,
    private val player: PlayerSettingsDataStore,
    private val trailers: TrailerSettingsDataStore
) : ViewModel() {
    private val fresh = run { repository.ensureLoaded(); repository.enabledConnections().isEmpty() }
    val finishing = MutableStateFlow(false)
    val failed = MutableStateFlow(false)

    fun complete() {
        if (finishing.value || repository.enabledConnections().none {
            it.providerId == "emby" && it.selectedLibraries.isNotEmpty()
        }) return
        finishing.value = true
        failed.value = false
        viewModelScope.launch {
            try {
                if (!layout.hasChosenLayout.first()) layout.setLayout(HomeLayout.CLASSIC)
                if (experience.mode.first() == null) experience.setMode(ExperienceMode.ESSENTIAL)
                experience.setAddonSetupSkipped(true)
                if (fresh) {
                    player.setStreamAutoPlayMode(StreamAutoPlayMode.FIRST_STREAM)
                    player.setStreamAutoPlaySource(StreamAutoPlaySource.ALL_SOURCES)
                    trailers.setEnabled(false)
                }
                onboarding.setHasSeenAuthQrOnFirstLaunch(true)
                preferences.completeServerSetup()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                failed.value = true
            } finally {
                finishing.value = false
            }
        }
    }
}

@Composable
fun UniversalEmbySetupScreen(
    onExit: () -> Unit,
    servers: MediaServersViewModel = hiltViewModel(),
    setup: UniversalEmbySetupViewModel = hiltViewModel()
) {
    val state by servers.uiState.collectAsStateWithLifecycle()
    val finishing by setup.finishing.collectAsStateWithLifecycle()
    val failed by setup.failed.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<String?>(null) }
    val connection = state.enabledConnections.firstOrNull { it.id == selectedId }
        ?: state.enabledConnections.firstOrNull { it.providerId == "emby" }
    val emby = servers.providers.firstOrNull { it.id == "emby" }
    var showSignIn by remember { mutableStateOf(connection == null) }
    val firstLibraryFocus = remember { FocusRequester() }
    LaunchedEffect(connection?.id) {
        if (connection?.libraries?.isNotEmpty() == true) {
            withFrameNanos { }
            runCatching { firstLibraryFocus.requestFocus() }
        }
    }
    BackHandler { if (showSignIn) showSignIn = false else onExit() }
    SettingsStandaloneScaffold(
        title = stringResource(R.string.kino_setup_title),
        subtitle = stringResource(R.string.kino_setup_description)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BrandWordmark(Modifier.height(48.dp).align(Alignment.Start), contentDescription = "Kino")
            if (connection == null) {
                Text(stringResource(R.string.kino_setup_privacy), style = MaterialTheme.typography.bodyLarge,
                    color = NuvioTheme.colors.TextPrimary)
                SettingsActionRow(title = stringResource(R.string.kino_connect_emby),
                    subtitle = stringResource(R.string.kino_setup_login_description),
                    enabled = emby != null, onClick = { showSignIn = true })
            } else {
                Text(stringResource(R.string.kino_select_libraries), style = MaterialTheme.typography.headlineSmall,
                    color = NuvioTheme.colors.TextPrimary)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (connection.libraries.isEmpty()) item {
                        Text(stringResource(R.string.kino_setup_empty), color = NuvioTheme.colors.TextPrimary)
                    }
                    items(connection.libraries, key = { it.id }) { library ->
                        SettingsToggleRow(title = library.name, subtitle = null, checked = library.selected,
                            modifier = if (library == connection.libraries.firstOrNull()) Modifier.focusRequester(firstLibraryFocus) else Modifier,
                            onToggle = { servers.setLibrarySelected(connection.id, library.id, !library.selected) })
                    }
                }
                if (failed) Text(stringResource(R.string.kino_setup_failed), color = NuvioTheme.colors.Error)
                SettingsActionRow(title = stringResource(R.string.kino_setup_other), subtitle = null,
                    onClick = { showSignIn = true })
                SettingsActionRow(title = stringResource(if (finishing) R.string.kino_setup_finishing else R.string.kino_setup_continue),
                    subtitle = stringResource(R.string.kino_setup_next_settings),
                    enabled = !finishing && connection.selectedLibraries.isNotEmpty(), onClick = setup::complete)
            }
        }
    }
    if (showSignIn && emby != null) ServerSignInDialog(
        request = ServerSignInRequest(emby), viewModel = servers,
        onConnected = { selectedId = it.id; showSignIn = false }, onDismiss = { showSignIn = false }
    )
}
